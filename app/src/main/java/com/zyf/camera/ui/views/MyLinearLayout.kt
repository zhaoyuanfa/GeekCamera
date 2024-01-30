package com.zyf.camera.ui.views

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.LinearLayout
import com.zyf.camera.extensions.TAG

class MyLinearLayout(context: Context, attributeSet: AttributeSet) :
    LinearLayout(context, attributeSet) {

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        Log.d(TAG(), "MyLinearLayout onTouchEvent")
        val result = super.onTouchEvent(event)
        Log.d(TAG(), "$result")
        return result
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        Log.d(TAG(), "MyLinearLayout onInterceptTouchEvent")
        val result = super.onInterceptTouchEvent(ev)
        Log.d(TAG(), "result = $result")
        return result
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        Log.d(TAG(), "MyLinearLayout dispatchTouchEvent")
        val result = super.dispatchTouchEvent(ev)
        Log.d(TAG(), "RESULT = $result")
        return result
    }

}