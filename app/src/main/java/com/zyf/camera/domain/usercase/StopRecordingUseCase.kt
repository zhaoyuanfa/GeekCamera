package com.zyf.camera.domain.usercase

import com.zyf.camera.domain.repository.CameraRepository
import javax.inject.Inject
import com.zyf.camera.utils.Logger

class StopRecordingUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke() {
        Logger.d("StopRecordingUseCase", "invoke called")
        cameraRepository.stopRecording()
    }
}