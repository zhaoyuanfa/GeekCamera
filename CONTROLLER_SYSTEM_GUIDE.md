# Camera Controller System 使用指南

## 概述

新的Camera Controller系统提供了一套统一、易于扩展的相机控制接口。该系统采用了模块化设计，支持各种相机功能的独立控制和状态管理。

## 核心架构

### 1. 基础接口 (base/CameraController.kt)

```kotlin
// 基础控制器接口
interface CameraController<T> {
    val currentValue: StateFlow<T>
    val isInitialized: StateFlow<Boolean>
    suspend fun initialize()
    suspend fun release()
    suspend fun reset()
}

// 范围控制器接口（如缩放、ISO等）
interface RangedController<T> : CameraController<T> {
    val minValue: T
    val maxValue: T
    suspend fun setValue(value: T)
}

// 模式控制器接口（如闪光灯、白平衡等）
interface ModeController<T> : CameraController<T> {
    suspend fun setMode(mode: T)
}

// 开关控制器接口
interface ToggleController : CameraController<Boolean> {
    suspend fun toggle()
}
```

### 2. 基础实现 (base/BaseCameraController.kt)

抽象基类提供了常用功能：
- 状态管理 (StateFlow)
- 生命周期管理 (initialize/release/reset)
- 错误处理
- Camera2 API 工具方法

### 3. 具体控制器实现

#### FlashController - 闪光灯控制
```kotlin
class FlashController : BaseCameraController<FlashMode>(), ModeController<FlashMode> {
    enum class FlashMode { OFF, ON, AUTO, TORCH }
    
    // 使用示例
    flashController.setMode(FlashMode.AUTO)
    flashController.toggleMode()  // 循环切换模式
    flashController.toggleTorch() // 开关手电筒
}
```

#### WhiteBalanceController - 白平衡控制
```kotlin
class WhiteBalanceController : BaseCameraController<WhiteBalanceMode>(), ModeController<WhiteBalanceMode> {
    enum class WhiteBalanceMode { AUTO, INCANDESCENT, FLUORESCENT, DAYLIGHT, CLOUDY_DAYLIGHT, etc. }
    
    // 使用示例
    whiteBalanceController.setMode(WhiteBalanceMode.DAYLIGHT)
    val description = whiteBalanceController.getModeDescription(WhiteBalanceMode.DAYLIGHT)
}
```

#### ISOController - ISO感光度控制
```kotlin
class ISOController : BaseCameraController<Int>(), RangedController<Int> {
    val isAutoMode: StateFlow<Boolean>
    
    // 使用示例
    isoController.setValue(800)
    isoController.setAutoMode(true)
    isoController.increaseISO()
    isoController.decreaseISO()
}
```

#### ZoomController - 缩放控制
```kotlin
class ZoomController : BaseCameraController<Float>(), RangedController<Float> {
    // 使用示例
    zoomController.setValue(2.0f)
    zoomController.zoomIn()
    zoomController.zoomOut()
}
```

### 4. 统一管理器 (CameraControllerManager.kt)

```kotlin
class CameraControllerManager {
    val zoomController: ZoomController
    val flashController: FlashController
    val whiteBalanceController: WhiteBalanceController
    val isoController: ISOController
    
    // 统一操作
    suspend fun initializeAll()
    suspend fun releaseAll()
    suspend fun resetAll()
    
    // 状态聚合
    val allControllersState: Flow<Map<String, Any>>
}
```

## 使用指南

### 1. 在数据层使用

#### CameraDataSourceImpl集成

```kotlin
class CameraDataSourceImpl {
    private lateinit var controllerManager: CameraControllerManager
    
    override suspend fun initializeCamera() {
        // 初始化控制器管理器
        controllerManager = CameraControllerManager(...)
        
        // 相机打开后初始化所有控制器
        controllerManager.initializeAll()
    }
    
    override fun close() {
        // 释放控制器资源
        controllerManager.releaseAll()
    }
    
    // 提供访问接口
    fun getZoomController() = controllerManager.zoomController
    fun getFlashController() = controllerManager.flashController
    // ... 其他控制器
}
```

### 2. 在UI层使用

#### 通过ViewModel访问

```kotlin
class CameraViewModel {
    fun getCameraDataSource(): CameraDataSource = 
        (cameraRepository as CameraRepositoryImpl).getCameraDataSource()
        
    suspend fun waitForInitialization() {
        while (!cameraOpened) { delay(100) }
    }
}
```

#### 在Fragment中使用

```kotlin
class CameraControlsFragment : Fragment() {
    private val cameraViewModel: CameraViewModel by activityViewModels()
    
    private fun initializeControllers() {
        lifecycleScope.launch {
            // 等待相机初始化
            cameraViewModel.waitForInitialization()
            
            // 获取控制器
            val dataSource = cameraViewModel.getCameraDataSource()
            val flashController = dataSource.getFlashController()
            val zoomController = dataSource.getZoomController()
            
            // 使用控制器
            flashController.setMode(FlashMode.AUTO)
            zoomController.setValue(2.0f)
        }
    }
    
    private fun observeStates() {
        lifecycleScope.launch {
            val dataSource = cameraViewModel.getCameraDataSource()
            dataSource.getAllControllerStates().collect { states ->
                updateUI(states)
            }
        }
    }
}
```

### 3. 响应式状态管理

所有控制器都提供了StateFlow来观察状态变化：

```kotlin
// 观察闪光灯状态
flashController.currentValue.collect { mode ->
    updateFlashButton(mode)
}

// 观察缩放状态
zoomController.currentValue.collect { zoom ->
    updateZoomDisplay(zoom)
}

// 观察所有控制器状态
controllerManager.allControllersState.collect { states ->
    updateAllUI(states)
}
```

## 扩展新控制器

### 1. 创建新的控制器类

```kotlin
class ExposureController : BaseCameraController<Int>(), RangedController<Int> {
    override val minValue = -6
    override val maxValue = 6
    
    override suspend fun setValue(value: Int) {
        validateRange(value)
        applyCaptureRequest { builder ->
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, value)
        }
        _currentValue.value = value
    }
    
    // 额外功能
    suspend fun setAutoExposure(enabled: Boolean) {
        applyCaptureRequest { builder ->
            val mode = if (enabled) CONTROL_AE_MODE_ON else CONTROL_AE_MODE_OFF
            builder.set(CaptureRequest.CONTROL_AE_MODE, mode)
        }
    }
}
```

### 2. 集成到管理器

```kotlin
class CameraControllerManager {
    // 添加新控制器
    val exposureController: ExposureController by lazy {
        ExposureController(cameraManager, getCurrentCameraId, getCameraDevice, getCaptureSession)
    }
    
    override suspend fun initializeAll() {
        // 添加到初始化列表
        exposureController.initialize()
    }
    
    // 添加到状态聚合
    override val allControllersState: Flow<Map<String, Any>> = combine(
        // 现有状态流...
        exposureController.currentValue
    ) { /* 合并状态 */ }
}
```

### 3. 在DataSource中提供访问

```kotlin
class CameraDataSourceImpl {
    fun getExposureController(): ExposureController = controllerManager.exposureController
}
```

## 最佳实践

### 1. 错误处理
- 所有Controller操作都应该在try-catch中执行
- 使用Logger记录错误信息
- 提供优雅的降级处理

### 2. 生命周期管理
- 在相机打开后初始化Controllers
- 在相机关闭时释放Controller资源
- 使用reset()重新配置Controllers

### 3. 状态同步
- 使用StateFlow观察状态变化
- 避免直接访问私有状态
- 确保UI更新在主线程执行

### 4. 性能优化
- 使用lazy初始化Controllers
- 避免频繁的Camera2 API调用
- 合理使用协程和线程切换

## 示例代码

完整的使用示例请参考：
- `CameraControlsFragment.kt` - UI层使用示例
- `CameraDataSourceImpl.kt` - 数据层集成
- 各个Controller实现文件

这个新的Controller系统提供了：
✅ 统一的接口设计
✅ 强类型安全
✅ 响应式状态管理  
✅ 易于测试和扩展
✅ 清晰的职责分离
✅ 优雅的错误处理