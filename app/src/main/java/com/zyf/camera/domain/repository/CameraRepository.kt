package com.zyf.camera.domain.repository

import android.graphics.SurfaceTexture
import android.net.Uri
import com.zyf.camera.domain.model.CameraMode

interface CameraRepository {
    suspend fun initializeCamera()
    suspend fun startPreview(surfaceTexture: SurfaceTexture)
    suspend fun captureImage(): Uri
    suspend fun startRecording(): Uri
    suspend fun stopRecording()
    suspend fun switchCamera()
    suspend fun toggleFlash()
    fun setCameraMode(mode: CameraMode)
    fun close()
}