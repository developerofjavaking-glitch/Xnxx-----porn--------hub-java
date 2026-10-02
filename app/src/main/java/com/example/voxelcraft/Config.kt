package com.example.voxelcraft

object Config {
    const val CHUNK_SIZE = 16
    const val WORLD_HEIGHT = 64
    const val SEA_LEVEL = 22
    const val SEED = 20240601L

    /** Chunks within this radius (in chunks) around the player are meshed and drawn. */
    const val RENDER_RADIUS = 7

    /** Block data is generated one ring further out so border faces can be culled correctly. */
    const val DATA_RADIUS = RENDER_RADIUS + 1

    /** Chunks farther than this are unloaded (a bit larger than DATA_RADIUS to avoid flicker). */
    const val UNLOAD_RADIUS = RENDER_RADIUS + 3

    const val MAX_GEN_IN_FLIGHT = 6
    const val MAX_MESH_IN_FLIGHT = 3
    const val UPLOADS_PER_FRAME = 3
}
