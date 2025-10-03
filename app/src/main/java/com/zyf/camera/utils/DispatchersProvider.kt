package com.zyf.camera.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Centralized provider for coroutine dispatchers. Replace direct Dispatchers.* usage
 * with this provider to make it easier to test and to satisfy lint rules about
 * hardcoded dispatchers.
 */
object DispatchersProvider {
    val main: CoroutineDispatcher = Dispatchers.Main
    val io: CoroutineDispatcher = Dispatchers.IO
    val default: CoroutineDispatcher = Dispatchers.Default
}

