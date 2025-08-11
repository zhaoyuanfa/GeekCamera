package com.zyf.camera.utils

import android.util.Log

object Logger {
    private const val TAG = "GCamera"

    private fun buildMsg(subTag: String, msg: String): String {
        val threadName = Thread.currentThread().name
        return "[$subTag][$threadName]$msg"
    }

    fun d(subTag: String, msg: String) {
        Log.d(TAG, buildMsg(subTag, msg))
    }

    fun i(subTag: String, msg: String) {
        Log.i(TAG, buildMsg(subTag, msg))
    }

    fun w(subTag: String, msg: String) {
        Log.w(TAG, buildMsg(subTag, msg))
    }

    fun e(subTag: String, msg: String) {
        Log.e(TAG, buildMsg(subTag, msg))
    }

    fun v(subTag: String, msg: String) {
        Log.v(TAG, buildMsg(subTag, msg))
    }

    /**
     * 打印调用栈，方便调试
     */
    fun trace(subTag: String, msg: String = "") {
        val stackTrace = Throwable().stackTrace
        // 跳过trace本身和Logger的调用，取后面的调用栈
        val stackInfo = stackTrace
            .drop(2)
            .joinToString("\n") { "    at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
        val fullMsg = if (msg.isNotEmpty()) "$msg\n$stackInfo" else stackInfo
        Log.d(TAG, buildMsg(subTag, fullMsg))
    }
}