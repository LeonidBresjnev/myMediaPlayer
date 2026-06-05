package com.equalizer.common

import android.util.Log
import com.mpatric.mp3agic.Mp3File
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder



class MusicMeta(file: File) {
    private var type: IntArray = intArrayOf(0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 1)
    private var numberOfBytes: IntArray = intArrayOf(4, 4, 4, 4, 4, 2, 2, 4, 4, 2, 2, 4, 4)
    private var chunkSize: Int = 0
    private var subChunk1Size: Int = 0
    internal var _sampleRate = 0
    internal var byteRate: Int = 0
    private var subChunk2Size: Int = 1
    private var bytePerSample: Int = 0
    internal var audioFormat: Short = 0
    internal var _numChannels = 0.toShort()
    private var blockAlign: Short = 0
    private var _bitsPerSample = 8.toShort()
    private var chunkID: String? = null
    private var format: String? = null
    private var subChunk1ID: String? = null
    private var subChunk2ID: String? = null
    internal var _name = ""
    internal var _imageArray : ByteArray? = null
    internal var _artist=""
    internal var track : Int? = null




    private fun byteArrayToNumber(bytes: ByteArray?, numOfBytes: Int, type: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(numOfBytes)
        if (type == 0) {
            buffer.order(ByteOrder.BIG_ENDIAN) // Check the illustration. If it says little endian, use LITTLE_ENDIAN
        } else {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
        }
        bytes?.let { buffer.put(it) }
        buffer.rewind()
        return buffer
    }

    init  {

        if (file.name.endsWith(suffix="wav",ignoreCase = true)) {
            _name=file.name
            _imageArray = byteArrayOf()
            _artist = ""
            FileInputStream(file).use { fileInputstream ->
                var byteBuffer: ByteBuffer
                for (i in numberOfBytes.indices) {
                    val byteArray = ByteArray(numberOfBytes[i])
                    fileInputstream.read(byteArray, 0, numberOfBytes[i])
                    byteBuffer = byteArrayToNumber(byteArray, numberOfBytes[i], type[i])
                    when (i) {
                        0 -> chunkID = String(byteArray)
                        1 -> chunkSize = byteBuffer.getInt()
                        2 -> format = String(byteArray)
                        3 -> subChunk1ID = String(byteArray)
                        4 -> subChunk1Size = byteBuffer.getInt()
                        5 -> audioFormat = byteBuffer.getShort()
                        6 -> _numChannels = byteBuffer.getShort()
                        7 -> {
                            Log.d("wavFile", "samplerate set")
                            _sampleRate = byteBuffer.getInt()
                        }

                        8 -> byteRate = byteBuffer.getInt()
                        9 -> blockAlign = byteBuffer.getShort()
                        10 -> { //_bitsPerSample.value =  byteBuffer.getShort()
                        }

                        11 -> {
                            subChunk2ID = String(byteArray)
                            if (subChunk2ID!!.compareTo("data") == 0) {
                                continue
                            } else if (subChunk2ID!!.compareTo("LIST") == 0) {
                                val byteArray2 = ByteArray(4)
                                fileInputstream.read(byteArray2, 0, 4)
                                byteBuffer = byteArrayToNumber(byteArray2, 4, 1)
                                val temp = byteBuffer.getInt()
                                //redundant data reading
                                val byteArray3 = ByteArray(temp)
                                fileInputstream.read(byteArray3, 0, temp)
                                fileInputstream.read(byteArray2, 0, 4)
                                subChunk2ID = String(byteArray2)
                            }
                        }

                        12 -> subChunk2Size = byteBuffer.getInt()
                    }
                }
                bytePerSample = 4096 * _bitsPerSample / 8
                Log.d("wavFile", "Sample rate: $_sampleRate")
                //fileInputstream.close()
            }
        }
        else if (file.name.endsWith(suffix="mp3",ignoreCase = true)) {
            Mp3File(file).apply {
                _sampleRate = sampleRate
                _bitsPerSample = bitrate.toShort()
                _numChannels = 0.toShort()
                _name = id3v2Tag?.title ?: id3v1Tag?.title ?: file.name
                _numChannels = if (channelMode.contains("stereo",ignoreCase = true)) 2 else 1
                _imageArray = id3v2Tag?.albumImage ?: byteArrayOf()
                _artist=(id3v2Tag?.artist)?:(id3v2Tag?.albumArtist)?:(id3v1Tag?.artist)?:""
                track= id3v2Tag?.track?.toInt() ?: id3v1Tag?.track?.toInt()
            }
        }

    }
}