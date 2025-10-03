package com.zyf.camera.data.controller

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import com.zyf.camera.data.controller.base.BaseCameraController
import com.zyf.camera.utils.Logger

// 高级对焦控制器
typealias AdvancedFocusController = FocusController

// 高级闪光灯控制器  
typealias AdvancedFlashController = FlashController

// 快门速度控制器
class ShutterSpeedController(
    cameraManager: CameraManager,
    getCurrentCameraId: () -> String,
    getCameraDevice: () -> CameraDevice?,
    getCaptureSession: () -> CameraCaptureSession?
) : BaseCameraController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) {
    
    override val isSupported = true
    override val isEnabled = true
    override fun checkSupport(): Boolean = true
    override suspend fun onInitialize() { Logger.d(tag, "onInitialize") }
    override suspend fun onRelease() { Logger.d(tag, "onRelease") }
    override suspend fun onReset() { Logger.d(tag, "onReset") }
}

// 光学防抖控制器
class OISController(
    cameraManager: CameraManager,
    getCurrentCameraId: () -> String,
    getCameraDevice: () -> CameraDevice?,
    getCaptureSession: () -> CameraCaptureSession?
) : BaseCameraController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) {
    
    override val isSupported = true
    override val isEnabled = true
    override fun checkSupport(): Boolean = true
    override suspend fun onInitialize() { Logger.d(tag, "onInitialize") }
    override suspend fun onRelease() { Logger.d(tag, "onRelease") }
    override suspend fun onReset() { Logger.d(tag, "onReset") }
}

// HDR控制器
class HDRController(
    cameraManager: CameraManager,
    getCurrentCameraId: () -> String,
    getCameraDevice: () -> CameraDevice?,
    getCaptureSession: () -> CameraCaptureSession?
) : BaseCameraController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) {
    
    override val isSupported = true
    override val isEnabled = true
    override fun checkSupport(): Boolean = true
    override suspend fun onInitialize() { Logger.d(tag, "onInitialize") }
    override suspend fun onRelease() { Logger.d(tag, "onRelease") }
    override suspend fun onReset() { Logger.d(tag, "onReset") }
}

// 场景检测控制器
class SceneDetectionController(
    cameraManager: CameraManager,
    getCurrentCameraId: () -> String,
    getCameraDevice: () -> CameraDevice?,
    getCaptureSession: () -> CameraCaptureSession?
) : BaseCameraController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) {
    
    override val isSupported = true
    override val isEnabled = true
    override fun checkSupport(): Boolean = true
    override suspend fun onInitialize() { Logger.d(tag, "onInitialize") }
    override suspend fun onRelease() { Logger.d(tag, "onRelease") }
    override suspend fun onReset() { Logger.d(tag, "onReset") }
}

// 视频控制器
class VideoController(
    cameraManager: CameraManager,
    getCurrentCameraId: () -> String,
    getCameraDevice: () -> CameraDevice?,
    getCaptureSession: () -> CameraCaptureSession?
) : BaseCameraController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) {
    
    override val isSupported = true
    override val isEnabled = true
    override fun checkSupport(): Boolean = true
    override suspend fun onInitialize() { Logger.d(tag, "onInitialize") }
    override suspend fun onRelease() { Logger.d(tag, "onRelease") }
    override suspend fun onReset() { Logger.d(tag, "onReset") }
}