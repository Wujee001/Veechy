package com.lingxi.ai.kb

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class KbDoc(
    val id: String,
    val name: String,
    val kind: String,
    val source: String,
    val addedAt: Long,
    val chars: Int,
    val chunkCount: Int
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("kind", kind)
        put("source", source)
        put("addedAt", addedAt)
        put("chars", chars)
        put("chunkCount", chunkCount)
    }

    companion object {
        fun fromJson(o: JSONObject) = KbDoc(
            id = o.optString("id"),
            name = o.optString("name"),
            kind = o.optString("kind"),
            source = o.optString("source"),
            addedAt = o.optLong("addedAt"),
            chars = o.optInt("chars"),
            chunkCount = o.optInt("chunkCount")
        )
    }
}

data class KbHit(val docName: String, val text: String, val score: Double)

/**
 * 本地知识库：文件内容分块后落在 filesDir/kb/{id}/chunks.json，索引存 index.json。
 * 只有用户主动删除才会消失。
 */
class KnowledgeStore(context: Context) {

    private val root = File(context.filesDir, "kb").apply { mkdirs() }
    private val indexFile = File(root, "index.json")
    private val cache = HashMap<String, List<String>>()

    var docs: List<KbDoc> = loadIndex()
        private set

    private fun loadIndex(): List<KbDoc> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            val list = ArrayList<KbDoc>()
            for (i in 0 until arr.length()) list.add(KbDoc.fromJson(arr.getJSONObject(i)))
            list.sortedByDescending { it.addedAt }
        }.getOrDefault(emptyList())
    }

    private fun saveIndex() {
        val arr = JSONArray()
        docs.forEach { arr.put(it.toJson()) }
        runCatching { indexFile.writeText(arr.toString()) }
    }

    fun add(name: String, kind: String, source: String, raw: String): KbDoc {
        val text = normalize(raw)
        require(text.isNotBlank()) { "没有提取到文本" }
        val chunks = split(text)
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        val arr = JSONArray()
        chunks.forEach { arr.put(it) }
        File(dir, "chunks.json").writeText(arr.toString())

        val doc = KbDoc(id, name, kind, source, System.currentTimeMillis(), text.length, chunks.size)
        docs = (docs + doc).sortedByDescending { it.addedAt }
        saveIndex()
        cache[id] = chunks
        return doc
    }

    fun remove(id: String) {
        cache.remove(id)
        runCatching { File(root, id).deleteRecursively() }
        docs = docs.filterNot { it.id == id }
        saveIndex()
    }

    fun clear() {
        cache.clear()
        runCatching { root.listFiles()?.forEach { it.deleteRecursively() } }
        docs = emptyList()
        saveIndex()
    }

    /** 基于字符 bigram 重叠的轻量检索，够用且不依赖向量模型。 */
    fun search(query: String, topK: Int = 3): List<KbHit> {
        if (docs.isEmpty() || query.isBlank()) return emptyList()
        val q = bigrams(query)
        if (q.isEmpty()) return emptyList()

        val hits = ArrayList<KbHit>()
        for (doc in docs) {
            val chunks = cache.getOrPut(doc.id) { loadChunks(doc.id) }
            for (c in chunks) {
                val score = score(q, c)
                if (score >= 0.12) hits.add(KbHit(doc.name, c, score))
            }
        }
        return hits.sortedByDescending { it.score }.take(topK)
    }

    private fun loadChunks(id: String): List<String> {
        val f = File(File(root, id), "chunks.json")
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            val list = ArrayList<String>()
            for (i in 0 until arr.length()) list.add(arr.optString(i))
            list
        }.getOrDefault(emptyList())
    }

    private fun score(query: Set<String>, chunk: String): Double {
        if (chunk.isBlank()) return 0.0
        val cb = bigrams(chunk)
        if (cb.isEmpty()) return 0.0
        var hit = 0
        for (g in query) if (g in cb) hit++
        return hit.toDouble() / query.size
    }

    private fun bigrams(s: String): Set<String> {
        val clean = s.lowercase().filter { it.isLetterOrDigit() }
        if (clean.length < 2) return if (clean.isEmpty()) emptySet() else setOf(clean)
        val out = HashSet<String>(clean.length)
        for (i in 0 until clean.length - 1) out.add(clean.substring(i, i + 2))
        return out
    }

    private fun normalize(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace(' ', ' ')
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun split(text: String, size: Int = 700, overlap: Int = 90): List<String> {
        if (text.length <= size) return listOf(text)
        val out = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + size, text.length)
            if (end < text.length) {
                // 尽量在句末断开
                val window = text.substring(start, end)
                val cut = maxOf(window.lastIndexOf('。'), window.lastIndexOf('\n'), window.lastIndexOf('.'))

                if (cut > size / 2) end = start + cut + 1
            }
            out.add(text.substring(start, end).trim())
            if (end >= text.length) break
            start = (end - overlap).coerceAtLeast(start + 1)
        }
        return out.filter { it.isNotBlank() }
    }
}
