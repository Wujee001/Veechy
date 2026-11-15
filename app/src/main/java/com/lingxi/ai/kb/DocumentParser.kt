package com.lingxi.ai.kb

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.zip.ZipInputStream

data class ParsedDoc(val name: String, val kind: String, val text: String)

/**
 * 本地文档解析：PDF / DOCX / PPTX / TXT 等。
 * 纯文本格式直接读；Office 系按 zip + xml 提取；PDF 用 PDFBox-Android。
 */
object DocumentParser {

    @Volatile
    private var pdfReady = false

    fun parse(context: Context, uri: Uri): ParsedDoc {
        val name = queryName(context, uri) ?: "未命名文档"
        val ext = name.substringAfterLast('.', "").lowercase()

        val text = when (ext) {
            "pdf" -> readPdf(context, uri)
            "docx", "docm" -> readDocx(context, uri)
            "pptx", "pptm" -> readPptx(context, uri)
            "txt", "md", "markdown", "csv", "json", "log" -> readPlain(context, uri)
            else -> {
                // 无法从扩展名判断时按内容嗅探
                val zip = readPlain(context, uri, limit = 4)
                when {
                    zip.startsWith("PK") -> readOfficeBySniff(context, uri)
                    else -> readPlain(context, uri)
                }
            }
        }.let(::cleanup)

        if (text.isBlank()) {
            throw IllegalStateException("没有提取到文本，可能是扫描版或加密文件")
        }
        return ParsedDoc(name, ext.ifBlank { "txt" }, text)
    }

    // ---------------------------------------------------------------- PDF

    private fun ensurePdf(context: Context) {
        if (!pdfReady) {
            synchronized(this) {
                if (!pdfReady) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    pdfReady = true
                }
            }
        }
    }

    private fun readPdf(context: Context, uri: Uri): String {
        ensurePdf(context)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开文件" }
            PDDocument.load(input).use { doc ->
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                return stripper.getText(doc)
            }
        }
    }

    // ---------------------------------------------------------------- DOCX

    private fun readDocx(context: Context, uri: Uri): String {
        val xml = readZipEntry(context, uri, "word/document.xml") ?: return ""
        val spaced = xml
            .replace("</w:p>", "\n")
            .replace("<w:br/>", "\n")
            .replace("<w:tab/>", "\t")
            .replace("</w:tc>", "\t")
        return stripXml(spaced)
    }

    // ---------------------------------------------------------------- PPTX

    private fun readPptx(context: Context, uri: Uri): String {
        val slides = ArrayList<Pair<Int, String>>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val n = entry.name
                    if (n.startsWith("ppt/slides/slide") && n.endsWith(".xml")) {
                        val index = n.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0
                        val xml = zip.readBytes().toString(Charsets.UTF_8)
                        slides.add(index to extractAText(xml))
                    } else if (n.startsWith("ppt/notesSlides/notesSlide") && n.endsWith(".xml")) {
                        val index = n.removePrefix("ppt/notesSlides/notesSlide").removeSuffix(".xml").toIntOrNull() ?: 0
                        val xml = zip.readBytes().toString(Charsets.UTF_8)
                        slides.add(index to extractAText(xml))
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return slides.sortedBy { it.first }
            .joinToString("\n\n") { (i, t) -> if (t.isBlank()) "" else "—— 第 ${i} 页 ——\n$t" }
    }

    private fun extractAText(xml: String): String {
        val sb = StringBuilder()
        val regex = Regex("<a:t>(.*?)</a:t>", RegexOption.DOT_MATCHES_ALL)
        regex.findAll(xml).forEach { sb.append(it.groupValues[1]).append('\n') }
        return unescape(sb.toString())
    }

    // ---------------------------------------------------------------- 通用

    private fun readOfficeBySniff(context: Context, uri: Uri): String =
        readDocx(context, uri).ifBlank { readPptx(context, uri) }

    private fun readPlain(context: Context, uri: Uri, limit: Int = Int.MAX_VALUE): String =
        context.contentResolver.openInputStream(uri)?.use {
            val bytes = it.readBytes()
            String(bytes, 0, minOf(bytes.size, limit), Charsets.UTF_8)
        } ?: ""

    private fun readZipEntry(context: Context, uri: Uri, entryName: String): String? {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == entryName) {
                        return zip.readBytes().toString(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }

    private fun stripXml(s: String): String = unescape(s.replace(Regex("<[^>]+>"), ""))

    private fun unescape(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#10;", "\n")
        .replace("&amp;", "&")

    private fun cleanup(s: String): String = s
        .replace('\u0000', ' ')
        .replace("\r\n", "\n")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun queryName(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return c.getString(idx)
                    }
                }
        }
        return uri.lastPathSegment
    }
}
