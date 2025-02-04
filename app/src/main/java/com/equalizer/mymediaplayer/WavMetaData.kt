package com.equalizer.mymediaplayer

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavMetaData: ViewModel() {
    private var type: IntArray = intArrayOf(0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 1)
    private var numberOfBytes: IntArray = intArrayOf(4, 4, 4, 4, 4, 2, 2, 4, 4, 2, 2, 4, 4)
    private var chunkSize: Int = 0
    private var subChunk1Size: Int = 0
    private var _sampleRate = MutableLiveData(0)
    private var byteRate: Int = 0
    private var subChunk2Size: Int = 1
    private var bytePerSample: Int = 0
    private var audioFormat: Short = 0
    private var _numChannels = MutableLiveData(0.toShort())
    private var blockAlign: Short = 0
    private var _bitsPerSample = MutableLiveData(8.toShort())
    private var chunkID: String? = null
    private var format: String? = null
    private var subChunk1ID: String? = null
    private var subChunk2ID: String? = null

    val sampleRate: LiveData<Int>
        get() {
            return _sampleRate
        }
    val numChannels: LiveData<Short>
        get() {
            return _numChannels
        }

    val bitsPerSample: LiveData<Short>
        get() {
            return _bitsPerSample
        }

    fun reset() {
        // type = intArrayOf()
        chunkSize = 0
        subChunk1Size = 0
        _sampleRate.value = 0
        byteRate = 0
        subChunk2Size = 1
        bytePerSample = 0
        _numChannels.value = 0
    }

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

    @Throws(IOException::class)
    fun readingAudioFile(
        file: File,
    )  {

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
                    6 -> _numChannels.value = byteBuffer.getShort()
                    7 -> {
                        Log.d("wavFile", "samplerate set")
                        _sampleRate.value = byteBuffer.getInt()
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
            bytePerSample = 4096 * (_bitsPerSample.value ?:0.toShort()) / 8
            Log.d("wavFile", "Sample rate: ${_sampleRate.value}")
            //fileInputstream.close()
        }

    }
}