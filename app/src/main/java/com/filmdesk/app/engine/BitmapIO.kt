package com.filmdesk.app.engine

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.filmdesk.app.model.ExportFormat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object BitmapIO {

    /** Long edge of the full-size photo in pixels (0 if unreadable). */
    fun longEdge(context: Context, uri: Uri): Int {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            max(o.outWidth, o.outHeight).coerceAtLeast(0)
        } catch (e: Exception) {
            0
        }
    }

    /** Decodes [uri] so that its long edge is at most [maxLongEdge], honouring EXIF rotation. */
    fun decode(context: Context, uri: Uri, maxLongEdge: Int): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val long = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (long / (sample * 2) >= maxLongEdge) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = try {
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        } ?: return null

        val l2 = max(bmp.width, bmp.height)
        if (l2 > maxLongEdge) {
            val s = maxLongEdge / l2.toFloat()
            bmp = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * s).roundToInt().coerceAtLeast(1),
                (bmp.height * s).roundToInt().coerceAtLeast(1),
                true,
            )
        }
        if (bmp.config != Bitmap.Config.ARGB_8888) bmp = bmp.copy(Bitmap.Config.ARGB_8888, false) ?: bmp

        val orientation = try {
            cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        return bmp
    }

    /**
     * Largest export long edge this device can process safely. Phones with small
     * heaps (older/low-end devices) get a smaller limit instead of crashing.
     */
    fun maxExportLongEdge(): Int {
        val maxBytes = Runtime.getRuntime().maxMemory()
        val pixels = maxBytes / (4L * 8L) // ~8 full-size int buffers during processing
        val edge = sqrt(pixels * 1.5).toInt()
        return edge.coerceIn(1600, 8192)
    }

    fun addBorder(src: Bitmap, caption: String): Bitmap {
        val w = src.width
        val h = src.height
        val short = min(w, h)
        val pad = (short * 0.04f).roundToInt()
        val bottom = (short * 0.11f).roundToInt()
        val out = Bitmap.createBitmap(w + pad * 2, h + pad + bottom, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFFF4F2EC.toInt())
        canvas.drawBitmap(src, pad.toFloat(), pad.toFloat(), null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF3A3A3A.toInt()
            textSize = bottom * 0.22f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.06f
        }
        var text = caption
        val maxW = out.width - pad * 2f
        while (text.length > 8 && paint.measureText(text) > maxW) text = text.dropLast(4) + "…"
        canvas.drawText(text, pad.toFloat(), h + pad + bottom * 0.58f, paint)
        return out
    }

    /** Saves to Pictures/FilmDesk. Returns the new image's Uri, or null on failure. */
    fun saveToGallery(
        context: Context, bmp: Bitmap, format: ExportFormat, quality: Int, recipeJson: String?,
        source: Uri? = null, keepLocation: Boolean = false,
    ): Uri? {
        val original = if (format == ExportFormat.JPEG && source != null) readOriginalExif(context, source, keepLocation) else null
        val name = "FilmDesk_${System.currentTimeMillis()}.${format.ext}"
        val compress = if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val cr = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, format.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FilmDesk")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                val ok = cr.openOutputStream(uri)?.use { bmp.compress(compress, quality, it) } ?: false
                if (!ok) { cr.delete(uri, null, null); return null }
                if (format == ExportFormat.JPEG && (recipeJson != null || original != null)) {
                    try {
                        cr.openFileDescriptor(uri, "rw")?.use { writeExif(ExifInterface(it.fileDescriptor), recipeJson, original, bmp) }
                    } catch (_: Exception) { /* metadata is optional */ }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                cr.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                cr.delete(uri, null, null)
                null
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FilmDesk")
            if (!dir.exists() && !dir.mkdirs()) return null
            val file = File(dir, name)
            val ok = FileOutputStream(file).use { bmp.compress(compress, quality, it) }
            if (!ok) return null
            if (format == ExportFormat.JPEG && (recipeJson != null || original != null)) {
                try { writeExif(ExifInterface(file.absolutePath), recipeJson, original, bmp) } catch (_: Exception) { }
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(format.mime), null)
            Uri.fromFile(file)
        }
    }

    /** Camera details worth keeping on the edited copy. */
    private val CAMERA_TAGS = listOf(
        ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
    )
    private val GPS_TAGS = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    )

    /**
     * Reads the original photo's camera details (and GPS only if [keepLocation]).
     * On Android 10+ location is hidden unless the app has ACCESS_MEDIA_LOCATION and asks
     * for the original file; if that isn't possible, the location is simply not copied.
     */
    fun readOriginalExif(context: Context, source: Uri, keepLocation: Boolean): Map<String, String>? {
        val cr = context.contentResolver
        val target = if (keepLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { MediaStore.setRequireOriginal(source) } catch (_: Exception) { source }
        } else source
        val exif = try {
            cr.openInputStream(target)?.use { ExifInterface(it) }
        } catch (_: Exception) {
            if (target != source) try { cr.openInputStream(source)?.use { ExifInterface(it) } } catch (_: Exception) { null } else null
        } ?: return null
        val tags = if (keepLocation) CAMERA_TAGS + GPS_TAGS else CAMERA_TAGS
        val out = LinkedHashMap<String, String>()
        for (t in tags) exif.getAttribute(t)?.let { out[t] = it }
        return out
    }

    private fun writeExif(exif: ExifInterface, recipeJson: String?, original: Map<String, String>?, bmp: Bitmap) {
        original?.forEach { (k, v) -> exif.setAttribute(k, v) }
        // Pixels are already rotated/cropped, so describe them as they are.
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, bmp.width.toString())
        exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, bmp.height.toString())
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Film Desk")
        if (recipeJson != null) exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "FilmDeskRecipe:$recipeJson")
        exif.saveAttributes()
    }

    /** Writes a temporary copy and opens the Android share sheet. */
    fun share(
        context: Context, bmp: Bitmap, format: ExportFormat, quality: Int,
        source: Uri? = null, recipeJson: String? = null, keepLocation: Boolean = false,
    ) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Only clear old copies (over an hour), so a share that's still being read isn't broken.
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val file = File(dir, "FilmDesk_${System.currentTimeMillis()}.${format.ext}")
        val compress = if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        FileOutputStream(file).use { bmp.compress(compress, quality, it) }
        if (format == ExportFormat.JPEG) {
            val original = source?.let { readOriginalExif(context, it, keepLocation) }
            if (original != null || recipeJson != null) {
                try { writeExif(ExifInterface(file.absolutePath), recipeJson, original, bmp) } catch (_: Exception) { }
            }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share photo").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
