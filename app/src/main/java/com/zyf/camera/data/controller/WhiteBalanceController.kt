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
 * 白平衡模式
 */
enum class WhiteBalanceMode(val value: Int) {
    AUTO(CaptureRequest.CONTROL_AWB_MODE_AUTO),
    DAYLIGHT(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY(CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    SHADE(CaptureRequest.CONTROL_AWB_MODE_SHADE),
    TUNGSTEN(CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT),
    FLUORESCENT(CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT),
    WARM_FLUORESCENT(CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT);
    
    companion object {
        fun fromValue(value: Int): WhiteBalanceMode? {
            return values().find { it.value == value }
        }
    }
}

/**
 * 白平衡控制器
 */
class WhiteBalanceController(
    cameraManager: CameraManager,
    getCurrentCameraId: () -> String,
    getCameraDevice: () -> CameraDevice?,
    getCaptureSession: () -> CameraCaptureSession?
) : BaseCameraController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession),
    ModeController<WhiteBalanceMode> {

    private val _modeFlow = MutableStateFlow(WhiteBalanceMode.AUTO)
    private var currentMode = WhiteBalanceMode.AUTO
    private var supportedModes = listOf<WhiteBalanceMode>()

    override val isSupported: Boolean
        get() = checkSupport()

    override val isEnabled: Boolean
        get() = currentMode != WhiteBalanceMode.AUTO

    override fun checkSupport(): Boolean {
        val availableModes = currentCameraCharacteristics?.get(
            CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
        )
        return availableModes != null && availableModes.isNotEmpty()
    }

    override suspend fun onInitialize() {
        // 检查支持的白平衡模式
        val availableModes = currentCameraCharacteristics?.get(
            CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
        ) ?: intArrayOf()
        
        supportedModes = WhiteBalanceMode.values().filter { mode ->
            availableModes.contains(mode.value)
        }
        
        Logger.d(tag, "Supported white balance modes: $supportedModes")
        
        // 设置默认模式为AUTO
        if (WhiteBalanceMode.AUTO in supportedModes) {
            setMode(WhiteBalanceMode.AUTO)
        }
    }

    override suspend fun onRelease() {
        // 重置为自动白平衡
        if (WhiteBalanceMode.AUTO in supportedModes) {
            setMode(WhiteBalanceMode.AUTO)
        }
    }

    override suspend fun onReset() {
        if (WhiteBalanceMode.AUTO in supportedModes) {
            setMode(WhiteBalanceMode.AUTO)
        }
    }

    override fun getSupportedModes(): List<WhiteBalanceMode> = supportedModes

    override fun getCurrentMode(): WhiteBalanceMode = currentMode

    override suspend fun setMode(mode: WhiteBalanceMode): Boolean {
        if (!supportedModes.contains(mode)) {
            Logger.w(tag, "Unsupported white balance mode: $mode")
            return false
        }

        setBusy(true)
        
        return try {
            val builder = createCaptureRequestBuilder() ?: return false
            
            builder.set(CaptureRequest.CONTROL_AWB_MODE, mode.value)
            
            val success = applyCaptureRequest(builder)
            if (success) {
                currentMode = mode
                _modeFlow.value = mode
                Logger.d(tag, "White balance mode set to: $mode")
            }
            
            setBusy(false)
            success
        } catch (e: Exception) {
            setError("Failed to set white balance mode", e)
            false
        }
    }

    override fun getModeFlow(): Flow<WhiteBalanceMode> = _modeFlow.asStateFlow()

    /**
     * 获取当前白平衡的色温描述
     */
    fun getColorTemperatureDescription(mode: WhiteBalanceMode): String {
        return when (mode) {
            WhiteBalanceMode.AUTO -> "自动"
            WhiteBalanceMode.DAYLIGHT -> "日光 (~5200K)"
            WhiteBalanceMode.CLOUDY -> "阴天 (~6000K)"
            WhiteBalanceMode.SHADE -> "阴影 (~7000K)"
            WhiteBalanceMode.TUNGSTEN -> "白炽灯 (~3200K)"
            WhiteBalanceMode.FLUORESCENT -> "荧光灯 (~4000K)"
            WhiteBalanceMode.WARM_FLUORESCENT -> "暖荧光灯 (~2700K)"
        }
    }

    /**
     * 快速切换常用白平衡模式
     */
    suspend fun toggleCommonModes(): WhiteBalanceMode {
        val commonModes = listOf(
            WhiteBalanceMode.AUTO,
            WhiteBalanceMode.DAYLIGHT,
            WhiteBalanceMode.CLOUDY,
            WhiteBalanceMode.TUNGSTEN
        ).filter { it in supportedModes }
        
        if (commonModes.isEmpty()) return currentMode
        
        val currentIndex = commonModes.indexOf(currentMode)
        val nextIndex = (currentIndex + 1) % commonModes.size
        val nextMode = commonModes[nextIndex]
        
        setMode(nextMode)
        return nextMode
    }
}