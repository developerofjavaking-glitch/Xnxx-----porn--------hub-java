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
    const val LEAVES = 8
    const val WATER = 9
    const val SEAGRASS = 10

    val names = arrayOf("Air", "Grass", "Dirt", "Stone", "Sand", "Snow", "Wood", "Brick", "Leaves", "Water", "Seagrass")

    // Colours as 0xRRGGBB (no textures: flat colours + face shading).
    val top = intArrayOf(
        0,
        0x56A03C, // Grass
        0x79553A, // Dirt
        0x7D7D80, // Stone
        0xDECD96, // Sand
        0xF5F8FA, // Snow
        0xA07846, // Wood
        0x9C5246, // Brick
        0x2E7D32, // Leaves (deep lush green)
        0x3498DB, // Water (cerulean blue)
        0x27AE60  // Seagrass (marine green)
    )
    val side = intArrayOf(
        0,
        0x79553A, // Grass side
        0x79553A, // Dirt side
        0x7D7D80, // Stone
        0xDECD96, // Sand
        0xE6ECF0, // Snow
        0x8D6236, // Wood side
        0x9C5246, // Brick
        0x266B2A, // Leaves side
        0x2980B9, // Water side
        0x1E8449  // Seagrass side
    )
    val bottom = intArrayOf(
        0,
        0x79553A, // Grass bottom
        0x79553A, // Dirt bottom
        0x7D7D80, // Stone
        0xDECD96, // Sand
        0x79553A, // Snow bottom
        0x7A532C, // Wood bottom
        0x9C5246, // Brick
        0x1B5E20, // Leaves bottom
        0x1F618D, // Water bottom
        0x145A32  // Seagrass bottom
    )

    /** Blocks the player can place (cycled with the BLOCK button). */
    val palette = intArrayOf(DIRT, STONE, SAND, WOOD, LEAVES, WATER, SEAGRASS, BRICK, GRASS, SNOW)
}
