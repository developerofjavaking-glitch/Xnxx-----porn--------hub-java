#include <algorithm>
#include <cmath>
#include <cstdlib>

#include "noise.h"
#include "voxel.h"

namespace vc {

namespace {

inline int64_t treeHash(int cellX, int cellZ) {
    int64_t h = (static_cast<int64_t>(cellX) * 3129871LL) ^ (static_cast<int64_t>(cellZ) * 6187903LL) ^ 20240601LL;
    h ^= (h >> 16);
    h *= 0x45d9f3bLL;
    h ^= (h >> 16);
    return h;
}

inline double riverValue(int wx, int wz) {
    const double x = wx;
    const double z = wz;
    return std::abs(noise_fbm(x * 0.005 + 500.0, z * 0.005 + 500.0, 3));
}

inline int floorDiv(int a, int b) {
    int res = a / b;
    int rem = a % b;
    if (rem != 0 && ((a < 0) ^ (b < 0))) res--;
    return res;
}

}  // namespace

int heightAt(int wx, int wz) {
    const double x = wx;
    const double z = wz;
    const double base = noise_fbm(x * 0.007, z * 0.007, 4);
    const double detail = noise_fbm(x * 0.04 + 100.0, z * 0.04 + 100.0, 3);
    const double ridge = noise_fbm(x * 0.005 - 50.0, z * 0.005 - 50.0, 3);
    const double ocean = noise_fbm(x * 0.0025 + 300.0, z * 0.0025 + 300.0, 3);

    const double seaDip = (ocean < -0.05) ? (ocean - (-0.05)) * 22.0 : 0.0;
    const double mountain = std::max(0.0, ridge * 1.6) * 32.0;

    double h = 26.0 + base * 11.0 + detail * 4.0 + mountain + seaDip;

    const double riv = riverValue(wx, wz);
    const double riverWidth = 0.05;
    if (riv < riverWidth) {
        const double carve = 1.0 - (riv / riverWidth);
        const double riverBed = SEA_LEVEL - 2.5 - carve * 2.0;
        h = h * (1.0 - carve * carve) + riverBed * (carve * carve);
    }

    return std::min(std::max(static_cast<int>(h), 2), HEIGHT - 3);
}

void fillChunk(int8_t* blocks, int cx, int cz) {
    std::fill(blocks, blocks + CHUNK_BYTES, static_cast<int8_t>(AIR));
    const int minWx = cx * CHUNK;
    const int minWz = cz * CHUNK;

    // 1. Terrain and water bodies (sea and rivers)
    for (int x = 0; x < CHUNK; x++) {
        for (int z = 0; z < CHUNK; z++) {
            const int wx = minWx + x;
            const int wz = minWz + z;
            const int h = heightAt(wx, wz);
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

            // Fill water up to SEA_LEVEL for seas and carved rivers
            if (h < SEA_LEVEL) {
                for (int y = h + 1; y <= SEA_LEVEL; y++) {
                    blocks[blockIndex(x, y, z)] = static_cast<int8_t>(WATER);
                }
            }
        }
    }

    // 2. Tree trunks and leaf canopies
    const int minCellX = floorDiv(minWx - 3, 7);
    const int maxCellX = floorDiv(minWx + CHUNK + 2, 7);
    const int minCellZ = floorDiv(minWz - 3, 7);
    const int maxCellZ = floorDiv(minWz + CHUNK + 2, 7);

    for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
        for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
            const int64_t th = treeHash(cellX, cellZ);
            if ((th & 0xFF) % 100 >= 45) continue;

            const int offsetX = static_cast<int>((th >> 8) & 3);
            const int offsetZ = static_cast<int>((th >> 12) & 3);
            const int tx = cellX * 7 + 1 + offsetX;
            const int tz = cellZ * 7 + 1 + offsetZ;

            const int groundH = heightAt(tx, tz);
            if (groundH <= SEA_LEVEL + 2 || groundH >= 44) continue;
            if (riverValue(tx, tz) < 0.055) continue;

            const int trunkHeight = 4 + static_cast<int>((th >> 16) & 1);
            const int topY = groundH + trunkHeight;

            const int lx = tx - minWx;
            const int lz = tz - minWz;

            // Wood trunk
            for (int y = groundH + 1; y <= topY; y++) {
                if (lx >= 0 && lx < CHUNK && lz >= 0 && lz < CHUNK && y >= 0 && y < HEIGHT) {
                    blocks[blockIndex(lx, y, lz)] = static_cast<int8_t>(WOOD);
                }
            }
            // Soil block below trunk
            if (lx >= 0 && lx < CHUNK && lz >= 0 && lz < CHUNK && groundH >= 0 && groundH < HEIGHT) {
                blocks[blockIndex(lx, groundH, lz)] = static_cast<int8_t>(DIRT);
            }

            auto placeLeaf = [&](int wx, int wy, int wz) {
                const int px = wx - minWx;
                const int pz = wz - minWz;
                if (px >= 0 && px < CHUNK && pz >= 0 && pz < CHUNK && wy >= 0 && wy < HEIGHT) {
                    const int idx = blockIndex(px, wy, pz);
                    if (blocks[idx] == static_cast<int8_t>(AIR)) {
                        blocks[idx] = static_cast<int8_t>(LEAVES);
                    }
                }
            };

            // Lower canopy (5x5 without corners)
            for (int ly = topY - 2; ly <= topY - 1; ly++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (std::abs(dx) == 2 && std::abs(dz) == 2) continue;
                        placeLeaf(tx + dx, ly, tz + dz);
                    }
                }
            }
            // Upper canopy (3x3)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    placeLeaf(tx + dx, topY, tz + dz);
                }
            }
            // Top cross (+)
            placeLeaf(tx, topY + 1, tz);
            placeLeaf(tx + 1, topY + 1, tz);
            placeLeaf(tx - 1, topY + 1, tz);
            placeLeaf(tx, topY + 1, tz + 1);
            placeLeaf(tx, topY + 1, tz - 1);
        }
    }
}

}  // namespace vc
