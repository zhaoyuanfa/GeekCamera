package com.zyf.camera.data.controller

import android.graphics.Rect
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

class CameraZoomController(
    cameraManager: CameraManager,
    getCurrentCameraId: () -> String,
    getCameraDevice: () -> CameraDevice?,
    getCaptureSession: () -> CameraCaptureSession?
) : BaseCameraController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession),
    RangedController<Float> {

    private val _valueFlow = MutableStateFlow(1.0f)
    private var currentZoom = 1.0f
    private var maxZoom = 1.0f
    private var activeRect: Rect? = null

    override val isSupported: Boolean
        get() = checkSupport()

    override fun checkSupport(): Boolean {
        val characteristics = currentCameraCharacteristics
        return characteristics?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) != null
    }

    override val isEnabled: Boolean
        get() = currentZoom > 1.0f

    override suspend fun onInitialize() {
        activeRect = currentCameraCharacteristics?.get(
            CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE
        )
        
        maxZoom = currentCameraCharacteristics?.get(
            CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
        ) ?: 1.0f
        
        Logger.d(tag, "Zoom support - Max zoom: $maxZoom, Active rect: $activeRect")
    }

    override suspend fun onRelease() {
        setValue(1.0f) // Reset to no zoom
    }

    override suspend fun onReset() {
        setValue(1.0f)
    }

    override fun getSupportedRange(): ClosedRange<Float> = 1.0f..maxZoom

    override fun getCurrentValue(): Float = currentZoom

    override suspend fun setValue(value: Float): Boolean {
        val clampedZoom = value.coerceIn(1.0f, maxZoom)
        val rect = activeRect ?: return false
        
        setBusy(true)
        
        return try {
            val cropW = (rect.width() / clampedZoom).toInt()
            val cropH = (rect.height() / clampedZoom).toInt()
            val cropLeft = (rect.centerX() - cropW / 2).coerceAtLeast(rect.left)
            val cropTop = (rect.centerY() - cropH / 2).coerceAtLeast(rect.top)
            val cropRect = Rect(cropLeft, cropTop, cropLeft + cropW, cropTop + cropH)

            val builder = createCaptureRequestBuilder() ?: return false
            builder.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
            
            val success = applyCaptureRequest(builder)
            if (success) {
                currentZoom = clampedZoom
                _valueFlow.value = clampedZoom
                Logger.d(tag, "Zoom set to: $clampedZoom")
            }
            
            setBusy(false)
            success
        } catch (e: Exception) {
            setError("Failed to set zoom", e)
            false
        }
    }

    override fun getValueFlow(): Flow<Float> = _valueFlow.asStateFlow()

    /**
     * 缩放到指定倍数
     */
    suspend fun setZoom(zoom: Float): Boolean {
        return setValue(zoom)
    }

    /**
     * 放大
     */
    suspend fun zoomIn(step: Float = 0.5f): Float {
        val newZoom = (currentZoom + step).coerceAtMost(maxZoom)
        setValue(newZoom)
        return newZoom
    }

    /**
     * 缩小
     */
    suspend fun zoomOut(step: Float = 0.5f): Float {
        val newZoom = (currentZoom - step).coerceAtLeast(1.0f)
        setValue(newZoom)
        return newZoom
    }

    /**
     * 获取最大缩放倍数
     */
    fun getMaxZoom(): Float = maxZoom

    /**
     * 是否可以放大
     */
    fun canZoomIn(): Boolean = currentZoom < maxZoom

    /**
     * 是否可以缩小
     */
    fun canZoomOut(): Boolean = currentZoom > 1.0f
}
