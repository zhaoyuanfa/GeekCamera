package com.zyf.camera.data.controller.base

import android.hardware.camera2.CameraCaptureSession
import android.os.Handler
import android.os.HandlerThread
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.zyf.camera.utils.Logger

/**
 * 相机控制器基础抽象类
 * 提供通用的实现逻辑
 */
abstract class BaseCameraController(
    protected val cameraManager: CameraManager,
    protected val getCurrentCameraId: () -> String,
    protected val getCameraDevice: () -> CameraDevice?,
    protected val getCaptureSession: () -> CameraCaptureSession?
) : CameraController {

    protected val tag: String = this::class.java.simpleName
    
    private val _stateFlow = MutableStateFlow<ControllerState>(ControllerState.Uninitialized)
    override fun getStateFlow(): StateFlow<ControllerState> = _stateFlow.asStateFlow()
    
    protected val currentCameraCharacteristics: CameraCharacteristics?
        get() = try {
            cameraManager.getCameraCharacteristics(getCurrentCameraId())
        } catch (e: Exception) {
            Logger.e(tag, "Failed to get camera characteristics: ${e.message}")
            null
        }
    
    override suspend fun initialize() {
        _stateFlow.value = ControllerState.Initializing
        try {
            if (checkSupport()) {
                onInitialize()
                _stateFlow.value = ControllerState.Ready
                Logger.d(tag, "Controller initialized successfully")
            } else {
                _stateFlow.value = ControllerState.Error("Hardware not supported")
                Logger.w(tag, "Controller not supported by hardware")
            }
        } catch (e: Exception) {
            _stateFlow.value = ControllerState.Error("Initialization failed", e)
            Logger.e(tag, "Failed to initialize controller: ${e.message}")
        }
    }
    
    override suspend fun release() {
        try {
            onRelease()
            _stateFlow.value = ControllerState.Uninitialized
            Logger.d(tag, "Controller released")
            try {
                _sessionHandlerThread?.quitSafely()
            } catch (_: Exception) {}
            _sessionHandlerThread = null
        } catch (e: Exception) {
            Logger.e(tag, "Failed to release controller: ${e.message}")
        }
    }
    
    override suspend fun reset() {
        try {
            _stateFlow.value = ControllerState.Busy
            onReset()
            _stateFlow.value = ControllerState.Ready
            Logger.d(tag, "Controller reset")
        } catch (e: Exception) {
            _stateFlow.value = ControllerState.Error("Reset failed", e)
            Logger.e(tag, "Failed to reset controller: ${e.message}")
        }
    }
    
    /**
     * 检查硬件是否支持此控制器
     */
    protected abstract fun checkSupport(): Boolean
    
    /**
     * 控制器初始化逻辑
     */
    protected abstract suspend fun onInitialize()
    
    /**
     * 控制器释放逻辑
     */
    protected abstract suspend fun onRelease()
    
    /**
     * 控制器重置逻辑
     */
    protected abstract suspend fun onReset()
    
    /**
     * 创建捕获请求构建器
     */
    protected fun createCaptureRequestBuilder(template: Int = CameraDevice.TEMPLATE_PREVIEW): CaptureRequest.Builder? {
        return try {
            getCameraDevice()?.createCaptureRequest(template)
        } catch (e: Exception) {
            Logger.e(tag, "Failed to create capture request builder: ${e.message}")
            null
        }
    }
    
    /**
     * 应用捕获请求
     */
    protected suspend fun applyCaptureRequest(builder: CaptureRequest.Builder): Boolean {
        return try {
            val session = getCaptureSession()
            if (session != null) {
                val handler = getSessionHandler()
                session.setRepeatingRequest(builder.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
                        Logger.d(tag, "applyCaptureRequest onCaptureStarted ts=$timestamp frame=$frameNumber")
                    }

                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        Logger.d(tag, "applyCaptureRequest onCaptureCompleted")
                    }

                    override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                        Logger.e(tag, "applyCaptureRequest onCaptureFailed: ${failure.reason}")
                    }
                }, handler)
                true
            } else {
                Logger.e(tag, "No active capture session")
                false
            }
        } catch (e: Exception) {
            Logger.e(tag, "Failed to apply capture request: ${e.message}")
            false
        }
    }

    // Session handler thread management for controller callbacks
    private var _sessionHandlerThread: HandlerThread? = null

    private fun ensureSessionHandler() {
        if (_sessionHandlerThread == null) {
            val thread = HandlerThread("ControllerSessionThread-${this::class.java.simpleName}")
            thread.start()
            _sessionHandlerThread = thread
        }
    }

    private fun getSessionHandler(): Handler {
        ensureSessionHandler()
        return Handler(_sessionHandlerThread!!.looper)
    }
    
    /**
     * 设置繁忙状态
     */
    protected fun setBusy(busy: Boolean) {
        _stateFlow.value = if (busy) ControllerState.Busy else ControllerState.Ready
    }
    
    /**
     * 设置错误状态
     */
    protected fun setError(message: String, throwable: Throwable? = null) {
        _stateFlow.value = ControllerState.Error(message, throwable)
    }
}