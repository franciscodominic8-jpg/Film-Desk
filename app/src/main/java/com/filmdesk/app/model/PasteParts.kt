package com.filmdesk.app.model

/** The groups of settings that "Paste edits" can copy from one photo to others. */
enum class PastePart(val title: String, val subtitle: String, val defaultOn: Boolean) {
    Film("Film Simulation", "Simulation and its strength", true),
    QMenu("Q Menu settings", "WB, Dynamic Range, Highlight/Shadow, Colour, Grain, Chrome FX", true),
    Light("Pro · Light", "Contrast, Highlights, Shadows, Whites, Blacks, Fade, Vignette", true),
    Color("Pro · Colour & HSL", "Temperature, Tint, Vibrance, Saturation, HSL", true),
    Curve("Curve", "YRGB curves, Intensity, Soft Clip", true),
    Detail("Detail", "Sharpness, Noise reduction, Clarity, Smooth skin", false),
    Crop("Crop & Rotate", "Usually different for each photo", false),
}

val DEFAULT_PASTE_PARTS: Set<PastePart> = PastePart.entries.filter { it.defaultOn }.toSet()

/** This photo's edits with the chosen [parts] taken from [src]. */
fun EditParams.pasteFrom(src: EditParams, parts: Set<PastePart>): EditParams {
    var p = this
    for (part in parts) p = when (part) {
        PastePart.Film -> p.copy(sim = src.sim, simStrength = src.simStrength)
        PastePart.QMenu -> p.copy(
            exposureThirds = src.exposureThirds, grain = src.grain,
            colorChrome = src.colorChrome, colorChromeBlue = src.colorChromeBlue,
            wb = src.wb, kelvin = src.kelvin, wbShiftR = src.wbShiftR, wbShiftB = src.wbShiftB,
            dynamicRange = src.dynamicRange, dRangePriority = src.dRangePriority,
            highlight = src.highlight, shadow = src.shadow, color = src.color,
            monoWC = src.monoWC, monoMG = src.monoMG,
        )
        PastePart.Light -> p.copy(
            contrast = src.contrast, highlights = src.highlights, shadows = src.shadows,
            whites = src.whites, blacks = src.blacks, fade = src.fade, vignette = src.vignette,
        )
        PastePart.Color -> p.copy(
            temp = src.temp, tint = src.tint, vibrance = src.vibrance, saturation = src.saturation,
            hslHue = src.hslHue, hslSat = src.hslSat, hslLum = src.hslLum,
        )
        PastePart.Curve -> p.copy(
            curveY = src.curveY, curveR = src.curveR, curveG = src.curveG, curveB = src.curveB,
            curveLinked = src.curveLinked, curveMix = src.curveMix,
            softLow = src.softLow, softHigh = src.softHigh, softLS = src.softLS, softHS = src.softHS,
        )
        PastePart.Detail -> p.copy(
            sharpness = src.sharpness, noiseReduction = src.noiseReduction,
            clarity = src.clarity, smoothSkin = src.smoothSkin,
        )
        PastePart.Crop -> p.withGeometryOf(src)
    }
    return p
}

/** Short summary of copied edits, e.g. "Classic Chrome · WB Auto · Grain Weak · Curve". */
fun EditParams.copySummary(): String {
    val d = EditParams()
    val parts = ArrayList<String>()
    parts += FILM_SIMS[sim].name
    if (wb != d.wb) parts += "WB " + WB_PRESETS[wb].label
    if (grain != d.grain) parts += "Grain " + GRAIN_OPTIONS[grain]
    if (hasCurves || hasSoftClip) parts += "Curve"
    return parts.joinToString(" · ")
}
