#include "ChebyshevFilter.h"
#include <cmath>

namespace equalizer {

    void ChebyshevPrototype::calculate(int order, int sampleRate, double epsilon) {
        double wa = std::tan(mu / 2.0) * 2.0 * sampleRate;

        polesD.clear();
        polesD.reserve(order);

        for (int m = 1; m <= order; ++m) {
            std::complex<double> poleA = std::complex<double>(
                -std::sinh(std::asinh(1.0 / epsilon) / order) * std::sin(PI * (2 * m - 1) / (2.0 * order)),
                std::cosh(std::asinh(1.0 / epsilon) / order) * std::cos(PI * (2 * m - 1) / (2.0 * order))
            ) * wa;

            std::complex<double> pD = -(poleA + 2.0 * sampleRate) / (poleA - 2.0 * sampleRate);
            polesD.push_back(pD);
        }
    }

    void ChebyshevFilter::setup(Type type, int order, int numChannels, std::shared_ptr<AudioSource> input) {
        this->type = type;
        this->order = order;
        this->numChannels = numChannels;

        // 8th order LP/HP -> 8 poles (4 SOS)
        // 8th order Prototype BP -> 16 poles (8 SOS)
        int numSections = (type == Type::BandPass) ? order : (order / 2);

        channelElements.clear();
        channelElements.resize(numChannels);

        for (int c = 0; c < numChannels; ++c) {
            channelElements[c].reserve(numSections);
            for (int i = 0; i < numSections; ++i) {
                if (i == 0) {
                    channelElements[c].push_back(std::make_unique<FilterElement>(input));
                } else {
                    channelElements[c].push_back(std::make_unique<FilterElement>(channelElements[c][i - 1].get()));
                }
            }
        }
    }

    void ChebyshevFilter::design(const std::vector<std::complex<double>>& polesD,
                                double sampleRate,
                                const std::vector<int>& freqBorders,
                                int bandIdx,
                                double epsilon) {
        const double PI = ChebyshevPrototype::PI;
        const double mu = ChebyshevPrototype::mu;

        poles.clear();
        sectionScales.clear();
        totalScale = 1.0;

        if (type == Type::LowPass) {
            double cutoff = PI * 2.0 * static_cast<double>(freqBorders[0]) / sampleRate;
            double alpha = std::sin((mu - cutoff) / 2.0) / std::sin((mu + cutoff) / 2.0);

            for (int i = 0; i < order / 2; ++i) {
                std::complex<double> p = (polesD[i] + alpha) / (polesD[i] * alpha + 1.0);
                auto scale0 = (p + 1.0) * std::tan(mu / 2.0) * (alpha - 1.0) / ((alpha + 1.0) * 4.0) * (std::pow(2.0 / epsilon, 1.0 / order));
                double scale = std::pow(std::abs(scale0), 2.0);

                poles.push_back(p);
                poles.push_back(std::conj(p));
                sectionScales.push_back(scale);
                totalScale *= scale;

                for (int c = 0; c < numChannels; ++c) {
                    channelElements[c][i]->setNumerator(std::complex<double>(-1.0, 0.0), scale);
                    channelElements[c][i]->setDenominator(p);
                }
            }
        } else if (type == Type::BandPass) {
            double cutoffLow = (PI * 2.0 * static_cast<double>(freqBorders[bandIdx - 1])) / sampleRate;
            double cutoffHigh = (PI * 2.0 * static_cast<double>(freqBorders[bandIdx])) / sampleRate;

            double alpha = std::cos((cutoffLow + cutoffHigh) / 2.0) / std::cos((cutoffLow - cutoffHigh) / 2.0);
            double k = std::tan(mu / 2.0) / std::tan((cutoffHigh - cutoffLow) / 2.0);

            // In an 8th-order prototype, polesD[0-3] and polesD[4-7] are conjugates.
            // polesD[0]/[7], [1]/[6], [2]/[5], [3]/[4] are pairs.
            // We only need to transform one from each pair (0, 1, 2, 3).
            for (int i = 0; i < 4; ++i) {
                std::complex<double> pd = polesD[i];
                std::complex<double> a = (pd + 1.0) * alpha * k;
                std::complex<double> b = (pd * (k + 1.0) + (k - 1.0)) * (pd * (k - 1.0) + k + 1.0);
                std::complex<double> d = std::pow(a * a - b, 0.5);

                std::complex<double> p1 = ( -d + a ) / (pd * (k - 1.0) + k + 1.0);
                std::complex<double> p2 = ( d + a) / (pd * (k - 1.0) + k + 1.0);

                double scale = std::abs(((pd + 1.0) * std::tan(mu / 2.0) / ((pd * (k - 1.0) + k + 1.0) * 2.0)) * (std::pow(2.0 / epsilon, 1.0 / order)));

                // Each root from the transform needs its own SOS paired with its own conjugate
                // to ensure real-valued coefficients.
                poles.push_back(p1);
                poles.push_back(std::conj(p1));
                poles.push_back(p2);
                poles.push_back(std::conj(p2));

                totalScale *= (scale * scale);

                for (int c = 0; c < numChannels; ++c) {
                    // SOS for p1
                    channelElements[c][i * 2]->setNumerator(std::array<float, 3>{1.f, 0.f, -1.f}, scale);
                    channelElements[c][i * 2]->setDenominator(p1);
                    // SOS for p2
                    channelElements[c][i * 2 + 1]->setNumerator(std::array<float, 3>{1.f, 0.f, -1.f}, scale);
                    channelElements[c][i * 2 + 1]->setDenominator(p2);
                }
            }
        } else if (type == Type::HighPass) {
            double cutoff = PI * 2.0 * static_cast<double>(freqBorders[6]) / sampleRate;
            double alpha = -std::cos((mu + cutoff) / 2.0) / std::cos((mu - cutoff) / 2.0);

            for (int i = 0; i < order / 2; ++i) {
                std::complex<double> p = -(polesD[i] + alpha) / (polesD[i] * alpha + 1.0);
                double scale = std::pow(std::abs((-p + 1.0) * std::tan(mu / 2.0) * (alpha - 1.0) / ((alpha + 1.0) * 4.0) * (std::pow(2.0 / epsilon, 1.0 / order))), 2.0);

                poles.push_back(p);
                poles.push_back(std::conj(p));
                sectionScales.push_back(scale);
                totalScale *= scale;

                for (int c = 0; c < numChannels; ++c) {
                    channelElements[c][i]->setNumerator(std::complex<double>(1.0, 0.0), scale);
                    channelElements[c][i]->setDenominator(p);
                }
            }
        }
    }

    void ChebyshevFilter::reset() {
        poles.clear();
        sectionScales.clear();
    }

    float ChebyshevFilter::getSample(int channel) {
        if (channel >= 0 && channel < numChannels && !channelElements[channel].empty()) {
            return channelElements[channel].back()->getSample();
        }
        return 0.0f;
    }

    float ChebyshevFilter::process(float input, int channel) {
        if (channel >= 0 && channel < numChannels && !channelElements[channel].empty()) {
            float s = input;
            for (auto& element : channelElements[channel]) {
                s = element->process(s);
            }
            return s;
        }
        return 0.0f;
    }

    std::complex<double> ChebyshevFilter::h(const std::complex<double>& z) const {
        std::complex<double> res(totalScale, 0.0);

        std::complex<double> num;
        if (type == Type::LowPass) num = (z + 1.0) * (z + 1.0);
        else if (type == Type::BandPass) num = (z + 1.0) * (z - 1.0);
        else num = (z - 1.0) * (z - 1.0);

        for (size_t i = 0; i < poles.size(); i += 2) {
            std::complex<double> den = (z - poles[i]) * (z - poles[i+1]);
            res *= (num / den);
        }

        return res;
    }

}
