package com.lingxi.ai.kb

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.lingxi.ai.media.MediaUtil
import java.util.zip.ZipInputStream

/**
 * 从文档里抽取图片，纯本地处理，不消耗任何模型额度。
 * 抽出来的图片作为图片消息一起发，是否"看懂"取决于模型。
 */
object DocImages {

    private val PIC_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    fun extract(context: Context, uri: Uri, ext: String, limit: Int = 4): List<String> = runCatching {
        when (ext) {
            "pdf" -> pdfPages(context, uri, limit)
            "docx", "docm" -> zipMedia(context, uri, "word/media/", limit)
            "pptx", "pptm" -> zipMedia(context, uri, "ppt/media/", limit)
            "xlsx", "xlsm" -> zipMedia(context, uri, "xl/media/", limit)
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    private fun zipMedia(context: Context, uri: Uri, prefix: String, limit: Int): List<String> {
        val out = ArrayList<String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && out.size < limit) {
                    val name = entry.name
                    val e = name.substringAfterLast('.', "").lowercase()
                    if (name.startsWith(prefix) && e in PIC_EXT) {
                        val bytes = zip.readBytes()
                        val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                        if (bmp != null && bmp.width >= 120 && bmp.height >= 120) {
                            out.add(MediaUtil.saveBitmap(context, bmp, 1024).absolutePath)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return out
    }

    /** PDF 没有"插图"的概念，按页渲染成位图。 */
    private fun pdfPages(context: Context, uri: Uri, limit: Int): List<String> {
        val out = ArrayList<String>()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return out
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pages = minOf(renderer.pageCount, limit)
                for (i in 0 until pages) {
                    renderer.openPage(i).use { page ->
                        val width = 1024
                        val height = (width * page.height / page.width.toFloat()).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.add(MediaUtil.saveBitmap(context, bmp, 1024).absolutePath)
                    }
                }
            }
        }
        return out
    }
}