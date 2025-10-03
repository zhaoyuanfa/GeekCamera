package com.zyf.camera.utils

import android.content.Context
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager

/**
 * 相机方向辅助类
 * 用于计算正确的相机方向和图片旋转角度
 */
class OrientationHelper(private val context: Context, private val cameraId: String) {
    
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    // 设备当前旋转角度
    private var deviceOrientation = 0
    
    // 方向监听器
    private val orientationEventListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            when {
                orientation >= 315 || orientation < 45 -> deviceOrientation = 0
                orientation >= 45 && orientation < 135 -> deviceOrientation = 90
                orientation >= 135 && orientation < 225 -> deviceOrientation = 180
                orientation >= 225 && orientation < 315 -> deviceOrientation = 270
            }
        }
    }
    
    init {
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
    }
    
    /**
     * 获取相机传感器方向
     */
    private fun getSensorOrientation(): Int {
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        } catch (e: Exception) {
            Logger.e("OrientationHelper", "Failed to get sensor orientation: ${e.message}")
            0
        }
    }
    
    /**
     * 判断是否为前置摄像头
     */
    private fun isFrontCamera(): Boolean {
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            lensFacing == CameraCharacteristics.LENS_FACING_FRONT
        } catch (e: Exception) {
            Logger.e("OrientationHelper", "Failed to get lens facing: ${e.message}")
            false
        }
    }
    
    /**
     * 获取屏幕旋转角度
     */
    private fun getScreenRotation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }
    
    /**
     * 计算JPEG图片的正确方向
     * 用于设置CaptureRequest.JPEG_ORIENTATION
     */
    fun getJpegOrientation(): Int {
        val sensorOrientation = getSensorOrientation()
        val screenRotation = getScreenRotation()
        val isFront = isFrontCamera()
        
        val rotation = if (isFront) {
            // 前置摄像头需要镜像处理
            (sensorOrientation + screenRotation) % 360
        } else {
            // 后置摄像头
            (sensorOrientation - screenRotation + 360) % 360
        }
        
        Logger.d("OrientationHelper", "JPEG Orientation - Sensor: $sensorOrientation, Screen: $screenRotation, Front: $isFront, Result: $rotation")
        return rotation
    }
    
    /**
     * 计算预览画面的旋转角度
     * 用于调整TextureView的显示
     */
    fun getPreviewRotation(): Int {
        val sensorOrientation = getSensorOrientation()
        val screenRotation = getScreenRotation()
        val isFront = isFrontCamera()
        
        val rotation = if (isFront) {
            (sensorOrientation + screenRotation) % 360
        } else {
            (sensorOrientation - screenRotation + 360) % 360
        }
        
        return rotation
    }
    
    /**
     * 获取用于TextureView变换的Matrix
     */
    fun getTextureTransform(textureWidth: Int, textureHeight: Int, 
                           previewWidth: Int, previewHeight: Int): Matrix {
        val matrix = Matrix()
        val screenRotation = getScreenRotation()
        
        // 计算缩放比例
        val scaleX = textureWidth.toFloat() / previewWidth
        val scaleY = textureHeight.toFloat() / previewHeight
        val scale = maxOf(scaleX, scaleY)
        
        // 设置缩放中心点
        val centerX = textureWidth / 2f
        val centerY = textureHeight / 2f
        
        // 应用旋转和缩放
        matrix.postScale(scale, scale, centerX, centerY)
        matrix.postRotate(screenRotation.toFloat(), centerX, centerY)
        
        return matrix
    }
    
    /**
     * 计算视频录制的方向提示
     * 用于MediaRecorder.setOrientationHint()
     */
    fun getVideoOrientation(): Int {
        val sensorOrientation = getSensorOrientation()
        val screenRotation = getScreenRotation()
        val isFront = isFrontCamera()
        
        val rotation = if (isFront) {
            // 前置摄像头
            (sensorOrientation + screenRotation) % 360
        } else {
            // 后置摄像头
            (sensorOrientation - screenRotation + 360) % 360
        }
        
        Logger.d("OrientationHelper", "Video Orientation - Sensor: $sensorOrientation, Screen: $screenRotation, Front: $isFront, Result: $rotation")
        return rotation
    }
    
    /**
     * 释放资源
     */
    fun cleanup() {
        orientationEventListener.disable()
    }
}