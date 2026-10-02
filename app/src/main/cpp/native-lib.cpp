// JNI glue between Kotlin (NativeLib.kt) and the C/C++ engine code.
#include <jni.h>

#include <cstring>
#include <vector>

#include "noise.h"
#include "voxel.h"

namespace {

// Each worker thread keeps its own scratch buffer: buildMesh() fills it, copyMesh() drains it.
thread_local std::vector<float> t_mesh;

bool readChunk(JNIEnv* env, jbyteArray arr, std::vector<jbyte>& dst) {
    if (arr == nullptr) return false;
    if (env->GetArrayLength(arr) != vc::CHUNK_BYTES) return false;
    dst.resize(vc::CHUNK_BYTES);
    env->GetByteArrayRegion(arr, 0, vc::CHUNK_BYTES, dst.data());
    return true;
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL Java_com_example_voxelcraft_NativeLib_nativeInit(
    JNIEnv* env, jobject, jlong seed, jintArray top, jintArray side, jintArray bottom) {
    noise_init(static_cast<long long>(seed));
    const jsize n = env->GetArrayLength(top);
    std::vector<jint> t(n), s(n), b(n);
    env->GetIntArrayRegion(top, 0, n, t.data());
    env->GetIntArrayRegion(side, 0, n, s.data());
    env->GetIntArrayRegion(bottom, 0, n, b.data());
    vc::setPalette(t.data(), s.data(), b.data(), static_cast<int>(n));
}

JNIEXPORT jint JNICALL Java_com_example_voxelcraft_NativeLib_nativeChunkBytes(JNIEnv*, jobject) {
    return vc::CHUNK_BYTES;
}

JNIEXPORT void JNICALL Java_com_example_voxelcraft_NativeLib_nativeFillChunk(
    JNIEnv* env, jobject, jbyteArray blocks, jint cx, jint cz) {
    if (blocks == nullptr || env->GetArrayLength(blocks) != vc::CHUNK_BYTES) return;
    std::vector<jbyte> buf(vc::CHUNK_BYTES);
    vc::fillChunk(reinterpret_cast<int8_t*>(buf.data()), cx, cz);
    env->SetByteArrayRegion(blocks, 0, vc::CHUNK_BYTES, buf.data());
}

JNIEXPORT jint JNICALL Java_com_example_voxelcraft_NativeLib_nativeBuildMesh(
    JNIEnv* env, jobject, jbyteArray self, jbyteArray xp, jbyteArray xm, jbyteArray zp, jbyteArray zm,
    jint cx, jint cz) {
    std::vector<jbyte> bs, bxp, bxm, bzp, bzm;
    if (!readChunk(env, self, bs)) {
        t_mesh.clear();
        return 0;
    }
    const bool hxp = readChunk(env, xp, bxp);
    const bool hxm = readChunk(env, xm, bxm);
    const bool hzp = readChunk(env, zp, bzp);
    const bool hzm = readChunk(env, zm, bzm);
    return vc::buildMesh(reinterpret_cast<const int8_t*>(bs.data()),
                         hxp ? reinterpret_cast<const int8_t*>(bxp.data()) : nullptr,
                         hxm ? reinterpret_cast<const int8_t*>(bxm.data()) : nullptr,
                         hzp ? reinterpret_cast<const int8_t*>(bzp.data()) : nullptr,
                         hzm ? reinterpret_cast<const int8_t*>(bzm.data()) : nullptr,
                         cx, cz, t_mesh);
}

/* Copies the mesh built by the previous nativeBuildMesh() call on this thread into a direct buffer. */
JNIEXPORT jboolean JNICALL Java_com_example_voxelcraft_NativeLib_nativeCopyMesh(
    JNIEnv* env, jobject, jobject dst, jint vertexCount) {
    void* p = env->GetDirectBufferAddress(dst);
    if (p == nullptr) return JNI_FALSE;
    const jlong cap = env->GetDirectBufferCapacity(dst);
    const size_t floats = static_cast<size_t>(vertexCount) * 6;
    const size_t bytes = floats * sizeof(float);
    if (static_cast<size_t>(cap) < bytes || t_mesh.size() < floats) return JNI_FALSE;
    std::memcpy(p, t_mesh.data(), bytes);
    return JNI_TRUE;
}

}  // extern "C"
