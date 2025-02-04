#include "Oscillator.h"
#include "Log.h"
#include "FilePlayer.h"

#include <fstream>

namespace equalizer {
    int32_t FilePlayer::fourBytesToInt (const uint8_t source[4],
                                        int startIndex)
    {
        int32_t result = (source[startIndex + 3] << 24) | (source[startIndex + 2] << 16) | (source[startIndex + 1] << 8) | source[startIndex];
        return result;

    }

    int16_t FilePlayer::twoBytesToInt (const uint8_t  source[2],
            int startIndex)
    {
        int16_t result = (source[startIndex + 1] << 8) | source[startIndex];
        return (result);
    }


    //=============================================================
    FilePlayer::FilePlayer () = default;

    int32_t FilePlayer::getSampleRate() const {
        return this->sampleRate;
    }

    uint16_t FilePlayer::getChannelCount() const {
        return this->numChannels;
    }

    int16_t FilePlayer::getSample() {
        uint8_t buffer[2];
        inputFile.read(reinterpret_cast<char *>(buffer), 2);
        int16_t sampleAsInt = twoBytesToInt (
                buffer,
                0);

       // return 0.f;
        return (sampleAsInt) ;
    }

    void FilePlayer::onPlaybackStopped() {
        inputFile.close();
    }

    bool FilePlayer::load (std::string fileName)
    {
        const char *cstr = fileName.c_str();
        LOGD("File to play: %s", cstr);
        LOGD("Load file started");
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

        /*
        if (myFile == NULL) {
            LOGD("Error opening filexxxx!");
            return 1;
        }

        fseek(myFile, offset, SEEK_SET);

        int fd = fileno(myFile);
        std::ifstream inputFile;
        inputFile.open(fd, std::ios::binary);

        char header[4];
        //char format[20];
        size_t bytesRead = fread(header, 1, 4, myFile);
        //bytesRead = fread(format, 5, 12, myFile);*/
        //fread
        //LOGD("Format: %s",std::string(header,header+4).c_str());

       // int fd = fileno(myFile);



        /*
        std::ifstream file (filePath, std::ios::binary);

        // check the file exists
        if (! file.good())
        {
            reportError ("ERROR: File doesn't exist or otherwise can't load file\n"  + filePath);
            return false;
        } else {
            LOGD("file is good");
        }

        std::vector<uint8_t> fileData;

        file.unsetf (std::ios::skipws);

        file.seekg (0, std::ios::end);
        size_t length = file.tellg();
        file.seekg (0, std::ios::beg);

        // allocate
        fileData.resize (length);

        file.read(reinterpret_cast<char*> (fileData.data()), length);
        file.close();

        if (file.gcount() != length)
        {
            LOGD ("ERROR: Couldn't read entire file\n%s",filePath.c_str());
            return false;
        } else {
            LOGD("entire file read.");
        }

        // Handle very small files that will break our attempt to read the
        // first header info from them
        if (fileData.size() < 12)
        {
            LOGD ("ERROR: File is not a valid audio file\n%s",filePath.c_str());
            return false;
        }
        else
        {
            return loadFromMemory (fileData);
        }*/
       return true;
    }


/*
    void FilePlayer::printSummary() const
    {
        LOGD("|======================================|");
        LOGD("Num Channels: %d", getNumChannels());
        LOGD("Num Samples Per Channel: %d",getNumSamplesPerChannel());
        LOGD("Sample Rate: %d",sampleRate);
        LOGD("Bit Depth: %d", bitDepth);
        LOGD("Length in Seconds: %d", getLengthInSeconds());
        LOGD("|======================================|");
    }*/

}
