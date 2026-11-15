package com.lingxi.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object MediaUtil {

    fun imageDir(context: Context): File =
        File(context.filesDir, "images").apply { mkdirs() }

    fun saveFromUri(context: Context, uri: Uri): File? = runCatching {
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return null
        saveBitmap(context, bmp)
    }.getOrNull()

    fun saveBitmap(context: Context, bmp: Bitmap, maxSide: Int = 1280): File {
        val scaled = scaleDown(bmp, maxSide)
        val f = File(imageDir(context), "img_${System.currentTimeMillis()}.jpg")
        FileOutputStream(f).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return f
    }

    fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val m = max(bmp.width, bmp.height)
        if (m <= maxSide) return bmp
        val r = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * r).toInt().coerceAtLeast(1),
            (bmp.height * r).toInt().coerceAtLeast(1),
            true
        )
    }

    /** 按需降采样解码，避免大图 OOM。 */
    fun loadScaled(path: String, maxSide: Int = 720): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
    }

    fun toDataUrl(file: File): String {
        val bytes = file.readBytes()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }
}
