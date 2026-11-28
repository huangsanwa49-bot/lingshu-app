package com.lingshu.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingshu.core.Feature
import com.lingshu.core.HubException
import com.lingshu.core.Lingshu
import com.lingshu.core.ui.LingshuBadge
import com.lingshu.core.ui.LingshuIcons
import kotlinx.coroutines.launch

object AssistantFeature : Feature {
    override val id = "assistant"
    override val requires: String? = null
    override val title = "小助理"
    override val icon = LingshuIcons.Assistant

    @Composable
    override fun Content() = AssistantScreen()
}

@Composable
private fun AssistantScreen() {
    val context = LocalContext.current
    val prefs = remember { Assistant.prefs(context) }
    val bubble by prefs.bubble.collectAsStateWithLifecycle()
    val speak by prefs.speak.collectAsStateWithLifecycle()
    val autoBrief by prefs.autoBrief.collectAsStateWithLifecycle()
    val speakReplies by prefs.speakReplies.collectAsStateWithLifecycle()
    // 从系统设置页回来时重新检查权限
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(context) }
    val notifyOk = remember(tick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    LaunchedEffect(bubble, overlayOk) {
        if (bubble && overlayOk) Assistant.start(context) else if (!bubble) Assistant.stop(context)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Preview()

        if (!overlayOk || !notifyOk) {
            Section("需要的权限") {
                if (!overlayOk) {
                    PermissionRow("显示在其他应用上层", "悬浮小助理需要这个权限") {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                        )
                    }
                }
                if (!notifyOk && Build.VERSION.SDK_INT >= 33) {
                    PermissionRow("通知", "任务完成、待确认的操作会发到通知栏") {
                        notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }

        Section("小助理") {
            Toggle("悬浮小助理", "退到后台时显示 benbentongxue 的悬浮入口，点一下就能对话", bubble, prefs::setBubble)
            HorizontalDivider()
            Toggle("语音播报", "用手机的语音读出问候和通知", speak, prefs::setSpeak)
            HorizontalDivider()
            Toggle("按时段问候", "早、中、下午、晚上各问候一次，夜里不出声", autoBrief, prefs::setAutoBrief)
            HorizontalDivider()
            Toggle("朗读对话回复", "在快捷对话里，benbentongxue 的回答也读出来", speakReplies, prefs::setSpeakReplies)
        }

        Text(
            "长按悬浮球：播报问候与天气。拖动可以换位置，松手自动贴边。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** 现在就听一下 benbentongxue 的问候。 */
@Composable
private fun Preview() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var line by remember { mutableStateOf<String?>(null) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            LingshuBadge(52.dp)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(line ?: "benbentongxue 在这儿", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "点右边的按钮，听听今天的问候",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            FilledTonalButton(onClick = {
                scope.launch {
                    line = try {
                        val b = Lingshu.api.brief()
                        Assistant.speak(context, b.speech)
                        b.speech
                    } catch (e: HubException) {
                        e.message
                    }
                }
            }) {
                Icon(LingshuIcons.Speaker, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("问候")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
        Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(vertical = 4.dp)) { content() } }
    }
}

@Composable
private fun Toggle(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionRow(title: String, desc: String, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
        Button(onClick = onGrant) { Text("去开启") }
    }
}
