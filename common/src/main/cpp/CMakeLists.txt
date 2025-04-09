# This file handles building each task as sub-project.
#
# Each task becomes an independent project in which you can experiment.
#
# Tasks are added as separate sub-projects so as compilation errors in
# one task don't affect another task.
cmake_minimum_required(VERSION 3.10)
project("myMediaPlayer")

# set(CMAKE_CXX_STANDARD 20)
message(${CMAKE_PROJECT_NAME})
set(JUCE_DIR vcpkg/packages/juce_arm64-android/share/juce)

set(JUCE_OBOE_INCLUDE_DIR vcpkg/packages/oboe_arm64-android/include)
set(JUCE_OBOE_LIBRARY vcpkg/packages/oboe_arm64-android/lib)
find_package(oboe REQUIRED CONFIG)
find_package(JUCE REQUIRED CONFIG)

# Add tasks subprojects
add_library(${CMAKE_PROJECT_NAME} SHARED
        receiver.cpp
        Oscillator.cpp
        equalizer.cpp
        FilterElement.cpp
        FilterSource.cpp
        OboeAudioPlayer.cpp
)

find_package(oboe REQUIRED CONFIG)
include_directories(include)
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        juce::juce_core
        juce::juce_audio_basics # GUI module
        juce::juce_audio_formats
        android
        log
        oboe::oboe)