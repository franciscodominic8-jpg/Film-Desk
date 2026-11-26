package com.filmdesk.app.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaItem(val id: Long, val uri: Uri)

/** Lists the phone's photos (newest first) and loads small thumbnails. */
object MediaRepository {
    // Sized by memory (about 24 MB), not by count: thumbnails can be up to 256 x 256.
    private val cache = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }

    suspend fun recent(context: Context, limit: Int = 500): List<MediaItem> = withContext(Dispatchers.IO) {
        val out = ArrayList<MediaItem>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(idCol)
                    out += MediaItem(id, ContentUris.withAppendedId(collection, id))
                }
            }
        } catch (e: SecurityException) {
            // Permission revoked while running: return what we have.
        }
        out
    }

    suspend fun thumbnail(context: Context, item: MediaItem): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(item.id)?.let { return@withContext it }
        val bmp = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(item.uri, Size(256, 256), null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Thumbnails.getThumbnail(
                    context.contentResolver, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null
                )
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(item.id, bmp)
        bmp
    }
}
