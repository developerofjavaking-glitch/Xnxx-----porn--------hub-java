package com.example.voxelcraft

import android.opengl.GLES20
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Infinite world made of chunks. Chunks are generated / meshed on worker threads around the player
 * and unloaded again when the player moves away. update() and uploadMeshes() run on the GL thread.
 */
class World {
    companion object {
        private const val TAG = "World"
        fun key(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
    }

    val chunks = ConcurrentHashMap<Long, Chunk>()
    private val saved = ConcurrentHashMap<Long, ByteArray>()       // edited chunks that were unloaded
    private val uploadQueue = ConcurrentLinkedQueue<Chunk>()

    private val pool = Executors.newFixedThreadPool(
        (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 3),
        ThreadFactory { r ->
            Thread(r, "chunk-worker").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    )

    // Offsets around the player, nearest first.
    private val spiralDx: IntArray
    private val spiralDz: IntArray
    private val spiralD2: IntArray

    init {
        val r = Config.DATA_RADIUS
        val list = ArrayList<IntArray>()
        for (dx in -r..r) for (dz in -r..r) {
            val d2 = dx * dx + dz * dz
            if (d2 <= r * r) list.add(intArrayOf(dx, dz, d2))
        }
        list.sortBy { it[2] }
        spiralDx = IntArray(list.size) { list[it][0] }
        spiralDz = IntArray(list.size) { list[it][1] }
        spiralD2 = IntArray(list.size) { list[it][2] }
    }

    fun getChunk(cx: Int, cz: Int): Chunk? = chunks[key(cx, cz)]

    fun getBlock(x: Int, y: Int, z: Int): Int {
        if (y < 0 || y >= Config.WORLD_HEIGHT) return 0
        val c = getChunk(x shr 4, z shr 4) ?: return 0
        if (!c.dataReady) return 0
        return c.blocks[c.index(x and 15, y, z and 15)].toInt()
    }

    /** Collision query: unloaded terrain and the world floor count as solid. */
    fun isSolid(x: Int, y: Int, z: Int): Boolean {
        if (y < 0) return true
        if (y >= Config.WORLD_HEIGHT) return false
        val c = getChunk(x shr 4, z shr 4) ?: return true
        if (!c.dataReady) return true
        return c.blocks[c.index(x and 15, y, z and 15)].toInt() != 0
    }

    fun setBlock(x: Int, y: Int, z: Int, id: Int) {
        if (y < 0 || y >= Config.WORLD_HEIGHT) return
        val cx = x shr 4
        val cz = z shr 4
        val c = getChunk(cx, cz) ?: return
        if (!c.dataReady) return
        val lx = x and 15
        val lz = z and 15
        c.blocks[c.index(lx, y, lz)] = id.toByte()
        c.modified = true
        c.version++
        // Neighbouring chunks show faces that depend on this block.
        if (lx == 0) getChunk(cx - 1, cz)?.let { it.version++ }
        if (lx == 15) getChunk(cx + 1, cz)?.let { it.version++ }
        if (lz == 0) getChunk(cx, cz - 1)?.let { it.version++ }
        if (lz == 15) getChunk(cx, cz + 1)?.let { it.version++ }
    }

    /** Highest solid block at a world column (used for spawning). */
    fun surfaceY(x: Int, z: Int): Int {
        for (y in Config.WORLD_HEIGHT - 1 downTo 0) if (getBlock(x, y, z) != 0) return y
        return 0
    }

    // ------------------------------------------------------------------ streaming (GL thread)

    fun update(pcx: Int, pcz: Int) {
        var genInFlight = 0
        var meshInFlight = 0
        val unload2 = Config.UNLOAD_RADIUS * Config.UNLOAD_RADIUS

        // 1. Unload far chunks.
        for (c in chunks.values) {
            val dx = c.cx - pcx
            val dz = c.cz - pcz
            if (dx * dx + dz * dz > unload2) {
                chunks.remove(key(c.cx, c.cz))
                c.removed = true
                if (c.modified && c.dataReady) saved[key(c.cx, c.cz)] = c.blocks.copyOf()
                if (c.vbo != 0) {
                    GLES20.glDeleteBuffers(1, intArrayOf(c.vbo), 0)
                    c.vbo = 0
                }
                c.pending = null
                c.vertexCount = 0
            } else {
                if (!c.dataReady) genInFlight++
                if (c.meshing) meshInFlight++
            }
        }

        // 2. Generate missing chunks, nearest first.
        for (i in spiralDx.indices) {
            if (genInFlight >= Config.MAX_GEN_IN_FLIGHT) break
            val cx = pcx + spiralDx[i]
            val cz = pcz + spiralDz[i]
            if (chunks.containsKey(key(cx, cz))) continue
            val c = Chunk(cx, cz)
            chunks[key(cx, cz)] = c
            submitGen(c)
            genInFlight++
        }

        // 3. Mesh chunks whose neighbours have data.
        val render2 = Config.RENDER_RADIUS * Config.RENDER_RADIUS
        for (i in spiralDx.indices) {
            if (spiralD2[i] > render2 || meshInFlight >= Config.MAX_MESH_IN_FLIGHT) break
            val cx = pcx + spiralDx[i]
            val cz = pcz + spiralDz[i]
            val c = getChunk(cx, cz) ?: continue
            if (!c.dataReady || c.meshing || c.meshedVersion == c.version) continue
            if (c.pending?.version == c.version) continue
            if (getChunk(cx + 1, cz)?.dataReady != true) continue
            if (getChunk(cx - 1, cz)?.dataReady != true) continue
            if (getChunk(cx, cz + 1)?.dataReady != true) continue
            if (getChunk(cx, cz - 1)?.dataReady != true) continue
            c.meshing = true
            submitMesh(c)
            meshInFlight++
        }
    }

    fun uploadMeshes(budget: Int) {
        var left = budget
        while (left > 0) {
            val c = uploadQueue.poll() ?: return
            if (c.removed) continue
            val p = c.pending ?: continue
            c.pending = null
            if (p.vertexCount > 0) {
                if (c.vbo == 0) {
                    val ids = IntArray(1)
                    GLES20.glGenBuffers(1, ids, 0)
                    c.vbo = ids[0]
                }
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, c.vbo)
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, p.vertexCount * 24, p.buffer, GLES20.GL_STATIC_DRAW)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            }
            c.vertexCount = p.vertexCount
            c.meshedVersion = p.version
            left--
        }
    }

    /** Call after the GL context was (re)created: old buffer handles are invalid. */
    fun onContextLost() {
        uploadQueue.clear()
        for (c in chunks.values) {
            c.vbo = 0
            c.vertexCount = 0
            c.meshedVersion = -1
            c.pending = null
        }
    }

    fun shutdown() {
        pool.shutdownNow()
    }

    // ------------------------------------------------------------------ workers

    private fun submitGen(c: Chunk) {
        pool.execute {
            try {
                val s = saved[key(c.cx, c.cz)]
                if (s != null) System.arraycopy(s, 0, c.blocks, 0, s.size)
                else if (NativeLib.available) NativeLib.fillChunk(c.blocks, c.cx, c.cz)
                else Terrain.fill(c)
            } catch (t: Throwable) {
                Log.e(TAG, "chunk generation failed", t)
            } finally {
                c.dataReady = true
            }
        }
    }

    private fun submitMesh(c: Chunk) {
        val version = c.version
        pool.execute {
            try {
                val r = Mesher.build(this, c, version)
                if (!c.removed) {
                    c.pending = r
                    uploadQueue.add(c)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "meshing failed", t)
            } finally {
                c.meshing = false
            }
        }
    }
}
