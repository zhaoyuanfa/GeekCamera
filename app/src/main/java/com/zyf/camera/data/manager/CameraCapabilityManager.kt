package com.zyf.camera.data.manager

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.zyf.camera.domain.model.CameraCapability
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.domain.model.LogicalCameraId
import com.zyf.camera.domain.model.Size
import com.zyf.camera.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 相机能力管理器 - 负责发现和管理设备上的所有相机
 * 支持逻辑ID映射、多摄像头配置和能力查询
 */
@Singleton
class CameraCapabilityManager @Inject constructor(
    private val context: Context
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val _availableCameras = mutableMapOf<LogicalCameraId, CameraCapability>()

    val availableCameras: Map<LogicalCameraId, CameraCapability>
        get() = _availableCameras.toMap()

    /**
     * 初始化并发现设备上的所有相机
     */
    fun discoverCameras() {
        _availableCameras.clear()

        try {
            val cameraIds = cameraManager.cameraIdList
            Logger.d("CameraCapabilityManager", "Found ${cameraIds.size} cameras: ${cameraIds.joinToString()}")

            for (cameraId in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val capability = buildCameraCapability(cameraId, characteristics)
                _availableCameras[capability.logicalId] = capability

                Logger.d("CameraCapabilityManager", "Mapped camera $cameraId to ${capability.logicalId}")
            }
        } catch (e: Exception) {
            Logger.e("CameraCapabilityManager", "Failed to discover cameras: ${e.message}")
        }
    }

    /**
     * 根据逻辑ID获取物理相机ID
     */
    fun getPhysicalCameraId(logicalId: LogicalCameraId): String? {
        val id = _availableCameras[logicalId]?.physicalId
        Logger.d("CameraCapabilityManager", "getPhysicalCameraId($logicalId) -> $id")
        return id
    }

    /**
     * 获取支持指定模式的相机列表
     */
    fun getCamerasForMode(mode: CameraMode): List<LogicalCameraId> {
        val list = _availableCameras.filter { (_, capability) ->
            capability.supportedModes.contains(mode)
        }.keys.toList()
        Logger.d("CameraCapabilityManager", "getCamerasForMode($mode) -> ${list.joinToString()}")
        return list
    }

    /**
     * 检查相机是否支持指定模式
     */
    fun isModeSupported(logicalId: LogicalCameraId, mode: CameraMode): Boolean {
        val supported = _availableCameras[logicalId]?.supportedModes?.contains(mode) ?: false
        Logger.d("CameraCapabilityManager", "isModeSupported($logicalId, $mode) -> $supported")
        return supported
    }

    /**
     * 获取最佳相机用于指定模式
     */
    fun getBestCameraForMode(mode: CameraMode): LogicalCameraId? {
        val best = when (mode) {
            CameraMode.ULTRA_WIDE -> availableCameras.keys.firstOrNull { it == LogicalCameraId.ULTRA_WIDE }
            CameraMode.TELEPHOTO -> availableCameras.keys.firstOrNull { it == LogicalCameraId.TELEPHOTO }
            CameraMode.MACRO -> availableCameras.keys.firstOrNull { it == LogicalCameraId.MACRO }
            CameraMode.WIDE_ANGLE -> availableCameras.keys.firstOrNull { it == LogicalCameraId.WIDE_ANGLE }
            CameraMode.PORTRAIT -> availableCameras.keys.firstOrNull {
                it == LogicalCameraId.TELEPHOTO || it == LogicalCameraId.MAIN
            }
            else -> LogicalCameraId.MAIN
        } ?: LogicalCameraId.MAIN
        Logger.d("CameraCapabilityManager", "getBestCameraForMode($mode) -> $best")
        return best
    }

    private fun buildCameraCapability(cameraId: String, characteristics: CameraCharacteristics): CameraCapability {
        val lensFacing = characteristics[CameraCharacteristics.LENS_FACING] ?: CameraCharacteristics.LENS_FACING_BACK
        val logicalId = determineLogicalId(cameraId, characteristics)

        // 支持的模式 - 基于相机特性动态确定
        val supportedModes = determineSupportedModes(characteristics, logicalId)

        // 最大变焦
        val maxZoom = characteristics[CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM] ?: 1.0f

        // 闪光灯支持
        val hasFlash = characteristics[CameraCharacteristics.FLASH_INFO_AVAILABLE] ?: false

        // OIS支持
        val hasOIS = characteristics[CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION]?.contains(
            CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON
        ) ?: false

        // 传感器尺寸
        val sensorSize = characteristics[CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE]?.let {
            Size(it.width(), it.height())
        }

        // 光圈值
        val aperture = characteristics[CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES]?.firstOrNull()

        // 焦距
        val focalLength = characteristics[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.firstOrNull()

        return CameraCapability(
            logicalId = logicalId,
            physicalId = cameraId,
            lensFacing = lensFacing,
            supportedModes = supportedModes,
            maxZoom = maxZoom,
            hasFlash = hasFlash,
            hasOIS = hasOIS,
            hasEIS = false, // EIS通常在更高层实现
            maxResolution = sensorSize ?: Size(1920, 1080),
            supportedVideoSizes = listOf(Size(1920, 1080), Size(1280, 720)),
            supportedFps = listOf(30, 60),
            aperture = aperture,
            focalLength = focalLength,
            sensorSize = sensorSize,
            isUltraWide = logicalId == LogicalCameraId.ULTRA_WIDE,
            isTelephoto = logicalId == LogicalCameraId.TELEPHOTO,
            isMacro = logicalId == LogicalCameraId.MACRO
        )
    }

    private fun determineLogicalId(cameraId: String, characteristics: CameraCharacteristics): LogicalCameraId {
        val lensFacing = characteristics[CameraCharacteristics.LENS_FACING]
        val focalLengths = characteristics[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]

        return when (lensFacing) {
            CameraCharacteristics.LENS_FACING_FRONT -> LogicalCameraId.FRONT
            CameraCharacteristics.LENS_FACING_BACK -> {
                // 根据焦距判断镜头类型
                focalLengths?.firstOrNull()?.let { focalLength ->
                    when {
                        focalLength < 20f -> LogicalCameraId.ULTRA_WIDE
                        focalLength > 80f -> LogicalCameraId.TELEPHOTO
                        focalLength in 20f..35f -> LogicalCameraId.WIDE_ANGLE
                        else -> LogicalCameraId.MAIN
                    }
                } ?: when (cameraId) {
                    "2" -> LogicalCameraId.ULTRA_WIDE
                    "3" -> LogicalCameraId.TELEPHOTO
                    "4" -> LogicalCameraId.MACRO
                    else -> LogicalCameraId.MAIN
                }
            }
            else -> LogicalCameraId.MAIN
        }
    }

    private fun determineSupportedModes(characteristics: CameraCharacteristics, logicalId: LogicalCameraId): Set<CameraMode> {
        val modes = mutableSetOf<CameraMode>()

        // 基础模式 - 所有相机都支持
        modes.add(CameraMode.PHOTO)
        modes.add(CameraMode.VIDEO)

        // 根据相机类型添加特定模式
        when (logicalId) {
            LogicalCameraId.MAIN -> {
                modes.addAll(setOf(
                    CameraMode.NIGHT, CameraMode.PORTRAIT, CameraMode.PRO,
                    CameraMode.PANORAMA, CameraMode.TIME_LAPSE, CameraMode.AI_BEAUTY
                ))
            }
            LogicalCameraId.ULTRA_WIDE -> {
                modes.addAll(setOf(
                    CameraMode.ULTRA_WIDE, CameraMode.PANORAMA, CameraMode.WIDE_ANGLE
                ))
            }
            LogicalCameraId.TELEPHOTO -> {
                modes.addAll(setOf(
                    CameraMode.TELEPHOTO, CameraMode.PORTRAIT, CameraMode.SUPER_RESOLUTION
                ))
            }
            LogicalCameraId.MACRO -> {
                modes.add(CameraMode.MACRO)
            }
            LogicalCameraId.FRONT -> {
                modes.addAll(setOf(
                    CameraMode.PORTRAIT, CameraMode.AI_BEAUTY, CameraMode.AR_STICKER
                ))
            }
            else -> {
                // 其他相机类型的默认模式
            }
        }

        // 检查硬件能力添加高级模式
        val availableCapabilities = characteristics[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]
        availableCapabilities?.let { caps ->
            if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                modes.add(CameraMode.PRO)
            }
            if (caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)) {
                modes.add(CameraMode.SLOW_MOTION)
            }
        }

        return modes
    }
}
