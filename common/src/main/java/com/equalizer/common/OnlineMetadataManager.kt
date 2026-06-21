package com.equalizer.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class MusicBrainzSearchResponse(
    val releases: List<MusicBrainzRelease> = emptyList()
)

@Serializable
data class MusicBrainzRelease(
    val id: String,
    val title: String,
    @SerialName("release-group") val releaseGroup: MusicBrainzReleaseGroup? = null,
    val date: String? = null,
    @SerialName("label-info") val labelInfo: List<MusicBrainzLabelInfo>? = null
)

@Serializable
data class MusicBrainzReleaseGroup(
    val id: String,
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
    val genres: List<MusicBrainzGenre>? = null
)

@Serializable
data class MusicBrainzLabelInfo(
    val label: MusicBrainzLabel? = null
)

@Serializable
data class MusicBrainzLabel(
    val name: String? = null
)

@Serializable
data class MusicBrainzGenre(
    val name: String
)

data class OnlineInfo(
    val releaseDate: String? = null,
    val label: String? = null,
    val genres: List<String>? = null,
    val artworkUrl: String? = null
)

object OnlineMetadataManager {
    private const val TAG = "OnlineMetadataManager"
    private const val USER_AGENT = "MyMediaPlayer/1.0 ( simon@example.com )"

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    private val artworkCache = ConcurrentHashMap<String, String>()
    private val infoCache = ConcurrentHashMap<String, OnlineInfo>()

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    suspend fun getOnlineInfo(context: Context, artist: String, title: String): OnlineInfo? {
        val cacheKey = "$artist|$title"
        infoCache[cacheKey]?.let { return it }

        if (!isNetworkAvailable(context)) return null

        try {
            val query = "release:\"$title\" AND artist:\"$artist\""
            val response: MusicBrainzSearchResponse = client.get("https://musicbrainz.org/ws/2/release/") {
                header("User-Agent", USER_AGENT)
                parameter("query", query)
                parameter("fmt", "json")
            }.body()

            val release = response.releases.firstOrNull() ?: return null
            
            val artworkUrl = "https://coverartarchive.org/release/${release.id}/front"
            
            val info = OnlineInfo(
                releaseDate = release.releaseGroup?.firstReleaseDate ?: release.date,
                label = release.labelInfo?.firstOrNull()?.label?.name,
                genres = release.releaseGroup?.genres?.map { it.name },
                artworkUrl = artworkUrl
            )
            
            infoCache[cacheKey] = info
            artworkCache[cacheKey] = artworkUrl
            
            return info
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching info for $artist - $title: ${e.message}")
            return null
        }
    }

    suspend fun getArtworkUrl(context: Context, artist: String, title: String): String? {
        val cacheKey = "$artist|$title"
        artworkCache[cacheKey]?.let { return it }
        
        return getOnlineInfo(context, artist, title)?.artworkUrl
    }
}
