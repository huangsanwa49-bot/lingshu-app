package com.lingshu.assistant

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 小助理的本地设置。不含任何机密。 */
class AssistantPrefs(context: Context) {
    private val p = context.getSharedPreferences("lingshu_assistant", Context.MODE_PRIVATE)

    private val _bubble = MutableStateFlow(p.getBoolean(K_BUBBLE, true))
    val bubble: StateFlow<Boolean> = _bubble.asStateFlow()

    private val _speak = MutableStateFlow(p.getBoolean(K_SPEAK, true))
    val speak: StateFlow<Boolean> = _speak.asStateFlow()

    private val _autoBrief = MutableStateFlow(p.getBoolean(K_AUTO_BRIEF, true))
    val autoBrief: StateFlow<Boolean> = _autoBrief.asStateFlow()

    private val _speakReplies = MutableStateFlow(p.getBoolean(K_SPEAK_REPLIES, false))
    val speakReplies: StateFlow<Boolean> = _speakReplies.asStateFlow()

    fun setBubble(v: Boolean) = set(K_BUBBLE, v, _bubble)
    fun setSpeak(v: Boolean) = set(K_SPEAK, v, _speak)
    fun setAutoBrief(v: Boolean) = set(K_AUTO_BRIEF, v, _autoBrief)
    fun setSpeakReplies(v: Boolean) = set(K_SPEAK_REPLIES, v, _speakReplies)

    /** 已处理到的通知 ID；-1 表示还没同步过（首次同步不播报旧通知）。 */
    var lastNoticeId: Long
        get() = p.getLong(K_LAST_NOTICE, -1)
        set(v) = p.edit().putLong(K_LAST_NOTICE, v).apply()

    /** 最近一次自动播报的时段（日期 + 早/中/晚），每个时段最多播一次。 */
    var lastBriefSlot: String?
        get() = p.getString(K_BRIEF_SLOT, null)
        set(v) = p.edit().putString(K_BRIEF_SLOT, v).apply()

    var bubbleX: Int
        get() = p.getInt(K_X, Int.MIN_VALUE)
        set(v) = p.edit().putInt(K_X, v).apply()

    var bubbleY: Int
        get() = p.getInt(K_Y, Int.MIN_VALUE)
        set(v) = p.edit().putInt(K_Y, v).apply()

    fun clearSessionState() {
        p.edit().remove(K_LAST_NOTICE).remove(K_BRIEF_SLOT).apply()
    }

    private fun set(key: String, v: Boolean, flow: MutableStateFlow<Boolean>) {
        p.edit().putBoolean(key, v).apply()
        flow.value = v
    }

    private companion object {
        const val K_BUBBLE = "bubble"
        const val K_SPEAK = "speak"
        const val K_AUTO_BRIEF = "auto_brief"
        const val K_SPEAK_REPLIES = "speak_replies"
        const val K_LAST_NOTICE = "last_notice"
        const val K_BRIEF_SLOT = "brief_slot"
        const val K_X = "x"
        const val K_Y = "y"
    }
}
