//
// Created by simon on 26-01-2025.
//
#include <jni.h>
#include <iostream>
#include "include/Log.h"
#include "include/Equalizer.h"

#define JUCE_CORE_INCLUDE_JNI_HELPERS 1
#include <juce_core/juce_core.h>
#include <juce_core/native/juce_JNIHelpers_android.h>

#include <fstream>

std::string jstringToString(JNIEnv* env, jstring jStr) {
    if (!jStr) {
        return "";
    }

    const char* chars = env->GetStringUTFChars(jStr, nullptr);
    std::string str(chars);
    env->ReleaseStringUTFChars(jStr, chars);

    return str;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeInit(JNIEnv *env, jobject thiz, jobject context) {
    LOGD("nativeInit called");
    juce::JNIClassBase::initialiseAllClasses(env, context);
    juce::Thread::initialiseJUCE(env, context);
}

JNIEXPORT jlong JNICALL
Java_com_equalizer_common_Equalizer_nativeCreate(JNIEnv *env, jobject thiz) {

    auto equalizer = std::make_unique<equalizer::Equalizer>();

    if (not equalizer) {
        LOGD("FAILED TO CREATE THE SYNTHESIZER");
        equalizer.reset(nullptr);
    } else {
        LOGD("I am created");
    }
    jlong x = reinterpret_cast<jlong>(equalizer.release());

    return x;
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeDelete(JNIEnv *env, jobject thiz,
                                                 jlong equalizer_handle) {
    auto *equalizer =
            reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (not equalizer) {
        LOGD("Attemp to destroy an uninitialized synthesizer");
        return;
    }
    delete equalizer;
}
JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeStop(JNIEnv *env, jobject thiz,
                                               jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) {
        equalizer->stop();
    } else {
        LOGD("Synthesize not create");
    }
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeSetVolumenLow(JNIEnv *env, jobject thiz,
                                                        jlong equalizer_handle,
                                                        jfloat volume_in_db,
                                                        jint freqInterval) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) {
        equalizer->setVolumenLow(static_cast<float>(volume_in_db), static_cast<int>(freqInterval));
    } else {
        LOGD("Synthesize not create");
    }
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativePlay(JNIEnv *env,
                                               jobject thiz,
                                               jlong synthesizer_handle,
                                               jstring jFileName,
                                               jint deviceId) {

    std::string fileName = jstringToString(env, jFileName);

    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(synthesizer_handle);
    if (equalizer) {
        equalizer->play(fileName, deviceId);
    } else {
        LOGD("synthesizer not created. please first create()");
    }
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativePlayWithVol(JNIEnv *env,
                                                      jobject thiz,
                                                      jlong synthesizer_handle,
                                                      jstring jFileName,
                                                      jfloatArray vol,
                                                      jint deviceId) {
    std::string fileName = jstringToString(env, jFileName);

    jint size = env->GetArrayLength(vol);
    jfloat *elements = env->GetFloatArrayElements(vol, nullptr);

    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(synthesizer_handle);
    if (equalizer) {
        int bands = (size < 8) ? size : 8;
        for (int i = 0; i < bands; i++) {
            equalizer->setVolumenLow(elements[i], i);
        }
        equalizer->play(fileName, deviceId);
    } else {
        LOGD("synthesizer not created. please first create()");
    }

    if (elements) {
        env->ReleaseFloatArrayElements(vol, elements, JNI_ABORT);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_equalizer_common_Equalizer_nativeIsPlaying(JNIEnv *env, jobject thiz,
                                                    jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) {
        LOGD("isPlaying = %d", equalizer->isPlaying());
        return equalizer->isPlaying();
    } else {
        LOGD("Synthesize not create");
    }
    return false;
}

JNIEXPORT jdouble JNICALL
Java_com_equalizer_common_Equalizer_nativeGetDuration(JNIEnv *env, jobject thiz,
                                                      jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) {
        return equalizer->getDuration();
    }
    return 0.0;
}

JNIEXPORT jdouble JNICALL
Java_com_equalizer_common_Equalizer_nativeGetCurrentPosition(JNIEnv *env, jobject thiz,
                                                             jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) {
        return equalizer->getCurrentPosition();
    }
    return 0.0;
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeSeekTo(JNIEnv *env, jobject thiz,
                                                 jlong equalizer_handle,
                                                 jdouble position_seconds) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) {
        equalizer->seekTo(position_seconds);
    }
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativeSetDelay(JNIEnv *env, jobject thiz,
                                                  jlong equalizer_handle,
                                                  jfloat left_delay,
                                                  jfloat right_delay) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (equalizer) {
        equalizer->setDelay(static_cast<float>(left_delay), static_cast<float>(right_delay));
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_equalizer_common_Equalizer_nativeGetFilterDesign(JNIEnv *env, jobject thiz, jlong equalizer_handle) {
    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(equalizer_handle);
    if (!equalizer) return nullptr;

    auto design = equalizer->getFilterDesign();
    if (design.empty()) return nullptr;

    int numBands = 8;
    int totalElements = 1 + (numBands * 2);
    for (const auto& band : design) {
        totalElements += (int)band.poles.size() * 2;
        totalElements += (int)band.zeros.size() * 2;
    }

    jfloatArray result = env->NewFloatArray(totalElements);
    jfloat* fill = new jfloat[totalElements];

    int idx = 0;
    fill[idx++] = (float)numBands;
    for (const auto& band : design) {
        fill[idx++] = (float)band.poles.size();
        fill[idx++] = (float)band.zeros.size();
    }

    for (const auto& band : design) {
        for (const auto& p : band.poles) {
            fill[idx++] = (float)p.real();
            fill[idx++] = (float)p.imag();
        }
        for (const auto& z : band.zeros) {
            fill[idx++] = (float)z.real();
            fill[idx++] = (float)z.imag();
        }
    }

    env->SetFloatArrayRegion(result, 0, totalElements, fill);
    delete[] fill;
    return result;
}
}
