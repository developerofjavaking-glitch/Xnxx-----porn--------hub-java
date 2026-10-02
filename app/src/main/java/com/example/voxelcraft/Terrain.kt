package com.example.voxelcraft

import kotlin.math.abs

object Terrain {

    private class TreeInfo(val x: Int, val z: Int, val groundH: Int, val trunkHeight: Int)

    fun riverValue(wx: Int, wz: Int): Double {
        val x = wx.toDouble()
        val z = wz.toDouble()
        return abs(Noise.fbm(x * 0.005 + 500.0, z * 0.005 + 500.0, 3))
    }

    private fun treeHash(cellX: Int, cellZ: Int): Long {
        var h = (cellX.toLong() * 3129871L) xor (cellZ.toLong() * 6187903L) xor Config.SEED
        h = h xor (h ushr 16)
        h *= 0x45d9f3bL
        h = h xor (h ushr 16)
        return h
    }

    private fun getTree(cellX: Int, cellZ: Int): TreeInfo? {
        val th = treeHash(cellX, cellZ)
        // ~45% of cells have a tree
        if ((th and 0xFF) % 100 >= 45) return null

        val offsetX = ((th ushr 8) and 3).toInt()
        val offsetZ = ((th ushr 12) and 3).toInt()
        val tx = cellX * 7 + 1 + offsetX
        val tz = cellZ * 7 + 1 + offsetZ

        val groundH = heightAt(tx, tz)
        // Trees grow on dry land, not submerged, not beaches, and not alpine snow
        if (groundH <= Config.SEA_LEVEL + 2 || groundH >= 44) return null
        if (riverValue(tx, tz) < 0.055) return null

        val trunkHeight = 4 + ((th ushr 16) and 1).toInt()
        return TreeInfo(tx, tz, groundH, trunkHeight)
    }

    fun heightAt(wx: Int, wz: Int): Int {
        val x = wx.toDouble()
        val z = wz.toDouble()
        val base = Noise.fbm(x * 0.007, z * 0.007, 4)
        val detail = Noise.fbm(x * 0.04 + 100.0, z * 0.04 + 100.0, 3)
        val ridge = Noise.fbm(x * 0.005 - 50.0, z * 0.005 - 50.0, 3)
        val ocean = Noise.fbm(x * 0.0025 + 300.0, z * 0.0025 + 300.0, 3)

        // Dip below sea level for expansive seas and oceans
        val seaDip = if (ocean < -0.05) (ocean - (-0.05)) * 22.0 else 0.0
        val mountain = maxOf(0.0, ridge * 1.6) * 32.0

        var h = 26.0 + base * 11.0 + detail * 4.0 + mountain + seaDip

        // River carving
        val riv = riverValue(wx, wz)
        val riverWidth = 0.05
        if (riv < riverWidth) {
            val carve = 1.0 - (riv / riverWidth)
            val riverBed = Config.SEA_LEVEL - 2.5 - carve * 2.0
            h = h * (1.0 - carve * carve) + riverBed * (carve * carve)
        }

        return h.toInt().coerceIn(2, Config.WORLD_HEIGHT - 3)
    }

    fun fill(c: Chunk) {
        val s = Config.CHUNK_SIZE
        val minWx = c.cx * s
        val minWz = c.cz * s

        // 1. Terrain and water bodies (sea and rivers)
        for (x in 0 until s) {
            for (z in 0 until s) {
                val wx = minWx + x
                val wz = minWz + z
                val h = heightAt(wx, wz)
                val beach = h <= Config.SEA_LEVEL + 2

                for (y in 0..h) {
                    val id = when {
                        y == 0 -> Blocks.STONE
                        y < h - 4 -> Blocks.STONE
                        y < h -> if (beach) Blocks.SAND else Blocks.DIRT
                        beach -> Blocks.SAND
                        h >= 52 -> Blocks.SNOW
                        h >= 44 -> Blocks.STONE
                        else -> Blocks.GRASS
                    }
                    c.blocks[c.index(x, y, z)] = id.toByte()
                }

                // Fill water up to SEA_LEVEL for seas and carved rivers
                if (h < Config.SEA_LEVEL) {
                    for (y in (h + 1)..Config.SEA_LEVEL) {
                        c.blocks[c.index(x, y, z)] = Blocks.WATER.toByte()
                    }
                }
            }
        }

        // 2. Tree trunks and leaf canopies
        val minCellX = Math.floorDiv(minWx - 3, 7)
        val maxCellX = Math.floorDiv(minWx + s + 2, 7)
        val minCellZ = Math.floorDiv(minWz - 3, 7)
        val maxCellZ = Math.floorDiv(minWz + s + 2, 7)

        for (cellX in minCellX..maxCellX) {
            for (cellZ in minCellZ..maxCellZ) {
                val tree = getTree(cellX, cellZ) ?: continue
                val tx = tree.x
                val tz = tree.z
                val groundH = tree.groundH
                val topY = groundH + tree.trunkHeight

                val lx = tx - minWx
                val lz = tz - minWz

                // Place wood trunk
                for (y in (groundH + 1)..topY) {
                    if (lx in 0 until s && lz in 0 until s && y in 0 until Config.WORLD_HEIGHT) {
                        c.blocks[c.index(lx, y, lz)] = Blocks.WOOD.toByte()
                    }
                }
                // Soil block right below trunk
                if (lx in 0 until s && lz in 0 until s && groundH in 0 until Config.WORLD_HEIGHT) {
                    c.blocks[c.index(lx, groundH, lz)] = Blocks.DIRT.toByte()
                }

                // Helper to safely place a leaf block
                fun placeLeaf(wx: Int, wy: Int, wz: Int) {
                    val px = wx - minWx
                    val pz = wz - minWz
                    if (px in 0 until s && pz in 0 until s && wy in 0 until Config.WORLD_HEIGHT) {
                        val idx = c.index(px, wy, pz)
                        if (c.blocks[idx] == Blocks.AIR.toByte()) {
                            c.blocks[idx] = Blocks.LEAVES.toByte()
                        }
                    }
                }

                // Lower canopy layers (5x5 without outer corners)
                for (ly in (topY - 2)..(topY - 1)) {
                    for (dx in -2..2) {
                        for (dz in -2..2) {
                            if (abs(dx) == 2 && abs(dz) == 2) continue
                            placeLeaf(tx + dx, ly, tz + dz)
                        }
                    }
                }
                // Upper canopy layer (3x3)
                for (dx in -1..1) {
                    for (dz in -1..1) {
                        placeLeaf(tx + dx, topY, tz + dz)
                    }
                }
                // Top cross (+)
                placeLeaf(tx, topY + 1, tz)
                placeLeaf(tx + 1, topY + 1, tz)
                placeLeaf(tx - 1, topY + 1, tz)
                placeLeaf(tx, topY + 1, tz + 1)
                placeLeaf(tx, topY + 1, tz - 1)
            }
        }
    }
}
