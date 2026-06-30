package com.equalizer.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class MusicBrainzSearchResponse(
    val releases: List<MusicBrainzRelease> = emptyList(),
    val recordings: List<MusicBrainzRecording> = emptyList()
)

@Serializable
data class MusicBrainzRecording(
    val id: String? = null,
    val title: String? = null,
    val releases: List<MusicBrainzRelease> = emptyList()
)

@Serializable
data class MusicBrainzRelease(
    val id: String? = null,
    val title: String? = null,
    @SerialName("release-group") val releaseGroup: MusicBrainzReleaseGroup? = null,
    val date: String? = null,
    @SerialName("label-info") val labelInfo: List<MusicBrainzLabelInfo>? = null,
    val genres: List<MusicBrainzGenre>? = null
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

@Serializable
data class OnlineInfo(
    val releaseDate: String? = null,
    val label: String? = null,
    val genres: List<String>? = null,
    val artworkUrl: String? = null,
    val isNotFound: Boolean = false
)

object OnlineMetadataManager {
    private const val TAG = "OnlineMetadataManager"
    private const val USER_AGENT = "MyMediaPlayer/1.0 ( simon@example.com )"
    private const val CACHE_FILE_NAME = "online_metadata_cache.json"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    private val infoCache = ConcurrentHashMap<String, OnlineInfo>()
    private var isCacheLoaded = false
    private val networkMutex = Mutex()

    private fun loadCache(context: Context) {
        if (isCacheLoaded) return
        try {
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            if (cacheFile.exists()) {
                val content = cacheFile.readText()
                val loadedCache: Map<String, OnlineInfo> = json.decodeFromString(content)
                infoCache.putAll(loadedCache)
                Log.d(TAG, "Loaded ${infoCache.size} items from persistent cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cache: ${e.message}")
        } finally {
            isCacheLoaded = true
        }
    }

    private fun saveCache(context: Context) {
        try {
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
            val content = json.encodeToString(infoCache.toMap())
            cacheFile.writeText(content)
            Log.d(TAG, "Saved persistent cache with ${infoCache.size} items")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache: ${e.message}")
        }
    }

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

    private fun escapeLucene(query: String): String {
        return query.replace("\"", "\\\"")
            .replace("'", "\\'")
    }

    suspend fun getOnlineInfo(context: Context, artist: String, title: String): OnlineInfo? {
        loadCache(context)
        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()
        
        if (cleanArtist.isBlank() || cleanTitle.isBlank()) return null
        
        val cacheKey = "$cleanArtist|$cleanTitle"
        infoCache[cacheKey]?.let { return it }

        if (!isNetworkAvailable(context)) return null

        try {
            networkMutex.lock()
            try {
                // Respect MusicBrainz rate limit: 1 request per second
                delay(1100L.milliseconds)
                
                // Try searching by recording first (better for individual songs)
                val recordingQuery = "recording:\"${escapeLucene(cleanTitle)}\" AND artist:\"${escapeLucene(cleanArtist)}\""
                
                val recordingResponse: MusicBrainzSearchResponse = client.get("https://musicbrainz.org/ws/2/recording/") {
                    header("User-Agent", USER_AGENT)
                    parameter("query", recordingQuery)
                    parameter("fmt", "json")
                }.body()

                var releaseId: String? = recordingResponse.recordings
                    .firstOrNull { it.releases.isNotEmpty() }
                    ?.releases?.firstOrNull()?.id

                if (releaseId == null) {
                    // Fallback to release search (better for folders/albums)
                    val releaseQuery = "release:\"${escapeLucene(cleanTitle)}\" AND artist:\"${escapeLucene(cleanArtist)}\""
                    
                    val releaseResponse: MusicBrainzSearchResponse = client.get("https://musicbrainz.org/ws/2/release/") {
                        header("User-Agent", USER_AGENT)
                        parameter("query", releaseQuery)
                        parameter("fmt", "json")
                    }.body()
                    
                    releaseId = releaseResponse.releases.firstOrNull()?.id
                }

                if (releaseId == null) {
                    val notFound = OnlineInfo(isNotFound = true)
                    infoCache[cacheKey] = notFound
                    saveCache(context)
                    return notFound
                }

                // Now fetch the full release details to get dates, labels, genres
                val release: MusicBrainzRelease = client.get("https://musicbrainz.org/ws/2/release/$releaseId") {
                    header("User-Agent", USER_AGENT)
                    parameter("inc", "release-groups+labels+genres")
                    parameter("fmt", "json")
                }.body()
                
                val artworkUrl = "https://coverartarchive.org/release/$releaseId/front"
                
                val info = OnlineInfo(
                    releaseDate = release.releaseGroup?.firstReleaseDate ?: release.date,
                    label = release.labelInfo?.firstOrNull()?.label?.name,
                    genres = release.releaseGroup?.genres?.map { it.name } ?: release.genres?.map { it.name },
                    artworkUrl = artworkUrl
                )
                
                infoCache[cacheKey] = info
                saveCache(context)
                return info
            } finally {
                networkMutex.unlock()
            }
        } catch (_: Exception) {
            return null
        }
    }
/*
    suspend fun getArtworkUrl(context: Context, artist: String, title: String): String? {
        return getOnlineInfo(context, artist, title)?.artworkUrl
    }*/
}
