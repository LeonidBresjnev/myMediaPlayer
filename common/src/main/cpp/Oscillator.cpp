
#include "Oscillator.h"
#include "Log.h"
#include <juce_audio_formats/juce_audio_formats.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <jni.h>

extern JavaVM* g_JavaVM;

namespace equalizer {

    class Oscillator::StreamDecoder : public juce::Thread {
    public:
        Oscillator* parent;
        std::string url;

        StreamDecoder(Oscillator* p, const std::string& u)
            : Thread("StreamDecoder"), parent(p), url(u) {}

        ~StreamDecoder() override {
            stopThread(3000);
        }

        void run() override {
            LOGD("StreamDecoder: Starting for %s", url.c_str());

            // Lower priority to ensure UI remains responsive
            setPriority(Priority::low);

            JNIEnv* env = nullptr;
            bool attached = false;
            if (g_JavaVM->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
                if (g_JavaVM->AttachCurrentThread(&env, nullptr) == 0) {
                    attached = true;
                }
            }

            AMediaExtractor* extractor = AMediaExtractor_new();
            media_status_t status = AMediaExtractor_setDataSource(extractor, url.c_str());
            if (status != AMEDIA_OK) {
                LOGD("StreamDecoder: AMediaExtractor failed, error=%d", status);
                AMediaExtractor_delete(extractor);
                if (attached) g_JavaVM->DetachCurrentThread();
                return;
            }

            int trackIdx = -1;
            int numTracks = AMediaExtractor_getTrackCount(extractor);
            AMediaFormat* format = nullptr;
            for (int i = 0; i < numTracks; ++i) {
                format = AMediaExtractor_getTrackFormat(extractor, i);
                const char* mime;
                if (AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
                    if (strncmp(mime, "audio/", 6) == 0) {
                        trackIdx = i;
                        break;
                    }
                }
                AMediaFormat_delete(format);
                format = nullptr;
            }

            if (trackIdx < 0) {
                LOGD("StreamDecoder: No audio track found");
                AMediaExtractor_delete(extractor);
                if (attached) g_JavaVM->DetachCurrentThread();
                return;
            }

            AMediaExtractor_selectTrack(extractor, trackIdx);

            const char* mime;
            AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime);
            AMediaCodec* codec = AMediaCodec_createDecoderByType(mime);
            if (AMediaCodec_configure(codec, format, nullptr, nullptr, 0) != AMEDIA_OK) {
                LOGD("StreamDecoder: Codec config failed");
                AMediaCodec_delete(codec);
                AMediaFormat_delete(format);
                AMediaExtractor_delete(extractor);
                if (attached) g_JavaVM->DetachCurrentThread();
                return;
            }

            AMediaCodec_start(codec);
            LOGD("StreamDecoder: Decoder started");

            bool sawInputEOS = false;
            bool sawOutputEOS = false;
            int64_t lastSampleTime = 0;

            while (!threadShouldExit() && !sawOutputEOS) {
                // Throttling: If buffer has more than 8 seconds of audio, wait.
                // This prevents high CPU usage and lock contention.
                if (parent->getAvailableSamples() > 44100 * 2 * 8) {
                    juce::Thread::sleep(200);
                    continue;
                }

                if (!sawInputEOS) {
                    ssize_t inIdx = AMediaCodec_dequeueInputBuffer(codec, 2000);
                    if (inIdx >= 0) {
                        size_t inSize;
                        uint8_t* inBuf = AMediaCodec_getInputBuffer(codec, inIdx, &inSize);
                        ssize_t sampleSize = AMediaExtractor_readSampleData(extractor, inBuf, inSize);

                        if (sampleSize < 0) {
                            sawInputEOS = true;
                            sampleSize = 0;
                        }

                        int64_t timeUs = AMediaExtractor_getSampleTime(extractor);
                        if (timeUs < 0) timeUs = lastSampleTime + 20000;
                        lastSampleTime = timeUs;

                        AMediaCodec_queueInputBuffer(codec, inIdx, 0, sampleSize, timeUs,
                                                    sawInputEOS ? AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM : 0);

                        if (!sawInputEOS) AMediaExtractor_advance(extractor);
                    }
                }

                AMediaCodecBufferInfo info;
                ssize_t outIdx = AMediaCodec_dequeueOutputBuffer(codec, &info, 2000);
                if (outIdx >= 0) {
                    if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) sawOutputEOS = true;

                    size_t outSize;
                    uint8_t* outBuf = AMediaCodec_getOutputBuffer(codec, outIdx, &outSize);
                    AMediaFormat* outFormat = AMediaCodec_getOutputFormat(codec);

                    int32_t outSR = 44100, outCh = 2;
                    AMediaFormat_getInt32(outFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &outSR);
                    AMediaFormat_getInt32(outFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &outCh);
                    AMediaFormat_delete(outFormat);

                    if (outCh > 0 && info.size > 0) {
                        int numSamplesInBuf = info.size / sizeof(int16_t);
                        auto* pcm = reinterpret_cast<int16_t*>(outBuf + info.offset);

                        std::vector<float> samples(numSamplesInBuf);
                        for (int i = 0; i < numSamplesInBuf; ++i) {
                            samples[i] = static_cast<float>(pcm[i]) / 32768.0f;
                        }

                        parent->pushPCMData(samples.data(), (int)samples.size(), outSR, outCh);
                    }

                    AMediaCodec_releaseOutputBuffer(codec, outIdx, false);
                } else if (outIdx == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                    juce::Thread::sleep(10);
                }
            }

            AMediaCodec_stop(codec);
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            AMediaExtractor_delete(extractor);
            if (attached) g_JavaVM->DetachCurrentThread();
            LOGD("StreamDecoder: Finished");
        }
    };

    Oscillator::Oscillator() {
        formatManager.registerBasicFormats();
    }

    Oscillator::~Oscillator() {
        onPlaybackStopped();
    }

    void Oscillator::onPlaybackStopped() {
        LOGD("Oscillator: onPlaybackStopped");
        // Don't hold _readerMutex while waiting for thread to stop
        auto decoder = std::move(_streamDecoder);
        if (decoder) decoder.reset();

        std::lock_guard<std::mutex> lock(_readerMutex);
        if (readerSource != nullptr) readerSource->releaseResources();
        readerSource.reset();
        reader.reset();
    }

    int32_t Oscillator::getSampleRate() const { return this->sampleRate; }
    uint16_t Oscillator::getChannelCount() const { return this->numChannels; }
    int64_t Oscillator::getLengthInSamples() const {
        std::lock_guard<std::mutex> lock(_readerMutex);
        if (_isStream) return 3600LL * 24LL * sampleRate;
        return reader ? reader->lengthInSamples : 0;
    }
    int64_t Oscillator::getCurrentPositionInSamples() const {
        std::lock_guard<std::mutex> lock(_readerMutex);
        return currentposition;
    }

    float Oscillator::getSample() {
        if (_isStream) {
            std::lock_guard<std::mutex> lock(streamMutex);
            if (streamAvailable > 0) {
                float sample = streamBuffer[streamReadPos];
                streamReadPos = (streamReadPos + 1) % streamBuffer.size();
                streamAvailable--;
                return sample;
            }
            return 0.0f;
        }

        std::lock_guard<std::mutex> lock(_readerMutex);
        if (!readerSource) return 0.0f;

        if (bufferpointer >= 1024) {
            juce::AudioSourceChannelInfo info(&floatBuffer, 0, 1024);
            readerSource->getNextAudioBlock(info);
            bufferpointer = 0;
            currentposition += 1024;
        }

        auto sample = floatBuffer.getReadPointer(currentchannel)[bufferpointer];
        if (++currentchannel >= numChannels) {
            currentchannel = 0;
            bufferpointer++;
        }
        return sample;
    }

    int Oscillator::getAvailableSamples() const {
        std::lock_guard<std::mutex> lock(streamMutex);
        return (int)streamAvailable;
    }

    void Oscillator::pushPCMData(const float* data, int numSamples, int streamSR, int streamChannels) {
        std::lock_guard<std::mutex> lock(streamMutex);
        if (streamBuffer.empty()) return;

        // Optimized push: handle mono to stereo or direct copy
        if (streamChannels == 1 && numChannels == 2) {
            for (int i = 0; i < numSamples; ++i) {
                float s = data[i];
                // Push twice for stereo
                for (int c = 0; c < 2; ++c) {
                    streamBuffer[streamWritePos] = s;
                    streamWritePos = (streamWritePos + 1) % streamBuffer.size();
                    if (streamAvailable < streamBuffer.size()) streamAvailable++;
                    else streamReadPos = (streamReadPos + 1) % streamBuffer.size();
                }
            }
        } else {
            for (int i = 0; i < numSamples; ++i) {
                streamBuffer[streamWritePos] = data[i];
                streamWritePos = (streamWritePos + 1) % streamBuffer.size();
                if (streamAvailable < streamBuffer.size()) streamAvailable++;
                else streamReadPos = (streamReadPos + 1) % streamBuffer.size();
            }
        }
    }

    bool Oscillator::load (const std::string& fileName, int32_t sampleRateFallback, int32_t channelsFallback)
    {
        onPlaybackStopped();
        bool isStream = (fileName.find("http://") == 0 || fileName.find("https://") == 0);

        if (isStream) {
            std::lock_guard<std::mutex> lock(_readerMutex);
            _isStream = true;
            sampleRate = sampleRateFallback;
            numChannels = static_cast<uint16_t>(channelsFallback);
            currentposition = 0;
            {
                std::lock_guard<std::mutex> lock2(streamMutex);
                streamBuffer.clear();
                streamBuffer.resize(sampleRate * numChannels * 20); // 20s
                streamReadPos = 0;
                streamWritePos = 0;
                streamAvailable = 0;
            }

            _streamDecoder = std::make_unique<StreamDecoder>(this, fileName);
            _streamDecoder->startThread();
            return true;
        } else {
            juce::File mp3File(fileName);
            if (!mp3File.exists()) return false;

            auto newReader = std::unique_ptr<juce::AudioFormatReader>(formatManager.createReaderFor(mp3File));
            if (newReader) {
                std::lock_guard<std::mutex> lock(_readerMutex);
                reader = std::move(newReader);
                numChannels = static_cast<uint16_t>(reader->numChannels);
                sampleRate = static_cast<int32_t>(reader->sampleRate);
                _isStream = false;

                readerSource = std::make_unique<juce::AudioFormatReaderSource>(reader.get(), false);
                readerSource->prepareToPlay(1024, sampleRate);

                floatBuffer = juce::AudioBuffer<float>(numChannels, 1024);
                bufferpointer = 1024;
                currentposition = 0;
                currentchannel = 0;
                return true;
            }
        }
        return false;
    }

    void Oscillator::seekTo(double positionSeconds) {
        std::lock_guard<std::mutex> lock(_readerMutex);
        if (!readerSource || _isStream) return;
        auto targetSample = static_cast<int64_t>(positionSeconds * sampleRate);
        readerSource->setNextReadPosition(targetSample);
        currentposition = targetSample;
        bufferpointer = 1024;
        currentchannel = 0;
    }

}
