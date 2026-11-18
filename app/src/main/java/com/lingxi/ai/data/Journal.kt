package com.lingxi.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class JournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val images: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("images", JSONArray().also { a -> images.forEach { a.put(it) } })
    }

    companion object {
        fun fromJson(o: JSONObject): JournalEntry {
            val list = ArrayList<String>()
            o.optJSONArray("images")?.let { a ->
                for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let(list::add)
            }
            return JournalEntry(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title"),
                body = o.optString("body"),
                images = list,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

/** 手帐：一条一个 JSON 文件，图片存 filesDir/images。 */
class JournalStore(context: Context) {

    private val dir = File(context.filesDir, "journal").apply { mkdirs() }

    fun list(): List<JournalEntry> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { f ->
            runCatching { JournalEntry.fromJson(JSONObject(f.readText())) }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    fun save(entry: JournalEntry): JournalEntry {
        val stamped = entry.copy(updatedAt = System.currentTimeMillis())
        runCatching { File(dir, "${stamped.id}.json").writeText(stamped.toJson().toString()) }
        return stamped
    }

    fun delete(id: String) {
        runCatching { File(dir, "$id.json").delete() }
    }
}