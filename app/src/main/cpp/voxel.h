#ifndef VOXEL_COMMON_H
#define VOXEL_COMMON_H

#include <cstdint>
#include <vector>

namespace vc {

// Must match Config.kt (CHUNK_SIZE, WORLD_HEIGHT, SEA_LEVEL).
constexpr int CHUNK = 16;
constexpr int HEIGHT = 64;
constexpr int SEA_LEVEL = 22;
constexpr int CHUNK_BYTES = CHUNK * CHUNK * HEIGHT;

// Must match Blocks.kt.
enum Block : int8_t { AIR = 0, GRASS = 1, DIRT = 2, STONE = 3, SAND = 4, SNOW = 5, WOOD = 6, BRICK = 7 };

inline int blockIndex(int x, int y, int z) { return (x * CHUNK + z) * HEIGHT + y; }

// terrain.cpp
int heightAt(int wx, int wz);
void fillChunk(int8_t* blocks, int cx, int cz);

// mesher.cpp
void setPalette(const int* top, const int* side, const int* bottom, int count);

// Builds the mesh of one chunk into `out` (6 floats per vertex: x y z r g b, 6 vertices per face).
// Neighbour pointers may be null (treated as solid). Returns the vertex count.
int buildMesh(const int8_t* self, const int8_t* xp, const int8_t* xm, const int8_t* zp, const int8_t* zm,
              int cx, int cz, std::vector<float>& out);

}  // namespace vc

#endif
