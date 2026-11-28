package com.lingshu.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 一个功能模块。App 首页按登记顺序显示为底部标签。
 * 以后加设备控制、R3 审批、语音对话：写一个新模块实现它，在 app 的 Features 里登记即可。
 */
interface Feature {
    /** 唯一标识。 */
    val id: String

    /** lingshu /v1/app/me 返回的 features 里要有这一项才显示；为 null 时总是显示（本地功能，如设置）。 */
    val requires: String?

    val title: String
    val icon: ImageVector

    @Composable
    fun Content()
}
