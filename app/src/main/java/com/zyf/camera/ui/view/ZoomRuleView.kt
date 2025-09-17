package com.android.camera.ui.zoom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import com.zyf.camera.utils.Logger
import android.view.MotionEvent
import android.view.View
import com.zyf.camera.R
import kotlin.math.min

class ZoomRuleView : View {
    private val TAG = "ZoomRuleView"

    private var valueFrom: Float = 0f
    private var valueTo: Float = 10f
    private var referenceValue = 1f
    private var cursor = 0f
    private var value: Float = 0f
    private var ticklines: FloatArray? = null
    private var interval = 0.1f
    private var tickNumber = valueFrom / interval

    constructor(context: Context, attributeSet: AttributeSet?) : super(context, attributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.ZoomRuleView)
        valueFrom = typedArray.getFloat(R.styleable.ZoomRuleView_valueFrom, 0f)
        valueTo = typedArray.getFloat(R.styleable.ZoomRuleView_valueFrom, 10f)
        value = typedArray.getFloat(R.styleable.ZoomRuleView_valueTo, 0f)
        typedArray.recycle()
    }

    constructor(context: Context) : super(context, null)

    fun setValueFrom(valueFrom: Float) {
        this.valueFrom = valueFrom
    }

    fun setValueTo(valueTo: Float) {
        this.valueTo = valueTo
    }

    fun setValue(value: Float) {
        this.value = value
    }

    fun getValue(): Float {
        return value
    }

    fun getValueFrom(): Float {
        return valueFrom
    }

    fun getValueTo(): Float {
        return valueTo
    }

    override fun onAttachedToWindow() {
        Logger.d(TAG, "onAttachedToWindow")
        lastX = width / 2f
        super.onAttachedToWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // print the measure spec
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        lastX = widthSize / 2f
        Logger.d(TAG, "onMeasure lastX = $lastX")
        Logger.d(
            TAG,
            "widthMode: $widthMode, widthSize: $widthSize, heightMode: $heightMode, heightSize: $heightSize"
        )
    }

    private val rect = Rect()
    private var start = 0f
    private var delta = 0f
    private var lastX = 0f

    var draw = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.getClipBounds(rect)
        canvas.drawLines(generateLines(), getPaint().apply { color = 0x88FFFFFF.toInt() })
        canvas.drawLine(width / 2f,
            height / 2f - 20f,
            width / 2f,
            height / 2f + 20f,
            getPaint().apply { color = Color.GREEN })
        Logger.d(TAG, "current zoom = ${getZoomValue(width / 2f)}")
    }

    private fun getPaint(): Paint {
        val paint = Paint()
        paint.color = 0x88FFFFFF.toInt()
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 1.5f
        paint.isAntiAlias = true
        return paint
    }

    private fun getDistance(index: Int, standardInterval: Float): Float {
        Logger.d(TAG, "index = $index")
        val denominator: Float
        return if (index >= 0) {
            denominator = 0.1f * index + 1f
            standardInterval / denominator
        } else {
            denominator = -0.1f * index + 1f
            standardInterval * denominator
        }

    }

    var defaultIndex = 0
    private fun generateLines(): FloatArray {
        val lineNumber =
            (valueTo - valueFrom).toInt() * 10 + ((valueTo - valueFrom) * 10 % 10).toInt()
        Logger.d(TAG, "lineNumber = $lineNumber")
        defaultIndex =
            (referenceValue - valueFrom).toInt() * 10 + ((referenceValue - valueFrom) * 10 % 10).toInt() - 1
        Logger.d(TAG, "defaultIndex = $defaultIndex")
        val keyPointIndexStep = 10
        val factor = 2f
        val standardInterval = 25f
        ticklines = FloatArray(lineNumber * 4)
        var currentInterval = 0f
        var realIndex = 0
        var distance = 0f
        val viewCenterX = width / 2
        val viewCenterY = height / 2
        // 分两段，一段是左边的，一段是右边的
        for (i in 0..defaultIndex) {
            realIndex = 0 - i
            var y0 = viewCenterY - 5f
            if (realIndex % keyPointIndexStep == 0) {
                y0 = viewCenterY - 20f
            }
            val tmp = defaultIndex - i
            ticklines!![tmp * 4] = viewCenterX + distance + delta + offset
            ticklines!![tmp * 4 + 1] = y0
            ticklines!![tmp * 4 + 2] = viewCenterX + distance + delta + offset
            ticklines!![tmp * 4 + 3] = viewCenterY + 5f
            currentInterval = getDistance(realIndex, standardInterval)
            distance -= currentInterval
        }
        distance = 0f
        currentInterval = 0f
        for (i in defaultIndex until lineNumber) {
            realIndex = i - defaultIndex
            var y0 = viewCenterY - 5f
            if (realIndex % keyPointIndexStep == 0) {
                y0 = viewCenterY - 20f
            }
            ticklines!![i * 4] = viewCenterX + distance + delta + offset
            ticklines!![i * 4 + 1] = y0
            ticklines!![i * 4 + 2] = viewCenterX + distance + delta + offset
            ticklines!![i * 4 + 3] = viewCenterY + 5f
            Logger.d(
                TAG,
                "realIndex = $realIndex, realIndex % keyPointIndexStep = ${realIndex % keyPointIndexStep}"
            )
            currentInterval = getDistance(realIndex, standardInterval)
            Logger.d(TAG, "currentInterval = $currentInterval")
            distance += currentInterval
            Logger.d(TAG, "distance = $distance")
        }
        return ticklines as FloatArray
    }

    private fun getZoomValue(distance: Float): Float {
        Logger.d(TAG, "distance = $distance")
        val tmpTickLines = ticklines?.clone() ?: return referenceValue
        if (distance <= tmpTickLines[0]) {
            return valueFrom
        }
        if (distance >= tmpTickLines[tmpTickLines.size - 2]) {
            return valueTo
        }
        val i: Int
        // 用二分法找出distance所在的区间
        var left = 0
        var right = ticklines!!.size / 4 - 1
        var mid = 0
        while (left <= right) {
            mid = (left + right) / 2
            if (distance > ticklines!![mid * 4]) {
                left = mid + 1
            } else if (distance < ticklines!![mid * 4]) {
                right = mid - 1
            } else {
                break
            }
        }
        i = min(left, right)
        Logger.d(TAG, "final i = $i")
        val deltaDistanceRatio =
            (distance - ticklines!![i * 4]) / (ticklines!![(i + 1) * 4] - ticklines!![i * 4])
        val deltaValue = deltaDistanceRatio * 0.1f
        val result = referenceValue + 0.1f * (i - defaultIndex) + deltaValue
        Logger.d(TAG, "result = $result")
        return result
    }

    private var offset = 0f

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onTouchEvent(event)
        }
        if (start == 0f) {
            start = event.x
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                delta = event.x - start
            }

            MotionEvent.ACTION_MOVE -> {
                delta = event.x - start
                Logger.d(TAG, "delta = $delta")
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                offset += delta
                start = 0f
                delta = 0f
            }
        }
        return true
    }

}