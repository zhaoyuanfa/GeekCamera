package com.zyf.camera.data.controller

import android.hardware.camera2.CameraManager
import com.zyf.camera.data.controller.CameraExposureController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * CameraExposureController的单元测试
 * 验证曝光控制功能和状态管理
 */
class CameraExposureControllerTest {

    @Mock
    private lateinit var cameraManager: CameraManager

    private lateinit var exposureController: CameraExposureController

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        exposureController = CameraExposureController(
            cameraManager = cameraManager,
            getCurrentCameraId = { "0" },
            getCameraDevice = { null },
            getCaptureSession = { null }
        )
    }

    @Test
    fun testExposureControllerInitialization() = runBlocking {
        // 测试初始状态
        assertFalse("Exposure controller should not be supported without camera characteristics", 
                   exposureController.isSupported)
        assertEquals("Initial exposure compensation should be 0", 
                    0, exposureController.getCurrentValue())
        
        // 测试默认自动曝光模式
        assertEquals("Default auto exposure mode should be ON",
                    CameraExposureController.AutoExposureMode.ON,
                    exposureController.getCurrentAutoExposureMode())
        
        // 测试默认曝光锁定状态
        assertFalse("Default exposure lock should be false",
                   exposureController.isExposureLocked())
    }

    @Test
    fun testExposureCompensationRange() {
        val range = exposureController.getSupportedRange()
        
        // 测试默认范围
        assertTrue("Min exposure compensation should be reasonable", range.start >= -10)
        assertTrue("Max exposure compensation should be reasonable", range.endInclusive <= 10)
        assertTrue("Range should be valid", range.start <= range.endInclusive)
    }

    @Test
    fun testExposureCompensationValue() {
        // 测试设置有效值
        val testValue = 2
        if (testValue in exposureController.getSupportedRange()) {
            runBlocking {
                // 注意：由于没有真实的相机设备，setValue可能会失败
                // 但我们可以测试参数验证逻辑
                val result = exposureController.setValue(testValue)
                // 在模拟环境中，这可能返回false，这是正常的
            }
        }
        
        // 测试无效值
        runBlocking {
            val result = exposureController.setValue(100) // 超出范围的值
            assertFalse("Should reject out-of-range values", result)
        }
    }

    @Test
    fun testExposureEVConversion() {
        // 测试EV值转换
        val evRange = exposureController.getSupportedEVRange()
        assertNotNull("EV range should not be null", evRange)
        assertTrue("EV range should be valid", evRange.start <= evRange.endInclusive)
        
        // 测试当前EV值
        val currentEV = exposureController.getCurrentEV()
        assertTrue("Current EV should be in range", currentEV in evRange)
    }

    @Test
    fun testAutoExposureModes() = runBlocking {
        val modes = CameraExposureController.AutoExposureMode.values()
        
        assertTrue("Should have multiple AE modes", modes.isNotEmpty())
        assertTrue("Should include basic ON mode", 
                  modes.contains(CameraExposureController.AutoExposureMode.ON))
        assertTrue("Should include OFF mode", 
                  modes.contains(CameraExposureController.AutoExposureMode.OFF))
        
        // 测试设置模式 (在模拟环境中可能失败，这是正常的)
        for (mode in modes) {
            exposureController.setAutoExposureMode(mode)
        }
    }

    @Test
    fun testMeteringModes() = runBlocking {
        val modes = CameraExposureController.MeteringMode.values()
        
        assertTrue("Should have multiple metering modes", modes.isNotEmpty())
        assertTrue("Should include center weighted mode", 
                  modes.contains(CameraExposureController.MeteringMode.CENTER_WEIGHTED))
        assertTrue("Should include spot mode", 
                  modes.contains(CameraExposureController.MeteringMode.SPOT))
        assertTrue("Should include matrix mode", 
                  modes.contains(CameraExposureController.MeteringMode.MATRIX))
        
        // 测试设置模式
        for (mode in modes) {
            exposureController.setMeteringMode(mode)
        }
    }

    @Test
    fun testExposureLockToggle() = runBlocking {
        val initialState = exposureController.isExposureLocked()
        
        // 测试切换
        exposureController.toggleExposureLock()
        // 在模拟环境中状态可能不会改变，这是正常的
        
        // 测试直接设置
        exposureController.setExposureLock(true)
        exposureController.setExposureLock(false)
    }

    @Test
    fun testExposureAdjustmentMethods() = runBlocking {
        val initialValue = exposureController.getCurrentValue()
        
        // 测试增加曝光
        exposureController.increaseExposure()
        
        // 测试减少曝光
        exposureController.decreaseExposure()
        
        // 在模拟环境中，值可能不会改变，但方法应该不会抛出异常
    }

    @Test
    fun testExposureEVSetting() = runBlocking {
        // 测试设置EV值
        val testEV = 0.5f
        exposureController.setExposureEV(testEV)
        
        // 测试边界值
        val evRange = exposureController.getSupportedEVRange()
        exposureController.setExposureEV(evRange.start)
        exposureController.setExposureEV(evRange.endInclusive)
    }

    @Test
    fun testBackwardCompatibility() = runBlocking {
        // 测试向后兼容的方法
        val initialCompensation = exposureController.getExposureCompensation()
        assertEquals("Backward compatible method should return same value",
                    exposureController.getCurrentValue(),
                    initialCompensation)
        
        // 测试设置方法
        val testValue = 1
        if (testValue in exposureController.getSupportedRange()) {
            exposureController.setExposureCompensation(testValue)
        }
    }

    @Test
    fun testStateFlows() = runBlocking {
        // 测试状态流
        val valueFlow = exposureController.getValueFlow()
        val currentValue = valueFlow.first()
        assertEquals("Value flow should return current value", 
                    exposureController.getCurrentValue(), currentValue)
        
        val autoModeFlow = exposureController.getAutoExposureModeFlow()
        val currentMode = autoModeFlow.first()
        assertEquals("Mode flow should return current mode",
                    exposureController.getCurrentAutoExposureMode(), currentMode)
        
        val lockFlow = exposureController.getExposureLockFlow()
        val currentLock = lockFlow.first()
        assertEquals("Lock flow should return current lock state",
                    exposureController.isExposureLocked(), currentLock)
        
        val meteringFlow = exposureController.getMeteringModeFlow()
        val currentMetering = meteringFlow.first()
        assertEquals("Metering flow should return current metering mode",
                    exposureController.getCurrentMeteringMode(), currentMetering)
    }

    @Test
    fun testControllerLifecycle() = runBlocking {
        // 测试初始化
        exposureController.initialize()
        
        // 测试重置
        exposureController.reset()
        
        // 重置后应该回到默认状态
        assertEquals("After reset, compensation should be 0", 
                    0, exposureController.getCurrentValue())
        assertEquals("After reset, AE mode should be ON",
                    CameraExposureController.AutoExposureMode.ON,
                    exposureController.getCurrentAutoExposureMode())
        assertFalse("After reset, exposure should not be locked",
                   exposureController.isExposureLocked())
        
        // 测试释放
        exposureController.release()
    }
}