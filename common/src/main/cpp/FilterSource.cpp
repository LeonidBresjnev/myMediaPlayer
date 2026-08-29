#include "FilterSource.h"
#include "Log.h"

namespace equalizer {
    FilterSource::FilterSource(std::shared_ptr<AudioSource> source) :
    myDuplicator{std::make_shared<Duplicator>()},
    _source(std::move(source)){
        bands.resize(8);
        for(int i=0; i<7; ++i) allpassChains.push_back(std::make_shared<AllpassFilter>());
        setFilter(44100, 2);
    }

    void FilterSource::setFilter(int sampleRate, int numChannels_) {
        if (sampleRate == lastSampleRate && numChannels_ == lastNumChannels) {
            return;
        }
        this->numChannels = numChannels_;
        this->lastSampleRate = sampleRate;
        this->lastNumChannels = numChannels_;

        c_const = ChebyshevPrototype::PI * 2.0 / sampleRate;

        const double epsilon = 0.5;
        prototype.calculate(order, sampleRate, epsilon);
        const auto& polesD = prototype.getPolesD();

        // 1. Link the processing chain
        bands[0].setup(ChebyshevFilter::Type::LowPass, order, numChannels, myDuplicator);

        allpassChains[0]->setup(numChannels, myDuplicator);
        for (int i = 1; i < 7; ++i) {
            allpassChains[i]->setup(numChannels, allpassChains[i - 1]);
        }

        for (int i = 0; i < 6; ++i) {
            bands[i + 1].setup(ChebyshevFilter::Type::BandPass, order, numChannels, allpassChains[i]);
        }
        bands[7].setup(ChebyshevFilter::Type::HighPass, order, numChannels, allpassChains[6]);

        // 2. Design the Chebyshev bands
        for (int i = 0; i < 8; ++i) {
            bands[i].design(polesD, sampleRate, freqBorders, i, epsilon);
        }

        // 3. Optimize and design Allpass filters
        optimizeAllpassFilters();

        LOGD("Filter Source: Hierarchical design constructed with allpass optimization for %d channels at %d Hz", numChannels, sampleRate);
    }

    void FilterSource::setDelay(int leftDelay, int rightDelay) {
        myDelay[0].setSize(min(max(1, leftDelay), 4096 - 1));
        myDelay[1].setSize(min(max(1, rightDelay), 4096 - 1));
        LOGD("Filter Source: delay set %d, %d", leftDelay, rightDelay);
    }

    std::complex<double> FilterSource::h(double f) const {
        std::complex<double> z = std::exp(std::complex<double>(0.0, f * c_const));

        std::complex<double> total = static_cast<double>(amplitude[0][0]) * bands[0].h(z);
        std::complex<double> cumulativeAP(1.0, 0.0);

        for (int i = 0; i < 6; ++i) {
            cumulativeAP *= allpassChains[i]->getGain(z);
            total += static_cast<double>(amplitude[0][i + 1]) * (cumulativeAP * bands[i + 1].h(z));
        }

        cumulativeAP *= allpassChains[6]->getGain(z);
        total += static_cast<double>(amplitude[0][7]) * (cumulativeAP * bands[7].h(z));

        return total;
    }

    std::complex<double> FilterSource::hUnoptimized(double f) const {
        std::complex<double> z = std::exp(std::complex<double>(0.0, f * c_const));
        std::complex<double> total(0.0, 0.0);
        for (int i = 0; i < 8; ++i) {
            total += static_cast<double>(amplitude[0][i]) * bands[i].h(z);
        }
        return total;
    }

    std::vector<double> FilterSource::getAnalysisResponse(double f_start, double f_end, double f_step) const {
        std::vector<double> res;
        for (double f = f_start; f <= f_end; f += f_step) {
            std::complex<double> g = h(f);
            res.push_back(std::abs(g));
            res.push_back(std::arg(g));
        }
        return res;
    }

    std::vector<double> FilterSource::getUnoptimizedAnalysisResponse(double f_start, double f_end, double f_step) const {
        std::vector<double> res;
        for (double f = f_start; f <= f_end; f += f_step) {
            std::complex<double> g = hUnoptimized(f);
            res.push_back(std::abs(g));
            res.push_back(std::arg(g));
        }
        return res;
    }

    std::vector<BandDesign> FilterSource::getFilterDesign() const {
        std::vector<BandDesign> designs;
        designs.reserve(8);

        for (int i = 0; i < 8; ++i) {
            BandDesign bd;
            const auto& p = bands[i].getPoles();
            bd.poles.reserve(p.size() * 2);
            for (const auto& pole : p) {
                bd.poles.push_back(pole);
                bd.poles.push_back(std::conj(pole));
            }

            // Add Zeros
            if (i == 0) { // LP
                bd.zeros.emplace_back(-1.0, 0.0);
                bd.zeros.emplace_back(-1.0, 0.0);
            } else if (i == 7) { // HP
                bd.zeros.emplace_back(1.0, 0.0);
                bd.zeros.emplace_back(1.0, 0.0);
            } else { // BP
                bd.zeros.emplace_back(1.0, 0.0);
                bd.zeros.emplace_back(-1.0, 0.0);
            }

            designs.push_back(std::move(bd));
        }
        return designs;
    }

    float FilterSource::getSample() {
        float inSample = _source->getSample();
        myDuplicator->setSample(inSample);

        float sample = amplitude[currentChannel][0] * bands[0].process(inSample, currentChannel);

        float currentAPInput = inSample;
        for (int i = 0; i < 7; ++i) {
            float apOut = allpassChains[i]->process(currentAPInput, currentChannel);
            sample += amplitude[currentChannel][i + 1] * bands[i + 1].process(apOut, currentChannel);
            currentAPInput = apOut;
        }

        currentChannel = (currentChannel + 1) % numChannels;

        auto params = reverbParams.load(std::memory_order_relaxed);
        if (params.enabled) {
            reverbFilters[currentChannel].setSample(sample);
            float reverbWet = reverbFilters[currentChannel].getSample();
            sample = (1.0f - params.balance) * sample + params.balance * reverbWet;
        }

        myDelay[currentChannel].setSample(sample);
        return myDelay[currentChannel].getSample();
    }

    void FilterSource::onPlaybackStopped() {
        _source->onPlaybackStopped();
    }

    void FilterSource::setAmplitude(float newAmplitude, int freqInterval) {
        if (freqInterval < 8) {
            amplitude[0][freqInterval] = newAmplitude;
        } else if (freqInterval < 16) {
            amplitude[1][freqInterval - 8] = newAmplitude;
        }
    }

    void FilterSource::setReverbParams(bool enabled, float balance, float r, float g, float d) {
        reverbParams.store({enabled, balance, d}, std::memory_order_relaxed);
        for (auto & reverbFilter : reverbFilters) {
            reverbFilter.setGmult(g);
            reverbFilter.setRmult(r);
            reverbFilter.setD(d);
        }
    }

    double FilterSource::lossfunction(int pairIndex, std::complex<double> pole) const {
        lossFunctionCalls++;
        if (pairIndex < 0 || pairIndex >= 7) return 1e18;

        double loss = 0.0;
        int borderFreq = freqBorders[pairIndex];
        int fStart = static_cast<int>(0.8 * borderFreq);
        int fEnd = static_cast<int>(1.2 * borderFreq);

        double fStep = static_cast<double>(fEnd - fStart) / 40.0;
        if (fStep < 1.0) fStep = 1.0;

        for (auto f = static_cast<double>(fStart); f < static_cast<double>(fEnd); f += 1.0) {
            std::complex<double> z = std::exp(std::complex<double>(0.0, f * c_const));

            // Optimization: Only consider the two bands adjacent to the current crossover.
            // This is sufficient for local phase alignment and significantly faster.
            std::complex<double> h1 = bands[pairIndex].getGain(z);
            std::complex<double> h2 = AllpassFilter::calculateGain(z, pole) * bands[pairIndex + 1].getGain(z);

            loss += std::pow(std::abs(h1 + h2) - 1.0, 2.0);
        }
        return loss;
    }

    void FilterSource::optimizeAllpassFilters() {
        auto mapToDisk = [](std::complex<double> z) {
            double absZ = std::abs(z);
            if (absZ < 1e-9) return z;
            double r_w = (-1.0 + std::sqrt(1.0 + 4.0 * absZ * absZ)) / (2.0 * absZ);
            return (z / absZ) * r_w;
        };

        auto mapToPlane = [](std::complex<double> w) {
            double absW2 = std::norm(w);
            if (absW2 >= 1.0) absW2 = 0.9999;
            return w / (1.0 - absW2);
        };

        for (int k = 0; k < 7; ++k) {
            lossFunctionCalls = 0;
            std::complex<double> currentPole(0.5, 0.1);
            std::complex<double> planeZ = mapToPlane(currentPole);
            double x = planeZ.real();
            double y = planeZ.imag();

            auto f_opt = [&](double px, double py) {
                return lossfunction(k, mapToDisk({px, py}));
            };

            double startLoss = f_opt(x, y);
            LOGD("FilterSource: Band %d start unoptimized loss: %f", k, startLoss);

            for (int iter = 0; iter < 100; ++iter) {
                double h_d = 1e-4;
                double f0 = f_opt(x, y);

                double fx_p = f_opt(x + h_d, y);
                double fx_m = f_opt(x - h_d, y);
                double fy_p = f_opt(x, y + h_d);
                double fy_m = f_opt(x, y - h_d);

                double fx = (fx_p - fx_m) / (2.0 * h_d);
                double fy = (fy_p - fy_m) / (2.0 * h_d);

                double fxx = (fx_p - 2.0 * f0 + fx_m) / (h_d * h_d);
                double fyy = (fy_p - 2.0 * f0 + fy_m) / (h_d * h_d);
                double fxy = (f_opt(x + h_d, y + h_d) - fx_p - fy_p + f0) / (h_d * h_d);

                double det = fxx * fyy - fxy * fxy;
                double dx, dy;

                if (std::abs(det) > 1e-12 && fxx > 0 && det > 0) {
                    dx = (fyy * fx - fxy * fy) / det;
                    dy = (fxx * fy - fxy * fx) / det;
                } else {
                    double gn = std::sqrt(fx * fx + fy * fy);
                    if (gn < 1e-12) break;
                    double al = 0.1;
                    dx = al * fx / gn;
                    dy = al * fy / gn;
                }

                double ss = 1.0;
                bool sf = false;
                while (ss > 1e-3) {
                    if (f_opt(x - ss * dx, y - ss * dy) < f0) {
                        x -= ss * dx;
                        y -= ss * dy;
                        sf = true;
                        break;
                    }
                    ss *= 0.5;
                }
                if (!sf || (ss * std::sqrt(dx * dx + dy * dy) < 1e-4)) break;
            }
            auto finalPole = mapToDisk({x, y});
            allpassChains[k]->design(finalPole);
            LOGD("FilterSource: Band %d optimized. Loss: %f, Pole: %f + %fi, Calls: %d",
                 k, lossfunction(k, finalPole), finalPole.real(), finalPole.imag(), lossFunctionCalls);
        }
    }

    float Duplicator::getSample() {
        return currentSample;
    }
    void Duplicator::setSample(float sample) {
        this->currentSample = sample;
    }
    void Duplicator::onPlaybackStopped() {}
}
