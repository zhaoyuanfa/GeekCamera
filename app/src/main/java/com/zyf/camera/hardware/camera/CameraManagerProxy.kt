package com.zyf.camera.hardware.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import com.zyf.camera.utils.Logger
import android.view.Surface
import com.zyf.camera.extensions.TAG

/**
 * 管理所有Camera开启、关闭、切换等操作
 */
class CameraManagerProxy(context: Context) {
    private var cameraOperationThread = HandlerThread("cameraOperationThread")
    private lateinit var cameraOperationHandler: CameraOperationHandler
    private lateinit var cameraManager: CameraManager
    private var cameraMangerProxy: CameraManagerProxy? = null
    private var cameraDevice: CameraDevice? = null
    private val cameraDeviceId = "0"
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var cameraOperationCallback: CameraOperationCallback? = null
    private var cameraState: Int = 0
    private val cameraDeviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Logger.d(TAG(), "onOpened: camera$cameraDeviceId = $camera")
            cameraState = 1
            cameraDevice = camera
            cameraOperationCallback?.onCameraOpened(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            Logger.d(TAG(), "onDisconnected: camera$cameraDeviceId = $camera")
            cameraState = 0
            cameraOperationCallback?.onDisconnected(camera)
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Logger.d(TAG(), "onError: camera$cameraDeviceId = $camera, error = $error")
            cameraState = 0
            cameraOperationCallback?.onError(camera, error)
        }

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
            Logger.d(TAG(), "onClosed: camera$cameraDeviceId = $camera")
        }
    }
    private val SessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Logger.d(TAG(), "onConfigured: session = $session")
            cameraCaptureSession = session
            cameraOperationCallback?.onCameraSessionCreated(session)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Logger.d(TAG(), "onConfigureFailed: session = $session")
            cameraOperationCallback?.onCameraSessionAborted(session)
        }
    }

    init {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraOperationThread.start()
        cameraOperationHandler = CameraOperationHandler(cameraOperationThread.looper)
    }

    fun onDestory() {
        cameraOperationThread.quitSafely()
    }

    inner class CameraOperationHandler(looper: Looper) : Handler(looper) {

        @SuppressLint("MissingPermission")
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                CameraEvent.OPEN.ordinal -> {
                    Logger.d(TAG(), "handleMessage: CameraEvent.OPEN")
                    cameraManager.openCamera(
                        "0",
                        cameraDeviceStateCallback,
                        cameraOperationHandler,
                    )
                }

                CameraEvent.CLOSE.ordinal -> {
                    Logger.d(TAG(), "handleMessage: CameraEvent.CLOSE")
                    cameraDevice?.close()
                    cameraState = 0
                }

                CameraEvent.CREATE_SESSION.ordinal -> {
                    Logger.d(TAG(), "handleMessage: CameraEvent.CREATE_SESSION, surface = ${msg.obj}")
                    val surfaceList = ArrayList<Surface>()
                    surfaceList.add(msg.obj as Surface)
                    cameraDevice?.createCaptureSession(
                        surfaceList,
                        SessionStateCallback,
                        cameraOperationHandler,
                    )
                }

                CameraEvent.ABORT_SESSION.ordinal -> {
                }

                CameraEvent.CLOSE_SESSION.ordinal -> {
                }
            }
        }
    }

    fun setCameraOperationCallback(cameraOperationCallback: CameraOperationCallback) {
        this.cameraOperationCallback = cameraOperationCallback
    }

    fun openCamera(cameraId: String) {
        cameraState = 0
        val message = Message()
        message.what = CameraEvent.OPEN.ordinal
        message.obj = cameraId
        cameraOperationHandler.sendMessage(message)
    }

    fun closeCamera() {
        val message = Message()
        message.what = CameraEvent.CLOSE.ordinal
        cameraOperationHandler.sendMessage(message)
    }

    private var previewSurface: Surface? = null
    fun createCaptureSession(surface: Surface) {
        Logger.d(TAG(), "createCaptureSession: surface = $surface")
        if (cameraState == 0) {
            Logger.d(TAG(), "createCaptureSession: cameraState = $cameraState")
            return
        }
        previewSurface = surface
        val message = Message()
        message.what = CameraEvent.CREATE_SESSION.ordinal
        message.obj = surface
        cameraOperationHandler.sendMessage(message)
    }

    fun createPreviewRequest(): CaptureRequest? {
        return cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
            addTarget(previewSurface!!)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        }?.build()
    }

    interface CameraOperationCallback {
        fun onCameraOpened(cameraDevice: CameraDevice)
        fun onDisconnected(camera: CameraDevice)
        fun onError(camera: CameraDevice, error: Int)
        fun onCameraClosed(cameraDevice: CameraDevice)
        fun onCameraSessionCreated(session: CameraCaptureSession)
        fun onCameraSessionAborted(session: CameraCaptureSession)
        fun onCameraSessionClosed(session: CameraCaptureSession)
    }
}
