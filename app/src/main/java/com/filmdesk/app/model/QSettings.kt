package com.filmdesk.app.model

import kotlin.math.roundToInt

/** One tile of the Fujifilm-style Q menu. */
sealed class QSetting(val id: String, val label: String, val hint: String) {
    abstract fun display(p: EditParams): String
    abstract fun isDefault(p: EditParams): Boolean

    class Choice(
        id: String, label: String, hint: String,
        val options: List<String>,
        val get: (EditParams) -> Int,
        val set: (EditParams, Int) -> EditParams,
    ) : QSetting(id, label, hint) {
        override fun display(p: EditParams) = options[get(p).coerceIn(0, options.lastIndex)]
        override fun isDefault(p: EditParams) = get(p) == get(EditParams())
    }

    class Stepper(
        id: String, label: String, hint: String,
        val min: Float, val max: Float, val step: Float,
        val format: (Float) -> String,
        val get: (EditParams) -> Float,
        val set: (EditParams, Float) -> EditParams,
    ) : QSetting(id, label, hint) {
        val steps: Int get() = ((max - min) / step).roundToInt()
        fun snap(v: Float) = ((v / step).roundToInt() * step).coerceIn(min, max)
        override fun display(p: EditParams) = format(get(p))
        override fun isDefault(p: EditParams) = get(p) == get(EditParams())
    }
}

private fun intStep(v: Float) = signed(v)

val Q_SETTINGS: List<QSetting> = listOf(
    QSetting.Choice("sim", "Film Sim", "20 film simulations",
        FILM_SIMS.map { it.short }, { it.sim }, { p, v -> p.copy(sim = v) }),
    QSetting.Stepper("exp", "Exposure", "1/3-stop steps, ±3 EV", -9f, 9f, 1f,
        { formatThirds(it.roundToInt()) }, { it.exposureThirds.toFloat() }, { p, v -> p.copy(exposureThirds = v.roundToInt()) }),
    QSetting.Choice("grain", "Grain", "Roughness · size",
        GRAIN_OPTIONS, { it.grain }, { p, v -> p.copy(grain = v) }),
    QSetting.Choice("cc", "Color Chrome", "Deeper tones in strongly coloured areas",
        CHROME_OPTIONS, { it.colorChrome }, { p, v -> p.copy(colorChrome = v) }),
    QSetting.Choice("ccb", "CC FX Blue", "Deeper, richer blues",
        CHROME_OPTIONS, { it.colorChromeBlue }, { p, v -> p.copy(colorChromeBlue = v) }),
    QSetting.Choice("wb", "White Bal.", "Preset, auto or Kelvin",
        WB_PRESETS.map { it.label }, { it.wb }, { p, v -> p.copy(wb = v) }),
    QSetting.Stepper("kelvin", "Kelvin", "Used when White Balance = Kelvin", 2500f, 10000f, 100f,
        { "${it.roundToInt()}K" }, { it.kelvin.toFloat() }, { p, v -> p.copy(kelvin = v.roundToInt(), wb = WB_PRESETS.lastIndex) }),
    QSetting.Stepper("wbr", "WB Shift R", "Red (+) ↔ Cyan (−)", -9f, 9f, 1f,
        ::intStep, { it.wbShiftR.toFloat() }, { p, v -> p.copy(wbShiftR = v.roundToInt()) }),
    QSetting.Stepper("wbb", "WB Shift B", "Blue (+) ↔ Yellow (−)", -9f, 9f, 1f,
        ::intStep, { it.wbShiftB.toFloat() }, { p, v -> p.copy(wbShiftB = v.roundToInt()) }),
    QSetting.Choice("dr", "Dyn. Range", "Highlight roll-off",
        DR_OPTIONS, { it.dynamicRange }, { p, v -> p.copy(dynamicRange = v) }),
    QSetting.Choice("drp", "D-Range Pri.", "Overrides DR, Highlight and Shadow",
        DRP_OPTIONS, { it.dRangePriority }, { p, v -> p.copy(dRangePriority = v) }),
    QSetting.Stepper("hl", "Highlight", "Tone curve −2 (soft) to +4 (hard)", -2f, 4f, 0.5f,
        { signed(it, 0.5f) }, { it.highlight }, { p, v -> p.copy(highlight = v) }),
    QSetting.Stepper("sh", "Shadow", "Tone curve −2 (soft) to +4 (hard)", -2f, 4f, 0.5f,
        { signed(it, 0.5f) }, { it.shadow }, { p, v -> p.copy(shadow = v) }),
    QSetting.Stepper("color", "Color", "Saturation −4 to +4", -4f, 4f, 1f,
        ::intStep, { it.color.toFloat() }, { p, v -> p.copy(color = v.roundToInt()) }),
    QSetting.Stepper("sharp", "Sharpness", "−4 to +4", -4f, 4f, 1f,
        ::intStep, { it.sharpness.toFloat() }, { p, v -> p.copy(sharpness = v.roundToInt()) }),
    QSetting.Stepper("nr", "High ISO NR", "Noise reduction −4 to +4", -4f, 4f, 1f,
        ::intStep, { it.noiseReduction.toFloat() }, { p, v -> p.copy(noiseReduction = v.roundToInt()) }),
    QSetting.Stepper("clarity", "Clarity", "Mid-tone contrast −5 to +5", -5f, 5f, 1f,
        ::intStep, { it.clarity.toFloat() }, { p, v -> p.copy(clarity = v.roundToInt()) }),
    QSetting.Stepper("mwc", "Mono WC", "B&W tone: warm (+) ↔ cool (−)", -9f, 9f, 1f,
        ::intStep, { it.monoWC.toFloat() }, { p, v -> p.copy(monoWC = v.roundToInt()) }),
    QSetting.Stepper("mmg", "Mono MG", "B&W tone: magenta (+) ↔ green (−)", -9f, 9f, 1f,
        ::intStep, { it.monoMG.toFloat() }, { p, v -> p.copy(monoMG = v.roundToInt()) }),
    QSetting.Choice("skin", "Smooth Skin", "Softens skin texture",
        SKIN_OPTIONS, { it.smoothSkin }, { p, v -> p.copy(smoothSkin = v) }),
)

/** Pro-tool slider definition (all −100..100 unless stated). */
class ProSlider(
    val label: String,
    val min: Int,
    val max: Int,
    val get: (EditParams) -> Int,
    val set: (EditParams, Int) -> EditParams,
)

class ProGroup(val label: String, val sliders: List<ProSlider>)

val PRO_GROUPS: List<ProGroup> = listOf(
    ProGroup("Light", listOf(
        ProSlider("Contrast", -100, 100, { it.contrast }, { p, v -> p.copy(contrast = v) }),
        ProSlider("Highlights", -100, 100, { it.highlights }, { p, v -> p.copy(highlights = v) }),
        ProSlider("Shadows", -100, 100, { it.shadows }, { p, v -> p.copy(shadows = v) }),
        ProSlider("Whites", -100, 100, { it.whites }, { p, v -> p.copy(whites = v) }),
        ProSlider("Blacks", -100, 100, { it.blacks }, { p, v -> p.copy(blacks = v) }),
        ProSlider("Fade", 0, 100, { it.fade }, { p, v -> p.copy(fade = v) }),
    )),
    ProGroup("Color", listOf(
        ProSlider("Temperature", -100, 100, { it.temp }, { p, v -> p.copy(temp = v) }),
        ProSlider("Tint", -100, 100, { it.tint }, { p, v -> p.copy(tint = v) }),
        ProSlider("Vibrance", -100, 100, { it.vibrance }, { p, v -> p.copy(vibrance = v) }),
        ProSlider("Saturation", -100, 100, { it.saturation }, { p, v -> p.copy(saturation = v) }),
    )),
    ProGroup("HSL", emptyList()),
    ProGroup("Effects", listOf(
        ProSlider("Vignette", -100, 100, { it.vignette }, { p, v -> p.copy(vignette = v) }),
    )),
)

fun hslSliders(band: Int): List<ProSlider> = listOf(
    ProSlider("Hue", -100, 100, { it.hslHue[band] }, { p, v -> p.copy(hslHue = p.hslHue.toMutableList().also { l -> l[band] = v }) }),
    ProSlider("Saturation", -100, 100, { it.hslSat[band] }, { p, v -> p.copy(hslSat = p.hslSat.toMutableList().also { l -> l[band] = v }) }),
    ProSlider("Luminance", -100, 100, { it.hslLum[band] }, { p, v -> p.copy(hslLum = p.hslLum.toMutableList().also { l -> l[band] = v }) }),
)

/**
 * Why a setting currently has no effect (null when it is active). The Q menu shows the
 * value dimmed and explains this instead of offering controls, like the camera does.
 */
fun QSetting.lockReason(p: EditParams): String? = when (id) {
    "dr", "hl", "sh" ->
        if (p.dRangePriority > 0) "Set automatically by D-Range Priority. Turn D-Range Priority off to change it." else null
    "mwc", "mmg" ->
        if (!FILM_SIMS[p.sim].isMono) "Only used by black & white film simulations (Acros, Monochrome, Sepia)." else null
    else -> null
}

/** The value as it is really applied (e.g. D-Range Priority overrides DR, Highlight, Shadow). */
fun QSetting.effectiveDisplay(p: EditParams): String {
    if (p.dRangePriority > 0 && (id == "dr" || id == "hl" || id == "sh")) {
        val t = p.effectiveTone()
        return when (id) {
            "dr" -> DR_OPTIONS[t.dr]
            "hl" -> signed(t.highlight, 0.5f)
            else -> signed(t.shadow, 0.5f)
        }
    }
    return display(p)
}
