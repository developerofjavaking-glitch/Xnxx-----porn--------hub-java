package com.example.voxelcraft

import kotlin.math.floor

/** Classic 2D Perlin noise + fractal sum. Works for negative / huge coordinates. */
object Noise {
    private val perm = IntArray(512)

    init {
        val p = IntArray(256) { it }
        val rnd = java.util.Random(Config.SEED)
        for (i in 255 downTo 1) {
            val j = rnd.nextInt(i + 1)
            val t = p[i]
            p[i] = p[j]
            p[j] = t
        }
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    private fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(a: Double, b: Double, t: Double) = a + t * (b - a)

    private fun grad(h: Int, x: Double, y: Double): Double = when (h and 7) {
        0 -> x + y
        1 -> -x + y
        2 -> x - y
        3 -> -x - y
        4 -> x
        5 -> -x
        6 -> y
        else -> -y
    }

    fun perlin(x: Double, y: Double): Double {
        val fx = floor(x)
        val fy = floor(y)
        val xi = fx.toInt() and 255
        val yi = fy.toInt() and 255
        val xf = x - fx
        val yf = y - fy
        val u = fade(xf)
        val v = fade(yf)
        val aa = perm[perm[xi] + yi]
        val ab = perm[perm[xi] + yi + 1]
        val ba = perm[perm[xi + 1] + yi]
        val bb = perm[perm[xi + 1] + yi + 1]
        val x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u)
        val x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u)
        return lerp(x1, x2, v)
    }

    fun fbm(x: Double, y: Double, octaves: Int): Double {
        var amp = 1.0
        var freq = 1.0
        var sum = 0.0
        var norm = 0.0
        for (i in 0 until octaves) {
            sum += perlin(x * freq, y * freq) * amp
            norm += amp
            amp *= 0.5
            freq *= 2.0
        }
        return sum / norm
    }
}
