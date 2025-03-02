# This file handles building each task as sub-project.
#
# Each task becomes an independent project in which you can experiment.
#
# Tasks are added as separate sub-projects so as compilation errors in
# one task don't affect another task.
cmake_minimum_required(VERSION 3.22.1)
project("myMediaPlayer")

# set(CMAKE_CXX_STANDARD 20)


# Add tasks subprojects
add_library(${CMAKE_PROJECT_NAME} SHARED
        receiver.cpp
        Oscillator.cpp
        equalizer.cpp
        filePlayer.cpp
        FilterElement.cpp
        FilterSource.cpp
        OboeAudioPlayer.cpp
        mp3/perform.cpp
        mp3/mp32pcm.cpp
        mp3.cpp

)

find_package(oboe REQUIRED CONFIG)
include_directories(include)
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log
        oboe::oboe)