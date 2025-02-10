//
// Created by simon on 26-01-2025.
//
#include <jni.h>
#include <iostream>
#include "include/Log.h"
#include "include/Equalizer.h"

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
JNIEXPORT jlong JNICALL
Java_com_equalizer_common_Equalizer_nativeCreate(JNIEnv *env, jobject thiz) {

    auto equalizer = std::make_unique<equalizer::Equalizer>();

    if (not equalizer) {
        LOGD("FAILED TO CREATE THE SYNTHESIZER");
        equalizer.reset(nullptr);
    } else {
        LOGD("I am created");
    }

    return reinterpret_cast<jlong>(equalizer.release());
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
    delete equalizer;
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
                                               jstring jFileName) {

    std::string fileName = jstringToString(env, jFileName);

    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(synthesizer_handle);
    if (equalizer) {
        equalizer->play(fileName);
    } else {
        LOGD("synthesizer not created. please first create()");
    }
}

JNIEXPORT void JNICALL
Java_com_equalizer_common_Equalizer_nativePlayWithVol(JNIEnv *env,
                                                      jobject thiz,
                                                      jlong synthesizer_handle,
                                                      jstring jFileName,

                                                      jfloatArray vol) {
    std::string fileName = jstringToString(env, jFileName);

    jint size = env->GetArrayLength(vol);
    jfloat *elements = env->GetFloatArrayElements(vol, nullptr);
/*
    std::array<float, 8> carray;
    for (int i = 0; i < size; ++i) {
        carray[i] = elements[i];
        equalizer->setVolumenLow(elements[i], i);

    }*/


    auto *equalizer = reinterpret_cast<equalizer::Equalizer *>(synthesizer_handle);
    if (equalizer) {
        for (int i = 0; i < 8; i++) {
            equalizer->setVolumenLow(elements[i], i);
        }
        equalizer->play(fileName);
    } else {
        LOGD("synthesizer not created. please first create()");
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
}
