#pragma once

#include <complex>
#include <vector>
#include <array>
#include <memory>
#include "FilterElement.h"

namespace equalizer {

    class ChebyshevPrototype {
    public:
        static constexpr double PI = 3.14159265358979323846;
        static constexpr double mu = PI / 8.0;

        ChebyshevPrototype() = default;

        void calculate(int order, int sampleRate, double epsilon);

        const std::vector<std::complex<double>>& getPolesD() const { return polesD; }
        static constexpr double getMu() { return mu; }

    private:
        std::vector<std::complex<double>> polesD;
    };

    class ChebyshevFilter {
    public:
        enum class Type {
            LowPass,
            BandPass,
            HighPass
        };

        ChebyshevFilter() = default;

        // Setup the processing chain
        void setup(Type type, int order, int numChannels, std::shared_ptr<AudioSource> input);

        // Calculate coefficients
        void design(const std::vector<std::complex<double>>& polesD,
                    double sampleRate,
                    const std::vector<int>& freqBorders,
                    int bandIdx,
                    double epsilon);

        void reset();

        // Process 1 sample for a specific channel
        float getSample(int channel);
        float process(float input, int channel);

        // Complex gain at a specific z-point
        std::complex<double> h(const std::complex<double>& z) const;
        std::complex<double> getGain(const std::complex<double>& z) const { return h(z); }

        const std::vector<std::complex<double>>& getPoles() const { return poles; }
        double getTotalScale() const { return totalScale; }

    private:
        Type type = Type::LowPass;
        int order = 8;
        int numChannels = 0;

        std::vector<std::complex<double>> poles; // All individual poles
        double totalScale = 1.0;
        std::vector<double> sectionScales;

        // [channel][section]
        std::vector<std::vector<std::unique_ptr<FilterElement>>> channelElements;
    };

}
