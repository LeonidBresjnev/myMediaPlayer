
#include "Oscillator.h"
#include "Log.h"


#include <fstream>
#include <juce_audio_formats/juce_audio_formats.h>

namespace equalizer {

    Oscillator::Oscillator() {
        formatManager.registerBasicFormats();
    }

    void Oscillator::onPlaybackStopped() {
        LOGD("onPlaybackStopped");

        reader.reset();
        reader.release();
    }

    int32_t Oscillator::getSampleRate() const {
        return this->sampleRate;
    }

    uint16_t Oscillator::getChannelCount() const {
        return this->numChannels;
    }

    float Oscillator::getSample() {
        if (bufferpointer >= 1024) {
            //refill buffer;
            reader->read(&floatBuffer,
                         0,
                         1024,
                         currentposition,
                         true,
                         false);
            bufferpointer=0;
            currentposition = currentposition + 1024;
        }
        auto sample = floatBuffer.getReadPointer(currentchannel)[bufferpointer];

        if (++currentchannel >= numChannels) {
            currentchannel = 0;
            bufferpointer++;
        }
        return sample;
    }

    bool Oscillator::load (const std::string& fileName)
    {
        juce::File mp3File(fileName);
        // Enable support for MP3, WAV, etc.
        if (!mp3File.exists()) {
            LOGD("File not found");
            return false;
        }

        reader =   std::unique_ptr<juce::AudioFormatReader>(formatManager.createReaderFor(mp3File));
        LOGD("JUCE samplerate %f",reader->sampleRate);
        LOGD("JUCE channels %d",reader->numChannels);
        LOGD("JUCE usesFloatingPointData %d",reader->usesFloatingPointData);
        numChannels=reader->numChannels;
        sampleRate = static_cast<int32_t>(reader->sampleRate);

        // Create a buffer for audio samples
        floatBuffer=juce::AudioBuffer<float>(numChannels,  1024);
        bufferpointer=1024;
        currentposition=0;
        currentchannel=0;
        return true;
    }

}
