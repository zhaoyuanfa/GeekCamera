package com.zyf.camera.data.manager

import com.zyf.camera.data.controller.*
import com.zyf.camera.data.model.FocusMode
import com.zyf.camera.data.model.HDRMode
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.domain.model.LogicalCameraId
import com.zyf.camera.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一的相机操作管理器 - 聚合所有控制器并提供统一接口
 * 支持动态注册控制器、能力查询和操作路由
 */
@Singleton
class CameraOperationManager @Inject constructor(
    private val capabilityManager: CameraCapabilityManager
) {
    // 基础控制器
    private var focusController: AdvancedFocusController? = null
    private var zoomController: ZoomController? = null
    private var exposureController: ExposureController? = null
    private var flashController: AdvancedFlashController? = null

    // 扩展控制器
    private var whiteBalanceController: WhiteBalanceController? = null
    private var isoController: ISOController? = null
    private var shutterSpeedController: ShutterSpeedController? = null
    private var oisController: OISController? = null
    private var hdrController: HDRController? = null
    private var sceneDetectionController: SceneDetectionController? = null
    private var videoController: VideoController? = null

    // 自定义控制器注册表
    private val customControllers = mutableMapOf<String, Any>()

    private val tag = "CameraOperationManager"

    /**
     * 注册控制器
     */
    fun registerFocusController(controller: AdvancedFocusController) {
        Logger.d(tag, "Registering FocusController: ${controller}")
        focusController = controller
    }

    fun registerZoomController(controller: ZoomController) {
        Logger.d(tag, "Registering ZoomController: ${controller}")
        zoomController = controller
    }

    fun registerExposureController(controller: ExposureController) {
        Logger.d(tag, "Registering ExposureController: ${controller}")
        exposureController = controller
    }

    fun registerFlashController(controller: AdvancedFlashController) {
        Logger.d(tag, "Registering FlashController: ${controller}")
        flashController = controller
    }

    fun registerWhiteBalanceController(controller: WhiteBalanceController) {
        Logger.d(tag, "Registering WhiteBalanceController: ${controller}")
        whiteBalanceController = controller
    }

    fun registerISOController(controller: ISOController) {
        Logger.d(tag, "Registering ISOController: ${controller}")
        isoController = controller
    }

    fun registerShutterSpeedController(controller: ShutterSpeedController) {
        Logger.d(tag, "Registering ShutterSpeedController: ${controller}")
        shutterSpeedController = controller
    }

    fun registerOISController(controller: OISController) {
        Logger.d(tag, "Registering OISController: ${controller}")
        oisController = controller
    }

    fun registerHDRController(controller: HDRController) {
        Logger.d(tag, "Registering HDRController: ${controller}")
        hdrController = controller
    }

    fun registerSceneDetectionController(controller: SceneDetectionController) {
        Logger.d(tag, "Registering SceneDetectionController: ${controller}")
        sceneDetectionController = controller
    }

    fun registerVideoController(controller: VideoController) {
        Logger.d(tag, "Registering VideoController: ${controller}")
        videoController = controller
    }

    /**
     * 注册自定义控制器
     */
    fun <T : Any> registerCustomController(name: String, controller: T) {
        Logger.d(tag, "Registering custom controller: name=$name, controller=$controller")
        customControllers[name] = controller
    }

    /**
     * 获取自定义控制器
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getCustomController(name: String, clazz: Class<T>): T? {
        val controller = customControllers[name] ?: return null
        val result = if (clazz.isInstance(controller)) clazz.cast(controller) else null
        Logger.d(tag, "Get custom controller: name=$name, clazz=${clazz.simpleName}, found=${result != null}")
        return result
    }

    /**
     * 检查控制器是否可用
     */
    fun isControllerAvailable(controllerType: ControllerType): Boolean {
        val available = when (controllerType) {
            ControllerType.FOCUS -> focusController != null
            ControllerType.ZOOM -> zoomController != null
            ControllerType.EXPOSURE -> exposureController != null
            ControllerType.FLASH -> flashController != null
            ControllerType.WHITE_BALANCE -> whiteBalanceController != null
            ControllerType.ISO -> isoController != null
            ControllerType.SHUTTER_SPEED -> shutterSpeedController != null
            ControllerType.OIS -> oisController != null
            ControllerType.HDR -> hdrController != null
            ControllerType.SCENE_DETECTION -> sceneDetectionController != null
            ControllerType.VIDEO -> videoController != null
        }
        Logger.d(tag, "Controller availability: $controllerType -> $available")
        return available
    }

    /**
     * 获取控制器实例
     */
    fun getFocusController(): AdvancedFocusController? = focusController
    fun getZoomController(): ZoomController? = zoomController
    fun getExposureController(): ExposureController? = exposureController
    fun getFlashController(): AdvancedFlashController? = flashController
    fun getWhiteBalanceController(): WhiteBalanceController? = whiteBalanceController
    fun getISOController(): ISOController? = isoController
    fun getShutterSpeedController(): ShutterSpeedController? = shutterSpeedController
    fun getOISController(): OISController? = oisController
    fun getHDRController(): HDRController? = hdrController
    fun getSceneDetectionController(): SceneDetectionController? = sceneDetectionController
    fun getVideoController(): VideoController? = videoController

    /**
     * 获取指定相机和模式下可用的控制器列表
     */
    fun getAvailableControllers(logicalId: LogicalCameraId, mode: CameraMode): List<ControllerType> {
        Logger.d(tag, "Query available controllers: logicalId=$logicalId, mode=$mode")
        val capability = capabilityManager.availableCameras[logicalId] ?: return emptyList()
        val availableControllers = mutableListOf<ControllerType>()

        // 检查每个控制器是否在当前配置下可用
        if (isControllerAvailable(ControllerType.FOCUS)) {
            availableControllers.add(ControllerType.FOCUS)
        }

        if (isControllerAvailable(ControllerType.ZOOM) && capability.maxZoom > 1.0f) {
            availableControllers.add(ControllerType.ZOOM)
        }

        if (isControllerAvailable(ControllerType.EXPOSURE)) {
            availableControllers.add(ControllerType.EXPOSURE)
        }

        if (isControllerAvailable(ControllerType.FLASH) && capability.hasFlash) {
            availableControllers.add(ControllerType.FLASH)
        }

        if (isControllerAvailable(ControllerType.OIS) && capability.hasOIS) {
            availableControllers.add(ControllerType.OIS)
        }

        // 根据模式添加特定控制器
        when (mode) {
            CameraMode.PRO -> {
                if (isControllerAvailable(ControllerType.ISO)) {
                    availableControllers.add(ControllerType.ISO)
                }
                if (isControllerAvailable(ControllerType.SHUTTER_SPEED)) {
                    availableControllers.add(ControllerType.SHUTTER_SPEED)
                }
                if (isControllerAvailable(ControllerType.WHITE_BALANCE)) {
                    availableControllers.add(ControllerType.WHITE_BALANCE)
                }
            }
            CameraMode.NIGHT -> {
                if (isControllerAvailable(ControllerType.HDR)) {
                    availableControllers.add(ControllerType.HDR)
                }
            }
            CameraMode.VIDEO, CameraMode.SLOW_MOTION, CameraMode.TIME_LAPSE -> {
                if (isControllerAvailable(ControllerType.VIDEO)) {
                    availableControllers.add(ControllerType.VIDEO)
                }
            }
            else -> { /* 其他模式使用基础控制器 */ }
        }

        if (isControllerAvailable(ControllerType.SCENE_DETECTION)) {
            availableControllers.add(ControllerType.SCENE_DETECTION)
        }

        Logger.d(tag, "Available controllers: ${availableControllers.joinToString()}")
        return availableControllers
    }

    /**
     * 批量应用相机设置
     */
    suspend fun applyCameraSettings(settings: CameraSettings) {
        Logger.d(tag, "applyCameraSettings called: $settings")
        settings.focusMode?.let { mode: FocusMode ->
            // TODO: Implement focus mode setting
            Logger.d(tag, "Focus mode set to $mode (not implemented)")
        }

        settings.zoomRatio?.let { ratio ->
            Logger.d(tag, "Applying zoom=$ratio")
            zoomController?.setZoom(ratio)
        }

        settings.exposureCompensation?.let { compensation ->
            Logger.d(tag, "Applying exposureCompensation=$compensation")
            exposureController?.setValue(compensation)
        }

        settings.flashMode?.let { mode ->
            Logger.d(tag, "Applying flashMode=$mode")
            flashController?.setMode(mode)
        }

        settings.whiteBalanceMode?.let { mode ->
            Logger.d(tag, "Applying whiteBalance=$mode")
            whiteBalanceController?.setMode(mode)
        }

        settings.iso?.let { iso ->
            Logger.d(tag, "Applying ISO=$iso")
            isoController?.setValue(iso)
        }

        settings.shutterSpeed?.let { speed ->
            // TODO: Implement shutter speed setting
            Logger.d(tag, "Shutter speed set to $speed (not implemented)")
        }

        settings.hdrMode?.let { mode ->
            // TODO: Implement HDR mode setting
            Logger.d(tag, "HDR mode set to $mode (not implemented)")
        }
    }
}

enum class ControllerType {
    FOCUS, ZOOM, EXPOSURE, FLASH, WHITE_BALANCE, ISO, SHUTTER_SPEED,
    OIS, HDR, SCENE_DETECTION, VIDEO
}

/**
 * 相机设置数据类 - 用于批量应用设置
 */
data class CameraSettings(
    val focusMode: FocusMode? = null,
    val zoomRatio: Float? = null,
    val exposureCompensation: Int? = null,
    val flashMode: FlashMode? = null,
    val whiteBalanceMode: WhiteBalanceMode? = null,
    val iso: Int? = null,
    val shutterSpeed: Long? = null,
    val hdrMode: HDRMode? = null
)
