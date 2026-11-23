package com.filmdesk.app.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private val ZERO8: List<Int> = List(8) { 0 }
private val FULL_MIX: List<Int> = List(4) { 100 }

/** Every setting of an edit. Immutable, so undo/redo is just a stack of these. */
data class EditParams(
    // ---- Fujifilm camera settings (Q menu) ----
    val sim: Int = 0,
    val simStrength: Float = 1f,
    val exposureThirds: Int = 0,          // −9..+9 = −3..+3 EV
    val grain: Int = 0,                   // index into GRAIN_OPTIONS
    val colorChrome: Int = 0,             // 0 off, 1 weak, 2 strong
    val colorChromeBlue: Int = 0,
    val wb: Int = 0,                      // index into WB_PRESETS
    val kelvin: Int = 5500,
    val wbShiftR: Int = 0,                // −9..+9
    val wbShiftB: Int = 0,
    val dynamicRange: Int = 0,            // 0 DR100, 1 DR200, 2 DR400
    val dRangePriority: Int = 0,          // 0 off, 1 weak, 2 strong, 3 auto
    val highlight: Float = 0f,            // −2..+4, 0.5 steps
    val shadow: Float = 0f,
    val color: Int = 0,                   // −4..+4
    val sharpness: Int = 0,
    val noiseReduction: Int = 0,
    val clarity: Int = 0,                 // −5..+5
    val monoWC: Int = 0,                  // −9..+9
    val monoMG: Int = 0,
    val smoothSkin: Int = 0,
    // ---- Pro adjustments ----
    val contrast: Int = 0,
    val highlights: Int = 0,
    val shadows: Int = 0,
    val whites: Int = 0,
    val blacks: Int = 0,
    val temp: Int = 0,
    val tint: Int = 0,
    val vibrance: Int = 0,
    val saturation: Int = 0,
    val fade: Int = 0,
    val vignette: Int = 0,
    /** Glow amount 0..100 (0 = off). */
    val glow: Int = 0,
    /** 0 Glow (bloom), 1 Halation (red-orange film glow), 2 Mist (soft haze). */
    val glowStyle: Int = 0,
    /** Brightness (0..100) where highlights start to glow. */
    val glowThreshold: Int = 70,
    /** How far the glow spreads, 0..100. */
    val glowSize: Int = 40,
    /** 0 neutral … 100 warm. */
    val glowWarmth: Int = 30,
    val hslHue: List<Int> = ZERO8,
    val hslSat: List<Int> = ZERO8,
    val hslLum: List<Int> = ZERO8,
    // ---- Tone curves (flat x,y point lists, see Curve.kt) ----
    val curveY: List<Float> = IDENTITY_CURVE,
    val curveR: List<Float> = IDENTITY_CURVE,
    val curveG: List<Float> = IDENTITY_CURVE,
    val curveB: List<Float> = IDENTITY_CURVE,
    /** Linked (ganged): Y moves R, G, B together. Unlinked: Y changes brightness (luma) only. */
    val curveLinked: Boolean = true,
    /** Curve intensity per channel Y, R, G, B: 0..100 (100 = full curve). */
    val curveMix: List<Int> = FULL_MIX,
    /** Soft clip: output black level, output white level (0..100), shadow and highlight softness (0..100). */
    val softLow: Int = 0,
    val softHigh: Int = 100,
    val softLS: Int = 0,
    val softHS: Int = 0,
    /** Soft clip linked: one set of values for R, G and B. Unlinked: [softRGB] is used. */
    val softLink: Boolean = true,
    /** Per-channel soft clip, 12 values: channel (R, G, B) × 4 + (Low, High, L.S., H.S.). */
    val softRGB: List<Int> = SOFT_RGB_DEFAULT,
    // ---- Geometry ----
    /** Crop ratio lock: index into ASPECTS (ASPECT_FREE = no lock). */
    val aspect: Int = ASPECT_FREE,
    /** Ratio turned the other way (3:2 → 2:3). */
    val aspectFlip: Boolean = false,
    val rotation: Int = 0,
    val flipH: Boolean = false,
    /** Fine rotation in degrees, −45..45; the photo zooms in so no corners are blank. */
    val straighten: Float = 0f,
    /** Crop rectangle as fractions of the rotated/straightened photo. */
    val cropL: Float = 0f,
    val cropT: Float = 0f,
    val cropR: Float = 1f,
    val cropB: Float = 1f,
) {
    /** Same look with this photo's crop/rotation kept (used when applying a recipe). */
    fun withGeometryOf(other: EditParams) = copy(
        aspect = other.aspect, aspectFlip = other.aspectFlip, rotation = other.rotation, flipH = other.flipH,
        straighten = other.straighten, cropL = other.cropL, cropT = other.cropT, cropR = other.cropR, cropB = other.cropB,
    )

    fun withoutGeometry() = withGeometryOf(EditParams())

    /** Resets only the Fujifilm Q-menu settings. */
    fun resetCamera() = EditParams().copy(
        contrast = contrast, highlights = highlights, shadows = shadows, whites = whites, blacks = blacks,
        temp = temp, tint = tint, vibrance = vibrance, saturation = saturation, fade = fade, vignette = vignette,
        glow = glow, glowStyle = glowStyle, glowThreshold = glowThreshold, glowSize = glowSize, glowWarmth = glowWarmth,
        hslHue = hslHue, hslSat = hslSat, hslLum = hslLum,
        curveY = curveY, curveR = curveR, curveG = curveG, curveB = curveB,
        curveLinked = curveLinked, curveMix = curveMix,
        softLow = softLow, softHigh = softHigh, softLS = softLS, softHS = softHS,
        softLink = softLink, softRGB = softRGB,
    ).withGeometryOf(this)

    /** Resets only the Pro adjustments. */
    fun resetPro() = copy(
        contrast = 0, highlights = 0, shadows = 0, whites = 0, blacks = 0, temp = 0, tint = 0,
        vibrance = 0, saturation = 0, fade = 0, vignette = 0,
        glow = 0, glowStyle = 0, glowThreshold = 70, glowSize = 40, glowWarmth = 30, hslHue = ZERO8, hslSat = ZERO8, hslLum = ZERO8,
        curveY = IDENTITY_CURVE, curveR = IDENTITY_CURVE, curveG = IDENTITY_CURVE, curveB = IDENTITY_CURVE,
        curveLinked = true, curveMix = FULL_MIX, softLow = 0, softHigh = 100, softLS = 0, softHS = 0,
        softLink = true, softRGB = SOFT_RGB_DEFAULT,
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 1)
        put("sim", sim); put("simStrength", simStrength.toDouble()); put("exposureThirds", exposureThirds)
        put("grain", grain); put("colorChrome", colorChrome); put("colorChromeBlue", colorChromeBlue)
        put("wb", wb); put("kelvin", kelvin); put("wbShiftR", wbShiftR); put("wbShiftB", wbShiftB)
        put("dynamicRange", dynamicRange); put("dRangePriority", dRangePriority)
        put("highlight", highlight.toDouble()); put("shadow", shadow.toDouble())
        put("color", color); put("sharpness", sharpness); put("noiseReduction", noiseReduction)
        put("clarity", clarity); put("monoWC", monoWC); put("monoMG", monoMG); put("smoothSkin", smoothSkin)
        put("contrast", contrast); put("highlights", highlights); put("shadows", shadows)
        put("whites", whites); put("blacks", blacks); put("temp", temp); put("tint", tint)
        put("vibrance", vibrance); put("saturation", saturation); put("fade", fade); put("vignette", vignette)
        put("glow", glow); put("glowStyle", glowStyle); put("glowThreshold", glowThreshold)
        put("glowSize", glowSize); put("glowWarmth", glowWarmth)
        put("hslHue", JSONArray(hslHue)); put("hslSat", JSONArray(hslSat)); put("hslLum", JSONArray(hslLum))
        fun curveJson(c: List<Float>) = JSONArray().apply { c.forEach { put(it.toDouble()) } }
        put("curveY", curveJson(curveY)); put("curveR", curveJson(curveR))
        put("curveG", curveJson(curveG)); put("curveB", curveJson(curveB))
        put("curveLinked", curveLinked); put("curveMix", JSONArray(curveMix))
        put("softLow", softLow); put("softHigh", softHigh); put("softLS", softLS); put("softHS", softHS)
        put("softLink", softLink); put("softRGB", JSONArray(softRGB))
        put("aspect", aspect); put("aspectFlip", aspectFlip); put("rotation", rotation); put("flipH", flipH)
        put("straighten", straighten.toDouble())
        put("cropL", cropL.toDouble()); put("cropT", cropT.toDouble()); put("cropR", cropR.toDouble()); put("cropB", cropB.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): EditParams {
            fun i(k: String, min: Int, max: Int, def: Int = 0) = o.optInt(k, def).coerceIn(min, max)
            fun f(k: String, min: Float, max: Float, def: Float = 0f) =
                o.optDouble(k, def.toDouble()).toFloat().let { if (it.isNaN()) def else it.coerceIn(min, max) }
            fun curve(k: String): List<Float> {
                val a = o.optJSONArray(k) ?: return IDENTITY_CURVE
                return sanitizeCurve(List(a.length()) { idx -> a.optDouble(idx, 0.0).toFloat() })
            }
            fun list(k: String): List<Int> {
                val a = o.optJSONArray(k) ?: return ZERO8
                return List(8) { idx -> a.optInt(idx, 0).coerceIn(-100, 100) }
            }
            return EditParams(
                sim = i("sim", 0, FILM_SIMS.lastIndex),
                simStrength = f("simStrength", 0f, 1f, 1f),
                exposureThirds = i("exposureThirds", -9, 9),
                grain = i("grain", 0, GRAIN_OPTIONS.lastIndex),
                colorChrome = i("colorChrome", 0, 2),
                colorChromeBlue = i("colorChromeBlue", 0, 2),
                wb = i("wb", 0, WB_PRESETS.lastIndex),
                kelvin = i("kelvin", 2500, 10000, 5500),
                wbShiftR = i("wbShiftR", -9, 9),
                wbShiftB = i("wbShiftB", -9, 9),
                dynamicRange = i("dynamicRange", 0, 2),
                dRangePriority = i("dRangePriority", 0, 3),
                highlight = f("highlight", -2f, 4f),
                shadow = f("shadow", -2f, 4f),
                color = i("color", -4, 4),
                sharpness = i("sharpness", -4, 4),
                noiseReduction = i("noiseReduction", -4, 4),
                clarity = i("clarity", -5, 5),
                monoWC = i("monoWC", -9, 9),
                monoMG = i("monoMG", -9, 9),
                smoothSkin = i("smoothSkin", 0, 2),
                contrast = i("contrast", -100, 100),
                highlights = i("highlights", -100, 100),
                shadows = i("shadows", -100, 100),
                whites = i("whites", -100, 100),
                blacks = i("blacks", -100, 100),
                temp = i("temp", -100, 100),
                tint = i("tint", -100, 100),
                vibrance = i("vibrance", -100, 100),
                saturation = i("saturation", -100, 100),
                fade = i("fade", 0, 100),
                vignette = i("vignette", -100, 100),
                glow = i("glow", 0, 100), glowStyle = i("glowStyle", 0, 2),
                glowThreshold = i("glowThreshold", 0, 100, 70), glowSize = i("glowSize", 0, 100, 40),
                glowWarmth = i("glowWarmth", 0, 100, 30),
                hslHue = list("hslHue"), hslSat = list("hslSat"), hslLum = list("hslLum"),
                curveY = curve("curveY"), curveR = curve("curveR"), curveG = curve("curveG"), curveB = curve("curveB"),
                curveLinked = o.optBoolean("curveLinked", true),
                curveMix = o.optJSONArray("curveMix")?.let { a -> List(4) { idx -> a.optInt(idx, 100).coerceIn(0, 100) } } ?: FULL_MIX,
                softLow = i("softLow", 0, 100, 0), softHigh = i("softHigh", 0, 100, 100),
                softLS = i("softLS", 0, 100, 0), softHS = i("softHS", 0, 100, 0),
                softLink = o.optBoolean("softLink", true),
                softRGB = o.optJSONArray("softRGB")?.takeIf { it.length() == 12 }
                    ?.let { a -> List(12) { idx -> a.optInt(idx, SOFT_RGB_DEFAULT[idx]).coerceIn(0, 100) } }
                    ?: SOFT_RGB_DEFAULT,
                // Before free crop, aspect 0 ("Original") meant no crop: now that is Free.
                aspect = if (!o.has("cropL") && o.optInt("aspect", 0) == 0) ASPECT_FREE else i("aspect", 0, ASPECTS.lastIndex, ASPECT_FREE),
                aspectFlip = o.optBoolean("aspectFlip", false),
                straighten = f("straighten", -45f, 45f),
                cropL = f("cropL", 0f, 0.99f), cropT = f("cropT", 0f, 0.99f),
                cropR = f("cropR", 0.01f, 1f, 1f), cropB = f("cropB", 0.01f, 1f, 1f),
                rotation = (o.optInt("rotation", 0) / 90 * 90).let { ((it % 360) + 360) % 360 },
                flipH = o.optBoolean("flipH", false),
            )
        }
    }
}

/** Dynamic range / highlight / shadow actually used, after D-Range Priority. */
data class Tone(val dr: Int, val highlight: Float, val shadow: Float)

fun EditParams.effectiveTone(): Tone = when (dRangePriority) {
    1, 3 -> Tone(maxOf(dynamicRange.coerceIn(0, 2), 1), -1f, -1f) // Weak / Auto
    2 -> Tone(2, -2f, -2f)                                         // Strong
    else -> Tone(dynamicRange.coerceIn(0, 2), highlight, shadow)
}

// ---------- Formatting helpers shared by the UI and export ----------

fun signed(v: Float, step: Float = 1f): String {
    val txt = if (step < 1f && v != v.roundToInt().toFloat()) String.format(java.util.Locale.US, "%.1f", v)
    else v.roundToInt().toString()
    return when {
        v > 0f -> "+$txt"
        v < 0f -> txt.replace("-", "−")
        else -> "0"
    }
}

fun formatThirds(t: Int): String {
    if (t == 0) return "±0"
    val a = kotlin.math.abs(t)
    val whole = a / 3
    val frac = a % 3
    val body = buildString {
        if (whole > 0) append(whole)
        if (frac > 0) { if (whole > 0) append(' '); append(frac).append("/3") }
    }
    return (if (t > 0) "+" else "−") + body
}

/** Compact one-line description of the camera settings, e.g. for recipe cards. */
fun EditParams.summary(): String = buildString {
    append("GRAIN ").append(GRAIN_SHORT[grain])
    if (colorChrome > 0) append("  CC ").append(CHROME_OPTIONS[colorChrome].uppercase())
    if (colorChromeBlue > 0) append("  CCB ").append(CHROME_OPTIONS[colorChromeBlue].uppercase())
    val wbLabel = if (WB_PRESETS[wb].type == WbType.CUSTOM) "${kelvin}K" else WB_PRESETS[wb].label.uppercase()
    append("  WB ").append(wbLabel)
    if (wbShiftR != 0 || wbShiftB != 0) append(" R").append(signed(wbShiftR.toFloat())).append(" B").append(signed(wbShiftB.toFloat()))
    val tone = effectiveTone()
    append("  ").append(DR_OPTIONS[tone.dr])
    if (dRangePriority > 0) append(" DRP-").append(DRP_OPTIONS[dRangePriority].uppercase())
    append("  H").append(signed(tone.highlight, 0.5f)).append(" S").append(signed(tone.shadow, 0.5f))
    if (FILM_SIMS[sim].isMono && (monoWC != 0 || monoMG != 0)) append("  WC").append(signed(monoWC.toFloat())).append(" MG").append(signed(monoMG.toFloat()))
    if (color != 0) append("  COLOR ").append(signed(color.toFloat()))
    if (exposureThirds != 0) append("  EV ").append(formatThirds(exposureThirds))
}
