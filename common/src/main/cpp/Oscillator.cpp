
#include "Oscillator.h"
#include "Log.h"


#include <fstream>
#include <juce_audio_formats/juce_audio_formats.h>

namespace equalizer {

    Oscillator::Oscillator() {
        formatManager.registerBasicFormats();
        formatManager.registerFormat(new juce::FlacAudioFormat(), false);
        formatManager.registerFormat(new juce::OggVorbisAudioFormat(), false);
        formatManager.registerFormat(new juce::MP3AudioFormat(), false);
    }

    void Oscillator::onPlaybackStopped() {
        LOGD("onPlaybackStopped");
        // We no longer reset the reader here to allow resuming from the same position.
        // The reader will be replaced in load() when a new file is selected.
    }

    int32_t Oscillator::getSampleRate() const {
        return this->sampleRate;
    }

    uint16_t Oscillator::getChannelCount() const {
        return this->numChannels;
    }

    int64_t Oscillator::getLengthInSamples() const {
        std::lock_guard<std::mutex> lock(_readerMutex);
        if (reader != nullptr) {
            return reader->lengthInSamples;
        }
        return 0;
    }

    int64_t Oscillator::getCurrentPositionInSamples() const {
        std::lock_guard<std::mutex> lock(_readerMutex);
        return currentposition;
    }

    float Oscillator::getSample() {
        std::lock_guard<std::mutex> lock(_readerMutex);

        // Defensive check: if reader is being swapped or is null, return silence
        if (reader == nullptr) {
            return 0.0f;
        }

        // End of file detection
        if (currentposition >= reader->lengthInSamples) {
            return 0.0f;
        }

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
            LOGD("File not found: %s", fileName.c_str());
            return false;
        }

        auto newReader = std::unique_ptr<juce::AudioFormatReader>(formatManager.createReaderFor(mp3File));
        if (newReader == nullptr) {
            LOGD("Failed to create reader for: %s", fileName.c_str());
            return false;
        }

        LOGD("JUCE samplerate %f",newReader->sampleRate);
        LOGD("JUCE channels %d",newReader->numChannels);
        LOGD("JUCE usesFloatingPointData %d",(int)newReader->usesFloatingPointData);

        {
            std::lock_guard<std::mutex> lock(_readerMutex);
            reader = std::move(newReader);
            numChannels=reader->numChannels;
            sampleRate = static_cast<int32_t>(reader->sampleRate);

            // Create a buffer for audio samples
            floatBuffer=juce::AudioBuffer<float>(numChannels,  1024);
            bufferpointer=1024;
            currentposition=0;
            currentchannel=0;
        }
        return true;
    }

    void Oscillator::seekTo(double positionSeconds) {
        std::lock_guard<std::mutex> lock(_readerMutex);
        if (reader == nullptr) return;

        // Convert seconds to sample position
        auto targetSample = static_cast<int64_t>(positionSeconds * sampleRate);

        // Clamp to valid range
        if (targetSample < 0) targetSample = 0;
        if (targetSample > reader->lengthInSamples) targetSample = reader->lengthInSamples;

        // Atomic-ish update of position
        currentposition = targetSample;

        // Reset buffer pointers to force an immediate refill from the new position
        bufferpointer = 1024;
        currentchannel = 0;
    }

}
