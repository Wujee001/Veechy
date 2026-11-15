package com.lingxi.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.ai.audio.TtsPlayer
import com.lingxi.ai.data.ChatMessage
import com.lingxi.ai.data.ChatStore
import com.lingxi.ai.data.Conversation
import com.lingxi.ai.data.Provider
import com.lingxi.ai.data.SettingsStore
import com.lingxi.ai.media.MediaUtil
import com.lingxi.ai.net.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val chatStore = ChatStore(app)
    private val api = ApiClient()

    val tts = TtsPlayer(app)

    var providers by mutableStateOf(settings.loadProviders())
        private set
    var activeProviderId by mutableStateOf(settings.activeProviderId)
        private set
    var activeModelId by mutableStateOf(settings.activeModelId)
        private set

    var temperature by mutableStateOf(settings.temperature)
    var systemPrompt by mutableStateOf(settings.systemPrompt)
    var contextTurns by mutableStateOf(settings.contextTurns)
    var autoSpeak by mutableStateOf(settings.autoSpeak)

    var conversations by mutableStateOf(chatStore.list())
        private set
    var current by mutableStateOf<Conversation?>(null)
        private set

    var streaming by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var busyHint by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    val messages: List<ChatMessage> get() = current?.messages.orEmpty()

    val activeProvider: Provider?
        get() {
            activeProviderId?.let { id -> providers.firstOrNull { it.id == id }?.let { return it } }
            return providers.firstOrNull { it.enabled && it.models.isNotEmpty() }
        }

    val activeModelLabel: String
        get() {
            val p = activeProvider ?: return "未配置模型"
            val m = activeModelId ?: p.models.firstOrNull()?.id ?: return p.name
            return m
        }

    init {
        tts.setRate(settings.speechRate)
        val list = conversations
        if (list.isEmpty()) {
            createConversation()
        } else {
            current = list.first()
        }
    }

    override fun onCleared() {
        job?.cancel()
        tts.shutdown()
        super.onCleared()
    }

    // ------------------------------------------------------------ 提示

    fun consumeStatus() {
        status = null
    }

    fun say(text: String) {
        status = text
    }

    // ------------------------------------------------------------ 会话

    fun createConversation() {
        val c = Conversation()
        chatStore.save(c)
        conversations = chatStore.list()
        current = c
    }

    fun openConversation(id: String) {
        val c = chatStore.load(id) ?: return
        current = c
    }

    fun deleteConversation(id: String) {
        chatStore.delete(id)
        conversations = chatStore.list()
        if (current?.id == id) {
            current = conversations.firstOrNull()
            if (current == null) createConversation()
        }
    }

    fun renameCurrent(title: String) {
        val c = current ?: return
        val updated = c.copy(title = title)
        current = updated
        chatStore.save(updated)
        conversations = chatStore.list()
    }

    private fun push(updated: Conversation) {
        current = updated
        chatStore.save(updated)
        conversations = chatStore.list()
    }

    // ------------------------------------------------------------ 模型

    fun selectModel(providerId: String, modelId: String) {
        activeProviderId = providerId
        activeModelId = modelId
        settings.activeProviderId = providerId
        settings.activeModelId = modelId
    }

    // ------------------------------------------------------------ 供应商

    fun addProvider(name: String, baseUrl: String, apiKey: String) {
        val p = Provider(name = name, baseUrl = baseUrl.trim(), apiKey = apiKey.trim())
        providers = providers + p
        settings.saveProviders(providers)
        if (activeProviderId == null) {
            activeProviderId = p.id
            settings.activeProviderId = p.id
        }
    }

    fun updateProvider(updated: Provider) {
        providers = providers.map { if (it.id == updated.id) updated else it }
        settings.saveProviders(providers)
    }

    fun removeProvider(id: String) {
        providers = providers.filterNot { it.id == id }
        settings.saveProviders(providers)
        if (activeProviderId == id) {
            activeProviderId = providers.firstOrNull()?.id
            activeModelId = null
            settings.activeProviderId = activeProviderId
            settings.activeModelId = null
        }
    }

    fun testProvider(providerId: String) {
        val p = providers.firstOrNull { it.id == providerId } ?: return
        busyHint = "检测中…"
        viewModelScope.launch {
            val result = runCatching { api.test(p.baseUrl, p.apiKey) }
            busyHint = null
            status = result.fold(
                onSuccess = { "[${p.name}] $it" },
                onFailure = { "[${p.name}] 失败：${it.message}" }
            )
        }
    }

    fun syncModels(providerId: String) {
        val p = providers.firstOrNull { it.id == providerId } ?: return
        busyHint = "同步模型中…"
        viewModelScope.launch {
            val result = runCatching { api.fetchModels(p.baseUrl, p.apiKey) }
            busyHint = null
            result.onSuccess { list ->
                val updated = p.copy(models = list, lastSync = System.currentTimeMillis())
                updateProvider(updated)
                status = "[${p.name}] 已同步 ${list.size} 个模型"
                if (activeModelId.isNullOrBlank() && list.isNotEmpty()) {
                    selectModel(p.id, list.first().id)
                } else if (activeModelId !in list.map { it.id }) {
                    list.firstOrNull()?.let { selectModel(p.id, it.id) }
                }
            }.onFailure {
                status = "[${p.name}] 同步失败：${it.message}"
            }
        }
    }

    // ------------------------------------------------------------ 发送

    fun stopGenerating() {
        job?.cancel()
        job = null
        streaming = false
        tts.stop()
        current?.let { push(it) }
    }

    fun send(text: String, imagePath: String? = null, speak: Boolean = false) {
        if (streaming) return
        val provider = activeProvider
        if (provider == null) {
            status = "请先在设置里添加 API 供应商"
            return
        }
        if (provider.apiKey.isBlank()) {
            status = "${provider.name} 未填写 API Key"
            return
        }
        val model = activeModelId ?: provider.models.firstOrNull()?.id
        if (model.isNullOrBlank()) {
            status = "请先同步并选择一个模型"
            return
        }
        if (text.isBlank() && imagePath == null) return

        val convo = current ?: return
        val base = if (convo.messages.isEmpty()) text.take(18).ifBlank { "图片对话" } else convo.title
        val userMsg = ChatMessage(role = "user", text = text.trim(), imagePath = imagePath)
        val placeholder = ChatMessage(role = "assistant", text = "")

        val withUser = convo.copy(
            title = base,
            messages = convo.messages + userMsg + placeholder,
            updatedAt = System.currentTimeMillis()
        )
        push(withUser)

        val payload = buildPayload(
            history = convo.messages + userMsg,
            system = systemPrompt,
            turns = contextTurns
        )

        streaming = true
        job = viewModelScope.launch {
            var acc = ""
            var reason = ""
            try {
                api.streamChat(
                    base = provider.baseUrl,
                    key = provider.apiKey,
                    model = model,
                    messages = payload,
                    temperature = temperature.toDouble(),
                    onDelta = { d ->
                        acc += d
                        patchLast { it.copy(text = acc) }
                    },
                    onReasoning = { r ->
                        reason += r
                        patchLast { it.copy(reasoning = reason) }
                    }
                )
                if (acc.isBlank()) {
                    patchLast { it.copy(text = "（模型没有返回内容）") }
                } else if (speak || autoSpeak) {
                    tts.speak(acc)
                }
            } catch (e: CancellationException) {
                if (acc.isNotBlank()) patchLast { it.copy(text = acc) }
            } catch (e: Exception) {
                val msg = e.message ?: "请求失败"
                if (acc.isBlank()) {
                    patchLast { it.copy(text = msg, error = true) }
                } else {
                    patchLast { it.copy(text = acc + "\n\n[中断] " + msg) }
                }
                status = msg
            } finally {
                streaming = false
                current?.let { push(it) }
            }
        }
    }

    private fun patchLast(block: (ChatMessage) -> ChatMessage) {
        val c = current ?: return
        val list = c.messages.toMutableList()
        if (list.isEmpty()) return
        val last = list.lastIndex
        if (list[last].role != "assistant") return
        list[last] = block(list[last])
        current = c.copy(messages = list, updatedAt = System.currentTimeMillis())
    }

    private fun buildPayload(history: List<ChatMessage>, system: String, turns: Int): JSONArray {
        val arr = JSONArray()
        if (system.isNotBlank()) {
            arr.put(JSONObject().put("role", "system").put("content", system))
        }
        val recent = if (turns <= 0) history else history.takeLast(turns * 2)
        for (m in recent) {
            if (m.error) continue
            if (m.role == "assistant" && m.text.isBlank()) continue
            val obj = JSONObject().put("role", m.role)
            val img = m.imagePath?.let { File(it) }?.takeIf { it.exists() }
            if (img != null) {
                val parts = JSONArray()
                parts.put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", m.text.ifBlank { "描述这张图片" })
                )
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put("url", MediaUtil.toDataUrl(img))
                        )
                )
                obj.put("content", parts)
            } else if (m.text.isNotBlank()) {
                obj.put("content", m.text)
            } else {
                continue
            }
            arr.put(obj)
        }
        return arr
    }

    // ------------------------------------------------------------ 设置写入

    fun setTemperature(v: Float) {
        temperature = v
        settings.temperature = v
    }

    fun setSystemPrompt(v: String) {
        systemPrompt = v
        settings.systemPrompt = v
    }

    fun setContextTurns(v: Int) {
        contextTurns = v
        settings.contextTurns = v
    }

    fun setAutoSpeak(v: Boolean) {
        autoSpeak = v
        settings.autoSpeak = v
    }

    fun setSpeechRate(v: Float) {
        settings.speechRate = v
        tts.setRate(v)
    }
}
