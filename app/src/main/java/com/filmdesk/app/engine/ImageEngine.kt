package com.filmdesk.app.engine

import android.graphics.Bitmap
import android.graphics.Matrix
import com.filmdesk.app.model.ASPECTS
import com.filmdesk.app.model.EditParams
import com.filmdesk.app.model.FILM_SIMS
import com.filmdesk.app.model.WB_PRESETS
import com.filmdesk.app.model.WbType
import com.filmdesk.app.model.curveLut
import com.filmdesk.app.model.isIdentityCurve
import com.filmdesk.app.model.effectiveTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh

/** Average linear colour of a photo, used by Auto white balance. */
class ImageStats(val avgR: Float, val avgG: Float, val avgB: Float) {
    companion object {
        val NEUTRAL = ImageStats(0.18f, 0.18f, 0.18f)
    }
}

/**
 * CPU image pipeline. Pure Kotlin, so it behaves the same on every Android version
 * and every chip (no GPU / RenderScript dependency). Work is split across cores.
 *
 * Order: geometry → linear WB + exposure → dynamic range → film simulation →
 * Fuji tone (highlight/shadow) → pro tone → colour (saturation, Color Chrome, HSL)
 * → detail (sharpness, NR, clarity, smooth skin) → grain + vignette.
 */
object ImageEngine {
    private const val ENC = 4096
    private val toLinear = FloatArray(256) { srgbToLinear(it / 255f) }
    private val toGamma = FloatArray(ENC + 1) { linearToSrgb(it / ENC.toFloat()) }
    private val toLinearF = FloatArray(ENC + 1) { srgbToLinear(it / ENC.toFloat()) }
    private val HUE_CENTERS = floatArrayOf(0f, 30f, 60f, 120f, 180f, 225f, 275f, 320f)

    // ------------------------------------------------------------------ public

    fun computeStats(bmp: Bitmap): ImageStats {
        val w = bmp.width
        val h = bmp.height
        val step = max(1, max(w, h) / 200)
        val row = IntArray(w)
        var r = 0.0; var g = 0.0; var b = 0.0; var n = 0
        var y = 0
        while (y < h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                val c = row[x]
                r += toLinear[(c shr 16) and 255]
                g += toLinear[(c shr 8) and 255]
                b += toLinear[c and 255]
                n++
                x += step
            }
            y += step
        }
        if (n == 0) return ImageStats.NEUTRAL
        return ImageStats(
            (r / n).toFloat().coerceAtLeast(1e-4f),
            (g / n).toFloat().coerceAtLeast(1e-4f),
            (b / n).toFloat().coerceAtLeast(1e-4f),
        )
    }

    fun applyGeometry(src: Bitmap, p: EditParams): Bitmap {
        var bmp = src
        if (p.rotation != 0 || p.flipH) {
            val m = Matrix()
            if (p.flipH) m.postScale(-1f, 1f)
            if (p.rotation != 0) m.postRotate(p.rotation.toFloat())
            bmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
        val ratio = ASPECTS[p.aspect.coerceIn(0, ASPECTS.lastIndex)].ratio
        if (ratio > 0f) {
            val w = bmp.width
            val h = bmp.height
            var cw = w
            var ch = h
            if (w.toFloat() / h > ratio) cw = (h * ratio).roundToInt() else ch = (w / ratio).roundToInt()
            cw = cw.coerceIn(1, w)
            ch = ch.coerceIn(1, h)
            if (cw != w || ch != h) bmp = Bitmap.createBitmap(bmp, (w - cw) / 2, (h - ch) / 2, cw, ch)
        }
        return bmp
    }

    /** Renders [source] with [p]. Safe to call from any coroutine; cancellable between stages. */
    suspend fun render(source: Bitmap, p: EditParams, stats: ImageStats, detail: Boolean = true): Bitmap {
        val bmp = applyGeometry(source, p)
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val prep = Prepared(p, stats)
        parallelRows(h) { y0, y1 -> colorRows(px, w, y0, y1, prep) }
        if (detail) applyDetail(px, w, h, p)
        if (p.grain > 0 || p.vignette != 0) parallelRows(h) { y0, y1 -> finishRows(px, w, h, y0, y1, p) }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    // ------------------------------------------------------------- preparation

    private class Prepared(p: EditParams, stats: ImageStats) {
        val sim = FILM_SIMS[p.sim.coerceIn(0, FILM_SIMS.lastIndex)]
        val strength = p.simStrength.coerceIn(0f, 1f)
        val gR: Float
        val gG: Float
        val gB: Float
        val dr: Int
        val shoulderOn: Boolean
        val knee: Float
        val a: Float
        val norm: Float
        val hl: Float
        val sh: Float
        /** Chroma multiplier from the Fuji Q-menu "Color" setting (−4..+4). */
        val colorMul = 1f + 0.08f * p.color
        /** Pro Saturation, −1..+1: plus works like vibrance, minus fades to black & white. */
        val proSat = p.saturation / 100f
        val vib = p.vibrance / 100f
        val cc = when (p.colorChrome) { 1 -> 0.14f; 2 -> 0.26f; else -> 0f }
        val ccb = when (p.colorChromeBlue) { 1 -> 0.12f; 2 -> 0.22f; else -> 0f }
        val proContrast = p.contrast / 100f * 0.3f
        val proHi = p.highlights / 100f
        val proSh = p.shadows / 100f
        val proWh = p.whites / 100f
        val proBl = p.blacks / 100f
        val fade = p.fade / 100f * 0.15f
        val wc = p.monoWC.toFloat()
        val mg = p.monoMG.toFloat()
        // Tone curves: a lookup table per channel, or null when the curve is a straight line.
        val lutY: FloatArray? = if (isIdentityCurve(p.curveY)) null else curveLut(p.curveY, CURVE_LUT)
        val lutR: FloatArray? = if (isIdentityCurve(p.curveR)) null else curveLut(p.curveR, CURVE_LUT)
        val lutG: FloatArray? = if (isIdentityCurve(p.curveG)) null else curveLut(p.curveG, CURVE_LUT)
        val lutB: FloatArray? = if (isIdentityCurve(p.curveB)) null else curveLut(p.curveB, CURVE_LUT)
        val curveOn = lutY != null || lutR != null || lutG != null || lutB != null
        val linked = p.curveLinked
        val mixY = p.curveMix.getOrElse(0) { 100 } / 100f
        val mixR = p.curveMix.getOrElse(1) { 100 } / 100f
        val mixG = p.curveMix.getOrElse(2) { 100 } / 100f
        val mixB = p.curveMix.getOrElse(3) { 100 } / 100f
        // Soft clip
        val softOn = p.softLow != 0 || p.softHigh != 100 || p.softLS != 0
        val outLow = p.softLow / 100f
        val outHigh = max(outLow + 0.01f, p.softHigh / 100f)
        val lsKnee = 0.5f * (p.softLS / 100f)         // shadows roll off below this
        val hslOn = p.hslHue.any { it != 0 } || p.hslSat.any { it != 0 } || p.hslLum.any { it != 0 }
        val hueShift = FloatArray(8) { p.hslHue.getOrElse(it) { 0 } * 0.3f }
        val satShift = FloatArray(8) { p.hslSat.getOrElse(it) { 0 } / 100f }
        val lumShift = FloatArray(8) { p.hslLum.getOrElse(it) { 0 } / 100f * 0.5f }

        init {
            val wb = whiteBalanceGains(p, stats)
            val ev = 2f.pow(p.exposureThirds / 3f)
            val t = p.temp / 100f
            val tn = p.tint / 100f
            gR = wb[0] * ev * (1f + 0.3f * t) * (1f + 0.025f * p.wbShiftR)
            gG = wb[1] * ev * (1f - 0.2f * tn)
            gB = wb[2] * ev * (1f - 0.3f * t) * (1f + 0.025f * p.wbShiftB)

            val tone = p.effectiveTone()
            val d = tone.dr
            dr = d
            hl = tone.highlight
            sh = tone.shadow
            // Highlight roll-off in linear light, before anything is clipped:
            // Dynamic Range and the Soft Clip H.S. control share one smooth shoulder.
            val hs = p.softHS / 100f
            var k = when (d) { 1 -> 0.75f; 2 -> 0.6f; else -> 1f }
            var range = 1f + d * 0.5f
            if (hs > 0f) {
                k = min(k, 1f - 0.45f * hs)
                range = max(range, 1f + 2f * hs)
            }
            knee = k
            shoulderOn = k < 1f
            if (shoulderOn) {
                a = 1f / (1f - knee)
                norm = 1f - exp(-a * (range - knee))
            } else {
                a = 1f
                norm = 1f
            }
        }
    }

    private fun whiteBalanceGains(p: EditParams, s: ImageStats): FloatArray {
        val preset = WB_PRESETS[p.wb.coerceIn(0, WB_PRESETS.lastIndex)]
        val g = when (preset.type) {
            WbType.AS_SHOT -> floatArrayOf(1f, 1f, 1f)
            WbType.AUTO -> floatArrayOf(autoBlend(s.avgG / s.avgR), 1f, autoBlend(s.avgG / s.avgB))
            WbType.KELVIN -> kelvinGains(preset.kelvin)
            WbType.CUSTOM -> kelvinGains(p.kelvin)
            WbType.UNDERWATER -> floatArrayOf(1.45f, 1f, 0.8f)
        }
        g[1] *= preset.green
        return g
    }

    private fun autoBlend(x: Float) = (1f + (x - 1f) * 0.75f).coerceIn(0.5f, 2f)

    /** Gains that re-balance a photo (assumed shot at ~5500K) as if the camera were set to [k]. */
    private fun kelvinGains(k: Int): FloatArray {
        val ref = kelvinRgb(5500)
        val t = kelvinRgb(k)
        val r = ref[0] / t[0]
        val g = ref[1] / t[1]
        val b = ref[2] / t[2]
        return floatArrayOf((r / g).coerceIn(0.3f, 3f), 1f, (b / g).coerceIn(0.3f, 3f))
    }

    private fun kelvinRgb(k: Int): FloatArray {
        val t = k.coerceIn(1000, 40000) / 100.0
        val r = if (t <= 66) 255.0 else 329.698727446 * (t - 60).pow(-0.1332047592)
        val g = if (t <= 66) 99.4708025861 * ln(t) - 161.1195681661 else 288.1221695283 * (t - 60).pow(-0.0755148492)
        val b = if (t >= 66) 255.0 else if (t <= 19) 0.0 else 138.5177312231 * ln(t - 10) - 305.0447927307
        return floatArrayOf(
            r.coerceIn(1.0, 255.0).toFloat(),
            g.coerceIn(1.0, 255.0).toFloat(),
            b.coerceIn(1.0, 255.0).toFloat(),
        )
    }

    // -------------------------------------------------------------- colour pass

    private fun colorRows(px: IntArray, w: Int, y0: Int, y1: Int, q: Prepared) {
        val sim = q.sim
        val m = sim.matrix
        val mono = sim.mono
        val st = sim.shadowTint
        val ht = sim.highlightTint
        val hsv = FloatArray(3)
        val lab = FloatArray(3)
        val rgbTmp = FloatArray(3)
        for (y in y0 until y1) {
            var i = y * w
            val end = i + w
            while (i < end) {
                val c = px[i]
                // 1. linear light, white balance, exposure, dynamic range, back to gamma
                var r = enc(shoulder(toLinear[(c shr 16) and 255] * q.gR, q))
                var g = enc(shoulder(toLinear[(c shr 8) and 255] * q.gG, q))
                var b = enc(shoulder(toLinear[c and 255] * q.gB, q))

                // 2. film simulation
                var sr = r
                var sg = g
                var sb = b
                if (m != null) {
                    val nr = m[0] * sr + m[1] * sg + m[2] * sb
                    val ng = m[3] * sr + m[4] * sg + m[5] * sb
                    val nb = m[6] * sr + m[7] * sg + m[8] * sb
                    sr = nr; sg = ng; sb = nb
                }
                if (mono != null) {
                    val l = mono[0] * sr + mono[1] * sg + mono[2] * sb
                    sr = l; sg = l; sb = l
                } else if (sim.saturation != 1f) {
                    val l = luma(sr, sg, sb)
                    sr = l + (sr - l) * sim.saturation
                    sg = l + (sg - l) * sim.saturation
                    sb = l + (sb - l) * sim.saturation
                }
                if (sim.contrast != 0f) {
                    sr = sCurve(sr, sim.contrast); sg = sCurve(sg, sim.contrast); sb = sCurve(sb, sim.contrast)
                }
                if (sim.toe > 0f) {
                    sr = sim.toe + sr * (1f - sim.toe)
                    sg = sim.toe + sg * (1f - sim.toe)
                    sb = sim.toe + sb * (1f - sim.toe)
                }
                val tl = luma(sr, sg, sb).coerceIn(0f, 1f)
                val sw = (1f - tl) * (1f - tl) * 2f
                val hw = tl * tl * 2f
                sr += st[0] * sw + ht[0] * hw
                sg += st[1] * sw + ht[1] * hw
                sb += st[2] * sw + ht[2] * hw
                if (sim.sepia) {
                    val k = 4f * tl * (1f - tl) + 0.25f
                    sr += 0.06f * k; sg += 0.015f * k; sb -= 0.06f * k
                }
                if (mono != null && (q.wc != 0f || q.mg != 0f)) {
                    sr += q.wc * 0.006f + q.mg * 0.004f
                    sg -= q.mg * 0.006f
                    sb += -q.wc * 0.006f + q.mg * 0.004f
                }
                r += (sr - r) * q.strength
                g += (sg - g) * q.strength
                b += (sb - b) * q.strength

                // 3. tone: Fuji Highlight / Shadow + pro Highlights/Shadows/Whites/Blacks
                val lum = luma(r, g, b).coerceIn(0f, 1f)
                var dL = 0f
                if (q.hl != 0f) dL += q.hl * 0.045f * smooth(0.45f, 1f, lum)
                if (q.sh != 0f) dL -= q.sh * 0.045f * (1f - smooth(0f, 0.55f, lum))
                if (q.proHi != 0f) dL += q.proHi * 0.22f * smooth(0.45f, 1f, lum)
                if (q.proSh != 0f) dL += q.proSh * 0.22f * (1f - smooth(0f, 0.55f, lum))
                if (q.proWh != 0f) dL += q.proWh * 0.15f * smooth(0.7f, 1f, lum)
                if (q.proBl != 0f) dL += q.proBl * 0.12f * (1f - smooth(0f, 0.3f, lum))
                r += dL; g += dL; b += dL
                if (q.proContrast != 0f) {
                    r = sCurve(r, q.proContrast); g = sCurve(g, q.proContrast); b = sCurve(b, q.proContrast)
                }

                // 3b. Custom curves (DaVinci-style):
                //     linked: Y curve moves R, G, B together (YRGB);
                //     unlinked: Y curve changes luma only, colour/saturation stay put.
                //     Then R, G, B curves. Each channel mixed by its intensity.
                if (q.curveOn) {
                    val ly = q.lutY
                    if (ly != null && q.mixY > 0f) {
                        if (q.linked) {
                            r += (ly[ci(r)] - r) * q.mixY
                            g += (ly[ci(g)] - g) * q.mixY
                            b += (ly[ci(b)] - b) * q.mixY
                        } else {
                            val yl = luma(r, g, b).coerceIn(0f, 1f)
                            val dy = (ly[ci(yl)] - yl) * q.mixY
                            r += dy; g += dy; b += dy
                        }
                    }
                    q.lutR?.let { if (q.mixR > 0f) r += (it[ci(r)] - r) * q.mixR }
                    q.lutG?.let { if (q.mixG > 0f) g += (it[ci(g)] - g) * q.mixG }
                    q.lutB?.let { if (q.mixB > 0f) b += (it[ci(b)] - b) * q.mixB }
                }
                // 3c. Soft clip: smooth roll-off instead of hard clipping, then output range.
                if (q.softOn) {
                    r = softClip(r, q); g = softClip(g, q); b = softClip(b, q)
                }

                // 4. Saturation / Vibrance / Fuji Color — scales chroma in OKLab, so brightness
                //    and hue stay put, and strong colours roll off smoothly instead of clipping.
                if (q.colorMul != 1f || q.proSat != 0f || q.vib != 0f) {
                    r = r.coerceIn(0f, 1f); g = g.coerceIn(0f, 1f); b = b.coerceIn(0f, 1f)
                    toOklab(lin(r), lin(g), lin(b), lab)
                    val ll = lab[0]
                    val la = lab[1]
                    val lb = lab[2]
                    val c = sqrt(la * la + lb * lb)
                    if (c > 1e-5f) {
                        var f = q.colorMul
                        val sat = (c / 0.22f).coerceIn(0f, 1f)
                        var skin = 0f
                        if (q.proSat > 0f || q.vib > 0f) {
                            var hue = Math.toDegrees(atan2(lb, la).toDouble()).toFloat()
                            if (hue < 0f) hue += 360f
                            skin = (1f - abs(hue - 55f) / 30f).coerceIn(0f, 1f)
                        }
                        if (q.proSat > 0f) {
                            // Vibrant-style: dull colours gain the most, strong colours and skin less.
                            f *= 1f + q.proSat * (0.4f + 0.6f * (1f - sat) * (1f - sat)) * (1f - 0.5f * skin)
                        } else if (q.proSat < 0f) {
                            // Minus side: straight fade to black & white (−100 = no colour).
                            f *= 1f + q.proSat
                        }
                        if (q.vib != 0f) {
                            if (q.vib > 0f) {
                                // Boost muted colours most, leave saturated ones and skin mostly alone.
                                f *= 1f + q.vib * (1f - sat) * (1f - sat) * (1f - 0.6f * skin)
                            } else {
                                // Negative vibrance tones down the loudest colours first.
                                f *= 1f + q.vib * (0.2f + 0.45f * sat)
                            }
                        }
                        f = f.coerceAtLeast(0f)
                        var ct = c * f
                        if (ct > c) ct = softGamut(ll, la / c, lb / c, c, ct, rgbTmp)
                        val k = ct / c
                        fromOklab(ll, la * k, lb * k, rgbTmp)
                        r = enc(rgbTmp[0]); g = enc(rgbTmp[1]); b = enc(rgbTmp[2])
                    }
                }

                // 5. Color Chrome effect / FX Blue: denser tone in saturated colours
                if (q.cc > 0f || q.ccb > 0f) {
                    val mx = max3(r, g, b)
                    val cs = (mx - min3(r, g, b)).coerceIn(0f, 1f)
                    if (q.cc > 0f) {
                        val f = 1f - q.cc * cs * cs * (0.5f + 0.5f * mx.coerceIn(0f, 1f))
                        r *= f; g *= f; b *= f
                    }
                    if (q.ccb > 0f) {
                        val bl = ((b - max(r, g)) * 3f).coerceIn(0f, 1f)
                        if (bl > 0f) {
                            val f = 1f - q.ccb * bl
                            r *= f; g *= f; b *= 1f - q.ccb * bl * 0.55f
                        }
                    }
                }

                // 6. HSL bands
                if (q.hslOn) {
                    rgbToHsv(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), hsv)
                    val hh = hsv[0]
                    var dh = 0f; var ds = 0f; var dl = 0f
                    for (k in 0 until 8) {
                        var d = abs(hh - HUE_CENTERS[k])
                        if (d > 180f) d = 360f - d
                        val wgt = 1f - d / 45f
                        if (wgt > 0f) {
                            dh += wgt * q.hueShift[k]; ds += wgt * q.satShift[k]; dl += wgt * q.lumShift[k]
                        }
                    }
                    if (dh != 0f || ds != 0f || dl != 0f) {
                        var nh = hh + dh
                        if (nh < 0f) nh += 360f
                        if (nh >= 360f) nh -= 360f
                        val ns = (hsv[1] * (1f + ds)).coerceIn(0f, 1f)
                        val nv = (hsv[2] * (1f + dl * hsv[1])).coerceIn(0f, 1f)
                        hsvToRgb(nh, ns, nv, hsv)
                        r = hsv[0]; g = hsv[1]; b = hsv[2]
                    }
                }

                // 7. fade (lifted blacks)
                if (q.fade > 0f) {
                    r = q.fade + r * (1f - q.fade)
                    g = q.fade + g * (1f - q.fade)
                    b = q.fade + b * (1f - q.fade)
                }
                px[i] = pack(r, g, b)
                i++
            }
        }
    }

    // ------------------------------------------------------------- detail pass

    private suspend fun applyDetail(px: IntArray, w: Int, h: Int, p: EditParams) {
        val long = max(w, h)
        // Skin first, so sharpening doesn't put the texture straight back.
        if (p.smoothSkin > 0) smoothSkin(px, w, h, p.smoothSkin)
        // Sharpness (unsharp mask) and High ISO NR share one small blur.
        val k = (if (p.sharpness >= 0) p.sharpness * 0.3f else p.sharpness * 0.12f) - max(0, p.noiseReduction) * 0.07f
        if (k != 0f) {
            val blur = boxBlur(px, w, h, max(1, long / 1800))
            for (i in px.indices) px[i] = addDetail(px[i], blur[i], k)
        }
        if (p.clarity != 0) {
            val r = max(2, long / 90)
            val blur = boxBlur(boxBlur(px, w, h, r), w, h, r)
            val kc = p.clarity * 0.09f
            for (i in px.indices) {
                val c = px[i]
                val l = lumaOf(c)
                px[i] = addDetail(c, blur[i], kc * 4f * l * (1f - l))
            }
        }
    }

    // ------------------------------------------------------------ smooth skin

    /**
     * Skin smoothing like a retouching app:
     *  - a soft, feathered skin mask (YCbCr skin range, works across skin tones),
     *  - an edge-preserving guided filter on brightness, so pores and small blemishes
     *    soften while eyes, lips, brows and hair edges stay sharp,
     *  - a gentle evening-out of blotchy colour (redness) under the same mask,
     *  - some fine texture is kept so skin never looks plastic.
     * Processed in horizontal strips to keep memory low on large exports.
     */
    private suspend fun smoothSkin(px: IntArray, w: Int, h: Int, level: Int) {
        val long = max(w, h)
        // Radius big enough that freckles/spots are "small" inside the window, so the
        // guided filter smooths them, while long edges (brows, lashes, lips) still win.
        val r = max(3, long / 140)
        val rc = r * 3                                   // wider radius for evening out colour
        val eps = if (level == 1) 0.0025f else 0.0060f   // edge threshold (higher = smoother)
        val amount = if (level == 1) 0.7f else 0.92f
        val colourAmt = if (level == 1) 0.45f else 0.75f
        val src = px.copyOf()
        val margin = max(2 * r, rc) + 2
        // Keep each strip's working memory to ~24 MB (11 float planes).
        val stripH = (24_000_000 / (w * 44) - 2 * margin).coerceIn(32, 256)
        val strips = (0 until h step stripH).toList()
        // Small images (the live preview) use several cores; big exports go one strip at a time.
        val parallel = if (w <= 2048) Runtime.getRuntime().availableProcessors().coerceIn(1, 4) else 1
        coroutineScope {
            strips.chunked(parallel).forEach { batch ->
                batch.map { y0 ->
                    async(Dispatchers.Default) {
                        ensureActive()
                        skinStrip(src, px, w, h, y0, min(h, y0 + stripH), margin, r, rc, eps, amount, colourAmt)
                    }
                }.awaitAll()
            }
        }
    }

    private fun skinStrip(
        src: IntArray, px: IntArray, w: Int, h: Int, y0: Int, y1: Int, margin: Int,
        r: Int, rc: Int, eps: Float, amount: Float, colourAmt: Float,
    ) {
        val ys = max(0, y0 - margin)
        val ye = min(h, y1 + margin)
        val sh = ye - ys
        val n = w * sh
        val lum = FloatArray(n); val mask = FloatArray(n)
        val cR = FloatArray(n); val cG = FloatArray(n); val cB = FloatArray(n)
        val tmp = FloatArray(n)
        val p1 = FloatArray(n); val p2 = FloatArray(n); val p3 = FloatArray(n); val p4 = FloatArray(n); val p5 = FloatArray(n)
        for (i in 0 until n) {
            val c = src[ys * w + i]
            val rr = ((c shr 16) and 255) / 255f
            val gg = ((c shr 8) and 255) / 255f
            val bb = (c and 255) / 255f
            val l = 0.299f * rr + 0.587f * gg + 0.114f * bb
            lum[i] = l
            mask[i] = skinProbability(rr, gg, bb)
            cR[i] = rr - l; cG[i] = gg - l; cB[i] = bb - l
        }
        // Final mask: skin likelihood, feathered so smoothing fades in and out without edges.
        boxF(mask, p1, w, sh, r, tmp)
        for (i in 0 until n) mask[i] = min(mask[i], p1[i] * 1.15f).coerceIn(0f, 1f)
        // Guided filter (self-guided) on brightness: q = mean(a)·I + mean(b).
        boxF(lum, p1, w, sh, r, tmp)                 // p1 = mean I
        for (i in 0 until n) p3[i] = lum[i] * lum[i]
        boxF(p3, p2, w, sh, r, tmp)                  // p2 = mean I²
        for (i in 0 until n) {
            val v = p2[i] - p1[i] * p1[i]
            val ai = v / (v + eps)
            p3[i] = ai                               // p3 = a
            p4[i] = p1[i] - ai * p1[i]               // p4 = b
        }
        boxF(p3, p1, w, sh, r, tmp)                  // p1 = mean a
        boxF(p4, p2, w, sh, r, tmp)                  // p2 = mean b
        // Even out blotchy colour (redness) using skin pixels only (mask-weighted average),
        // so lips, eyes and clothing never bleed into the skin.
        boxF(mask, p4, w, sh, rc, tmp)               // p4 = local skin weight
        for (ch in arrayOf(cR, cG, cB)) {
            for (i in 0 until n) p3[i] = ch[i] * mask[i]
            boxF(p3, p5, w, sh, rc, tmp)
            for (i in 0 until n) {
                val wsum = p4[i]
                if (wsum > 0.05f) ch[i] += (p5[i] / wsum - ch[i]) * colourAmt * mask[i]
            }
        }
        val from = (y0 - ys) * w
        val to = (y1 - ys) * w
        for (i in from until to) {
            val m = mask[i]
            if (m <= 0.01f) continue
            val l = lum[i]
            val nl = l + (p1[i] * l + p2[i] - l) * amount * m
            px[ys * w + i] = pack(nl + cR[i], nl + cG[i], nl + cB[i])
        }
    }

    /** 0..1 skin likelihood from the classic YCbCr skin range, with soft edges. */
    private fun skinProbability(r: Float, g: Float, b: Float): Float {
        val y = 255f * (0.299f * r + 0.587f * g + 0.114f * b)
        val cb = 128f + 255f * (-0.168736f * r - 0.331264f * g + 0.5f * b)
        val cr = 128f + 255f * (0.5f * r - 0.418688f * g - 0.081312f * b)
        val pCb = softRange(cb, 77f, 127f, 8f)
        val pCr = softRange(cr, 133f, 173f, 8f)
        val pY = smooth(25f, 55f, y) * (1f - smooth(245f, 254f, y))
        return pCb * pCr * pY
    }

    private fun softRange(x: Float, lo: Float, hi: Float, feather: Float): Float =
        smooth(lo - feather, lo + feather, x) * (1f - smooth(hi - feather, hi + feather, x))

    /** Separable box filter on a float plane (edges clamped), written into [dst]. */
    private fun boxF(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int, tmp: FloatArray) {
        val div = 1f / (2 * r + 1)
        for (y in 0 until h) {
            val base = y * w
            var sum = 0f
            for (k in -r..r) sum += src[base + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[base + x] = sum * div
                sum += src[base + min(x + r + 1, w - 1)] - src[base + max(x - r, 0)]
            }
        }
        for (x in 0 until w) {
            var sum = 0f
            for (k in -r..r) sum += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum * div
                sum += tmp[min(y + r + 1, h - 1) * w + x] - tmp[max(y - r, 0) * w + x]
            }
        }
    }

    /** Separable box blur on packed RGB. Returns a new array. */
    fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(src.size)
        val dst = IntArray(src.size)
        blurLines(src, tmp, w, h, r, true)
        blurLines(tmp, dst, w, h, r, false)
        return dst
    }

    private fun blurLines(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val stride = if (horizontal) 1 else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var sr = 0; var sg = 0; var sb = 0
            for (k in -r..r) {
                val c = src[base + k.coerceIn(0, len - 1) * stride]
                sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255
            }
            for (j in 0 until len) {
                dst[base + j * stride] = OPAQUE or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[base + min(j + r + 1, len - 1) * stride]
                val sub = src[base + max(j - r, 0) * stride]
                sr += ((add shr 16) and 255) - ((sub shr 16) and 255)
                sg += ((add shr 8) and 255) - ((sub shr 8) and 255)
                sb += (add and 255) - (sub and 255)
            }
        }
    }

    // ---------------------------------------------------- grain + vignette pass

    private fun finishRows(px: IntArray, w: Int, h: Int, y0: Int, y1: Int, p: EditParams) {
        val long = max(w, h).toFloat()
        val amp = when (p.grain) { 1, 2 -> 0.035f; 3, 4 -> 0.07f; else -> 0f }
        val large = p.grain == 2 || p.grain == 4
        // Grain size scales with resolution so preview and export look alike.
        val cell = long / 2400f * (if (large) 2.4f else 1.2f)
        val freq = 1f / max(cell, 1f)
        val v = p.vignette / 100f
        val cx = w / 2f
        val cy = h / 2f
        for (y in y0 until y1) {
            val dy = (y - cy) / cy
            for (x in 0 until w) {
                val i = y * w + x
                val c = px[i]
                var r = ((c shr 16) and 255) / 255f
                var g = ((c shr 8) and 255) / 255f
                var b = (c and 255) / 255f
                if (v != 0f) {
                    val dx = (x - cx) / cx
                    val d2 = (dx * dx + dy * dy) * 0.5f
                    val vm = 1f + v * 0.8f * smooth(0.1f, 1f, d2)
                    r *= vm; g *= vm; b *= vm
                }
                if (amp > 0f) {
                    val l = luma(r, g, b).coerceIn(0f, 1f)
                    val n = if (cell <= 1f) hashNoise(x, y) else valueNoise(x * freq, y * freq)
                    val d = n * amp * (0.35f + 2.6f * l * (1f - l))
                    r += d; g += d; b += d
                }
                px[i] = pack(r, g, b)
            }
        }
    }

    // ------------------------------------------------------------ OKLab colour

    private fun lin(v: Float): Float = toLinearF[(v.coerceIn(0f, 1f) * ENC).toInt()]

    /** Linear sRGB → OKLab (Björn Ottosson). */
    private fun toOklab(r: Float, g: Float, b: Float, out: FloatArray) {
        val l = Math.cbrt((0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b))
        val m = Math.cbrt((0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b))
        val s = Math.cbrt((0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b))
        out[0] = (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s).toFloat()
        out[1] = (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s).toFloat()
        out[2] = (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s).toFloat()
    }

    /** OKLab → linear sRGB (may be outside 0..1 if out of gamut). */
    private fun fromOklab(lL: Float, a: Float, b: Float, out: FloatArray) {
        val l_ = lL + 0.3963377774f * a + 0.2158037573f * b
        val m_ = lL - 0.1055613458f * a - 0.0638541728f * b
        val s_ = lL - 0.0894841775f * a - 1.2914855480f * b
        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_
        out[0] = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
        out[1] = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
        out[2] = -0.0041960863f * l - 0.5110127212f * m + 1.5003456678f * s
    }

    private fun inGamut(lL: Float, ua: Float, ub: Float, c: Float, tmp: FloatArray): Boolean {
        fromOklab(lL, ua * c, ub * c, tmp)
        val e = 1e-4f
        return tmp[0] >= -e && tmp[0] <= 1f + e && tmp[1] >= -e && tmp[1] <= 1f + e && tmp[2] >= -e && tmp[2] <= 1f + e
    }

    /**
     * Raises chroma from [c] towards [target] along the same hue and lightness, easing into
     * the edge of the sRGB gamut (soft knee) so saturated areas keep their detail.
     */
    private fun softGamut(lL: Float, ua: Float, ub: Float, c: Float, target: Float, tmp: FloatArray): Float {
        // Fast path: comfortably inside the gamut.
        if (inGamut(lL, ua, ub, target * 1.35f, tmp)) return target
        var lo = c
        var hi = max(target * 1.35f, c + 1e-4f)
        repeat(12) {
            val mid = (lo + hi) * 0.5f
            if (inGamut(lL, ua, ub, mid, tmp)) lo = mid else hi = mid
        }
        val cMax = lo
        val knee = max(c, 0.72f * cMax)
        if (target <= knee || cMax - knee < 1e-5f) return min(target, cMax)
        val span = cMax - knee
        return knee + span * tanh((target - knee) / span)
    }

    // ----------------------------------------------------------------- helpers

    private suspend fun parallelRows(h: Int, block: (Int, Int) -> Unit) {
        coroutineScope {
            val n = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
            val chunk = (h + n - 1) / n
            (0 until n).map { idx ->
                async(Dispatchers.Default) {
                    ensureActive()
                    val y0 = idx * chunk
                    val y1 = min(h, y0 + chunk)
                    if (y0 < y1) block(y0, y1)
                }
            }.awaitAll()
        }
    }

    private const val OPAQUE = -0x1000000 // 0xFF000000
    private const val CURVE_LUT = 1024

    /**
     * Soft clip (like DaVinci's): L.S. lifts crushed shadows gently, then Low/High set the
     * output black and white levels. (H.S. is applied earlier, in linear light — see Prepared.)
     */
    private fun softClip(v: Float, q: Prepared): Float {
        var x = v
        if (q.lsKnee > 0f && x < q.lsKnee) {
            val span = q.lsKnee
            x = q.lsKnee - span * tanh((q.lsKnee - x) / span)
        }
        return q.outLow + (q.outHigh - q.outLow) * x.coerceIn(0f, 1f)
    }

    private fun ci(v: Float): Int = (v.coerceIn(0f, 1f) * (CURVE_LUT - 1) + 0.5f).toInt()

    /** 64-bin histograms (Y, R, G, B), each scaled so its tallest bin is 1. */
    class Histogram(val y: FloatArray, val r: FloatArray, val g: FloatArray, val b: FloatArray)

    fun histogram(bmp: Bitmap, bins: Int = 64): Histogram {
        val w = bmp.width
        val h = bmp.height
        val step = max(1, max(w, h) / 400)
        val hy = FloatArray(bins); val hr = FloatArray(bins); val hg = FloatArray(bins); val hb = FloatArray(bins)
        val row = IntArray(w)
        var y = 0
        while (y < h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                val c = row[x]
                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255
                hr[r * bins / 256]++; hg[g * bins / 256]++; hb[b * bins / 256]++
                hy[((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255) * bins / 256]++
                x += step
            }
            y += step
        }
        fun norm(a: FloatArray): FloatArray {
            // Ignore the extreme end bins when scaling so clipped areas don't flatten the rest.
            var mx = 1f
            for (i in 1 until bins - 1) mx = max(mx, a[i])
            return FloatArray(bins) { (a[it] / mx).coerceAtMost(1f) }
        }
        return Histogram(norm(hy), norm(hr), norm(hg), norm(hb))
    }

    private fun shoulder(x: Float, q: Prepared): Float {
        if (!q.shoulderOn || x <= q.knee) return x
        return q.knee + (1f - q.knee) * min(1f, (1f - exp(-q.a * (x - q.knee))) / q.norm)
    }

    private fun enc(v: Float): Float = toGamma[(v.coerceIn(0f, 1f) * ENC).toInt()]

    private fun pack(r: Float, g: Float, b: Float): Int {
        val ri = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val gi = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val bi = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return OPAQUE or (ri shl 16) or (gi shl 8) or bi
    }

    private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun lumaOf(c: Int) =
        (0.2126f * ((c shr 16) and 255) + 0.7152f * ((c shr 8) and 255) + 0.0722f * (c and 255)) / 255f

    private fun max3(a: Float, b: Float, c: Float) = max(a, max(b, c))
    private fun min3(a: Float, b: Float, c: Float) = min(a, min(b, c))

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Gentle S-curve; k > 0 adds contrast, k < 0 flattens. */
    private fun sCurve(x: Float, k: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c + k * (c - 0.5f) * 4f * c * (1f - c)
    }

    private fun addDetail(c: Int, blur: Int, k: Float): Int {
        val r = (c shr 16) and 255
        val g = (c shr 8) and 255
        val b = c and 255
        val nr = (r + k * (r - ((blur shr 16) and 255))).roundToInt().coerceIn(0, 255)
        val ng = (g + k * (g - ((blur shr 8) and 255))).roundToInt().coerceIn(0, 255)
        val nb = (b + k * (b - (blur and 255))).roundToInt().coerceIn(0, 255)
        return OPAQUE or (nr shl 16) or (ng shl 8) or nb
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val r = (((a shr 16) and 255) + (((b shr 16) and 255) - ((a shr 16) and 255)) * t).roundToInt()
        val g = (((a shr 8) and 255) + (((b shr 8) and 255) - ((a shr 8) and 255)) * t).roundToInt()
        val bl = ((a and 255) + ((b and 255) - (a and 255)) * t).roundToInt()
        return OPAQUE or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or bl.coerceIn(0, 255)
    }

    private fun hashNoise(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 32767.5f - 1f
    }

    private fun valueNoise(x: Float, y: Float): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val fx = x - xi
        val fy = y - yi
        val u = fx * fx * (3f - 2f * fx)
        val v = fy * fy * (3f - 2f * fy)
        val a = hashNoise(xi, yi)
        val b = hashNoise(xi + 1, yi)
        val c = hashNoise(xi, yi + 1)
        val d = hashNoise(xi + 1, yi + 1)
        val top = a + (b - a) * u
        val bottom = c + (d - c) * u
        return ((top + (bottom - top) * v) * 1.6f).coerceIn(-1f, 1f)
    }

    private fun rgbToHsv(r: Float, g: Float, b: Float, out: FloatArray) {
        val mx = max3(r, g, b)
        val mn = min3(r, g, b)
        val d = mx - mn
        var h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (h < 0f) h += 360f
        out[0] = h
        out[1] = if (mx == 0f) 0f else d / mx
        out[2] = mx
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float, out: FloatArray) {
        val c = v * s
        val hp = h / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val m = v - c
        val (r, g, b) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        out[0] = r + m; out[1] = g + m; out[2] = b + m
    }
}

private fun srgbToLinear(v: Float): Float =
    if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

private fun linearToSrgb(v: Float): Float =
    if (v <= 0.0031308f) v * 12.92f else (1.055 * v.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()
