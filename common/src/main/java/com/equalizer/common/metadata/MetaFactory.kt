package com.equalizer.common.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.mpatric.mp3agic.Mp3File
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MetaFactory {
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

    fun createMeta(file: File, context: Context): MusicMetaInterface? {

        if (file.name.endsWith(suffix = "wav", ignoreCase = true)) {
            var numberOfBytes: IntArray = intArrayOf(4, 4, 4, 4, 4, 2, 2, 4, 4, 2, 2, 4, 4)
            var type: IntArray = intArrayOf(0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 1)
            var chunkSize = 0
            var subChunk1Size = 0
            var subChunk2Size = 1
            var chunkID: String? = null
            var format: String? = null
            var subChunk1ID: String? = null
            var subChunk2ID: String? = null
            var audioFormat: Short = 0
            var _numChannels = 0.toShort()
            var _sampleRate = 0
            var byteRate = 0
            var blockAlign: Short = 0
            var bytePerSample = 0
            var _bitsPerSample = 8.toShort()

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
                            if (subChunk2ID.compareTo("data") == 0) {
                                continue
                            } else if (subChunk2ID.compareTo("LIST") == 0) {
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
            }
            return WavMeta(
                audioFormat = audioFormat,
                numChannels = _numChannels.toInt(),
                sampleRate = _sampleRate,
                byteRate = byteRate

            )
        } else if (file.name.endsWith(suffix = "mp3", ignoreCase = true)) {
            return Mp3File(file).run {
                Mp3Meta(
                    sampleRate = sampleRate,
                    name = id3v2Tag?.title ?: id3v1Tag?.title ?: file.name,
                    numChannels = if (channelMode.contains("stereo", ignoreCase = true)) 2 else 1,
                    imageArray = id3v2Tag?.albumImage ?: byteArrayOf(),
                    artist = (id3v2Tag?.artist) ?: (id3v2Tag?.albumArtist) ?: (id3v1Tag?.artist)
                    ?: "",
                    track = id3v2Tag?.track?.toInt() ?: id3v1Tag?.track?.toInt()
                )
            }
        } else if (file.name.endsWith(suffix = "m4a", ignoreCase = true)) {

            val metadataRetriever = MediaMetadataRetriever()
            metadataRetriever.setDataSource(context, Uri.fromFile(file))


            // Extract common metadata
            val name =
                metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            val artist =
                metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album =
                metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val sampleRate =
                metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toInt() ?: 0
            val imagearray = metadataRetriever.embeddedPicture
            val track = -1
//                metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) ?.toInt() ?: 0

            metadataRetriever.release()

            Log.d("m4aFile", "m4aFile")
            Log.d("m4aFile", name)
            Log.d("m4aFile", artist)
            Log.d("m4aFile", album)
            Log.d("m4aFile", sampleRate.toString())

            return M4aMeta(
                sampleRate = sampleRate,
                name = name,
                imageArray = imagearray,
                artist = artist,
                track = track
            )

        }
        return null
    }
}