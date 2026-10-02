cmake_minimum_required(VERSION 3.18.1)
project(voxelnative C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# -ffp-contract=off keeps the floating point results identical to the Kotlin fallback code.
add_compile_options(-O2 -ffp-contract=off -fvisibility=hidden -Wall -Wextra)

add_library(voxelnative SHARED
    native-lib.cpp   # JNI glue (C++)
    terrain.cpp      # terrain generation (C++)
    mesher.cpp       # chunk mesh builder (C++)
    noise.c          # Perlin noise (plain C)
)

find_library(log-lib log)
target_link_libraries(voxelnative ${log-lib})

# Android 15+ devices may use 16 KB memory pages.
if(ANDROID)
    target_link_options(voxelnative PRIVATE -Wl,-z,max-page-size=16384)
endif()
