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
        [[nodiscard]] bool isPlaying() const;
        [[nodiscard]] double getDuration() const;
        [[nodiscard]] double getCurrentPosition() const;
        void seekTo(double positionSeconds);
        void setVolumenLow(float , int);
        void setDelay(float leftDelay, float rightDelay);

        std::vector<BandDesign> getFilterDesign() const {
            return _filterSource->getFilterDesign();
        }
        std::vector<double> getAnalysisResponse(double start, double end, double step) const {
            return _filterSource->getAnalysisResponse(start, end, step);
        }
        std::vector<double> getUnoptimizedAnalysisResponse(double start, double end, double step) const {
            return _filterSource->getUnoptimizedAnalysisResponse(start, end, step);
        }



    private:
        bool _isPlaying = false;
        std::string _currentFileName;
        std::shared_ptr<Oscillator> _oscillator;
        std::shared_ptr<FilterSource> _filterSource;
        //std::shared_ptr<FilePlayer> _filePlayer;
        std::unique_ptr<AudioPlayer> _audioPlayer;
        std::array<float, 2> delay = {0.f, 0.0f};

    };
}