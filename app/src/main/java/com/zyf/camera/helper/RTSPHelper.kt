package com.zyf.camera.helper

import android.content.Context
import android.view.SurfaceView
import net.majorkernelpanic.streaming.SessionBuilder

class RTSPHelper {
    fun buildSession(context: Context, mSurfaceView: SurfaceView) {
        SessionBuilder.getInstance()
            .setSurfaceHolder(mSurfaceView.holder)
            .setContext(context)
            .setAudioEncoder(SessionBuilder.AUDIO_AAC)
            .setVideoEncoder(SessionBuilder.VIDEO_H264)
    }
}
