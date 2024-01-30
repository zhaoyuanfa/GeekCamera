package com.zyf.camera.extensions

fun Any.TAG(): String = this::class.java.name.split(".").last()
