#include "AllpassFilter.h"

namespace equalizer {

    void AllpassFilter::setup(int numChannels, std::shared_ptr<AudioSource> input) {
        this->numChannels = numChannels;
        this->input = input;

        elements.clear();
        elements.reserve(numChannels);
        for (int c = 0; c < numChannels; ++c) {
            elements.push_back(std::make_unique<FilterElement>(input));
        }
    }

    void AllpassFilter::design(std::complex<double> pole) {
        this->p = pole;
        for (int c = 0; c < numChannels; ++c) {
            elements[c]->setAllpass(pole);
        }
    }

    float AllpassFilter::getSample(int channel) {
        if (channel >= 0 && channel < numChannels) {
            return elements[channel]->getSample();
        }
        return 0.0f;
    }

    float AllpassFilter::process(float input, int channel) {
        if (channel >= 0 && channel < numChannels) {
            return elements[channel]->process(input);
        }
        return 0.0f;
    }

    std::complex<double> AllpassFilter::getGain(std::complex<double> z) const {
        // H(z) = ((1 - p1*z)(1 - p2*z)) / ((z - p1)(z - p2)) where p2 = conj(p1)
        std::complex<double> p1 = p;
        std::complex<double> p2 = std::conj(p);

        std::complex<double> numerator = (1.0 - std::conj(p1) * z) * (1.0 - std::conj(p2) * z);
        std::complex<double> denominator = (z - p1) * (z - p2);

        return numerator / denominator;
    }

}
