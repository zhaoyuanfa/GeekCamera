package com.zyf.camera.data.controller

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import com.zyf.camera.data.controller.base.BaseCameraController
import com.zyf.camera.data.controller.base.ModeController
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 闪光灯模式
 */
enum class FlashMode {
    OFF,    // 关闭
    ON,     // 强制开启
    AUTO,   // 自动
    TORCH   // 手电筒模式
}

/**
 * 闪光灯控制器
 */
class FlashController(
    cameraManager: CameraManager,
    getCurrentCameraId: () -> String,
    getCameraDevice: () -> CameraDevice?,
    getCaptureSession: () -> CameraCaptureSession?
) : BaseCameraController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession),
    ModeController<FlashMode> {

    private val _modeFlow = MutableStateFlow(FlashMode.OFF)
    private var currentMode = FlashMode.OFF
    private var supportedModes = listOf<FlashMode>()

    override val isSupported: Boolean
        get() = checkSupport()

    override val isEnabled: Boolean
        get() = currentMode != FlashMode.OFF

    override fun checkSupport(): Boolean {
        return currentCameraCharacteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    override suspend fun onInitialize() {
        // 检查支持的闪光灯模式
        supportedModes = mutableListOf<FlashMode>().apply {
            add(FlashMode.OFF) // 所有相机都支持关闭闪光灯
            
            if (isSupported) {
                add(FlashMode.ON)   // 强制开启
                add(FlashMode.AUTO) // 自动模式
                
                // 检查是否支持手电筒模式
                val availableAeModes = currentCameraCharacteristics?.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES
                )
                if (availableAeModes?.contains(CaptureRequest.CONTROL_AE_MODE_ON) == true) {
                    add(FlashMode.TORCH)
                }
            }
        }
        
        Logger.d(tag, "Supported flash modes: $supportedModes")
    }

    override suspend fun onRelease() {
        // 关闭闪光灯
        setMode(FlashMode.OFF)
    }

    override suspend fun onReset() {
        setMode(FlashMode.OFF)
    }

    override fun getSupportedModes(): List<FlashMode> = supportedModes

    override fun getCurrentMode(): FlashMode = currentMode

    override suspend fun setMode(mode: FlashMode): Boolean {
        if (!supportedModes.contains(mode)) {
            Logger.w(tag, "Unsupported flash mode: $mode")
            return false
        }

        setBusy(true)
        
        return try {
            val builder = createCaptureRequestBuilder() ?: return false
            
            when (mode) {
                FlashMode.OFF -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
                FlashMode.ON -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                }
                FlashMode.AUTO -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                }
                FlashMode.TORCH -> {
                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
            }
            
            val success = applyCaptureRequest(builder)
            if (success) {
                currentMode = mode
                _modeFlow.value = mode
                Logger.d(tag, "Flash mode set to: $mode")
            }
            
            setBusy(false)
            success
        } catch (e: Exception) {
            setError("Failed to set flash mode", e)
            false
        }
    }

    override fun getModeFlow(): Flow<FlashMode> = _modeFlow.asStateFlow()

    /**
     * 快速切换闪光灯模式（循环切换常用模式）
     */
    suspend fun toggleMode(): FlashMode {
        val commonModes = listOf(FlashMode.OFF, FlashMode.AUTO, FlashMode.ON)
            .filter { it in supportedModes }
        
        if (commonModes.isEmpty()) return currentMode
        
        val currentIndex = commonModes.indexOf(currentMode)
        val nextIndex = (currentIndex + 1) % commonModes.size
        val nextMode = commonModes[nextIndex]
        
        setMode(nextMode)
        return nextMode
    }

    /**
     * 手电筒模式快捷开关
     */
    suspend fun toggleTorch(): Boolean {
        return if (FlashMode.TORCH in supportedModes) {
            val targetMode = if (currentMode == FlashMode.TORCH) FlashMode.OFF else FlashMode.TORCH
            setMode(targetMode)
        } else {
            Logger.w(tag, "Torch mode not supported")
            false
        }
    }
}