package com.zyf.camera.data.modehandler

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.view.Surface
import androidx.core.content.FileProvider
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoModeHandler(
    private val context: Context,
    private val cameraManager: CameraManager,
    private val getCameraDevice: () -> CameraDevice?,
    private val getCaptureSession: () -> CameraCaptureSession?,
    private val getFlashMode: () -> Int
) : CameraModeHandler {
    private var mediaRecorder: MediaRecorder? = null
    private var videoFile: File? = null
    private var previewSurface: Surface? = null
    private var recorderSurface: Surface? = null

    override suspend fun startPreview(surfaceTexture: SurfaceTexture) {
        withContext(Dispatchers.IO) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            previewSurface = Surface(surfaceTexture)
            mediaRecorder = MediaRecorder()
            videoFile = createVideoFile()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(videoFile?.absolutePath)
                setVideoEncodingBitRate(10_000_000)
                setVideoFrameRate(30)
                setVideoSize(1920, 1080)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                prepare()
            }
            recorderSurface = mediaRecorder?.surface
            val outputSurfaces = listOf(previewSurface!!, recorderSurface!!)
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(previewSurface!!)
                addTarget(recorderSurface!!)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.FLASH_MODE, getFlashMode())
            }
            device.createCaptureSession(
                outputSurfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        getCaptureSession()?.close()
                        builder.build().let {
                            session.setRepeatingRequest(it, null, null)
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                null
            )
        }
    }

    override suspend fun capture(): Uri? = null

    override suspend fun startRecording(): Uri? {
        return withContext(Dispatchers.IO) {
            mediaRecorder?.start()
            videoFile?.let {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    it
                )
            }
        }
    }

    override suspend fun stopRecording() {
        withContext(Dispatchers.IO) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.reset()
            } catch (_: Exception) {}
        }
    }

    override fun setCameraMode(mode: CameraMode) {}
    override fun close() {
        mediaRecorder?.release()
        mediaRecorder = null
        previewSurface?.release()
        recorderSurface?.release()
        previewSurface = null
        recorderSurface = null
    }

    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("VID_${timeStamp}_", ".mp4", storageDir)
    }
}

