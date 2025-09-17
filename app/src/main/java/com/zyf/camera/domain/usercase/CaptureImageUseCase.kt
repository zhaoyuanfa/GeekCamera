package com.zyf.camera.domain.usercase

import android.net.Uri
import com.zyf.camera.domain.repository.CameraRepository
import javax.inject.Inject
import com.zyf.camera.utils.Logger

class CaptureImageUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(): Uri {
        Logger.d("CaptureImageUseCase", "invoke called")
        return cameraRepository.captureImage()
    }
}