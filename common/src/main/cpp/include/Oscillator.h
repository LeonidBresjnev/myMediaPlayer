#pragma once

#include <cmath>
#include "AudioSource.h"
#include <atomic>

#include <fstream>
namespace equalizer {
    class Oscillator : public AudioSource {
    public:
        explicit Oscillator();

        int16_t getSample() override;
        void onPlaybackStopped() override;

        void setSamplingRate(int) ;

        virtual void setAmplitude(float newAmplitude);

        int32_t getSampleRate() const;
        uint16_t getChannelCount() const;

        bool load(std::string );
    private:
        const float pi = 4*atanf(1.f);
        float _phase{1.5f};
        float _phaseIncrement{0.f};
        std::atomic<float> amplitude{1.f};
        bool channel= false;

        static int32_t fourBytesToInt (const uint8_t source[4], int startIndex );
        static int16_t twoBytesToInt (const uint8_t source[2], int );
        std::ifstream inputFile;
        uint16_t numChannels=2;
        int32_t sampleRate=0;
    };

}