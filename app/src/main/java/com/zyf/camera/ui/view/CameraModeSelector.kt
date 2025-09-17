package com.zyf.camera.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import com.zyf.camera.R
import com.zyf.camera.domain.model.CameraMode

class CameraModeSelector @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private var currentMode = CameraMode.PHOTO
    var onModeSelected: ((CameraMode) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_mode_selector, this, true)
        
        findViewById<ImageButton>(R.id.btnPhoto).setOnClickListener {
            selectMode(CameraMode.PHOTO)
        }
        
        findViewById<ImageButton>(R.id.btnVideo).setOnClickListener {
            selectMode(CameraMode.VIDEO)
        }
    }

    private fun selectMode(mode: CameraMode) {
        currentMode = mode
        updateSelectionUI()
        onModeSelected?.invoke(mode)
    }

    private fun updateSelectionUI() {
        findViewById<ImageButton>(R.id.btnPhoto).isSelected = (currentMode == CameraMode.PHOTO)
        findViewById<ImageButton>(R.id.btnVideo).isSelected = (currentMode == CameraMode.VIDEO)
    }
}