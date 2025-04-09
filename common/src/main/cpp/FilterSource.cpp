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
        //double tanwc = tan(cutoff / 2.0);

        double alpha = sin((mu-cutoff)/2) / sin((mu+cutoff)/2);
            //LOGD("alpha=%f", alpha);

        for (auto i = 0; i < (order / 2); i++) {
            std::complex<double> pole = (polesD[i]+alpha)/(polesD[i]*alpha+1.0);
            double scale = std::pow( abs((pole+1.0)*tan(mu/2)*(alpha-1.0)/((alpha+1.0)*4.0)*(std::pow(2.0/e,1.0/order ))),2.0);


           // LOGD("i= %d, pole: %f, %f, scale: %f",i, pole.real(), pole.imag(), scale);
            for (auto &channel: lowpass) {
                    /*
                    double theta = PI / 2.0 + PI * (2.0 * i + 1.0) / order;
                    std::complex<double> e = std::complex(cos(theta), sin(theta));
                    std::complex<double> poles =
                            (ComplexOne + (e * tanwc)) / (ComplexOne - (e * tanwc));
                    //std::cout<<"theta: "<<theta<<", "<<poles<<"\n";
                    double scale = (1.0 - 2.0 * poles.real() + std::pow(abs(poles), 2.0)) / 4.0;
    */
                channel[i] = (i==0) ? FilterElement(myDuplicator) : FilterElement(&channel[i - 1]);


                channel[i].setNumerator(MinusComplexOne, scale);
                channel[i].setDenominator(pole);
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

        //



        for (int band=0; band<6; band++) {
            double cutoffLow = (PI * 2.0 * static_cast<double>(freqBorders[band])) / static_cast<double>(sampleRate);
            double cutoffHigh = (PI * 2.0 * static_cast<double>(freqBorders[band+1])) / static_cast<double>(sampleRate);
            //double wal = tan(cutoffLow/2.0);
            //double wau = tan(cutoffHigh/2.0);

            //double w0 = wau*wal;
            //double w1=(wau-wal);


            //LOGD("cutoffs, %f, %f", cutoffLow, cutoffHigh);
            //LOGD("w, %f, %f", w0, w1);

            alpha = cos((cutoffLow+cutoffHigh)/2) / cos((cutoffLow-cutoffHigh)/2);
            double k = tan(mu/2)/tan((cutoffHigh-cutoffLow)/2);

            //std::complex<double> scaleAngle = Complex(cos((cutoffLow+cutoffHigh)/2),sin((cutoffLow+cutoffHigh)/2));

            for (auto i = 0; i < order; i++) {
                //Buttersworth with analog prototype:;
                //double theta = PI / 2.0 + PI * (2.0 * i + 1.0) / (2*order);
                //analog poles
               /* std::complex<double> e = std::complex(cos(theta), sin(theta));

                std::complex<double> discriminant = std::pow(e*w1,2.0 )-w0*4.0;

                std::complex<double> pole;
                if (i<order/2 ) {
                    pole = (std::pow(discriminant,0.5)+(w0-1))/(e*w1-(1+w0) );
                } else {
                    pole = (-std::pow(discriminant,0.5)+(w0-1))/(e*w1-(1+w0) );
                }

                double scale = abs( (scaleAngle-ComplexOne)*(scaleAngle+ComplexOne))/
                               abs(scaleAngle*scaleAngle
                                   - scaleAngle*pole.real()*2.0+(abs(pole)*abs(pole)));
*/
                //Chebyshev with digital prototype:;
                std::complex a = (polesD[i]+1.0)*alpha*k,
                b= (polesD[i]*(k+1)+(k-1))*(polesD[i]*(k-1)+k+1.0),
                d=std::pow(a*a-b,0.5);
                std::complex<double> pole = ((i<order/2) ? -d+a : d+a)/(polesD[i]*(k-1)+k+1.0);

                double scale = abs( ((polesD[i]+1.0)*tan(mu/2)/((polesD[i]*(k-1)+k+1.0)*2.0)) *  (std::pow(2.0/e,1.0/order)));


                for (auto c=0; c<numChannels; c++) {

                    bandpass[band][c][i] = (i==0)? FilterElement(myDuplicator): FilterElement(&bandpass[band][c][i - 1]);
                    bandpass[band][c][i].setNumerator({1.f,0.f,-1.f}, scale);
                    bandpass[band][c][i].setDenominator(pole);
                }
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

//            LOGD("i= %d, pole: %f, %f, scale: %f",i, pole.real(), pole.imag(), scale);
            for (auto &channel: highpass) {
                    //double theta = PI / 2.0 + PI * (2.0 * i + 1.0) / order;
                    //std::complex<double> e = std::complex(cos(theta), sin(theta));
                    //std::complex<double> poles = (ComplexOne + (e * tanwc)) / (ComplexOne - (e * tanwc));
                    //std::cout<<"theta: "<<theta<<", "<<poles<<"\n";
                    //double scale = (1.0 + 2.0 * poles.real() + std::pow(abs(poles), 2.0)) / 4.0;

                    channel[i] = (i==0) ? FilterElement(myDuplicator) : FilterElement(&channel[i - 1]);
                    channel[i].setNumerator(ComplexOne, scale);
                    channel[i].setDenominator(pole);
                }
        }

        LOGD("filter constucted");
    }

    float FilterSource::getSample() {
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