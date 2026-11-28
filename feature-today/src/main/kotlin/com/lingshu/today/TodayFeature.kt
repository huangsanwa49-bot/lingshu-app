package com.lingshu.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lingshu.core.Brief
import com.lingshu.core.Feature
import com.lingshu.core.HubException
import com.lingshu.core.Notice
import com.lingshu.core.Lingshu
import com.lingshu.core.ui.LingshuBadge
import com.lingshu.core.ui.LingshuIcons
import kotlinx.coroutines.launch

/** “今日”：问候、天气、关心提醒，以及发给本人的通知（可标记已读）。 */
object TodayFeature : Feature {
    override val id = "today"
    override val requires = "brief"
    override val title = "今日"
    override val icon = LingshuIcons.Home

    /** 朗读简报。app 模块接到小助理的语音上，本模块不直接依赖小助理。 */
    var speak: ((String) -> Unit)? = null

    @Composable
    override fun Content() = TodayScreen()
}

@Composable
private fun TodayScreen() {
    val scope = rememberCoroutineScope()
    val showInbox = Lingshu.me?.features?.contains("notifications") == true
    var brief by remember { mutableStateOf<Brief?>(null) }
    var notices by remember { mutableStateOf<List<Notice>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun reload() {
        if (loading) return
        loading = true
        scope.launch {
            try {
                brief = Lingshu.api.brief()
                if (showInbox) notices = Lingshu.api.recentNotifications()
                error = null
            } catch (e: HubException) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun markRead(n: Notice) {
        scope.launch {
            try {
                Lingshu.api.markRead(n.id)
                notices = notices?.map { if (it.id == n.id) it.copy(read = true) else it }
                brief = brief?.let { it.copy(unread = (it.unread - 1).coerceAtLeast(0)) }
            } catch (e: HubException) {
                error = e.message
            }
        }
    }

    // 每次回到这一页（包括从系统通知点进来）都刷新
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "brief") { BriefCard(brief, loading, onSpeak = { b -> TodayFeature.speak?.invoke(b.speech) }) }
        error?.let { msg ->
            item(key = "error") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(msg, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        if (showInbox) {
            item(key = "inbox-title") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "通知",
                        Modifier.weight(1f).padding(start = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = ::reload, enabled = !loading) { Text("刷新") }
                }
            }
            val list = notices
            when {
                list == null -> Unit
                list.isEmpty() -> item(key = "inbox-empty") {
                    Text(
                        "暂无通知",
                        Modifier.padding(horizontal = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> items(list, key = { it.id }) { NoticeCard(it, onRead = { markRead(it) }) }
            }
        }
    }
}

@Composable
private fun BriefCard(brief: Brief?, loading: Boolean, onSpeak: (Brief) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LingshuBadge(44.dp)
                Spacer(Modifier.size(12.dp))
                Text(
                    brief?.greeting ?: "benbentongxue 在这儿",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            if (brief != null) {
                Spacer(Modifier.size(12.dp))
                brief.weather?.takeIf { it.isNotBlank() }?.let { Text("天气：$it", style = MaterialTheme.typography.bodyLarge) }
                Text(brief.care, style = MaterialTheme.typography.bodyLarge)
                if (brief.unread > 0) {
                    Text(
                        "有 ${brief.unread} 条未读通知",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.size(12.dp))
                FilledTonalButton(onClick = { onSpeak(brief) }) {
                    Icon(LingshuIcons.Speaker, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("播报今日简报")
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(n: Notice, onRead: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            // 未读：标题前一个小圆点
            Surface(
                shape = CircleShape,
                color = if (n.read) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 7.dp).size(8.dp),
            ) {}
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (n.read) null else FontWeight.SemiBold)
                if (n.body.isNotBlank() && n.body != n.title) {
                    Text(n.body, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    n.createdAt.take(16).replace('T', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!n.read) TextButton(onClick = onRead) { Text("已读") }
        }
    }
}
