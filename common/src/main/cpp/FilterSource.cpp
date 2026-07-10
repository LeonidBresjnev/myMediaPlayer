#include "FilterSource.h"
#include "Log.h"

namespace equalizer {
    FilterSource::FilterSource(std::shared_ptr<AudioSource> source) :
    myDuplicator{std::make_shared<Duplicator>()},
    _source(std::move(source)){}


    void FilterSource::setFilter(int sampleRate, int numChannels_) {
        this->numChannels = numChannels_;
        const double PI = acos(-1);


        //Define digital prototype of Chebyshev type;
        const double mu = PI / 8;
        const double wa = tan(mu/2)*2*sampleRate;
        const double e = 0.4;

        std::array<std::complex<double>, order> polesD;
        for (auto m = 1; m <= order; m++) {
            std::complex<double> poleA=std::complex(
                    -sinh(asinh(1.0 / e) / order) * sin(PI * (2 * m - 1) / (2 * order)),
                    cosh(asinh(1.0 / e) / order) * cos(PI * (2 * m - 1) / (2 * order)))*wa;
            polesD[m-1] = -(poleA+2.0 * sampleRate)/(poleA-2.0 * sampleRate);

            // LOGD("m=%d, pole proto: %f, %f, pole analog: %f, %f",m, polesD[m-1].real(), polesD[m-1].imag(), poleA.real(), poleA.imag());
        }



        //make lowpass;
        double cutoff = PI * 2.0 * static_cast<double>(freqBorders[0]) / static_cast<double>(sampleRate);

        double alpha = sin((mu-cutoff)/2) / sin((mu+cutoff)/2);

        for (auto c=0; c<numChannels; c++) {
            for (auto i = 0; i < (order / 2); i++) {
                std::complex<double> pole = (polesD[i]+alpha)/(polesD[i]*alpha+1.0);
                double scale = std::pow( abs((pole+1.0)*tan(mu/2)*(alpha-1.0)/((alpha+1.0)*4.0)*(std::pow(2.0/e,1.0/order ))),2.0);
                    lowpass[c][i] = (i==0) ? FilterElement(myDuplicator) : FilterElement(lowpass[c][i - 1]);
                lowpass[c][i].setNumerator(MinusComplexOne, scale);
                lowpass[c][i].setDenominator(pole);
            }

            for (int band=0; band<6; band++) {
                double cutoffLow = (PI * 2.0 * static_cast<double>(freqBorders[band])) / static_cast<double>(sampleRate);
                double cutoffHigh = (PI * 2.0 * static_cast<double>(freqBorders[band+1])) / static_cast<double>(sampleRate);

                alpha = cos((cutoffLow+cutoffHigh)/2) / cos((cutoffLow-cutoffHigh)/2);
                double k = tan(mu/2)/tan((cutoffHigh-cutoffLow)/2);

                //std::complex<double> scaleAngle = Complex(cos((cutoffLow+cutoffHigh)/2),sin((cutoffLow+cutoffHigh)/2));

                for (auto i = 0; i < order; i++) {

                    //Chebyshev with digital prototype:;
                    std::complex a = (polesD[i]+1.0)*alpha*k,
                    b= (polesD[i]*(k+1)+(k-1))*(polesD[i]*(k-1)+k+1.0),
                    d=std::pow(a*a-b,0.5);
                    std::complex<double> pole = ((i<order/2) ? -d+a : d+a)/(polesD[i]*(k-1)+k+1.0);

                    double scale = abs( ((polesD[i]+1.0)*tan(mu/2)/((polesD[i]*(k-1)+k+1.0)*2.0)) *  (std::pow(2.0/e,1.0/order)));
                        bandpass[c][band][i] = (i==0)? FilterElement(myDuplicator): FilterElement(&bandpass[c][band][i - 1]);
                        bandpass[c][band][i].setNumerator({1.f,0.f,-1.f}, scale);
                        bandpass[c][band][i].setDenominator(pole);
                }
            }

            //Highpass:;
            cutoff = PI * 2.0 * static_cast<double>(freqBorders[6]) / static_cast<double>(sampleRate);
            //double tanwc = tan(cutoff / 2.0);

            alpha = -cos((mu+cutoff)/2) / cos((mu-cutoff)/2);

            //  LOGD("alpha=%f", alpha);

            for (auto i = 0; i < (order / 2); i++) {
                std::complex pole = -(polesD[i]+alpha)/(polesD[i]*alpha+1.0);
                double scale = std::pow( abs((-pole+1.0)*tan(mu/2)*(alpha-1.0)/((alpha+1.0)*4.0)*(std::pow(2.0/e,1.0/order ))),2.0);

                    highpass[c][i] = (i==0) ? FilterElement(myDuplicator) : FilterElement(highpass[c][i - 1]);
                    highpass[c][i].setNumerator(ComplexOne, scale);
                    highpass[c][i].setDenominator(pole);
            }
        }

        LOGD("filter constucted");
    }

    float FilterSource::getSample() {
        myDuplicator->setSample(_source->getSample());
        auto sample =
                amplitude[0]*lowpass[currentChannel][order/2-1].getSample()
                +  amplitude[1]*bandpass[currentChannel][0][order-1].getSample()
                   +  amplitude[2]*bandpass[currentChannel][1][order-1].getSample()
                      +  amplitude[3]*bandpass[currentChannel][2][order-1].getSample()
                         +  amplitude[4]*bandpass[currentChannel][3][order-1].getSample()
                            +  amplitude[5]*bandpass[currentChannel][4][order-1].getSample()
                               +  amplitude[6]*bandpass[currentChannel][5][order-1].getSample()
                   +  amplitude[7]*highpass[currentChannel][order/2-1].getSample();
        currentChannel = (currentChannel +1)%numChannels;
        //LOGD("filtered sample value, return %f", sample);
        return sample;
    }

    void FilterSource::onPlaybackStopped() {
        _source->onPlaybackStopped();
    }
    void FilterSource::setAmplitude(float newAmplitude, int freqInterval) {
        amplitude[freqInterval].store(newAmplitude);
    }

    float Duplicator::getSample() {
        return currentSample;
    }
    void Duplicator::setSample(float sample) {
        this->currentSample=sample;
    }
    void Duplicator::onPlaybackStopped() {
    }

}