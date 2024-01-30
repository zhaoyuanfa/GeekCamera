package com.zyf.camera.ui.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.zyf.camera.databinding.ActivityMainBinding
import com.zyf.camera.extensions.TAG
import com.zyf.camera.hardware.camera.CameraManagerProxy
import com.zyf.camera.helper.BluetoothHelper
import com.zyf.camera.ui.base.BaseActivity
import com.zyf.camera.ui.component.surface.SurfaceManager
import net.majorkernelpanic.streaming.Session
import java.net.NetworkInterface


@SuppressLint("MissingPermission")
class MainActivity : BaseActivity(), CameraManagerProxy.CameraOperationCallback,
    SurfaceHolder.Callback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManagerProxy
    private lateinit var surfaceManager: SurfaceManager
    private var previewSurface: Surface? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    var mRTSPSession: Session? = null
    var previewStarted = false

    private var paused = false

    private lateinit var mRecordHandler: Handler

    private lateinit var mBTHelper: BluetoothHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableBluetooth()
        mBTHelper = BluetoothHelper(this)
        mBTHelper.onCreate()

        binding = ActivityMainBinding.inflate(layoutInflater).apply {
            setContentView(root)
        }
        surfaceManager = SurfaceManager(binding)
        surfaceManager.setSurfaceCallback(this)
        cameraManager = CameraManagerProxy(this)
        cameraManager.setCameraOperationCallback(this)


//        this.startService(Intent(this, RtspServer::class.java))

        val mRecordThread = HandlerThread("RecordThread")
        mRecordThread.start()
        mRecordHandler = Handler(mRecordThread.looper)
    }

    override fun onResume() {
        super.onResume()
        paused = false
        Log.d(TAG(), "onResume: ")
        val size = Size(640, 480)
        surfaceManager.requestSurface(size)
        cameraManager.openCamera("0")
    }

    override fun onPause() {
        super.onPause()
        paused = true
        Log.d(TAG(), "onPause: ")
        cameraManager.closeCamera()
        surfaceManager.onPause()
        previewSurface = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.onDestory()
    }

    override fun onCameraOpened(cameraDevice: CameraDevice) {
        if (previewSurface == null) {
            return
        }
        cameraManager.createCaptureSession(previewSurface!!)
    }

    override fun onDisconnected(camera: CameraDevice) {
    }

    override fun onError(camera: CameraDevice, error: Int) {
    }

    override fun onCameraClosed(cameraDevice: CameraDevice) {
    }

    override fun onCameraSessionCreated(session: CameraCaptureSession) {
        if (paused) {
            return
        }
        cameraManager.createPreviewRequest()?.let {
            session.setRepeatingRequest(
                it, object : CameraCaptureSession.CaptureCallback() {

                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        if (previewStarted.not()) {
                            Log.d(TAG(), "onCaptureCompleted: previewStarted = $previewStarted")
                            previewStarted = true
//                            mRTSPSession?.start()

                        }
                    }
                }, null
            )
        }
    }

    override fun onCameraSessionAborted(session: CameraCaptureSession) {
    }

    override fun onCameraSessionClosed(session: CameraCaptureSession) {
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
    }


    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG(), "surfaceChanged: format = $format, width = $width, height = $height")
        previewSurface = holder.surface
        cameraManager.createCaptureSession(previewSurface!!)

//        val sessionBuilder = SessionBuilder.getInstance().setSurfaceHolder(holder).setContext(this)
//            .setAudioEncoder(SessionBuilder.AUDIO_AAC).setVideoEncoder(SessionBuilder.VIDEO_H264)
//            .setCamera(0).setDestination(InetAddress.getByName(getIpAddress()))
//
//        mRTSPSession = sessionBuilder.build().apply {
//            destination = InetAddress.getByName(getIpAddress())
//        }
//        mRecordHandler.postDelayed({
//            mRTSPSession?.start()
//        }, 1000)
//
//        val ipAddress = getIpAddress()
//        val rtspUrl = "rtsp://$ipAddress:${RtspServer.DEFAULT_RTSP_PORT}"
//        Log.d(TAG(), "rtspUrl = $rtspUrl")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }

    private fun getIpAddress(): String {
        var ip = ""
        try {
            val enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (enumNetworkInterfaces.hasMoreElements()) {
                val networkInterface = enumNetworkInterfaces.nextElement()
                val enumInetAddress = networkInterface.inetAddresses
                while (enumInetAddress.hasMoreElements()) {
                    val inetAddress = enumInetAddress.nextElement()
                    if (inetAddress.isSiteLocalAddress) {
                        ip += inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ip += "Something Wrong! " + e.toString() + "\n"
        }
        return ip
    }

    fun enableBluetooth() {
        val bluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(TAG(), "enableBluetooth: ")
            } else {
                Log.d(TAG(), "enableBluetooth: failed")
            }
        }

        // 启动 Activity
        bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
}
