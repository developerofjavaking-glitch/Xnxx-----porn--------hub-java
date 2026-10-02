#ifndef VOXEL_NOISE_H
#define VOXEL_NOISE_H

#ifdef __cplusplus
extern "C" {
#endif

/* Builds the permutation table. Uses the java.util.Random algorithm so the result is identical to the
 * Kotlin implementation (Noise.kt) for the same seed. Call once before any other function. */
void noise_init(long long seed);

double noise_perlin(double x, double y);
double noise_fbm(double x, double y, int octaves);

#ifdef __cplusplus
}
#endif

#endif
