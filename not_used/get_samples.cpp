
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
/*
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
}*/
return 0;



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