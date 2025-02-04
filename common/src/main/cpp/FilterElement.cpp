
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
       // this->takeNew=takeNew_;

    }

    void FilterElement::setNumerator(std::array<float, 2> coefs) {
        this->numerator[0] = coefs[0];
        this->numerator[1] = coefs[1];
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
        y.size = 1;
    }

    void FilterElement::setDenominator(std::complex<double> z0) {
        this->denominator[0] = 1.f;
        this->denominator[1] = static_cast<float>(-2.0 * z0.real());
        this->denominator[2] = static_cast<float>(abs(z0) * abs(z0));
        y.size = 2;
    }


    float FilterElement::getSample() {
        if (providerType == 0) x.push_back(providerRaw->getSample());
        else x.push_back(providerFilter->getSample());
        float newSample =
                (numerator[0] * x.buffer[(x.head + 2) % x.size] +
                 numerator[1] * x.buffer[(x.head + 1) % x.size] +
                 numerator[2] * x.buffer[(x.head + 0) % x.size]
                 - denominator[1] * y.buffer[(y.head + 1) % y.size]
                 - denominator[2] * y.buffer[(y.head + 0) % y.size]) /*/ denominator[0]*/;
        y.push_back(newSample);
        //LOGD("sample value %f", newSample);
        return newSample;
    }
}
/*
void Filter::display() {
    std::cout<<"numerator:"<<numerator[0]<<" "<<numerator[1]<<" "<<numerator[2]<<std::endl;
    x.display();
    std::cout<<"y:\n";
    y.display();
    std::cout<<"denominator"<<denominator[0]<<" "<<denominator[1]<<" "<<denominator[2]<<std::endl;
}
*/

