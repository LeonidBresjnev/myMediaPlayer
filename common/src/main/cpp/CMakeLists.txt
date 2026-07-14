# This file handles building each task as sub-project.
#
# Each task becomes an independent project in which you can experiment.
#
# Tasks are added as separate sub-projects so as compilation errors in
# one task don't affect another task.
cmake_minimum_required(VERSION 3.10)
project("myMediaPlayer")

# Use find_package to locate dependencies.
# If building with vcpkg, the toolchain will set the search paths automatically.
find_package(oboe REQUIRED CONFIG)
find_package(JUCE REQUIRED CONFIG)

# Enable MP3 support in JUCE modules
add_compile_definitions(JUCE_USE_MP3AUDIOFORMAT=1)

message(STATUS "Project: ${CMAKE_PROJECT_NAME}")
message(STATUS "JUCE found: ${JUCE_FOUND}")

# Add tasks subprojects
add_library(${CMAKE_PROJECT_NAME} SHARED
        receiver.cpp
        Oscillator.cpp
        equalizer.cpp
        Delayfilter.cpp
        FilterElement.cpp
        FilterSource.cpp
        OboeAudioPlayer.cpp
)

include_directories(include)

target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        juce::juce_core
        juce::juce_audio_basics
        juce::juce_audio_formats
        android
        log
        oboe::oboe
)
