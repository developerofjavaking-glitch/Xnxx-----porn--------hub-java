# VoxelCraft (Kotlin + C/C++)

A small Minecraft-style voxel game for Android. Rendering, input and game logic are Kotlin
(`android.opengl`, OpenGL ES 2.0); the heavy chunk work runs in **C and C++** through JNI.

## Native code (`app/src/main/cpp`)
| File | Language | Job |
|------|----------|-----|
| `noise.c` / `noise.h` | C | Perlin noise + java.util.Random-compatible permutation |
| `terrain.cpp` | C++ | terrain height + block filling for a chunk |
| `mesher.cpp` | C++ | face-culled mesh builder (6 floats per vertex) |
| `native-lib.cpp` | C++ | JNI glue (`NativeLib.kt`) |
| `voxel.h` | C++ | shared constants (must match `Config.kt` / `Blocks.kt`) |

If `libvoxelnative.so` cannot be loaded, the game automatically falls back to the Kotlin versions
(`Terrain.kt`, `Mesher.buildKotlin`). The HUD shows `engine: C/C++` or `engine: Kotlin`.
The C/C++ and Kotlin versions produce identical terrain and meshes.

## Features
- Infinite procedural terrain, chunks of 16x16x64, generated/meshed on worker threads and unloaded again
  when you walk away (edited chunks stay in memory)
- Hidden-face culling, frustum culling, distance fog, camera-relative rendering
- Touch controls: floating joystick (left), drag to look (right), JUMP / DOWN / BREAK / PLACE / FLY / BLOCK
- Walking physics, auto step-up, fly mode, voxel raycast for breaking/placing blocks
- Launcher icon (adaptive + legacy) and a title logo shown at start (`res/drawable-nodpi/logo.png`)

## Build
- Android Studio: open the folder, install **NDK 26.1.10909125** and **CMake 3.22.1** from SDK Manager
  (Studio offers this automatically), then run `app`.
- GitHub: push the folder contents; `.github/workflows/build.yml` installs NDK/CMake and builds a debug APK
  (Actions -> run -> Artifacts).

## Tuning (`Config.kt`)
`RENDER_RADIUS` (default 7), streaming budgets, `SEED`. If you change `CHUNK_SIZE`, `WORLD_HEIGHT` or
`SEA_LEVEL`, change the same constants in `cpp/voxel.h` (the app checks this and falls back to Kotlin if they differ).
