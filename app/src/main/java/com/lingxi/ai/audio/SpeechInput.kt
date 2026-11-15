package com.lingxi.ai.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 基于系统 SpeechRecognizer 的连续语音输入。
 * 所有回调都在主线程，状态直接暴露给 Compose。
 */
class SpeechInput(private val context: Context) {

    var available by mutableStateOf(SpeechRecognizer.isRecognitionAvailable(context))
        private set
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var level by mutableStateOf(0f)
        private set
    var errorText by mutableStateOf<String?>(null)
        private set

    var onFinal: ((String) -> Unit)? = null
    var autoRestart = true

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wantListening = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
            errorText = null
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            listening = false
            level = 0f
        }

        override fun onError(error: Int) {
            listening = false
            level = 0f
            errorText = describe(error)
            if (wantListening && autoRestart) {
                main.postDelayed({ if (wantListening) startInternal() }, 450)
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            partial = ""
            listening = false
            level = 0f
            if (text.isNotBlank()) onFinal?.invoke(text.trim())
            if (wantListening && autoRestart) {
                main.postDelayed({ if (wantListening) startInternal() }, 300)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start() {
        wantListening = true
        main.post { startInternal() }
    }

    fun stop() {
        wantListening = false
        main.post {
            runCatching { recognizer?.stopListening() }
            listening = false
            level = 0f
        }
    }

    fun destroy() {
        wantListening = false
        autoRestart = false
        main.post {
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
    }

    private fun startInternal() {
        if (!available) return
        if (recognizer == null) {
            recognizer = runCatching {
                SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(listener)
                }
            }.getOrNull()
        }
        if (recognizer == null) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }
        runCatching {
            recognizer?.cancel()
            recognizer?.startListening(intent)
            listening = true
        }.onFailure { errorText = it.message }
    }

    private fun describe(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "录音出错"
        SpeechRecognizer.ERROR_CLIENT -> "识别客户端异常"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
        SpeechRecognizer.ERROR_NO_MATCH -> "没听清"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
        SpeechRecognizer.ERROR_SERVER -> "服务器错误"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音"
        else -> "识别错误($code)"
    }
}
