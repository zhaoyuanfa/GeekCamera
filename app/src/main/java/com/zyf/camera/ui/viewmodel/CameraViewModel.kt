package com.zyf.camera.ui.viewmodel

import android.graphics.SurfaceTexture
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.domain.model.CameraState
import com.zyf.camera.domain.usercase.CaptureImageUseCase
import com.zyf.camera.domain.usercase.StartRecordingUseCase
import com.zyf.camera.domain.usercase.StopRecordingUseCase
import com.zyf.camera.domain.usercase.SwitchCameraUseCase
import com.zyf.camera.extensions.TAG
import com.zyf.camera.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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

    private var cameraOpened: Boolean = false
    private var pendingSurfaceTexture: SurfaceTexture? = null

    init {
        Logger.d(TAG(), "init CameraViewModel")
        initializeCamera()
    }

    private fun initializeCamera() {
        Logger.d(TAG(), "initializeCamera")
        viewModelScope.launch {
            try {
                cameraRepository.initializeCamera()
                Logger.d(TAG(), "Camera initialized")
                cameraOpened = true
                // 如果SurfaceTexture已可用，则立即startPreview
                pendingSurfaceTexture?.let {
                    startPreviewIfReady(it)
                }
                _cameraState.postValue(CameraState.Ready)
            } catch (e: Exception) {
                Logger.e(TAG(), "Camera initialization failed: ${e.message}")
                _cameraState.postValue(CameraState.Error(e.message ?: "Camera initialization failed"))
            }
        }
    }

    fun captureImageOrVideo() {
        Logger.d(TAG(), "captureImageOrVideo, mode=${_cameraMode.value}")
        viewModelScope.launch {
            when (_cameraMode.value) {
                CameraMode.PHOTO -> captureImage()
                CameraMode.VIDEO -> {
                    if (_cameraState.value is CameraState.RecordingStarted) {
                        stopRecording()
                    } else {
                        startRecording()
                    }
                }
                else -> {}
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
        viewModelScope.launch {
            try {
                switchCameraUseCase()
                Logger.d(TAG(), "Camera switched")
                _cameraState.postValue(CameraState.CameraSwitched)
            } catch (e: Exception) {
                Logger.e(TAG(), "Switch camera failed: ${e.message}")
                _cameraState.postValue(CameraState.Error(e.message ?: "Switch camera failed"))
            }
        }
    }

    fun setCameraMode(mode: CameraMode) {
        Logger.d(TAG(), "setCameraMode: $mode")
        _cameraMode.value = mode
        cameraRepository.setCameraMode(mode)
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
        viewModelScope.launch {
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
        if (cameraOpened) {
            startPreviewIfReady(surface)
        } else {
            pendingSurfaceTexture = surface
        }
    }

    override fun onCleared() {
        Logger.d(TAG(), "onCleared")
        super.onCleared()
        stopRecordingTimer()
    }
}