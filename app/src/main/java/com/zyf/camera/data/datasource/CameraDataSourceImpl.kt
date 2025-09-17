package com.zyf.camera.data.datasource

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.zyf.camera.data.controller.CameraFocusController
import com.zyf.camera.data.controller.FocusController
import com.zyf.camera.data.modehandler.CameraModeHandler
import com.zyf.camera.data.modehandler.PhotoModeHandler
import com.zyf.camera.data.modehandler.VideoModeHandler
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CameraDataSource {

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var currentCameraId = "0"
    private var isBackCamera = true
    private var flashMode = CameraMetadata.FLASH_MODE_OFF
    private var currentMode: CameraMode = CameraMode.PHOTO
    private lateinit var focusController: FocusController
    // 新增：模式处理器映射
    private val modeHandlers: MutableMap<CameraMode, CameraModeHandler> = mutableMapOf()
    private var currentHandler: CameraModeHandler? = null

    override suspend fun initializeCamera() {
        Logger.d("CameraDataSourceImpl", "initializeCamera called")
        return withContext(Dispatchers.IO) {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            currentCameraId = getCameraId()
            Logger.d("CameraDataSourceImpl", "getCameraId: $currentCameraId")
            // 初始化 focusController
            focusController = CameraFocusController(
                cameraManager = cameraManager,
                getCurrentCameraId = { currentCameraId },
                getCameraDevice = { cameraDevice },
                getCaptureSession = { captureSession },
                getImageReaderSurface = { imageReader?.surface },
                getFlashMode = { flashMode }
            )
            // 初始化模式处理器
            modeHandlers[CameraMode.PHOTO] = PhotoModeHandler(
                context,
                cameraManager,
                { cameraDevice },
                { captureSession },
                { flashMode }
            )
            modeHandlers[CameraMode.VIDEO] = VideoModeHandler(
                context,
                cameraManager,
                { cameraDevice },
                { captureSession },
                { flashMode }
            )
            currentHandler = modeHandlers[currentMode]

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
        Logger.d("CameraDataSourceImpl", "startPreview called (by handler)")
        currentHandler?.startPreview(surfaceTexture)
    }

    override suspend fun captureImage(): Uri {
        Logger.d("CameraDataSourceImpl", "captureImage called (by handler)")
        return currentHandler?.capture() ?: throw IllegalStateException("Current mode does not support captureImage")
    }

    override suspend fun startRecording(): Uri {
        Logger.d("CameraDataSourceImpl", "startRecording called (by handler)")
        return currentHandler?.startRecording() ?: throw IllegalStateException("Current mode does not support startRecording")
    }

    override suspend fun stopRecording() {
        Logger.d("CameraDataSourceImpl", "stopRecording called (by handler)")
        currentHandler?.stopRecording()
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
            // 可选：通知 handler 更新预览
        }
    }

    override fun setCameraMode(mode: CameraMode) {
        Logger.d("CameraDataSourceImpl", "setCameraMode: $mode")
        currentMode = mode
        currentHandler = modeHandlers[mode]
        currentHandler?.setCameraMode(mode)
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


    override fun close() {
        Logger.d("CameraDataSourceImpl", "close called")
        cameraDevice?.close()
        captureSession?.close()
        modeHandlers.values.forEach { it.close() }
    }

    suspend fun focusAt(x: Float, y: Float) {
        focusController.focusAt(x, y, currentMode)
    }
}