#include <cstdint>

#include "voxel.h"

namespace vc {

namespace {

// Face order: +X, -X, +Y, -Y, +Z, -Z. Corners are counter-clockwise seen from outside.
const float FACE_V[6][12] = {
    {1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1},
    {0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0},
    {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0},
    {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
    {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1},
    {1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0},
};
const int DX[6] = {1, -1, 0, 0, 0, 0};
const int DY[6] = {0, 0, 1, -1, 0, 0};
const int DZ[6] = {0, 0, 0, 0, 1, -1};
const float SHADE[6] = {0.80f, 0.80f, 1.0f, 0.5f, 0.65f, 0.65f};
const int ORDER[6] = {0, 1, 2, 0, 2, 3};

constexpr int MAX_BLOCK_TYPES = 256;
int g_top[MAX_BLOCK_TYPES];
int g_side[MAX_BLOCK_TYPES];
int g_bottom[MAX_BLOCK_TYPES];

float hash3(int x, int y, int z) {
    uint32_t h = (static_cast<uint32_t>(x) * 73856093u) ^ (static_cast<uint32_t>(y) * 19349663u) ^
                 (static_cast<uint32_t>(z) * 83492791u);
    h ^= (h >> 13);
    h *= 1274126177u;
    h ^= (h >> 16);
    return static_cast<float>(h & 0xFFFFu) / 65535.0f;
}

struct Ctx {
    const int8_t* self;
    const int8_t* xp;
    const int8_t* xm;
    const int8_t* zp;
    const int8_t* zm;
};

inline bool isSolid(const Ctx& c, int x, int y, int z) {
    if (y < 0) return true;
    if (y >= HEIGHT) return false;
    if (x >= 0 && x < CHUNK && z >= 0 && z < CHUNK) return c.self[blockIndex(x, y, z)] != 0;
    const int8_t* n;
    if (x < 0) n = c.xm;
    else if (x >= CHUNK) n = c.xp;
    else if (z < 0) n = c.zm;
    else n = c.zp;
    if (n == nullptr) return true;  // neighbour not available: hide the face
    return n[blockIndex(x & (CHUNK - 1), y, z & (CHUNK - 1))] != 0;
}

}  // namespace

void setPalette(const int* top, const int* side, const int* bottom, int count) {
    if (count > MAX_BLOCK_TYPES) count = MAX_BLOCK_TYPES;
    for (int i = 0; i < count; i++) {
        g_top[i] = top[i];
        g_side[i] = side[i];
        g_bottom[i] = bottom[i];
    }
}

int buildMesh(const int8_t* self, const int8_t* xp, const int8_t* xm, const int8_t* zp, const int8_t* zm,
              int cx, int cz, std::vector<float>& out) {
    const Ctx ctx{self, xp, xm, zp, zm};
    out.clear();
    out.reserve(1 << 15);

    for (int x = 0; x < CHUNK; x++) {
        for (int z = 0; z < CHUNK; z++) {
            for (int y = 0; y < HEIGHT; y++) {
                const int id = self[blockIndex(x, y, z)];
                if (id <= 0 || id >= MAX_BLOCK_TYPES) continue;
                const float variation = 0.93f + 0.07f * hash3(cx * CHUNK + x, y, cz * CHUNK + z);
                for (int f = 0; f < 6; f++) {
                    if (isSolid(ctx, x + DX[f], y + DY[f], z + DZ[f])) continue;
                    const int col = (f == 2) ? g_top[id] : (f == 3) ? g_bottom[id] : g_side[id];
                    const float k = SHADE[f] * variation;
                    const float r = static_cast<float>((col >> 16) & 255) / 255.0f * k;
                    const float g = static_cast<float>((col >> 8) & 255) / 255.0f * k;
                    const float b = static_cast<float>(col & 255) / 255.0f * k;
                    const float* v = FACE_V[f];
                    for (int i = 0; i < 6; i++) {
                        const int o = ORDER[i];
                        out.push_back(static_cast<float>(x) + v[o * 3]);
                        out.push_back(static_cast<float>(y) + v[o * 3 + 1]);
                        out.push_back(static_cast<float>(z) + v[o * 3 + 2]);
                        out.push_back(r);
                        out.push_back(g);
                        out.push_back(b);
                    }
                }
            }
        }
    }
    return static_cast<int>(out.size() / 6);
}

}  // namespace vc
