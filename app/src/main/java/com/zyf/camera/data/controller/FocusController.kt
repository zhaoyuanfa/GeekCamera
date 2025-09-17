package com.zyf.camera.data.controller

import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.MeteringRectangle
import android.os.Handler
import android.os.Looper
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FocusController {
    suspend fun focusAt(x: Float, y: Float, mode: CameraMode)
}

class CameraFocusController(
    private val cameraManager: CameraManager,
    private val getCurrentCameraId: () -> String,
    private val getCameraDevice: () -> CameraDevice?,
    private val getCaptureSession: () -> CameraCaptureSession?,
    private val getImageReaderSurface: () -> android.view.Surface?,
    private val getFlashMode: () -> Int
) : FocusController {
    override suspend fun focusAt(x: Float, y: Float, mode: CameraMode) {
        Logger.d("CameraFocusController", "focusAt called, x=$x, y=$y, mode=$mode")
        withContext(Dispatchers.IO) {
            val device = getCameraDevice() ?: throw IllegalStateException("Camera not opened")
            val session = getCaptureSession() ?: throw IllegalStateException("Session not ready")
            val cameraId = getCurrentCameraId()
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val sensorArraySize = characteristics.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?: throw IllegalStateException("No sensor info")
            val focusX = (x * sensorArraySize.width()).toInt()
            val focusY = (y * sensorArraySize.height()).toInt()
            val focusArea = MeteringRectangle(
                (focusX - 100).coerceAtLeast(0),
                (focusY - 100).coerceAtLeast(0),
                200, 200,
                MeteringRectangle.METERING_WEIGHT_MAX
            )
            val template = when (mode) {
                CameraMode.PHOTO -> CameraDevice.TEMPLATE_STILL_CAPTURE
                CameraMode.VIDEO -> CameraDevice.TEMPLATE_RECORD
                else -> CameraDevice.TEMPLATE_PREVIEW
            }
            val builder = device.createCaptureRequest(template).apply {
                getImageReaderSurface()?.let { addTarget(it) }
                set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(focusArea))
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                set(CaptureRequest.FLASH_MODE, getFlashMode())
            }
            Logger.d("CameraFocusController", "focusAt: sending AF trigger, mode=$mode")
            session.capture(builder.build(), null, Handler(Looper.getMainLooper()))
        }
    }
}

