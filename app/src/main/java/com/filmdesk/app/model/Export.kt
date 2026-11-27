package com.filmdesk.app.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ExportFormat(val label: String, val mime: String, val ext: String) {
    JPEG("JPEG", "image/jpeg", "jpg"),
    PNG("PNG", "image/png", "png"),
    WEBP("WebP", "image/webp", "webp"),
    HEIF("HEIF", "image/heif", "heic"),
}

enum class SizeMode(val label: String) {
    Full("Full"), LongEdge("Long edge"), ShortEdge("Short edge"), Megapixels("Megapixels"), Percent("%"),
}

/** Lightroom-style output sharpening, tuned for where the photo will be seen. */
enum class Sharpen(val label: String) { Off("Off"), Screen("Screen"), Matte("Matte"), Glossy("Glossy") }

enum class MetadataMode(val label: String) { All("All"), CopyrightOnly("Copyright only"), None("None") }

val SHARPEN_LEVELS = listOf("Low", "Standard", "High")
val WATERMARK_COLORS = listOf("White", "Black", "Amber")

data class ExportSettings(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 92,
    val webpLossless: Boolean = false,
    /** Lower the quality until the file fits under [limitMb] (JPEG and lossy WebP). */
    val limitSize: Boolean = false,
    val limitMb: Float = 2f,
    val sizeMode: SizeMode = SizeMode.Full,
    val longEdge: Int = 2048,
    val shortEdge: Int = 1080,
    val megapixels: Int = 12,
    val percent: Int = 50,
    val sharpen: Sharpen = Sharpen.Off,
    val sharpenLevel: Int = 1,
    val metadata: MetadataMode = MetadataMode.All,
    val cameraInfo: Boolean = true,
    /** Copy GPS location from the original photo (off by default for privacy). */
    val keepLocation: Boolean = false,
    val embedRecipe: Boolean = true,
    val copyright: String = "",
    /** Subfolder inside Pictures/LumoraFilm ("" = none). */
    val folder: String = "",
    val watermark: Boolean = false,
    val wmText: String = "",
    /** 0..8, left to right, top to bottom. */
    val wmPos: Int = 8,
    /** Text height in % of the photo's short edge. */
    val wmSize: Int = 4,
    val wmOpacity: Int = 80,
    val wmColor: Int = 0,
) {
    /** Whether quality matters for this format. */
    val lossy: Boolean
        get() = format == ExportFormat.JPEG || format == ExportFormat.HEIF || (format == ExportFormat.WEBP && !webpLossless)

    val canLimitSize: Boolean get() = format == ExportFormat.JPEG || (format == ExportFormat.WEBP && !webpLossless)

    /** Metadata is written only into JPEG, where it is reliable on every phone. */
    val canWriteMetadata: Boolean get() = format == ExportFormat.JPEG

    val folderPath: String get() = "LumoraFilm" + (cleanFolder(folder).takeIf { it.isNotEmpty() }?.let { "/$it" } ?: "")

    fun toJson(): JSONObject = JSONObject().apply {
        put("format", format.name); put("quality", quality); put("webpLossless", webpLossless)
        put("limitSize", limitSize); put("limitMb", limitMb.toDouble())
        put("sizeMode", sizeMode.name); put("longEdge", longEdge); put("shortEdge", shortEdge)
        put("megapixels", megapixels); put("percent", percent)
        put("sharpen", sharpen.name); put("sharpenLevel", sharpenLevel)
        put("metadata", metadata.name); put("cameraInfo", cameraInfo); put("keepLocation", keepLocation)
        put("embedRecipe", embedRecipe); put("copyright", copyright); put("folder", folder)
        put("watermark", watermark); put("wmText", wmText); put("wmPos", wmPos); put("wmSize", wmSize)
        put("wmOpacity", wmOpacity); put("wmColor", wmColor)
    }

    companion object {
        fun fromJson(o: JSONObject): ExportSettings {
            val d = ExportSettings()
            fun <E : Enum<E>> enum(k: String, values: Array<E>, def: E) = values.firstOrNull { it.name == o.optString(k) } ?: def
            return ExportSettings(
                format = enum("format", ExportFormat.entries.toTypedArray(), d.format),
                quality = o.optInt("quality", d.quality).coerceIn(10, 100),
                webpLossless = o.optBoolean("webpLossless", false),
                limitSize = o.optBoolean("limitSize", false),
                limitMb = o.optDouble("limitMb", 2.0).toFloat().let { if (it.isNaN()) 2f else it.coerceIn(0.1f, 100f) },
                sizeMode = enum("sizeMode", SizeMode.entries.toTypedArray(), d.sizeMode),
                longEdge = o.optInt("longEdge", d.longEdge).coerceIn(64, 16000),
                shortEdge = o.optInt("shortEdge", d.shortEdge).coerceIn(64, 16000),
                megapixels = o.optInt("megapixels", d.megapixels).coerceIn(1, 200),
                percent = o.optInt("percent", d.percent).coerceIn(1, 100),
                sharpen = enum("sharpen", Sharpen.entries.toTypedArray(), d.sharpen),
                sharpenLevel = o.optInt("sharpenLevel", 1).coerceIn(0, 2),
                metadata = enum("metadata", MetadataMode.entries.toTypedArray(), d.metadata),
                cameraInfo = o.optBoolean("cameraInfo", true),
                keepLocation = o.optBoolean("keepLocation", false),
                embedRecipe = o.optBoolean("embedRecipe", true),
                copyright = o.optString("copyright", "").take(120),
                folder = cleanFolder(o.optString("folder", "")),
                watermark = o.optBoolean("watermark", false),
                wmText = o.optString("wmText", "").take(80),
                wmPos = o.optInt("wmPos", 8).coerceIn(0, 8),
                wmSize = o.optInt("wmSize", 4).coerceIn(1, 15),
                wmOpacity = o.optInt("wmOpacity", 80).coerceIn(5, 100),
                wmColor = o.optInt("wmColor", 0).coerceIn(0, WATERMARK_COLORS.lastIndex),
            )
        }
    }
}

/** Folder names: letters, digits, space, - and _ only, one level. */
fun cleanFolder(s: String): String = s.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(40)

/** A named set of export settings. Built-in presets change only file type, size and sharpening. */
data class ExportPreset(val name: String, val settings: ExportSettings, val builtIn: Boolean = false, val detail: String = "") {
    fun applyTo(s: ExportSettings): ExportSettings = if (!builtIn) settings else s.copy(
        format = settings.format, quality = settings.quality, webpLossless = settings.webpLossless,
        limitSize = false, sizeMode = settings.sizeMode, longEdge = settings.longEdge, shortEdge = settings.shortEdge,
        sharpen = settings.sharpen, sharpenLevel = settings.sharpenLevel,
    )

    fun matches(s: ExportSettings): Boolean {
        if (!builtIn) return settings == s
        val p = settings
        if (s.format != p.format || s.webpLossless != p.webpLossless || s.limitSize || s.sizeMode != p.sizeMode) return false
        if (p.lossy && s.quality != p.quality) return false
        if (p.sizeMode == SizeMode.LongEdge && s.longEdge != p.longEdge) return false
        if (p.sizeMode == SizeMode.ShortEdge && s.shortEdge != p.shortEdge) return false
        if (s.sharpen != p.sharpen || (p.sharpen != Sharpen.Off && s.sharpenLevel != p.sharpenLevel)) return false
        return true
    }

    fun toJson(): JSONObject = JSONObject().put("name", name).put("settings", settings.toJson())

    companion object {
        fun fromJson(o: JSONObject): ExportPreset? {
            val st = o.optJSONObject("settings") ?: return null
            return ExportPreset(o.optString("name", "Preset").take(40), ExportSettings.fromJson(st))
        }

        fun listToJson(list: List<ExportPreset>) = JSONArray().apply { list.forEach { put(it.toJson()) } }
    }
}

val BUILT_IN_PRESETS = listOf(
    ExportPreset("JPG Small", ExportSettings(quality = 85, sizeMode = SizeMode.LongEdge, longEdge = 2048), true, "2048 px · 85"),
    ExportPreset("JPG Full", ExportSettings(quality = 92, sizeMode = SizeMode.Full), true, "Full · 92"),
    ExportPreset(
        "Instagram",
        ExportSettings(quality = 90, sizeMode = SizeMode.ShortEdge, shortEdge = 1080, sharpen = Sharpen.Screen),
        true, "1080 px · 90",
    ),
    ExportPreset("PNG Full", ExportSettings(format = ExportFormat.PNG, sizeMode = SizeMode.Full), true, "Lossless"),
)

/**
 * Output size for a crop of [w]×[h] pixels: never larger than the photo, nor than [limit]
 * on the long edge (what this phone's memory allows).
 */
fun targetSize(w: Int, h: Int, s: ExportSettings, limit: Int): Pair<Int, Int> {
    if (w <= 0 || h <= 0) return w to h
    val long = max(w, h).toFloat()
    val short = min(w, h).toFloat()
    var k = when (s.sizeMode) {
        SizeMode.Full -> 1f
        SizeMode.LongEdge -> s.longEdge / long
        SizeMode.ShortEdge -> s.shortEdge / short
        SizeMode.Megapixels -> sqrt(s.megapixels * 1_000_000f / (w.toFloat() * h))
        SizeMode.Percent -> s.percent / 100f
    }
    k = min(k, 1f)
    k = min(k, limit / long)
    var tw = (w * k).roundToInt().coerceAtLeast(1)
    var th = (h * k).roundToInt().coerceAtLeast(1)
    if (s.format == ExportFormat.HEIF) {
        // The HEIF encoder wants even sizes.
        tw = max(2, tw - tw % 2); th = max(2, th - th % 2)
    }
    return tw to th
}

fun formatBytes(b: Long): String = when {
    b >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f MB", b / 1_000_000f)
    else -> "${(b / 1000).coerceAtLeast(1)} KB"
}
