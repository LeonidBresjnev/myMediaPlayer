
#include <array>
#include <utility>
#include "FilterElement.h"
#include "Log.h"


namespace equalizer {
    FilterElement::FilterElement(std::shared_ptr<AudioSource> providerRaw_) {
        this->providerType = 0;
        this->providerRaw = std::move(providerRaw_);
        this->providerFilter = nullptr;
        this->numerator[0] = 1.f;
        this->denominator[0] = 1.f;
    }

    FilterElement::FilterElement(FilterElement *providerFilter_) {

        this->providerType = 1;
        this->providerFilter = providerFilter_;
        this->providerRaw = nullptr;
        this->numerator[0] = 1.f;
        this->denominator[0] = 1.f;
    }

    void FilterElement::setNumerator(std::array<float, 2> coefs) {
        this->numerator[0] = coefs[0];
        this->numerator[1] = coefs[1];
        this->numerator[2] = 0.0f;
        x.size = 2;
    }
    void FilterElement::setNumerator(std::array<float, 3> coefs, double scalar) {
        this->numerator[0] = static_cast<float>(coefs[0]*scalar);
        this->numerator[1] = static_cast<float>(coefs[1]*scalar);
        this->numerator[2] = static_cast<float>(coefs[2]*scalar);
        x.size = 3;
    }

    void FilterElement::setNumerator(std::complex<double> z0, double scalar) {

        this->numerator[0] = static_cast<float>(scalar);
        this->numerator[1] = static_cast<float>(-scalar * 2.0 * z0.real());
        this->numerator[2] = static_cast<float>(scalar * abs(z0) * abs(z0));
        x.size = 3;
    }

    void FilterElement::setDenominator(double d) {
        this->denominator[0] = 1.f;
        this->denominator[1] = static_cast<float>(d);
        this->denominator[2] = 0.0f;
        y.size = 1;
    }

    void FilterElement::setDenominator(std::complex<double> z0) {
        this->denominator[0] = 1.f;
        this->denominator[1] = static_cast<float>(-2.0 * z0.real());
        this->denominator[2] = static_cast<float>(abs(z0) * abs(z0));
        y.size = 2;
    }

    void FilterElement::setAllpass(std::complex<double> p) {
        float re = static_cast<float>(p.real());
        float norm2 = static_cast<float>(std::norm(p));

        this->numerator[0] = norm2;
        this->numerator[1] = -2.0f * re;
        this->numerator[2] = 1.0f;
        x.size = 3;

        this->denominator[0] = 1.0f;
        this->denominator[1] = -2.0f * re;
        this->denominator[2] = norm2;
        y.size = 2;
    }

    float FilterElement::getSample() {
        if (providerType == 0) x.push_back(providerRaw->getSample());
        else if (providerType == 1) x.push_back(providerFilter->getSample());
        return compute();
    }

    float FilterElement::process(float input) {
        x.push_back(input);
        return compute();
    }

    float FilterElement::compute() {
        float newSample =
                (numerator[0] * x.buffer[(x.head + 2) % x.size] +
                 numerator[1] * x.buffer[(x.head + 1) % x.size] +
                 numerator[2] * x.buffer[(x.head + 0) % x.size]
                 - denominator[1] * y.buffer[(y.head + 1) % y.size]
                 - denominator[2] * y.buffer[(y.head + 0) % y.size]);
        y.push_back(newSample);
        return newSample;
    }
}
