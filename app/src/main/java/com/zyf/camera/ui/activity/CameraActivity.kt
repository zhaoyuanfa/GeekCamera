package com.zyf.camera.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.zyf.camera.R
import com.zyf.camera.databinding.ActivityCameraBinding
import com.zyf.camera.domain.model.CameraMode
import com.zyf.camera.domain.model.CameraState
import com.zyf.camera.ui.viewmodel.CameraViewModel
import dagger.hilt.android.AndroidEntryPoint
import android.view.TextureView
import android.graphics.SurfaceTexture
import com.zyf.camera.utils.Logger
import com.zyf.camera.extensions.TAG

@AndroidEntryPoint
class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var viewModel: CameraViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            setupCamera()
        } else {
            Toast.makeText(this, "Camera and storage permissions are required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.d(TAG(), "onCreate")
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CameraViewModel::class.java]

        checkPermissions()
        setupListeners()
        observeViewModel()
    }

    private fun checkPermissions() {
        Logger.d(TAG(), "checkPermissions")
        val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            Logger.d(TAG(), "All permissions granted")
            setupCamera()
        } else {
            Logger.d(TAG(), "Requesting permissions: $permissionsToRequest")
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun setupCamera() {
        Logger.d(TAG(), "setupCamera")
        binding.texturePreview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                Logger.d(TAG(), "onSurfaceTextureAvailable: $surface, $width x $height")
                viewModel.onPreviewSurfaceAvailable(surface)
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                Logger.d(TAG(), "onSurfaceTextureSizeChanged: $width x $height")
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                Logger.d(TAG(), "onSurfaceTextureDestroyed")
                return true
            }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                // No-op: Not needed for this app
            }
        }
    }

    private fun setupListeners() {
        Logger.d(TAG(), "setupListeners")
        binding.btnCapture.setOnClickListener {
            Logger.d(TAG(), "btnCapture clicked")
            viewModel.captureImageOrVideo()
        }
        binding.btnSwitchCamera.setOnClickListener {
            Logger.d(TAG(), "btnSwitchCamera clicked")
            viewModel.switchCamera()
        }
        binding.btnModePhoto.setOnClickListener {
            Logger.d(TAG(), "btnModePhoto clicked")
            viewModel.setCameraMode(CameraMode.PHOTO)
        }
        binding.btnModeVideo.setOnClickListener {
            Logger.d(TAG(), "btnModeVideo clicked")
            viewModel.setCameraMode(CameraMode.VIDEO)
        }
    }

    private fun observeViewModel() {
        Logger.d(TAG(), "observeViewModel")
        viewModel.cameraState.observe(this) { state ->
            Logger.d(TAG(), "cameraState: $state")
            when (state) {
                is CameraState.Captured -> showCaptureResult(state.imageUri)
                is CameraState.RecordingStarted -> showRecordingStarted()
                is CameraState.RecordingStopped -> showRecordingStopped()
                is CameraState.Error -> showError(state.message)
                is CameraState.Recorded -> {
                    Logger.d(TAG(), "Video recorded: ${state.videoUri}")
                }
                else -> {}
            }
        }
        viewModel.cameraMode.observe(this) { mode ->
            Logger.d(TAG(), "cameraMode: $mode")
            updateModeUI(mode)
        }
        viewModel.recordingTime.observe(this) { time ->
            updateRecordingTime(time)
        }
    }

    private fun showCaptureResult(imageUri: Uri) {
        Logger.d(TAG(), "showCaptureResult: $imageUri")
        Toast.makeText(this, "Image captured: $imageUri", Toast.LENGTH_SHORT).show()
    }

    private fun showRecordingStarted() {
        Logger.d(TAG(), "showRecordingStarted")
        binding.recordingIndicator.visibility = View.VISIBLE
    }

    private fun showRecordingStopped() {
        Logger.d(TAG(), "showRecordingStopped")
        binding.recordingIndicator.visibility = View.GONE
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        Logger.e(TAG(), "showError: $message")
        Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
    }

    private fun updateModeUI(mode: CameraMode) {
        Logger.d(TAG(), "updateModeUI: $mode")
        when (mode) {
            CameraMode.PHOTO -> {
                binding.btnModePhoto.isSelected = true
                binding.btnModeVideo.isSelected = false
                binding.btnCapture.setImageResource(R.drawable.ic_launcher_foreground)
            }
            CameraMode.VIDEO -> {
                binding.btnModePhoto.isSelected = false
                binding.btnModeVideo.isSelected = true
                binding.btnCapture.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }
    }

    private fun updateRecordingTime(timeMillis: Long) {
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(timeMillis)
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(timeMillis) % 60
        binding.txtRecordingTime.text = String.format(
            java.util.Locale.getDefault(),
            "%02d:%02d", minutes, seconds)
    }
}