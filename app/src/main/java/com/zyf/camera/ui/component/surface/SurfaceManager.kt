package com.zyf.camera.ui.component.surface

import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.zyf.camera.databinding.ActivityMainBinding
import com.zyf.camera.extensions.TAG

class SurfaceManager(binding: ActivityMainBinding) {
    private var surfaceView: SurfaceView
    private var surfaceCallback: SurfaceHolder.Callback? = null
    private var previewSize: Size? = null
    private var mSurfaceCallBack = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG(), "surfaceCreated: ")
            surfaceCallback?.surfaceCreated(holder)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.d(TAG(), "surfaceChanged: format = $format, width = $width, height = $height")
            if (previewSize == null) {
                return
            }
            surfaceCallback?.surfaceChanged(holder, format, width, height)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG(), "surfaceDestroyed: ")
            surfaceCallback?.surfaceDestroyed(holder)
        }
    }

    init {
        surfaceView = binding.surfaceView
        surfaceView.holder.addCallback(mSurfaceCallBack)
    }

    fun requestSurface(size: Size) {
        Log.d(TAG(), "requestSurface: size = $size")
        previewSize = size
        Log.d(
            TAG(),
            "requestSurface: surfaceView.width = ${surfaceView.layoutParams.width}, " +
                    "surfaceView.height = ${surfaceView.layoutParams.height}"
        )
        surfaceView.layoutParams.width = surfaceView.resources.displayMetrics.widthPixels
        surfaceView.layoutParams.height = surfaceView.layoutParams.width * size.width / size.height
        surfaceView.holder.setFixedSize(size.width, size.height)
    }

    fun setSurfaceCallback(callback: SurfaceHolder.Callback) {
        surfaceCallback = callback
    }

    fun onPause() {
        previewSize = null
    }
}
