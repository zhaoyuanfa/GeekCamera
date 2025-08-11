package com.zyf.camera.ui.activity

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import com.zyf.camera.databinding.ActivityMainBinding
import com.zyf.camera.extensions.TAG
import com.zyf.camera.hardware.camera.CameraManagerProxy
import com.zyf.camera.ui.base.BaseActivity
import com.zyf.camera.ui.component.surface.SurfaceManager
import com.zyf.camera.utils.Logger


@SuppressLint("MissingPermission")
class MainActivity : BaseActivity(), CameraManagerProxy.CameraOperationCallback,
    SurfaceHolder.Callback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManagerProxy: CameraManagerProxy
    private lateinit var surfaceManager: SurfaceManager
    private var previewSurface: Surface? = null
    private var cameraDevice: CameraDevice? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    var previewStarted = false

    private var paused = false

    private lateinit var mRecordHandler: Handler


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater).apply {
            setContentView(root)
        }
        surfaceManager = SurfaceManager(binding)
        surfaceManager.setSurfaceCallback(this)
        cameraManagerProxy = CameraManagerProxy(this)
        cameraManagerProxy.setCameraOperationCallback(this)


        val mRecordThread = HandlerThread("RecordThread")
        mRecordThread.start()
        mRecordHandler = Handler(mRecordThread.looper)
    }

    override fun onResume() {
        super.onResume()
        paused = false
        Logger.d(TAG(), "onResume: E")
        val size = Size(1920, 1080)
        surfaceManager.requestSurface(size)
        cameraManagerProxy.openCamera("0")
        Logger.d(TAG(), "onResume: X")
    }

    override fun onPause() {
        super.onPause()
        Logger.d(TAG(), "onPause: E")
        paused = true
        cameraManagerProxy.closeCamera()
        surfaceManager.onPause()
        previewSurface = null
        Logger.d(TAG(), "onPause: X")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManagerProxy.onDestory()
    }

    override fun onCameraOpened(cameraDevice: CameraDevice) {
        if (previewSurface == null || paused) {
            return
        }
        cameraManagerProxy.createCaptureSession(previewSurface!!)
    }

    override fun onDisconnected(camera: CameraDevice) {
        Logger.d(TAG(), "onDisconnected: Camera disconnected")
    }

    override fun onError(camera: CameraDevice, error: Int) {
        Logger.e(TAG(), "onError: Camera error occurred, error code: $error")
        when (error) {
            CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> {
                Logger.e(TAG(), "Camera device error")
            }
            CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> {
                Logger.e(TAG(), "Camera disabled")
            }
            CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> {
                Logger.e(TAG(), "Camera service error")
            }
            else -> {
                Logger.e(TAG(), "Unknown camera error: $error")
            }
        }
        camera.close()
    }

    override fun onCameraClosed(cameraDevice: CameraDevice) {
        Logger.d(TAG(), "onCameraClosed: Camera closed")
    }

    override fun onCameraSessionCreated(session: CameraCaptureSession) {
        if (paused) {
            return
        }
        cameraManagerProxy.createPreviewRequest()?.let {
            session.setRepeatingRequest(
                it, object : CameraCaptureSession.CaptureCallback() {

                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        if (previewStarted.not()) {
                            Logger.d(TAG(), "onCaptureCompleted: previewStarted = $previewStarted")
                            previewStarted = true
                            onFirstPreviewFrame()
                        }
                    }
                }, null
            )
        }
    }

    override fun onCameraSessionAborted(session: CameraCaptureSession) {
        Logger.w(TAG(), "onCameraSessionAborted: Camera session aborted")
        cameraDevice?.close()
        cameraDevice = null
        previewStarted = false
    }

    override fun onCameraSessionClosed(session: CameraCaptureSession) {
        Logger.d(TAG(), "onCameraSessionClosed: Camera session closed")
        cameraDevice?.close()
        cameraDevice = null
        previewStarted = false
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Logger.d(TAG(), "surfaceCreated: holder = $holder")
    }


    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Logger.d(TAG(), "surfaceChanged: format = $format, width = $width, height = $height")
        previewSurface = holder.surface
        if (cameraDevice != null && !paused) {
            cameraManagerProxy.createCaptureSession(previewSurface!!)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Logger.d(TAG(), "surfaceDestroyed: holder = $holder")
    }

    private fun  onFirstPreviewFrame() {
        Logger.d(TAG(), "onFirstPreviewFrame: First preview frame received")
    }
}
