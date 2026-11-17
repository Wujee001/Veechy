package com.lingxi.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// ---------------------------------------------------------------- 模型 / 供应商

data class ModelInfo(
    val id: String,
    val ownedBy: String = "",
    val vision: Boolean = false,
    val audio: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("ownedBy", ownedBy)
        put("vision", vision)
        put("audio", audio)
    }

    companion object {
        fun fromJson(o: JSONObject) = ModelInfo(
            id = o.optString("id"),
            ownedBy = o.optString("ownedBy"),
            vision = o.optBoolean("vision"),
            audio = o.optBoolean("audio")
        )

        /** 依据常见命名推断能力，仅用于界面标注。 */
        fun guessVision(id: String): Boolean {
            val s = id.lowercase()
            return listOf(
                "vl", "vision", "4o", "omni", "4.1", "gemini", "claude-3", "claude-4",
                "gpt-4-turbo", "glm-4v", "llava", "pixtral", "qvq", "internvl", "video"
            ).any { s.contains(it) }
        }

        fun guessAudio(id: String): Boolean {
            val s = id.lowercase()
            return listOf("omni", "audio", "realtime", "speech", "tts", "asr").any { s.contains(it) }
        }
    }
}

data class Provider(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val enabled: Boolean = true,
    val search: Boolean = false,
    val models: List<ModelInfo> = emptyList(),
    val lastSync: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("baseUrl", baseUrl)
        put("apiKey", apiKey)
        put("enabled", enabled)
        put("search", search)
        put("lastSync", lastSync)
        put("models", JSONArray().also { a -> models.forEach { a.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Provider {
            val arr = o.optJSONArray("models") ?: JSONArray()
            val list = ArrayList<ModelInfo>()
            for (i in 0 until arr.length()) list.add(ModelInfo.fromJson(arr.getJSONObject(i)))
            return Provider(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.optString("name"),
                baseUrl = o.optString("baseUrl"),
                apiKey = o.optString("apiKey"),
                enabled = o.optBoolean("enabled", true),
                search = o.optBoolean("search", false),
                models = list,
                lastSync = o.optLong("lastSync")
            )
        }
    }
}

// ---------------------------------------------------------------- 消息 / 会话

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String = "",
    val images: List<String> = emptyList(),
    val reasoning: String? = null,
    val error: Boolean = false,
    val time: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role)
        put("text", text)
        put("images", JSONArray().also { a -> images.forEach { a.put(it) } })
        put("reasoning", reasoning ?: "")
        put("error", error)
        put("time", time)
    }

    companion object {
        fun fromJson(o: JSONObject): ChatMessage {
            val list = ArrayList<String>()
            o.optJSONArray("images")?.let { a ->
                for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let(list::add)
            }
            // 兼容 1.5.x 之前的单图字段
            if (list.isEmpty()) {
                o.optString("imagePath").takeIf { it.isNotBlank() }?.let(list::add)
            }
            return ChatMessage(
                id = o.optString("id", UUID.randomUUID().toString()),
                role = o.optString("role", "user"),
                text = o.optString("text"),
                images = list,
                reasoning = o.optString("reasoning").ifBlank { null },
                error = o.optBoolean("error"),
                time = o.optLong("time", System.currentTimeMillis())
            )
        }
    }
}

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("updatedAt", updatedAt)
        put("messages", JSONArray().also { a -> messages.forEach { a.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Conversation {
            val arr = o.optJSONArray("messages") ?: JSONArray()
            val list = ArrayList<ChatMessage>()
            for (i in 0 until arr.length()) list.add(ChatMessage.fromJson(arr.getJSONObject(i)))
            return Conversation(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "新对话"),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                messages = list
            )
        }
    }
}

// ---------------------------------------------------------------- 设置存储

class SettingsStore(context: Context) {

    private val sp = context.getSharedPreferences("lingxi_settings", Context.MODE_PRIVATE)

    var activeProviderId: String?
        get() = sp.getString(KEY_PROVIDER, null)
        set(value) = sp.edit().putString(KEY_PROVIDER, value).apply()

    var activeModelId: String?
        get() = sp.getString(KEY_MODEL, null)
        set(value) = sp.edit().putString(KEY_MODEL, value).apply()

    var temperature: Float
        get() = sp.getFloat(KEY_TEMP, 0.7f)
        set(value) = sp.edit().putFloat(KEY_TEMP, value).apply()

    var systemPrompt: String
        get() = sp.getString(KEY_SYS, DEFAULT_SYSTEM) ?: DEFAULT_SYSTEM
        set(value) = sp.edit().putString(KEY_SYS, value).apply()

    var contextTurns: Int
        get() = sp.getInt(KEY_TURNS, 12)
        set(value) = sp.edit().putInt(KEY_TURNS, value).apply()

    var autoSpeak: Boolean
        get() = sp.getBoolean(KEY_AUTOSPEAK, true)
        set(value) = sp.edit().putBoolean(KEY_AUTOSPEAK, value).apply()

    var speechRate: Float
        get() = sp.getFloat(KEY_RATE, 1.0f)
        set(value) = sp.edit().putFloat(KEY_RATE, value).apply()

    var kbEnabled: Boolean
        get() = sp.getBoolean(KEY_KB, true)
        set(value) = sp.edit().putBoolean(KEY_KB, value).apply()

    var onboarded: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var profession: String
        get() = sp.getString(KEY_PROFESSION, "") ?: ""
        set(value) = sp.edit().putString(KEY_PROFESSION, value).apply()

    var gender: String
        get() = sp.getString(KEY_GENDER, "") ?: ""
        set(value) = sp.edit().putString(KEY_GENDER, value).apply()

    var giftLoaded: Boolean
        get() = sp.getBoolean(KEY_GIFT, false)
        set(value) = sp.edit().putBoolean(KEY_GIFT, value).apply()

    var timetableJson: String
        get() = sp.getString(KEY_TT, "") ?: ""
        set(value) = sp.edit().putString(KEY_TT, value).apply()

    var timetableRules: String
        get() = sp.getString(KEY_TT_RULES, "") ?: ""
        set(value) = sp.edit().putString(KEY_TT_RULES, value).apply()

    var nightMode: Boolean
        get() = sp.getBoolean(KEY_NIGHT, false)
        set(value) = sp.edit().putBoolean(KEY_NIGHT, value).apply()

    fun loadProviders(): List<Provider> {
        val raw = sp.getString(KEY_PROVIDERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<Provider>()
            for (i in 0 until arr.length()) list.add(Provider.fromJson(arr.getJSONObject(i)))
            list
        }.getOrDefault(emptyList())
    }

    fun saveProviders(list: List<Provider>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString(KEY_PROVIDERS, arr.toString()).apply()
    }

    companion object {
        const val DEFAULT_SYSTEM = "你是一个简洁、可靠的中文助手。回答直给结论，不啰嗦。"

        private const val KEY_PROVIDERS = "providers"
        private const val KEY_PROVIDER = "active_provider"
        private const val KEY_MODEL = "active_model"
        private const val KEY_TEMP = "temperature"
        private const val KEY_SYS = "system_prompt"
        private const val KEY_TURNS = "context_turns"
        private const val KEY_AUTOSPEAK = "auto_speak"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_KB = "kb_enabled"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_PROFESSION = "profession"
        private const val KEY_GENDER = "gender"
        private const val KEY_GIFT = "gift_loaded"
        private const val KEY_TT = "timetable_json"
        private const val KEY_TT_RULES = "timetable_rules"
        private const val KEY_NIGHT = "night_mode"
    }
}

// ---------------------------------------------------------------- 会话存储

class ChatStore(context: Context) {

    private val dir = File(context.filesDir, "chats").apply { mkdirs() }

    fun list(): List<Conversation> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { f ->
            runCatching { Conversation.fromJson(JSONObject(f.readText())) }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    fun load(id: String): Conversation? {
        val f = File(dir, "$id.json")
        if (!f.exists()) return null
        return runCatching { Conversation.fromJson(JSONObject(f.readText())) }.getOrNull()
    }

    fun save(c: Conversation) {
        runCatching { File(dir, "${c.id}.json").writeText(c.toJson().toString()) }
    }

    fun delete(id: String) {
        runCatching { File(dir, "$id.json").delete() }
    }

    fun clearAll() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }
}
