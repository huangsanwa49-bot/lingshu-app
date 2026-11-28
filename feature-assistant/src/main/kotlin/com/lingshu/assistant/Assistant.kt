package com.lingshu.assistant

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** 小助理的对外入口。app 模块在启动时设置 quickChat，让本模块不依赖对话模块。 */
object Assistant {
    private var prefsInstance: AssistantPrefs? = null

    /** 点悬浮球时打开的界面（快捷对话）。 */
    var quickChat: ((Context) -> Intent)? = null

    /** 服务运行时可用，用于朗读对话回复。 */
    internal var speaker: Speaker? = null

    fun prefs(context: Context): AssistantPrefs =
        prefsInstance ?: synchronized(this) {
            prefsInstance ?: AssistantPrefs(context.applicationContext).also { prefsInstance = it }
        }

    fun canShowBubble(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** 登录后、App 在前台时调用（Android 12 起不能从后台启动前台服务）。 */
    fun start(context: Context) {
        if (!prefs(context).bubble.value) return
        ContextCompat.startForegroundService(context, Intent(context, AssistantService::class.java))
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, AssistantService::class.java))
    }

    fun onSignedOut(context: Context) {
        stop(context)
        prefs(context).clearSessionState()
        // 换人登录后不应看到上一位家人的通知
        NotificationManagerCompat.from(context).cancelAll()
    }

    /** 设置里打开了“语音播报”时朗读（如“今日”页的简报）。小助理服务没在运行时临时建一个。 */
    fun speak(context: Context, text: String) {
        if (!prefs(context).speak.value) return
        (speaker ?: Speaker(context).also { speaker = it }).say(text)
    }

    internal fun openQuickChat(context: Context) {
        val intent = quickChat?.invoke(context) ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 对话回复：设置里打开了“朗读回复”时读出来。 */
    fun speakReply(context: Context, text: String) {
        if (prefs(context).speakReplies.value) speaker?.say(text)
    }
}
