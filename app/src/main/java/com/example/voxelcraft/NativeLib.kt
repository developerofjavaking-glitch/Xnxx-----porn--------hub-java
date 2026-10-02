package com.example.voxelcraft

import android.util.Log
import java.nio.ByteBuffer

/**
 * Bridge to the C/C++ code in src/main/cpp (libvoxelnative.so):
 *  - terrain generation  (noise.c, terrain.cpp)
 *  - chunk mesh building (mesher.cpp)
 * If the library cannot be loaded the game falls back to the Kotlin implementations
 * (Terrain.kt / Mesher.build), so [available] tells which engine is running.
 */
object NativeLib {
    @Volatile
    var available = false
        private set

    init {
        try {
            System.loadLibrary("voxelnative")
            if (nativeChunkBytes() == Config.CHUNK_SIZE * Config.CHUNK_SIZE * Config.WORLD_HEIGHT) {
                nativeInit(Config.SEED, Blocks.top, Blocks.side, Blocks.bottom)
                available = true
            } else {
                Log.w("NativeLib", "native constants do not match Config.kt, using Kotlin fallback")
            }
        } catch (t: Throwable) {
            Log.w("NativeLib", "native library not available, using Kotlin fallback: $t")
        }
    }

    private external fun nativeInit(seed: Long, top: IntArray, side: IntArray, bottom: IntArray)
    private external fun nativeChunkBytes(): Int
    private external fun nativeFillChunk(blocks: ByteArray, cx: Int, cz: Int)
    private external fun nativeBuildMesh(
        self: ByteArray, xp: ByteArray?, xm: ByteArray?, zp: ByteArray?, zm: ByteArray?, cx: Int, cz: Int
    ): Int
    private external fun nativeCopyMesh(dst: ByteBuffer, vertexCount: Int): Boolean

    fun fillChunk(blocks: ByteArray, cx: Int, cz: Int) = nativeFillChunk(blocks, cx, cz)

    /** Builds the mesh natively; returns the vertex count (6 floats per vertex). */
    fun buildMesh(
        self: ByteArray, xp: ByteArray?, xm: ByteArray?, zp: ByteArray?, zm: ByteArray?, cx: Int, cz: Int
    ): Int = nativeBuildMesh(self, xp, xm, zp, zm, cx, cz)

    /** Must be called on the same thread right after [buildMesh]. */
    fun copyMesh(dst: ByteBuffer, vertexCount: Int): Boolean = nativeCopyMesh(dst, vertexCount)
}
