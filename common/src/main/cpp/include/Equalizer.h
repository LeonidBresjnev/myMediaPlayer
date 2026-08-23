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
        void play(const std::string&, int32_t deviceId = 0, int32_t sampleRateFallback = 44100, int32_t channelsFallback = 2);
        [[nodiscard]] bool isPlaying() const;
        [[nodiscard]] double getDuration() const;
        [[nodiscard]] double getCurrentPosition() const;
        void seekTo(double positionSeconds);
        void setVolumenLow(float , int);
        void setDelay(float leftDelay, float rightDelay);
        void setReverbParams(bool enabled, float balance, float r, float g);
        float getNextSample() { return _filterSource->getSample(); }
        int getAvailableSamples() { return _oscillator->getAvailableSamples(); }

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