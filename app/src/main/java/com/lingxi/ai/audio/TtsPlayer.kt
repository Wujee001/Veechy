package com.lingxi.ai.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.UUID

/**
 * 系统 TTS 朗读。自动清理 Markdown 标记与代码块，并分段送入避免超长中断。
 */
class TtsPlayer(context: Context) {

    private var tts: TextToSpeech? = null

    var ready by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set

    var rate by mutableStateOf(1.0f)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val r = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.getDefault())
                }
                engine.setSpeechRate(rate)
                engine.setPitch(1.0f)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        speaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        speaking = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        speaking = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        speaking = false
                    }
                })
                ready = true
            }
        }
    }

    fun setRate(value: Float) {
        rate = value
        runCatching { tts?.setSpeechRate(value) }
    }

    fun speak(text: String) {
        val engine = tts ?: return
        if (!ready) return
        val clean = clean(text)
        if (clean.isBlank()) return
        stop()
        runCatching { engine.setSpeechRate(rate) }
        chunks(clean, 180).forEachIndexed { index, part ->
            engine.speak(part, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
        speaking = false
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
    }

    private fun clean(raw: String): String {
        var s = raw
        s = s.replace(Regex("```[\\s\\S]*?```"), "（代码略）")
        s = s.replace(Regex("`([^`]*)`")) { it.groupValues[1] }
        s = s.replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
        s = s.replace(Regex("\\[([^\\]]*)]\\([^)]*\\)")) { it.groupValues[1] }
        s = s.replace(Regex("(?m)^#{1,6}\\s*"), "")
        s = s.replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        s = s.replace(Regex("[*_~>|]"), "")
        s = s.replace(Regex("https?://\\S+"), "")
        s = s.replace(Regex("\\s+"), " ")
        return s.trim()
    }

    /** 按句号切分，保证长回答边生成边播。 */
    private fun chunks(text: String, size: Int): List<String> {
        if (text.length <= size) return listOf(text)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(ch)
            if (sb.length >= size && (ch == '。' || ch == '！' || ch == '？' || ch == '；' || ch == '\n')) {
                out.add(sb.toString().trim())
                sb.clear()
            }
        }
        if (sb.isNotBlank()) out.add(sb.toString().trim())
        return out.filter { it.isNotBlank() }
    }
}
