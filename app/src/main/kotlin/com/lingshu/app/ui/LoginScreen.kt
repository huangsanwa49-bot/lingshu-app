package com.lingshu.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lingshu.core.HubException
import com.lingshu.core.Lingshu
import com.lingshu.core.ui.LingshuIcons
import kotlinx.coroutines.launch

/** 第二步：账号、密码、动态口令。登录成功后 lingshu 就知道是哪位家人。 */
@Composable
fun LoginScreen() {
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    // 密码与口令不进 saveable 状态，避免被系统写进恢复数据
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val notice by Lingshu.lastError.collectAsStateWithLifecycle()
    val endpoint by Lingshu.endpoint.collectAsStateWithLifecycle()

    LaunchedEffect(notice) {
        if (notice != null) {
            error = notice
            Lingshu.clearError()
        }
    }

    AuthScaffold("登录", "用家里给你的账号登录，lingshu 就知道你是谁") {
        ErrorText(error)
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.trim(); error = null },
            label = { Text("用户名") },
            leadingIcon = { Icon(LingshuIcons.User, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("密码") },
            leadingIcon = { Icon(LingshuIcons.Lock, null) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = totp,
            onValueChange = { v -> totp = v.filter(Char::isDigit).take(6); error = null },
            label = { Text("动态口令（6 位）") },
            supportingText = { Text("打开手机上的验证器 App 查看，离线生成，不走短信") },
            singleLine = true,
            textStyle = TextStyle(fontSize = 22.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                busy = true
                scope.launch {
                    try {
                        Lingshu.login(username, password, totp)
                    } catch (e: HubException) {
                        error = e.message
                        totp = ""
                    } finally {
                        password = if (error == null) "" else password
                        busy = false
                    }
                }
            },
            enabled = username.isNotBlank() && password.isNotEmpty() && totp.length == 6 && !busy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("登录")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { Lingshu.unpair() }, modifier = Modifier.fillMaxWidth()) {
            Text(
                "连接的是 ${endpoint?.display ?: "—"} · 更换",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
