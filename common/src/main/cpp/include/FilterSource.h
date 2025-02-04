#pragma once

#include <cmath>
#include "AudioSource.h"
#include <atomic>

#include "FilterElement.h"
#include <complex>
using Complex = std::complex<double>;
namespace equalizer {

    class Duplicator: public AudioSource {
    public:
        int16_t getSample() override;
        void setSample(int16_t) ;
        void onPlaybackStopped() override;
    private:
        int16_t currentSample=0;
    };


    class FilterSource : public AudioSource {
    public:

        explicit FilterSource(std::shared_ptr<AudioSource> source);
        int16_t getSample() override;
        void onPlaybackStopped() override;
        void setFilter(int,int);
        virtual void setAmplitude(float newAmplitude, int freqInterval);
        std::shared_ptr<AudioSource> _source;




        constexpr static const std::complex ComplexOne = Complex(1.0, 0.0);
        constexpr static const std::complex MinusComplexOne = Complex(-1.0, 0.0);
    private:
        
        std::shared_ptr<Duplicator> myDuplicator;
        const int freqBorders[7]={125,250,500,1000,2000,4000,8000};
        std::array<std::atomic<float>,8> amplitude= {1.f,1.f,1.f,1.f,1.f,1.f,1.f,1.f};
        static const int order=16;
        FilterElement lowpass[2][order/2];
        FilterElement bandpass[6][2][order];
        FilterElement highpass[2][order/2];
        int numChannels=0;
        int currentChannel=0;
    };


}


