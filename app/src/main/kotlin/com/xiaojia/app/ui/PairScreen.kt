package com.xiaojia.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojia.core.HubException
import com.xiaojia.core.Tls
import com.xiaojia.core.Xiaojia
import com.xiaojia.core.ui.XjIcons
import kotlinx.coroutines.launch

/**
 * 第一步：连上家里的灵枢系统并核对证书指纹。
 * 指纹在运维门户总览页或 `xiaojia-hub app-pin` 里查看；一致才配对，之后只认这把公钥。
 */
@Composable
fun PairScreen() {
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var probed by remember { mutableStateOf<Triple<String, Int, ByteArray>?>(null) }

    AuthScaffold("连接灵枢系统", "手机连上家里的 Wi-Fi，填写迷你主机的地址") {
        ErrorText(error)
        val p = probed
        if (p == null) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim(); error = null },
                label = { Text("地址") },
                placeholder = { Text("192.168.1.10") },
                supportingText = { Text("默认端口 ${Xiaojia.DEFAULT_PORT}，可写成 地址:端口") },
                leadingIcon = { Icon(XjIcons.Wifi, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val parsed = parseAddress(address)
                    if (parsed == null) {
                        error = "地址格式不对，例如 192.168.1.10 或 192.168.1.10:8443"
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        try {
                            val pin = Xiaojia.api.probe(parsed.first, parsed.second)
                            probed = Triple(parsed.first, parsed.second, pin)
                        } catch (e: HubException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = address.isNotBlank() && !busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("连接")
            }
        } else {
            Text("请核对证书指纹", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "和运维门户总览页显示的「App 配对指纹」逐位比对。一致才点配对；不一致说明连到了别的设备，不要继续。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(XjIcons.Shield, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("${p.first}:${p.second}", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        Tls.fingerprint(p.third),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { probed = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("不一致") }
                Button(
                    onClick = { Xiaojia.pair(p.first, p.second, p.third) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text("一致，配对") }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "现阶段只能在家里的 Wi-Fi 下使用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val HOST = Regex("^[A-Za-z0-9.-]{1,253}$")

internal fun parseAddress(raw: String): Pair<String, Int>? {
    val s = raw.removePrefix("https://").removeSuffix("/")
    val host: String
    val port: Int
    val i = s.lastIndexOf(':')
    if (i > 0) {
        host = s.substring(0, i)
        port = s.substring(i + 1).toIntOrNull() ?: return null
    } else {
        host = s
        port = Xiaojia.DEFAULT_PORT
    }
    if (!HOST.matches(host) || port !in 1..65535) return null
    return host to port
}
