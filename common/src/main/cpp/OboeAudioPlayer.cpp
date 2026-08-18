#include "OboeAudioPlayer.h"
#include <utility>
#include "AudioSource.h"
#include "Log.h"

using namespace oboe;

namespace equalizer {
    OboeAudioPlayer::OboeAudioPlayer(
            std::shared_ptr<AudioSource> source) : _source(std::move(source)) {}

    OboeAudioPlayer::~OboeAudioPlayer() {
        OboeAudioPlayer::stop();
    }

    int32_t OboeAudioPlayer::play(int32_t _samplingRate,uint16_t channelCount_, int32_t deviceId) {
        // Create an AudioStream using the Oboe's builder
        AudioStreamBuilder builder;
        const auto result =
                builder.setPerformanceMode(PerformanceMode::LowLatency)
                        ->setDirection(Direction::Output)
                        ->setSampleRate(_samplingRate)
                        ->setDataCallback(this)
                        ->setSharingMode(SharingMode::Shared)
                        ->setFormat( AudioFormat::Float )
                        ->setDeviceId(deviceId)
                        ->setContentType(ContentType::Music)
                        ->setUsage(Usage::Media)
                        ->setChannelCount(channelCount_)
                        ->setSampleRateConversionQuality(SampleRateConversionQuality::Medium)
                        ->openStream(_stream);
        this->channelCount=channelCount_;

        if (result != Result::OK) {
            LOGD("Stream creation failed: %s", convertToText(result));
            return static_cast<int32_t>(result);
        }

        // request a playback start
        const auto playResult = _stream->requestStart();
        if (playResult != Result::OK) {
            LOGD("Playback start failed: %s", convertToText(playResult));
        }

        return static_cast<int32_t>(playResult);
    }

    void OboeAudioPlayer::stop() {
        // if there is an active stream, stop, close, and destroy it
        if (_stream) {
            _stream->stop();
            _stream->close();
            _stream.reset();
        }
    }

    DataCallbackResult
    OboeAudioPlayer::onAudioReady(oboe::AudioStream* audioStream,
                                  void* audioData,
                                  int32_t framesCount) {
        // we requested floating-point processing, thus, we treat the given
        // memory block as an array of floats
        // WARNING: the sample format may differ from the requested one.
        // Please, refer to Oboe's documentation for details.
        auto* floatData = reinterpret_cast<float *>(audioData);

        // Let's fill the array with samples.
        // This code works for any number of interleaved channels
        // and any number of frames.
        for (auto frame = 0; frame < framesCount; ++frame) {
            // retrieve a sample from the AudioSource
            // (in our case, it's a WavetableOscillator)
            // copy the samples to all channels of this frame
            for (auto channel = 0; channel < channelCount; ++channel) {
                //const auto sample = _source->getSample();
                auto sample = _source->getSample();
                //LOGD("oboe sample: %f", sample);
                floatData[frame * channelCount + channel] = sample;
            }
        }
        // indicate to the Oboe library that the playback should continue
        return oboe::DataCallbackResult::Continue;
    }
}