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
 * there Lumora Film's film looks, Q Menu and Pro tools work exactly as on a JPEG.
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

    /** Answers per photo link, so the check runs once, not on every thumbnail and render. */
    private val rawCache = android.util.LruCache<String, Boolean>(1024)

    private val PLAIN_IMAGE_TYPES = setOf(
        "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif", "image/gif", "image/bmp", "image/avif",
    )

    /** True when [uri] is a camera RAW file (by type, file name or the Fujifilm RAF header). */
    fun isRaw(context: Context, uri: Uri): Boolean {
        val key = uri.toString()
        rawCache.get(key)?.let { return it }
        val raw = detect(context, uri)
        rawCache.put(key, raw)
        return raw
    }

    fun isRawExtension(e: String) = e.lowercase(Locale.US) in EXTENSIONS

    private fun ext(name: String?) = name?.substringAfterLast('.', "")?.lowercase(Locale.US).orEmpty()

    private fun detect(context: Context, uri: Uri): Boolean {
        // Studio copies keep the original file name: the extension says it all.
        if (uri.scheme == "file") {
            val e = ext(uri.lastPathSegment)
            if (e in EXTENSIONS) return true
            if (e.isNotEmpty()) return false
        }
        val mime = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }?.lowercase(Locale.US)
        if (mime != null) {
            if (mime in PLAIN_IMAGE_TYPES) return false
            if (mime == "image/dng" || mime.startsWith("image/x-") && mime != "image/x-icon" && mime != "image/x-ms-bmp") return true
        }
        if (ext(uri.lastPathSegment) in EXTENSIONS || ext(displayName(context, uri)) in EXTENSIONS) return true
        // Fujifilm RAF files start with "FUJIFILMCCD-RAW".
        return try {
            context.contentResolver.openInputStream(uri)?.use { s ->
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

    private fun availableMemory(context: Context): Long = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
    } catch (e: Exception) {
        Long.MAX_VALUE
    }

    /** Width and height of the developed full-size photo, already upright (null if unreadable). */
    fun size(context: Context, uri: Uri): Pair<Int, Int>? {
        if (!loaded) return null
        val info = withPath(context, uri) { nativeInfo(it) } ?: return null
        return if (info[0] > 0 && info[1] > 0) info[0] to info[1] else null
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
        val info = withPath(context, uri) { if (loaded) nativeInfo(it) else null } ?: return null
        val full = max(info[0], info[1])
        val pixels = info[0].toLong() * info[1]
        // A full-size develop needs about 14 bytes a pixel at once (LibRaw's 16-bit work image,
        // the raw data and the Bitmap): over 1 GB for a 100 MP file. When the phone hasn't got
        // that free, develop at half size (still 25 MP from a 100 MP sensor) instead of crashing.
        val fitsInMemory = pixels * 14 < availableMemory(context) / 2
        val half = full > 0 && (maxLongEdge * 2 <= full || !fitsInMemory)
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
