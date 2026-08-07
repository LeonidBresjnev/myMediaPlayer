package com.equalizer.common

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.discard
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class IcyMetadataTracker(
    private val scope: CoroutineScope,
    private val onMetadataChanged: (String) -> Unit
) {
    private var job: Job? = null
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 60000
        }
    }

    fun startTracking(url: String) {
        stopTracking()
        job = scope.launch(Dispatchers.IO) {
            var retryCount = 0
            while (isActive) {
                try {
                    client.prepareGet(url) {
                        header("Icy-MetaData", "1")
                        header("User-Agent", "Mozilla/5.0")
                        header("Connection", "keep-alive")
                    }.execute { response ->
                        val metaint =
                            response.headers["icy-metaint"]?.toIntOrNull() ?: return@execute

                        val channel: ByteReadChannel = response.bodyAsChannel()
                        var lastTitle = ""
                        retryCount = 0 
                        
                        while (isActive && !channel.isClosedForRead) {
                            channel.discard(metaint.toLong())
                            
                            val lengthByte = channel.readByte().toInt() and 0xFF
                            if (lengthByte > 0) {
                                val metadataLength = lengthByte * 16
                                val buffer = ByteArray(metadataLength)
                                channel.readFully(buffer)
                                
                                val metadataString = String(buffer, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                                if (metadataString.contains("StreamTitle='")) {
                                    val title = metadataString
                                        .substringAfter("StreamTitle='")
                                        .substringBefore("';")
                                        .trim()
                                    
                                    if (title.isNotBlank() && title != lastTitle) {
                                        lastTitle = title
                                        onMetadataChanged(title)

                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("IcyTracker", "Connection error for $url: ${e.message}")
                }
                
                retryCount++
                val waitTime = (retryCount * 2000L).coerceAtMost(30000L)
                delay(waitTime.milliseconds)
            }
        }
    }

    fun stopTracking() {
        job?.cancel()
        job = null
    }
}
