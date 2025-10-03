package com.zyf.camera.data.repository

import android.graphics.SurfaceTexture
import android.net.Uri
import com.zyf.camera.data.datasource.CameraDataSource
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.domain.repository.CameraRepository
import com.zyf.camera.utils.DispatchersProvider
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CameraRepositoryImpl @Inject constructor(
    private val cameraDataSource: CameraDataSource
) : CameraRepository {

    override suspend fun initializeCamera() {
        Logger.d("CameraRepositoryImpl", "initializeCamera called")
        withContext(DispatchersProvider.io) {
            cameraDataSource.initializeCamera()
        }
    }

    override suspend fun startPreview(surfaceTexture: SurfaceTexture) {
        Logger.d("CameraRepositoryImpl", "startPreview called")
        withContext(DispatchersProvider.io) {
            cameraDataSource.startPreview(surfaceTexture)
        }
    }

    override suspend fun captureImage(): Uri {
        Logger.d("CameraRepositoryImpl", "captureImage called")
        return withContext(DispatchersProvider.io) {
            cameraDataSource.captureImage()
        }
    }

    override suspend fun startRecording(): Uri {
        Logger.d("CameraRepositoryImpl", "startRecording called")
        return withContext(DispatchersProvider.io) {
            cameraDataSource.startRecording()
        }
    }

    override suspend fun stopRecording() {
        Logger.d("CameraRepositoryImpl", "stopRecording called")
        withContext(DispatchersProvider.io) {
            cameraDataSource.stopRecording()
        }
    }

    override suspend fun switchCamera() {
        Logger.d("CameraRepositoryImpl", "switchCamera called")
        withContext(DispatchersProvider.io) {
            cameraDataSource.switchCamera()
        }
    }

    override suspend fun toggleFlash() {
        Logger.d("CameraRepositoryImpl", "toggleFlash called")
        withContext(DispatchersProvider.io) {
            cameraDataSource.toggleFlash()
        }
    }

    override fun setCameraMode(mode: CameraMode) {
        Logger.d("CameraRepositoryImpl", "setCameraMode: $mode")
        cameraDataSource.setCameraMode(mode)
    }

    override suspend fun setZoom(zoom: Float) {
        Logger.d("CameraRepositoryImpl", "setZoom called with value: $zoom")
        withContext(DispatchersProvider.io) {
            cameraDataSource.setZoom(zoom)
        }
    }

    override suspend fun setExposure(exposure: Int) {
        Logger.d("CameraRepositoryImpl", "setExposure called with value: $exposure")
        withContext(DispatchersProvider.io) {
            cameraDataSource.setExposureCompensation(exposure)
        }
    }

    override fun close() {
        Logger.d("CameraRepositoryImpl", "close called")
        cameraDataSource.close()
    }

    /**
     * 获取CameraDataSource实例以便访问新的Controller系统
     * 提供给ViewModel访问底层Controller功能
     */
    fun getCameraDataSource(): CameraDataSource = cameraDataSource
}