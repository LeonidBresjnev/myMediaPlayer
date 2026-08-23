#include "Reverb.h"

namespace equalizer {

SingleReverbFilter::SingleReverbFilter(float g0, float r0, size_t L0)
    : baseG(g0), baseR(r0), L(L0) {
    inputHistory.resize(2);
    outputHistory.resize(L0);
}

float SingleReverbFilter::process(float inputSample, float r_mult, float g_mult) {
    const float g = baseG * g_mult;
    const float r = baseR * r_mult;

    inputHistory.push(inputSample);

    float out = (inputSample - g * inputHistory.sampleAtDelay(1)) +
                g * outputHistory.sampleAtDelay(0) +
                r * outputHistory.sampleAtDelay(L - 1);

    outputHistory.push(out);
    return out;
}

ReverbFilter::ReverbFilter() : reverbs([]() {
    struct Config { float g, r; size_t L; };
    const std::array<Config, 8> base = {{
        {0.28f, 0.42f, 1499},
        {0.30f, 0.44f, 1637},
        {0.32f, 0.46f, 1901},
        {0.34f, 0.48f, 2347},
        {0.36f, 0.50f, 2693},
        {0.38f, 0.52f, 2953},
        {0.40f, 0.54f, 3251},
        {0.42f, 0.56f, 3571}
    }};

    // Find the maximum scaling factor for g such that r < (1 - g*f) * 0.999
    // This implies g*f < 1 - r/0.999, or f < (1 - r/0.999) / g
    double minFactor = 1e18;
    for (const auto& c : base) {
        double f = (1.0 - (double)c.r / 0.999) / (double)c.g;
        if (f < minFactor) minFactor = f;
    }

    // Subtract a tiny margin to ensure strict inequality (r < ...)
    float factor = static_cast<float>(minFactor) - 1e-6f;

    return std::array<SingleReverbFilter, 8> {
        SingleReverbFilter(base[0].g * factor, base[0].r, base[0].L),
        SingleReverbFilter(base[1].g * factor, base[1].r, base[1].L),
        SingleReverbFilter(base[2].g * factor, base[2].r, base[2].L),
        SingleReverbFilter(base[3].g * factor, base[3].r, base[3].L),
        SingleReverbFilter(base[4].g * factor, base[4].r, base[4].L),
        SingleReverbFilter(base[5].g * factor, base[5].r, base[5].L),
        SingleReverbFilter(base[6].g * factor, base[6].r, base[6].L),
        SingleReverbFilter(base[7].g * factor, base[7].r, base[7].L)
    };
}()) {}

float ReverbFilter::process(float inputSample, float r_mult, float g_mult) {
    float out = 0.0f;
    for (auto& reverb : reverbs) {
        out += reverb.process(inputSample, r_mult, g_mult);
    }
    return out / static_cast<float>(reverbs.size());
}

} // namespace equalizer
