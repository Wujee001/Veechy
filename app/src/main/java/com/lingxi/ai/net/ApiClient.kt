package com.lingxi.ai.net

import com.lingxi.ai.data.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * OpenAI 兼容协议客户端。
 *
 * 支持的 baseUrl 形态：
 *  - https://api.qnaigc.com                  -> https://api.qnaigc.com/v1/chat/completions
 *  - https://xxx/compatible-mode/v1          -> https://xxx/compatible-mode/v1/chat/completions
 *  - http://192.168.1.10:11434/v1            -> http://192.168.1.10:11434/v1/chat/completions
 */
class ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun endpoint(base: String, path: String): String {
        var b = base.trim().trimEnd('/')
        if (b.isEmpty()) throw IOException("API 地址为空")
        if (!b.endsWith("/v1") && !b.contains("/v1/") && !b.contains("/compatible-mode/")) {
            b = "$b/v1"
        }
        return b + path
    }

    // ------------------------------------------------------------ 模型列表

    suspend fun fetchModels(base: String, key: String): List<ModelInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(endpoint(base, "/models"))
            .apply { if (key.isNotBlank()) header("Authorization", "Bearer $key") }
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} ${extractError(body)}")
            }
            val arr = runCatching { JSONObject(body).optJSONArray("data") }
                .getOrNull() ?: throw IOException("返回格式不是 OpenAI /models")
            val out = ArrayList<ModelInfo>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                out.add(
                    ModelInfo(
                        id = id,
                        ownedBy = o.optString("owned_by"),
                        vision = ModelInfo.guessVision(id),
                        audio = ModelInfo.guessAudio(id)
                    )
                )
            }
            out.sortedBy { it.id }
        }
    }

    // ------------------------------------------------------------ 流式对话

    /**
     * @return 完整回复文本
     */
    suspend fun streamChat(
        base: String,
        key: String,
        model: String,
        messages: JSONArray,
        temperature: Double,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit = {},
        enableSearch: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("stream", true)
            put("temperature", temperature)
            // 阿里云百炼 / 通义千问 兼容模式：开启后由服务端联网检索
            if (enableSearch) put("enable_search", true)
        }

        val req = Request.Builder()
            .url(endpoint(base, "/chat/completions"))
            .apply { if (key.isNotBlank()) header("Authorization", "Bearer $key") }
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val call = client.newCall(req)
        val handle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IOException("HTTP ${resp.code} ${extractError(err)}")
                }

                val body = resp.body ?: throw IOException("响应为空")
                val ctype = resp.header("Content-Type").orEmpty()

                // 有些服务端忽略 stream=true 直接返回 JSON
                if (ctype.contains("application/json")) {
                    val json = JSONObject(body.string())
                    val msg = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    val text = if (msg == null || msg.isNull("content")) "" else msg.optString("content", "")
                    if (text.isNotEmpty()) withContext(Dispatchers.Main) { onDelta(text) }
                    return@withContext text
                }

                val sb = StringBuilder()
                val source = body.source()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank() || !line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    val delta = json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta") ?: continue

                    // 注意：JSONObject.optString 对 JSON null 会返回字符串 "null"，
                    // DeepSeek 等会在首包/尾包给出 content:null，必须先判空
                    val content = if (delta.isNull("content")) "" else delta.optString("content", "")
                    if (content.isNotEmpty()) {
                        sb.append(content)
                        // 回调切回主线程，保证 Compose 状态写入安全
                        withContext(Dispatchers.Main) { onDelta(content) }
                    }
                    val rc = if (delta.isNull("reasoning_content")) "" else delta.optString("reasoning_content", "")
                    val r2 = if (delta.isNull("reasoning")) "" else delta.optString("reasoning", "")
                    val reasoning = if (rc.isEmpty()) r2 else rc
                    if (reasoning.isNotEmpty() && reasoning != "null") {
                        withContext(Dispatchers.Main) { onReasoning(reasoning) }
                    }
                }
                sb.toString()
            }
        } finally {
            handle?.dispose()
        }
    }

    /** 连通性检测：只请求 /models，不消耗 token。 */
    suspend fun test(base: String, key: String): String = withContext(Dispatchers.IO) {
        val models = fetchModels(base, key)
        "连接成功 · ${models.size} 个模型"
    }

    private fun extractError(raw: String): String {
        if (raw.isBlank()) return ""
        val msg = runCatching {
            val o = JSONObject(raw)
            o.optJSONObject("error")?.optString("message")
                ?: o.optString("message")
                ?: o.optString("detail")
        }.getOrNull()
        return (msg ?: raw).take(240)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
