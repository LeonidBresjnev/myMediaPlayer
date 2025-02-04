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

    void Equalizer::play(std::string fileName) {

        _oscillator->load(fileName);

        _isPlaying = true;
        LOGD("play called");


        int32_t samplingRate = _oscillator->getSampleRate();
        int32_t numChannels = _oscillator->getChannelCount();
        LOGD("sampleRate=%d", samplingRate);
        _oscillator->setSamplingRate(samplingRate);
        _filterSource->setFilter(samplingRate,numChannels);
        /*for (int i=0; i<200; i++) {
            LOGD("sample :%d", _filterSource->getSample());
        }*/
        const auto result = _audioPlayer -> play(samplingRate, numChannels);
        if (result == 0) {
            _isPlaying = true;
        } else {
            LOGD("Could not start playback.");
        }
    }

    bool Equalizer::isPlaying() const {
        LOGD("isPlaying() called");
        return this->_isPlaying;
    }

    void Equalizer::setVolumenLow(float volumeInDb, int freqInterval) {
        _filterSource->setAmplitude(volumeInDb, freqInterval);
        //LOGD("VolumenLow set to %f", volumeInDb);
    }
}