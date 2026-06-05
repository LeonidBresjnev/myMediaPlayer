package com.equalizer.common.metadata

data class M4aMeta(
    override var numChannels: Int=0,
    override var sampleRate: Int,
    var artist: String,
    var name: String,
    var imageArray: ByteArray?,
    var track: Int?
): MusicMetaInterface