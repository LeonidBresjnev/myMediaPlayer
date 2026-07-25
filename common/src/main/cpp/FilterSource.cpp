#include "FilterSource.h"
#include "Log.h"

namespace equalizer {
    FilterSource::FilterSource(std::shared_ptr<AudioSource> source) :
    myDuplicator{std::make_shared<Duplicator>()},
    _source(std::move(source)){
        bands.resize(8);
        setFilter(44100, 2);
    }

    void FilterSource::setFilter(int sampleRate, int numChannels_) {
        this->numChannels = numChannels_;
        c_const = ChebyshevPrototype::PI * 2.0 / sampleRate;

        const double epsilon = 0.5;
        prototype.calculate(order, sampleRate, epsilon);
        const auto& polesD = prototype.getPolesD();

        // 1. Setup and design LowPass (Band 0)
        bands[0].setup(ChebyshevFilter::Type::LowPass, order, numChannels, myDuplicator);
        bands[0].design(polesD, sampleRate, freqBorders, 0, epsilon);

        // 2. Setup and design BandPass (Bands 1-6)
        for (int i = 0; i < 6; ++i) {
            bands[i + 1].setup(ChebyshevFilter::Type::BandPass, order, numChannels, myDuplicator);
            bands[i + 1].design(polesD, sampleRate, freqBorders, i + 1, epsilon);
        }

        // 3. Setup and design HighPass (Band 7)
        bands[7].setup(ChebyshevFilter::Type::HighPass, order, numChannels, myDuplicator);
        bands[7].design(polesD, sampleRate, freqBorders, 7, epsilon);

        LOGD("Filter Source: Hierarchical design constructed for %d channels at %d Hz", numChannels, sampleRate);
    }

    void FilterSource::setDelay(int leftDelay, int rightDelay) {
        myDelay[0].setSize(min(max(1, leftDelay), 4096 - 1));
        myDelay[1].setSize(min(max(1, rightDelay), 4096 - 1));
        LOGD("Filter Source: delay set %d, %d", leftDelay, rightDelay);
    }

    std::complex<double> FilterSource::h(double f) const {
        std::complex<double> z = std::exp(std::complex<double>(0.0, f * c_const));
        std::complex<double> total= static_cast<double>(amplitude[0][0]) * bands[0].h(z);

        for (int i = 0; i < 6; ++i) {
            total += static_cast<double>(amplitude[0][i + 1]) * bands[i + 1].h(z);
        }

        total += static_cast<double>(amplitude[0][7]) * bands[7].h(z);

        return total;
    }

    std::vector<double> FilterSource::getMagnitudeResponse(double f_start, double f_end, double f_step) const {
        std::vector<double> res;
        for (double f = f_start; f <= f_end; f += f_step) {
            res.push_back(std::abs(h(f)));
        }
        return res;
    }

    std::vector<BandDesign> FilterSource::getFilterDesign() const {
        std::vector<BandDesign> designs;
        designs.reserve(8);

        for (int i = 0; i < 8; ++i) {
            BandDesign bd;
            const auto& p = bands[i].getPoles();
            bd.poles.reserve(p.size() * 2);
            for (const auto& pole : p) {
                bd.poles.push_back(pole);
                bd.poles.push_back(std::conj(pole));
            }

            // Add Zeros
            if (i == 0) { // LP
                bd.zeros.emplace_back(-1.0, 0.0);
                bd.zeros.emplace_back(-1.0, 0.0);
            } else if (i == 7) { // HP
                bd.zeros.emplace_back(1.0, 0.0);
                bd.zeros.emplace_back(1.0, 0.0);
            } else { // BP
                bd.zeros.emplace_back(1.0, 0.0);
                bd.zeros.emplace_back(-1.0, 0.0);
            }

            designs.push_back(std::move(bd));
        }
        return designs;
    }

    float FilterSource::getSample() {
        myDuplicator->setSample(_source->getSample());

        float sample = 0.0f;
        for (int i = 0; i < 8; ++i) {
            sample += amplitude[currentChannel][i] * bands[i].getSample(currentChannel);
        }

        currentChannel = (currentChannel + 1) % numChannels;
        myDelay[currentChannel].setSample(sample);
        return myDelay[currentChannel].getSample();
    }

    void FilterSource::onPlaybackStopped() {
        _source->onPlaybackStopped();
    }

    void FilterSource::setAmplitude(float newAmplitude, int freqInterval) {
        if (freqInterval < 8) {
            amplitude[0][freqInterval] = newAmplitude;
        } else if (freqInterval < 16) {
            amplitude[1][freqInterval - 8] = newAmplitude;
        }
    }
/*
    double FilterSource::lossfunction(int pairIndex, std::complex<double> pole) const {
        //lossFunctionCalls++;
        if (pairIndex < 0 || pairIndex >= 7) return 1e18;

        constexpr double c = PI * 2.0 / sampleRate;
        auto ap_current = AllpassFilter(pole);
        double loss = 0.0;

        // Determine frequency range for optimization
        int fStart = 0.8* freqBorders[pairIndex] ;
        int fEnd = 1.2*freqBorders[pairIndex];

        //std::cout<<pairIndex<<" "<<fStart<<" "<<fEnd<<std::endl;
        for (int f = fStart; f < fEnd; f++) {
            std::complex<double> z = exp(std::complex(0.0, c * f));
            std::complex<double> gain =  ap_current.getGain(z)* filters[pairIndex + 1]->getGain(z);
            std::complex<double> combinedGain = filters[pairIndex]->getGain(z) + gain;
            loss += std::pow(std::abs(combinedGain) - 1.0, 2.0);
        }
        //std::cout<<pairIndex<<std::endl;
        return loss;
    }*/

    float Duplicator::getSample() {
        return currentSample;
    }
    void Duplicator::setSample(float sample) {
        this->currentSample = sample;
    }
    void Duplicator::onPlaybackStopped() {}
}
