
#include <jni.h>
#include <iostream>
#include "include/Log.h"
#include "include/Equalizer.h"

#define JUCE_CORE_INCLUDE_JNI_HELPERS 1
#include <juce_core/juce_core.h>
#include <juce_core/native/juce_JNIHelpers_android.h>

#include <fstream>

JavaVM* g_JavaVM = nullptr;

std::string jstringToString(JNIEnv* env, jstring jStr) {
    if (!jStr) return "";
    const char* chars = env->GetStringUTFChars(jStr, nullptr);
    std::string str(chars);
    env->ReleaseStringUTFChars(jStr, chars);
    return str;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeInit(JNIEnv *env, jobject thiz, jobject context) {
    LOGD("nativeInit called");
    env->GetJavaVM(&g_JavaVM);
    juce::JNIClassBase::initialiseAllClasses(env, context);
}

JNIEXPORT jlong JNICALL
Java_com_equalizer_common_Equalizer_nativeCreate(JNIEnv *env, jobject thiz) {
    auto equalizer = std::make_unique<equalizer::Equalizer>();
    if (!equalizer) {
        LOGD("FAILED TO CREATE THE EQUALIZER");
    } else {
        LOGD("Equalizer created");
    }
    return reinterpret_cast<jlong>(equalizer.release());
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeDelete(JNIEnv *env, jobject thiz, jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) delete equalizer;
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeStop(JNIEnv *env, jobject thiz, jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) equalizer->stop();
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeSetVolumenLow(JNIEnv *env, jobject thiz, jlong equalizer_handle, jfloat volume_in_db, jint freqInterval) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) equalizer->setVolumenLow(static_cast<float>(volume_in_db), static_cast<int>(freqInterval));
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativePlayWithVol(JNIEnv *env, jobject thiz, jlong synthesizer_handle, jstring jFileName, jfloatArray vol, jint deviceId, jint sampleRate, jint channels) {
    std::string fileName = jstringToString(env, jFileName);
    jfloat *elements = env->GetFloatArrayElements(vol, nullptr);
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(synthesizer_handle);
    if (equalizer) {
        int bands = env->GetArrayLength(vol);
        for (int i = 0; i < std::min(bands, 16); i++) {
            equalizer->setVolumenLow(elements[i], i);
        }
        equalizer->play(fileName, deviceId, sampleRate, channels);
    }
    if (elements) env->ReleaseFloatArrayElements(vol, elements, JNI_ABORT);
}

JNIEXPORT jboolean JNICALL
Java_com_equalizer_common_Equalizer_nativeIsPlaying(JNIEnv *env, jobject thiz, jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    return equalizer != nullptr && equalizer->isPlaying();
}

JNIEXPORT jdouble JNICALL
Java_com_equalizer_common_Equalizer_nativeGetDuration(JNIEnv *env, jobject thiz, jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    return equalizer ? equalizer->getDuration() : 0.0;
}

JNIEXPORT jdouble JNICALL
Java_com_equalizer_common_Equalizer_nativeGetCurrentPosition(JNIEnv *env, jobject thiz, jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    return equalizer ? equalizer->getCurrentPosition() : 0.0;
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeSeekTo(JNIEnv *env, jobject thiz, jlong equalizer_handle, jdouble position_seconds) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) equalizer->seekTo(position_seconds);
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeSetDelay(JNIEnv *env, jobject thiz, jlong equalizer_handle, jfloat left_delay, jfloat right_delay) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) equalizer->setDelay(left_delay, right_delay);
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeSetReverbParams(JNIEnv *env, jobject thiz, jlong equalizer_handle, jboolean enabled, jfloat balance, jfloat r, jfloat g) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) equalizer->setReverbParams(enabled == JNI_TRUE, balance, r, g);
}

JNIEXPORT jfloatArray JNICALL
Java_com_equalizer_common_Equalizer_nativeGetFilterDesign(JNIEnv *env, jobject thiz, jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (!equalizer) return nullptr;
    auto design = equalizer->getFilterDesign();
    if (design.empty()) return nullptr;
    int numBands = (int)design.size();
    int totalElements = 1 + (numBands * 2);
    for (const auto& band : design) {
        totalElements += (int)(band.poles.size() + band.zeros.size()) * 2;
    }
    jfloatArray result = env->NewFloatArray(totalElements);
    auto* fill = new jfloat[totalElements];
    int idx = 0;
    fill[idx++] = (float)numBands;
    for (const auto& band : design) {
        fill[idx++] = (float)band.poles.size();
        fill[idx++] = (float)band.zeros.size();
    }
    for (const auto& band : design) {
        for (const auto& p : band.poles) { fill[idx++] = (float)p.real(); fill[idx++] = (float)p.imag(); }
        for (const auto& z : band.zeros) { fill[idx++] = (float)z.real(); fill[idx++] = (float)z.imag(); }
    }
    env->SetFloatArrayRegion(result, 0, totalElements, fill);
    delete[] fill;
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_equalizer_common_Equalizer_nativeGetAnalysisResponse(JNIEnv *env, jobject thiz, jlong equalizer_handle, jdouble start, jdouble end, jdouble step) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (!equalizer) return nullptr;
    auto res = equalizer->getAnalysisResponse(start, end, step);
    jfloatArray result = env->NewFloatArray((jsize)res.size());
    auto* fill = new jfloat[res.size()];
    for (size_t i = 0; i < res.size(); ++i) fill[i] = (jfloat)res[i];
    env->SetFloatArrayRegion(result, 0, (jsize)res.size(), fill);
    delete[] fill;
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_equalizer_common_Equalizer_nativeGetUnoptimizedAnalysisResponse(JNIEnv *env, jobject thiz, jlong equalizer_handle, jdouble start, jdouble end, jdouble step) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (!equalizer) return nullptr;
    auto res = equalizer->getUnoptimizedAnalysisResponse(start, end, step);
    jfloatArray result = env->NewFloatArray((jsize)res.size());
    auto* fill = new jfloat[res.size()];
    for (size_t i = 0; i < res.size(); ++i) fill[i] = (jfloat)res[i];
    env->SetFloatArrayRegion(result, 0, (jsize)res.size(), fill);
    delete[] fill;
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_equalizer_common_Equalizer_nativeGetDiagnosticSamples(JNIEnv *env, jobject thiz, jlong equalizer_handle, jint count) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (!equalizer) return nullptr;
    jfloatArray result = env->NewFloatArray(count);
    auto* fill = new jfloat[count];
    for (int i = 0; i < count; ++i) fill[i] = equalizer->getNextSample();
    env->SetFloatArrayRegion(result, 0, count, fill);
    delete[] fill;
    return result;
}

JNIEXPORT jint JNICALL
Java_com_equalizer_common_Equalizer_nativeGetAvailableSamples(JNIEnv *env, jobject thiz, jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (!equalizer) return 0;
    return equalizer->getAvailableSamples();
}

}
