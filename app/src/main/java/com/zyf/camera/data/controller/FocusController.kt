package com.zyf.camera.data.controller

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import com.zyf.camera.data.controller.base.BaseCameraController
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FocusController(
    cameraManager: CameraManager,
    getCurrentCameraId: () -> String,
    getCameraDevice: () -> CameraDevice?,
    getCaptureSession: () -> CameraCaptureSession?
) : BaseCameraController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession) {

    companion object {
        private const val TAG = "FocusController"
    }

    private val _focusState = MutableStateFlow(false)
    val focusState = _focusState.asStateFlow()

    private val _isAutoFocus = MutableStateFlow(true)
    val isAutoFocus = _isAutoFocus.asStateFlow()

    override val isSupported = true
    override val isEnabled = true

    override fun checkSupport(): Boolean = true

    override suspend fun onInitialize() {
        Logger.d(TAG, "Initializing focus controller")
        _focusState.value = true
        _isAutoFocus.value = true
    }

    override suspend fun onRelease() {
        Logger.d(TAG, "Releasing focus controller")
        _focusState.value = false
        _isAutoFocus.value = true
    }

    override suspend fun onReset() {
        Logger.d(TAG, "Resetting focus controller")
        _focusState.value = false
        _isAutoFocus.value = true
    }

    fun setAutoFocus(enabled: Boolean) {
        Logger.d(TAG, "setAutoFocus: $enabled")
        _isAutoFocus.value = enabled
    }

    fun performFocus() {
        _focusState.value = !_focusState.value
        Logger.d(TAG, "performFocus -> focusState=${_focusState.value}")
    }

    // 对焦到指定位置
    suspend fun focusAt(x: Float, y: Float): Boolean {
        Logger.d(TAG, "Focus at ($x, $y)")
        return try {
            val device = getCameraDevice()
            if (device != null) {
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                applyCaptureRequest(builder)
            } else {
                false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to focus at position: ${e.message}")
            false
        }
    }
}