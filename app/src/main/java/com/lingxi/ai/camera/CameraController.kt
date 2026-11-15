package com.lingxi.ai.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.lingxi.ai.media.MediaUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * CameraX 封装：绑定前置/后置摄像头预览，并按需抓取一帧 JPEG。
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {

    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private val executor = ContextCompat.getMainExecutor(context)

    fun bind(previewView: PreviewView, useFront: Boolean = true) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = cameraProvider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture

            val selector = CameraSelector.Builder()
                .requireLensFacing(if (useFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
                .build()

            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            }.onFailure {
                // 前置不可用时退回后置
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                }
            }
        }, executor)
    }

    fun unbind() {
        runCatching { provider?.unbindAll() }
        provider = null
        imageCapture = null
    }

    suspend fun snap(): File? = suspendCancellableCoroutine { cont ->
        val capture = imageCapture
        if (capture == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val file = File(MediaUtil.imageDir(context), "snap_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (cont.isActive) cont.resume(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        )
    }
}
