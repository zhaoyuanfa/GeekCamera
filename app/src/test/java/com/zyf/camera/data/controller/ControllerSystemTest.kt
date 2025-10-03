package com.zyf.camera.data.controller

import android.hardware.camera2.CameraManager
import com.zyf.camera.data.controller.base.CameraController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * Controller系统的单元测试
 * 验证各个Controller的基本功能和状态管理
 */
class ControllerSystemTest {

    @Mock
    private lateinit var cameraManager: CameraManager

    private lateinit var controllerManager: CameraControllerManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        controllerManager = CameraControllerManager(
            cameraManager = cameraManager,
            getCurrentCameraId = { "0" },
            getCameraDevice = { null },
            getCaptureSession = { null }
        )
    }

    @Test
    fun testFlashControllerInitialization() = runBlocking {
        val flashController = controllerManager.flashController
        
        // 测试初始状态
        assertFalse("Flash controller should not be initialized initially", 
                   flashController.isInitialized.value)
        assertEquals("Initial flash mode should be OFF", 
                    FlashController.FlashMode.OFF, flashController.currentValue.value)
        
        // 测试初始化
        flashController.initialize()
        assertTrue("Flash controller should be initialized after initialize()", 
                  flashController.isInitialized.value)
    }

    @Test
    fun testZoomControllerRangeValidation() = runBlocking {
        val zoomController = controllerManager.zoomController
        zoomController.initialize()
        
        // 测试有效范围
        assertTrue("Min zoom should be valid", zoomController.minValue >= 1.0f)
        assertTrue("Max zoom should be greater than min", 
                  zoomController.maxValue > zoomController.minValue)
        
        // 测试初始值
        assertEquals("Initial zoom should be 1.0f", 1.0f, zoomController.currentValue.value)
    }

    @Test
    fun testISOControllerAutoMode() = runBlocking {
        val isoController = controllerManager.isoController
        isoController.initialize()
        
        // 测试初始状态 - 应该是自动模式
        assertTrue("ISO should start in auto mode", isoController.isAutoMode.value)
        
        // 测试切换到手动模式
        isoController.setAutoMode(false)
        assertFalse("ISO should be in manual mode after setAutoMode(false)", 
                   isoController.isAutoMode.value)
        
        // 测试设置具体值时自动切换到手动模式
        isoController.setAutoMode(true)
        isoController.setValue(800)
        assertFalse("Setting ISO value should switch to manual mode", 
                   isoController.isAutoMode.value)
        assertEquals("ISO value should be set correctly", 800, isoController.currentValue.value)
    }

    @Test
    fun testWhiteBalanceControllerModes() = runBlocking {
        val wbController = controllerManager.whiteBalanceController
        wbController.initialize()
        
        // 测试初始模式
        assertEquals("Initial white balance mode should be AUTO", 
                    WhiteBalanceController.WhiteBalanceMode.AUTO, 
                    wbController.currentValue.value)
        
        // 测试模式切换
        wbController.setMode(WhiteBalanceController.WhiteBalanceMode.DAYLIGHT)
        assertEquals("White balance mode should change to DAYLIGHT", 
                    WhiteBalanceController.WhiteBalanceMode.DAYLIGHT, 
                    wbController.currentValue.value)
        
        // 测试模式描述
        val description = wbController.getModeDescription(
            WhiteBalanceController.WhiteBalanceMode.DAYLIGHT)
        assertNotNull("Mode description should not be null", description)
        assertTrue("Mode description should not be empty", description.isNotEmpty())
    }

    @Test
    fun testExposureControllerInitialization() = runBlocking {
        val exposureController = controllerManager.exposureController
        
        // 测试初始状态
        assertEquals("Initial exposure compensation should be 0", 
                    0, exposureController.getCurrentValue())
        assertEquals("Initial auto exposure mode should be ON", 
                    CameraExposureController.AutoExposureMode.ON, 
                    exposureController.getCurrentAutoExposureMode())
        assertFalse("Initial exposure lock should be false", 
                   exposureController.isExposureLocked())
        
        // 测试初始化
        exposureController.initialize()
    }

    @Test
    fun testControllerManagerLifecycle() = runBlocking {
        // 测试初始化所有控制器
        controllerManager.initializeAll()
        
        assertTrue("Flash controller should be initialized", 
                  controllerManager.flashController.isInitialized.value)
        assertTrue("Zoom controller should be initialized", 
                  controllerManager.zoomController.isInitialized.value)
        assertTrue("ISO controller should be initialized", 
                  controllerManager.isoController.isInitialized.value)
        assertTrue("White balance controller should be initialized", 
                  controllerManager.whiteBalanceController.isInitialized.value)
        // 注意：ExposureController可能在没有真实硬件时不会被标记为initialized
    }

    @Test
    fun testControllerStateAggregation() = runBlocking {
        controllerManager.initializeAll()
        
        // 测试状态聚合
        val states = controllerManager.allControllersState.first()
        
        assertNotNull("States map should not be null", states)
        assertTrue("States map should not be empty", states.isNotEmpty())
        assertTrue("States should contain flash mode", 
                  states.containsKey("flashMode"))
        assertTrue("States should contain zoom level", 
                  states.containsKey("zoomLevel"))
        assertTrue("States should contain ISO value", 
                  states.containsKey("isoValue"))
        assertTrue("States should contain white balance mode", 
                  states.containsKey("whiteBalanceMode"))
    }

    @Test
    fun testFlashControllerToggleMethods() = runBlocking {
        val flashController = controllerManager.flashController
        flashController.initialize()
        
        // 测试模式切换
        val initialMode = flashController.currentValue.value
        flashController.toggleMode()
        val newMode = flashController.currentValue.value
        assertNotEquals("Flash mode should change after toggle", initialMode, newMode)
        
        // 测试手电筒切换
        flashController.toggleTorch()
        // 手电筒模式应该被设置或取消
        // 具体行为取决于当前状态
    }

    @Test
    fun testZoomControllerUtilityMethods() = runBlocking {
        val zoomController = controllerManager.zoomController
        zoomController.initialize()
        
        val initialZoom = zoomController.currentValue.value
        
        // 测试放大
        zoomController.zoomIn()
        val zoomedIn = zoomController.currentValue.value
        assertTrue("Zoom should increase after zoomIn()", zoomedIn > initialZoom)
        
        // 测试缩小
        zoomController.zoomOut()
        val zoomedOut = zoomController.currentValue.value
        assertTrue("Zoom should decrease after zoomOut()", zoomedOut < zoomedIn)
    }

    @Test
    fun testISOControllerUtilityMethods() = runBlocking {
        val isoController = controllerManager.isoController
        isoController.initialize()
        
        // 先设置为手动模式
        isoController.setAutoMode(false)
        isoController.setValue(800)
        
        val initialISO = isoController.currentValue.value
        
        // 测试增加ISO
        isoController.increaseISO()
        val increasedISO = isoController.currentValue.value
        assertTrue("ISO should increase after increaseISO()", increasedISO > initialISO)
        
        // 测试减少ISO
        isoController.decreaseISO()
        val decreasedISO = isoController.currentValue.value
        assertTrue("ISO should decrease after decreaseISO()", decreasedISO < increasedISO)
    }

    @Test
    fun testControllerReset() = runBlocking {
        controllerManager.initializeAll()
        
        // 修改一些状态
        controllerManager.flashController.setMode(FlashController.FlashMode.TORCH)
        controllerManager.zoomController.setValue(3.0f)
        
        // 重置所有控制器
        controllerManager.resetAll()
        
        // 验证状态已重置
        assertEquals("Flash should reset to OFF", 
                    FlashController.FlashMode.OFF, 
                    controllerManager.flashController.currentValue.value)
        assertEquals("Zoom should reset to 1.0f", 
                    1.0f, 
                    controllerManager.zoomController.currentValue.value)
    }

    @Test
    fun testExposureControllerFunctionality() = runBlocking {
        val exposureController = controllerManager.exposureController
        exposureController.initialize()
        
        // 测试曝光补偿范围
        val range = exposureController.getSupportedRange()
        assertTrue("Exposure range should be valid", range.start <= range.endInclusive)
        
        // 测试EV转换
        val evRange = exposureController.getSupportedEVRange()
        assertTrue("EV range should be valid", evRange.start <= evRange.endInclusive)
        
        // 测试当前EV值
        val currentEV = exposureController.getCurrentEV()
        assertTrue("Current EV should be in range", currentEV in evRange)
        
        // 测试自动曝光模式
        val aeModes = CameraExposureController.AutoExposureMode.values()
        assertTrue("Should have AE modes", aeModes.isNotEmpty())
        
        // 测试测光模式  
        val meteringModes = CameraExposureController.MeteringMode.values()
        assertTrue("Should have metering modes", meteringModes.isNotEmpty())
    }

    @Test
    fun testControllerErrorHandling() = runBlocking {
        val zoomController = controllerManager.zoomController
        zoomController.initialize()
        
        // 测试范围外的值
        try {
            zoomController.setValue(-1.0f) // 无效值
            fail("Should throw exception for invalid zoom value")
        } catch (e: Exception) {
            assertTrue("Should throw IllegalArgumentException", 
                      e is IllegalArgumentException)
        }
        
        try {
            zoomController.setValue(100.0f) // 超出最大值
            fail("Should throw exception for zoom value exceeding max")
        } catch (e: Exception) {
            assertTrue("Should throw IllegalArgumentException", 
                      e is IllegalArgumentException)
        }
    }
}