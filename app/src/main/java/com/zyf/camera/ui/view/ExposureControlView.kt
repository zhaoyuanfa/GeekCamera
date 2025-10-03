package com.zyf.camera.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Button
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.zyf.camera.data.controller.CameraExposureController
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.launch

/**
 * 曝光控制UI组件
 * 提供直观的曝光补偿、自动曝光模式和测光模式控制界面
 */
class ExposureControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var exposureController: CameraExposureController? = null
    private var lifecycleOwner: LifecycleOwner? = null

    // UI组件
    private lateinit var exposureCompensationSeekBar: SeekBar
    private lateinit var exposureValueText: TextView
    private lateinit var autoExposureModeButton: Button
    private lateinit var meteringModeButton: Button
    private lateinit var exposureLockButton: Button

    private val seekBarOffset = 20 // SeekBar偏移量，使其支持负值

    init {
        orientation = VERTICAL
        setupViews()
    }

    private fun setupViews() {
        // 创建曝光补偿控制
        TextView(context).apply {
            text = "曝光补偿 (EV)"
            textSize = 14f
        }.also { addView(it) }

        exposureValueText = TextView(context).apply {
            text = "0.0 EV"
            textSize = 16f
            setPadding(0, 8, 0, 8)
        }.also { addView(it) }

        exposureCompensationSeekBar = SeekBar(context).apply {
            max = 40 // -2.0 到 +2.0 EV，以0.1EV为步长
            progress = seekBarOffset // 默认0 EV
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        updateExposureCompensation(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }.also { addView(it) }

        // 自动曝光模式按钮
        autoExposureModeButton = Button(context).apply {
            text = "AE: ON"
            setOnClickListener { cycleAutoExposureMode() }
        }.also { addView(it) }

        // 测光模式按钮
        meteringModeButton = Button(context).apply {
            text = "测光: 中央重点"
            setOnClickListener { cycleMeteringMode() }
        }.also { addView(it) }

        // 曝光锁定按钮
        exposureLockButton = Button(context).apply {
            text = "曝光锁定: OFF"
            setOnClickListener { toggleExposureLock() }
        }.also { addView(it) }
    }

    /**
     * 设置曝光控制器
     */
    fun setExposureController(controller: CameraExposureController, lifecycleOwner: LifecycleOwner) {
        this.exposureController = controller
        this.lifecycleOwner = lifecycleOwner
        
        observeControllerStates()
        updateUI()
    }

    private fun observeControllerStates() {
        val owner = lifecycleOwner ?: return
        val controller = exposureController ?: return

        // 观察曝光补偿值变化
        owner.lifecycleScope.launch {
            controller.getValueFlow().collect { value ->
                updateExposureValueDisplay(value)
                updateSeekBarPosition(value)
            }
        }

        // 观察自动曝光模式变化
        owner.lifecycleScope.launch {
            controller.getAutoExposureModeFlow().collect { mode ->
                updateAutoExposureModeDisplay(mode)
            }
        }

        // 观察曝光锁定状态变化
        owner.lifecycleScope.launch {
            controller.getExposureLockFlow().collect { locked ->
                updateExposureLockDisplay(locked)
            }
        }

        // 观察测光模式变化
        owner.lifecycleScope.launch {
            controller.getMeteringModeFlow().collect { mode ->
                updateMeteringModeDisplay(mode)
            }
        }
    }

    private fun updateExposureCompensation(seekBarProgress: Int) {
        val exposureController = this.exposureController ?: return
        val lifecycleOwner = this.lifecycleOwner ?: return

        lifecycleOwner.lifecycleScope.launch {
            try {
                // 将SeekBar进度转换为EV值
                val evValue = (seekBarProgress - seekBarOffset) * 0.1f
                exposureController.setExposureEV(evValue)
                Logger.d("ExposureControlView", "Exposure EV set to $evValue")
            } catch (e: Exception) {
                Logger.e("ExposureControlView", "Failed to set exposure compensation: ${e.message}")
            }
        }
    }

    private fun cycleAutoExposureMode() {
        val exposureController = this.exposureController ?: return
        val lifecycleOwner = this.lifecycleOwner ?: return

        lifecycleOwner.lifecycleScope.launch {
            try {
                val modes = CameraExposureController.AutoExposureMode.values()
                val currentMode = exposureController.getCurrentAutoExposureMode()
                val currentIndex = modes.indexOf(currentMode)
                val nextMode = modes[(currentIndex + 1) % modes.size]
                
                exposureController.setAutoExposureMode(nextMode)
                Logger.d("ExposureControlView", "Auto exposure mode changed to $nextMode")
            } catch (e: Exception) {
                Logger.e("ExposureControlView", "Failed to cycle auto exposure mode: ${e.message}")
            }
        }
    }

    private fun cycleMeteringMode() {
        val exposureController = this.exposureController ?: return
        val lifecycleOwner = this.lifecycleOwner ?: return

        lifecycleOwner.lifecycleScope.launch {
            try {
                val modes = CameraExposureController.MeteringMode.values()
                val currentMode = exposureController.getCurrentMeteringMode()
                val currentIndex = modes.indexOf(currentMode)
                val nextMode = modes[(currentIndex + 1) % modes.size]
                
                exposureController.setMeteringMode(nextMode)
                Logger.d("ExposureControlView", "Metering mode changed to $nextMode")
            } catch (e: Exception) {
                Logger.e("ExposureControlView", "Failed to cycle metering mode: ${e.message}")
            }
        }
    }

    private fun toggleExposureLock() {
        val exposureController = this.exposureController ?: return
        val lifecycleOwner = this.lifecycleOwner ?: return

        lifecycleOwner.lifecycleScope.launch {
            try {
                exposureController.toggleExposureLock()
                Logger.d("ExposureControlView", "Exposure lock toggled")
            } catch (e: Exception) {
                Logger.e("ExposureControlView", "Failed to toggle exposure lock: ${e.message}")
            }
        }
    }

    private fun updateExposureValueDisplay(compensationValue: Int) {
        val exposureController = this.exposureController ?: return
        val evValue = exposureController.getCurrentEV()
        exposureValueText.text = String.format("%.1f EV", evValue)
    }

    private fun updateSeekBarPosition(compensationValue: Int) {
        val exposureController = this.exposureController ?: return
        val evValue = exposureController.getCurrentEV()
        val seekBarProgress = ((evValue / 0.1f) + seekBarOffset).toInt()
        
        // 避免循环调用
        if (exposureCompensationSeekBar.progress != seekBarProgress) {
            exposureCompensationSeekBar.progress = seekBarProgress.coerceIn(0, exposureCompensationSeekBar.max)
        }
    }

    private fun updateAutoExposureModeDisplay(mode: CameraExposureController.AutoExposureMode) {
        val displayText = when (mode) {
            CameraExposureController.AutoExposureMode.OFF -> "AE: OFF"
            CameraExposureController.AutoExposureMode.ON -> "AE: ON"
            CameraExposureController.AutoExposureMode.AUTO_FLASH -> "AE: AUTO FLASH"
            CameraExposureController.AutoExposureMode.ALWAYS_FLASH -> "AE: ALWAYS FLASH"
            CameraExposureController.AutoExposureMode.REDEYE -> "AE: REDEYE REDUCTION"
        }
        autoExposureModeButton.text = displayText
    }

    private fun updateExposureLockDisplay(locked: Boolean) {
        exposureLockButton.text = if (locked) "曝光锁定: ON" else "曝光锁定: OFF"
    }

    private fun updateMeteringModeDisplay(mode: CameraExposureController.MeteringMode) {
        val displayText = when (mode) {
            CameraExposureController.MeteringMode.CENTER_WEIGHTED -> "测光: 中央重点"
            CameraExposureController.MeteringMode.SPOT -> "测光: 点测光"
            CameraExposureController.MeteringMode.MATRIX -> "测光: 矩阵测光"
        }
        meteringModeButton.text = displayText
    }

    private fun updateUI() {
        val controller = exposureController ?: return
        
        // 更新所有显示
        updateExposureValueDisplay(controller.getCurrentValue())
        updateSeekBarPosition(controller.getCurrentValue())
        updateAutoExposureModeDisplay(controller.getCurrentAutoExposureMode())
        updateExposureLockDisplay(controller.isExposureLocked())
        updateMeteringModeDisplay(controller.getCurrentMeteringMode())
    }

    /**
     * 便利方法：增加曝光
     */
    fun increaseExposure() {
        val exposureController = this.exposureController ?: return
        val lifecycleOwner = this.lifecycleOwner ?: return

        lifecycleOwner.lifecycleScope.launch {
            try {
                exposureController.increaseExposure()
            } catch (e: Exception) {
                Logger.e("ExposureControlView", "Failed to increase exposure: ${e.message}")
            }
        }
    }

    /**
     * 便利方法：减少曝光
     */
    fun decreaseExposure() {
        val exposureController = this.exposureController ?: return
        val lifecycleOwner = this.lifecycleOwner ?: return

        lifecycleOwner.lifecycleScope.launch {
            try {
                exposureController.decreaseExposure()
            } catch (e: Exception) {
                Logger.e("ExposureControlView", "Failed to decrease exposure: ${e.message}")
            }
        }
    }

    /**
     * 重置曝光到默认值
     */
    fun resetExposure() {
        val exposureController = this.exposureController ?: return
        val lifecycleOwner = this.lifecycleOwner ?: return

        lifecycleOwner.lifecycleScope.launch {
            try {
                exposureController.setValue(0)
                exposureController.setAutoExposureMode(CameraExposureController.AutoExposureMode.ON)
                exposureController.setExposureLock(false)
                Logger.d("ExposureControlView", "Exposure reset to defaults")
            } catch (e: Exception) {
                Logger.e("ExposureControlView", "Failed to reset exposure: ${e.message}")
            }
        }
    }
}