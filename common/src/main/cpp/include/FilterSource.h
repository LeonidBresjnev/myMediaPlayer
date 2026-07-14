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
        float getSample() override;
        void setSample(float) ;
        void onPlaybackStopped() override;
    private:
        float currentSample=0;
    };

    class Delayfilter: public AudioSource {
    public:
        Delayfilter();
        float getSample() override;
        void setSample(float);
        void onPlaybackStopped() override;
        void setSize(size_t newSize);

    private:
        float currentSample=0;
        float buffer[4096]{};
        size_t head;
        size_t size;

        void push_back(float item);

    };




    class FilterSource : public AudioSource {
    public:

        explicit FilterSource(std::shared_ptr<AudioSource> source);
        float getSample() override;
        void onPlaybackStopped() override;
        void setFilter(int,int);
        void setDelay(int,int);
        virtual void setAmplitude(float newAmplitude, int freqInterval);
        std::shared_ptr<AudioSource> _source;

        constexpr static const std::complex ComplexOne = Complex(1.0, 0.0);
        constexpr static const std::complex MinusComplexOne = Complex(-1.0, 0.0);
    private:

        std::shared_ptr<Duplicator> myDuplicator;
        Delayfilter myDelay[2];
        const int freqBorders[7]={125,250,500,1000,2000,4000,8000};
        float amplitude[2][8] = {
            {1.f,1.f,1.f,1.f,1.f,1.f,1.f,1.f}, // Left
            {1.f,1.f,1.f,1.f,1.f,1.f,1.f,1.f}  // Right
        };
        static const int order=8;
        FilterElement lowpass[2][order/2];
        FilterElement bandpass[6][2][order];
        FilterElement highpass[2][order/2];
        int numChannels=0;
        int currentChannel=0;


        static int max(int a, int b) {
            return a>b?a:b;
        }
        static int min(int a,int b) {
            return a<b?a:b;
        }
    };

}


