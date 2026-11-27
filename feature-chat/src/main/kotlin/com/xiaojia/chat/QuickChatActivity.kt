package com.xiaojia.chat

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaojia.core.AuthState
import com.xiaojia.core.Xiaojia
import com.xiaojia.core.ui.XiaojiaBadge
import com.xiaojia.core.ui.XiaojiaTheme
import com.xiaojia.core.ui.XjIcons

/** 点悬浮小助理弹出的对话窗：浮在当前应用上方，点空白处关闭。 */
class QuickChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Xiaojia.auth.value !is AuthState.SignedIn) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            XiaojiaTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        tonalElevation = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.72f)
                            .clickable(remember { MutableInteractionSource() }, indication = null) {},
                    ) {
                        Column(Modifier.navigationBarsPadding()) {
                            Grabber()
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                XiaojiaBadge(32.dp)
                                Spacer(Modifier.size(10.dp))
                                Text("笨笨同学", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { finish() }) { Icon(XjIcons.Close, contentDescription = "关闭") }
                            }
                            ChatScreen(compact = true)
                        }
                    }
                }
            }
        }
    }
}
