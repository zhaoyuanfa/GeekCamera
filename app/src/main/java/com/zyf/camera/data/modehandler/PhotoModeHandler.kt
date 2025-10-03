package com.zyf.camera.data.modehandler

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.HandlerThread
import java.util.concurrent.Executor
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.util.Size
import android.view.Surface
import androidx.core.content.FileProvider
import com.zyf.camera.data.controller.*
import com.zyf.camera.data.manager.MediaDirectoryManager
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.utils.DispatchersProvider
import com.zyf.camera.utils.Logger
import com.zyf.camera.utils.OrientationHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.min

class PhotoModeHandler(
    private val context: Context,
    private val cameraManager: CameraManager,
    private val getCameraDevice: () -> CameraDevice?,
    private val getCaptureSession: () -> CameraCaptureSession?,
    private val setCaptureSession: (CameraCaptureSession) -> Unit,
    private val getFlashMode: () -> Int,
    private val getCurrentCameraId: () -> String
) : CameraModeHandler {

    // session thread & executor for session callbacks (reused and cleaned up in onDetach)
    private var sessionHandlerThread: HandlerThread? = null
    private var sessionExecutor: Executor? = null
    // 用于将图片保存等 I/O 操作移到后台，避免阻塞主线程
    private var imageSaveHandlerThread: HandlerThread? = null

    private fun getImageSaveHandler(): android.os.Handler {
        if (imageSaveHandlerThread == null) {
            val thread = HandlerThread("PhotoImageSave")
            thread.start()
            imageSaveHandlerThread = thread
        }
        return android.os.Handler(imageSaveHandlerThread!!.looper)
    }

    private fun ensureSessionExecutor(name: String = "PhotoSessionThread"): Executor {
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

    private var imageReader: ImageReader? = null
    private var rawImageReader: ImageReader? = null
    
    // 媒体目录管理器
    private val mediaDirectoryManager = MediaDirectoryManager(context)
    
    // 方向辅助器
    private var orientationHelper: OrientationHelper? = null

    // 拍照模式配置
    private var captureMode: PhotoCaptureMode = PhotoCaptureMode.SINGLE
    private var enableRawCapture = false
    private var enableHDR = false
    private var burstCount = 1

    // 图像处理设置
    private var jpegQuality = 95
    private var enableNoiseReduction = true
    private var enableEdgeEnhancement = true

    override suspend fun onAttach(cameraDevice: CameraDevice, currentSession: CameraCaptureSession?) {
        Logger.d("PhotoModeHandler", "onAttach called")
        // 初始化方向辅助器
        orientationHelper = OrientationHelper(context, getCurrentCameraId())
        initializeImageReaders()
    }

    override suspend fun onDetach() {
        Logger.d("PhotoModeHandler", "onDetach called")
        cleanupImageReaders()
        // 释放方向辅助器
        orientationHelper?.cleanup()
        orientationHelper = null

        // teardown session thread
        try {
            sessionHandlerThread?.quitSafely()
        } catch (_: Exception) {
        }
        sessionHandlerThread = null
        sessionExecutor = null
        // teardown image save thread
        try {
            imageSaveHandlerThread?.quitSafely()
        } catch (_: Exception) {
        }
        imageSaveHandlerThread = null
    }

    override fun requiredSurfaces(surfaceTexture: SurfaceTexture): List<Surface> {
        val surfaces = mutableListOf<Surface>()
        surfaces.add(Surface(surfaceTexture))

        imageReader?.surface?.let { surfaces.add(it) }
        if (enableRawCapture) {
            rawImageReader?.surface?.let { surfaces.add(it) }
        }

        return surfaces
    }

    override suspend fun onSessionConfigured(session: CameraCaptureSession) {
        Logger.d("PhotoModeHandler", "onSessionConfigured called")
    }

    override suspend fun startPreview(surfaceTexture: SurfaceTexture) {
        withContext(DispatchersProvider.io) {
            // 自动设置SurfaceTexture预览尺寸
            val cameraId = getCurrentCameraId()
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val previewSizes = map?.getOutputSizes(SurfaceTexture::class.java)
            val selectedSize = chooseBestSize(previewSizes, TARGET_ASPECT_RATIO) ?: android.util.Size(1920, 1080)
            surfaceTexture.setDefaultBufferSize(selectedSize.width, selectedSize.height)

            Logger.d("PhotoModeHandler", "startPreview called, cameraId=$cameraId, selectedSize=${selectedSize.width}x${selectedSize.height}")

            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val surface = Surface(surfaceTexture)

            Logger.d("PhotoModeHandler", "CameraDevice: $device, Surface: $surface")

            // 确保ImageReader已初始化
            if (imageReader == null) {
                initializeImageReaders()
            }

            val outputSurfaces = mutableListOf(surface)
            imageReader?.surface?.let { outputSurfaces.add(it) }
            if (enableRawCapture) {
                rawImageReader?.surface?.let { outputSurfaces.add(it) }
            }

            Logger.d("PhotoModeHandler", "OutputSurfaces count: ${outputSurfaces.size}")

            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)

                // 配置预览参数
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                set(CaptureRequest.FLASH_MODE, getFlashMode())

                // 图像质量设置
                set(CaptureRequest.JPEG_QUALITY, jpegQuality.toByte())
                set(CaptureRequest.NOISE_REDUCTION_MODE,
                    if (enableNoiseReduction) CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
                    else CaptureRequest.NOISE_REDUCTION_MODE_OFF)
                set(CaptureRequest.EDGE_MODE,
                    if (enableEdgeEnhancement) CaptureRequest.EDGE_MODE_HIGH_QUALITY
                    else CaptureRequest.EDGE_MODE_OFF)
            }

            Logger.d("PhotoModeHandler", "CaptureRequest for preview built")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputs = outputSurfaces.map { OutputConfiguration(it) }
                val executor = ensureSessionExecutor("PhotoSessionThread")
                val stateCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Logger.d("PhotoModeHandler", "CaptureSession configured: $session")
                        setCaptureSession(session)
                        try {
                            session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureStarted(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, timestamp: Long, frameNumber: Long) {
                                    Logger.d("PhotoModeHandler", "Preview onCaptureStarted ts=$timestamp frame=$frameNumber")
                                }

                                override fun onCaptureCompleted(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
                                    Logger.d("PhotoModeHandler", "Preview onCaptureCompleted")
                                }

                                override fun onCaptureFailed(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, failure: android.hardware.camera2.CaptureFailure) {
                                    Logger.e("PhotoModeHandler", "Preview onCaptureFailed: ${failure.reason}")
                                }
                            }, getSessionHandler())
                            Logger.d("PhotoModeHandler", "Preview repeating request started")
                        } catch (e: Exception) {
                            Logger.e("PhotoModeHandler", "Failed to start preview: "+e.message)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Logger.e("PhotoModeHandler", "Session configuration failed: $session")
                    }
                }

                val sessionConfig = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, stateCallback)
                device.createCaptureSession(sessionConfig)
            } else {
                device.createCaptureSession(
                    outputSurfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            Logger.d("PhotoModeHandler", "CaptureSession configured: $session")
                            setCaptureSession(session)
                            try {
                                session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                                    override fun onCaptureStarted(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, timestamp: Long, frameNumber: Long) {
                                        Logger.d("PhotoModeHandler", "Preview onCaptureStarted ts=$timestamp frame=$frameNumber")
                                    }

                                    override fun onCaptureCompleted(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
                                        Logger.d("PhotoModeHandler", "Preview onCaptureCompleted")
                                    }

                                    override fun onCaptureFailed(session: CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, failure: android.hardware.camera2.CaptureFailure) {
                                        Logger.e("PhotoModeHandler", "Preview onCaptureFailed: ${failure.reason}")
                                    }
                                }, getSessionHandler())
                                Logger.d("PhotoModeHandler", "Preview repeating request started")
                            } catch (e: Exception) {
                                Logger.e("PhotoModeHandler", "Failed to start preview: "+e.message)
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Logger.e("PhotoModeHandler", "Session configuration failed: $session")
                        }
                    },
                    getSessionHandler()
                )
            }
        }
    }

    override suspend fun capture(): Uri? {
        return when (captureMode) {
            PhotoCaptureMode.SINGLE -> captureSinglePhoto()
            PhotoCaptureMode.BURST -> captureBurstPhotos()
            PhotoCaptureMode.HDR -> captureHDRPhoto()
            PhotoCaptureMode.RAW -> captureRawPhoto()
            PhotoCaptureMode.NIGHT -> captureNightPhoto()
            PhotoCaptureMode.PORTRAIT -> capturePortraitPhoto()
        }
    }

    private suspend fun captureSinglePhoto(): Uri? {
        return withContext(DispatchersProvider.io) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val imageReader = imageReader ?: throw IllegalStateException("ImageReader not initialized")
            val session = getCaptureSession() ?: throw IllegalStateException("Capture session not available")

            val photoFile = createImageFile()

            suspendCancellableCoroutine<Uri?> { continuation ->
                // 将 Image 可用时的处理放到后台线程，避免在主线程进行磁盘/MediaStore 写入
                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage()
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer[bytes]

                        // 直接保存字节数据到媒体库，避免创建重复文件
                        val imageUri = mediaDirectoryManager.savePhotoToMediaStore(bytes, "IMG")
                        
                        // 如果MediaStore保存失败，使用临时文件作为备用方案
                        val finalUri = imageUri ?: run {
                            photoFile.writeBytes(bytes)
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                photoFile
                            )
                        }

                        Logger.d("PhotoModeHandler", "Photo saved with URI: $finalUri")

                        if (continuation.isActive) {
                            continuation.resume(finalUri)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    } finally {
                        image.close()
                    }
                }, getImageSaveHandler())

                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(imageReader.surface)

                    // 优化拍照设置
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    set(CaptureRequest.FLASH_MODE, getFlashMode())
                    set(CaptureRequest.JPEG_QUALITY, jpegQuality.toByte())
                    set(CaptureRequest.JPEG_ORIENTATION, getOrientation())

                    // 图像处理设置
                    set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                    set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                    set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)
                }

                try {
                    session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(RuntimeException("Capture failed: ${failure.reason}"))
                            }
                        }
                    }, getImageSaveHandler())
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                continuation.invokeOnCancellation {
                    imageReader.setOnImageAvailableListener(null, null)
                }
            }
        }
    }

    private suspend fun captureBurstPhotos(): Uri? {
        return withContext(DispatchersProvider.io) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val imageReader = imageReader ?: throw IllegalStateException("ImageReader not initialized")
            val session = getCaptureSession() ?: throw IllegalStateException("Capture session not available")

            val photoFiles = mutableListOf<File>()
            val latch = CountDownLatch(burstCount)

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireNextImage()
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer[bytes]

                    // 直接保存字节数据到MediaStore，避免创建重复文件
                    val burstIndex = photoFiles.size
                    val imageUri = mediaDirectoryManager.savePhotoToMediaStore(bytes, "BURST_${burstIndex}")
                    
                    // 如果需要，创建临时文件作为备用方案
                    if (imageUri != null) {
                        Logger.d("PhotoModeHandler", "Burst image $burstIndex saved with URI: $imageUri")
                    }

                } catch (e: Exception) {
                    Logger.e("PhotoModeHandler", "Failed to save burst image: ${e.message}")
                } finally {
                    image.close()
                    latch.countDown()
                }
            }, null)

            val requests = mutableListOf<CaptureRequest>()
            for (i in 0 until burstCount) {
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(imageReader.surface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.JPEG_QUALITY, jpegQuality.toByte())
                    set(CaptureRequest.JPEG_ORIENTATION, getOrientation())
                }
                requests.add(builder.build())
            }

            session.captureBurst(requests, null, null)

            latch.await(10, TimeUnit.SECONDS)

            // 连拍完成，返回成功标志
            // 实际项目中可能需要返回所有连拍图片的URI列表
            if (latch.count == 0L) {
                // 返回一个表示连拍成功的URI，可以使用MediaStore的查询URI
                Uri.parse("content://media/external/images/media")
            } else {
                null
            }
        }
    }

    private suspend fun captureHDRPhoto(): Uri? {
        return withContext(DispatchersProvider.io) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val imageReader = imageReader ?: throw IllegalStateException("ImageReader not initialized")
            val session = getCaptureSession() ?: throw IllegalStateException("Capture session not available")

            val exposureValues = listOf(-2, 0, 2) // 不同曝光补偿值
            val hdrImages = mutableListOf<ByteArray>()

            for ((index, ev) in exposureValues.withIndex()) {
                val latch = CountDownLatch(1)
                var imageBytes: ByteArray? = null

                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage()
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer[bytes]
                        imageBytes = bytes
                        hdrImages.add(bytes)
                    } finally {
                        image.close()
                        latch.countDown()
                    }
                }, null)

                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(imageReader.surface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev)
                    set(CaptureRequest.JPEG_QUALITY, jpegQuality.toByte())
                }

                session.capture(builder.build(), null, null)
                latch.await(5, TimeUnit.SECONDS)
            }

            // HDR合成处理 - 现在使用正常曝光的图片
            val normalExposureBytes = hdrImages.getOrNull(1)
            if (normalExposureBytes != null) {
                // 直接保存HDR合成结果到媒体库
                mediaDirectoryManager.savePhotoToMediaStore(normalExposureBytes, "HDR")
            } else {
                null
            }
        }
    }

    private suspend fun captureRawPhoto(): Uri? {
        return withContext(DispatchersProvider.io) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val rawReader = rawImageReader ?: throw IllegalStateException("RAW ImageReader not initialized")
            val session = getCaptureSession() ?: throw IllegalStateException("Capture session not available")

            val rawFile = createRawFile()

            suspendCancellableCoroutine<Uri?> { continuation ->
                rawReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage()
                    try {
                        // RAW图像处理逻辑
                        saveRawImage(image, rawFile)

                        // 注意：RAW文件通常不保存到媒体库，直接使用FileProvider
                        val imageUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            rawFile
                        )

                        if (continuation.isActive) {
                            continuation.resume(imageUri)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    } finally {
                        image.close()
                    }
                }, getImageSaveHandler())

                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(rawReader.surface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }

                try {
                    session.capture(builder.build(), null, null)
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
    }

    private suspend fun captureNightPhoto(): Uri? {
        return withContext(DispatchersProvider.io) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val imageReader = imageReader ?: throw IllegalStateException("ImageReader not initialized")
            val session = getCaptureSession() ?: throw IllegalStateException("Capture session not available")

            suspendCancellableCoroutine<Uri?> { continuation ->
                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage()
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer[bytes]

                        // 直接保存夜景照片字节数据到媒体库
                        val imageUri = mediaDirectoryManager.savePhotoToMediaStore(bytes, "NIGHT")
                        
                        // 如果MediaStore保存失败，使用临时文件作为备用方案
                        val finalUri = imageUri ?: run {
                            val photoFile = createImageFile("night")
                            photoFile.writeBytes(bytes)
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                photoFile
                            )
                        }

                        if (continuation.isActive) {
                            continuation.resume(finalUri)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    } finally {
                        image.close()
                    }
                }, getImageSaveHandler())

                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(imageReader.surface)

                    // 夜间模式优化设置
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

                    // 降噪和边缘增强
                    set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                    set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)

                    // ISO和曝光设置（如果支持手动控制）
                    try {
                        set(CaptureRequest.SENSOR_SENSITIVITY, 1600)
                        set(CaptureRequest.SENSOR_EXPOSURE_TIME, 100_000_000L) // 1/10 秒
                    } catch (e: Exception) {
                        // 设备不支持手动ISO/曝光控制
                        Logger.d("PhotoModeHandler", "Manual exposure control not supported")
                    }

                    set(CaptureRequest.JPEG_QUALITY, jpegQuality.toByte())
                    set(CaptureRequest.JPEG_ORIENTATION, getOrientation())
                }

                try {
                    session.capture(builder.build(), null, null)
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
    }

    private suspend fun capturePortraitPhoto(): Uri? {
        return withContext(DispatchersProvider.io) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val imageReader = imageReader ?: throw IllegalStateException("ImageReader not initialized")
            val session = getCaptureSession() ?: throw IllegalStateException("Capture session not available")

            suspendCancellableCoroutine<Uri?> { continuation ->
                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage()
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer[bytes]

                        // 直接保存人像照片字节数据到媒体库
                        val imageUri = mediaDirectoryManager.savePhotoToMediaStore(bytes, "PORTRAIT")
                        
                        // 如果MediaStore保存失败，使用临时文件作为备用方案
                        val finalUri = imageUri ?: run {
                            val photoFile = createImageFile("portrait")
                            photoFile.writeBytes(bytes)
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                photoFile
                            )
                        }

                        if (continuation.isActive) {
                            continuation.resume(imageUri)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    } finally {
                        image.close()
                    }
                }, null)

                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(imageReader.surface)

                    // 人像模式优化设置
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)

                    // 优化人像的色彩和对比度
                    set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY)
                    set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)

                    // 适度的锐化和降噪
                    set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
                    set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)

                    set(CaptureRequest.JPEG_QUALITY, jpegQuality.toByte())
                    set(CaptureRequest.JPEG_ORIENTATION, getOrientation())
                }

                try {
                    session.capture(builder.build(), null, null)
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
    }

    // 配置方法
    fun setCaptureMode(mode: PhotoCaptureMode) {
        captureMode = mode
    }

    fun setRawCaptureEnabled(enabled: Boolean) {
        enableRawCapture = enabled
        if (enabled && rawImageReader == null) {
            initializeRawImageReader()
        }
    }

    fun setJpegQuality(quality: Int) {
        jpegQuality = quality.coerceIn(1, 100)
    }

    fun setBurstCount(count: Int) {
        burstCount = count.coerceIn(1, 20)
    }

    override suspend fun startRecording(): Uri? = null
    override suspend fun stopRecording(): Uri? = null
    override fun setCameraMode(mode: CameraMode) {
        // No-op: mode is handled by the parent datasource
    }

    override fun close() {
        cleanupImageReaders()
        // 释放方向辅助器
        orientationHelper?.cleanup()
        orientationHelper = null
    }

    override fun supportsCapture(): Boolean = true
    override fun supportsRecording(): Boolean = false

    // 私有辅助方法
    private fun initializeImageReaders() {
        val cameraId = getCurrentCameraId()
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        // 获取支持的JPEG尺寸
    val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: arrayOf(Size(1920, 1080))
    val photoSize = chooseBestSize(jpegSizes, TARGET_ASPECT_RATIO) ?: jpegSizes.maxByOrNull { it.width * it.height } ?: Size(1920, 1080)

    imageReader = ImageReader.newInstance(photoSize.width, photoSize.height, ImageFormat.JPEG, 10)

        if (enableRawCapture) {
            initializeRawImageReader()
        }
    }

    private fun initializeRawImageReader() {
        val cameraId = getCurrentCameraId()
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        // 检查是否支持RAW格式
        val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR)
        if (rawSizes != null && rawSizes.isNotEmpty()) {
            val maxRawSize = rawSizes.maxByOrNull { it.width * it.height } ?: rawSizes[0]
            rawImageReader = ImageReader.newInstance(maxRawSize.width, maxRawSize.height, ImageFormat.RAW_SENSOR, 2)
        }
    }

    private fun cleanupImageReaders() {
        imageReader?.close()
        imageReader = null
        rawImageReader?.close()
        rawImageReader = null
    }

    private fun createImageFile(prefix: String = "IMG"): File {
        return mediaDirectoryManager.createPhotoFile(prefix)
    }

    private fun createRawFile(): File {
        return mediaDirectoryManager.createRawFile()
    }

    private fun saveRawImage(image: Image, file: File) {
        // 简化的RAW图像保存逻辑
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer[bytes]
        file.writeBytes(bytes)
    }

    private fun getOrientation(): Int {
        return orientationHelper?.getJpegOrientation() ?: 0
    }
}

private const val TARGET_ASPECT_RATIO = 4f / 3f

private fun chooseBestSize(sizes: Array<Size>?, targetRatio: Float, tolerance: Float = 0.02f): Size? {
    if (sizes.isNullOrEmpty()) return null
    val matchingSizes = sizes.filter { size ->
        val ratio = size.width.toFloat() / size.height
        val inverseRatio = size.height.toFloat() / size.width
        abs(ratio - targetRatio) <= tolerance || abs(inverseRatio - targetRatio) <= tolerance
    }
    if (matchingSizes.isNotEmpty()) {
        return matchingSizes.maxByOrNull { it.width * it.height }
    }

    return sizes.minByOrNull { size ->
        val ratio = size.width.toFloat() / size.height
        val inverseRatio = size.height.toFloat() / size.width
        min(abs(ratio - targetRatio), abs(inverseRatio - targetRatio))
    }
}

enum class PhotoCaptureMode {
    SINGLE,     // 单拍
    BURST,      // 连拍
    HDR,        // HDR
    RAW,        // RAW格式
    NIGHT,      // 夜景模式
    PORTRAIT    // 人像模式
}
