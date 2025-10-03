package com.zyf.camera.domain.model

import android.net.Uri

sealed class CameraState {
    object Initializing : CameraState()
    object Ready : CameraState()
    object Capturing : CameraState()
    data class Captured(val imageUri: Uri) : CameraState()
    object RecordingStarted : CameraState()
    object RecordingStopped : CameraState()
    data class Recorded(val videoUri: Uri) : CameraState()
    data class Error(val message: String) : CameraState()
    object CameraSwitched : CameraState()
    object FlashToggled : CameraState()
}