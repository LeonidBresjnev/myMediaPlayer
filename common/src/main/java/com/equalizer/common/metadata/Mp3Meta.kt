package com.equalizer.common.metadata

data class Mp3Meta(
    override var numChannels: Int,
    override var sampleRate: Int,
    var artist: String,
    var name: String,
    var imageArray: ByteArray?,
    var track: Int?
): MusicMetaInterface