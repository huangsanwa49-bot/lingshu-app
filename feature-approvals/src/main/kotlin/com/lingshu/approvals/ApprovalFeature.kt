package com.lingshu.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lingshu.core.Approval
import com.lingshu.core.Feature
import com.lingshu.core.HubException
import com.lingshu.core.Lingshu
import com.lingshu.core.ui.LingshuIcons
import java.time.OffsetDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** R3 高风险操作：列出服务端生成的待确认项，本人在手机上确认后执行。 */
object ApprovalFeature : Feature {
    override val id = "approvals"
    override val requires = "approvals"
    override val title = "待确认"
    override val icon = LingshuIcons.Shield

    @Composable
    override fun Content() = ApprovalScreen()
}

@Composable
private fun ApprovalScreen() {
    val scope = rememberCoroutineScope()
    var approvals by remember { mutableStateOf<List<Approval>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf<Approval?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun reload() {
        if (loading) return
        loading = true
        scope.launch {
            try {
                approvals = Lingshu.api.approvals()
                error = null
            } catch (e: HubException) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun confirm(a: Approval) {
        if (busyId != null) return
        busyId = a.id
        scope.launch {
            try {
                Lingshu.api.confirmApproval(a.id)
                approvals = approvals?.filterNot { it.id == a.id }
                error = null
            } catch (e: HubException) {
                val msg = e.message ?: "确认失败"
                error = msg
                // 已处理、过期、不存在：刷新列表，让界面回到真实状态
                if (msg.contains("已处理") || msg.contains("过期") || msg.contains("不存在")) reload()
            } finally {
                busyId = null
            }
        }
    }

    LaunchedEffect(Unit) {
        // 切到这个标签时 App 已经在前台，不会再有 ON_RESUME；这里做首次加载
        reload()
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload() }

    val list = approvals
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hint") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    "开门等高风险操作需要你在这里确认；超过 2 分钟会失效，需要重新发起。",
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
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
        when {
            list == null -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            list.isEmpty() -> item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没有待确认的操作", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "有人发起开门等高风险操作时，会出现在这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> items(list, key = { it.id }) { a ->
                ApprovalCard(
                    a = a,
                    now = now,
                    busy = busyId == a.id,
                    onConfirm = { confirming = a },
                )
            }
        }
        if (list != null) {
            item(key = "refresh") {
                TextButton(onClick = ::reload, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text("刷新")
                }
            }
        }
    }

    confirming?.let { a ->
        AlertDialog(
            onDismissRequest = { if (busyId == null) confirming = null },
            title = { Text("确认执行？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(a.description ?: a.tool, style = MaterialTheme.typography.bodyLarge)
                    if (a.args.isNotEmpty()) {
                        Text(
                            a.args.entries.joinToString("，") { "${it.key} = ${it.value}" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        "确认后立即执行，且只能确认一次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { confirming = null; confirm(a) },
                    enabled = busyId == null,
                ) { Text("确认执行") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }, enabled = busyId == null) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ApprovalCard(a: Approval, now: Long, busy: Boolean, onConfirm: () -> Unit) {
    val remaining = expiresAtMillis(a.expiresAt)?.minus(now)
    val expired = remaining != null && remaining <= 0
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                a.description ?: a.tool,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (a.tool != a.description) {
                Text(
                    a.tool,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (a.args.isNotEmpty()) {
                Text(
                    a.args.entries.joinToString("，") { "${it.key} = ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        expired -> "已过期，请重新发起"
                        remaining != null -> "剩余 ${formatRemaining(remaining)}"
                        else -> "有效期未知"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onConfirm, enabled = !expired && !busy, shape = RoundedCornerShape(12.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("确认")
                }
            }
        }
    }
}

private fun expiresAtMillis(value: String): Long? =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()

private fun formatRemaining(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
