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
        scaleFactors.clear();

        if (type == Type::LowPass) {
            double cutoff = PI * 2.0 * static_cast<double>(freqBorders[0]) / sampleRate;
            double alpha = std::sin((mu - cutoff) / 2.0) / std::sin((mu + cutoff) / 2.0);

            for (int i = 0; i < order / 2; ++i) {
                std::complex<double> pole = (polesD[i] + alpha) / (polesD[i] * alpha + 1.0);
                auto scale0 = (pole + 1.0) * std::tan(mu / 2.0) * (alpha - 1.0) / ((alpha + 1.0) * 4.0) * (std::pow(2.0 / epsilon, 1.0 / order));
                double scale = std::pow(std::abs(scale0), 2.0);

                poles.push_back(pole);
                scaleFactors.push_back(scale);

                for (int c = 0; c < numChannels; ++c) {
                    channelElements[c][i]->setNumerator(std::complex<double>(-1.0, 0.0), scale);
                    channelElements[c][i]->setDenominator(pole);
                }
            }
        } else if (type == Type::BandPass) {
            // bandIdx 1-6 corresponds to freqBorders indices (bandIdx-1) and bandIdx
            double cutoffLow = (PI * 2.0 * static_cast<double>(freqBorders[bandIdx - 1])) / sampleRate;
            double cutoffHigh = (PI * 2.0 * static_cast<double>(freqBorders[bandIdx])) / sampleRate;

            double alpha = std::cos((cutoffLow + cutoffHigh) / 2.0) / std::cos((cutoffLow - cutoffHigh) / 2.0);
            double k = std::tan(mu / 2.0) / std::tan((cutoffHigh - cutoffLow) / 2.0);

            for (int i = 0; i < order; ++i) {
                std::complex<double> a = (polesD[i] + 1.0) * alpha * k;
                std::complex<double> b = (polesD[i] * (k + 1.0) + (k - 1.0)) * (polesD[i] * (k - 1.0) + k + 1.0);
                std::complex<double> d = std::pow(a * a - b, 0.5);
                std::complex<double> pole = ((i < order / 2) ? -d + a : d + a) / (polesD[i] * (k - 1.0) + k + 1.0);

                double scale = std::abs(((polesD[i] + 1.0) * std::tan(mu / 2.0) / ((polesD[i] * (k - 1.0) + k + 1.0) * 2.0)) * (std::pow(2.0 / epsilon, 1.0 / order)));

                poles.push_back(pole);
                scaleFactors.push_back(scale);

                for (int c = 0; c < numChannels; ++c) {
                    channelElements[c][i]->setNumerator(std::array<float, 3>{1.f, 0.f, -1.f}, scale);
                    channelElements[c][i]->setDenominator(pole);
                }
            }
        } else if (type == Type::HighPass) {
            double cutoff = PI * 2.0 * static_cast<double>(freqBorders[6]) / sampleRate;
            double alpha = -std::cos((mu + cutoff) / 2.0) / std::cos((mu - cutoff) / 2.0);

            for (int i = 0; i < order / 2; ++i) {
                std::complex<double> pole = -(polesD[i] + alpha) / (polesD[i] * alpha + 1.0);
                double scale = std::pow(std::abs((-pole + 1.0) * std::tan(mu / 2.0) * (alpha - 1.0) / ((alpha + 1.0) * 4.0) * (std::pow(2.0 / epsilon, 1.0 / order))), 2.0);

                poles.push_back(pole);
                scaleFactors.push_back(scale);

                for (int c = 0; c < numChannels; ++c) {
                    channelElements[c][i]->setNumerator(std::complex<double>(1.0, 0.0), scale);
                    channelElements[c][i]->setDenominator(pole);
                }
            }
        }
    }

    void ChebyshevFilter::reset() {
        // We don't necessarily want to clear elements here if numChannels hasn't changed.
        // But we should reset their internal state if needed (CircularBuffers).
        // Actually, the current FilterElement doesn't have a reset method.
        // For now, let's just clear the design parameters.
        poles.clear();
        scaleFactors.clear();
    }

    float ChebyshevFilter::getSample(int channel) {
        if (channel >= 0 && channel < numChannels && !channelElements[channel].empty()) {
            return channelElements[channel].back()->getSample();
        }
        return 0.0f;
    }

    std::complex<double> ChebyshevFilter::h(const std::complex<double>& z) const {
        std::complex<double> res(1.0, 0.0);

        for (size_t i = 0; i < poles.size(); ++i) {
            std::complex<double> num;
            std::complex<double> den;

            switch (type) {
                case Type::LowPass:
                    num = (z + 1.0) * (z + 1.0);
                    den = (z - poles[i]) * (z - std::conj(poles[i]));
                    break;

                case Type::BandPass:
                    num = (z * z - 1.0);
                    den = (z - poles[i]) * (z - std::conj(poles[i]));
                    break;

                case Type::HighPass:
                    num = (z - 1.0) * (z - 1.0);
                    den = (z - poles[i]) * (z - std::conj(poles[i]));
                    break;
            }

            res *= (scaleFactors[i] * num / den);
        }

        return res;
    }

}
