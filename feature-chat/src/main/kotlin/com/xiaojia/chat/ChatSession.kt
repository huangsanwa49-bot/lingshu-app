package com.xiaojia.chat

import com.xiaojia.core.HubException
import com.xiaojia.core.Xiaojia
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

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private var conversationId = UUID.randomUUID().toString()
    private var nextId = 0L

    /** 收到回复时回调（悬浮小助理用来朗读）。 */
    var onReply: ((String) -> Unit)? = null

    init {
        Xiaojia.onSignOut { reset() }
    }

    fun reset() {
        _messages.value = emptyList()
        conversationId = UUID.randomUUID().toString()
    }

    fun send(text: String) {
        val content = text.trim().take(4000)
        if (content.isEmpty() || _sending.value) return
        _messages.update { it + ChatMessage(nextId++, fromUser = true, text = content) }
        val turns = _messages.value.filterNot { it.failed }.takeLast(CONTEXT_TURNS)
            .map { (if (it.fromUser) "user" else "assistant") to it.text }
        _sending.value = true
        Xiaojia.scope.launch {
            try {
                val reply = Xiaojia.api.chat(conversationId, turns)
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
