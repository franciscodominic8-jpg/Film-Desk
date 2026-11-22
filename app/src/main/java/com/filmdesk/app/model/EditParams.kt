package com.filmdesk.app.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private val ZERO8: List<Int> = List(8) { 0 }

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
    val hslHue: List<Int> = ZERO8,
    val hslSat: List<Int> = ZERO8,
    val hslLum: List<Int> = ZERO8,
    // ---- Geometry ----
    val aspect: Int = 0,
    val rotation: Int = 0,
    val flipH: Boolean = false,
) {
    /** Same look with this photo's crop/rotation kept (used when applying a recipe). */
    fun withGeometryOf(other: EditParams) = copy(aspect = other.aspect, rotation = other.rotation, flipH = other.flipH)

    fun withoutGeometry() = copy(aspect = 0, rotation = 0, flipH = false)

    /** Resets only the Fujifilm Q-menu settings. */
    fun resetCamera() = EditParams().copy(
        contrast = contrast, highlights = highlights, shadows = shadows, whites = whites, blacks = blacks,
        temp = temp, tint = tint, vibrance = vibrance, saturation = saturation, fade = fade, vignette = vignette,
        hslHue = hslHue, hslSat = hslSat, hslLum = hslLum,
        aspect = aspect, rotation = rotation, flipH = flipH,
    )

    /** Resets only the Pro adjustments. */
    fun resetPro() = copy(
        contrast = 0, highlights = 0, shadows = 0, whites = 0, blacks = 0, temp = 0, tint = 0,
        vibrance = 0, saturation = 0, fade = 0, vignette = 0, hslHue = ZERO8, hslSat = ZERO8, hslLum = ZERO8,
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
        put("hslHue", JSONArray(hslHue)); put("hslSat", JSONArray(hslSat)); put("hslLum", JSONArray(hslLum))
        put("aspect", aspect); put("rotation", rotation); put("flipH", flipH)
    }

    companion object {
        fun fromJson(o: JSONObject): EditParams {
            fun i(k: String, min: Int, max: Int, def: Int = 0) = o.optInt(k, def).coerceIn(min, max)
            fun f(k: String, min: Float, max: Float, def: Float = 0f) =
                o.optDouble(k, def.toDouble()).toFloat().let { if (it.isNaN()) def else it.coerceIn(min, max) }
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
                hslHue = list("hslHue"), hslSat = list("hslSat"), hslLum = list("hslLum"),
                aspect = i("aspect", 0, ASPECTS.lastIndex),
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

fun EditParams.caption(): String =
    FILM_SIMS[sim].name.uppercase() + "   ·   " + summary()
