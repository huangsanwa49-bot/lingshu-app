package com.xiaojia.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaojia.core.Feature
import com.xiaojia.core.Xiaojia
import com.xiaojia.core.ui.XiaojiaBadge
import com.xiaojia.core.ui.XjIcons

object ChatFeature : Feature {
    override val id = "chat"
    override val requires = "chat"
    override val title = "对话"
    override val icon = XjIcons.Chat

    @Composable
    override fun Content() = ChatScreen()
}

@Composable
fun ChatScreen(modifier: Modifier = Modifier, compact: Boolean = false) {
    val messages by ChatSession.messages.collectAsStateWithLifecycle()
    val sending by ChatSession.sending.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()

    LaunchedEffect(messages.size, sending) {
        val count = messages.size + if (sending) 1 else 0
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                EmptyChat(compact)
            } else {
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(messages, key = { it.id }) { Bubble(it) }
                    if (sending) item(key = "typing") { Typing() }
                }
            }
        }
        Surface(tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 4000) input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("和笨笨同学说点什么…") },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        ChatSession.send(input)
                        input = ""
                    }),
                )
                Spacer(Modifier.size(8.dp))
                FilledIconButton(
                    onClick = {
                        ChatSession.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !sending,
                    modifier = Modifier.size(48.dp),
                ) { Icon(XjIcons.Send, contentDescription = "发送") }
            }
        }
    }
}

@Composable
private fun EmptyChat(compact: Boolean) {
    val name = Xiaojia.me?.name
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        XiaojiaBadge(if (compact) 52.dp else 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(if (name != null) "$name，有什么可以帮你？" else "有什么可以帮你？", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "对话内容只在这次打开时保留，手机上不存记录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val mine = m.fromUser
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        if (!mine) {
            XiaojiaBadge(30.dp)
            Spacer(Modifier.size(8.dp))
        }
        Surface(
            color = when {
                m.failed -> colors.errorContainer
                mine -> colors.primary
                else -> colors.surfaceVariant
            },
            contentColor = when {
                m.failed -> colors.onErrorContainer
                mine -> colors.onPrimary
                else -> colors.onSurface
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp,
                bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(m.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Typing() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        XiaojiaBadge(30.dp)
        Spacer(Modifier.size(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("笨笨同学正在想…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 快捷对话窗口顶部的小把手。 */
@Composable
internal fun Grabber() {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 36.dp, height = 4.dp).background(Color.Gray.copy(alpha = 0.4f), CircleShape))
    }
}
