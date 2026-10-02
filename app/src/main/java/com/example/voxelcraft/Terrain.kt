package com.example.voxelcraft

object Terrain {

    fun heightAt(wx: Int, wz: Int): Int {
        val x = wx.toDouble()
        val z = wz.toDouble()
        val base = Noise.fbm(x * 0.008, z * 0.008, 4)
        val detail = Noise.fbm(x * 0.04 + 100.0, z * 0.04 + 100.0, 3)
        val ridge = Noise.fbm(x * 0.005 - 50.0, z * 0.005 - 50.0, 3)
        val h = 27.0 + base * 14.0 + detail * 5.0 + maxOf(0.0, ridge * 1.7) * 34.0
        return h.toInt().coerceIn(2, Config.WORLD_HEIGHT - 3)
    }

    fun fill(c: Chunk) {
        val s = Config.CHUNK_SIZE
        for (x in 0 until s) {
            for (z in 0 until s) {
                val h = heightAt(c.cx * s + x, c.cz * s + z)
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
            }
        }
    }
}
