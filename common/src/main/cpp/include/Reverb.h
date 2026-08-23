#pragma once

#include <vector>
#include <atomic>
#include <array>
#include <cstddef>

namespace equalizer {

struct ReverbParams {
    bool enabled;
    float balance;
    float r;
    float g;
};

// Simple circular buffer for reverb delay lines
class ReverbCircularBuffer {
public:
    void resize(size_t size) {
        buffer.assign(size, 0.0f);
        writeIndex = 0;
    }

    float push(float sample) {
        if (buffer.empty()) return 0.0f;
        float displaced = buffer[writeIndex];
        buffer[writeIndex] = sample;
        writeIndex = (writeIndex + 1) % buffer.size();
        return displaced;
    }

    float sampleAtDelay(size_t delay) const {
        if (buffer.empty() || delay >= buffer.size()) return 0.0f;
        size_t readIndex = (writeIndex + buffer.size() - 1 - delay) % buffer.size();
        return buffer[readIndex];
    }

private:
    std::vector<float> buffer;
    size_t writeIndex = 0;
};

class SingleReverbFilter {
public:
    SingleReverbFilter(float g0, float r0, size_t L0);
    float process(float inputSample, float r_mult, float g_mult);

private:
    ReverbCircularBuffer inputHistory;
    ReverbCircularBuffer outputHistory;
    float baseG;
    float baseR;
    size_t L;
};

class ReverbFilter {
public:
    ReverbFilter();
    float process(float inputSample, float r_mult, float g_mult);

private:
    std::array<SingleReverbFilter, 8> reverbs;
};

} // namespace equalizer
