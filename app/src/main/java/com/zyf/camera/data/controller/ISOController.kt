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
 * ISO控制器
 */
class ISOController(
    cameraManager: CameraManager,
    getCurrentCameraId: () -> String,
    getCameraDevice: () -> CameraDevice?,
    getCaptureSession: () -> CameraCaptureSession?
) : BaseCameraController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession),
    RangedController<Int> {

    private val _valueFlow = MutableStateFlow(100)
    private var currentValue = 100
    private var supportedRange = 100..1600
    private var isAutoMode = true

    override val isSupported: Boolean
        get() = checkSupport()

    override val isEnabled: Boolean
        get() = !isAutoMode

    override fun checkSupport(): Boolean {
        return currentCameraCharacteristics?.get(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        ) != null
    }

    override suspend fun onInitialize() {
        // 获取ISO支持范围
        val sensitivityRange = currentCameraCharacteristics?.get(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        )
        
        if (sensitivityRange != null) {
            supportedRange = sensitivityRange.lower..sensitivityRange.upper
            currentValue = supportedRange.start
            Logger.d(tag, "Supported ISO range: $supportedRange")
        }
        
        // 默认使用自动ISO
        setAutoMode(true)
    }

    override suspend fun onRelease() {
        setAutoMode(true)
    }

    override suspend fun onReset() {
        setAutoMode(true)
    }

    override fun getSupportedRange(): ClosedRange<Int> = supportedRange

    override fun getCurrentValue(): Int = currentValue

    override suspend fun setValue(value: Int): Boolean {
        val clampedValue = value.coerceIn(supportedRange)
        
        setBusy(true)
        
        return try {
            val builder = createCaptureRequestBuilder() ?: return false
            
            // 设置手动曝光模式
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, clampedValue)
            
            val success = applyCaptureRequest(builder)
            if (success) {
                currentValue = clampedValue
                isAutoMode = false
                _valueFlow.value = clampedValue
                Logger.d(tag, "ISO set to: $clampedValue")
            }
            
            setBusy(false)
            success
        } catch (e: Exception) {
            setError("Failed to set ISO", e)
            false
        }
    }

    override fun getValueFlow(): Flow<Int> = _valueFlow.asStateFlow()

    /**
     * 设置自动ISO模式
     */
    suspend fun setAutoMode(enabled: Boolean): Boolean {
        setBusy(true)
        
        return try {
            val builder = createCaptureRequestBuilder() ?: return false
            
            if (enabled) {
                // 自动曝光模式
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                isAutoMode = true
                Logger.d(tag, "Auto ISO enabled")
            } else {
                // 如果要切换到手动模式，使用当前值
                setValue(currentValue)
                return true
            }
            
            val success = applyCaptureRequest(builder)
            setBusy(false)
            success
        } catch (e: Exception) {
            setError("Failed to set auto ISO mode", e)
            false
        }
    }

    /**
     * 是否为自动模式
     */
    fun isAutoMode(): Boolean = isAutoMode

    /**
     * 获取常用的ISO值列表
     */
    fun getCommonISOValues(): List<Int> {
        val commonValues = listOf(100, 200, 400, 800, 1600, 3200, 6400)
        return commonValues.filter { it in supportedRange }
    }

    /**
     * 设置到下一个常用ISO值
     */
    suspend fun setNextCommonValue(): Int {
        val commonValues = getCommonISOValues()
        if (commonValues.isEmpty()) return currentValue
        
        val currentIndex = commonValues.indexOfFirst { it >= currentValue }
        val nextIndex = if (currentIndex == -1 || currentIndex == commonValues.size - 1) {
            0
        } else {
            currentIndex + 1
        }
        
        val nextValue = commonValues[nextIndex]
        setValue(nextValue)
        return nextValue
    }

    /**
     * 增加ISO（按档位）
     */
    suspend fun increaseISO(): Int {
        val newValue = when {
            currentValue < 200 -> 200
            currentValue < 400 -> 400
            currentValue < 800 -> 800
            currentValue < 1600 -> 1600
            currentValue < 3200 -> 3200
            currentValue < 6400 -> 6400
            else -> supportedRange.endInclusive
        }.coerceIn(supportedRange)
        
        setValue(newValue)
        return newValue
    }

    /**
     * 降低ISO（按档位）
     */
    suspend fun decreaseISO(): Int {
        val newValue = when {
            currentValue > 3200 -> 3200
            currentValue > 1600 -> 1600
            currentValue > 800 -> 800
            currentValue > 400 -> 400
            currentValue > 200 -> 200
            else -> supportedRange.start
        }.coerceIn(supportedRange)
        
        setValue(newValue)
        return newValue
    }
}