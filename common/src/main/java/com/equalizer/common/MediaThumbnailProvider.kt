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
import java.net.URLDecoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import androidx.core.net.toUri

class MediaThumbnailProvider : ContentProvider() {

    companion object {
        private const val TAG = "MediaThumbnailProvider"
        
        @JvmField
        var AUTHORITY = "com.equalizer.mymediaplayer.thumbnail"
        
        @JvmField
        var CONTENT_URI: Uri = "content://$AUTHORITY".toUri()
        
        @JvmStatic
        fun init(context: Context) {
            AUTHORITY = "${context.packageName}.thumbnail"
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

        private val fileCache = LruCache<String, File>(50)
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

    override fun getType(uri: Uri): String = "image/png"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val pathParam = uri.getQueryParameter("path") ?: return null
        
        val path = try {
            URLDecoder.decode(pathParam, "UTF-8")
        } catch (e: Exception) {
            pathParam
        }

        fileCache.get(path)?.let {
            if (it.exists() && it.length() > 0) {
                return ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        
        val file = if (path.startsWith("http")) {
            val cleanUrl = path.trim()
            val fileName = "remote_thumb_${cleanUrl.hashCode()}.jpg"
            val cacheFile = File(context?.cacheDir, fileName)
            
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cacheFile
            } else {
                try {
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
            val cacheFile = File(context?.cacheDir, fileName)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cacheFile
            } else {
                extractEmbeddedArt(path, cacheFile)
            }
        }

        if (file == null || !file.exists()) {
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
        val cacheFile = File(context?.cacheDir, fileName)
        
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
                if (!response.isSuccessful) return null
                val body = response.body
                val tempFile = File.createTempFile("download_", ".jpg", context?.cacheDir)
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { input.copyTo(it) }
                }
                if (tempFile.length() > 0 && tempFile.renameTo(cacheFile)) return cacheFile
                return if (tempFile.length() > 0) tempFile else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadRemoteImage error: ${e.message}")
            return null
        }
    }

    private fun extractEmbeddedArt(filePath: String, cacheFile: File): File? {
        val file = File(filePath)
        if (!file.exists()) return null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val artwork = retriever.embeddedPicture ?: return null
            FileOutputStream(cacheFile).use { it.write(artwork) }
            return cacheFile
        } catch (e: Exception) {
            Log.e(TAG, "extractEmbeddedArt error: ${e.message}")
            return null
        } finally {
            retriever.release()
        }
    }
}
