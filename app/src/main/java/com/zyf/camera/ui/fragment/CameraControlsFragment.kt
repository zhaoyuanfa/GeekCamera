package com.zyf.camera.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.zyf.camera.data.controller.FlashController
import com.zyf.camera.data.controller.ISOController
import com.zyf.camera.data.controller.WhiteBalanceController
import com.zyf.camera.data.controller.ZoomController
import com.zyf.camera.data.controller.CameraExposureController
import com.zyf.camera.ui.viewmodel.CameraViewModel
import com.zyf.camera.utils.Logger
import kotlinx.coroutines.launch

/**
 * 展示如何使用新的Controller系统的示例Fragment
 * 提供各种相机控制功能的UI界面
 */
class CameraControlsFragment : Fragment() {

    private lateinit var cameraViewModel: CameraViewModel

    // UI组件
    private lateinit var flashButton: Button
    private lateinit var whiteBalanceButton: Button
    private lateinit var isoSeekBar: SeekBar
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var isoValueText: TextView
    private lateinit var zoomValueText: TextView
    private lateinit var controllerStatusText: TextView
    private lateinit var exposureLockButton: Button

    // Controllers
    private var zoomController: ZoomController? = null
    private var flashController: FlashController? = null
    private var whiteBalanceController: WhiteBalanceController? = null
    private var isoController: ISOController? = null
    private var exposureController: CameraExposureController? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 这里应该是实际的布局，现在用代码创建示例UI
        return createSampleLayout(inflater, container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraViewModel = ViewModelProvider(requireActivity())[CameraViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeUI(view)
        observeControllerStates()
    }

    private fun createSampleLayout(inflater: LayoutInflater, container: ViewGroup?): View {
        // 简单创建一个垂直布局作为示例
        val context = requireContext()
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Flash控制按钮
        flashButton = Button(context).apply {
            text = "Flash: OFF"
            setOnClickListener { toggleFlash() }
        }
        layout.addView(flashButton)

        // White Balance控制按钮
        whiteBalanceButton = Button(context).apply {
            text = "WB: AUTO"
            setOnClickListener { cycleWhiteBalance() }
        }
        layout.addView(whiteBalanceButton)

        // ISO控制
        layout.addView(TextView(context).apply { text = "ISO Control" })
        isoValueText = TextView(context).apply { text = "ISO: AUTO" }
        layout.addView(isoValueText)
        
        isoSeekBar = SeekBar(context).apply {
            max = 100 // 将映射到实际ISO范围
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) updateISO(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(isoSeekBar)

        // Zoom控制
        layout.addView(TextView(context).apply { text = "Zoom Control" })
        zoomValueText = TextView(context).apply { text = "Zoom: 1.0x" }
        layout.addView(zoomValueText)
        
        zoomSeekBar = SeekBar(context).apply {
            max = 100 // 将映射到实际缩放范围
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) updateZoom(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(zoomSeekBar)

        // 曝光控制
        layout.addView(TextView(context).apply { text = "Exposure Control" })
        
        // 曝光补偿SeekBar
        val exposureSeekBar = SeekBar(context).apply {
            max = 40 // -2.0 到 +2.0 EV
            progress = 20 // 默认0 EV
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) updateExposure(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(exposureSeekBar)

        // 曝光锁定按钮
        exposureLockButton = Button(context).apply {
            text = "Exposure Lock: OFF"
            setOnClickListener { toggleExposureLock() }
        }
        layout.addView(exposureLockButton)

        // 控制器状态显示
        controllerStatusText = TextView(context).apply {
            text = "Controllers Status: Not initialized"
            textSize = 12f
        }
        layout.addView(controllerStatusText)

        return layout
    }

    private fun initializeUI(view: View) {
        // 在ViewModel准备好后获取Controllers
        lifecycleScope.launch {
            try {
                // 等待CameraViewModel初始化完成
                cameraViewModel.waitForInitialization()
                
                // 获取各种Controllers - TODO: Implement proper controller access
                val controllerManager = cameraViewModel.getCameraControllerManager()
                zoomController = controllerManager.zoomController
                flashController = controllerManager.flashController  
                whiteBalanceController = controllerManager.whiteBalanceController
                isoController = controllerManager.isoController
                exposureController = controllerManager.exposureController

                Logger.d("CameraControlsFragment", "Controllers initialized successfully")
                updateControllerStatus("Controllers initialized")
                
            } catch (e: Exception) {
                Logger.e("CameraControlsFragment", "Failed to initialize controllers: ${e.message}")
                updateControllerStatus("Failed to initialize controllers: ${e.message}")
            }
        }
    }

    private fun observeControllerStates() {
        lifecycleScope.launch {
            try {
                // 观察所有控制器状态
                val dataSource = cameraViewModel.getCameraDataSource()
                dataSource.getAllControllerStates().collect { states ->
                    updateUI(states)
                }
            } catch (e: Exception) {
                Logger.e("CameraControlsFragment", "Failed to observe controller states: ${e.message}")
            }
        }
    }

    private fun updateUI(states: Map<String, Any>) {
        lifecycleScope.launch {
            try {
                // 更新Flash按钮
                flashController?.let { controller ->
                    val flashMode = controller.getCurrentMode()
                    flashButton.text = "Flash: ${flashMode.name}"
                }

                // 更新White Balance按钮
                whiteBalanceController?.let { controller ->
                    val wbMode = controller.getCurrentMode()
                    whiteBalanceButton.text = "WB: ${wbMode.name}"
                }

                // 更新ISO显示
                isoController?.let { controller ->
                    val isoValue = controller.getCurrentValue()
                    val displayText = if (controller.isAutoMode()) {
                        "ISO: AUTO ($isoValue)"
                    } else {
                        "ISO: $isoValue"
                    }
                    isoValueText.text = displayText
                }

                // 更新Zoom显示
                zoomController?.let { controller ->
                    val zoomValue = controller.getCurrentValue()
                    zoomValueText.text = String.format("Zoom: %.1fx", zoomValue)
                }

                // 更新Exposure显示
                exposureController?.let { controller ->
                    val exposureValue = controller.getCurrentEV()
                    // 可以添加UI元素来显示曝光值
                    Logger.d("CameraControlsFragment", "Current exposure EV: $exposureValue")
                    val lockText = if (controller.isExposureLocked()) "Exposure Lock: ON" else "Exposure Lock: OFF"
                    exposureLockButton.text = lockText
                }

            } catch (e: Exception) {
                Logger.e("CameraControlsFragment", "Failed to update UI: ${e.message}")
            }
        }
    }

    private fun toggleFlash() {
        lifecycleScope.launch {
            try {
                flashController?.toggleMode()
                Logger.d("CameraControlsFragment", "Flash toggled")
            } catch (e: Exception) {
                Logger.e("CameraControlsFragment", "Failed to toggle flash: ${e.message}")
            }
        }
    }

    private fun cycleWhiteBalance() {
        lifecycleScope.launch {
            try {
                whiteBalanceController?.let { controller ->
                    val modes = controller.getSupportedModes()
                    if (modes.isNotEmpty()) {
                        val currentIndex = modes.indexOf(controller.getCurrentMode())
                        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % modes.size
                        val nextMode = modes[nextIndex]
                        controller.setMode(nextMode)
                        Logger.d("CameraControlsFragment", "White balance cycled to $nextMode")
                    }
                }
            } catch (e: Exception) {
                Logger.e("CameraControlsFragment", "Failed to cycle white balance: ${e.message}")
            }
        }
    }

    private fun updateISO(progress: Int) {
        lifecycleScope.launch {
            try {
                isoController?.let { controller ->
                    if (progress == 0) {
                        // 设置为自动模式
                        controller.setAutoMode(true)
                    } else {
                        // 计算实际ISO值（假设范围100-3200）
                        val minIso = 100
                        val maxIso = 3200
                        val isoValue = minIso + (maxIso - minIso) * progress / 100
                        controller.setAutoMode(false)
                        controller.setValue(isoValue)
                    }
                }
            } catch (e: Exception) {
                Logger.e("CameraControlsFragment", "Failed to update ISO: ${e.message}")
            }
        }
    }

    private fun updateZoom(progress: Int) {
        lifecycleScope.launch {
            try {
                zoomController?.let { controller ->
                    // 计算实际缩放值（假设范围1.0-10.0）
                    val minZoom = 1.0f
                    val maxZoom = 10.0f
                    val zoomValue = minZoom + (maxZoom - minZoom) * progress / 100f
                    controller.setValue(zoomValue)
                }
            } catch (e: Exception) {
                Logger.e("CameraControlsFragment", "Failed to update zoom: ${e.message}")
            }
        }
    }

    private fun updateExposure(progress: Int) {
        lifecycleScope.launch {
            try {
                exposureController?.let { controller ->
                    // 将SeekBar进度转换为EV值 (-2.0 到 +2.0)
                    val evValue = (progress - 20) * 0.1f
                    controller.setExposureEV(evValue)
                    Logger.d("CameraControlsFragment", "Exposure EV set to $evValue")
                }
            } catch (e: Exception) {
                Logger.e("CameraControlsFragment", "Failed to update exposure: ${e.message}")
            }
        }
    }

    private fun toggleExposureLock() {
        lifecycleScope.launch {
            try {
                exposureController?.toggleExposureLock()
                Logger.d("CameraControlsFragment", "Exposure lock toggled")
            } catch (e: Exception) {
                Logger.e("CameraControlsFragment", "Failed to toggle exposure lock: ${e.message}")
            }
        }
    }

    private fun updateControllerStatus(status: String) {
        lifecycleScope.launch {
            controllerStatusText.text = "Controllers Status: $status"
        }
    }

    companion object {
        fun newInstance() = CameraControlsFragment()
    }
}