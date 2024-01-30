package com.zyf.camera.utils

import java.lang.Thread.UncaughtExceptionHandler

class ExceptionHandler(
    private val defaultUncaughtExceptionHandler: UncaughtExceptionHandler?,
) : UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        defaultUncaughtExceptionHandler?.uncaughtException(t, e)
    }
}
