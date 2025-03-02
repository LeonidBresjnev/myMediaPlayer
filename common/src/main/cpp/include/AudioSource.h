#pragma once

#include <cstdint>
namespace equalizer {
    class AudioSource {
    public:
        virtual ~AudioSource() = default;

        // Return 1 sample of audio to be played back
        virtual int16_t getSample() = 0;

        // A callback invoked when the audio stream is stopped
        virtual void onPlaybackStopped() = 0;
    };
}