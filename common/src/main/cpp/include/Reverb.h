#pragma once

#include <vector>
#include <atomic>
#include <array>
#include <cstddef>
#include "AudioSource.h"

namespace equalizer {

struct ReverbParams {
    bool enabled;
    float balance;
    float d;
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

    [[nodiscard]] float sampleAtDelay(size_t delay) const {
        if (buffer.empty() || delay >= buffer.size()) return 0.0f;
        size_t readIndex = (writeIndex + buffer.size() - 1 - delay) % buffer.size();
        return buffer[readIndex];
    }

private:
    std::vector<float> buffer;
    size_t writeIndex = 0;
};

class SingleReverbFilter : public AudioSource {
public:
    SingleReverbFilter(float g0, float r0, size_t L0);
    float getSample() override;
    void setSample(float);
    void setR(float r_mult);
    void setG(float g_mult);
    void onPlaybackStopped() override;

private:
    ReverbCircularBuffer inputHistory;
    ReverbCircularBuffer outputHistory;
    float baseG;
    float baseR;
    std::atomic<float> g;
    std::atomic<float> r;
    size_t L;
};

class ReverbFilter : public AudioSource {
public:
    ReverbFilter();
    float getSample() override;
    void setSample(float);
    void onPlaybackStopped() override;
    void setRmult(float);
    void setGmult(float);
    void setD(float);

private:
    std::array<SingleReverbFilter, 8> reverbs;
    std::atomic<float> gMult;
    std::atomic<float> rMult;

    //control the Hoedinger allpass filter
    ReverbCircularBuffer inputHistory;
    ReverbCircularBuffer outputHistory;
    std::atomic<float> d;
};

} // namespace equalizer
