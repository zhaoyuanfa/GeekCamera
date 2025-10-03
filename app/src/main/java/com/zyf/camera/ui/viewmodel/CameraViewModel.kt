package com.zyf.camera.ui.viewmodel

import android.graphics.SurfaceTexture
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyf.camera.data.controller.CameraControllerManager
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.domain.model.CameraState
import com.zyf.camera.domain.usercase.CaptureImageUseCase
import com.zyf.camera.domain.usercase.StartRecordingUseCase
import com.zyf.camera.domain.usercase.StopRecordingUseCase
import com.zyf.camera.domain.usercase.SwitchCameraUseCase
import com.zyf.camera.extensions.TAG
import com.zyf.camera.utils.Logger
import com.zyf.camera.utils.DispatchersProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraRepository: com.zyf.camera.domain.repository.CameraRepository,
    private val captureImageUseCase: CaptureImageUseCase,
    private val startRecordingUseCase: StartRecordingUseCase,
    private val stopRecordingUseCase: StopRecordingUseCase,
    private val switchCameraUseCase: SwitchCameraUseCase
) : ViewModel() {

    private val _previewTexture = MutableLiveData<SurfaceTexture>()
    val previewTexture: LiveData<SurfaceTexture> = _previewTexture

    private val _cameraState = MutableLiveData<CameraState>()
    val cameraState: LiveData<CameraState> = _cameraState

    private val _cameraMode = MutableLiveData(CameraMode.PHOTO)
    val cameraMode: LiveData<CameraMode> = _cameraMode

    private val _recordingTime = MutableLiveData(0L)
    val recordingTime: LiveData<Long> = _recordingTime

    private var recordingStartTime: Long = 0L
    private var recordingTimer: Timer? = null
    private var initializationJob: Job? = null

    private var cameraOpened: Boolean = false
    private var pendingSurfaceTexture: SurfaceTexture? = null
    private var pendingReopen: Boolean = false
    private var initializationAttempts: Int = 0
    private val maxInitializationAttempts = 3

    private fun initializeCamera(force: Boolean = false) {
        if (initializationJob?.isActive == true) {
            Logger.w(TAG(), "initializeCamera already in progress, skipping request")
            return
        }

        if (cameraOpened && !force) {
            Logger.d(TAG(), "Camera already initialized, skipping initializeCamera")
            return
        }

        Logger.d(TAG(), "initializeCamera${if (force) " (forced)" else ""}")
        
        // 设置初始化状态
        if (initializationAttempts == 0 || force) {
            _cameraState.postValue(CameraState.Initializing)
        }
        
        // 确保相机初始化完全在IO线程进行，避免阻塞主线程
        val job = viewModelScope.launch(DispatchersProvider.io) {
            try {
                // 缩短超时时间，如果超时则快速失败而不是长时间等待
                withTimeout(5_000L) { // 5秒超时
                    cameraRepository.initializeCamera()
                }
                Logger.d(TAG(), "Camera initialized successfully")
                cameraOpened = true
                pendingReopen = false
                initializationAttempts = 0 // 成功后重置计数器
                pendingSurfaceTexture?.let {
                    Logger.d(TAG(), "Resuming pending preview after initialization")
                    startPreviewIfReady(it)
                }
                _cameraState.postValue(CameraState.Ready)
            } catch (e: TimeoutCancellationException) {
                Logger.e(TAG(), "Camera initialization timeout (attempt $initializationAttempts)")
                handleInitializationFailure("相机初始化超时，请检查相机权限", e)
            } catch (e: SecurityException) {
                Logger.e(TAG(), "Camera permission denied: ${e.message}")
                cameraOpened = false
                initializationAttempts = 0 // 权限问题不重试
                _cameraState.postValue(CameraState.Error("相机权限被拒绝"))
            } catch (e: Exception) {
                Logger.e(TAG(), "Camera initialization failed: ${e.message} (attempt $initializationAttempts)")
                handleInitializationFailure(e.message ?: "相机初始化失败", e)
            } finally {
                initializationJob = null
            }
        }
        initializationJob = job
    }

    private suspend fun handleInitializationFailure(message: String, exception: Throwable) {
        initializationAttempts++
        cameraOpened = false
        
        if (initializationAttempts < maxInitializationAttempts) {
            Logger.w(TAG(), "Retrying camera initialization (attempt $initializationAttempts/$maxInitializationAttempts)")
            // 等待一段时间后重试
            delay(1000L * initializationAttempts) // 递增延迟
            initializeCamera(force = true)
        } else {
            Logger.e(TAG(), "Camera initialization failed after $maxInitializationAttempts attempts")
            initializationAttempts = 0
            _cameraState.postValue(CameraState.Error("$message (已重试${maxInitializationAttempts}次)"))
        }
    }

    fun ensureCameraInitialized() {
        Logger.d(TAG(), "ensureCameraInitialized")
        if (!cameraOpened) {
            initializeCamera()
        } else {
            Logger.d(TAG(), "ensureCameraInitialized skipped, cameraOpened=$cameraOpened")
        }
    }

    fun captureImageOrVideo() {
        Logger.d(TAG(), "captureImageOrVideo, mode=${_cameraMode.value}")
        viewModelScope.launch(DispatchersProvider.io) {
            when (_cameraMode.value) {
                CameraMode.PHOTO -> captureImage()
                CameraMode.VIDEO -> {
                    if (_cameraState.value is CameraState.RecordingStarted) {
                        stopRecording()
                    } else {
                        startRecording()
                    }
                }
                CameraMode.NIGHT -> {/* TODO: Implement NIGHT mode capture */}
                CameraMode.PORTRAIT -> {/* TODO: Implement PORTRAIT mode capture */}
                CameraMode.PANORAMA -> {/* TODO: Implement PANORAMA mode capture */}
                CameraMode.SLOW_MOTION -> {/* TODO: Implement SLOW_MOTION mode capture */}
                CameraMode.TIME_LAPSE -> {/* TODO: Implement TIME_LAPSE mode capture */}
                CameraMode.PRO -> {/* TODO: Implement PRO mode capture */}
                CameraMode.MACRO -> {/* TODO: Implement MACRO mode capture */}
                CameraMode.WIDE_ANGLE -> {/* TODO: Implement WIDE_ANGLE mode capture */}
                CameraMode.TELEPHOTO -> {/* TODO: Implement TELEPHOTO mode capture */}
                CameraMode.ULTRA_WIDE -> {/* TODO: Implement ULTRA_WIDE mode capture */}
                CameraMode.DUAL_CAMERA -> {/* TODO: Implement DUAL_CAMERA mode capture */}
                CameraMode.AI_BEAUTY -> {/* TODO: Implement AI_BEAUTY mode capture */}
                CameraMode.DOCUMENT -> {/* TODO: Implement DOCUMENT mode capture */}
                CameraMode.QR_CODE -> {/* TODO: Implement QR_CODE mode capture */}
                CameraMode.AR_STICKER -> {/* TODO: Implement AR_STICKER mode capture */}
                CameraMode.LIVE_FOCUS -> {/* TODO: Implement LIVE_FOCUS mode capture */}
                CameraMode.SUPER_RESOLUTION -> {/* TODO: Implement SUPER_RESOLUTION mode capture */}
                null -> TODO()
            }
        }
    }

    private suspend fun captureImage() {
        Logger.d(TAG(), "captureImage")
        _cameraState.postValue(CameraState.Capturing)
        try {
            val imageUri = captureImageUseCase()
            Logger.d(TAG(), "Image captured: $imageUri")
            _cameraState.postValue(CameraState.Captured(imageUri))
        } catch (e: Exception) {
            Logger.e(TAG(), "Capture failed: ${e.message}")
            _cameraState.postValue(CameraState.Error(e.message ?: "Capture failed"))
        }
    }

    private suspend fun startRecording() {
        Logger.d(TAG(), "startRecording")
        try {
            val videoUri = startRecordingUseCase()
            Logger.d(TAG(), "Recording started: $videoUri")
            _cameraState.postValue(CameraState.RecordingStarted)
            startRecordingTimer()
        } catch (e: Exception) {
            Logger.e(TAG(), "Recording start failed: ${e.message}")
            _cameraState.postValue(CameraState.Error(e.message ?: "Recording start failed"))
        }
    }

    private suspend fun stopRecording() {
        Logger.d(TAG(), "stopRecording")
        try {
            stopRecordingUseCase()
            Logger.d(TAG(), "Recording stopped")
            _cameraState.postValue(CameraState.RecordingStopped)
            stopRecordingTimer()
        } catch (e: Exception) {
            Logger.e(TAG(), "Recording stop failed: ${e.message}")
            _cameraState.postValue(CameraState.Error(e.message ?: "Recording stop failed"))
        }
    }

    fun switchCamera() {
        Logger.d(TAG(), "switchCamera")
        viewModelScope.launch(DispatchersProvider.io) {
            try {
                switchCameraUseCase()
                Logger.d(TAG(), "Camera switched")
                _cameraState.postValue(CameraState.CameraSwitched)
                // 切换摄像头后自动尝试预览
                val surface = pendingSurfaceTexture ?: previewTexture.value
                if (surface != null) {
                    Logger.d(TAG(), "Auto start preview after camera switch, surface=$surface")
                    startPreviewIfReady(surface)
                } else {
                    Logger.d(TAG(), "No SurfaceTexture available after camera switch, waiting for onPreviewSurfaceAvailable")
                }
            } catch (e: Exception) {
                Logger.e(TAG(), "Switch camera failed: ${e.message}")
                _cameraState.postValue(CameraState.Error(e.message ?: "Switch camera failed"))
            }
        }
    }

    fun setCameraMode(mode: CameraMode) {
        Logger.d(TAG(), "setCameraMode: $mode, current mode: ${_cameraMode.value}")
        _cameraMode.value = mode
        Logger.d(TAG(), "Calling cameraRepository.setCameraMode($mode)")
        cameraRepository.setCameraMode(mode)
        Logger.d(TAG(), "setCameraMode complete, new mode: ${_cameraMode.value}")
    }

    private fun startRecordingTimer() {
        Logger.d(TAG(), "startRecordingTimer")
        recordingStartTime = System.currentTimeMillis()
        recordingTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    val elapsedTime = System.currentTimeMillis() - recordingStartTime
                    _recordingTime.postValue(elapsedTime)
                }
            }, 0, 1000)
        }
    }

    private fun stopRecordingTimer() {
        Logger.d(TAG(), "stopRecordingTimer")
        recordingTimer?.cancel()
        recordingTimer = null
        _recordingTime.postValue(0L)
    }

    private fun startPreviewIfReady(surface: SurfaceTexture) {
        Logger.d(TAG(), "startPreviewIfReady: $surface")
        viewModelScope.launch(DispatchersProvider.io) {
            try {
                cameraRepository.startPreview(surface)
                Logger.d(TAG(), "Preview started")
                pendingSurfaceTexture = null
            } catch (e: Exception) {
                Logger.e(TAG(), "Start preview failed: ${e.message}")
                _cameraState.postValue(CameraState.Error(e.message ?: "Start preview failed"))
            }
        }
    }

    fun onPreviewSurfaceAvailable(surface: SurfaceTexture) {
        Logger.d(TAG(), "onPreviewSurfaceAvailable: $surface")
        // Save the latest surface so we can reuse it after camera switches
        // This callback runs on the main/UI thread, use setValue to make it immediately available
        _previewTexture.value = surface
        
        // 异步处理预览启动，避免阻塞主线程
        viewModelScope.launch(DispatchersProvider.io) {
            if (cameraOpened) {
                startPreviewIfReady(surface)
            } else {
                pendingSurfaceTexture = surface
                // 如果相机还未打开，尝试初始化
                ensureCameraInitialized()
            }
        }
        Logger.d(TAG(), "onPreviewSurfaceAvailable complete, cameraOpened=$cameraOpened")
    }

    /**
     * 重新初始化相机（用于应用恢复时）
     */
    fun reopenCamera() {
        Logger.d(TAG(), "reopenCamera")
        if (initializationJob?.isActive == true) {
            Logger.w(TAG(), "reopenCamera requested while initialization is running, ignoring")
            return
        }

        viewModelScope.launch(DispatchersProvider.io) {
            try {
                if (cameraOpened) {
                    Logger.w(TAG(), "Camera marked as opened, closing before reopen")
                    cameraRepository.close()
                    cameraOpened = false
                }
            } catch (e: Exception) {
                Logger.e(TAG(), "Error closing camera during reopen: ${e.message}")
            }

            _previewTexture.value?.let { surface ->
                pendingSurfaceTexture = surface
            }

            initializeCamera(force = true)
        }
    }

    /**
     * 关闭相机资源
     */
    fun closeCamera() {
        Logger.d(TAG(), "closeCamera")
        viewModelScope.launch(DispatchersProvider.io) {
            try {
                if (initializationJob?.isActive == true) {
                    Logger.w(TAG(), "reopenCamera requested while initialization running, scheduling after init")
                    pendingReopen = true
                    return@launch
                }

                initializationJob?.cancel()
                initializationJob = null
                // 如果正在录制，先停止录制
                if (_cameraState.value == CameraState.RecordingStarted) {
                    stopRecording()
                }
                // 关闭相机资源
                cameraRepository.close()
                cameraOpened = false
                initializeCamera(force = true)
            } catch (e: Exception) {
                Logger.e(TAG(), "Error closing camera: ${e.message}")
            }
        }
    }

    /**
     * 获取CameraDataSource以便访问新的Controller系统
     * 只在相机已初始化后调用此方法
     */
    fun getCameraDataSource(): com.zyf.camera.data.datasource.CameraDataSource {
        if (!cameraOpened) {
            throw IllegalStateException("Camera not initialized. Call initializeCamera first.")
        }
        return (cameraRepository as com.zyf.camera.data.repository.CameraRepositoryImpl).getCameraDataSource()
    }

    fun getCameraControllerManager(): CameraControllerManager {
        return getCameraDataSource().getCameraControllerManager()
    }

    /**
     * 等待相机初始化完成
     * 用于确保Controller系统已准备就绪
     */
    suspend fun waitForInitialization() {
        Logger.d(TAG(), "waitForInitialization")
        while (!cameraOpened) {
            kotlinx.coroutines.delay(100)
        }
    }

    fun onHostResumed() {
        if (cameraOpened) {
            reopenCamera()
        } else {
            ensureCameraInitialized()
        }
    }

    override fun onCleared() {
        Logger.d(TAG(), "onCleared")
        super.onCleared()
        stopRecordingTimer()
        // 在ViewModel销毁时关闭相机资源
        closeCamera()
    }
}