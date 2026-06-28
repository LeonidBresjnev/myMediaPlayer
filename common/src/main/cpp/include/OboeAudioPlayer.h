#pragma once

#include <oboe/Oboe.h>
#include "AudioPlayer.h"

namespace equalizer {
    class AudioSource;

    class OboeAudioPlayer : public oboe::AudioStreamDataCallback,
                            public AudioPlayer {

    public:
        int channelCount = oboe::ChannelCount::Mono;

        explicit OboeAudioPlayer(std::shared_ptr<AudioSource> source);

        ~OboeAudioPlayer();

        int32_t play(int32_t ,uint16_t, int32_t deviceId = 0) override;

        void stop() override;

        oboe::DataCallbackResult onAudioReady(oboe::AudioStream* audioStream,
                                              void* audioData,
                                              int32_t framesCount) override;


    private:
        std::shared_ptr<AudioSource> _source;
        std::shared_ptr<oboe::AudioStream> _stream;
    };
}

