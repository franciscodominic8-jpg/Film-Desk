package com.filmdesk.app.model

val GRAIN_OPTIONS = listOf("Off", "Weak · Small", "Weak · Large", "Strong · Small", "Strong · Large")
val GRAIN_SHORT = listOf("OFF", "WEAK·S", "WEAK·L", "STRONG·S", "STRONG·L")
val CHROME_OPTIONS = listOf("Off", "Weak", "Strong")
val DR_OPTIONS = listOf("DR100", "DR200", "DR400")
val DRP_OPTIONS = listOf("Off", "Weak", "Strong", "Auto")
val SKIN_OPTIONS = listOf("Off", "Weak", "Strong")

enum class WbType { AS_SHOT, AUTO, KELVIN, CUSTOM, UNDERWATER }

class WbPreset(val label: String, val type: WbType, val kelvin: Int = 5500, val green: Float = 1f)

val WB_PRESETS = listOf(
    WbPreset("As Shot", WbType.AS_SHOT),
    WbPreset("Auto", WbType.AUTO),
    WbPreset("Daylight", WbType.KELVIN, 5500),
    WbPreset("Shade", WbType.KELVIN, 7200),
    WbPreset("Fluorescent 1", WbType.KELVIN, 6500, green = 0.97f),
    WbPreset("Fluorescent 2", WbType.KELVIN, 4500, green = 0.97f),
    WbPreset("Fluorescent 3", WbType.KELVIN, 4000, green = 0.97f),
    WbPreset("Incandescent", WbType.KELVIN, 3000),
    WbPreset("Underwater", WbType.UNDERWATER),
    WbPreset("Kelvin", WbType.CUSTOM),
)

class Aspect(val label: String, val ratio: Float)

val ASPECTS = listOf(
    Aspect("Original", 0f),
    Aspect("1:1", 1f),
    Aspect("4:5", 4f / 5f),
    Aspect("3:2", 3f / 2f),
    Aspect("2:3", 2f / 3f),
    Aspect("16:9", 16f / 9f),
    Aspect("9:16", 9f / 16f),
    Aspect("65:24 pano", 65f / 24f),
)

enum class ExportFormat(val label: String, val mime: String, val ext: String) {
    JPEG("JPEG", "image/jpeg", "jpg"),
    PNG("PNG (lossless)", "image/png", "png"),
}

/** Long-edge sizes for export; null = full resolution (limited by device memory). */
val EXPORT_SIZES: List<Int?> = listOf(null, 4096, 2048, 1080)

/** Hue centres (degrees) for the 8 HSL colour bands. */
val HSL_NAMES = listOf("Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta")
val HSL_SWATCHES = listOf(0xFFE5484D, 0xFFF08C3A, 0xFFE9C43A, 0xFF4CB467, 0xFF3BB8C4, 0xFF3E7BE0, 0xFF8A5BD6, 0xFFD14FA8)
