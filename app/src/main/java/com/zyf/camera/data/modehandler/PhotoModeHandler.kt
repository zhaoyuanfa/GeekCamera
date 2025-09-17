package com.zyf.camera.data.modehandler

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.Environment
import android.view.Surface
import androidx.core.content.FileProvider
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PhotoModeHandler(
    private val context: Context,
    private val cameraManager: CameraManager,
    private val getCameraDevice: () -> CameraDevice?,
    private val getCaptureSession: () -> CameraCaptureSession?,
    private val getFlashMode: () -> Int
) : CameraModeHandler {
    private var imageReader: ImageReader? = null

    override suspend fun startPreview(surfaceTexture: SurfaceTexture) {
        withContext(Dispatchers.IO) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val surface = Surface(surfaceTexture)
            if (imageReader == null) {
                imageReader = ImageReader.newInstance(1920, 1080, android.graphics.ImageFormat.JPEG, 1)
            }
            val outputSurfaces = mutableListOf(surface)
            imageReader?.surface?.let { outputSurfaces.add(it) }
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.FLASH_MODE, getFlashMode())
            }
            device.createCaptureSession(
                outputSurfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        getCaptureSession()?.close()
                        builder.build().let {
                            session.setRepeatingRequest(it, null, null)
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                null
            )
        }
    }

    override suspend fun capture(): Uri? {
        return withContext(Dispatchers.IO) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val imageReader = imageReader ?: throw IllegalStateException("ImageReader not initialized")
            val photoFile = createImageFile()
            var imageUri: Uri? = null
            val latch = java.util.concurrent.CountDownLatch(1)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer[bytes]
                    photoFile.writeBytes(bytes)
                    imageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        photoFile
                    )
                } finally {
                    image.close()
                    latch.countDown()
                }
            }, null)
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.FLASH_MODE, getFlashMode())
            }
            getCaptureSession()?.capture(builder.build(), null, null)
            latch.await()
            imageUri
        }
    }

    override suspend fun startRecording(): Uri? = null
    override suspend fun stopRecording() {}
    override fun setCameraMode(mode: CameraMode) {}
    override fun close() {
        imageReader?.close()
        imageReader = null
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }
}

