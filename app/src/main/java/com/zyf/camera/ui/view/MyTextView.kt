package com.zyf.camera.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import com.zyf.camera.R

class MyTextView : AppCompatTextView {

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attributeSet: AttributeSet?) : super(context, attributeSet)

    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(
        context, attributeSet, defStyleAttr
    )

    private var bgResource: Drawable =
        ResourcesCompat.getDrawable(resources, R.drawable.ic_launcher_background, null)!!

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bgResource.draw(canvas)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        setBgResourceWidth(100)
    }

    private fun setBgResourceWidth(bgSize: Int) {
        val canvasCenterX = width.div(2)
        val canvasCenterY = height.div(2)
        bgResource.setBounds(
            canvasCenterX - bgSize / 2,
            canvasCenterY - bgSize / 2,
            canvasCenterX + bgSize / 2,
            canvasCenterY + bgSize / 2,
        )
        invalidate()
    }
}
