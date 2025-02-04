#include "FilterSource.h"
#include "Log.h"

namespace equalizer {
    FilterSource::FilterSource(std::shared_ptr<AudioSource> source) :
    myDuplicator{std::make_shared<Duplicator>()},
    _source(std::move(source)){}


    void FilterSource::setFilter(int sampleRate, int numChannels_) {
        this->numChannels = numChannels_;
        const double PI = acos(-1);

        double cutoff = PI * 2.0 * static_cast<double>(freqBorders[0]) / static_cast<double>(sampleRate);
        double tanwc = tan(cutoff / 2.0);

        for (auto &channel: lowpass) {
            for (int i = 0; i < (order / 2); i++) {
                double theta = PI / 2.0 + PI * (2.0 * i + 1.0) / order;
                std::complex<double> e = std::complex(cos(theta), sin(theta));
                std::complex<double> poles =
                        (ComplexOne + (e * tanwc)) / (ComplexOne - (e * tanwc));
                //std::cout<<"theta: "<<theta<<", "<<poles<<"\n";
                double scale = (1.0 - 2.0 * poles.real() + std::pow(abs(poles), 2.0)) / 4.0;
                if (i == 0) channel[0] = FilterElement(myDuplicator);
                else channel[i] = FilterElement(&channel[i - 1]);
                channel[i].setNumerator(MinusComplexOne, scale);
                channel[i].setDenominator(poles);
            }
        }
/*
        for (int band=0; band<6; band++) {
            double cutoffLow = PI * 2.0 * static_cast<double>(freqBorders[band+1]) / static_cast<double>(sampleRate);
            double tanwcLow = tan(cutoffLow / 2.0);
            double cutoffHigh = PI * 2.0 * static_cast<double>(freqBorders[band]) / static_cast<double>(sampleRate);
            double tanwcHigh = tan(cutoffHigh / 2.0);
            for (int c=0; c<numChannels; c++) {
                for (int j = 0; j < order; j++) {
                    if (j < order / 2) {
                        int i=j;
                        double theta = PI / 2.0 + PI * (2.0 * i + 1.0) / order;
                        std::complex<double> e = std::complex(cos(theta), sin(theta));
                        std::complex<double> poles =
                                (ComplexOne + (e * tanwcLow)) / (ComplexOne - (e * tanwcLow));

                        double scale = (1.0 - 2.0 * poles.real() + std::pow(abs(poles), 2.0)) / 4.0;
                        if (i == 0) bandpass[band][c][i] = FilterElement(myDuplicator);
                        else bandpass[band][c][i] = FilterElement(&bandpass[band][c][i - 1]);
                        bandpass[band][c][i].setNumerator(MinusComplexOne, scale);
                        bandpass[band][c][i].setDenominator(poles);
                    }
                    else {
                        int i=j-order/2;
                        double theta = PI / 2.0 + PI * (2.0 * i + 1.0) / order;
                        std::complex<double> e = std::complex(cos(theta), sin(theta));
                        std::complex<double> poles =
                                (ComplexOne + (e * tanwcHigh)) / (ComplexOne - (e * tanwcHigh));

                        double scale = (1.0 + 2.0 * poles.real() + std::pow(abs(poles), 2.0)) / 4.0;

                        bandpass[band][c][j] = FilterElement(bandpass[band][c][j - 1]);
                        bandpass[band][c][j].setNumerator(ComplexOne, scale);
                        bandpass[band][c][j].setDenominator(poles);
                    }
                }

            }
        }*/

        for (int band=0; band<6; band++) {
            double cutoffLow = PI * 2.0 * static_cast<double>(freqBorders[band]) / static_cast<double>(sampleRate);
            double cutoffHigh = PI * 2.0 * static_cast<double>(freqBorders[band+1]) / static_cast<double>(sampleRate);
            double wal = tan(cutoffLow/2.0);
            double wau = tan(cutoffHigh/2.0);

            double w0 = wau*wal;
            double w1=(wau-wal);


            //LOGD("cutoffs, %f, %f", cutoffLow, cutoffHigh);
            //LOGD("w, %f, %f", w0, w1);
            std::complex<double> scaleAngle = Complex(cos((cutoffLow+cutoffHigh)/2),sin((cutoffLow+cutoffHigh)/2));
            for (int c=0; c<numChannels; c++) {
                for (int i = 0; i < order; i++) {
                    double theta = PI / 2.0 + PI * (2.0 * i + 1.0) / (2*order);
                    std::complex<double> e = std::complex(cos(theta), sin(theta));
                    //LOGD("e %f,%f", e.real(), e.imag());

                    std::complex<double> discriminant = std::pow(e*w1,2.0 )-w0*4.0;

                    //LOGD("discriminant - %d, %f, %f", freqBorders[band], discriminant.real(), discriminant.imag());

                    std::complex<double> pole;
                    if (i<order/2 ) {
                        pole = (std::pow(discriminant,0.5)+(w0-1))/(e*w1-(1+w0) );
                    } else {
                        pole = (-std::pow(discriminant,0.5)+(w0-1))/(e*w1-(1+w0) );
                    }

                    //LOGD("pole, %f, %f", pole.real(), pole.imag());

                    double scale = abs( (scaleAngle-ComplexOne)*(scaleAngle+ComplexOne))/
                            abs(scaleAngle*scaleAngle
                            - scaleAngle*pole.real()*2.0+(abs(pole)*abs(pole)));
                    if (i == 0) bandpass[band][c][i] = FilterElement(myDuplicator);
                    else bandpass[band][c][i] = FilterElement(&bandpass[band][c][i - 1]);
                    bandpass[band][c][i].setNumerator({1.f,0.f,-1.f}, 1.0/scale);
                    bandpass[band][c][i].setDenominator(pole);

                }
            }
        }

        cutoff = PI * 2.0 * static_cast<double>(freqBorders[6]) / static_cast<double>(sampleRate);
        tanwc = tan(cutoff / 2.0);
        for (auto &channel: highpass) {
            for (int i = 0; i < (order / 2); i++) {
                double theta = PI / 2.0 + PI * (2.0 * i + 1.0) / order;
                std::complex<double> e = std::complex(cos(theta), sin(theta));
                std::complex<double> poles =
                        (ComplexOne + (e * tanwc)) / (ComplexOne - (e * tanwc));
                //std::cout<<"theta: "<<theta<<", "<<poles<<"\n";
                double scale = (1.0 + 2.0 * poles.real() + std::pow(abs(poles), 2.0)) / 4.0;
                if (i == 0) channel[0] = FilterElement(myDuplicator);
                else channel[i] = FilterElement(&channel[i - 1]);
                channel[i].setNumerator(ComplexOne, scale);
                channel[i].setDenominator(poles);
            }
        }

        LOGD("filter constucted");
    }

    int16_t FilterSource::getSample() {
        myDuplicator->setSample(_source->getSample());
        auto sample =
                amplitude[0]*lowpass[currentChannel][order/2-1].getSample()
                +  amplitude[1]*bandpass[0][currentChannel][order-1].getSample()
                   +  amplitude[2]*bandpass[1][currentChannel][order-1].getSample()
                      +  amplitude[3]*bandpass[2][currentChannel][order-1].getSample()
                         +  amplitude[4]*bandpass[3][currentChannel][order-1].getSample()
                            +  amplitude[5]*bandpass[4][currentChannel][order-1].getSample()
                               +  amplitude[6]*bandpass[5][currentChannel][order-1].getSample()
                   +  amplitude[7]*highpass[currentChannel][order/2-1].getSample();
        currentChannel = (currentChannel +1)%numChannels;
        //LOGD("sample value, return %f", sample);
        return static_cast<int16_t>(std::round(sample));
    }

    void FilterSource::onPlaybackStopped() {
        _source->onPlaybackStopped();
    }
    void FilterSource::setAmplitude(float newAmplitude, int freqInterval) {
        amplitude[freqInterval].store(newAmplitude);
    }

    int16_t Duplicator::getSample() {
        return currentSample;
    }
    void Duplicator::setSample(int16_t sample) {
        this->currentSample=sample;
    }
    void Duplicator::onPlaybackStopped() {
    }

}