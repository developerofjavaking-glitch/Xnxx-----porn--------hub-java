package com.example.voxelcraft

import java.nio.ByteBuffer
import java.nio.ByteOrder

class FloatList(capacity: Int) {
    var a = FloatArray(capacity)
    var n = 0

    fun add6(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float) {
        if (n + 6 > a.size) a = a.copyOf(a.size * 2)
        a[n] = x; a[n + 1] = y; a[n + 2] = z
        a[n + 3] = r; a[n + 4] = g; a[n + 5] = b
        n += 6
    }
}

/** Builds a triangle mesh for one chunk (hidden faces are skipped). Runs on worker threads. */
object Mesher {
    // Face order: +X, -X, +Y, -Y, +Z, -Z. Corners are counter-clockwise seen from outside.
    private val FACE_V = arrayOf(
        floatArrayOf(1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 1f),
        floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 0f, 0f, 0f, 0f),
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 1f, 0f),
        floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 1f, 1f),
        floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f)
    )
    private val DX = intArrayOf(1, -1, 0, 0, 0, 0)
    private val DY = intArrayOf(0, 0, 1, -1, 0, 0)
    private val DZ = intArrayOf(0, 0, 0, 0, 1, -1)
    private val SHADE = floatArrayOf(0.80f, 0.80f, 1.0f, 0.5f, 0.65f, 0.65f)
    private val ORDER = intArrayOf(0, 1, 2, 0, 2, 3)

    private fun hash(x: Int, y: Int, z: Int): Float {
        var h = (x * 73856093) xor (y * 19349663) xor (z * 83492791)
        h = h xor (h ushr 13)
        h *= 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    private fun isSolid(
        c: Chunk, xp: Chunk?, xm: Chunk?, zp: Chunk?, zm: Chunk?,
        x: Int, y: Int, z: Int
    ): Boolean {
        val s = Config.CHUNK_SIZE
        if (y < 0) return true
        if (y >= Config.WORLD_HEIGHT) return false
        if (x in 0 until s && z in 0 until s) return c.blocks[c.index(x, y, z)].toInt() != 0
        val n = when {
            x < 0 -> xm
            x >= s -> xp
            z < 0 -> zm
            else -> zp
        } ?: return true
        return n.blocks[n.index(x and (s - 1), y, z and (s - 1))].toInt() != 0
    }

    fun build(world: World, c: Chunk, version: Int): MeshResult {
        if (NativeLib.available) return buildNative(world, c, version)
        return buildKotlin(world, c, version)
    }

    /** C++ implementation (mesher.cpp). */
    private fun buildNative(world: World, c: Chunk, version: Int): MeshResult {
        val verts = NativeLib.buildMesh(
            c.blocks,
            world.getChunk(c.cx + 1, c.cz)?.blocks,
            world.getChunk(c.cx - 1, c.cz)?.blocks,
            world.getChunk(c.cx, c.cz + 1)?.blocks,
            world.getChunk(c.cx, c.cz - 1)?.blocks,
            c.cx, c.cz
        )
        val bytes = ByteBuffer.allocateDirect(verts * 24).order(ByteOrder.nativeOrder())
        if (verts > 0 && !NativeLib.copyMesh(bytes, verts)) throw IllegalStateException("native mesh copy failed")
        return MeshResult(version, bytes.asFloatBuffer(), verts)
    }

    /** Kotlin fallback, used when the native library is missing. */
    private fun buildKotlin(world: World, c: Chunk, version: Int): MeshResult {
        val s = Config.CHUNK_SIZE
        val xp = world.getChunk(c.cx + 1, c.cz)
        val xm = world.getChunk(c.cx - 1, c.cz)
        val zp = world.getChunk(c.cx, c.cz + 1)
        val zm = world.getChunk(c.cx, c.cz - 1)
        val out = FloatList(1 shl 15)

        for (x in 0 until s) {
            for (z in 0 until s) {
                for (y in 0 until Config.WORLD_HEIGHT) {
                    val id = c.blocks[c.index(x, y, z)].toInt()
                    if (id == 0) continue
                    val variation = 0.93f + 0.07f * hash(c.cx * s + x, y, c.cz * s + z)
                    for (f in 0 until 6) {
                        if (isSolid(c, xp, xm, zp, zm, x + DX[f], y + DY[f], z + DZ[f])) continue
                        val col = when (f) {
                            2 -> Blocks.top[id]
                            3 -> Blocks.bottom[id]
                            else -> Blocks.side[id]
                        }
                        val k = SHADE[f] * variation
                        val r = ((col shr 16) and 255) / 255f * k
                        val g = ((col shr 8) and 255) / 255f * k
                        val b = (col and 255) / 255f * k
                        val v = FACE_V[f]
                        for (i in ORDER) {
                            out.add6(x + v[i * 3], y + v[i * 3 + 1], z + v[i * 3 + 2], r, g, b)
                        }
                    }
                }
            }
        }

        val buf = ByteBuffer.allocateDirect(out.n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(out.a, 0, out.n)
        buf.position(0)
        return MeshResult(version, buf, out.n / 6)
    }
}
