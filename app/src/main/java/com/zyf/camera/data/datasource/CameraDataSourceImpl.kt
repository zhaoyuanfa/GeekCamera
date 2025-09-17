package com.zyf.camera.data.datasource

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.util.Size
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
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import dagger.hilt.android.qualifiers.ApplicationContext
import android.os.Handler
import android.os.Looper

class CameraDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CameraDataSource {

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    private var imageReader: ImageReader? = null
    private var currentCameraId = "0"
    private var isBackCamera = true
    private var flashMode = CameraMetadata.FLASH_MODE_OFF
    private var currentMode: CameraMode = CameraMode.PHOTO
    private var videoFile: File? = null
    private var previewSize: Size? = null // 可选：如后续不用可删除

    override suspend fun initializeCamera() {
        Logger.d("CameraDataSourceImpl", "initializeCamera called")
        return withContext(Dispatchers.IO) {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            currentCameraId = getCameraId()
            Logger.d("CameraDataSourceImpl", "getCameraId: $currentCameraId")

            // 检查 CAMERA 权限
            val permission = android.Manifest.permission.CAMERA
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Logger.e("CameraDataSourceImpl", "Camera permission not granted")
                throw SecurityException("Camera permission not granted")
            }
            suspendCancellableCoroutine<Unit> { continuation ->
                try {
                    cameraManager.openCamera(currentCameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(device: CameraDevice) {
                            Logger.d("CameraDataSourceImpl", "Camera opened: $device")
                            cameraDevice = device
                            continuation.resume(Unit)
                        }

                        override fun onDisconnected(device: CameraDevice) {
                            Logger.w("CameraDataSourceImpl", "Camera disconnected: $device")
                            device.close()
                            cameraDevice = null
                        }

                        override fun onError(device: CameraDevice, error: Int) {
                            Logger.e("CameraDataSourceImpl", "Camera open failed: $error")
                            device.close()
                            cameraDevice = null
                            continuation.resumeWithException(RuntimeException("Camera open failed: $error"))
                        }
                    }, Handler(Looper.getMainLooper()))
                } catch (e: SecurityException) {
                    Logger.e("CameraDataSourceImpl", "Camera permission denied: ${e.message}")
                    continuation.resumeWithException(SecurityException("Camera permission denied: ${e.message}"))
                }
            }
        }
    }

    override suspend fun startPreview(surfaceTexture: SurfaceTexture) {
        Logger.d("CameraDataSourceImpl", "startPreview called")
        withContext(Dispatchers.IO) {
            try {
                val surface = Surface(surfaceTexture)
                Logger.d("CameraDataSourceImpl", "Preview surface created: $surface")
                // 初始化 imageReader，假设使用 1920x1080 尺寸
                if (imageReader == null) {
                    Logger.d("CameraDataSourceImpl", "Initialize ImageReader")
                    imageReader = ImageReader.newInstance(1920, 1080, android.graphics.ImageFormat.JPEG, 1)
                }
                val outputSurfaces = mutableListOf(surface)
                imageReader?.surface?.let { outputSurfaces.add(it) }
                val captureRequestBuilder = cameraDevice?.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
                )?.apply {
                    addTarget(surface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.FLASH_MODE, flashMode)
                }

                Logger.d("CameraDataSourceImpl", "Creating capture session with surfaces: $outputSurfaces, device: $cameraDevice")
                cameraDevice?.createCaptureSession(
                    outputSurfaces,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            Logger.d("CameraDataSourceImpl", "CaptureSession configured: $session")
                            captureSession = session
                            captureRequestBuilder?.build()?.let {
                                session.setRepeatingRequest(it, null, null)
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Logger.e("CameraDataSourceImpl", "Failed to configure capture session")
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: CameraAccessException) {
                Logger.e("CameraDataSourceImpl", "Camera access exception: ${e.message}")
                throw RuntimeException("Camera access exception: ${e.message}")
            }
        }
    }

    override suspend fun captureImage(): Uri {
        Logger.d("CameraDataSourceImpl", "captureImage called")
        return withContext(Dispatchers.IO) {
            try {
                val photoFile = createImageFile()
                Logger.d("CameraDataSourceImpl", "Photo file created: ${photoFile.absolutePath}")
                val imageReader = imageReader ?: throw IllegalStateException("ImageReader not initialized")
                var imageUri: Uri? = null
                val latch = java.util.concurrent.CountDownLatch(1)

                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireNextImage()
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer[bytes] // 用下标访问器替换 get
                        photoFile.writeBytes(bytes)
                        imageUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            photoFile
                        )
                        Logger.d("CameraDataSourceImpl", "Image saved: ${photoFile.absolutePath}")
                    } finally {
                        image.close()
                        latch.countDown()
                    }
                }, Handler(Looper.getMainLooper()))

                val captureRequest = cameraDevice?.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE
                )?.apply {
                    addTarget(imageReader.surface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.FLASH_MODE, flashMode)
                }?.build()

                if (captureRequest != null) {
                    Logger.d("CameraDataSourceImpl", "CaptureRequest built, capturing...")
                    captureSession?.capture(captureRequest, null, null)
                } else {
                    Logger.e("CameraDataSourceImpl", "Failed to build capture request")
                    throw RuntimeException("Failed to build capture request")
                }

                latch.await() // 等待图片保存完成
                imageUri ?: throw RuntimeException("Image capture failed")
            } catch (e: Exception) {
                Logger.e("CameraDataSourceImpl", "Capture failed: ${e.message}")
                throw RuntimeException("Capture failed: ${e.message}")
            }
        }
    }

    override suspend fun startRecording(): Uri {
        Logger.d("CameraDataSourceImpl", "startRecording called")
        return withContext(Dispatchers.IO) {
            try {
                videoFile = createVideoFile()
                Logger.d("CameraDataSourceImpl", "Video file created: ${videoFile?.absolutePath}")
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(videoFile?.absolutePath)
                    setVideoEncodingBitRate(10_000_000)
                    setVideoFrameRate(30)
                    setVideoSize(1920, 1080)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    prepare()
                    start()
                }
                Logger.d("CameraDataSourceImpl", "MediaRecorder started")
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    videoFile!!
                )
            } catch (e: Exception) {
                Logger.e("CameraDataSourceImpl", "Recording start failed: ${e.message}")
                throw RuntimeException("Recording start failed: ${e.message}")
            }
        }
    }

    override suspend fun stopRecording() {
        Logger.d("CameraDataSourceImpl", "stopRecording called")
        withContext(Dispatchers.IO) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.reset()
                mediaRecorder = null
                Logger.d("CameraDataSourceImpl", "MediaRecorder stopped and reset")
            } catch (e: Exception) {
                Logger.e("CameraDataSourceImpl", "Recording stop failed: ${e.message}")
                throw RuntimeException("Recording stop failed: ${e.message}")
            }
        }
    }

    override suspend fun switchCamera() {
        Logger.d("CameraDataSourceImpl", "switchCamera called, isBackCamera=$isBackCamera")
        withContext(Dispatchers.IO) {
            try {
                cameraDevice?.close()
                cameraDevice = null
                captureSession?.close()
                captureSession = null

                isBackCamera = !isBackCamera
                Logger.d("CameraDataSourceImpl", "Switching camera, now isBackCamera=$isBackCamera")
                initializeCamera()
            } catch (e: Exception) {
                Logger.e("CameraDataSourceImpl", "Switch camera failed: ${e.message}")
                throw RuntimeException("Switch camera failed: ${e.message}")
            }
        }
    }

    override suspend fun toggleFlash() {
        Logger.d("CameraDataSourceImpl", "toggleFlash called, current flashMode=$flashMode")
        withContext(Dispatchers.IO) {
            flashMode = when (flashMode) {
                CameraMetadata.FLASH_MODE_OFF -> CameraMetadata.FLASH_MODE_TORCH
                else -> CameraMetadata.FLASH_MODE_OFF
            }
            Logger.d("CameraDataSourceImpl", "Flash mode changed to $flashMode")
            updatePreviewRequest()
        }
    }

    override fun setCameraMode(mode: CameraMode) {
        Logger.d("CameraDataSourceImpl", "setCameraMode: $mode")
        currentMode = mode
    }

    private fun getCameraId(): String {
        Logger.d("CameraDataSourceImpl", "getCameraId, isBackCamera=$isBackCamera")
        val cameraIds = cameraManager.cameraIdList
        return if (isBackCamera) {
            cameraIds.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraIds[0]
        } else {
            cameraIds.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: cameraIds[0]
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val file = File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
        Logger.d("CameraDataSourceImpl", "createImageFile: ${file.absolutePath}")
        return file
    }

    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val file = File.createTempFile(
            "VID_${timeStamp}_",
            ".mp4",
            storageDir
        )
        Logger.d("CameraDataSourceImpl", "createVideoFile: ${file.absolutePath}")
        return file
    }

    private suspend fun updatePreviewRequest() {
        Logger.d("CameraDataSourceImpl", "updatePreviewRequest called")
        withContext(Dispatchers.IO) {
            try {
                val surface = imageReader?.surface
                val captureRequestBuilder = cameraDevice?.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
                )?.apply {
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.FLASH_MODE, flashMode)
                }
                captureRequestBuilder?.build()?.let {
                    captureSession?.setRepeatingRequest(it, null, null)
                }
            } catch (e: CameraAccessException) {
                Logger.e("CameraDataSourceImpl", "Failed to update preview request: ${e.message}")
            }
        }
    }

    override fun close() {
        Logger.d("CameraDataSourceImpl", "close called")
        cameraDevice?.close()
        captureSession?.close()
        mediaRecorder?.release()
        imageReader?.close()
    }
}