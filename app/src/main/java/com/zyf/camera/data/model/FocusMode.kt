package com.zyf.camera.data.model

/**
 * 对焦模式枚举
 */
enum class FocusMode {
    /**
     * 自动对焦
     */
    AUTO,
    
    /**
     * 连续对焦（视频）
     */
    CONTINUOUS_VIDEO,
    
    /**
     * 连续对焦（图片）
     */
    CONTINUOUS_PICTURE,
    
    /**
     * 手动对焦
     */
    MANUAL,
    
    /**
     * 关闭对焦
     */
    OFF,
    
    /**
     * 微距对焦
     */
    MACRO
}