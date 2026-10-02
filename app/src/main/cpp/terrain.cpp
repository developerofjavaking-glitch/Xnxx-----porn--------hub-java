#include <algorithm>

#include "noise.h"
#include "voxel.h"

namespace vc {

int heightAt(int wx, int wz) {
    const double x = wx;
    const double z = wz;
    const double base = noise_fbm(x * 0.008, z * 0.008, 4);
    const double detail = noise_fbm(x * 0.04 + 100.0, z * 0.04 + 100.0, 3);
    const double ridge = noise_fbm(x * 0.005 - 50.0, z * 0.005 - 50.0, 3);
    const double h = 27.0 + base * 14.0 + detail * 5.0 + std::max(0.0, ridge * 1.7) * 34.0;
    return std::min(std::max(static_cast<int>(h), 2), HEIGHT - 3);
}

void fillChunk(int8_t* blocks, int cx, int cz) {
    std::fill(blocks, blocks + CHUNK_BYTES, static_cast<int8_t>(AIR));
    for (int x = 0; x < CHUNK; x++) {
        for (int z = 0; z < CHUNK; z++) {
            const int h = heightAt(cx * CHUNK + x, cz * CHUNK + z);
            const bool beach = h <= SEA_LEVEL + 2;
            for (int y = 0; y <= h; y++) {
                int8_t id;
                if (y == 0) id = STONE;
                else if (y < h - 4) id = STONE;
                else if (y < h) id = beach ? SAND : DIRT;
                else if (beach) id = SAND;
                else if (h >= 52) id = SNOW;
                else if (h >= 44) id = STONE;
                else id = GRASS;
                blocks[blockIndex(x, y, z)] = id;
            }
        }
    }
}

}  // namespace vc
