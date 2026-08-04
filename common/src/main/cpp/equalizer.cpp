#include "Log.h"
#include "Equalizer.h"
#include "OboeAudioPlayer.h"
#include "FilePlayer.h"

namespace equalizer {
    Equalizer::Equalizer() :
            _oscillator{std::make_shared<Oscillator>() },
            _filterSource{std::make_shared<FilterSource>(_oscillator)},
            _audioPlayer{std::make_unique<OboeAudioPlayer>(_filterSource)}  {

    }

    Equalizer::~Equalizer() = default;

    void Equalizer::stop() {
        LOGD("stop called");
        _audioPlayer->stop();
        _isPlaying = false;
    }



    void Equalizer::play(const std::string& fileName, int32_t deviceId, int32_t sampleRateFallback, int32_t channelsFallback) {
        bool isSameFile = (fileName == _currentFileName);
        bool isStream = (fileName.find("http://") == 0 || fileName.find("https://") == 0);

        // 1. Explicitly stop and close the current Oboe stream before doing anything else.
        _audioPlayer->stop();
        _isPlaying = false;

        // 2. Only load if it's a different file.
        if (!isSameFile) {
            const auto loadresult = _oscillator->load(fileName, sampleRateFallback, channelsFallback);
            if (!loadresult && !isStream) {
                LOGD("Could not load local file: %s", fileName.c_str());
                _currentFileName = "";
                return;
            } else if (!loadresult && isStream) {
                LOGD("Could not load stream metadata yet for: %s. Will attempt playback with fallbacks.", fileName.c_str());
            }
            _currentFileName = fileName;
        }

        int32_t samplingRate = _oscillator->getSampleRate();
        uint16_t numChannels = _oscillator->getChannelCount();

        if (samplingRate <= 0) {
            LOGD("Using fallback sample rate: %d", sampleRateFallback);
            samplingRate = sampleRateFallback;
        }
        if (numChannels <= 0) {
            LOGD("Using fallback channels: %d", channelsFallback);
            numChannels = static_cast<uint16_t>(channelsFallback);
        }

        LOGD("Starting playback: sampleRate=%d, channels=%d", samplingRate, numChannels);

        _filterSource->setFilter(samplingRate, numChannels);
        _filterSource->setDelay(
                (int)(this->delay[0]*((float)samplingRate)/1000.0f),
                (int)(this->delay[1]*((float)samplingRate)/1000.0f));

        const auto result = _audioPlayer -> play(samplingRate, numChannels, deviceId);
        if (result == 0) {
            _isPlaying = true;
        } else {
            LOGD("Could not start Oboe playback. Result: %d", result);
        }
    }

    bool Equalizer::isPlaying() const {
        LOGD("isPlaying() called");
        return this->_isPlaying;
    }

    double Equalizer::getDuration() const {
        if (_oscillator) {
            const int32_t samplingRate = _oscillator->getSampleRate();
            if (samplingRate > 0) {
                return (double)_oscillator->getLengthInSamples() / samplingRate;
            }
        }
        return 0.0;
    }

    double Equalizer::getCurrentPosition() const {
        if (_oscillator) {
            const int32_t samplingRate = _oscillator->getSampleRate();
            if (samplingRate > 0) {
                return (double)_oscillator->getCurrentPositionInSamples() / samplingRate;
            }
        }
        return 0.0;
    }

    void Equalizer::seekTo(double positionSeconds) {
        if (_oscillator) {
            _oscillator->seekTo(positionSeconds);
        }
    }

    void Equalizer::setVolumenLow(float volumeInDb, int freqInterval) {
        _filterSource->setAmplitude(volumeInDb, freqInterval);
        //LOGD("VolumenLow set to %f", volumeInDb);
    }

    void Equalizer::setDelay(float leftDelay, float rightDelay) {
        LOGD("Equalizer::setDelay: L=%.2f ms, R=%.2f ms", leftDelay, rightDelay);
        this -> delay = { leftDelay, rightDelay };
        // Implementation will follow in next iteration
    }
}