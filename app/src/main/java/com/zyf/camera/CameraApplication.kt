package com.zyf.camera

import android.app.Application
import com.zyf.camera.utils.ExceptionHandler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CameraApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        // 初始化设置
        /**
         * Handler for uncaught exceptions
         */
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        val exceptionHandler = ExceptionHandler(defaultExceptionHandler)
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler)

    }
}