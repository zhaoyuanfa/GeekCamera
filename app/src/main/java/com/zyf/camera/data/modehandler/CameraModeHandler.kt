package com.zyf.camera.data.modehandler

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.net.Uri
import com.zyf.camera.domain.model.CameraMode

/**
 * A richer mode handler interface. Implementations are responsible for wiring capture session
 * configuration, producing capture/start-recording/stop-recording behavior and releasing resources.
 *
 * Lifecycle:
 *  - onAttach will be called when the CameraDevice is available (without forcing a camera reopen)
 *  - onConfigureSession should return the list of Surfaces required by the mode so the owner can
 *    create/configure a CameraCaptureSession. Implementations should also be able to handle
 *    being attached/detached multiple times.
 */
interface CameraModeHandler {
    suspend fun onAttach(cameraDevice: CameraDevice, currentSession: CameraCaptureSession?)
    suspend fun onDetach()

    /** Return the surfaces this handler needs for a capture session. */
    fun requiredSurfaces(surfaceTexture: SurfaceTexture): List<android.view.Surface>

    /** Called after a CameraCaptureSession is created and configured. */
    suspend fun onSessionConfigured(session: CameraCaptureSession)

    /** Start the preview flow for this mode (should be a no-op if session already configured). */
    suspend fun startPreview(surfaceTexture: SurfaceTexture)

    /** Capture image if supported, otherwise throw or return null. */
    suspend fun capture(): Uri?

    /** Recording lifecycle. Return Uri when recording starts. */
    suspend fun startRecording(): Uri?
    suspend fun stopRecording(): Uri?

    /** Optional query helpers */
    fun supportsCapture(): Boolean
    fun supportsRecording(): Boolean

    fun setCameraMode(mode: CameraMode)
    fun close()
}
