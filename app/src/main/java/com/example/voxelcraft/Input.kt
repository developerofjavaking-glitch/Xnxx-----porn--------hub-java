package com.example.voxelcraft

import java.util.concurrent.ConcurrentLinkedQueue

/** Touch input written by the HUD (UI thread) and read by the renderer (GL thread). */
class Input {
    @Volatile var moveX = 0f      // strafe, -1..1
    @Volatile var moveY = 0f      // forward, -1..1
    @Volatile var jump = false
    @Volatile var down = false

    val actions = ConcurrentLinkedQueue<Int>()

    private var lookX = 0f
    private var lookY = 0f
    private val lock = Any()

    fun addLook(dx: Float, dy: Float) {
        synchronized(lock) {
            lookX += dx
            lookY += dy
        }
    }

    fun consumeLook(out: FloatArray) {
        synchronized(lock) {
            out[0] = lookX
            out[1] = lookY
            lookX = 0f
            lookY = 0f
        }
    }

    companion object {
        const val ACT_BREAK = 1
        const val ACT_PLACE = 2
        const val ACT_FLY = 3
        const val ACT_BLOCK = 4
    }
}

/** State shared between renderer and HUD. */
class Shared {
    val input = Input()
    @Volatile var hudText = ""
    @Volatile var flying = false
    @Volatile var blockIndex = 0
}
