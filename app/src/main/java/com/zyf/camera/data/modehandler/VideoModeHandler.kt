package com.zyf.camera.data.modehandler

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Build
import android.os.HandlerThread
import java.util.concurrent.Executor
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.util.Size
import android.view.Surface
import androidx.core.content.FileProvider
import com.zyf.camera.data.manager.MediaDirectoryManager
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.utils.DispatchersProvider
import com.zyf.camera.utils.Logger
import com.zyf.camera.utils.OrientationHelper
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

class VideoModeHandler(
    private val context: Context,
    private val cameraManager: CameraManager,
    private val getCameraDevice: () -> CameraDevice?,
    @Suppress("unused") private val getCaptureSession: () -> CameraCaptureSession?, // Reserved for session state queries
    private val setCaptureSession: (CameraCaptureSession) -> Unit,
    private val getFlashMode: () -> Int,
    private val getCurrentCameraId: () -> String
) : CameraModeHandler {
    private var mediaRecorder: MediaRecorder? = null
    private var videoFile: File? = null
    private var videoUri: Uri? = null  // MediaStore URI
    private var isActuallyRecording: Boolean = false  // 标志是否真正开始录制
    private var previewSurface: Surface? = null
    private var recorderSurface: Surface? = null
    private var currentSession: CameraCaptureSession? = null
    private var sessionHandlerThread: HandlerThread? = null
    private var sessionExecutor: Executor? = null

    private fun ensureSessionExecutor(name: String = "VideoSessionThread"): Executor {
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
    
    // 媒体目录管理器
    private val mediaDirectoryManager = MediaDirectoryManager(context)
    
    // 方向助手
    private var orientationHelper: OrientationHelper? = null

    override suspend fun onAttach(cameraDevice: CameraDevice, currentSession: CameraCaptureSession?) {
        Logger.d("VideoModeHandler", "onAttach called")
        // Initialize OrientationHelper
        orientationHelper = OrientationHelper(context, getCurrentCameraId())
        // Initialize MediaRecorder when video mode is attached
        initializeMediaRecorder()
    }

    override suspend fun onDetach() {
        Logger.d("VideoModeHandler", "onDetach called")
        // Stop any ongoing recording and release resources if needed
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
            // Ignore stop exceptions - recorder may not be started
        }
        
        // Close current session to prevent callback issues
        try {
            currentSession?.close()
            currentSession = null
        } catch (e: Exception) {
            Logger.e("VideoModeHandler", "Exception closing session: ${e.message}")
        }
        
        // Clean up surfaces
        previewSurface?.release()
        previewSurface = null
        
        // 清理未使用的视频文件
        if (!isActuallyRecording && videoFile != null) {
            try {
                videoFile?.let { file ->
                    if (file.exists() && file.length() == 0L) {
                        file.delete()
                        Logger.d("VideoModeHandler", "Deleted unused empty video file: ${file.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Logger.e("VideoModeHandler", "Error deleting unused video file: ${e.message}")
            }
        }
        
        // 清理文件引用
        videoFile = null
        videoUri = null
        isActuallyRecording = false
        
        // Clean up OrientationHelper
        orientationHelper?.cleanup()
        orientationHelper = null
        
        releaseMediaRecorder()
    }

    override fun requiredSurfaces(surfaceTexture: SurfaceTexture): List<Surface> {
        val surface = Surface(surfaceTexture)
        val list = mutableListOf(surface)
        recorderSurface?.let { list.add(it) }
        return list
    }

    override suspend fun onSessionConfigured(session: CameraCaptureSession) {
        // No-op by default - could be used for post-configuration setup
    }

    @Suppress("DEPRECATION") // Using legacy MediaRecorder for compatibility with older devices
    override suspend fun startPreview(surfaceTexture: SurfaceTexture) {
        Logger.d("VideoModeHandler", "startPreview called")
        withContext(DispatchersProvider.io) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")

            val selectedSize = cameraManager
                .getCameraCharacteristics(getCurrentCameraId())
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
                ?.let { chooseBestSize(it, TARGET_ASPECT_RATIO) }

            selectedSize?.let { size ->
                surfaceTexture.setDefaultBufferSize(size.width, size.height)
            }

            previewSurface = Surface(surfaceTexture)
            
            // Ensure MediaRecorder is initialized
            if (mediaRecorder == null) {
                Logger.d("VideoModeHandler", "MediaRecorder not initialized, initializing now")
                initializeMediaRecorder()
            }
            
            // Always create new session to ensure surface consistency
            Logger.d("VideoModeHandler", "Creating new capture session for preview")
            val outputSurfaces = listOf(previewSurface!!, recorderSurface!!)
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(previewSurface!!)
                addTarget(recorderSurface!!)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.FLASH_MODE, getFlashMode())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputs = outputSurfaces.map { OutputConfiguration(it) }
                val executor = ensureSessionExecutor("VideoSessionThread")
                val stateCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Logger.d("VideoModeHandler", "Capture session configured successfully")
                        try {
                            val currentDevice = getCameraDevice()
                            if (currentDevice != null && currentDevice == device) {
                                currentSession = session
                                setCaptureSession(session)
                                builder.build().let {
                                    session.setRepeatingRequest(it, null, getSessionHandler())
                                }
                                Logger.d("VideoModeHandler", "Preview repeating request started")
                            } else {
                                Logger.w("VideoModeHandler", "Camera device changed, ignoring session callback")
                                session.close()
                            }
                        } catch (e: IllegalStateException) {
                            Logger.e("VideoModeHandler", "Session already closed: ${e.message}")
                        } catch (e: Exception) {
                            Logger.e("VideoModeHandler", "Failed to start repeating request: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Logger.e("VideoModeHandler", "Capture session configuration failed")
                    }
                }

                val sessionConfig = SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, stateCallback)
                device.createCaptureSession(sessionConfig)
            } else {
                device.createCaptureSession(
                    outputSurfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            Logger.d("VideoModeHandler", "Capture session configured successfully")
                            try {
                                // Check if the camera device is still valid
                                val currentDevice = getCameraDevice()
                                if (currentDevice != null && currentDevice == device) {
                                    // Track the current session
                                    currentSession = session
                                    // Update datasource captureSession reference
                                    setCaptureSession(session)
                                        builder.build().let {
                                        session.setRepeatingRequest(it, null, getSessionHandler())
                                    }
                                    Logger.d("VideoModeHandler", "Preview repeating request started")
                                } else {
                                    Logger.w("VideoModeHandler", "Camera device changed, ignoring session callback")
                                    session.close()
                                }
                            } catch (e: IllegalStateException) {
                                Logger.e("VideoModeHandler", "Session already closed: ${e.message}")
                                // Session was closed, ignore
                            } catch (e: Exception) {
                                Logger.e("VideoModeHandler", "Failed to start repeating request: ${e.message}")
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Logger.e("VideoModeHandler", "Capture session configuration failed")
                            // No-op: session configuration failed - will be handled by caller
                        }
                    },
                    getSessionHandler()
                )
            }
        }
    }

    override suspend fun capture(): Uri? {
        Logger.d("VideoModeHandler", "capture() called - not supported for video mode")
        return null
    }

    override suspend fun startRecording(): Uri? {
        Logger.d("VideoModeHandler", "startRecording() called")
        return withContext(DispatchersProvider.io) {
            try {
                if (mediaRecorder == null) {
                    Logger.w("VideoModeHandler", "MediaRecorder not initialized, reinitializing...")
                    initializeMediaRecorder()
                    if (mediaRecorder == null) {
                        Logger.e("VideoModeHandler", "Failed to reinitialize MediaRecorder")
                        throw IllegalStateException("Failed to reinitialize MediaRecorder")
                    }
                }
                
                // MediaRecorder已经准备好，直接开始录制
                Logger.d("VideoModeHandler", "Starting actual recording to: ${videoFile?.absolutePath}")
                
                isActuallyRecording = true
                mediaRecorder?.start()
                
                val uri = videoFile?.let {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        it
                    )
                }
                Logger.d("VideoModeHandler", "Recording started, uri: $uri")
                return@withContext uri
            } catch (e: Exception) {
                Logger.e("VideoModeHandler", "Failed to start recording: ${e.message}")
                // 清理失败的文件
                videoFile?.let { file ->
                    if (file.exists() && file.length() == 0L) {
                        file.delete()
                        Logger.d("VideoModeHandler", "Deleted empty video file after start failure")
                    }
                }
                videoFile = null
                throw e
            }
        }
    }

    override suspend fun stopRecording(): Uri? {
        Logger.d("VideoModeHandler", "stopRecording() called")
        return withContext(DispatchersProvider.io) {
            val uri = try {
                Logger.d("VideoModeHandler", "Stopping MediaRecorder")
                mediaRecorder?.stop()
                isActuallyRecording = false
                
                // 如果已经通过MediaStore创建了文件，直接返回URI
                if (videoUri != null) {
                    Logger.d("VideoModeHandler", "Video already registered in MediaStore: $videoUri")
                    videoUri
                } else {
                    // 回退方案：保存到媒体库
                    videoFile?.let { file ->
                        mediaDirectoryManager.saveVideoToMediaStore(file)
                            ?: FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            ).also {
                                Logger.d("VideoModeHandler", "Video saved to: ${file.absolutePath}")
                            }
                    }
                }
            } catch (e: Exception) {
                Logger.e("VideoModeHandler", "Exception stopping MediaRecorder: ${e.message}")
                // Return null if stopping failed
                null
            }
            
            // Reset and reinitialize MediaRecorder for next recording
            try {
                Logger.d("VideoModeHandler", "Reinitializing MediaRecorder for next recording")
                mediaRecorder?.reset()
                mediaRecorder?.release()
                mediaRecorder = null
                recorderSurface?.release()
                recorderSurface = null
                
                // 清理文件引用
                videoFile = null
                videoUri = null
                
                // Initialize new MediaRecorder for next use
                initializeMediaRecorder()
                
                // Restart preview to avoid freezing - surface configuration changed
                Logger.d("VideoModeHandler", "Restarting preview after MediaRecorder reinitialization")
                
                // Need to restart preview session because MediaRecorder surface changed
                withContext(DispatchersProvider.io) {
                    try {
                        // Close current session to force recreation
                        currentSession?.close()
                        currentSession = null
                        
                        Logger.d("VideoModeHandler", "Closed current session to force preview restart")
                    } catch (e: Exception) {
                        Logger.e("VideoModeHandler", "Failed to clear session after recording: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Logger.e("VideoModeHandler", "Failed to reinitialize MediaRecorder: ${e.message}")
            }
            
            Logger.d("VideoModeHandler", "Recording stopped, uri: $uri")
            return@withContext uri
        }
    }

    override fun setCameraMode(mode: CameraMode) {
        // No-op: mode is handled by the parent datasource
    }

    override fun close() {
        Logger.d("VideoModeHandler", "close called")
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
            // Ignore stop exceptions during cleanup
        }
        
        // Close current session
        try {
            currentSession?.close()
            currentSession = null
        } catch (e: Exception) {
            Logger.e("VideoModeHandler", "Exception closing session in close(): ${e.message}")
        }
        
        // 清理文件引用
        videoFile = null
        videoUri = null
        
        releaseMediaRecorder()
        previewSurface?.release()
        previewSurface = null
    }

    override fun supportsCapture(): Boolean = false
    override fun supportsRecording(): Boolean = true

    private fun createVideoFile(): File {
        return mediaDirectoryManager.createVideoFile()
    }

    @Suppress("DEPRECATION")
    private suspend fun initializeMediaRecorder() {
        withContext(DispatchersProvider.io) {
            Logger.d("VideoModeHandler", "initializeMediaRecorder called")
            try {
                releaseMediaRecorder() // Clean up any existing recorder
                
                mediaRecorder = MediaRecorder()
                // 不在这里创建文件，等到真正开始录制时才创建
                
                Logger.d("VideoModeHandler", "MediaRecorder created, preparing with real file for Surface")
                
                // 创建实际的录制文件（但标记为临时状态，未正式录制时会删除）
                if (videoFile == null) {
                    val fileAndUri = mediaDirectoryManager.createVideoFileInMediaStore()
                    if (fileAndUri != null) {
                        videoFile = fileAndUri.first
                        videoUri = fileAndUri.second
                        Logger.d("VideoModeHandler", "Created video file in MediaStore: ${videoFile?.absolutePath}")
                    } else {
                        // 回退方案：创建普通文件
                        videoFile = createVideoFile()
                        Logger.d("VideoModeHandler", "Fallback: Created regular video file: ${videoFile?.absolutePath}")
                    }
                }
                
                if (videoFile == null) {
                    throw IllegalStateException("Failed to create video file")
                }
                
                mediaRecorder?.apply {
                    val cameraId = getCurrentCameraId()
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val videoSize = map?.getOutputSizes(MediaRecorder::class.java)?.let { sizes ->
                        chooseBestSize(sizes, TARGET_ASPECT_RATIO)
                    }

                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(videoFile!!.absolutePath)
                    setVideoEncodingBitRate(10_000_000)
                    setVideoFrameRate(30)
                    videoSize?.let { size ->
                        setVideoSize(size.width, size.height)
                    } ?: setVideoSize(1440, 1080)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    // 设置视频方向
                    val videoOrientation = orientationHelper?.getVideoOrientation() ?: 0
                    setOrientationHint(videoOrientation)
                    // 准备MediaRecorder以获取surface
                    prepare()
                }
                recorderSurface = mediaRecorder?.surface
                Logger.d("VideoModeHandler", "MediaRecorder initialized successfully (not prepared yet)")
            } catch (e: Exception) {
                Logger.e("VideoModeHandler", "Failed to initialize MediaRecorder: ${e.message}")
                releaseMediaRecorder()
                throw e
            }
        }
    }

    private fun releaseMediaRecorder() {
        Logger.d("VideoModeHandler", "releaseMediaRecorder called")
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Logger.e("VideoModeHandler", "Exception releasing MediaRecorder: ${e.message}")
        }
        mediaRecorder = null
        recorderSurface?.release()
        recorderSurface = null
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
