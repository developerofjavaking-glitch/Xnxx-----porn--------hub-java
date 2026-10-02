package com.example.voxelcraft

import kotlin.math.abs
import kotlin.math.floor

/** x/y/z = block that was hit, px/py/pz = the empty block in front of it (where to place). */
class RayHit(val x: Int, val y: Int, val z: Int, val px: Int, val py: Int, val pz: Int)

object Raycast {
    /** Voxel traversal (Amanatides & Woo). */
    fun cast(
        world: World,
        ox: Double, oy: Double, oz: Double,
        dx: Double, dy: Double, dz: Double,
        maxDist: Double
    ): RayHit? {
        var x = floor(ox).toInt()
        var y = floor(oy).toInt()
        var z = floor(oz).toInt()
        val stepX = if (dx > 0) 1 else -1
        val stepY = if (dy > 0) 1 else -1
        val stepZ = if (dz > 0) 1 else -1
        val inf = Double.POSITIVE_INFINITY
        val tDeltaX = if (dx != 0.0) abs(1.0 / dx) else inf
        val tDeltaY = if (dy != 0.0) abs(1.0 / dy) else inf
        val tDeltaZ = if (dz != 0.0) abs(1.0 / dz) else inf
        var tMaxX = if (dx > 0) (x + 1 - ox) / dx else if (dx < 0) (ox - x) / -dx else inf
        var tMaxY = if (dy > 0) (y + 1 - oy) / dy else if (dy < 0) (oy - y) / -dy else inf
        var tMaxZ = if (dz > 0) (z + 1 - oz) / dz else if (dz < 0) (oz - z) / -dz else inf

        var px = x
        var py = y
        var pz = z
        while (true) {
            px = x; py = y; pz = z
            val t: Double
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                t = tMaxX; x += stepX; tMaxX += tDeltaX
            } else if (tMaxY < tMaxZ) {
                t = tMaxY; y += stepY; tMaxY += tDeltaY
            } else {
                t = tMaxZ; z += stepZ; tMaxZ += tDeltaZ
            }
            if (t > maxDist) return null
            if (world.getBlock(x, y, z) != 0) return RayHit(x, y, z, px, py, pz)
        }
    }
}
