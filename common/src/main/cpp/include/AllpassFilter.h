#pragma once

#include <complex>
#include <vector>
#include <memory>
#include "AudioSource.h"
#include "FilterElement.h"

namespace equalizer {

    class AllpassFilter : public AudioSource {
    public:
        AllpassFilter() = default;

        void setup(int numChannels, std::shared_ptr<AudioSource> input);
        void design(std::complex<double> pole);

        float getSample() override { return 0.0f; } // Not used directly in this way
        float getSample(int channel);
        float process(float input, int channel);
        void onPlaybackStopped() override {}

        std::complex<double> getGain(std::complex<double> z) const;

        static std::complex<double> calculateGain(std::complex<double> z, std::complex<double> pole) {
            std::complex<double> p1 = pole;
            std::complex<double> p2 = std::conj(pole);
            std::complex<double> numerator = (1.0 - std::conj(p1) * z) * (1.0 - std::conj(p2) * z);
            std::complex<double> denominator = (z - p1) * (z - p2);
            return numerator / denominator;
        }

    private:
        std::complex<double> p = std::complex<double>(0.0, 0.0);
        int numChannels = 0;

        std::shared_ptr<AudioSource> input;
        std::vector<std::unique_ptr<FilterElement>> elements;
    };

}
