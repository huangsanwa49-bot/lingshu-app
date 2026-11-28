package com.lingshu.core.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Brand {
    val Blue = Color(0xFF2A78D6)
    val BlueDark = Color(0xFF1C5CAB)
    val Aqua = Color(0xFF1BAF7A)
    val Violet = Color(0xFF6250D6)
    val Gradient = Brush.linearGradient(listOf(Blue, Aqua))
}

private val Light = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FA),
    onPrimaryContainer = Color(0xFF0D366B),
    secondary = Brand.Aqua,
    background = Color(0xFFF5F6F3),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F1EE),
    onSurfaceVariant = Color(0xFF52565C),
    error = Color(0xFFD03B3B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5598E7),
    onPrimary = Color(0xFF0B1F3A),
    primaryContainer = Color(0xFF184F95),
    onPrimaryContainer = Color(0xFFDCE9FA),
    secondary = Color(0xFF199E70),
    background = Color(0xFF0E1013),
    surface = Color(0xFF181B20),
    surfaceVariant = Color(0xFF1F232A),
    onSurfaceVariant = Color(0xFFBFC3C9),
    error = Color(0xFFE66767),
)

@Composable
fun LingshuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        // Android 12 起跟随系统取色，但主色仍用 lingshu 蓝，保证品牌一致
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
                .copy(primary = (if (dark) Dark else Light).primary, onPrimary = (if (dark) Dark else Light).onPrimary)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** lingshu 的圆形标志：蓝绿渐变上一个“家”字。悬浮球用同样的外观。 */
@Composable
fun LingshuBadge(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(Brand.Gradient, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("家", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}
