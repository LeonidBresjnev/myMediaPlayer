package com.equalizer.common.metadata

data class WavMeta(
    override var numChannels: Int,
    override var sampleRate: Int,
    var audioFormat: Short,
    var byteRate: Int): MusicMetaInterface