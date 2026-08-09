#pragma once

#include <cmath>
#include "AudioSource.h"
#include <atomic>
#include <functional>
#include <thread>
#include <vector>
#include <mutex>

#include <juce_audio_formats/juce_audio_formats.h>
#include <juce_audio_basics/juce_audio_basics.h>

namespace equalizer {

    class Oscillator : public AudioSource {
    public:
        explicit Oscillator();
        virtual ~Oscillator();

        float getSample() override;
        void onPlaybackStopped() override;

        int32_t getSampleRate() const;
        uint16_t getChannelCount() const;
        int64_t getLengthInSamples() const;
        int64_t getCurrentPositionInSamples() const;

        bool load(const std::string& fileName, int32_t sampleRateFallback = 44100, int32_t channelsFallback = 2);
        void seekTo(double positionSeconds);

        void pushPCMData(const float* data, int numSamples, int streamSR, int streamChannels);
        int getAvailableSamples() const;

        class StreamDecoder;

    private:
        uint16_t numChannels = 2;
        int32_t sampleRate = 0;
        uint16_t bufferpointer = 1024;
        bool _isStream = false;

        juce::AudioFormatManager formatManager;
        std::unique_ptr<juce::AudioFormatReader> reader;
        std::unique_ptr<juce::AudioFormatReaderSource> readerSource;
        mutable std::mutex _readerMutex;

        std::unique_ptr<StreamDecoder> _streamDecoder;

        std::vector<float> streamBuffer;
        size_t streamReadPos = 0;
        size_t streamWritePos = 0;
        size_t streamAvailable = 0;
        mutable std::mutex streamMutex;

        juce::AudioBuffer<float> floatBuffer;
        juce::int64 currentposition = 0;
        uint16_t currentchannel = 0;
    };

}
