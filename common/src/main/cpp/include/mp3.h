#pragma once

#include "AudioSource.h"
#include "mp32pcm.h"
#include <cmath>
#include <fstream>
#include <cstdint>
/*
#define BUFSIZE (4 * MP3_MIN_BUFFER)
namespace equalizer {

    class Mp3 : public AudioSource  {
    public:
        explicit Mp3();

        int16_t getSample() override ;
        void onPlaybackStopped() override ;

        int32_t getSampleRate() const;
        uint16_t getChannelCount() const;

        bool load(const std::string);

    private:
        bool channel= false;

        static int16_t twoBytesToInt (const uint8_t source[2], int );
        std::ifstream inputFile;
        uint16_t numChannels=2;
        int32_t sampleRate=0;
        mp3_options myoptions;
        mp3_sample buffer[BUFSIZE];
        mp3_info myinfo;
        int id;
        int buffersize;
        int bufferpointer;
    };

}*/