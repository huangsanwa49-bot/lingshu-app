package com.lingshu.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingshu.app.Features
import com.lingshu.core.Feature
import com.lingshu.core.Me
import com.lingshu.core.Lingshu
import com.lingshu.core.ui.Brand
import com.lingshu.core.ui.LingshuIcons

private object MeFeature : Feature {
    override val id = "me"
    override val requires: String? = null
    override val title = "我的"
    override val icon = LingshuIcons.User

    @Composable
    override fun Content() = MeScreen()
}

@Composable
fun HomeScreen(me: Me) {
    val tabs = remember(me.features) {
        Features.all.filter { it.requires == null || it.requires in me.features } + MeFeature
    }
    var current by rememberSaveable { mutableStateOf(tabs.first().id) }
    val tab = tabs.firstOrNull { it.id == current } ?: tabs.first()

    Column(Modifier.fillMaxSize()) {
        Header(me, tab.title)
        Box(Modifier.weight(1f)) { tab.Content() }
        NavigationBar {
            tabs.forEach { f ->
                NavigationBarItem(
                    selected = f.id == tab.id,
                    onClick = { current = f.id },
                    icon = { Icon(f.icon, contentDescription = null) },
                    label = { Text(f.title) },
                )
            }
        }
    }
}

@Composable
private fun Header(me: Me, title: String) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${me.name} · ${roleName(me.role)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Avatar(me.name, 40)
        }
    }
}

@Composable
private fun Avatar(name: String, size: Int) {
    Surface(shape = CircleShape, color = Brand.Violet, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun roleName(role: String) = when (role) {
    "admin" -> "管理员"
    "member" -> "家庭成员"
    else -> role
}

@Composable
private fun MeScreen() {
    val me = Lingshu.me ?: return
    val endpoint by Lingshu.endpoint.collectAsStateWithLifecycle()
    var confirmUnpair by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(me.name, 56)
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(me.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${roleName(me.role)} · 已验证本人身份",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(vertical = 6.dp)) {
                InfoRow("lingshu", endpoint?.display ?: "—")
                HorizontalDivider()
                InfoRow("证书指纹", endpoint?.fingerprint ?: "—", mono = true)
                HorizontalDivider()
                InfoRow("登录有效至", me.expiresAt.take(16).replace('T', ' '))
            }
        }

        Text(
            "安全说明：登录凭据用手机的硬件密钥加密保存；只连配对过的 lingshu；对话记录不留在手机上；本页禁止截屏。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        OutlinedButton(onClick = { Lingshu.signOut() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("退出登录")
        }
        TextButton(onClick = { confirmUnpair = true }, modifier = Modifier.fillMaxWidth()) {
            Text("解除与 lingshu 的配对", color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("解除配对？") },
            text = { Text("会退出登录并忘记这台 lingshu，之后需要重新核对指纹配对。") },
            confirmButton = { TextButton(onClick = { Lingshu.unpair() }) { Text("解除") } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = if (mono) FontFamily.Monospace else null, style = MaterialTheme.typography.bodyMedium)
    }
}
