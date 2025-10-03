package com.zyf.camera.domain.model

/**
 * 相机逻辑ID枚举，支持多摄像头配置
 * 不同设备可能有不同的相机配置，此枚举提供统一的逻辑标识
 */
enum class LogicalCameraId {
    MAIN,           // 主摄像头 (通常是后置主摄)
    FRONT,          // 前置摄像头
    WIDE_ANGLE,     // 广角摄像头
    ULTRA_WIDE,     // 超广角摄像头
    TELEPHOTO,      // 长焦摄像头
    MACRO,          // 微距摄像头
    DEPTH,          // 深度摄像头 (TOF/结构光)
    PERISCOPE,      // 潜望式长焦
    MONOCHROME,     // 黑白摄像头
    THERMAL,        // 热成像摄像头
    INFRARED,       // 红外摄像头
    FISHEYE,        // 鱼眼摄像头
    DUAL_MAIN,      // 双主摄组合
    TRIPLE_CAMERA,  // 三摄组合
    QUAD_CAMERA,    // 四摄组合
    ALL_CAMERAS     // 所有可用摄像头
}

/**
 * 相机特性描述
 */
data class CameraCapability(
    val logicalId: LogicalCameraId,
    val physicalId: String,
    val lensFacing: Int,
    val supportedModes: Set<CameraMode>,
    val maxZoom: Float,
    val hasFlash: Boolean,
    val hasOIS: Boolean,         // 光学防抖
    val hasEIS: Boolean,         // 电子防抖
    val maxResolution: Size,
    val supportedVideoSizes: List<Size>,
    val supportedFps: List<Int>,
    val aperture: Float?,        // 光圈值
    val focalLength: Float?,     // 焦距
    val sensorSize: Size?,       // 传感器尺寸
    val isUltraWide: Boolean = false,
    val isTelephoto: Boolean = false,
    val isMacro: Boolean = false
)

data class Size(val width: Int, val height: Int)
