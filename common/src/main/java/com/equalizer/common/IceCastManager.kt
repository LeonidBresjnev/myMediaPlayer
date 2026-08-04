package com.equalizer.common

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RadioBrowserStation(
    val name: String,
    val url: String,
    val url_resolved: String? = null,
    val tags: String? = null,
    val bitrate: Int? = null,
    val codec: String? = null,
    val server_type: String? = null
)

data class IceCastStation(
    val name: String,
    val url: String,
    val genre: String?,
    val bitrate: Int,
    val samplerate: Int,
    val channels: Int
)

object IceCastManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
        }
    }

    private val mirrors = listOf(
        "at1.api.radio-browser.info",
        "de1.api.radio-browser.info",
        "fr1.api.radio-browser.info"
    )

    suspend fun fetchStations(): List<IceCastStation> = withContext(Dispatchers.IO) {
        for (mirror in mirrors) {
            try {
                Log.d("IceCastManager", "Attempting to fetch from $mirror")
                val response = client.get("https://$mirror/json/stations/search") {
                    parameter("order", "clickcount")
                    parameter("reverse", "true")
                    parameter("limit", "100")
                    parameter("hidebroken", "true")
                }
                val jsonString = response.bodyAsText()
                val stations: List<RadioBrowserStation> = json.decodeFromString(jsonString)
                
                if (stations.isNotEmpty()) {
                    // Filter for MP3 anywhere in URL or codec
                    val mp3Stations = stations.filter { 
                        it.url.lowercase().contains("mp3") || 
                        it.url_resolved?.lowercase()?.contains("mp3") == true ||
                        it.codec?.lowercase()?.contains("mp3") == true ||
                        it.server_type?.lowercase()?.contains("mp3") == true
                    }
                    
                    Log.d("IceCastManager", "Successfully fetched ${mp3Stations.size} stations from $mirror")
                    return@withContext mp3Stations.map { 
                        IceCastStation(
                            name = it.name,
                            url = it.url_resolved ?: it.url,
                            genre = it.tags?.split(",")?.firstOrNull()?.trim(),
                            bitrate = it.bitrate ?: 128,
                            samplerate = 44100,
                            channels = 2
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("IceCastManager", "Mirror $mirror failed: ${e.message}")
            }
        }
        emptyList()
    }
}
