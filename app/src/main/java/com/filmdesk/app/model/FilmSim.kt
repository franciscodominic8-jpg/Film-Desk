package com.filmdesk.app.model

/** A colour range (OKLab hue, degrees) that a film simulation treats in its own way. */
class HueBand(
    val center: Float,
    val width: Float,
    /** Hue turn in degrees (+ = towards the next colour round the wheel). */
    val shift: Float = 0f,
    /** Saturation multiplier for this range. */
    val sat: Float = 1f,
    /** Brightness change for this range (OKLab L). */
    val lum: Float = 0f,
)

/**
 * A film simulation "look", modelled after Fujifilm's published character of each
 * simulation: separate highlight and shadow tone hardness, overall and midtone colour,
 * per-colour hue/saturation/brightness shifts, and tinted shadows and highlights.
 * Worked out in OKLab so brightness and colour can be changed independently.
 * These are careful approximations, not Fujifilm's own (secret) processing.
 */
class FilmSim(
    val name: String,
    val short: String,
    val description: String,
    /** Shadow tone hardness: + darker, harder shadows; − lifted, softer. */
    val shadowTone: Float = 0f,
    /** Highlight tone hardness: + brighter, harder highlights; − softer. */
    val highlightTone: Float = 0f,
    val saturation: Float = 1f,
    /** Extra colour in the midtones (Classic Neg). */
    val midSat: Float = 0f,
    val bands: List<HueBand> = emptyList(),
    /** OKLab (a, b) tint added to shadows / highlights. */
    val shadowTint: FloatArray = NO_TINT,
    val highlightTint: FloatArray = NO_TINT,
    /** Channel weights for black & white sims (simulates colour filters). */
    val mono: FloatArray? = null,
    val sepia: Boolean = false,
    /** Fallback swatch colour (ARGB) for lists before thumbnails are ready. */
    val swatch: Long = 0xFF7FA9D6,
) {
    val isMono: Boolean get() = mono != null
}

private val NO_TINT = floatArrayOf(0f, 0f)
private fun ab(a: Float, b: Float) = floatArrayOf(a, b)
private val BW = floatArrayOf(0.30f, 0.59f, 0.11f)
private val BW_YE = floatArrayOf(0.38f, 0.56f, 0.06f)
private val BW_R = floatArrayOf(0.60f, 0.35f, 0.05f)
private val BW_G = floatArrayOf(0.22f, 0.70f, 0.08f)

// OKLab hue guide: red ≈ 25°, orange/skin ≈ 55°, yellow ≈ 100°, green ≈ 140°, cyan ≈ 200°, blue ≈ 265°, purple ≈ 305°.
val FILM_SIMS: List<FilmSim> = listOf(
    FilmSim("Provia / Standard", "Provia", "Natural, all-round colour",
        shadowTone = 0.10f, highlightTone = 0.08f, saturation = 1.04f, swatch = 0xFF7FA9D6),
    FilmSim("Velvia / Vivid", "Velvia", "Saturated, punchy landscapes",
        shadowTone = 0.28f, highlightTone = 0.12f, saturation = 1.30f,
        bands = listOf(HueBand(25f, 40f, -6f, 1.10f, -0.01f), HueBand(140f, 45f, 0f, 1.15f, -0.01f),
            HueBand(265f, 45f, -4f, 1.18f, -0.035f), HueBand(305f, 40f, 0f, 1.2f)),
        swatch = 0xFF4C8FE0),
    FilmSim("Astia / Soft", "Astia", "Soft contrast, gentle skin tones",
        shadowTone = 0f, highlightTone = -0.06f, saturation = 1.10f,
        bands = listOf(HueBand(55f, 30f, 0f, 0.88f, 0.015f), HueBand(265f, 45f, 0f, 1.05f, 0.02f)),
        swatch = 0xFF9DBBDD),
    FilmSim("Classic Chrome", "Classic Chrome", "Muted colour, firm shadows — documentary look",
        shadowTone = 0.32f, highlightTone = -0.06f, saturation = 0.74f,
        bands = listOf(HueBand(265f, 50f, -14f, 0.80f), HueBand(140f, 45f, -8f, 0.72f, -0.01f),
            HueBand(25f, 35f, 6f, 0.85f), HueBand(305f, 40f, -10f, 0.6f)),
        shadowTint = ab(-0.006f, -0.008f), highlightTint = ab(0.004f, 0.008f),
        swatch = 0xFF8AA0AE),
    FilmSim("Reala Ace", "Reala Ace", "True-to-life colour with crisp tone",
        shadowTone = 0.22f, highlightTone = 0.18f, saturation = 1.02f, swatch = 0xFF85ABD0),
    FilmSim("Pro Neg. Hi", "Pro Neg Hi", "Portrait negative, a little more contrast",
        shadowTone = 0.14f, highlightTone = 0.04f, saturation = 0.92f,
        bands = listOf(HueBand(55f, 30f, 0f, 0.95f, 0.01f)), swatch = 0xFF8EAAC6),
    FilmSim("Pro Neg. Std", "Pro Neg Std", "Portrait negative, flat and soft",
        shadowTone = -0.06f, highlightTone = -0.10f, saturation = 0.84f,
        bands = listOf(HueBand(55f, 30f, 0f, 0.92f, 0.012f)), swatch = 0xFF9AB0C4),
    FilmSim("Classic Neg.", "Classic Neg", "Hard tone, teal skies, cyan shadows",
        shadowTone = 0.38f, highlightTone = 0.30f, saturation = 0.86f, midSat = 0.30f,
        bands = listOf(HueBand(265f, 55f, -28f, 0.85f), HueBand(200f, 40f, -6f, 0.9f),
            HueBand(140f, 45f, 10f, 0.78f, -0.015f), HueBand(100f, 30f, -10f, 0.9f), HueBand(25f, 35f, -4f, 1.0f, -0.01f)),
        shadowTint = ab(-0.014f, -0.012f), highlightTint = ab(0.004f, 0.010f),
        swatch = 0xFF7FA2A8),
    FilmSim("Nostalgic Neg.", "Nostalgic Neg", "Amber highlights, rich shadows",
        shadowTone = 0.18f, highlightTone = -0.08f, saturation = 0.90f,
        bands = listOf(HueBand(140f, 45f, -14f, 0.85f), HueBand(265f, 45f, 0f, 0.78f)),
        shadowTint = ab(0.004f, 0.006f), highlightTint = ab(0.008f, 0.028f),
        swatch = 0xFFA9B6B8),
    FilmSim("Eterna / Cinema", "Eterna", "Cinematic: low saturation, soft shadows",
        shadowTone = -0.16f, highlightTone = -0.18f, saturation = 0.60f,
        bands = listOf(HueBand(55f, 30f, 0f, 1.1f)),
        shadowTint = ab(-0.010f, -0.010f), highlightTint = ab(0.002f, 0.006f),
        swatch = 0xFF8FA3A8),
    FilmSim("Eterna Bleach Bypass", "Bleach Bypass", "Desaturated with high contrast",
        shadowTone = 0.42f, highlightTone = 0.30f, saturation = 0.40f, swatch = 0xFFA7B1B4),
    FilmSim("Acros", "Acros", "Fine-grain B&W with deep tones",
        shadowTone = 0.22f, highlightTone = 0.14f, saturation = 0f, mono = BW, swatch = 0xFFB8B8B8),
    FilmSim("Acros + Ye Filter", "Acros+Ye", "B&W, yellow filter — slightly darker sky",
        shadowTone = 0.24f, highlightTone = 0.16f, saturation = 0f, mono = BW_YE, swatch = 0xFF9A9A9A),
    FilmSim("Acros + R Filter", "Acros+R", "B&W, red filter — dramatic skies",
        shadowTone = 0.28f, highlightTone = 0.20f, saturation = 0f, mono = BW_R, swatch = 0xFF6A6A6A),
    FilmSim("Acros + G Filter", "Acros+G", "B&W, green filter — lighter foliage and skin",
        shadowTone = 0.22f, highlightTone = 0.14f, saturation = 0f, mono = BW_G, swatch = 0xFFA8A8A8),
    FilmSim("Monochrome", "Mono", "Standard B&W",
        shadowTone = 0.06f, highlightTone = 0.04f, saturation = 0f, mono = BW, swatch = 0xFFADADAD),
    FilmSim("Monochrome + Ye", "Mono+Ye", "Standard B&W, yellow filter",
        shadowTone = 0.08f, highlightTone = 0.06f, saturation = 0f, mono = BW_YE, swatch = 0xFF9E9E9E),
    FilmSim("Monochrome + R", "Mono+R", "Standard B&W, red filter",
        shadowTone = 0.12f, highlightTone = 0.08f, saturation = 0f, mono = BW_R, swatch = 0xFF707070),
    FilmSim("Monochrome + G", "Mono+G", "Standard B&W, green filter",
        shadowTone = 0.06f, highlightTone = 0.04f, saturation = 0f, mono = BW_G, swatch = 0xFFABABAB),
    FilmSim("Sepia", "Sepia", "Warm brown-toned B&W",
        shadowTone = 0.04f, highlightTone = 0.02f, saturation = 0f, mono = BW, sepia = true, swatch = 0xFFC9B08A),
)
