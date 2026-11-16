package com.lingxi.ai.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 用户反馈。走第三方表单转发（Formspree 等），App 里只放一个接收地址，
 * 收件邮箱不会出现在客户端，用户也看不到。
 *
 * 使用前把 ENDPOINT 换成你的表单地址，例如：
 *   https://formspree.io/f/xxxxxxxx
 */
object Feedback {

    private const val ENDPOINT = ""

    val configured: Boolean get() = ENDPOINT.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun submit(text: String, imagePath: String?): Unit = withContext(Dispatchers.IO) {
        if (ENDPOINT.isBlank()) throw IOException("反馈通道未配置")
        if (text.isBlank() && imagePath == null) throw IOException("内容为空")

        val payload = JSONObject().apply {
            put("message", text.trim())
            put("来源", "Wujee's AI Android")
            put("时间", System.currentTimeMillis().toString())
            imagePath?.let { p ->
                val f = File(p)
                if (f.exists()) {
                    put("图片名", f.name)
                    put("图片", "data:image/jpeg;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                }
            }
        }

        val req = Request.Builder()
            .url(ENDPOINT)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("提交失败（HTTP ${resp.code}）")
            }
            if (body.contains("\"errors\"", ignoreCase = true)) {
                throw IOException("提交被拒，请稍后再试")
            }
        }
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()
}