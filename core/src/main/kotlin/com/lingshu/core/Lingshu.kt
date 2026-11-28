package com.lingshu.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object NeedPairing : AuthState
    data object NeedLogin : AuthState
    data object Checking : AuthState
    data class Offline(val message: String) : AuthState
    data class SignedIn(val me: Me) : AuthState
}

/**
 * App 的全局状态：配对的 lingshu 地址、登录会话、前后台状态。各功能模块通过它取 API 与当前成员。
 */
object Lingshu {
    private lateinit var appContext: Context
    private lateinit var secure: SecureStore
    private val prefs by lazy { appContext.getSharedPreferences("lingshu", Context.MODE_PRIVATE) }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _endpoint = MutableStateFlow<Endpoint?>(null)
    val endpoint: StateFlow<Endpoint?> = _endpoint.asStateFlow()

    private val _auth = MutableStateFlow<AuthState>(AuthState.NeedPairing)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    /** 有界面在前台时为 true；悬浮小助理只在 App 退到后台时出现。 */
    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    private val signOutListeners = mutableListOf<() -> Unit>()

    val api: HubApi by lazy {
        HubApi(endpoint = { _endpoint.value }, token = { secure.get(KEY_TOKEN) }, onUnauthorized = { expire() })
    }

    val me: Me? get() = (auth.value as? AuthState.SignedIn)?.me

    fun init(app: Application) {
        appContext = app
        secure = SecureStore(app)
        _endpoint.value = loadEndpoint()
        app.registerActivityLifecycleCallbacks(VisibilityTracker)
        when {
            _endpoint.value == null -> _auth.value = AuthState.NeedPairing
            secure.get(KEY_TOKEN) == null -> _auth.value = AuthState.NeedLogin
            else -> refresh()
        }
    }

    /** 用保存的会话向 lingshu 确认身份。会话失效时 onUnauthorized 转到登录；连不上时保留会话，可重试。 */
    fun refresh() {
        scope.launch {
            _auth.value = AuthState.Checking
            try {
                _auth.value = AuthState.SignedIn(api.me())
            } catch (e: HubException) {
                if (!e.unauthorized) _auth.value = AuthState.Offline(e.message ?: "连不上 lingshu")
            }
        }
    }

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun pair(host: String, port: Int, pin: ByteArray) {
        val changed = _endpoint.value?.let { !it.pin.contentEquals(pin) } ?: true
        prefs.edit()
            .putString(KEY_HOST, host)
            .putInt(KEY_PORT, port)
            .putString(KEY_PIN, Base64.encodeToString(pin, Base64.NO_WRAP))
            .apply()
        _endpoint.value = Endpoint(host, port, pin)
        // 换了服务器就作废旧会话
        if (changed) clearSession()
        if (secure.get(KEY_TOKEN) == null) _auth.value = AuthState.NeedLogin else refresh()
    }

    fun unpair() {
        signOut()
        prefs.edit().remove(KEY_HOST).remove(KEY_PORT).remove(KEY_PIN).apply()
        _endpoint.value = null
        _auth.value = AuthState.NeedPairing
    }

    suspend fun login(username: String, password: String, totp: String) {
        val (token, _) = api.login(username, password, totp)
        secure.put(KEY_TOKEN, token)
        try {
            _auth.value = AuthState.SignedIn(api.me())
        } catch (e: HubException) {
            clearSession()
            throw e
        }
    }

    /** 本人主动退出：本地立即清除；同时告诉 lingshu 作废这个会话（连不上也不影响本地退出）。 */
    fun signOut() {
        val token = secure.get(KEY_TOKEN)
        clearSession()
        if (token != null) scope.launch { runCatching { api.logout(token) } }
        if (_endpoint.value != null) _auth.value = AuthState.NeedLogin
    }

    /** lingshu 说会话失效（过期、被管理员注销、账号停用）。 */
    private fun expire() {
        clearSession()
        _lastError.value = "登录已失效，请重新登录"
        if (_endpoint.value != null) _auth.value = AuthState.NeedLogin
    }

    private fun clearSession() {
        secure.put(KEY_TOKEN, null)
        signOutListeners.forEach { it() }
    }

    fun onSignOut(listener: () -> Unit) {
        signOutListeners += listener
    }

    fun clearError() {
        _lastError.value = null
    }

    private fun loadEndpoint(): Endpoint? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val pin = prefs.getString(KEY_PIN, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return Endpoint(host, prefs.getInt(KEY_PORT, DEFAULT_PORT), pin)
    }

    private object VisibilityTracker : Application.ActivityLifecycleCallbacks {
        private var started = 0
        override fun onActivityStarted(activity: Activity) {
            started++
            _foreground.value = true
        }

        override fun onActivityStopped(activity: Activity) {
            started = (started - 1).coerceAtLeast(0)
            if (started == 0) _foreground.value = false
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    const val DEFAULT_PORT = 8443
    private const val KEY_TOKEN = "session_token"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_PIN = "pin"
}
