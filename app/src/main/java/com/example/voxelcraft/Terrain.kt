package com.example.voxelcraft

import kotlin.math.abs

object Terrain {

    private class TreeInfo(val x: Int, val z: Int, val groundH: Int, val trunkHeight: Int, val isPine: Boolean)

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
        // ~50% of cells have a tree
        if ((th and 0xFF) % 100 >= 50) return null

        val offsetX = ((th ushr 8) and 3).toInt()
        val offsetZ = ((th ushr 12) and 3).toInt()
        val tx = cellX * 7 + 1 + offsetX
        val tz = cellZ * 7 + 1 + offsetZ

        val groundH = heightAt(tx, tz)
        // Trees grow on dry land, not submerged, not beaches, and not alpine snow
        if (groundH <= Config.SEA_LEVEL + 2 || groundH >= 44) return null
        if (riverValue(tx, tz) < 0.055) return null

        val isPine = ((th ushr 20) and 1) == 1L
        val trunkHeight = if (isPine) (7 + ((th ushr 16) and 3).toInt()) else (5 + ((th ushr 16) and 1).toInt())
        return TreeInfo(tx, tz, groundH, trunkHeight, isPine)
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

        // Smooth river and valley generation:
        // Guarantees continuous, unbroken waterways filled with water at SEA_LEVEL
        val riv = riverValue(wx, wz)
        val valleyWidth = 0.08
        if (riv < valleyWidth) {
            val riverWidth = 0.038
            if (riv < riverWidth) {
                // Inside river channel: riverbed is always below SEA_LEVEL (depth 3 to 4 blocks)
                val bedT = riv / riverWidth
                val bedY = (Config.SEA_LEVEL - 3.8) + bedT * 1.6 // 18.2 at center, 19.8 at edge
                h = bedY
            } else {
                // Valley slope: gentle banks rising from river's edge to surrounding land
                val t = (riv - riverWidth) / (valleyWidth - riverWidth)
                val smoothT = t * t * (3.0 - 2.0 * t) // smoothstep
                val bankY = Config.SEA_LEVEL + 0.8
                val targetH = bankY + (h - bankY).coerceAtLeast(0.0) * smoothT
                h = targetH
            }
        }

        return h.toInt().coerceIn(2, Config.WORLD_HEIGHT - 3)
    }

    fun fill(c: Chunk) {
        val s = Config.CHUNK_SIZE
        val minWx = c.cx * s
        val minWz = c.cz * s

        // 1. Terrain, rivers, seas, and detailed seabed
        for (x in 0 until s) {
            for (z in 0 until s) {
                val wx = minWx + x
                val wz = minWz + z
                val h = heightAt(wx, wz)
                val beach = h <= Config.SEA_LEVEL + 1
                val isUnderwater = h < Config.SEA_LEVEL

                // Detailed seabed composition using local noise
                val seabedNoise = ((wx * 37 + wz * 19 + (wx xor wz)) and 0xFF)
                val seabedBlock = when {
                    seabedNoise < 120 -> Blocks.SAND       // 47% golden sand
                    seabedNoise < 195 -> Blocks.STONE      // 30% river gravel/rock
                    else -> Blocks.DIRT                    // 23% river silt/clay
                }

                for (y in 0..h) {
                    val id = when {
                        y == 0 -> Blocks.STONE
                        y < h - 4 -> Blocks.STONE
                        isUnderwater -> if (y >= h - 2) seabedBlock else Blocks.STONE
                        y < h -> if (beach) Blocks.SAND else Blocks.DIRT
                        beach -> Blocks.SAND
                        h >= 52 -> Blocks.SNOW
                        h >= 44 -> Blocks.STONE
                        else -> Blocks.GRASS
                    }
                    c.blocks[c.index(x, y, z)] = id.toByte()
                }

                // Fill water in oceans and rivers up to SEA_LEVEL
                if (isUnderwater) {
                    for (y in (h + 1)..Config.SEA_LEVEL) {
                        c.blocks[c.index(x, y, z)] = Blocks.WATER.toByte()
                    }
                }
            }
        }

        // 2. Multi-tier detailed tree canopies
        val minCellX = Math.floorDiv(minWx - 4, 7)
        val maxCellX = Math.floorDiv(minWx + s + 3, 7)
        val minCellZ = Math.floorDiv(minWz - 4, 7)
        val maxCellZ = Math.floorDiv(minWz + s + 3, 7)

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

                if (tree.isPine) {
                    // Tiered pine foliage
                    for (ly in (topY - 4)..topY) {
                        val radius = if ((topY - ly) % 2 == 0) 2 else 1
                        for (dx in -radius..radius) {
                            for (dz in -radius..radius) {
                                if (radius == 2 && abs(dx) == 2 && abs(dz) == 2) continue
                                placeLeaf(tx + dx, ly, tz + dz)
                            }
                        }
                    }
                    placeLeaf(tx, topY + 1, tz)
                } else {
                    // Lush Oak foliage with natural overhangs
                    for (ly in (topY - 3)..(topY - 1)) {
                        for (dx in -2..2) {
                            for (dz in -2..2) {
                                if (abs(dx) == 2 && abs(dz) == 2 && ly != topY - 2) continue
                                placeLeaf(tx + dx, ly, tz + dz)
                            }
                        }
                    }
                    for (dx in -1..1) {
                        for (dz in -1..1) {
                            placeLeaf(tx + dx, topY, tz + dz)
                        }
                    }
                    // Cross cap + random corner foliage
                    placeLeaf(tx, topY + 1, tz)
                    placeLeaf(tx + 1, topY + 1, tz)
                    placeLeaf(tx - 1, topY + 1, tz)
                    placeLeaf(tx, topY + 1, tz + 1)
                    placeLeaf(tx, topY + 1, tz - 1)
                    // Hanging leaf accents
                    placeLeaf(tx + 2, topY - 4, tz)
                    placeLeaf(tx - 2, topY - 4, tz)
                    placeLeaf(tx, topY - 4, tz + 2)
                    placeLeaf(tx, topY - 4, tz - 2)
                }
            }
        }
    }
}
