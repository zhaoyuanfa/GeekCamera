package com.zyf.camera.data.repository

import android.graphics.SurfaceTexture
import android.net.Uri
import com.zyf.camera.data.datasource.CameraDataSource
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.domain.repository.CameraRepository
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CameraRepositoryImpl @Inject constructor(
    private val cameraDataSource: CameraDataSource
) : CameraRepository {

    override suspend fun initializeCamera() {
        Logger.d("CameraRepositoryImpl", "initializeCamera called")
        withContext(Dispatchers.IO) {
            cameraDataSource.initializeCamera()
        }
    }

    override suspend fun startPreview(surfaceTexture: SurfaceTexture) {
        Logger.d("CameraRepositoryImpl", "startPreview called")
        withContext(Dispatchers.IO) {
            cameraDataSource.startPreview(surfaceTexture)
        }
    }

    override suspend fun captureImage(): Uri {
        Logger.d("CameraRepositoryImpl", "captureImage called")
        return withContext(Dispatchers.IO) {
            cameraDataSource.captureImage()
        }
    }

    override suspend fun startRecording(): Uri {
        Logger.d("CameraRepositoryImpl", "startRecording called")
        return withContext(Dispatchers.IO) {
            cameraDataSource.startRecording()
        }
    }

    override suspend fun stopRecording() {
        Logger.d("CameraRepositoryImpl", "stopRecording called")
        withContext(Dispatchers.IO) {
            cameraDataSource.stopRecording()
        }
    }

    override suspend fun switchCamera() {
        Logger.d("CameraRepositoryImpl", "switchCamera called")
        withContext(Dispatchers.IO) {
            cameraDataSource.switchCamera()
        }
    }

    override suspend fun toggleFlash() {
        Logger.d("CameraRepositoryImpl", "toggleFlash called")
        withContext(Dispatchers.IO) {
            cameraDataSource.toggleFlash()
        }
    }

    override fun setCameraMode(mode: CameraMode) {
        Logger.d("CameraRepositoryImpl", "setCameraMode: $mode")
        cameraDataSource.setCameraMode(mode)
    }

    override fun close() {
        Logger.d("CameraRepositoryImpl", "close called")
        cameraDataSource.close()
    }
}