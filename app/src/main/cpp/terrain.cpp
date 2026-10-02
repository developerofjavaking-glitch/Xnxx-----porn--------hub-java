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

    // Smooth river and valley generation:
    // Guarantees continuous, unbroken waterways filled with water at SEA_LEVEL
    const double riv = riverValue(wx, wz);
    const double valleyWidth = 0.08;
    if (riv < valleyWidth) {
        const double riverWidth = 0.038;
        if (riv < riverWidth) {
            const double bedT = riv / riverWidth;
            const double bedY = (SEA_LEVEL - 3.8) + bedT * 1.6;
            h = bedY;
        } else {
            const double t = (riv - riverWidth) / (valleyWidth - riverWidth);
            const double smoothT = t * t * (3.0 - 2.0 * t);
            const double bankY = SEA_LEVEL + 0.8;
            const double targetH = bankY + std::max(0.0, h - bankY) * smoothT;
            h = targetH;
        }
    }

    return std::min(std::max(static_cast<int>(h), 2), HEIGHT - 3);
}

void fillChunk(int8_t* blocks, int cx, int cz) {
    std::fill(blocks, blocks + CHUNK_BYTES, static_cast<int8_t>(AIR));
    const int minWx = cx * CHUNK;
    const int minWz = cz * CHUNK;

    // 1. Terrain, rivers, seas, and detailed seabed
    for (int x = 0; x < CHUNK; x++) {
        for (int z = 0; z < CHUNK; z++) {
            const int wx = minWx + x;
            const int wz = minWz + z;
            const int h = heightAt(wx, wz);
            const bool beach = h <= SEA_LEVEL + 1;
            const bool isUnderwater = h < SEA_LEVEL;

            const int seabedNoise = ((wx * 37 + wz * 19 + (wx ^ wz)) & 0xFF);
            int8_t seabedBlock;
            if (seabedNoise < 120) seabedBlock = SAND;
            else if (seabedNoise < 195) seabedBlock = STONE;
            else seabedBlock = DIRT;

            for (int y = 0; y <= h; y++) {
                int8_t id;
                if (y == 0) id = STONE;
                else if (y < h - 4) id = STONE;
                else if (isUnderwater) id = (y >= h - 2) ? seabedBlock : STONE;
                else if (y < h) id = beach ? SAND : DIRT;
                else if (beach) id = SAND;
                else if (h >= 52) id = SNOW;
                else if (h >= 44) id = STONE;
                else id = GRASS;
                blocks[blockIndex(x, y, z)] = id;
            }

            // Fill water in oceans and rivers up to SEA_LEVEL
            if (isUnderwater) {
                for (int y = h + 1; y <= SEA_LEVEL; y++) {
                    blocks[blockIndex(x, y, z)] = static_cast<int8_t>(WATER);
                }
            }
        }
    }

    // 2. Multi-tier detailed tree canopies
    const int minCellX = floorDiv(minWx - 4, 7);
    const int maxCellX = floorDiv(minWx + CHUNK + 3, 7);
    const int minCellZ = floorDiv(minWz - 4, 7);
    const int maxCellZ = floorDiv(minWz + CHUNK + 3, 7);

    for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
        for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
            int64_t th = treeHash(cellX, cellZ);
            if ((th & 0xFF) % 100 >= 50) continue;

            const int offsetX = static_cast<int>((th >> 8) & 3);
            const int offsetZ = static_cast<int>((th >> 12) & 3);
            const int tx = cellX * 7 + 1 + offsetX;
            const int tz = cellZ * 7 + 1 + offsetZ;

            const int groundH = heightAt(tx, tz);
            if (groundH <= SEA_LEVEL + 2 || groundH >= 44) continue;
            if (riverValue(tx, tz) < 0.055) continue;

            const bool isPine = ((th >> 20) & 1) == 1;
            const int trunkHeight = isPine ? (7 + static_cast<int>((th >> 16) & 3))
                                           : (5 + static_cast<int>((th >> 16) & 1));
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

            if (isPine) {
                for (int ly = topY - 4; ly <= topY; ly++) {
                    const int radius = ((topY - ly) % 2 == 0) ? 2 : 1;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (radius == 2 && std::abs(dx) == 2 && std::abs(dz) == 2) continue;
                            placeLeaf(tx + dx, ly, tz + dz);
                        }
                    }
                }
                placeLeaf(tx, topY + 1, tz);
            } else {
                for (int ly = topY - 3; ly <= topY - 1; ly++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (std::abs(dx) == 2 && std::abs(dz) == 2 && ly != topY - 2) continue;
                            placeLeaf(tx + dx, ly, tz + dz);
                        }
                    }
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        placeLeaf(tx + dx, topY, tz + dz);
                    }
                }
                placeLeaf(tx, topY + 1, tz);
                placeLeaf(tx + 1, topY + 1, tz);
                placeLeaf(tx - 1, topY + 1, tz);
                placeLeaf(tx, topY + 1, tz + 1);
                placeLeaf(tx, topY + 1, tz - 1);
                placeLeaf(tx + 2, topY - 4, tz);
                placeLeaf(tx - 2, topY - 4, tz);
                placeLeaf(tx, topY - 4, tz + 2);
                placeLeaf(tx, topY - 4, tz - 2);
            }
        }
    }
}

}  // namespace vc
