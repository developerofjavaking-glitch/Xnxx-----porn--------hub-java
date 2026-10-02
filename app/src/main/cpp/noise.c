/* Plain C: 2D Perlin noise. */
#include "noise.h"

#include <math.h>
#include <stdint.h>

static int perm[512];

/* ---- java.util.Random (LCG) ---- */
static uint64_t jr_seed;
#define JR_MASK ((1ULL << 48) - 1ULL)

static void jr_set(int64_t s) {
    jr_seed = ((uint64_t)s ^ 0x5DEECE66DULL) & JR_MASK;
}

static int32_t jr_next(int bits) {
    jr_seed = (jr_seed * 0x5DEECE66DULL + 0xBULL) & JR_MASK;
    return (int32_t)(jr_seed >> (48 - bits));
}

static int32_t jr_next_int(int32_t bound) {
    int32_t r = jr_next(31);
    int32_t m = bound - 1;
    if ((bound & m) == 0) {
        return (int32_t)(((int64_t)bound * (int64_t)r) >> 31);
    }
    for (int32_t u = r;; u = jr_next(31)) {
        r = u % bound;
        if ((int32_t)((uint32_t)u - (uint32_t)r + (uint32_t)m) >= 0) break;
    }
    return r;
}

void noise_init(long long seed) {
    int p[256];
    for (int i = 0; i < 256; i++) p[i] = i;
    jr_set((int64_t)seed);
    for (int i = 255; i >= 1; i--) {
        int j = jr_next_int(i + 1);
        int t = p[i];
        p[i] = p[j];
        p[j] = t;
    }
    for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
}

static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
static double lerp(double a, double b, double t) { return a + t * (b - a); }

static double grad(int h, double x, double y) {
    switch (h & 7) {
        case 0: return x + y;
        case 1: return -x + y;
        case 2: return x - y;
        case 3: return -x - y;
        case 4: return x;
        case 5: return -x;
        case 6: return y;
        default: return -y;
    }
}

double noise_perlin(double x, double y) {
    double fx = floor(x);
    double fy = floor(y);
    int xi = (int)fx & 255;
    int yi = (int)fy & 255;
    double xf = x - fx;
    double yf = y - fy;
    double u = fade(xf);
    double v = fade(yf);
    int aa = perm[perm[xi] + yi];
    int ab = perm[perm[xi] + yi + 1];
    int ba = perm[perm[xi + 1] + yi];
    int bb = perm[perm[xi + 1] + yi + 1];
    double x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
    double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
    return lerp(x1, x2, v);
}

double noise_fbm(double x, double y, int octaves) {
    double amp = 1.0, freq = 1.0, sum = 0.0, norm = 0.0;
    for (int i = 0; i < octaves; i++) {
        sum += noise_perlin(x * freq, y * freq) * amp;
        norm += amp;
        amp *= 0.5;
        freq *= 2.0;
    }
    return sum / norm;
}
