#pragma once

#include <cmath>
#include "AudioSource.h"
#include "mp32pcm.h"
#include <atomic>
#include <functional>


#include <fstream>
//#define BUFSIZE (1 * MP3_MIN_BUFFER)
#define BUFSIZE (16*MP3_MIN_BUFFER)
namespace equalizer {
    enum class AudioFormat {
        UNKNOWN,
        WAV,
        MP3
    };

    class Oscillator : public AudioSource {
    public:
        explicit Oscillator();

        int16_t getSample() override;
        void onPlaybackStopped() override;


        int32_t getSampleRate() const;
        uint16_t getChannelCount() const;

        bool load(const std::string& );
        std::shared_ptr<std::ifstream> pmyfile;

        static bool endsWithWavCaseInsensitive(std::string str, std::string suffix) {
            std::transform(str.begin(), str.end(), str.begin(), ::tolower);
            if (str.length() >= 3) {
                return str.substr(str.length() - 3) == suffix;
            }
            return false;
        }

    private:

        static int32_t fourBytesToInt (const uint8_t source[4], int startIndex );
        static int16_t twoBytesToInt (const uint8_t source[2], int );
        std::ifstream inputFile;
        uint16_t numChannels=2;
        int32_t sampleRate=0;
        int buffersize;
        int bufferpointer;
        uint8_t buffer[1024];
        AudioFormat format;


        int id;
        mp3_options myoptions;
        mp3_sample mymp3buffer[BUFSIZE];
        mp3_info myinfo;
    };

}