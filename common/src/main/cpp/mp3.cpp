//
// Created by simon on 22-02-2025.
//

#include "mp3.h"
#include "include/Log.h"
#include <cstdint>

namespace equalizer {


    int mp3_reader2(int fileHandle,std::ifstream *myFile, void *buffer, size_t bufferSize) {
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

    int printinfo2(mp3_info *thisfileinfo) {
        LOGD("Sample rate: %d",thisfileinfo->sample_rate);
        LOGD("Channels: %d",thisfileinfo->channels);
        return MP3_CONTINUE;
    }

    Mp3::Mp3() :
    buffersize{-1} {

    }


    int32_t Mp3::getSampleRate() const {
return this->sampleRate;
    }

    int16_t Mp3::getSample() {
        if (bufferpointer >= buffersize-1) {
            //refill buffer;
            buffersize=mp3_read(id, buffer, BUFSIZE);
            bufferpointer=0;
        }
        int16_t sample= twoBytesToInt(reinterpret_cast<uint8_t *>(buffer), bufferpointer);
        bufferpointer += 1;
        return sample;
    }

    void Mp3::onPlaybackStopped() {
        inputFile.close();
        LOGD("file closed");
    }

    int16_t Mp3::twoBytesToInt (const uint8_t  source[2], int startIndex)
    {
        int16_t result = (source[startIndex + 1] << 8) | source[startIndex];
        return (result);
    }

    bool Mp3::load(const std::string source) {
        inputFile.open(source, std::ios::binary);
        myoptions.info_callback = &printinfo2;
        myoptions.flags = MP3_INFO_ONCE;
        // Open the mp3 file (assuming 'read' is defined elsewhere or replaced with functional logic).
        id = mp3_open(&inputFile,&mp3_reader2, &myoptions); // NULL is replaced with nullptr in C++
        if (id < 0) {
            LOGD("Failed to open MP3 file");
            return false;
        }
        LOGD("opened!");
        buffersize=0;
        bufferpointer=0;
        return true;

    }
}