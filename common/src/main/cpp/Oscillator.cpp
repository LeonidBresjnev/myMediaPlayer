
#include "Oscillator.h"
#include "Log.h"


#include <fstream>
namespace equalizer {
    int32_t Oscillator::fourBytesToInt (const uint8_t source[4],
                                        int startIndex )
    {
        int32_t result = (source[startIndex + 3] << 24) | (source[startIndex + 2] << 16) | (source[startIndex + 1] << 8) | source[startIndex];
        return result;

    }

    int16_t Oscillator::twoBytesToInt (const uint8_t  source[2],
                                       int startIndex)
    {
        int16_t result = (source[startIndex + 1] << 8) | source[startIndex];
        return (result);
    }
    Oscillator::Oscillator()
            : _phaseIncrement{0.4} {
    }

    void Oscillator::setSamplingRate(int samplingRate) {
        this->_phaseIncrement = 2.f * pi * 440.f / (float) samplingRate;
    }
/*
    int16_t Oscillator::getSample() {
        const auto sample = 0.5f * sin(_phase);
        if (channel) {
            _phase = fmod(_phase + _phaseIncrement, 2.f * pi);
        }
        channel = !channel;
        return ( static_cast<int16_t>(32768.f*amplitude*sample));
    }*/

    void Oscillator::onPlaybackStopped() {
        _phase = 0.f;
        inputFile.close();
        LOGD("file closed");
    }
    void Oscillator::setAmplitude(float newAmplitude) {
        amplitude.store(newAmplitude);
    }
    int32_t Oscillator::getSampleRate() const {
        return this->sampleRate;
    }
    uint16_t Oscillator::getChannelCount() const {
        return this->numChannels;
    }

    int16_t Oscillator::getSample() {
        uint8_t buffer[2];
        inputFile.read(reinterpret_cast<char *>(buffer), 2);
        int16_t sampleAsInt = twoBytesToInt (
                buffer,
                0);

        // return 0.f;
        return (sampleAsInt) ;
    }

    bool Oscillator::load (std::string fileName)
    {
        LOGD("Load file started");
        const char *cstr = fileName.c_str();
        inputFile.open(cstr, std::ios::binary); // Open the file in binary mode
        //inputFile()=std::ifstream("/storage/emulated/0/Music/orkester.wav", std::ios::binary);
        if (!inputFile.good()) {
            LOGD("Failed to open the file.");
            return false;
        }
        char meta[44];
        inputFile.read(meta,44);
        std::string header(meta,meta+4);
        int32_t fileSizeInBytes = fourBytesToInt (reinterpret_cast<const uint8_t *>(meta), 4) + 8;

        std::string format(meta+8,meta+12);
        std::string fmt(meta+12,meta+16);
        LOGD("header %s",header.c_str());
        LOGD("Bytes: %d",fileSizeInBytes);
        LOGD("Format: %s", format.c_str());
        LOGD("fmt: %s", fmt.c_str());
        int32_t formatdatasize = fourBytesToInt (reinterpret_cast<const uint8_t *>(meta), 16);

        uint16_t audioFormat = twoBytesToInt (reinterpret_cast<const uint8_t *>(meta), 20);//20

        numChannels = twoBytesToInt (reinterpret_cast<const uint8_t *>(meta), 22);//22

        sampleRate = fourBytesToInt (reinterpret_cast<const uint8_t *>(meta), 24);//24

        uint32_t numBytesPerSecond = fourBytesToInt (reinterpret_cast<const uint8_t *>(meta), 28);//28
        uint16_t numBytesPerBlock = twoBytesToInt (reinterpret_cast<const uint8_t *>(meta), 32);//32
        uint16_t bitDepth = twoBytesToInt (reinterpret_cast<const uint8_t *>(meta), 34);//34
        uint16_t bitsPerSample= twoBytesToInt (reinterpret_cast<const uint8_t *>(meta), 36);//36
        std::string dataStr(meta+36,meta+40);
        uint32_t totalAudioLen = fourBytesToInt (reinterpret_cast<const uint8_t *>(meta), 40);//28

        LOGD("formatdatasize: %d",formatdatasize);
        LOGD("audioFormat: %d",audioFormat);
        LOGD("numChannels: %d",numChannels);
        LOGD("sampleRate: %d",sampleRate);
        LOGD("numBytesPerSecond: %d",numBytesPerSecond);
        LOGD("numBytesPerBlock: %d",numBytesPerBlock);
        LOGD("bitDepth: %d",bitDepth);
        LOGD("bitsPerSample: %d",bitsPerSample);
        LOGD("data: %s", dataStr.c_str());
        LOGD("totalAudioLen: %d",totalAudioLen);


        return true;
    }
}
