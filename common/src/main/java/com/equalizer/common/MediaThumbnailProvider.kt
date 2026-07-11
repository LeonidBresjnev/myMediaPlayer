package com.equalizer.common

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import com.equalizer.common.metadata.MetaFactory
import com.equalizer.common.metadata.Mp3Meta
import com.equalizer.common.metadata.M4aMeta
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import androidx.core.net.toUri

class MediaThumbnailProvider : ContentProvider() {

    companion object {
        private const val TAG = "MediaThumbnailProvider"
        
        @JvmStatic
        fun getAuthority(context: Context): String {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PROVIDERS)
                packageInfo.providers?.find { it.name == MediaThumbnailProvider::class.java.name }?.authority 
                    ?: "${context.packageName}.thumbnail"
            } catch (e: Exception) {
                e.message?.let {Log.d("Thumbnail",it)}
                "${context.packageName}.thumbnail"
            }
        }

        @JvmStatic
        fun getArtworkUri(context: Context, path: String): Uri {
            return Uri.Builder()
                .scheme("content")
                .authority(getAuthority(context))
                .appendPath("thumb.jpg") // Fake extension for better compatibility with some hosts
                .appendQueryParameter("path", path)
                .build()
        }

        @JvmField
        var AUTHORITY = "com.equalizer.mymediaplayer.thumbnail"
        
        @JvmField
        var CONTENT_URI: Uri = "content://$AUTHORITY".toUri()
        
        @JvmStatic
        fun init(context: Context) {
            AUTHORITY = getAuthority(context)
            CONTENT_URI = "content://$AUTHORITY".toUri()
            Log.d(TAG, "Initialized with authority: $AUTHORITY")
        }

        private val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        private val executor = Executors.newFixedThreadPool(4)
        private val rateLimitLock = ReentrantLock()
        private var lastRequestTime = 0L

        private val fileCache = LruCache<String, File>(100)
        private val notFoundCache = LruCache<String, Boolean>(100)
    }

    override fun onCreate(): Boolean {
        context?.let { init(it) }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String {
        return if (uri.path?.endsWith(".png", true) == true) "image/png" else "image/jpeg"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val path = uri.getQueryParameter("path") ?: return null
        
        if (notFoundCache.get(path) == true) {
            Log.d(TAG, "Skipping $path - already in notFoundCache")
            return null
        }

        Log.d(TAG, "openFile for path: $path")

        fileCache.get(path)?.let {
            if (it.exists() && it.length() > 0) {
                Log.d(TAG, "Serving $path from memory cache")
                return ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        
        val context = this.context ?: return null
        val file = if (path.startsWith("http")) {
            val cleanUrl = path.trim()
            val fileName = "remote_thumb_${cleanUrl.hashCode()}.jpg"
            val cacheFile = File(context.cacheDir, fileName)
            
            if (cacheFile.exists() && cacheFile.length() > 0) {
                Log.d(TAG, "Serving $path from disk cache")
                cacheFile
            } else {
                try {
                    Log.d(TAG, "Downloading remote image: $path")
                    executor.submit(Callable {
                        downloadRemoteImage(path)
                    }).get(30, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.e(TAG, "openFile: Download task failed for $path", e)
                    null
                }
            }
        } else {
            val fileName = "local_thumb_${path.hashCode()}.jpg"
            val cacheDir = context.cacheDir
            val cacheFile = File(cacheDir, fileName)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cacheFile
            } else {
                val f = File(path)
                val result = if (f.isDirectory) {
                    extractFolderArt(f, cacheFile)
                } else {
                    extractEmbeddedArt(path, cacheFile)
                }

                // If local extraction failed, try online search as a last resort
                if (result == null && !f.isDirectory) {
                    try {
                        Log.d(TAG, "Local art missing for $path, searching online...")
                        executor.submit(Callable {
                            searchAndDownloadOnline(path)
                        }).get(45, TimeUnit.SECONDS)
                    } catch (e: Exception) {
                        Log.e(TAG, "Online search failed for $path", e)
                        null
                    }
                } else {
                    result
                }
            }
        }

        if (file == null || !file.exists() || file.length() == 0L) {
            Log.e(TAG, "openFile: Failed to provide file for $path")
            notFoundCache.put(path, true)
            return null
        }

        fileCache.put(path, file)

        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.e(TAG, "openFile: Error opening PFD: ${e.message}")
            null
        }
    }

    private fun downloadRemoteImage(url: String): File? {
        val cleanUrl = url.trim()
        val fileName = "remote_thumb_${cleanUrl.hashCode()}.jpg"
        val context = this.context ?: return null
        val cacheFile = File(context.cacheDir, fileName)
        
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile

        rateLimitLock.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < 1100L) Thread.sleep(1100L - elapsed)
            lastRequestTime = System.currentTimeMillis()
        }

        try {
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "MyMediaPlayer/1.0 ( simon@example.com )")
                .header("Accept", "image/*")
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Download response for $cleanUrl: ${response.code} ${response.message}")
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed with code ${response.code} for $cleanUrl")
                    return null
                }
                val body = response.body ?: return null
                Log.d(TAG, "Response body length: ${body.contentLength()} type: ${body.contentType()}")
                
                val tempFile = File.createTempFile("download_", ".jpg", context.cacheDir)
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { input.copyTo(it) }
                }
                
                if (tempFile.exists() && tempFile.length() > 0) {
                    if (tempFile.renameTo(cacheFile)) {
                        Log.d(TAG, "Successfully cached remote image: ${cacheFile.absolutePath}")
                        return cacheFile
                    }
                    Log.w(TAG, "Failed to rename temp file to cache file")
                    return tempFile
                }
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadRemoteImage error: ${e.message}", e)
            return null
        }
    }

    private fun extractEmbeddedArt(filePath: String, cacheFile: File, silent: Boolean = false): File? {
        val file = File(filePath)
        if (!file.exists()) return null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val artwork = retriever.embeddedPicture ?: return null
            FileOutputStream(cacheFile).use { it.write(artwork) }
            return cacheFile
        } catch (e: Exception) {
            if (!silent) Log.e(TAG, "extractEmbeddedArt error: ${e.message}")
            return null
        } finally {
            retriever.release()
        }
    }

    private fun extractFolderArt(folder: File, cacheFile: File): File? {
        val musicFiles = folder.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".mp3", true) || it.name.endsWith(".m4a", true))
        }?.sortedBy { it.name }?.take(20) ?: return null

        for (file in musicFiles) {
            // Use silent=true to avoid filling logcat with "no artwork" errors during discovery
            val result = extractEmbeddedArt(file.absolutePath, cacheFile, silent = true)
            if (result != null) {
                Log.d(TAG, "Found folder artwork in ${file.name}")
                return result
            }
        }
        return null
    }

    private fun searchAndDownloadOnline(localPath: String): File? {
        val context = this.context ?: return null
        val file = File(localPath)
        if (!file.exists()) return null

        return kotlinx.coroutines.runBlocking {
            try {
                val meta = MetaFactory.createMeta(file, context)
                if (meta == null) return@runBlocking null

                val artist = when (meta) {
                    is Mp3Meta -> meta.artist
                    is M4aMeta -> meta.artist
                    else -> null
                }
                val title = when (meta) {
                    is Mp3Meta -> meta.name
                    is M4aMeta -> meta.name
                    else -> file.name
                }

                if (artist.isNullOrBlank() || title.isNullOrBlank()) return@runBlocking null

                val info = OnlineMetadataManager.getOnlineInfo(context, artist, title)
                if (info?.artworkUrl != null) {
                    return@runBlocking downloadRemoteImage(info.artworkUrl)
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "searchAndDownloadOnline error: ${e.message}")
                null
            }
        }
    }
}
