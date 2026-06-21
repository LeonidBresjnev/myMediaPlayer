package com.equalizer.common

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MediaThumbnailProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.equalizer.mymediaplayer.thumbnail"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val filePath = uri.getQueryParameter("path") ?: return null
        val file = File(filePath)
        if (!file.exists()) return null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val artwork = retriever.embeddedPicture ?: return null

            // Create a temporary file to store the artwork so we can return a ParcelFileDescriptor
            val tempFile = File.createTempFile("thumb_", ".jpg", context?.cacheDir)
            tempFile.deleteOnExit()
            
            FileOutputStream(tempFile).use { fos ->
                fos.write(artwork)
            }

            return ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.e("ThumbnailProvider", "Error opening file: ${e.message}")
            return null
        } finally {
            retriever.release()
        }
    }
}
