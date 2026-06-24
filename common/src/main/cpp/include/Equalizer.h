#pragma once

#include <cstdio>
#include <memory>
#include "Oscillator.h"
#include "AudioPlayer.h"
#include "FilePlayer.h"
#include "FilterSource.h"


namespace equalizer {

    class AudioPlayer;

    class Equalizer {
        public:
        Equalizer();
        ~Equalizer();
        void stop();
        void play(const std::string&, int32_t deviceId = 0);
        bool isPlaying() const;
        void setVolumenLow(float , int);


    private:
        bool _isPlaying = false;
        std::shared_ptr<Oscillator> _oscillator;
        std::shared_ptr<FilterSource> _filterSource;
        //std::shared_ptr<FilePlayer> _filePlayer;
        std::unique_ptr<AudioPlayer> _audioPlayer;


    };
}