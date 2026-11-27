package com.xiaojia.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 系统自带的语音合成。手机里没装中文语音时，只显示文字不出声。 */
class Speaker(context: Context) {
    private var ready = false
    private val pending = ArrayDeque<String>()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            if (ready) while (pending.isNotEmpty()) say(pending.removeFirst(), queue = true)
        }
        pending.clear()
    }

    fun say(text: String, queue: Boolean = false) {
        if (text.isBlank()) return
        if (!ready) {
            if (pending.size < 5) pending.addLast(text)
            return
        }
        val mode = if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        tts.speak(text.take(TextToSpeech.getMaxSpeechInputLength()), mode, null, "xj-${System.nanoTime()}")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
