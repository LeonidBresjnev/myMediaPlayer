#pragma once


#include <cstdint>

namespace equalizer {
    class AudioPlayer {
    public:
        virtual ~AudioPlayer() = default;

        // Start the audio device

        virtual int32_t play(int32_t,uint16_t, int32_t deviceId = 0) = 0;

        // Stop the audio device
        virtual void stop() = 0;
    };
}