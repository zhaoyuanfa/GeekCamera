package com.zyf.camera.data.modehandler

import android.graphics.SurfaceTexture
import android.net.Uri
import com.zyf.camera.domain.model.CameraMode

interface CameraModeHandler {
    suspend fun startPreview(surfaceTexture: SurfaceTexture)
    suspend fun capture(): Uri? // 某些模式可能不支持，返回 null
    suspend fun startRecording(): Uri? // 某些模式可能不支持，返回 null
    suspend fun stopRecording() // 某些模式可能不支持
    fun setCameraMode(mode: CameraMode)
    fun close()
}
