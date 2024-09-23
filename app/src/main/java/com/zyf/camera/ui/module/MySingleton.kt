package com.zyf.camera.ui.module

import android.content.Context

class MySingleton private constructor(context: Context) {
    init {
        // 初始化代码，使用context参数
    }

    companion object {
        @Volatile
        private var INSTANCE: MySingleton? = null

        fun getInstance(context: Context): MySingleton {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MySingleton(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}