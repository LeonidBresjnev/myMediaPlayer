
#include "Oscillator.h"
#include "Log.h"


#include <fstream>

namespace equalizer {
    uint16_t _numChannels_=2;
    int32_t _sampleRate_=0;

    int printinfo(mp3_info *thisfileinfo) {
        LOGD("Sample rate: %d",thisfileinfo->sample_rate);
        LOGD("Channels: %d",thisfileinfo->channels);
        LOGD("bit_per_sample: %d",thisfileinfo->bit_per_sample);
        LOGD("bit_rate: %d",thisfileinfo->bit_rate);
        LOGD("frame_size: %d",thisfileinfo->frame_size);

        _sampleRate_=static_cast<int32_t>(thisfileinfo->sample_rate);
        _numChannels_=static_cast<uint16_t>( thisfileinfo->channels);
        return MP3_CONTINUE;
    }

    int mp3_reader(int fileHandle,std::ifstream *myFile, void *buffer, size_t bufferSize) {
        // std::cout<<i++ <<std::endl;
        if (!(*myFile) || !(*myFile).is_open()) {
            //std::cout<< "Error: Failed to read from the file, file is not open." << std::endl;
            return 0;
        }

        // Read from the file into the buffer
        (*myFile).read(static_cast<char *>(buffer), bufferSize);
        auto bytesRead = (*myFile).gcount();
        // Return the number of bytes read
        //  std::cout<<"bytes read: "<<bytesRead<<std::endl;
        return static_cast<int>(bytesRead);
    }

    Oscillator::Oscillator() = default;

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

    int16_t Oscillator::twoBytesToInt2 (const int  source[2],
                                       int startIndex)
    {
        int16_t result = (source[startIndex + 1] << 8) | source[startIndex];
        return (result);
    }


    void Oscillator::onPlaybackStopped() {
        inputFile.close();
        LOGD("file closed");
        if (this->format == AudioFormat::MP3) {
            mp3_close(id);
        }
    }

    int32_t Oscillator::getSampleRate() const {
        return this->sampleRate;
    }
    uint16_t Oscillator::getChannelCount() const {
        return this->numChannels;
    }

    int16_t Oscillator::getSample() {
    if (this->format == AudioFormat::WAV) {
        if (bufferpointer >= buffersize-1) {
            //refill buffer;
            inputFile.read(reinterpret_cast<char *>(buffer), 1024);
            buffersize=static_cast<int>(inputFile.gcount());
            bufferpointer=0;
        }

        int16_t sample= twoBytesToInt(buffer,bufferpointer);
        bufferpointer += 2;
        return sample;
        /*
        uint8_t buffer[2];
        inputFile.read(reinterpret_cast<char *>(buffer), 2);
        int16_t sampleAsInt = twoBytesToInt (
                buffer,
                0);
        // return 0.f;
        return (sampleAsInt) ;*/
    }
    else if (this->format == AudioFormat::MP3) {
            if (bufferpointer >= buffersize) {
                //refill buffer;
                //LOGD("refilling buffer");
                buffersize=mp3_read(id, mymp3buffer, BUFSIZE);
                bufferpointer=0;
            }
           // int16_t sample= twoBytesToInt2(reinterpret_cast<int *>(mymp3buffer), bufferpointer);
            auto sample= mymp3buffer[bufferpointer];
            bufferpointer +=1;
            return static_cast<short> (sample);
    }
    return 0;
    }
    bool Oscillator::load (std::string fileName)
    {
        LOGD("Load file started");
        inputFile.open(fileName, std::ios::binary); // Open the file in binary mode
        //inputFile()=std::ifstream("/storage/emulated/0/Music/orkester.wav", std::ios::binary);
        if (!inputFile.good()) {
            LOGD("Failed to open the file.");
            return false;
        }

        if (endsWithWavCaseInsensitive(fileName, std::string("wav"))) {
            char meta[44];
            inputFile.read(meta, 44);
            std::string header(meta, meta + 4);
            int32_t fileSizeInBytes =
                    fourBytesToInt(reinterpret_cast<const uint8_t *>(meta), 4) + 8;

            std::string format(meta + 8, meta + 12);
            std::string fmt(meta + 12, meta + 16);
            LOGD("header %s", header.c_str());
            LOGD("Bytes: %d", fileSizeInBytes);
            LOGD("Format: %s", format.c_str());
            LOGD("fmt: %s", fmt.c_str());
            int32_t formatdatasize = fourBytesToInt(reinterpret_cast<const uint8_t *>(meta), 16);

            uint16_t audioFormat = twoBytesToInt(reinterpret_cast<const uint8_t *>(meta), 20);//20

            numChannels = twoBytesToInt(reinterpret_cast<const uint8_t *>(meta), 22);//22

            sampleRate = fourBytesToInt(reinterpret_cast<const uint8_t *>(meta), 24);//24

            uint32_t numBytesPerSecond = fourBytesToInt(reinterpret_cast<const uint8_t *>(meta),
                                                        28);//28
            uint16_t numBytesPerBlock = twoBytesToInt(reinterpret_cast<const uint8_t *>(meta),
                                                      32);//32
            uint16_t bitDepth = twoBytesToInt(reinterpret_cast<const uint8_t *>(meta), 34);//34
            uint16_t bitsPerSample = twoBytesToInt(reinterpret_cast<const uint8_t *>(meta), 36);//36
            std::string dataStr(meta + 36, meta + 40);
            uint32_t totalAudioLen = fourBytesToInt(reinterpret_cast<const uint8_t *>(meta),
                                                    40);//28

            LOGD("formatdatasize: %d", formatdatasize);
            LOGD("audioFormat: %d", audioFormat);
            LOGD("numChannels: %d", numChannels);
            LOGD("sampleRate: %d", sampleRate);
            LOGD("numBytesPerSecond: %d", numBytesPerSecond);
            LOGD("numBytesPerBlock: %d", numBytesPerBlock);
            LOGD("bitDepth: %d", bitDepth);
            LOGD("bitsPerSample: %d", bitsPerSample);
            LOGD("data: %s", dataStr.c_str());
            LOGD("totalAudioLen: %d", totalAudioLen);

            bufferpointer=-1;
            buffersize=0;
            this->format = AudioFormat::WAV;
            return true;
        }
        else if (endsWithWavCaseInsensitive(fileName, std::string("mp3"))) {
            LOGD("mp3 file will be loaded later");
            myoptions.info_callback = &printinfo;
            myoptions.flags = MP3_INFO_ONCE  | MP3_STEREO | MP3_SYNC_1;
            // Open the mp3 file (assuming 'read' is defined elsewhere or replaced with functional logic).
            id = mp3_open(&inputFile,&mp3_reader, &myoptions); // NULL is replaced with nullptr in C++
            if (id < 0) {
                LOGD("Failed to open MP3 file");
                return false;
            }
            LOGD("opened!");

            //refill buffer;
            buffersize=mp3_read(id, mymp3buffer, BUFSIZE);
                bufferpointer=0;
                LOGD("buffersize: %d", buffersize);

            LOGD("mp3channels: %d", _numChannels_);
            LOGD("mp3samplerate: %d", _sampleRate_);
            numChannels=2;
            sampleRate=44100;
            this->format = AudioFormat::MP3;
            return true;
        }
        return false;
    }
}
