package com.zyf.geekcamera.base

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.zyf.geekcamera.extensions.TAG

class BaseActivity: ComponentActivity() {
    // 请求多个权限
    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d(TAG(), "${it.key} = ${it.value}")
            }
        }


    // 申请权限的方法
    fun requestPermissions() {
        requestMultiplePermissions.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

}