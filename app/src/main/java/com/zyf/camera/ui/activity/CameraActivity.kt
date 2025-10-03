package com.zyf.camera.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.livedata.observeAsState
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.domain.model.CameraState
import com.zyf.camera.extensions.TAG
import com.zyf.camera.ui.compose.CameraScreen
import com.zyf.camera.ui.compose.CameraPreview
import com.zyf.camera.ui.viewmodel.CameraViewModel
import com.zyf.camera.utils.Logger
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CameraActivity : ComponentActivity() {

    private lateinit var viewModel: CameraViewModel

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private var hasPermissions by mutableStateOf(false)
    private var textureViewRef: TextureView? = null
    private var lastSurfaceTexture: SurfaceTexture? = null
    private var isResumed = false
    private var hasWindowFocusFlag = false
    private var cameraInitTriggered = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            hasPermissions = true
            textureViewRef?.surfaceTexture?.let {
                lastSurfaceTexture = it
                viewModel.onPreviewSurfaceAvailable(it)
            }
            triggerCameraInitIfReady()
        } else {
            hasPermissions = false
            Toast.makeText(this, "Camera and storage permissions are required", Toast.LENGTH_LONG)
                .show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.d(TAG(), "onCreate")
        viewModel = ViewModelProvider(this)[CameraViewModel::class.java]
        checkPermissions()

        setContent {
            val cameraState by viewModel.cameraState.observeAsState()
            val cameraMode by viewModel.cameraMode.observeAsState(CameraMode.PHOTO)
            val recordingTime by viewModel.recordingTime.observeAsState(0L)
            val context = LocalContext.current

            val currentCameraState by rememberUpdatedState(cameraState)

            LaunchedEffect(cameraState) {
                when (val state = cameraState) {
                    is CameraState.Captured -> {
                        Toast.makeText(
                            context, "Image captured: ${state.imageUri}", Toast.LENGTH_SHORT
                        ).show()
                    }

                    is CameraState.RecordingStarted -> {
                        Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
                    }

                    is CameraState.RecordingStopped -> {
                        Toast.makeText(context, "Recording stopped", Toast.LENGTH_SHORT).show()
                    }

                    is CameraState.Error -> {
                        Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                    }

                    is CameraState.CameraSwitched -> {
                        lastSurfaceTexture?.let { viewModel.onPreviewSurfaceAvailable(it) }
                    }

                    else -> Unit
                }
            }

            MaterialTheme {
                CameraScreen(
                    hasPermissions = hasPermissions,
                    cameraMode = cameraMode,
                    cameraState = currentCameraState,
                    recordingTime = recordingTime,
                    onRequestPermissions = {
                        requestPermissionLauncher.launch(requiredPermissions)
                    },
                    onCapture = { viewModel.captureImageOrVideo() },
                    onSwitchCamera = { viewModel.switchCamera() },
                    onSelectMode = { mode -> viewModel.setCameraMode(mode) },
                    preview = {
                        CameraPreview(
                            onTextureViewReady = { textureView -> textureViewRef = textureView },
                            onSurfaceAvailable = { surface ->
                                lastSurfaceTexture = surface
                                viewModel.onPreviewSurfaceAvailable(surface)
                            },
                            onSurfaceSizeChanged = { textureView ->
                                applyPreviewTransform(
                                    textureView
                                )
                            },
                            onSurfaceDestroyed = { lastSurfaceTexture = null })
                    })
            }
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            hasPermissions = true
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun applyPreviewTransform(textureView: TextureView) {
        val viewWidth = textureView.width
        val viewHeight = textureView.height

        if (viewWidth <= 0 || viewHeight <= 0) {
            Logger.w(
                TAG(), "Invalid view dimensions: ${viewWidth}x${viewHeight}, skipping transform"
            )
            return
        }

        val rotation = windowManager.defaultDisplay.rotation
        val isLandscape =
            rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270

        val previewAspectRatio = if (isLandscape) 4f / 3f else 3f / 4f
        val viewAspectRatio = viewWidth.toFloat() / viewHeight.toFloat()

        val scaleX: Float
        val scaleY: Float

        if (viewAspectRatio > previewAspectRatio) {
            scaleY = 1f
            scaleX = previewAspectRatio / viewAspectRatio
        } else {
            scaleX = 1f
            scaleY = viewAspectRatio / previewAspectRatio
        }

        val matrix = Matrix().apply {
            setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        }
        textureView.setTransform(matrix)
    }

    override fun onResume() {
        Logger.d(TAG(), "onResume")
        super.onResume()
        isResumed = true
        if (::viewModel.isInitialized) {
            triggerCameraInitIfReady()
            textureViewRef?.let { textureView ->
                if (textureView.isAvailable) {
                    textureView.surfaceTexture?.let { surfaceTexture ->
                        lastSurfaceTexture = surfaceTexture
                        viewModel.onPreviewSurfaceAvailable(surfaceTexture)
                        textureView.post { applyPreviewTransform(textureView) }
                    }
                }
            }
        }
    }

    override fun onPause() {
        Logger.d(TAG(), "onPause")
        super.onPause()
        isResumed = false
        cameraInitTriggered = false // 重置状态，允许下次重新触发
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hasWindowFocusFlag = hasFocus
        if (hasFocus) {
            triggerCameraInitIfReady()
        }
    }

    private fun triggerCameraInitIfReady() {
        if (!::viewModel.isInitialized) return
        if (!hasPermissions) return
        if (!isResumed) return
        if (!hasWindowFocusFlag) return
        if (cameraInitTriggered) return // 避免重复触发
        
        cameraInitTriggered = true
        // 使用异步方式触发相机初始化，避免阻塞主线程
        // 添加小延迟确保UI完全渲染完成
        lifecycleScope.launch {
            try {
                delay(300) // 300ms延迟，让UI先完成渲染
                viewModel.onHostResumed()
            } catch (e: Exception) {
                Logger.e(TAG(), "Camera initialization failed: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@CameraActivity, "相机初始化失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onStop() {
        Logger.d(TAG(), "onStop")
        super.onStop()
        viewModel.closeCamera()
    }

    override fun onDestroy() {
        Logger.d(TAG(), "onDestroy")
        super.onDestroy()
        if (::viewModel.isInitialized) {
            viewModel.closeCamera()
        }
    }

    override fun finish() {
        Logger.d(TAG(), "finish")
        viewModel.closeCamera()
        super.finish()
    }
}