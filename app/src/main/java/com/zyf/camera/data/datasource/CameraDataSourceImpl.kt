package com.zyf.camera.data.datasource

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import com.zyf.camera.data.controller.*
import com.zyf.camera.data.controller.base.ControllerState
import com.zyf.camera.data.manager.CameraCapabilityManager
import com.zyf.camera.data.manager.CameraOperationManager
import com.zyf.camera.data.manager.CameraSettings
import com.zyf.camera.data.manager.ControllerType
import com.zyf.camera.data.modehandler.CameraModeHandler
import com.zyf.camera.data.modehandler.PhotoModeHandler
import com.zyf.camera.data.modehandler.VideoModeHandler
import com.zyf.camera.data.model.FocusMode
import com.zyf.camera.data.model.HDRMode
import com.zyf.camera.domain.model.CameraCapability
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.domain.model.LogicalCameraId
import com.zyf.camera.utils.DispatchersProvider
import com.zyf.camera.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityManager: CameraCapabilityManager,
    private val operationManager: CameraOperationManager
) : CameraDataSource {

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var currentCameraId = "0"
    private var currentLogicalId = LogicalCameraId.MAIN
    private var isBackCamera = true
    private var flashMode = CameraMetadata.FLASH_MODE_OFF
    private var currentMode: CameraMode = CameraMode.PHOTO
    private var isCurrentlyRecording = false
    private var currentZoomRatio = 1.0f
    // Keep last preview SurfaceTexture so we can restart preview automatically after camera re-open
    private var pendingPreviewSurface: SurfaceTexture? = null
    @Volatile
    private var controllersInitialized = false
    @Volatile
    private var lastInitializedSession: CameraCaptureSession? = null

    // 新增：模式处理器映射
    private val modeHandlers: MutableMap<CameraMode, CameraModeHandler> = mutableMapOf()
    private var currentHandler: CameraModeHandler? = null

    // Controllers - 使用新的管理器
    private lateinit var controllerManager: CameraControllerManager

    // Coroutine scope for running suspend lifecycle operations
    private val job = SupervisorJob()
    private val scope = CoroutineScope(DispatchersProvider.main + job)
    private val cameraInitMutex = Mutex()
    private val controllerInitMutex = Mutex()

    override suspend fun initializeCamera() {
        Logger.d("CameraDataSourceImpl", "initializeCamera called")
        cameraInitMutex.withLock {
            withContext(DispatchersProvider.io) {
                controllersInitialized = false
                prepareCameraEnvironment()

                if (cameraDevice != null) {
                    Logger.w("CameraDataSourceImpl", "Camera already initialized, skipping re-init")
                    return@withContext
                }

                ensureCameraPermission()
                openCameraDevice()
                cameraDevice?.let { device ->
                    attachCurrentHandler(device)
                }
            }
        }
    }

    private fun prepareCameraEnvironment() {
        Logger.d("CameraDataSourceImpl", "prepareCameraEnvironment called")
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        currentCameraId = getCameraId()
        Logger.d("CameraDataSourceImpl", "getCameraId: $currentCameraId")

        controllerManager = CameraControllerManager(
            cameraManager = cameraManager,
            getCurrentCameraId = { currentCameraId },
            getCameraDevice = { cameraDevice },
            getCaptureSession = { captureSession }
        )
        Logger.d("CameraDataSourceImpl", "CameraControllerManager created")

        configureModeHandlers()
    }

    private fun configureModeHandlers() {
        Logger.d("CameraDataSourceImpl", "configureModeHandlers called (currentMode=$currentMode)")
        modeHandlers[CameraMode.PHOTO] = PhotoModeHandler(
            context,
            cameraManager,
            { cameraDevice },
            { captureSession },
            { session -> onCaptureSessionConfigured(session) },
            { flashMode },
            getCurrentCameraId = { currentCameraId }
        )
        modeHandlers[CameraMode.VIDEO] = VideoModeHandler(
            context,
            cameraManager,
            { cameraDevice },
            { captureSession },
            { session -> captureSession = session },
            { flashMode },
            getCurrentCameraId = { currentCameraId }
        )
        currentHandler = modeHandlers[currentMode]
        Logger.d("CameraDataSourceImpl", "Mode handlers configured: ${modeHandlers.keys}")
    }

    private fun ensureCameraPermission() {
        val permission = android.Manifest.permission.CAMERA
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Logger.e("CameraDataSourceImpl", "Camera permission not granted")
            throw SecurityException("Camera permission not granted")
        }
    }

    private suspend fun openCameraDevice() {
        suspendCancellableCoroutine<Unit> { continuation ->
            try {
                // 创建后台线程Handler，避免阻塞主线程
                val backgroundHandler = Handler(
                    android.os.HandlerThread("CameraBackground").apply {
                        start()
                    }.looper
                )
                Logger.d("CameraDataSourceImpl", "Opening camera with ID: $currentCameraId")
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
                }, backgroundHandler)
                
                // 设置超时取消
                continuation.invokeOnCancellation {
                    backgroundHandler.looper.quitSafely()
                }
            } catch (e: SecurityException) {
                Logger.e("CameraDataSourceImpl", "Camera permission denied: ${e.message}")
                continuation.resumeWithException(SecurityException("Camera permission denied: ${e.message}"))
            }
        }
    }

    private fun onCaptureSessionConfigured(session: CameraCaptureSession) {
        captureSession = session
        Logger.d("CameraDataSourceImpl", "onCaptureSessionConfigured: $session")
        scope.launch(DispatchersProvider.io) {
            controllerInitMutex.withLock {
                if (controllersInitialized && lastInitializedSession === session) {
                    Logger.d("CameraDataSourceImpl", "Controllers already initialized for current session, skipping")
                    return@withLock
                }
                try {
                    controllerManager.initializeAll()
                    operationManager.registerFocusController(controllerManager.focusController)
                    lastInitializedSession = session
                    controllersInitialized = true
                    Logger.d("CameraDataSourceImpl", "Controllers initialized successfully after session configured")
                } catch (e: Exception) {
                    Logger.e("CameraDataSourceImpl", "Failed to initialize controllers: ${e.message}")
                }
            }
        }
    }

    private suspend fun attachCurrentHandler(device: CameraDevice) {
        Logger.d("CameraDataSourceImpl", "attachCurrentHandler called with ${currentHandler?.javaClass?.simpleName}")
        currentHandler?.let { handler ->
            // 使用IO调度器避免阻塞主线程
            withContext(DispatchersProvider.io) {
                try {
                    handler.onAttach(device, captureSession)
                } catch (e: Exception) {
                    Logger.e("CameraDataSourceImpl", "Handler onAttach failed: ${e.message}")
                }

                pendingPreviewSurface?.let { surfaceTexture ->
                    try {
                        handler.startPreview(surfaceTexture)
                        pendingPreviewSurface = null
                    } catch (e: Exception) {
                        Logger.e("CameraDataSourceImpl", "Failed to start pending preview: ${e.message}")
                    }
                }
            }
        }
    }

    override suspend fun startPreview(surfaceTexture: SurfaceTexture) {
        Logger.d("CameraDataSourceImpl", "startPreview called (by handler)")
        // Remember surface so preview can be restarted automatically after camera reopen
        pendingPreviewSurface = surfaceTexture
        currentHandler?.startPreview(surfaceTexture)
    }

    override suspend fun captureImage(): Uri {
        Logger.d("CameraDataSourceImpl", "captureImage called (by handler)")
        return currentHandler?.capture() ?: throw IllegalStateException("Current mode does not support captureImage")
    }

    override suspend fun startRecording(): Uri {
        Logger.d("CameraDataSourceImpl", "startRecording called (by handler)")
        val uri = currentHandler?.startRecording() ?: throw IllegalStateException("Current mode does not support startRecording")
        isCurrentlyRecording = true
        return uri
    }

    override suspend fun stopRecording() {
        Logger.d("CameraDataSourceImpl", "stopRecording called (by handler)")
        currentHandler?.stopRecording()
        isCurrentlyRecording = false
        
        // Restart preview after recording to avoid freeze
        pendingPreviewSurface?.let { surface ->
            Logger.d("CameraDataSourceImpl", "Restarting preview after recording stopped")
            try {
                currentHandler?.startPreview(surface)
            } catch (e: Exception) {
                Logger.e("CameraDataSourceImpl", "Failed to restart preview after recording: ${e.message}")
            }
        }
    }

    override suspend fun switchCamera() {
        Logger.d("CameraDataSourceImpl", "switchCamera called, isBackCamera=$isBackCamera")
        withContext(DispatchersProvider.io) {
            try {
                // Close device and session and re-open other camera
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
        Logger.d("CameraDataSourceImpl", "toggleFlash called")
        withContext(DispatchersProvider.io) {
            try {
                controllerManager.flashController.toggleMode()
                // Update internal flashMode for backward compatibility with handlers
                val currentFlashMode = controllerManager.flashController.getCurrentMode()
                flashMode = when (currentFlashMode) {
                    FlashMode.OFF -> CameraMetadata.FLASH_MODE_OFF
                    FlashMode.ON -> CaptureRequest.FLASH_MODE_SINGLE
                    FlashMode.AUTO -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    FlashMode.TORCH -> CaptureRequest.FLASH_MODE_TORCH
                }
                Logger.d("CameraDataSourceImpl", "Flash mode changed to $currentFlashMode")
            } catch (e: Exception) {
                Logger.e("CameraDataSourceImpl", "Failed to toggle flash: ${e.message}")
            }
        }
    }

    override fun setCameraMode(mode: CameraMode) {
        Logger.d("CameraDataSourceImpl", "setCameraMode: $mode")
        val oldHandler = currentHandler
        val newHandler = modeHandlers[mode]
        Logger.d("CameraDataSourceImpl", "Mode handlers - oldHandler: ${oldHandler?.javaClass?.simpleName}, newHandler: ${newHandler?.javaClass?.simpleName}")
        currentMode = mode
        currentHandler = newHandler

        // Inform handlers synchronously about mode change, then perform attach/detach asynchronously
        try {
            oldHandler?.setCameraMode(mode)
            newHandler?.setCameraMode(mode)
        } catch (e: Exception) {
            Logger.e("CameraDataSourceImpl", "setCameraMode handler set failed: ${e.message}")
        }

        // If cameraDevice is open, try to reconfigure without closing camera
        cameraDevice?.let { device ->
            scope.launch(DispatchersProvider.io) {
                try {
                    oldHandler?.onDetach()
                } catch (e: Exception) {
                    Logger.e("CameraDataSourceImpl", "oldHandler onDetach failed: ${e.message}")
                }

                try {
                    newHandler?.onAttach(device, captureSession)
                } catch (e: Exception) {
                    Logger.e("CameraDataSourceImpl", "newHandler onAttach failed: ${e.message}")
                }

                // Restart preview with new mode configuration if surface is available
                pendingPreviewSurface?.let { surface ->
                    Logger.d("CameraDataSourceImpl", "Restarting preview after mode switch with surface: $surface")
                    try {
                        newHandler?.startPreview(surface)
                    } catch (e: Exception) {
                        Logger.e("CameraDataSourceImpl", "Failed to restart preview after mode switch: ${e.message}")
                    }
                }
            }
        }
    }

    override fun registerModeHandler(mode: CameraMode, handler: CameraModeHandler) {
        Logger.d("CameraDataSourceImpl", "registerModeHandler: $mode -> ${handler.javaClass.simpleName}")
        modeHandlers[mode] = handler
    }

    // 新的Controller系统现在通过CameraControllerManager统一管理
    // 提供访问各种Controller的公共接口
    
    fun getZoomController() = controllerManager.zoomController
    fun getFlashController() = controllerManager.flashController  
    fun getWhiteBalanceController() = controllerManager.whiteBalanceController
    fun getISOController() = controllerManager.isoController
    fun getExposureController() = controllerManager.exposureController

    override fun getCameraControllerManager(): CameraControllerManager {
        if (!::controllerManager.isInitialized) {
            throw IllegalStateException("Camera controller manager not initialized. Call initializeCamera first.")
        }
        return controllerManager
    }

    // 获取所有Controller的状态流
    override fun getAllControllerStates(): Flow<Map<String, ControllerState>> {
        if (!::controllerManager.isInitialized) {
            throw IllegalStateException("Camera controller manager not initialized. Call initializeCamera first.")
        }
        return controllerManager.allControllersState
    }

    private fun getCameraId(): String {
        Logger.d("CameraDataSourceImpl", "getCameraId, isBackCamera=$isBackCamera")
        val cameraIds = cameraManager.cameraIdList
        return if (isBackCamera) {
            cameraIds.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraIds[0]
        } else {
            cameraIds.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT
            } ?: cameraIds[0]
        }
    }


    override fun close() {
        Logger.d("CameraDataSourceImpl", "close called")
        job.cancel()
        
        // 释放控制器资源
        scope.launch {
            try {
                controllerManager.releaseAll()
                Logger.d("CameraDataSourceImpl", "Controllers released successfully")
            } catch (e: Exception) {
                Logger.e("CameraDataSourceImpl", "Failed to release controllers: ${e.message}")
            }
        }
        
        cameraDevice?.close()
        captureSession?.close()
        imageReader?.close()
        cameraDevice = null
        captureSession = null
        imageReader = null
        controllersInitialized = false
        lastInitializedSession = null
        modeHandlers.values.forEach { it.close() }
    }

    override suspend fun focusAt(x: Float, y: Float) {
        Logger.d("CameraDataSourceImpl", "focusAt called: x=$x, y=$y")
        controllerManager.focusController.focusAt(x, y)
    }

    override suspend fun setZoom(zoom: Float) {
        Logger.d("CameraDataSourceImpl", "setZoom called: $zoom")
        controllerManager.zoomController.setValue(zoom)
    }

    override suspend fun setExposureCompensation(value: Int) {
        try {
            controllerManager.exposureController.setValue(value)
            Logger.d("CameraDataSourceImpl", "Exposure compensation set to $value")
        } catch (e: Exception) {
            Logger.e("CameraDataSourceImpl", "Failed to set exposure compensation: ${e.message}")
        }
    }

    // ==== 扩展：逻辑相机ID支持 ====
    override suspend fun switchToLogicalCamera(logicalId: LogicalCameraId) {
        Logger.d("CameraDataSourceImpl", "switchToLogicalCamera: $logicalId")
        withContext(DispatchersProvider.io) {
            try {
                val physicalId = capabilityManager.getPhysicalCameraId(logicalId)
                    ?: throw IllegalArgumentException("Logical camera $logicalId not available")

                if (physicalId != currentCameraId) {
                    // Need to switch physical camera
                    cameraDevice?.close()
                    cameraDevice = null
                    captureSession?.close()
                    captureSession = null

                    currentCameraId = physicalId
                    currentLogicalId = logicalId

                    // Reinitialize camera with new ID
                    initializeCamera()
                } else {
                    // Same physical camera, just update logical ID
                    currentLogicalId = logicalId
                }
            } catch (e: Exception) {
                Logger.e("CameraDataSourceImpl", "Switch to logical camera failed: ${e.message}")
                throw RuntimeException("Switch to logical camera failed: ${e.message}")
            }
        }
    }

    override fun getCurrentLogicalCamera(): LogicalCameraId = currentLogicalId

    override fun getAvailableCameras(): Map<LogicalCameraId, CameraCapability> {
        return capabilityManager.availableCameras
    }

    override fun getCamerasForMode(mode: CameraMode): List<LogicalCameraId> {
        return capabilityManager.getCamerasForMode(mode)
    }

    // ==== 扩展：模式处理器管理 ====
    override fun unregisterModeHandler(mode: CameraMode) {
        Logger.d("CameraDataSourceImpl", "unregisterModeHandler: $mode")
        modeHandlers.remove(mode)
        if (currentMode == mode) {
            currentHandler = null
        }
    }

    override fun getModeHandler(mode: CameraMode): CameraModeHandler? {
        val handler = modeHandlers[mode]
        Logger.d("CameraDataSourceImpl", "getModeHandler($mode) -> ${handler?.javaClass?.simpleName}")
        return handler
    }

    // ==== 扩展：控制器管理 ====
    override fun getOperationManager(): CameraOperationManager = operationManager

    override fun isControllerAvailable(controllerType: ControllerType): Boolean {
        val available = operationManager.isControllerAvailable(controllerType)
        Logger.d("CameraDataSourceImpl", "isControllerAvailable($controllerType) -> $available")
        return available
    }

    override fun getAvailableControllers(mode: CameraMode): List<ControllerType> {
        val list = operationManager.getAvailableControllers(currentLogicalId, mode)
        Logger.d("CameraDataSourceImpl", "getAvailableControllers(logical=$currentLogicalId, mode=$mode) -> ${list.joinToString()}")
        return list
    }

    // ==== 扩展：批量设置 ====
    override suspend fun applyCameraSettings(settings: CameraSettings) {
        Logger.d("CameraDataSourceImpl", "applyCameraSettings called")
        operationManager.applyCameraSettings(settings)

        // Update local state
        settings.zoomRatio?.let { currentZoomRatio = it }
    }

    override suspend fun resetToDefaultSettings() {
        Logger.d("CameraDataSourceImpl", "resetToDefaultSettings called")
        val defaultSettings = CameraSettings(
            focusMode = FocusMode.AUTO,
            zoomRatio = 1.0f,
            exposureCompensation = 0,
            flashMode = FlashMode.OFF,
            whiteBalanceMode = WhiteBalanceMode.AUTO,
            hdrMode = HDRMode.OFF
        )
        applyCameraSettings(defaultSettings)
    }

    // ==== 扩展：高级控制方法 ====
    override suspend fun setFocusMode(mode: FocusMode) {
        // TODO: Implement focus mode setting in FocusController
        Logger.d("CameraDataSourceImpl", "setFocusMode called: $mode (not implemented)")
    }

    override suspend fun setWhiteBalance(mode: WhiteBalanceMode) {
        Logger.d("CameraDataSourceImpl", "setWhiteBalance called: $mode")
        operationManager.getWhiteBalanceController()?.setMode(mode)
    }

    override suspend fun setISO(iso: Int) {
        Logger.d("CameraDataSourceImpl", "setISO called: $iso")
        operationManager.getISOController()?.setValue(iso)
    }

    override suspend fun setShutterSpeed(speedInMicroseconds: Long) {
        // TODO: Implement shutter speed setting
        Logger.d("CameraDataSourceImpl", "setShutterSpeed called: $speedInMicroseconds (not implemented)")
    }

    override suspend fun setHDRMode(mode: HDRMode) {
        // TODO: Implement HDR mode setting  
        Logger.d("CameraDataSourceImpl", "setHDRMode called: $mode (not implemented)")
    }


    // ==== 扩展：能力查询 ====
    override fun isModeSupported(mode: CameraMode): Boolean {
        return capabilityManager.isModeSupported(currentLogicalId, mode)
    }

    override fun isModeSupported(logicalId: LogicalCameraId, mode: CameraMode): Boolean {
        return capabilityManager.isModeSupported(logicalId, mode)
    }

    override fun getCameraCapability(logicalId: LogicalCameraId): CameraCapability? {
        return capabilityManager.availableCameras[logicalId]
    }

    override fun getBestCameraForMode(mode: CameraMode): LogicalCameraId? {
        return capabilityManager.getBestCameraForMode(mode)
    }

    // ==== 扩展：状态查询 ====
    override fun getCurrentSettings(): CameraSettings {
        return CameraSettings(
            focusMode = null, // Would need to query from focus controller
            zoomRatio = currentZoomRatio,
            exposureCompensation = controllerManager.exposureController.getCurrentValue(),
            flashMode = when (flashMode) {
                CameraMetadata.FLASH_MODE_OFF -> FlashMode.OFF
                CameraMetadata.FLASH_MODE_TORCH -> FlashMode.TORCH
                else -> FlashMode.AUTO
            },
            whiteBalanceMode = null, // Would need to query from white balance controller
            iso = null, // Would need to query from ISO controller
            shutterSpeed = null, // Would need to query from shutter speed controller
            hdrMode = null, // Would need to query from HDR controller
        )
    }

    override fun isRecording(): Boolean = isCurrentlyRecording

    override fun getCurrentZoom(): Float = currentZoomRatio

    override fun getMaxZoom(): Float {
        val capability = capabilityManager.availableCameras[currentLogicalId]
        return capability?.maxZoom ?: 1.0f
    }

    // ==== 内部方法更新 ====
}