package com.zyf.camera.data.modehandler

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.HandlerThread
import java.util.concurrent.Executor
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.view.Surface
import androidx.core.content.FileProvider
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.utils.DispatchersProvider
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 夜景模式处理器示例 - 展示如何扩展新的拍摄模式
 * 支持多帧合成、长曝光、降噪等夜景拍摄特性
 */
class NightModeHandler(
    private val context: Context,
    private val cameraManager: CameraManager,
    private val getCameraDevice: () -> CameraDevice?,
    private val getCaptureSession: () -> CameraCaptureSession?,
    private val setCaptureSession: (CameraCaptureSession) -> Unit,
    private val getFlashMode: () -> Int
) : CameraModeHandler {

    private var imageReader: ImageReader? = null
    private var isCapturing = false
    private val captureFrameCount = 5 // 多帧合成数量
    private var sessionHandlerThread: HandlerThread? = null
    private var sessionExecutor: Executor? = null

    private fun ensureSessionExecutor(name: String = "NightSessionThread"): Executor {
        if (sessionExecutor == null) {
            val thread = HandlerThread(name)
            thread.start()
            sessionHandlerThread = thread
            val handler = Handler(thread.looper)
            sessionExecutor = Executor { r -> handler.post(r) }
        }
        return sessionExecutor!!
    }

    private fun getSessionHandler(): Handler {
        ensureSessionExecutor()
        return Handler(sessionHandlerThread!!.looper)
    }

    override suspend fun onAttach(cameraDevice: CameraDevice, currentSession: CameraCaptureSession?) {
        Logger.d("NightModeHandler", "onAttach called - initializing night mode")
        // 可以在这里预配置夜景模式相关参数
    }

    override suspend fun onDetach() {
        Logger.d("NightModeHandler", "onDetach called - cleaning up night mode")
        isCapturing = false
        try {
            sessionHandlerThread?.quitSafely()
        } catch (_: Exception) {
        }
        sessionHandlerThread = null
        sessionExecutor = null
    }

    override fun requiredSurfaces(surfaceTexture: SurfaceTexture): List<Surface> {
        val surface = Surface(surfaceTexture)
        val list = mutableListOf(surface)
        imageReader?.surface?.let { list.add(it) }
        return list
    }

    override suspend fun onSessionConfigured(session: CameraCaptureSession) {
        Logger.d("NightModeHandler", "onSessionConfigured called - night mode session ready")
    }

    override suspend fun startPreview(surfaceTexture: SurfaceTexture) {
        withContext(DispatchersProvider.io) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val surface = Surface(surfaceTexture)

            if (imageReader == null) {
                imageReader = ImageReader.newInstance(4032, 3024, android.graphics.ImageFormat.JPEG, captureFrameCount)
            }

            val outputSurfaces = mutableListOf(surface)
            imageReader?.surface?.let { outputSurfaces.add(it) }

            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)

                // 夜景模式优化设置
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

                // 夜景模式特殊参数
                set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)

                // 关闭闪光灯（夜景模式通常不使用闪光灯）
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputs = outputSurfaces.map { OutputConfiguration(it) }
                val executor = ensureSessionExecutor("NightSessionThread")
                val stateCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        setCaptureSession(session)
                        builder.build().let {
                            session.setRepeatingRequest(it, object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureStarted(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, timestamp: Long, frameNumber: Long) {
                                    Logger.d("NightModeHandler", "Preview onCaptureStarted ts=$timestamp frame=$frameNumber")
                                }

                                override fun onCaptureCompleted(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
                                    Logger.d("NightModeHandler", "Preview onCaptureCompleted")
                                }

                                override fun onCaptureFailed(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, failure: android.hardware.camera2.CaptureFailure) {
                                    Logger.e("NightModeHandler", "Preview onCaptureFailed: ${failure.reason}")
                                }
                            }, getSessionHandler())
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        // No-op: session configuration failed
                    }
                }

                val sessionConfig = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, stateCallback)
                device.createCaptureSession(sessionConfig)
            } else {
                device.createCaptureSession(
                    outputSurfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            setCaptureSession(session)
                                builder.build().let {
                                session.setRepeatingRequest(it, object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureStarted(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, timestamp: Long, frameNumber: Long) {
                                        Logger.d("NightModeHandler", "Preview onCaptureStarted ts=$timestamp frame=$frameNumber")
                                    }

                                    override fun onCaptureCompleted(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
                                        Logger.d("NightModeHandler", "Preview onCaptureCompleted")
                                    }

                                    override fun onCaptureFailed(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, failure: android.hardware.camera2.CaptureFailure) {
                                        Logger.e("NightModeHandler", "Preview onCaptureFailed: ${failure.reason}")
                                    }
                                }, getSessionHandler())
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            // No-op: session configuration failed
                        }
                    },
                    getSessionHandler()
                )
            }
        }
    }

    override suspend fun capture(): Uri? {
        if (isCapturing) {
            Logger.w("NightModeHandler", "Night mode capture already in progress")
            return null
        }

        return withContext(DispatchersProvider.io) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val imageReader = imageReader ?: throw IllegalStateException("ImageReader not initialized")
            val photoFile = createNightModeImageFile()

            isCapturing = true

            try {
                // 夜景模式多帧拍摄逻辑
                var combinedUri: Uri? = null
                val capturedImages = mutableListOf<ByteArray>()

                repeat(captureFrameCount) { frameIndex ->
                    Logger.d("NightModeHandler", "Capturing night mode frame ${frameIndex + 1}/$captureFrameCount")

                    val latch = java.util.concurrent.CountDownLatch(1)
                    var frameData: ByteArray? = null

                    imageReader.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireNextImage()
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer[bytes]
                            frameData = bytes
                        } finally {
                            image.close()
                            latch.countDown()
                        }
                    }, getSessionHandler())

                    // 为每帧设置不同的曝光参数（模拟包围曝光）
                    val exposureCompensation = when (frameIndex) {
                        0 -> -2 // 欠曝
                        1 -> -1
                        2 -> 0  // 正常曝光
                        3 -> 1
                        4 -> 2  // 过曝
                        else -> 0
                    }

                    val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(imageReader.surface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensation)
                        set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                        set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    }

                    getCaptureSession()?.capture(builder.build(), null, null)
                    latch.await()

                    frameData?.let { capturedImages.add(it) }

                    // 帧间延迟
                    kotlinx.coroutines.delay(200)
                }

                // 这里应该实现多帧合成算法，目前简化为使用第一帧
                if (capturedImages.isNotEmpty()) {
                    photoFile.writeBytes(capturedImages.first())
                    combinedUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        photoFile
                    )
                }

                combinedUri
            } finally {
                isCapturing = false
            }
        }
    }

    override suspend fun startRecording(): Uri? {
        // 夜景模式不支持录像
        return null
    }

    override suspend fun stopRecording(): Uri? {
        // 夜景模式不支持录像
        return null
    }

    override fun setCameraMode(mode: CameraMode) {
        Logger.d("NightModeHandler", "setCameraMode: $mode")
    }

    override fun close() {
        isCapturing = false
        imageReader?.close()
        imageReader = null
    }

    override fun supportsCapture(): Boolean = true
    override fun supportsRecording(): Boolean = false

    private fun createNightModeImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return File.createTempFile("NIGHT_${timeStamp}_", ".jpg", storageDir)
    }
}
