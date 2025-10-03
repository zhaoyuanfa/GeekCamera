package com.zyf.camera.data.controller

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import com.zyf.camera.data.controller.base.CameraController
import com.zyf.camera.data.controller.base.ControllerState
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * 相机控制器管理器
 * 统一管理和协调所有的相机控制器
 */
class CameraControllerManager(
    private val cameraManager: CameraManager,
    private val getCurrentCameraId: () -> String,
    private val getCameraDevice: () -> CameraDevice?,
    private val getCaptureSession: () -> CameraCaptureSession?
) {
    private val tag = "CameraControllerManager"
    
    // 所有控制器
    private val _controllers = mutableMapOf<Class<out CameraController>, CameraController>()
    private val _managerState = MutableStateFlow<ManagerState>(ManagerState.Uninitialized)
    
    // 核心控制器
    val flashController: FlashController by lazy {
        getOrCreateController { FlashController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) }
    }
    
    val whiteBalanceController: WhiteBalanceController by lazy {
        getOrCreateController { WhiteBalanceController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) }
    }
    
    val isoController: ISOController by lazy {
        getOrCreateController { ISOController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) }
    }
    
    val zoomController: CameraZoomController by lazy {
        getOrCreateController { CameraZoomController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) }
    }
    
    val focusController: FocusController by lazy {
        getOrCreateController { FocusController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) }
    }
    
    val exposureController: CameraExposureController by lazy {
        getOrCreateController { CameraExposureController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) }
    }

    /**
     * 管理器状态
     */
    sealed class ManagerState {
        object Uninitialized : ManagerState()
        object Initializing : ManagerState()
        object Ready : ManagerState()
        data class Error(val message: String) : ManagerState()
    }

    /**
     * 获取管理器状态流
     */
    fun getManagerStateFlow(): StateFlow<ManagerState> = _managerState.asStateFlow()

    /**
     * 初始化所有控制器
     */
    suspend fun initializeAll() {
        _managerState.value = ManagerState.Initializing
        Logger.d(tag, "Initializing all controllers...")
        
        try {
            val controllers = getAllControllers()
            Logger.d(tag, "Found ${controllers.size} controllers to initialize")
            
            // 并行初始化所有控制器
            controllers.forEach { controller ->
                try {
                    if (controller.isSupported) {
                        controller.initialize()
                        Logger.d(tag, "Initialized ${controller.javaClass.simpleName}")
                    } else {
                        Logger.d(tag, "Skipped ${controller.javaClass.simpleName} (not supported)")
                    }
                } catch (e: Exception) {
                    Logger.e(tag, "Failed to initialize ${controller.javaClass.simpleName}: ${e.message}")
                }
            }
            
            _managerState.value = ManagerState.Ready
            Logger.d(tag, "All controllers initialized")
        } catch (e: Exception) {
            _managerState.value = ManagerState.Error("Failed to initialize controllers: ${e.message}")
            Logger.e(tag, "Failed to initialize controllers: ${e.message}")
        }
    }

    /**
     * 释放所有控制器
     */
    suspend fun releaseAll() {
        Logger.d(tag, "Releasing all controllers...")
        
        getAllControllers().forEach { controller ->
            try {
                controller.release()
                Logger.d(tag, "Released ${controller.javaClass.simpleName}")
            } catch (e: Exception) {
                Logger.e(tag, "Failed to release ${controller.javaClass.simpleName}: ${e.message}")
            }
        }
        
        _controllers.clear()
        _managerState.value = ManagerState.Uninitialized
        Logger.d(tag, "All controllers released")
    }

    /**
     * 重置所有控制器到默认状态
     */
    suspend fun resetAll() {
        Logger.d(tag, "Resetting all controllers...")
        
        getAllControllers().forEach { controller ->
            try {
                if (controller.getStateFlow().value == ControllerState.Ready) {
                    controller.reset()
                    Logger.d(tag, "Reset ${controller.javaClass.simpleName}")
                }
            } catch (e: Exception) {
                Logger.e(tag, "Failed to reset ${controller.javaClass.simpleName}: ${e.message}")
            }
        }
        
        Logger.d(tag, "All controllers reset")
    }

    /**
     * 获取特定类型的控制器
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : CameraController> getController(clazz: Class<T>): T? {
        val controller = _controllers[clazz] as? T
        Logger.d(tag, "getController(${clazz.simpleName}) -> ${controller != null}")
        return controller
    }

    /**
     * 获取所有已注册的控制器
     */
    fun getAllControllers(): List<CameraController> {
        // 触发所有lazy初始化
        flashController
        whiteBalanceController
        isoController
        zoomController
        focusController
        exposureController

        val list = _controllers.values.toList()
        Logger.d(tag, "getAllControllers() -> size=${list.size}")
        return list
    }

    /**
     * 获取支持的控制器列表
     */
    fun getSupportedControllers(): List<CameraController> {
        val supported = getAllControllers().filter { it.isSupported }
        Logger.d(tag, "getSupportedControllers() -> size=${supported.size}")
        return supported
    }

    /**
     * 获取已启用的控制器列表
     */
    fun getEnabledControllers(): List<CameraController> {
        val enabled = getAllControllers().filter { it.isEnabled }
        Logger.d(tag, "getEnabledControllers() -> size=${enabled.size}")
        return enabled
    }

    /**
     * 获取所有控制器状态的组合流
     */
    fun getAllControllerStatesFlow(): Flow<Map<String, ControllerState>> {
        val controllers = getAllControllers()
        if (controllers.isEmpty()) {
            Logger.d(tag, "getAllControllerStatesFlow() -> no controllers")
            return MutableStateFlow(emptyMap<String, ControllerState>()).asStateFlow()
        }
        
        Logger.d(tag, "getAllControllerStatesFlow() -> combining ${controllers.size} controllers")
        return combine(controllers.map { controller -> controller.getStateFlow() }) { states: Array<ControllerState> ->
            controllers.mapIndexed { index, controller ->
                controller.javaClass.simpleName to states[index]
            }.toMap()
        }
    }

    /**
     * 获取所有控制器状态的流
     */
    val allControllersState: Flow<Map<String, ControllerState>>
        get() = combine(
            flashController.getStateFlow(),
            whiteBalanceController.getStateFlow(),
            isoController.getStateFlow(),
            zoomController.getStateFlow(),
            exposureController.getStateFlow()
        ) { flashState,
            whiteBalanceState,
            isoState,
            zoomState,
            exposureState ->
            mapOf(
                "flashMode" to flashState,
                "whiteBalanceMode" to whiteBalanceState,
                "isoValue" to isoState,
                "zoomLevel" to zoomState,
                "exposureCompensation" to exposureState
            )
        }

    /**
     * 获取或创建控制器
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : CameraController> getOrCreateController(factory: () -> T): T {
        val instance = factory()
        val clazz = instance.javaClass as Class<T>
        val controller = _controllers.getOrPut(clazz) {
            Logger.d(tag, "Creating controller: ${clazz.simpleName}")
            instance
        } as T
        if (controller !== instance) {
            Logger.d(tag, "Reusing existing controller: ${clazz.simpleName}")
        }
        return controller
    }

    /**
     * 检查所有控制器是否准备就绪
     */
    fun areAllControllersReady(): Boolean {
        val allReady = getAllControllers().all {
            !it.isSupported || it.getStateFlow().value == ControllerState.Ready
        }
        Logger.d(tag, "areAllControllersReady() -> $allReady")
        return allReady
    }

    /**
     * 获取控制器错误列表
     */
    fun getControllerErrors(): List<Pair<String, String>> {
        val errors = getAllControllers().mapNotNull { controller ->
            val state = controller.getStateFlow().value
            if (state is ControllerState.Error) {
                controller.javaClass.simpleName to state.message
            } else null
        }
        Logger.d(tag, "getControllerErrors() -> size=${errors.size}")
        return errors
    }
}