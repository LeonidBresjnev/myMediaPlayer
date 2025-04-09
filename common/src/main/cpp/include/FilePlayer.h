#pragma once

#include <cstdio>
#include <fstream>
#include "AudioSource.h"

namespace equalizer {
   /* class FilePlayer: public AudioSource {
    public:
        explicit FilePlayer();
        float getSample() override;
        //int16_t getSample(bool) ;
        void onPlaybackStopped() override;

        int32_t getSampleRate() const;
        uint16_t getChannelCount() const;

        bool load(const std::string );
    private:

        static int32_t fourBytesToInt (const uint8_t source[4], int startIndex );
        static int16_t twoBytesToInt (const uint8_t source[2], int );
        std::ifstream inputFile; // Open the file in binary mode
        int32_t sampleRate=0;
        uint16_t numChannels=0;
        int32_t currentSample=0;

    };*/
}