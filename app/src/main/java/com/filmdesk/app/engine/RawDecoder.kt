package com.filmdesk.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * RAW photos (Fujifilm RAF, DNG, CR2/CR3, NEF, ARW, ORF, RW2, PEF, SRW, ...) through LibRaw.
 * The RAW is developed neutrally (white balance as shot, sRGB) into a normal Bitmap, and from
 * there Film Desk's film looks, Q Menu and Pro tools work exactly as on a JPEG.
 */
object RawDecoder {

    private val loaded: Boolean = try {
        System.loadLibrary("filmdeskraw")
        true
    } catch (e: Throwable) {
        false
    }

    /** Highlight handling: 2 = blend (gentle recovery of bright areas). */
    private const val HIGHLIGHT_BLEND = 2

    private val EXTENSIONS = setOf(
        "raf", "dng", "cr2", "cr3", "crw", "nef", "nrw", "arw", "srf", "sr2", "orf", "rw2", "raw",
        "pef", "srw", "3fr", "fff", "iiq", "rwl", "x3f", "erf", "kdc", "dcr", "mrw", "mos", "mef",
    )

    @JvmStatic private external fun nativeInfo(path: String): IntArray?
    @JvmStatic private external fun nativeDevelop(path: String, half: Boolean, highlight: Int): Long
    @JvmStatic private external fun nativeSize(handle: Long): IntArray
    @JvmStatic private external fun nativeCopy(handle: Long, bitmap: Bitmap): Boolean
    @JvmStatic private external fun nativeClose(handle: Long)

    /** True when [uri] is a camera RAW file (by type, file name or the Fujifilm RAF header). */
    fun isRaw(context: Context, uri: Uri): Boolean {
        val cr = context.contentResolver
        val mime = try { cr.getType(uri) } catch (e: Exception) { null }?.lowercase(Locale.US)
        if (mime != null && (mime.startsWith("image/x-") && mime != "image/x-icon" && mime != "image/x-ms-bmp" || mime == "image/dng")) {
            return true
        }
        val name = (uri.lastPathSegment ?: "") + " " + (displayName(context, uri) ?: "")
        if (name.split(' ').any { it.substringAfterLast('.', "").lowercase(Locale.US) in EXTENSIONS }) return true
        // Fujifilm RAF files start with "FUJIFILMCCD-RAW".
        return try {
            cr.openInputStream(uri)?.use { s ->
                val head = ByteArray(15)
                s.read(head) == 15 && String(head, Charsets.US_ASCII) == "FUJIFILMCCD-RAW"
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        if (uri.scheme == "file") null
        else context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /** Runs [block] with a file path LibRaw can open: the file itself, or this app's handle to it. */
    private fun <T> withPath(context: Context, uri: Uri, block: (String) -> T?): T? {
        if (uri.scheme == "file") return uri.path?.let(block)
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> block("/proc/self/fd/${pfd.fd}") }
        } catch (e: Exception) {
            null
        }
    }

    /** Long edge of the developed full-size photo (0 if unreadable). */
    fun longEdge(context: Context, uri: Uri): Int {
        if (!loaded) return 0
        val info = withPath(context, uri) { nativeInfo(it) } ?: return 0
        return max(info[0], info[1]).coerceAtLeast(0)
    }

    /**
     * Develops [uri] with its long edge at most [maxLongEdge]. Small sizes use LibRaw's fast
     * half-size develop; exports use the full sensor resolution. Already turned upright.
     */
    fun decode(context: Context, uri: Uri, maxLongEdge: Int): Bitmap? {
        if (!loaded) return null
        val full = longEdge(context, uri)
        val half = full > 0 && maxLongEdge * 2 <= full
        val bmp = withPath(context, uri) { path ->
            val h = nativeDevelop(path, half, HIGHLIGHT_BLEND)
            if (h == 0L) return@withPath null
            try {
                val size = nativeSize(h)
                if (size[0] <= 0 || size[1] <= 0) return@withPath null
                val out = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888)
                if (nativeCopy(h, out)) out else { out.recycle(); null }
            } catch (e: OutOfMemoryError) {
                null
            } finally {
                nativeClose(h)
            }
        } ?: return null
        val l = max(bmp.width, bmp.height)
        if (l <= maxLongEdge) return bmp
        val s = maxLongEdge / l.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            bmp, (bmp.width * s).roundToInt().coerceAtLeast(1), (bmp.height * s).roundToInt().coerceAtLeast(1), true,
        )
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }
}
