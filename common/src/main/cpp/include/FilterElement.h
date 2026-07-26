#pragma once
#include <complex>
#include <array>

#include "CircularBuffer.h"
#include "FilePlayer.h"


namespace equalizer {
    class FilterElement {
    private:

        short providerType;

        FilterElement *providerFilter;

        std::shared_ptr<AudioSource> providerRaw;

        CircularBuffer x = CircularBuffer(3);
        CircularBuffer y = CircularBuffer(3);

        float numerator[3]{0.f, 0.f, 0.f};
        float denominator[3]{0.f, 0.f, 0.f};
        //bool takeNew=true;

    public:
        FilterElement() = default;

        explicit FilterElement(std::shared_ptr<AudioSource> providerRaw_);

        explicit FilterElement(FilterElement *providerFilter_);

        void setNumerator(std::array<float, 2>);
        void setNumerator(std::array<float, 3>, double);

        void setNumerator(std::complex<double>, double);

        void setDenominator(std::complex<double>);

        void setDenominator(double);

        void setAllpass(std::complex<double> pole);

        float getSample() ;
        float process(float input);
    private:
        float compute();
    };
}