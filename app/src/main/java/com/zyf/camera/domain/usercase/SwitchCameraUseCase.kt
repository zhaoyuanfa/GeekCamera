package com.zyf.camera.domain.usercase

import com.zyf.camera.domain.repository.CameraRepository
import com.zyf.camera.utils.Logger
import javax.inject.Inject

class SwitchCameraUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke() {
        Logger.d("SwitchCameraUseCase", "invoke called")
        cameraRepository.switchCamera()
    }
}