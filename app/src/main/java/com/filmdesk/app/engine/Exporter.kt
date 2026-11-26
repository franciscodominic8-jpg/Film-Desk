package com.filmdesk.app.engine

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.heifwriter.HeifWriter
import com.filmdesk.app.model.EditParams
import com.filmdesk.app.model.ExportFormat
import com.filmdesk.app.model.ExportSettings
import com.filmdesk.app.model.MetadataMode
import com.filmdesk.app.model.Sharpen
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Final steps of an export: resize, output sharpening, watermark, encoding, metadata, saving. */
object Exporter {

    class Encoded(val bytes: ByteArray, val quality: Int, val fits: Boolean)

    /** Size of the photo as it is shown (EXIF rotation applied), or null if unreadable. */
    fun orientedSize(context: Context, uri: Uri): Pair<Int, Int>? {
        val cr = context.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        } catch (e: Exception) {
            return null
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        val orientation = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } ?: 1
        } catch (e: Exception) {
            1
        }
        val turned = orientation in 5..8
        return if (turned) o.outHeight to o.outWidth else o.outWidth to o.outHeight
    }

    /** Resize to the export size, then output sharpening and watermark. */
    fun finish(src: Bitmap, tw: Int, th: Int, s: ExportSettings): Bitmap {
        var b = src
        // Step down by halves first: much cleaner than one big jump.
        while (b.width / 2 >= tw && b.height / 2 >= th && b.width / 2 > 0) {
            b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        }
        if (b.width > tw || b.height > th) b = Bitmap.createScaledBitmap(b, min(tw, b.width), min(th, b.height), true)
        if (s.format == ExportFormat.HEIF && (b.width % 2 != 0 || b.height % 2 != 0)) {
            b = Bitmap.createBitmap(b, 0, 0, max(2, b.width - b.width % 2), max(2, b.height - b.height % 2))
        }
        if (!b.isMutable) b = b.copy(Bitmap.Config.ARGB_8888, true)
        if (s.sharpen != Sharpen.Off) sharpen(b, s.sharpen, s.sharpenLevel)
        if (s.watermark && s.wmText.isNotBlank()) watermark(b, s)
        return b
    }

    /**
     * Output sharpening for the final size: an unsharp mask on brightness only (no colour
     * fringes), stronger for print papers than for screens. Works row by row to save memory.
     */
    private fun sharpen(b: Bitmap, kind: Sharpen, level: Int) {
        val amounts = when (kind) {
            Sharpen.Screen -> floatArrayOf(0.35f, 0.55f, 0.8f)
            Sharpen.Matte -> floatArrayOf(0.6f, 0.9f, 1.25f)
            Sharpen.Glossy -> floatArrayOf(0.45f, 0.7f, 1.0f)
            Sharpen.Off -> return
        }
        val k = amounts[level.coerceIn(0, 2)]
        val w = b.width
        val h = b.height
        if (w < 3 || h < 3) return
        val rows = Array(3) { IntArray(w) }
        val lum = Array(3) { FloatArray(w) }
        fun load(y: Int, slot: Int) {
            b.getPixels(rows[slot], 0, w, 0, y.coerceIn(0, h - 1), w, 1)
            val r = rows[slot]; val l = lum[slot]
            for (x in 0 until w) {
                val c = r[x]
                l[x] = 0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)
            }
        }
        load(0, 0); load(0, 1); load(1, 2)            // slots: above, current, below
        val out = IntArray(w)
        for (y in 0 until h) {
            val a = lum[0]; val c = lum[1]; val d = lum[2]
            val cur = rows[1]
            for (x in 0 until w) {
                val x0 = max(0, x - 1); val x1 = min(w - 1, x + 1)
                val mean = (a[x0] + a[x] + a[x1] + c[x0] + c[x] + c[x1] + d[x0] + d[x] + d[x1]) / 9f
                val delta = (c[x] - mean) * k
                val px = cur[x]
                val r = (((px shr 16) and 255) + delta).roundToInt().coerceIn(0, 255)
                val g = (((px shr 8) and 255) + delta).roundToInt().coerceIn(0, 255)
                val bl = ((px and 255) + delta).roundToInt().coerceIn(0, 255)
                out[x] = (px and -0x1000000) or (r shl 16) or (g shl 8) or bl
            }
            // Slide the window down before writing, so the next row still sees the original pixels.
            val topRow = rows[0]; val topLum = lum[0]
            rows[0] = rows[1]; lum[0] = lum[1]
            rows[1] = rows[2]; lum[1] = lum[2]
            rows[2] = topRow; lum[2] = topLum
            load(y + 2, 2)
            b.setPixels(out, 0, w, 0, y, w, 1)
        }
    }

    private fun watermark(b: Bitmap, s: ExportSettings) {
        val short = min(b.width, b.height).toFloat()
        val color = when (s.wmColor) { 1 -> 0xFF000000.toInt(); 2 -> 0xFFF2A33A.toInt(); else -> 0xFFFFFFFF.toInt() }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = (s.wmOpacity.coerceIn(5, 100) * 255 / 100)
            textSize = short * s.wmSize.coerceIn(1, 15) / 100f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            letterSpacing = 0.03f
            if (s.wmColor != 1) setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, 0x66000000)
        }
        val text = s.wmText.trim()
        val tw = paint.measureText(text)
        val margin = short * 0.035f
        val col = s.wmPos % 3
        val row = s.wmPos / 3
        val x = when (col) { 0 -> margin; 1 -> (b.width - tw) / 2f; else -> b.width - tw - margin }
        val fm = paint.fontMetrics
        val y = when (row) {
            0 -> margin - fm.ascent
            1 -> b.height / 2f - (fm.ascent + fm.descent) / 2f
            else -> b.height - margin - fm.descent
        }
        Canvas(b).drawText(text, x, y, paint)
    }

    /** Encodes at [quality]. HEIF needs Android 9+ and a phone with a HEVC encoder. */
    fun encode(context: Context, b: Bitmap, s: ExportSettings, quality: Int): ByteArray {
        val q = quality.coerceIn(1, 100)
        if (s.format == ExportFormat.HEIF) return encodeHeif(context, b, q)
        val out = ByteArrayOutputStream()
        val ok = when (s.format) {
            ExportFormat.PNG -> b.compress(Bitmap.CompressFormat.PNG, 100, out)
            ExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (s.webpLossless) b.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                else b.compress(Bitmap.CompressFormat.WEBP_LOSSY, q, out)
            } else {
                @Suppress("DEPRECATION")
                b.compress(Bitmap.CompressFormat.WEBP, if (s.webpLossless) 100 else q, out)
            }
            else -> b.compress(Bitmap.CompressFormat.JPEG, q, out)
        }
        if (!ok) error("Couldn't encode the photo")
        return out.toByteArray()
    }

    private fun encodeHeif(context: Context, b: Bitmap, q: Int): ByteArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) error("HEIF needs Android 9 or newer")
        val tmp = File(context.cacheDir, "encode_${System.nanoTime()}.heic")
        try {
            val writer = HeifWriter.Builder(tmp.absolutePath, b.width, b.height, HeifWriter.INPUT_MODE_BITMAP)
                .setQuality(q)
                .setMaxImages(1)
                .build()
            try {
                writer.start()
                writer.addBitmap(b)
                writer.stop(30_000)
            } finally {
                writer.close()
            }
            return tmp.readBytes()
        } catch (e: Exception) {
            error("This phone can't save HEIF — choose JPEG or WebP")
        } finally {
            tmp.delete()
        }
    }

    /** Encodes, lowering the quality if needed to stay under the size limit. */
    fun encodeFitting(context: Context, b: Bitmap, s: ExportSettings): Encoded {
        val first = encode(context, b, s, s.quality)
        if (!s.limitSize || !s.canLimitSize) return Encoded(first, s.quality, true)
        val limit = (s.limitMb * 1_000_000).toLong()
        if (first.size <= limit) return Encoded(first, s.quality, true)
        var lo = 10
        var hi = s.quality - 1
        var best: ByteArray? = null
        var bestQ = lo
        repeat(7) {
            if (lo > hi) return@repeat
            val mid = (lo + hi) / 2
            val bytes = encode(context, b, s, mid)
            if (bytes.size <= limit) { best = bytes; bestQ = mid; lo = mid + 1 } else hi = mid - 1
        }
        best?.let { return Encoded(it, bestQ, true) }
        return Encoded(encode(context, b, s, 10), 10, false)
    }

    /** EXIF to write, or null for none (metadata "None", or a format that can't hold it). */
    fun metadata(context: Context, source: Uri, s: ExportSettings, p: EditParams): Map<String, String>? {
        if (s.metadata == MetadataMode.None || !s.canWriteMetadata) return null
        val tags = LinkedHashMap<String, String>()
        if (s.metadata == MetadataMode.All) {
            if (s.cameraInfo || s.keepLocation) {
                BitmapIO.readOriginalExif(context, source, s.keepLocation)?.forEach { (k, v) ->
                    val gps = k.startsWith("GPS")
                    if ((gps && s.keepLocation) || (!gps && s.cameraInfo)) tags[k] = v
                }
            }
            tags[ExifInterface.TAG_SOFTWARE] = "Film Desk"
            if (s.embedRecipe) tags[ExifInterface.TAG_USER_COMMENT] = "FilmDeskRecipe:" + p.toJson()
        }
        val c = s.copyright.trim()
        if (c.isNotEmpty()) {
            tags[ExifInterface.TAG_COPYRIGHT] = c
            tags[ExifInterface.TAG_ARTIST] = c.removePrefix("©").trim()
        }
        return tags
    }

    private fun writeExif(exif: ExifInterface, tags: Map<String, String>, w: Int, h: Int) {
        tags.forEach { (k, v) -> exif.setAttribute(k, v) }
        // Pixels are already turned and cropped, so describe them as they are.
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, w.toString())
        exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, h.toString())
        exif.saveAttributes()
    }

    /** Saves to Pictures/FilmDesk[/folder]. Returns the new file's Uri. */
    fun saveToGallery(
        context: Context, bytes: ByteArray, s: ExportSettings, tags: Map<String, String>?, w: Int, h: Int, name: String,
    ): Uri {
        val file = "$name.${s.format.ext}"
        val cr = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file)
                put(MediaStore.Images.Media.MIME_TYPE, s.format.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + s.folderPath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Couldn't save the photo")
            try {
                cr.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Couldn't save the photo")
                if (!tags.isNullOrEmpty()) {
                    try {
                        cr.openFileDescriptor(uri, "rw")?.use { writeExif(ExifInterface(it.fileDescriptor), tags, w, h) }
                    } catch (_: Exception) { /* metadata is optional */ }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                cr.update(uri, values, null, null)
                return uri
            } catch (e: Exception) {
                cr.delete(uri, null, null)
                throw e
            }
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), s.folderPath)
        if (!dir.exists() && !dir.mkdirs()) error("Couldn't create the folder")
        val out = File(dir, file)
        out.writeBytes(bytes)
        if (!tags.isNullOrEmpty()) {
            try { writeExif(ExifInterface(out.absolutePath), tags, w, h) } catch (_: Exception) { }
        }
        MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf(s.format.mime), null)
        return Uri.fromFile(out)
    }

    /** Writes a temporary copy and opens the Android share sheet. */
    fun share(context: Context, bytes: ByteArray, s: ExportSettings, tags: Map<String, String>?, w: Int, h: Int, name: String) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Only clear old copies (over an hour), so a share that's still being read isn't broken.
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val file = File(dir, "$name.${s.format.ext}")
        file.writeBytes(bytes)
        if (!tags.isNullOrEmpty()) {
            try { writeExif(ExifInterface(file.absolutePath), tags, w, h) } catch (_: Exception) { }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = s.format.mime
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
