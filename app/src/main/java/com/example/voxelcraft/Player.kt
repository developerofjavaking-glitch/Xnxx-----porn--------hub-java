package com.example.voxelcraft

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class Player {
    var x = 8.5
    var y = 70.0
    var z = 8.5
    var vy = 0.0
    var yaw = 0f        // 0 = looking towards -Z, increases clockwise (to the right)
    var pitch = 0f      // positive = up
    var flying = false
    var onGround = false
    var spawned = false

    companion object {
        const val HALF_WIDTH = 0.3
        const val HEIGHT = 1.8
        const val EYE = 1.62
        private const val WALK_SPEED = 4.5
        private const val FLY_SPEED = 12.0
        private const val GRAVITY = 28.0
        private const val JUMP_SPEED = 8.6
    }

    fun update(dt: Float, input: Input, world: World) {
        if (!spawned) return
        val d = dt.toDouble()

        val fwd = input.moveY.toDouble()
        val str = input.moveX.toDouble()
        val yawD = yaw.toDouble()
        var wx = sin(yawD) * fwd + cos(yawD) * str
        var wz = -cos(yawD) * fwd + sin(yawD) * str
        val len = sqrt(wx * wx + wz * wz)
        if (len > 1.0) {
            wx /= len
            wz /= len
        }
        val speed = if (flying) FLY_SPEED else WALK_SPEED
        val dx = wx * speed * d
        val dz = wz * speed * d

        if (flying) {
            vy = (if (input.jump) 8.0 else 0.0) + (if (input.down) -8.0 else 0.0)
        } else {
            vy -= GRAVITY * d
            if (vy < -50.0) vy = -50.0
            if (input.jump && onGround) {
                vy = JUMP_SPEED
            }
        }

        onGround = false
        val dy = vy * d
        val steps = max(1, ceil(max(abs(dx), max(abs(dz), abs(dy))) / 0.25).toInt())
        for (i in 0 until steps) {
            moveAxis(0, dx / steps, world)
            moveAxis(2, dz / steps, world)
            moveAxis(1, dy / steps, world)
        }
    }

    private fun moveAxis(axis: Int, d: Double, world: World) {
        if (d == 0.0) return
        shift(axis, d)
        if (collides(world)) {
            shift(axis, -d)
            if (axis == 1) {
                if (d < 0) onGround = true
                vy = 0.0
            } else if (!flying && onGround) {
                tryStepUp(axis, d, world)
            }
        }
    }

    /** Auto-jump over one-block ledges (handy with touch controls). */
    private fun tryStepUp(axis: Int, d: Double, world: World) {
        y += 1.02
        shift(axis, d)
        if (collides(world)) {
            shift(axis, -d)
            y -= 1.02
        } else {
            vy = 0.0
        }
    }

    private fun shift(axis: Int, d: Double) {
        when (axis) {
            0 -> x += d
            1 -> y += d
            else -> z += d
        }
    }

    private fun collides(world: World): Boolean {
        val x0 = floor(x - HALF_WIDTH).toInt()
        val x1 = floor(x + HALF_WIDTH).toInt()
        val y0 = floor(y).toInt()
        val y1 = floor(y + HEIGHT - 0.01).toInt()
        val z0 = floor(z - HALF_WIDTH).toInt()
        val z1 = floor(z + HALF_WIDTH).toInt()
        for (bx in x0..x1) for (by in y0..y1) for (bz in z0..z1) {
            if (world.isSolid(bx, by, bz)) return true
        }
        return false
    }

    fun intersectsBlock(bx: Int, by: Int, bz: Int): Boolean =
        x - HALF_WIDTH < bx + 1 && x + HALF_WIDTH > bx &&
            y < by + 1 && y + HEIGHT > by &&
            z - HALF_WIDTH < bz + 1 && z + HALF_WIDTH > bz
}
