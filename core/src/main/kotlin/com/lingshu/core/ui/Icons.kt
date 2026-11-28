package com.lingshu.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 线条图标，和运维门户同一套风格。自己画，省掉图标库。 */
object LingshuIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = PathParser().parsePathString(it).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Home = icon("home", "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6.5h-6V21H4a1 1 0 0 1-1-1z")
    val Chat = icon("chat", "M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.4-4.9A8 8 0 1 1 21 12z", "M8.5 11h.01M12 11h.01M15.5 11h.01")
    val Assistant = icon(
        "assistant",
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z",
        "M8.5 14.5a4.5 4.5 0 0 0 7 0",
        "M9 9.5h.01M15 9.5h.01",
    )
    val Bell = icon("bell", "M6 9a6 6 0 0 1 12 0c0 5 2 6.5 2 6.5H4S6 14 6 9z", "M10 19.5a2 2 0 0 0 4 0")
    val User = icon("user", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M4.5 20.5a7.5 7.5 0 0 1 15 0")
    val Send = icon("send", "M4 12 20 4l-4 16-4-6.5z", "M12 13.5 20 4")
    val Lock = icon("lock", "M4 10.5h16V21H4z", "M8 10.5V7a4 4 0 0 1 8 0v3.5")
    val Shield = icon("shield", "M12 21.5s8-3.8 8-10V5.2L12 2.5 4 5.2v6.3c0 6.2 8 10 8 10z", "m9 12 2 2 4-4")
    val Wifi = icon("wifi", "M2.5 9a14 14 0 0 1 19 0", "M5.5 12.5a9.5 9.5 0 0 1 13 0", "M8.5 16a5 5 0 0 1 7 0", "M12 19.5h.01")
    val Close = icon("close", "M6 6l12 12M18 6 6 18")
    val Speaker = icon("speaker", "M4 9.5h3.5L12 5.5v13L7.5 14.5H4z", "M15.5 9a4 4 0 0 1 0 6", "M18 6.5a7.5 7.5 0 0 1 0 11")
}
