package com.zyf.camera.data.controller.base

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 相机控制器基础接口
 * 所有具体的控制器都应该实现这个接口
 */
interface CameraController {
    /**
     * 控制器是否可用（硬件支持）
     */
    val isSupported: Boolean
    
    /**
     * 控制器是否已启用
     */
    val isEnabled: Boolean
    
    /**
     * 初始化控制器
     */
    suspend fun initialize()
    
    /**
     * 释放控制器资源
     */
    suspend fun release()
    
    /**
     * 重置控制器到默认状态
     */
    suspend fun reset()
    
    /**
     * 获取控制器状态变化流
     */
    fun getStateFlow(): StateFlow<ControllerState>
}

/**
 * 控制器状态
 */
sealed class ControllerState {
    object Uninitialized : ControllerState()
    object Initializing : ControllerState()
    object Ready : ControllerState()
    object Busy : ControllerState()
    data class Error(val message: String, val throwable: Throwable? = null) : ControllerState()
}

/**
 * 带有数值范围的控制器接口
 */
interface RangedController<T : Comparable<T>> : CameraController {
    /**
     * 获取支持的数值范围
     */
    fun getSupportedRange(): ClosedRange<T>
    
    /**
     * 获取当前数值
     */
    fun getCurrentValue(): T
    
    /**
     * 设置数值
     */
    suspend fun setValue(value: T): Boolean
    
    /**
     * 获取数值变化流
     */
    fun getValueFlow(): Flow<T>
}

/**
 * 带有模式选择的控制器接口
 */
interface ModeController<T> : CameraController {
    /**
     * 获取支持的模式列表
     */
    fun getSupportedModes(): List<T>
    
    /**
     * 获取当前模式
     */
    fun getCurrentMode(): T
    
    /**
     * 设置模式
     */
    suspend fun setMode(mode: T): Boolean
    
    /**
     * 获取模式变化流
     */
    fun getModeFlow(): Flow<T>
}

/**
 * 开关控制器接口
 */
interface ToggleController : CameraController {
    /**
     * 获取当前开关状态
     */
    fun isOn(): Boolean
    
    /**
     * 设置开关状态
     */
    suspend fun setEnabled(enabled: Boolean): Boolean
    
    /**
     * 切换开关状态
     */
    suspend fun toggle(): Boolean {
        return setEnabled(!isOn())
    }
    
    /**
     * 获取状态变化流
     */
    fun getToggleFlow(): Flow<Boolean>
}