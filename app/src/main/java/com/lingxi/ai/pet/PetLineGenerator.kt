package com.lingxi.ai.pet

import android.content.Context
import com.lingxi.ai.data.ChatStore
import com.lingxi.ai.data.InnerVoice
import com.lingxi.ai.data.Persona
import com.lingxi.ai.data.SettingsStore
import com.lingxi.ai.data.Timetable
import com.lingxi.ai.net.ApiClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * 定时冒出的那句话。
 *
 * 走模型：注入真实时间、课表、用户提示词、隐藏人设，以及当前对话的最近几轮，
 * 让 TA 像突然想起你一样主动说一句，只有一句。
 * 断网 / 未配置 API / 生成失败时退回本地台词，保证气泡不会空。
 */
object PetLineGenerator {

    private const val RULES =
        "现在你要主动给对方发一句话，就像突然想起 TA。\n" +
            "要求：\n" +
            "1）只说一句，不超过 30 字；不要写〔〕、不要引号、不要表情、不要解释。\n" +
            "2）语气符合人设：温柔里带一点掌控感，不客套、不喊口号。\n" +
            "3）优先顺着上面的对话历史自然延续；没有可接的话，再结合真实时间或课表关心一句。\n" +
            "4）不许编造现实：不要说你在做什么、不要说看到对方在做什么、不要虚构事件。\n" +
            "5）不要重复最近已经说过的话。"

    suspend fun generate(context: Context): String {
        val remote = runCatching { remote(context) }.getOrNull()
        if (!remote.isNullOrBlank()) return remote
        return PetMessages.scheduled(context)
    }

    private suspend fun remote(context: Context): String {
        val settings = SettingsStore(context)
        val providers = settings.loadProviders()
        val provider = settings.activeProviderId
            ?.let { id -> providers.firstOrNull { it.id == id } }
            ?: providers.firstOrNull { it.enabled && it.models.isNotEmpty() }
            ?: throw IllegalStateException("未配置供应商")
        if (provider.apiKey.isBlank()) throw IllegalStateException("未填写 API Key")
        val model = settings.activeModelId ?: provider.models.firstOrNull()?.id
        if (model.isNullOrBlank()) throw IllegalStateException("未选择模型")

        val courses = if (settings.timetableEnabled) {
            Timetable.decode(settings.timetableJson)
        } else {
            emptyList()
        }

        val arr = JSONArray()
        arr.put(system("【当前真实时间】" + Timetable.describe(courses)))
        if (settings.systemPrompt.isNotBlank()) arr.put(system(settings.systemPrompt))
        arr.put(system(Persona.block(settings.gender)))
        arr.put(system(RULES))

        val convo = ChatStore(context).list().firstOrNull()
        val history = convo?.messages
            ?.filter { !it.error && it.text.isNotBlank() && !it.pet }
            ?.takeLast(8)
            .orEmpty()
        for (m in history) {
            val text = InnerVoice.bodyOnly(m.text).take(500)
            if (text.isBlank()) continue
            arr.put(
                JSONObject()
                    .put("role", if (m.role == "user") "user" else "assistant")
                    .put("content", text)
            )
        }
        arr.put(JSONObject().put("role", "user").put("content", "（该你主动开口了，一句话。）"))

        val sb = StringBuilder()
        ApiClient().streamChat(
            base = provider.baseUrl,
            key = provider.apiKey,
            model = model,
            messages = arr,
            temperature = 0.9,
            onDelta = { sb.append(it) }
        )
        return clean(sb.toString())
    }

    private fun system(text: String): JSONObject =
        JSONObject().put("role", "system").put("content", text)

    private fun clean(raw: String): String {
        val noVoice = raw.replace(Regex("〔[^〕]*〕"), " ").trim()
        val line = noVoice.split('\n').firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val unquoted = line.trim('「', '」', '"', '\'', '“', '”', ' ', '…')
        if (unquoted.isBlank()) return ""
        // 只保留第一句
        val end = Regex("[。！？!?]").find(unquoted)
        val sentence = if (end != null) unquoted.substring(0, end.range.last + 1) else unquoted
        return sentence.take(45).trim()
    }
}