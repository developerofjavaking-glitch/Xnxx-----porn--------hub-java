package com.example.voxelcraft

object Blocks {
    const val AIR = 0
    const val GRASS = 1
    const val DIRT = 2
    const val STONE = 3
    const val SAND = 4
    const val SNOW = 5
    const val WOOD = 6
    const val BRICK = 7

    val names = arrayOf("Air", "Grass", "Dirt", "Stone", "Sand", "Snow", "Wood", "Brick")

    // Colours as 0xRRGGBB (no textures: flat colours + face shading).
    val top = intArrayOf(0, 0x56A03C, 0x79553A, 0x7D7D80, 0xDECD96, 0xF5F8FA, 0xA07846, 0x9C5246)
    val side = intArrayOf(0, 0x79553A, 0x79553A, 0x7D7D80, 0xDECD96, 0xE6ECF0, 0xA07846, 0x9C5246)
    val bottom = intArrayOf(0, 0x79553A, 0x79553A, 0x7D7D80, 0xDECD96, 0x79553A, 0xA07846, 0x9C5246)

    /** Blocks the player can place (cycled with the BLOCK button). */
    val palette = intArrayOf(DIRT, STONE, SAND, WOOD, BRICK, GRASS, SNOW)
}
