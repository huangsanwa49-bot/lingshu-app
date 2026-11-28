package com.lingshu.app

import android.app.Application
import android.content.Intent
import com.lingshu.assistant.Assistant
import com.lingshu.assistant.AssistantFeature
import com.lingshu.chat.ChatFeature
import com.lingshu.chat.ChatSession
import com.lingshu.chat.QuickChatActivity
import com.lingshu.approvals.ApprovalFeature
import com.lingshu.core.Feature
import com.lingshu.core.Lingshu
import com.lingshu.today.TodayFeature

/** 功能模块登记处。以后的设备控制、语音对话在这里加一行。 */
object Features {
    val all: List<Feature> = listOf(TodayFeature, ChatFeature, ApprovalFeature, AssistantFeature)
}

class LingshuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lingshu.init(this)
        // 模块之间不直接依赖，在这里接线
        Assistant.quickChat = { ctx -> Intent(ctx, QuickChatActivity::class.java) }
        ChatSession.onReply = { Assistant.speakReply(this, it) }
        TodayFeature.speak = { Assistant.speak(this, it) }
        Lingshu.onSignOut { Assistant.onSignedOut(this) }
    }
}
