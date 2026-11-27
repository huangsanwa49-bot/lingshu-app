package com.xiaojia.app

import android.app.Application
import android.content.Intent
import com.xiaojia.assistant.Assistant
import com.xiaojia.assistant.AssistantFeature
import com.xiaojia.chat.ChatFeature
import com.xiaojia.chat.ChatSession
import com.xiaojia.chat.QuickChatActivity
import com.xiaojia.core.Feature
import com.xiaojia.core.Xiaojia
import com.xiaojia.today.TodayFeature

/** 功能模块登记处。以后的设备控制、R3 审批、语音对话在这里加一行。 */
object Features {
    val all: List<Feature> = listOf(TodayFeature, ChatFeature, AssistantFeature)
}

class XiaojiaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Xiaojia.init(this)
        // 模块之间不直接依赖，在这里接线
        Assistant.quickChat = { ctx -> Intent(ctx, QuickChatActivity::class.java) }
        ChatSession.onReply = { Assistant.speakReply(this, it) }
        TodayFeature.speak = { Assistant.speak(this, it) }
        Xiaojia.onSignOut { Assistant.onSignedOut(this) }
    }
}
