package com.zyf.camera.hardware.camera

/**
 * CameraEvent，包含开启Camera、关闭Camera事件
 */
enum class CameraEvent(i: Int) {
    OPEN(0),
    CLOSE(1),
    CREATE_SESSION(2),
    ABORT_SESSION(3),
    CLOSE_SESSION(4),
    ;

    // value
    companion object {
        fun valueOf(value: Int): CameraEvent {
            return when (value) {
                0 -> OPEN
                1 -> CLOSE
                2 -> CREATE_SESSION
                3 -> ABORT_SESSION
                4 -> CLOSE_SESSION
                else -> throw IllegalArgumentException()
            }
        }
    }
}
