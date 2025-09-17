package com.zyf.camera.ui.view

import android.content.Context
import android.util.AttributeSet
import com.zyf.camera.utils.Logger
import android.view.MotionEvent
import android.widget.LinearLayout
import com.zyf.camera.extensions.TAG

class MyLinearLayout(context: Context, attributeSet: AttributeSet) :
    LinearLayout(context, attributeSet) {

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Logger.d(TAG(), "MyLinearLayout onTouchEvent")
        val result = super.onTouchEvent(event)
        Logger.d(TAG(), "$result")
        return result
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        Logger.d(TAG(), "MyLinearLayout onInterceptTouchEvent")
        val result = super.onInterceptTouchEvent(ev)
        Logger.d(TAG(), "result = $result")
        return result
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        Logger.d(TAG(), "MyLinearLayout dispatchTouchEvent")
        val result = super.dispatchTouchEvent(ev)
        Logger.d(TAG(), "RESULT = $result")
        return result
    }

}