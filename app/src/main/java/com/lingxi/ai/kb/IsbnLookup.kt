package com.lingxi.ai.kb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ISBN → 书目元数据（标题、作者、出版社、年份、页数、分类、简介、链接）。
 *
 * 只取公开的书目接口（Google Books / Open Library），不抓取书籍正文。
 */
object IsbnLookup {

    data class BookInfo(
        val isbn: String,
        val title: String,
        val authors: String,
        val publisher: String,
        val year: String,
        val pages: String,
        val subjects: String,
        val description: String,
        val link: String
    ) {
        fun toKbText(): String = buildString {
            appendLine("书名：$title")
            if (authors.isNotBlank()) appendLine("作者：$authors")
            if (publisher.isNotBlank()) appendLine("出版社：$publisher")
            if (year.isNotBlank()) appendLine("出版年：$year")
            if (pages.isNotBlank()) appendLine("页数：$pages")
            if (subjects.isNotBlank()) appendLine("分类：$subjects")
            appendLine("ISBN：$isbn")
            if (link.isNotBlank()) appendLine("书目页：$link")
            if (description.isNotBlank()) {
                appendLine()
                appendLine("简介：")
                append(description)
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun normalize(raw: String): String =
        raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()

    suspend fun lookup(raw: String): BookInfo = withContext(Dispatchers.IO) {
        val isbn = normalize(raw)
        if (isbn.length != 10 && isbn.length != 13) {
            throw IOException("ISBN 应为 10 位或 13 位")
        }
        runCatching { google(isbn) }.getOrNull()
            ?: runCatching { openLibrary(isbn) }.getOrNull()
            ?: throw IOException("没有查到这本书")
    }

    // ---------------------------------------------------------------- Google Books

    private fun google(isbn: String): BookInfo? {
        val req = Request.Builder()
            .url("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = JSONObject(body)
            val items = json.optJSONArray("items") ?: return null
            if (items.length() == 0) return null
            val v = items.getJSONObject(0).optJSONObject("volumeInfo") ?: return null

            val authors = v.optJSONArray("authors")?.let { a ->
                (0 until a.length()).joinToString("、") { a.optString(it) }
            }.orEmpty()
            val cats = v.optJSONArray("categories")?.let { a ->
                (0 until a.length()).joinToString("、") { a.optString(it) }
            }.orEmpty()

            return BookInfo(
                isbn = isbn,
                title = v.optString("title"),
                authors = authors,
                publisher = v.optString("publisher"),
                year = v.optString("publishedDate"),
                pages = v.optInt("pageCount", 0).takeIf { it > 0 }?.toString().orEmpty(),
                subjects = cats,
                description = v.optString("description"),
                link = v.optString("infoLink")
            )
        }
    }

    // ---------------------------------------------------------------- Open Library

    private fun openLibrary(isbn: String): BookInfo? {
        val req = Request.Builder()
            .url("https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val root = JSONObject(body)
            val v = root.optJSONObject("ISBN:$isbn") ?: return null

            fun names(key: String): String {
                val arr = v.optJSONArray(key) ?: return ""
                return (0 until arr.length()).joinToString("、") {
                    arr.getJSONObject(it).optString("name")
                }
            }

            return BookInfo(
                isbn = isbn,
                title = v.optString("title"),
                authors = names("authors"),
                publisher = names("publishers"),
                year = v.optString("publish_date"),
                pages = v.optString("number_of_pages").let { if (it == "0") "" else it },
                subjects = names("subjects"),
                description = v.optString("notes"),
                link = v.optString("url")
            )
        }
    }
}
