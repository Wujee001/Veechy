package com.lingxi.ai.kb

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ParsedDoc(val name: String, val kind: String, val text: String)

/**
 * 本地文档解析：PDF / DOCX / PPTX / TXT 等。
 * 纯文本格式直接读；Office 系按 zip + xml 提取；PDF 用 PDFBox-Android。
 */
object DocumentParser {

    @Volatile
    private var pdfReady = false

    /** 只接受这些扩展名，其他一律拒绝，避免把图片/压缩包当文本读进来。 */
    private val ALLOWED = setOf(
        "pdf", "docx", "docm", "pptx", "pptm", "xlsx", "xlsm",
        "txt", "md", "markdown", "csv", "json", "log"
    )

    fun parse(context: Context, uri: Uri): ParsedDoc {
        val name = queryName(context, uri) ?: "未命名文档"
        val ext = name.substringAfterLast('.', "").lowercase()

        if (ext !in ALLOWED) {
            throw IllegalArgumentException(
                "不支持的文件类型${if (ext.isBlank()) "" else "：.$ext"}，只接受 PDF / DOCX / PPTX / TXT / MD / CSV"
            )
        }

        val text = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开文件" }
            readByExt(context, ext, input)
        }

        if (text.isBlank()) {
            throw IllegalStateException("没有提取到文本，可能是扫描版或加密文件")
        }
        return ParsedDoc(name, ext, text)
    }

    /** 读取 APK 内置 assets 里的文档（见面礼）。 */
    fun parseAsset(context: Context, assetName: String): ParsedDoc {
        val ext = assetName.substringAfterLast('.', "txt").lowercase()
        val text = context.assets.open(assetName).use { input ->
            readByExt(context, ext, input)
        }
        if (text.isBlank()) throw IllegalStateException("内置文档没有提取到文本")
        return ParsedDoc(assetName.substringBeforeLast('.', assetName), ext, text)
    }

    private fun readByExt(context: Context, ext: String, input: InputStream): String = cleanup(
        when (ext) {
            "pdf" -> readPdfStream(context, input)
            "docx", "docm" -> readDocxStream(input)
            "pptx", "pptm" -> readPptxStream(input)
            "xlsx", "xlsm" -> readXlsxStream(input)
            else -> {
                val raw = readTextStream(input)
                val bad = raw.count { it == '�' || (it < ' ' && it != '\n' && it != '\r' && it != '\t') }
                require(raw.isEmpty() || bad * 100 / raw.length < 2) { "这不是文本文件，内容无法读取" }
                raw
            }
        }
    )

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

    private fun readPdfStream(context: Context, input: InputStream): String {
        ensurePdf(context)
        PDDocument.load(input).use { doc ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            return stripper.getText(doc)
        }
    }

    // ---------------------------------------------------------------- DOCX

    private fun readDocxStream(input: InputStream): String {
        var xml: String? = null
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xml = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val raw = xml ?: return ""
        val spaced = raw
            .replace("</w:p>", "\n")
            .replace("<w:br/>", "\n")
            .replace("<w:tab/>", "\t")
            .replace("</w:tc>", "\t")
        return stripXml(spaced)
    }

    // ---------------------------------------------------------------- PPTX

    private fun readPptxStream(input: InputStream): String {
        val slides = ArrayList<Pair<Int, String>>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val n = entry.name
                if (n.startsWith("ppt/slides/slide") && n.endsWith(".xml")) {
                    val index = n.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0
                    slides.add(index to extractAText(zip.readBytes().toString(Charsets.UTF_8)))
                } else if (n.startsWith("ppt/notesSlides/notesSlide") && n.endsWith(".xml")) {
                    val index = n.removePrefix("ppt/notesSlides/notesSlide").removeSuffix(".xml").toIntOrNull() ?: 0
                    slides.add(index to extractAText(zip.readBytes().toString(Charsets.UTF_8)))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return slides.sortedBy { it.first }
            .joinToString("\n\n") { (i, t) -> if (t.isBlank()) "" else "—— 第 ${i} 页 ——\n$t" }
    }

    private fun extractAText(xml: String): String {
        val sb = StringBuilder()
        Regex("<a:t>(.*?)</a:t>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach {
            sb.append(it.groupValues[1]).append('\n')
        }
        return unescape(sb.toString())
    }

    // ---------------------------------------------------------------- XLSX

    private fun readXlsxStream(input: InputStream): String {
        val shared = ArrayList<String>()
        val sheets = ArrayList<Pair<String, String>>()

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" ->
                        shared.addAll(parseSharedStrings(zip.readBytes().toString(Charsets.UTF_8)))
                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") ->
                        sheets.add(name to zip.readBytes().toString(Charsets.UTF_8))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val sb = StringBuilder()
        sheets.sortedBy { it.first }.forEach { (_, xml) ->
            val text = sheetToText(xml, shared)
            if (text.isNotBlank()) sb.append(text).append('\n')
        }
        return sb.toString()
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val out = ArrayList<String>()
        Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { si ->
            val text = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                .findAll(si.groupValues[1])
                .joinToString("") { unescape(it.groupValues[1]) }
            out.add(text)
        }
        return out
    }

    private fun sheetToText(xml: String, shared: List<String>): String {
        val sb = StringBuilder()
        Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { row ->
            val cells = ArrayList<String>()
            Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1]).forEach { c ->
                val attrs = c.groupValues[1]
                val inner = c.groupValues[2]
                val isShared = attrs.contains("t=\"s\"")
                val v = Regex("<v>(.*?)</v>").find(inner)?.groupValues?.get(1)
                val inlineT = Regex("<t[^>]*>(.*?)</t>").find(inner)?.groupValues?.get(1)
                val cell = when {
                    isShared && v != null -> shared.getOrNull(v.toIntOrNull() ?: -1).orEmpty()
                    inlineT != null -> unescape(inlineT)
                    v != null -> v
                    else -> ""
                }
                cells.add(cell)
            }
            val line = cells.joinToString(" | ").trim()
            if (line.isNotBlank()) sb.append(line).append('\n')
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- 通用

    private fun readTextStream(input: InputStream, limit: Int = Int.MAX_VALUE): String {
        val bytes = input.readBytes()
        return String(bytes, 0, minOf(bytes.size, limit), Charsets.UTF_8)
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
