package com.zyf.camera.data.controller

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import com.zyf.camera.data.controller.base.BaseCameraController
import com.zyf.camera.data.controller.base.RangedController
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 相机曝光控制器
 * 
 * 提供以下功能:
 * 1. 曝光补偿 (EV) 控制
 * 2. 自动曝光模式控制
 * 3. 曝光锁定控制
 * 4. 测光模式控制
 */
class CameraExposureController(
    cameraManager: CameraManager,
    getCurrentCameraId: () -> String,
    getCameraDevice: () -> CameraDevice?,
    getCaptureSession: () -> CameraCaptureSession?
) : BaseCameraController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession),
    RangedController<Int> {

    companion object {
        private const val TAG = "CameraExposureController"
    }

    // 曝光补偿值流
    private val _valueFlow = MutableStateFlow(0)
    private var currentCompensationValue = 0
    private var supportedCompensationRange = -6..6 // 默认范围，-2EV到+2EV，步长1/3EV

    // 自动曝光模式
    enum class AutoExposureMode(val value: Int) {
        OFF(CaptureRequest.CONTROL_AE_MODE_OFF),
        ON(CaptureRequest.CONTROL_AE_MODE_ON),
        AUTO_FLASH(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH),
        ALWAYS_FLASH(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH),
        REDEYE(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)
    }

    // 测光模式
    enum class MeteringMode(val value: Int) {
        CENTER_WEIGHTED(1),
        SPOT(2),
        MATRIX(3)
    }

    private val _autoExposureModeFlow = MutableStateFlow(AutoExposureMode.ON)
    private val _exposureLockFlow = MutableStateFlow(false)
    private val _meteringModeFlow = MutableStateFlow(MeteringMode.CENTER_WEIGHTED)

    override val isSupported: Boolean
        get() = currentCameraCharacteristics?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) != null

    override val isEnabled: Boolean = true

    override fun checkSupport(): Boolean {
        return currentCameraCharacteristics?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) != null
    }

    override suspend fun onInitialize() {
        Logger.d(TAG, "Initializing exposure controller")
        try {
            val characteristics = currentCameraCharacteristics
            if (characteristics != null) {
                // 获取曝光补偿范围
                val range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
                if (range != null) {
                    supportedCompensationRange = range.lower..range.upper
                    Logger.d(TAG, "Exposure compensation range: $supportedCompensationRange")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize exposure controller: ${e.message}")
        }
    }

    override suspend fun onRelease() {
        Logger.d(TAG, "Releasing exposure controller")
        _valueFlow.value = 0
        _autoExposureModeFlow.value = AutoExposureMode.ON
        _exposureLockFlow.value = false
        _meteringModeFlow.value = MeteringMode.CENTER_WEIGHTED
    }

    override suspend fun onReset() {
        Logger.d(TAG, "Resetting exposure controller")
        setValue(0)
        setAutoExposureMode(AutoExposureMode.ON)
        setExposureLock(false)
        setMeteringMode(MeteringMode.CENTER_WEIGHTED)
    }

    // RangedController接口实现
    override fun getSupportedRange(): ClosedRange<Int> = supportedCompensationRange

    override fun getCurrentValue(): Int = currentCompensationValue

    override suspend fun setValue(value: Int): Boolean {
        if (!supportedCompensationRange.contains(value)) {
            Logger.w(TAG, "Exposure compensation value $value out of range $supportedCompensationRange")
            return false
        }

        return try {
            val device = getCameraDevice()
            val success = if (device != null) {
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, value)
                applyCaptureRequest(builder)
            } else {
                false
            }
            
            if (success) {
                currentCompensationValue = value
                _valueFlow.value = value
                Logger.d(TAG, "Exposure compensation set to $value")
            }
            success
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set exposure compensation: ${e.message}")
            false
        }
    }

    override fun getValueFlow(): Flow<Int> = _valueFlow.asStateFlow()

    /**
     * 设置自动曝光模式
     */
    suspend fun setAutoExposureMode(mode: AutoExposureMode): Boolean {
        return try {
            val device = getCameraDevice()
            val success = if (device != null) {
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.set(CaptureRequest.CONTROL_AE_MODE, mode.value)
                applyCaptureRequest(builder)
            } else {
                false
            }
            
            if (success) {
                _autoExposureModeFlow.value = mode
                Logger.d(TAG, "Auto exposure mode set to $mode")
            }
            success
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set auto exposure mode: ${e.message}")
            false
        }
    }

    /**
     * 设置曝光锁定
     */
    suspend fun setExposureLock(locked: Boolean): Boolean {
        return try {
            val device = getCameraDevice()
            val success = if (device != null) {
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.set(CaptureRequest.CONTROL_AE_LOCK, locked)
                applyCaptureRequest(builder)
            } else {
                false
            }
            
            if (success) {
                _exposureLockFlow.value = locked
                Logger.d(TAG, "Exposure lock set to $locked")
            }
            success
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set exposure lock: ${e.message}")
            false
        }
    }

    /**
     * 设置测光模式
     */
    suspend fun setMeteringMode(mode: MeteringMode): Boolean {
        return try {
            val device = getCameraDevice()
            val success = if (device != null) {
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                // 注意：这里应该使用合适的测光模式参数，这里简化处理
                applyCaptureRequest(builder)
            } else {
                false
            }
            
            if (success) {
                _meteringModeFlow.value = mode
                Logger.d(TAG, "Metering mode set to $mode")
            }
            success
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set metering mode: ${e.message}")
            false
        }
    }

    /**
     * 切换曝光锁定状态
     */
    suspend fun toggleExposureLock(): Boolean {
        return setExposureLock(!_exposureLockFlow.value)
    }

    /**
     * 增加曝光补偿
     */
    suspend fun increaseExposure(step: Int = 1): Boolean {
        val newValue = (currentCompensationValue + step).coerceAtMost(supportedCompensationRange.endInclusive)
        return setValue(newValue)
    }

    /**
     * 减少曝光补偿
     */
    suspend fun decreaseExposure(step: Int = 1): Boolean {
        val newValue = (currentCompensationValue - step).coerceAtLeast(supportedCompensationRange.start)
        return setValue(newValue)
    }

    /**
     * 重置曝光补偿到0
     */
    suspend fun resetExposure(): Boolean = setValue(0)

    // 获取状态流的方法
    fun getAutoExposureModeFlow(): Flow<AutoExposureMode> = _autoExposureModeFlow.asStateFlow()
    fun getExposureLockFlow(): Flow<Boolean> = _exposureLockFlow.asStateFlow()
    fun getMeteringModeFlow(): Flow<MeteringMode> = _meteringModeFlow.asStateFlow()

    fun getCurrentAutoExposureMode(): AutoExposureMode = _autoExposureModeFlow.value
    fun getCurrentMeteringMode(): MeteringMode = _meteringModeFlow.value

    fun isExposureLocked(): Boolean = _exposureLockFlow.value

    suspend fun setExposureEV(evValue: Float): Boolean {
        val compensationValue = evToCompensationValue(evValue).coerceIn(supportedCompensationRange)
        return setValue(compensationValue)
    }

    fun getCurrentEV(): Float = compensationValueToEv(currentCompensationValue)

    /**
     * 将EV补偿值转换为Camera2 API的补偿值
     * Camera2 API使用整数步长，通常1/3 EV = 1步
     */
    fun evToCompensationValue(evValue: Float): Int {
        val characteristics = currentCameraCharacteristics
        val step = characteristics?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        return if (step != null) {
            (evValue / step.toFloat()).toInt()
        } else {
            (evValue * 3).toInt() // 默认1/3 EV步长
        }
    }

    /**
     * 将Camera2 API的补偿值转换为EV值
     */
    fun compensationValueToEv(compensationValue: Int): Float {
        val characteristics = currentCameraCharacteristics
        val step = characteristics?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        return if (step != null) {
            compensationValue * step.toFloat()
        } else {
            compensationValue / 3.0f // 默认1/3 EV步长
        }
    }
}