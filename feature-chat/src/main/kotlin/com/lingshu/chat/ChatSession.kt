package com.lingshu.chat

import com.lingshu.core.HubException
import com.lingshu.core.Lingshu
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val id: Long, val fromUser: Boolean, val text: String, val failed: Boolean = false)

/**
 * 当前对话。主界面与悬浮小助理的快捷对话共用一份。
 * 只在内存里：关掉 App 或退出登录就清空，手机上不留对话记录（个人记忆在家里的 Hindsight）。
 */
object ChatSession {
    private const val CONTEXT_TURNS = 20
    // lingshu 网关限制整个请求 64 KB；中文按 3 字节算，16k 字符留出足够余量
    private const val CONTEXT_CHARS = 16_000

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private var conversationId = UUID.randomUUID().toString()
    private var nextId = 0L

    /** 收到回复时回调（悬浮小助理用来朗读）。 */
    var onReply: ((String) -> Unit)? = null

    init {
        Lingshu.onSignOut { reset() }
    }

    fun reset() {
        _messages.value = emptyList()
        conversationId = UUID.randomUUID().toString()
    }

    fun send(text: String) {
        val content = text.trim().take(4000)
        if (content.isEmpty() || _sending.value) return
        _messages.update { it + ChatMessage(nextId++, fromUser = true, text = content) }
        val recent = _messages.value.filterNot { it.failed }.takeLast(CONTEXT_TURNS)
        val turns = ArrayDeque<Pair<String, String>>()
        var used = 0
        for (m in recent.asReversed()) {
            val cost = m.text.length + 24
            if (turns.isNotEmpty() && used + cost > CONTEXT_CHARS) break
            turns.addFirst((if (m.fromUser) "user" else "assistant") to m.text)
            used += cost
        }
        _sending.value = true
        Lingshu.scope.launch {
            try {
                val reply = Lingshu.api.chat(conversationId, turns)
                _messages.update { it + ChatMessage(nextId++, fromUser = false, text = reply) }
                onReply?.invoke(reply)
            } catch (e: HubException) {
                _messages.update { it + ChatMessage(nextId++, fromUser = false, text = e.message ?: "发送失败", failed = true) }
            } finally {
                _sending.value = false
            }
        }
    }
}
