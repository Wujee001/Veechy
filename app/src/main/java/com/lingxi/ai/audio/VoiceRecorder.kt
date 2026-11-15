package com.lingxi.ai.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * 系统语音识别不可用时的兜底：直接录音为 16k mono WAV，
 * 交给服务端 /audio/transcriptions 转写。
 */
class VoiceRecorder(private val context: Context) {

    var recording by mutableStateOf(false)
        private set
    var level by mutableStateOf(0f)
        private set

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var pcmFile: File? = null

    private val sampleRate = 16000

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false

        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4
            )
        }.getOrNull() ?: return false

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            return false
        }

        val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.pcm")
        pcmFile = file
        record = rec
        recording = true

        runCatching { rec.startRecording() }

        thread = Thread {
            val buf = ByteArray(minBuf)
            runCatching {
                FileOutputStream(file).use { out ->
                    while (recording) {
                        val n = rec.read(buf, 0, buf.size)
                        if (n <= 0) continue
                        out.write(buf, 0, n)
                        var sum = 0.0
                        var i = 0
                        while (i + 1 < n) {
                            val s = ((buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8))
                                .toShort().toInt()
                            sum += (s * s).toDouble()
                            i += 2
                        }
                        val rms = sqrt(sum / (n / 2).coerceAtLeast(1))
                        level = (rms / 7000.0).toFloat().coerceIn(0f, 1f)
                    }
                }
            }
            level = 0f
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }.also { it.start() }

        return true
    }

    fun stop(): File? {
        if (!recording) return null
        recording = false
        level = 0f
        runCatching { thread?.join(800) }
        thread = null
        record = null

        val pcm = pcmFile ?: return null
        pcmFile = null
        if (!pcm.exists() || pcm.length() < 2048) {
            runCatching { pcm.delete() }
            return null
        }

        val wav = File(pcm.parentFile, pcm.nameWithoutExtension + ".wav")
        return runCatching {
            writeWav(pcm, wav)
            pcm.delete()
            wav
        }.getOrNull()
    }

    fun cancel() {
        recording = false
        runCatching { thread?.join(400) }
        thread = null
        record = null
        pcmFile?.let { runCatching { it.delete() } }
        pcmFile = null
        level = 0f
    }

    private fun writeWav(pcm: File, out: File) {
        val data = pcm.readBytes()
        FileOutputStream(out).use { os ->
            os.write("RIFF".toByteArray())
            os.write(intLE(36 + data.size))
            os.write("WAVE".toByteArray())
            os.write("fmt ".toByteArray())
            os.write(intLE(16))
            os.write(shortLE(1))
            os.write(shortLE(1))
            os.write(intLE(sampleRate))
            os.write(intLE(sampleRate * 2))
            os.write(shortLE(2))
            os.write(shortLE(16))
            os.write("data".toByteArray())
            os.write(intLE(data.size))
            os.write(data)
        }
    }

    private fun intLE(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun shortLE(v: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()
}
