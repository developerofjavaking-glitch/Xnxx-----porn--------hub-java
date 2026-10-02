package com.example.voxelcraft

import java.nio.FloatBuffer

class MeshResult(val version: Int, val buffer: FloatBuffer, val vertexCount: Int)

class Chunk(val cx: Int, val cz: Int) {
    val blocks = ByteArray(Config.CHUNK_SIZE * Config.CHUNK_SIZE * Config.WORLD_HEIGHT)

    // Shared between the GL thread and worker threads.
    @Volatile var dataReady = false
    @Volatile var meshing = false
    @Volatile var removed = false
    @Volatile var version = 0          // bumped on every block edit
    @Volatile var modified = false     // has player edits (kept in memory when unloaded)
    @Volatile var pending: MeshResult? = null

    // GL thread only.
    var meshedVersion = -1
    var vbo = 0
    var vertexCount = 0

    fun index(x: Int, y: Int, z: Int): Int = (x * Config.CHUNK_SIZE + z) * Config.WORLD_HEIGHT + y
}
