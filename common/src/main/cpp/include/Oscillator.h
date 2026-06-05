#pragma once

#include <cmath>
#include "AudioSource.h"
//#include "../../../../../not_used/mp32pcm.h"
#include <atomic>
#include <functional>


#include <fstream>
#include <juce_audio_formats/juce_audio_formats.h>

namespace equalizer {

    class Oscillator : public AudioSource {
    public:
        explicit Oscillator();

        float getSample() override;
        void onPlaybackStopped() override;


        int32_t getSampleRate() const;
        uint16_t getChannelCount() const;

        bool load(const std::string& );
       // std::shared_ptr<std::ifstream> pmyfile;

    private:

        uint16_t numChannels=2;
        int32_t sampleRate=0;
        uint16_t bufferpointer;
        //uint8_t buffer[1024];

        juce::AudioFormatManager formatManager;
        std::unique_ptr<juce::AudioFormatReader> reader;
        int numSamplesToRead;
        juce::AudioBuffer<float> floatBuffer;
        juce::AudioBuffer<int> intBuffer;
        juce::int64 currentposition;
        u_short currentchannel;
    };

}