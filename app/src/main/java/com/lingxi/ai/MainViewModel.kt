package com.lingxi.ai

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lingxi.ai.audio.TtsPlayer
import com.lingxi.ai.data.ChatMessage
import com.lingxi.ai.data.ChatStore
import com.lingxi.ai.data.Conversation
import com.lingxi.ai.data.InnerVoice
import com.lingxi.ai.data.Course
import com.lingxi.ai.data.Persona
import com.lingxi.ai.data.Provider
import com.lingxi.ai.data.Timetable
import com.lingxi.ai.data.SettingsStore
import com.lingxi.ai.media.MediaUtil
import com.lingxi.ai.kb.DocumentParser
import com.lingxi.ai.kb.IsbnLookup
import com.lingxi.ai.kb.KbDoc
import com.lingxi.ai.kb.KbHit
import com.lingxi.ai.kb.KnowledgeStore
import com.lingxi.ai.net.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val chatStore = ChatStore(app)
    private val api = ApiClient()

    val tts = TtsPlayer(app)
    val kb = KnowledgeStore(app)

    var kbDocs by mutableStateOf(kb.docs)
        private set
    var kbBusy by mutableStateOf<String?>(null)
        private set
    var kbEnabled by mutableStateOf(settings.kbEnabled)
        private set
    var bookPreview by mutableStateOf<IsbnLookup.BookInfo?>(null)
        private set

    var onboarded by mutableStateOf(settings.onboarded)
        private set

    var courses by mutableStateOf(Timetable.decode(settings.timetableJson))
        private set
    var timetableRules by mutableStateOf(settings.timetableRules)
        private set
    var ttBusy by mutableStateOf(false)
        private set

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
        tts.updateRate(settings.speechRate)
        val list = conversations
        if (list.isEmpty()) {
            createConversation()
        } else {
            current = list.first()
        }
        if (settings.onboarded) loadGift()
    }

    // ------------------------------------------------------------ 首启引导

    fun completeOnboarding(profession: String, gender: String) {
        val p = profession.trim()
        val g = gender.trim()
        settings.profession = p
        settings.gender = g
        val prompt = Persona.defaultSystemPrompt(p)
        settings.systemPrompt = prompt
        systemPrompt = prompt
        settings.onboarded = true
        onboarded = true
        loadGift()
    }

    /**
     * 重新填写资料。性别决定隐藏人设，改了等于换人，因此清空全部对话；
     * 课表、知识库、API 供应商保持不动。
     */
    fun resetProfile() {
        chatStore.clearAll()
        conversations = emptyList()
        current = null
        createConversation()
        settings.onboarded = false
        onboarded = false
        status = "对话已清空，请重新填写资料"
    }

    /** 内置见面礼：四六级写作高级提分词汇。只装一次。 */
    private fun loadGift() {
        if (settings.giftLoaded) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            kbBusy = "正在准备见面礼…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val parsed = DocumentParser.parseAsset(ctx, "gift_cet4_writing.pdf")
                    kb.add(parsed.name, parsed.kind, "内置赠礼", parsed.text)
                    parsed
                }
            }
            kbBusy = null
            result.onSuccess {
                kbDocs = kb.docs
                settings.giftLoaded = true
                status = "已经放好见面礼：${it.name}"
            }.onFailure {
                status = "见面礼导入失败：${it.message}"
            }
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

        val history = convo.messages + userMsg

        streaming = true
        job = viewModelScope.launch {
            var acc = ""
            try {
                val hits = if (kbEnabled) {
                    withContext(Dispatchers.IO) { kb.search(text, 3) }
                } else {
                    emptyList()
                }
                val payload = buildPayload(
                    history = history,
                    system = systemPrompt,
                    turns = contextTurns,
                    kbHits = hits
                )
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
                    // 思考过程不展示，也不落库
                    enableSearch = provider.search
                )
                if (acc.isBlank()) {
                    patchLast { it.copy(text = "（模型没有返回内容）") }
                } else if (speak || autoSpeak) {
                    // 朗读只念正文，跳过心理活动
                    tts.speak(InnerVoice.bodyOnly(acc))
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

    private fun buildPayload(
        history: List<ChatMessage>,
        system: String,
        turns: Int,
        kbHits: List<KbHit> = emptyList()
    ): JSONArray {
        val arr = JSONArray()
        // 用户自己在设置里写的提示词优先
        if (system.isNotBlank()) {
            arr.put(JSONObject().put("role", "system").put("content", system))
        }
        // 隐藏人设：只在请求里注入，界面上不暴露
        arr.put(JSONObject().put("role", "system").put("content", Persona.block(settings.gender)))
        // 真实时间与课表，供自然带出，不要求逐条播报
        arr.put(
            JSONObject().put("role", "system").put(
                "content",
                "【现实信息】" + Timetable.describe(courses) +
                    " 这些是真实的时间和课程安排。问到某天上什么课，直接按上面的完整课表回答；" +
                    "其他时候需要就自然带出来（比如提醒上课、问一句吃没吃），" +
                    "不要像播报一样逐条念，也不要每句话都提时间。"
            )
        )
        if (kbHits.isNotEmpty()) {
            val ctx = kbHits.joinToString("\n\n") { "[资料 · ${it.docName}]\n${it.text}" }
            arr.put(
                JSONObject().put("role", "system").put(
                    "content",
                    "以下是从用户本地知识库检索到的资料片段。回答时优先依据这些内容；" +
                        "资料不足时明确说明，不要编造。引用时注明来源文件名。\n\n$ctx"
                )
            )
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

    // ------------------------------------------------------------ 知识库

    fun updateKbEnabled(value: Boolean) {
        kbEnabled = value
        settings.kbEnabled = value
    }

    fun importDocument(uri: Uri) {
        if (kbBusy != null) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            kbBusy = "解析中…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val parsed = DocumentParser.parse(ctx, uri)
                    kb.add(parsed.name, parsed.kind, "本地文件", parsed.text)
                    parsed
                }
            }
            kbBusy = null
            result.onSuccess { d ->
                kbDocs = kb.docs
                status = "已存入知识库：${d.name}（${d.text.length} 字）"
            }.onFailure {
                status = "导入失败：${it.message}"
            }
        }
    }

    fun removeKnowledge(id: String) {
        kb.remove(id)
        kbDocs = kb.docs
        status = "已从知识库删除"
    }

    fun lookupIsbn(raw: String) {
        val isbn = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
        if (isbn.length != 10 && isbn.length != 13) {
            status = "ISBN 应为 10 位或 13 位，你输入的是 ${isbn.length} 位"
            return
        }
        viewModelScope.launch {
            // 1. 先试公开书目接口
            kbBusy = "查询书目…"
            val online = runCatching { IsbnLookup.lookup(isbn) }
            if (online.isSuccess) {
                kbBusy = null
                bookPreview = online.getOrNull()
                return@launch
            }

            // 2. 在线源不可用 → 交给模型（配合 enable_search 就是联网检索）
            val provider = activeProvider
            val model = activeModelId ?: provider?.models?.firstOrNull()?.id
            if (provider == null || model.isNullOrBlank()) {
                kbBusy = null
                status = "在线书目源不可用（${online.exceptionOrNull()?.message}），且未配置模型"
                return@launch
            }

            kbBusy = if (provider.search) "在线源不可用，改用模型联网检索…" else "在线源不可用，改用模型推断…"
            val inferred = runCatching { modelLookup(provider, model, isbn) }
            kbBusy = null
            inferred.onSuccess { bookPreview = it }
                .onFailure { status = "查询失败：${it.message}" }
        }
    }

    /** 让当前模型按固定 JSON 结构给出书目信息。 */
    private suspend fun modelLookup(
        provider: Provider,
        model: String,
        isbn: String
    ): IsbnLookup.BookInfo {
        val messages = JSONArray().apply {
            put(
                JSONObject().put("role", "system").put(
                    "content",
                    "你是图书信息助手。根据 ISBN 输出这本书的信息，只输出一个 JSON 对象，" +
                        "不要解释、不要代码块标记。字段：title、authors、publisher、year、" +
                        "pages、subjects、description。不确定的字段用空字符串，禁止编造。"
                )
            )
            put(JSONObject().put("role", "user").put("content", "ISBN: $isbn"))
        }

        val raw = api.streamChat(
            base = provider.baseUrl,
            key = provider.apiKey,
            model = model,
            messages = messages,
            temperature = 0.2,
            onDelta = {},
            enableSearch = provider.search
        )

        val body = raw.substringAfter('{', "").substringBeforeLast('}', "")
        if (body.isBlank()) throw IllegalStateException("模型没有返回可解析的内容")
        val o = runCatching { JSONObject("{$body}") }
            .getOrElse { throw IllegalStateException("模型返回的不是 JSON") }

        val title = o.optString("title").trim()
        if (title.isBlank()) throw IllegalStateException("模型也查不到这本书")

        return IsbnLookup.BookInfo(
            isbn = isbn,
            title = title,
            authors = o.optString("authors"),
            publisher = o.optString("publisher"),
            year = o.optString("year"),
            pages = o.optString("pages"),
            subjects = o.optString("subjects"),
            description = o.optString("description"),
            link = "",
            origin = "model"
        )
    }

    fun saveBookToKb(info: IsbnLookup.BookInfo) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { kb.add(info.title, "book", "ISBN ${info.isbn}", info.toKbText()) }
            }
            result.onSuccess {
                kbDocs = kb.docs
                bookPreview = null
                status = "已存入知识库：${it.name}"
            }.onFailure { status = "保存失败：${it.message}" }
        }
    }

    fun clearBookPreview() {
        bookPreview = null
    }

    // ------------------------------------------------------------ 语音转写兜底

    var sttBusy by mutableStateOf(false)
        private set

    /** 录音文件走服务端 /audio/transcriptions 转写后直接发送。 */
    fun transcribeAndSend(file: File) {
        val provider = activeProvider
        if (provider == null) {
            status = "请先在设置里添加 API 供应商"
            return
        }
        if (provider.apiKey.isBlank()) {
            status = "API Key 未填写"
            return
        }
        if (sttBusy) return

        viewModelScope.launch {
            sttBusy = true
            val result = runCatching { api.transcribe(provider.baseUrl, provider.apiKey, file) }
            sttBusy = false
            runCatching { file.delete() }
            result.onSuccess { text ->
                if (text.isBlank()) status = "没听清，再说一次" else send(text, speak = true)
            }.onFailure {
                status = "转写失败：${it.message}（当前服务可能不支持 /audio/transcriptions，可换回系统识别）"
            }
        }
    }

    // ------------------------------------------------------------ 课表

    fun updateTimetableRules(v: String) {
        timetableRules = v
        settings.timetableRules = v
    }

    fun clearTimetable() {
        courses = emptyList()
        settings.timetableJson = ""
        status = "课表已清空"
    }

    fun parseTimetable(imagePath: String?) {
        val provider = activeProvider
        if (provider == null) {
            status = "请先在设置里添加 API 供应商"
            return
        }
        if (provider.apiKey.isBlank()) {
            status = "API Key 未填写"
            return
        }
        val model = activeModelId ?: provider.models.firstOrNull()?.id
        if (model.isNullOrBlank()) {
            status = "请先同步并选择模型"
            return
        }
        if (imagePath == null && timetableRules.isBlank()) {
            status = "请先选择课表截图"
            return
        }
        if (ttBusy) return

        viewModelScope.launch {
            ttBusy = true
            val result = runCatching {
                val messages = timetableMessages(imagePath)
                val raw = api.streamChat(
                    base = provider.baseUrl,
                    key = provider.apiKey,
                    model = model,
                    messages = messages,
                    temperature = 0.1,
                    onDelta = {}
                )
                Timetable.parse(raw)
            }
            ttBusy = false
            result.onSuccess { list ->
                courses = list
                settings.timetableJson = Timetable.encode(list)
                status = "课表已保存：${list.size} 节课"
            }.onFailure {
                status = "课表解析失败：${it.message}（需要选一个支持图片的模型）"
            }
        }
    }

    private fun timetableMessages(imagePath: String?): JSONArray {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", TT_PROMPT))

        val parts = JSONArray()
        val text = buildString {
            append("请解析这张课表。")
            if (timetableRules.isNotBlank()) {
                append("\n\n补充规则：\n")
                append(timetableRules)
            }
        }
        parts.put(JSONObject().put("type", "text").put("text", text))

        imagePath?.let { path ->
            val f = File(path)
            if (f.exists()) {
                parts.put(
                    JSONObject().put("type", "image_url")
                        .put("image_url", JSONObject().put("url", MediaUtil.toDataUrl(f)))
                )
            }
        }
        arr.put(JSONObject().put("role", "user").put("content", parts))
        return arr
    }

    // ------------------------------------------------------------ 设置写入

    fun updateTemperature(v: Float) {
        temperature = v
        settings.temperature = v
    }

    fun updateSystemPrompt(v: String) {
        systemPrompt = v
        settings.systemPrompt = v
    }

    fun updateContextTurns(v: Int) {
        contextTurns = v
        settings.contextTurns = v
    }

    fun updateAutoSpeak(v: Boolean) {
        autoSpeak = v
        settings.autoSpeak = v
    }

    fun updateSpeechRate(v: Float) {
        settings.speechRate = v
        tts.updateRate(v)
    }

    private companion object {
        const val TT_PROMPT =
            "你是课表解析助手。根据用户提供的课表图片和补充规则，输出一个 JSON 数组，" +
                "不要任何解释文字，不要代码块标记。每个元素字段：" +
                "{\"weekday\":1到7的整数(周一=1,周日=7),\"start\":\"HH:MM\",\"end\":\"HH:MM\"," +
                "\"name\":\"课程名\",\"place\":\"上课地点\",\"teacher\":\"教师\",\"weeks\":\"周次\"}。" +
                "把图片里的节次按补充规则换算成具体起止时间；同一门课一周多节的拆成多条；" +
                "地点或教师看不清就留空字符串。"
    }
}
