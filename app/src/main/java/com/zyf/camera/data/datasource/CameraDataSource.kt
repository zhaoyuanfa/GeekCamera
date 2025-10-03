package com.zyf.camera.data.datasource

import android.graphics.SurfaceTexture
import android.net.Uri
import com.zyf.camera.data.controller.*
import com.zyf.camera.data.controller.base.ControllerState
import com.zyf.camera.data.manager.CameraOperationManager
import com.zyf.camera.data.manager.CameraSettings
import com.zyf.camera.data.manager.ControllerType
import com.zyf.camera.data.modehandler.CameraModeHandler
import com.zyf.camera.data.model.FocusMode
import com.zyf.camera.data.model.HDRMode
import com.zyf.camera.data.controller.CameraControllerManager
import com.zyf.camera.domain.model.CameraCapability
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.domain.model.LogicalCameraId
import kotlinx.coroutines.flow.Flow

interface CameraDataSource {
    // 基础相机操作
    suspend fun initializeCamera()
    suspend fun startPreview(surfaceTexture: SurfaceTexture)
    suspend fun captureImage(): Uri
    suspend fun startRecording(): Uri
    suspend fun stopRecording()
    suspend fun switchCamera()
    suspend fun toggleFlash()
    fun setCameraMode(mode: CameraMode)
    fun close()

    // 扩展：逻辑相机ID支持
    suspend fun switchToLogicalCamera(logicalId: LogicalCameraId)
    fun getCurrentLogicalCamera(): LogicalCameraId
    fun getAvailableCameras(): Map<LogicalCameraId, CameraCapability>
    fun getCamerasForMode(mode: CameraMode): List<LogicalCameraId>

    // 扩展：模式处理器管理
    fun registerModeHandler(mode: CameraMode, handler: CameraModeHandler)
    fun unregisterModeHandler(mode: CameraMode)
    fun getModeHandler(mode: CameraMode): CameraModeHandler?

    // 扩展：控制器管理
    fun getOperationManager(): CameraOperationManager
    fun isControllerAvailable(controllerType: ControllerType): Boolean
    fun getAvailableControllers(mode: CameraMode): List<ControllerType>
    fun getCameraControllerManager(): CameraControllerManager
    fun getAllControllerStates(): Flow<Map<String, ControllerState>>

    // 扩展：批量设置
    suspend fun applyCameraSettings(settings: CameraSettings)
    suspend fun resetToDefaultSettings()

    // 基础控制方法（保持向后兼容）
    suspend fun focusAt(x: Float, y: Float)
    suspend fun setZoom(zoom: Float)
    suspend fun setExposureCompensation(value: Int)

    // 扩展：高级控制方法
    suspend fun setFocusMode(mode: FocusMode)
    suspend fun setWhiteBalance(mode: WhiteBalanceMode)
    suspend fun setISO(iso: Int)
    suspend fun setShutterSpeed(speedInMicroseconds: Long)
    suspend fun setHDRMode(mode: HDRMode)

    // 扩展：能力查询
    fun isModeSupported(mode: CameraMode): Boolean
    fun isModeSupported(logicalId: LogicalCameraId, mode: CameraMode): Boolean
    fun getCameraCapability(logicalId: LogicalCameraId): CameraCapability?
    fun getBestCameraForMode(mode: CameraMode): LogicalCameraId?

    // 扩展：状态查询
    fun getCurrentSettings(): CameraSettings
    fun isRecording(): Boolean
    fun getCurrentZoom(): Float
    fun getMaxZoom(): Float
}