package com.filmdesk.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.filmdesk.app.model.EditParams
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A photo imported into the Studio. Only a link to the photo and its edits are kept;
 * the original file is never changed. [params] is null when the photo has no edits.
 */
data class StudioItem(
    val id: Long,
    val uri: Uri,
    val params: EditParams? = null,
    val added: Long = System.currentTimeMillis(),
    /** For photos shared from another app: the link they came from ([uri] is our private copy). */
    val source: String? = null,
) {
    val edited: Boolean get() = params != null
}

/** Saves the Studio list as JSON in app storage, plus a small edited thumbnail per photo. */
class StudioStore(context: Context) {
    private val file = File(context.filesDir, "studio.json")
    private val thumbDir = File(context.filesDir, "studio_thumbs").apply { mkdirs() }

    fun load(): List<StudioItem> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("uri").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                StudioItem(
                    id = o.optLong("id"),
                    uri = Uri.parse(uri),
                    params = o.optJSONObject("params")?.let { EditParams.fromJson(it) },
                    added = o.optLong("added", 0L),
                    source = o.optString("source").takeIf { it.isNotEmpty() },
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<StudioItem>) {
        val arr = JSONArray()
        for (it in list) {
            val o = JSONObject().put("id", it.id).put("uri", it.uri.toString()).put("added", it.added)
            it.params?.let { p -> o.put("params", p.toJson()) }
            it.source?.let { s -> o.put("source", s) }
            arr.put(o)
        }
        try {
            val tmp = File(file.parentFile, "studio.json.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) { file.writeText(arr.toString()); tmp.delete() }
        } catch (e: Exception) {
            // Best effort: the list is kept in memory until the next successful save.
        }
    }

    private val importDir = File(context.filesDir, "imported").apply { mkdirs() }

    /**
     * Keeps a private copy of a photo shared from another app. Such links only work while the
     * app is open, so without a copy the photo would be lost after a restart.
     */
    fun copyIn(context: Context, uri: Uri): Uri? {
        val name = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
        val safe = (name ?: "photo.jpg").replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80)
        val file = File(importDir, "${System.currentTimeMillis()}_$safe")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return null
            Uri.fromFile(file)
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    /** Deletes private copies that no Studio photo uses any more. */
    fun cleanCopies(list: List<StudioItem>) {
        val used = list.mapNotNull { it.uri.path }.toHashSet()
        importDir.listFiles()?.forEach { f -> if (f.path !in used) f.delete() }
    }

    private fun thumbFile(id: Long) = File(thumbDir, "$id.jpg")

    fun readThumb(id: Long): Bitmap? = try {
        thumbFile(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    } catch (e: Throwable) {
        null
    }

    fun writeThumb(id: Long, bmp: Bitmap) {
        try {
            thumbFile(id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (e: Exception) {
        }
    }

    fun deleteThumb(id: Long) {
        thumbFile(id).delete()
    }
}
