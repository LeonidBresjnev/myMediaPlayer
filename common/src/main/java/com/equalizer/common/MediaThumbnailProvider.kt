package com.equalizer.common

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
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
        const val AUTHORITY = "com.equalizer.mymediaplayer.thumbnail"
        val CONTENT_URI: Uri = "content://$AUTHORITY".toUri()
        
        private val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        private val executor = Executors.newSingleThreadExecutor()
        private val rateLimitLock = ReentrantLock()
        private var lastRequestTime = 0L
    }

    override fun onCreate(): Boolean = true

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
        val pathParam = uri.getQueryParameter("path") ?: return openFallbackIcon()
        
        val path = try {
            URLDecoder.decode(pathParam, "UTF-8")
        } catch (e: Exception) {
            e.message?.let {
                Log.d("MediaThumbnailProvider", it)
            }
            pathParam
        }

        Log.d(TAG, "openFile: requested path: $path")
        
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
            extractEmbeddedArt(path)
        }

        if (file == null || !file.exists()) {
            return openFallbackIcon()
        }

        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.e(TAG, "openFile: Error opening PFD: ${e.message}")
            openFallbackIcon()
        }
    }

    private fun openFallbackIcon(): ParcelFileDescriptor? {
        return try {
            val fallbackFile = File(context?.cacheDir, "fallback_icon_v3.png")
            if (!fallbackFile.exists() || fallbackFile.length() == 0L) {
                val transparentPng = byteArrayOf(
                    -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, -60, -119, 0, 0, 0, 11, 73, 68, 65, 84, 8, -41, 99, 96, 0, 2, 0, 0, 5, 0, 1, -21, 38, -80, 50, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126
                )
                FileOutputStream(fallbackFile).use { it.write(transparentPng) }
            }
            ParcelFileDescriptor.open(fallbackFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.e(TAG, "Error providing fallback icon", e)
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

    private fun extractEmbeddedArt(filePath: String): File? {
        val file = File(filePath)
        if (!file.exists()) return null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val artwork = retriever.embeddedPicture ?: return null
            val tempFile = File.createTempFile("thumb_", ".jpg", context?.cacheDir)
            // DON'T delete on exit, as the car host might need it later
            FileOutputStream(tempFile).use { it.write(artwork) }
            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "extractEmbeddedArt error: ${e.message}")
            return null
        } finally {
            retriever.release()
        }
    }
}
