#pragma once

#include <cmath>
#include "AudioSource.h"
#include <atomic>
#include <vector>
#include <array>
#include <memory>
#include <complex>

#include "FilterElement.h"
#include "ChebyshevFilter.h"
#include "AllpassFilter.h"

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

    struct BandDesign {
        std::vector<Complex> poles;
        std::vector<Complex> zeros;
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

        std::vector<BandDesign> getFilterDesign() const;
        std::vector<double> getAnalysisResponse(double f_start, double f_end, double f_step) const;
        std::vector<double> getUnoptimizedAnalysisResponse(double f_start, double f_end, double f_step) const;

        constexpr static const std::complex ComplexOne = Complex(1.0, 0.0);
        constexpr static const std::complex MinusComplexOne = Complex(-1.0, 0.0);

        double lossfunction(int pairIndex, std::complex<double> pole) const;
        void optimizeAllpassFilters();
    private:

        std::shared_ptr<Duplicator> myDuplicator;
        Delayfilter myDelay[2];
        const std::vector<int> freqBorders = {125, 250, 500, 1000, 2000, 4000, 8000};
        float amplitude[2][8] = {
            {1.f,1.f,1.f,1.f,1.f,1.f,1.f,1.f}, // Left
            {1.f,1.f,1.f,1.f,1.f,1.f,1.f,1.f}  // Right
        };
        static const int order=8;

        ChebyshevPrototype prototype;
        std::vector<ChebyshevFilter> bands;
        std::vector<std::shared_ptr<AllpassFilter>> allpassChains;

        std::complex<double> h(double f) const;
        std::complex<double> hUnoptimized(double f) const;

        double c_const = 0.0;
        int numChannels = 0;
        int currentChannel = 0;
        int lastSampleRate = 0;
        int lastNumChannels = 0;
        mutable int lossFunctionCalls = 0;

        static int max(int a, int b) {
            return a>b?a:b;
        }
        static int min(int a,int b) {
            return a<b?a:b;
        }
    };


}
