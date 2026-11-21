package com.filmdesk.app.model

/**
 * A film simulation "look". These are hand-tuned approximations of each Fujifilm
 * simulation's character (saturation, contrast, colour shifts, tinted shadows and
 * highlights, B&W filter response). They are not Fujifilm's own algorithms.
 * For closer matches, replace them with 3D LUTs measured from real camera files.
 */
class FilmSim(
    val name: String,
    val short: String,
    val description: String,
    val saturation: Float,
    val contrast: Float,
    val toe: Float = 0f,
    /** Row-major 3x3 colour matrix applied before everything else, or null for identity. */
    val matrix: FloatArray? = null,
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

private val NO_TINT = floatArrayOf(0f, 0f, 0f)
private fun tint(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)
private val BW = floatArrayOf(0.30f, 0.59f, 0.11f)
private val BW_YE = floatArrayOf(0.38f, 0.56f, 0.06f)
private val BW_R = floatArrayOf(0.60f, 0.35f, 0.05f)
private val BW_G = floatArrayOf(0.22f, 0.70f, 0.08f)

val FILM_SIMS: List<FilmSim> = listOf(
    FilmSim("Provia / Standard", "Provia", "Natural, all-round colour",
        saturation = 1.05f, contrast = 0.12f, swatch = 0xFF7FA9D6),
    FilmSim("Velvia / Vivid", "Velvia", "Saturated, punchy landscapes",
        saturation = 1.38f, contrast = 0.22f,
        matrix = floatArrayOf(1.04f, -0.02f, -0.02f, -0.02f, 1.04f, -0.02f, -0.03f, -0.03f, 1.06f),
        swatch = 0xFF4C8FE0),
    FilmSim("Astia / Soft", "Astia", "Soft contrast, gentle skin tones",
        saturation = 1.08f, contrast = 0.05f,
        shadowTint = tint(0f, 0f, 0.006f), highlightTint = tint(0.010f, 0.005f, 0f),
        swatch = 0xFF9DBBDD),
    FilmSim("Classic Chrome", "Classic Chrome", "Muted colour, firm tone — documentary look",
        saturation = 0.78f, contrast = 0.20f,
        matrix = floatArrayOf(0.95f, 0.05f, 0f, 0.02f, 0.95f, 0.03f, 0f, 0.08f, 0.92f),
        shadowTint = tint(0f, 0.005f, 0.012f), highlightTint = tint(0.012f, 0.008f, -0.005f),
        swatch = 0xFF8AA0AE),
    FilmSim("Reala Ace", "Reala Ace", "True-to-life colour with crisp tone",
        saturation = 1.02f, contrast = 0.16f, swatch = 0xFF85ABD0),
    FilmSim("Pro Neg. Hi", "Pro Neg Hi", "Portrait negative, a little more contrast",
        saturation = 0.92f, contrast = 0.12f, highlightTint = tint(0.006f, 0.003f, 0f),
        swatch = 0xFF8EAAC6),
    FilmSim("Pro Neg. Std", "Pro Neg Std", "Portrait negative, flat and soft",
        saturation = 0.88f, contrast = 0.02f, toe = 0.01f, swatch = 0xFF9AB0C4),
    FilmSim("Classic Neg.", "Classic Neg", "Snapshot negative, hard shadows, shifted hues",
        saturation = 0.90f, contrast = 0.26f,
        matrix = floatArrayOf(0.92f, 0.10f, -0.02f, 0f, 0.92f, 0.08f, -0.02f, 0.12f, 0.90f),
        shadowTint = tint(-0.010f, 0.010f, 0.020f), highlightTint = tint(0.020f, 0.010f, -0.020f),
        swatch = 0xFF7FA2A8),
    FilmSim("Nostalgic Neg.", "Nostalgic Neg", "Amber highlights, rich shadows",
        saturation = 0.90f, contrast = 0.14f, toe = 0.01f,
        shadowTint = tint(0.005f, 0f, -0.005f), highlightTint = tint(0.030f, 0.015f, -0.025f),
        swatch = 0xFFA9B6B8),
    FilmSim("Eterna / Cinema", "Eterna", "Cinematic: low saturation, soft shadows",
        saturation = 0.70f, contrast = -0.05f, toe = 0.025f,
        shadowTint = tint(0f, 0.008f, 0.012f), highlightTint = tint(0.005f, 0.005f, -0.005f),
        swatch = 0xFF8FA3A8),
    FilmSim("Eterna Bleach Bypass", "Bleach Bypass", "Desaturated with high contrast",
        saturation = 0.45f, contrast = 0.35f, swatch = 0xFFA7B1B4),
    FilmSim("Acros", "Acros", "Fine-grain B&W with deep tones",
        saturation = 0f, contrast = 0.18f, mono = BW, swatch = 0xFFB8B8B8),
    FilmSim("Acros + Ye Filter", "Acros+Ye", "B&W, yellow filter — slightly darker sky",
        saturation = 0f, contrast = 0.20f, mono = BW_YE, swatch = 0xFF9A9A9A),
    FilmSim("Acros + R Filter", "Acros+R", "B&W, red filter — dramatic skies",
        saturation = 0f, contrast = 0.24f, mono = BW_R, swatch = 0xFF6A6A6A),
    FilmSim("Acros + G Filter", "Acros+G", "B&W, green filter — lighter foliage and skin",
        saturation = 0f, contrast = 0.18f, mono = BW_G, swatch = 0xFFA8A8A8),
    FilmSim("Monochrome", "Mono", "Standard B&W",
        saturation = 0f, contrast = 0.08f, mono = BW, swatch = 0xFFADADAD),
    FilmSim("Monochrome + Ye", "Mono+Ye", "Standard B&W, yellow filter",
        saturation = 0f, contrast = 0.10f, mono = BW_YE, swatch = 0xFF9E9E9E),
    FilmSim("Monochrome + R", "Mono+R", "Standard B&W, red filter",
        saturation = 0f, contrast = 0.14f, mono = BW_R, swatch = 0xFF707070),
    FilmSim("Monochrome + G", "Mono+G", "Standard B&W, green filter",
        saturation = 0f, contrast = 0.08f, mono = BW_G, swatch = 0xFFABABAB),
    FilmSim("Sepia", "Sepia", "Warm brown-toned B&W",
        saturation = 0f, contrast = 0.05f, mono = BW, sepia = true, swatch = 0xFFC9B08A),
)
