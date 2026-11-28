package com.lingshu.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lingshu.app.ui.HomeScreen
import kotlinx.coroutines.launch
import com.lingshu.app.ui.LoginScreen
import com.lingshu.app.ui.PairScreen
import com.lingshu.assistant.Assistant
import com.lingshu.core.AuthState
import com.lingshu.core.Lingshu
import com.lingshu.core.ui.LingshuBadge
import com.lingshu.core.ui.LingshuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不允许截屏、录屏，最近任务里也不显示内容
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        startAssistantWhenSignedIn()
        enableEdgeToEdge()
        setContent {
            LingshuTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val auth by Lingshu.auth.collectAsStateWithLifecycle()
                    AnimatedContent(
                        targetState = auth,
                        contentKey = { it::class },
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "auth",
                    ) { state ->
                        when (state) {
                            AuthState.NeedPairing -> PairScreen()
                            AuthState.NeedLogin -> LoginScreen()
                            AuthState.Checking -> Loading()
                            is AuthState.Offline -> Offline(state.message)
                            is AuthState.SignedIn -> HomeScreen(state.me)
                        }
                    }
                }
            }
        }
    }

    /** 界面在前台、已登录时启动小助理服务（Android 12 起不能从后台启动前台服务），包括刚登录成功的那一刻。 */
    private fun startAssistantWhenSignedIn() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Lingshu.auth.collect { state ->
                    if (state is AuthState.SignedIn && Assistant.canShowBubble(this@MainActivity)) {
                        Assistant.start(this@MainActivity)
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LingshuBadge(72.dp)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun Offline(message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LingshuBadge(72.dp)
        Spacer(Modifier.height(20.dp))
        Text("暂时连不上 lingshu", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { Lingshu.refresh() }) { Text("重试") }
        TextButton(onClick = { Lingshu.signOut() }) { Text("退出登录") }
    }
}
