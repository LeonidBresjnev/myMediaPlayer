/*
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

            //numChannels = twoBytesToInt(reinterpret_cast<const uint8_t *>(meta), 22);//22

            //sampleRate = fourBytesToInt(reinterpret_cast<const uint8_t *>(meta), 24);//24

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
            pmyfile = std::make_shared<std::ifstream>(fileName, std::ios::binary);
            id = mp3_open(pmyfile,&mp3_reader, &myoptions); // NULL is replaced with nullptr in C++
            if (id < 0) {
                LOGD("Failed to open MP3 file");
                return false;
            }
            LOGD("opened!");

            //refill buffer;
            buffersize=mp3_read(id, mymp3buffer, BUFSIZE);
                bufferpointer=0;
                LOGD("buffersize: %d", buffersize);


            this->format = AudioFormat::MP3;

            //JUCE;



            return true;
        }*/