package com.filmdesk.app.model

import kotlin.math.sqrt

/**
 * Tone curves are stored as a flat list of control points: [x0, y0, x1, y1, …],
 * all in 0..1, sorted by x, first x = 0 and last x = 1.
 */
val IDENTITY_CURVE: List<Float> = listOf(0f, 0f, 1f, 1f)

const val MAX_CURVE_POINTS = 12

fun isIdentityCurve(c: List<Float>): Boolean =
    c.size == 4 && c[0] == 0f && c[1] == 0f && c[2] == 1f && c[3] == 1f

/** Makes a stored/imported curve safe: even length, 2..12 points, in range, sorted, end points at x=0 and x=1. */
fun sanitizeCurve(raw: List<Float>): List<Float> {
    if (raw.size < 4 || raw.size % 2 != 0) return IDENTITY_CURVE
    val pts = raw.chunked(2)
        .map { (x, y) -> (if (x.isNaN()) 0f else x.coerceIn(0f, 1f)) to (if (y.isNaN()) 0f else y.coerceIn(0f, 1f)) }
        .sortedBy { it.first }
        .take(MAX_CURVE_POINTS)
        .toMutableList()
    if (pts.size < 2) return IDENTITY_CURVE
    pts[0] = 0f to pts[0].second
    pts[pts.lastIndex] = 1f to pts.last().second
    return pts.flatMap { listOf(it.first, it.second) }
}

/**
 * Samples the curve into a lookup table of [size] entries using monotone cubic
 * (Fritsch–Carlson) interpolation: smooth like a spline, but never overshoots
 * between points, so highlights and shadows can't wrap or clip unexpectedly.
 */
fun curveLut(c: List<Float>, size: Int = 1024): FloatArray {
    val n = c.size / 2
    val xs = FloatArray(n) { c[it * 2] }
    val ys = FloatArray(n) { c[it * 2 + 1] }
    val out = FloatArray(size)
    if (n < 2) {
        for (i in 0 until size) out[i] = i / (size - 1f)
        return out
    }
    // Slopes of each segment and tangents at each point.
    val d = FloatArray(n - 1) { i ->
        val dx = xs[i + 1] - xs[i]
        if (dx <= 1e-6f) 0f else (ys[i + 1] - ys[i]) / dx
    }
    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) {
        m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    }
    for (i in 0 until n - 1) {
        if (d[i] == 0f) {
            m[i] = 0f; m[i + 1] = 0f
        } else {
            val a = m[i] / d[i]
            val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) {
                val t = 3f / sqrt(s)
                m[i] = t * a * d[i]
                m[i + 1] = t * b * d[i]
            }
        }
    }
    var seg = 0
    for (k in 0 until size) {
        val x = k / (size - 1f)
        while (seg < n - 2 && x > xs[seg + 1]) seg++
        val x0 = xs[seg]
        val x1 = xs[seg + 1]
        val h = x1 - x0
        val y = when {
            h <= 1e-6f -> ys[seg + 1]
            x <= x0 -> ys[seg]
            x >= x1 -> ys[seg + 1]
            else -> {
                val t = (x - x0) / h
                val t2 = t * t
                val t3 = t2 * t
                (2 * t3 - 3 * t2 + 1) * ys[seg] + (t3 - 2 * t2 + t) * h * m[seg] +
                    (-2 * t3 + 3 * t2) * ys[seg + 1] + (t3 - t2) * h * m[seg + 1]
            }
        }
        out[k] = y.coerceIn(0f, 1f)
    }
    return out
}

/** Channels of the tone curve: 0 = Y (all colours together), 1 = Red, 2 = Green, 3 = Blue. */
fun EditParams.curve(channel: Int): List<Float> = when (channel) {
    1 -> curveR
    2 -> curveG
    3 -> curveB
    else -> curveY
}

fun EditParams.withCurve(channel: Int, c: List<Float>): EditParams = when (channel) {
    1 -> copy(curveR = c)
    2 -> copy(curveG = c)
    3 -> copy(curveB = c)
    else -> copy(curveY = c)
}

val EditParams.hasCurves: Boolean
    get() = !isIdentityCurve(curveY) || !isIdentityCurve(curveR) || !isIdentityCurve(curveG) || !isIdentityCurve(curveB)

val EditParams.hasSoftClip: Boolean
    get() = if (softLink) softLow != 0 || softHigh != 100 || softLS != 0 || softHS != 0
    else softRGB != SOFT_RGB_DEFAULT

/** Soft clip value index: Low, High, L.S., H.S. */
const val SOFT_LOW = 0
const val SOFT_HIGH = 1
const val SOFT_LS = 2
const val SOFT_HS = 3
val SOFT_RGB_DEFAULT: List<Int> = List(12) { if (it % 4 == SOFT_HIGH) 100 else 0 }

/** Soft clip value [kind] for channel [ch] (0 R, 1 G, 2 B), from the linked set when linked. */
fun EditParams.softValue(ch: Int, kind: Int): Int =
    if (softLink) when (kind) { SOFT_LOW -> softLow; SOFT_HIGH -> softHigh; SOFT_LS -> softLS; else -> softHS }
    else softRGB.getOrElse(ch * 4 + kind) { if (kind == SOFT_HIGH) 100 else 0 }

/**
 * Sets soft clip [kind] to [v]. Linked: the shared value. Unlinked: only channel [ch].
 * Low stays below High.
 */
fun EditParams.withSoft(ch: Int, kind: Int, v: Int): EditParams {
    val lo = softValue(ch, SOFT_LOW)
    val hi = softValue(ch, SOFT_HIGH)
    val x = when (kind) {
        SOFT_LOW -> v.coerceIn(0, hi - 1)
        SOFT_HIGH -> v.coerceIn(lo + 1, 100)
        else -> v.coerceIn(0, 100)
    }
    return if (softLink) when (kind) {
        SOFT_LOW -> copy(softLow = x); SOFT_HIGH -> copy(softHigh = x); SOFT_LS -> copy(softLS = x); else -> copy(softHS = x)
    } else copy(softRGB = softRGB.toMutableList().also { it[ch * 4 + kind] = x })
}

/** Link or unlink soft clip; unlinking starts every channel from the shared values. */
fun EditParams.withSoftLink(link: Boolean): EditParams =
    if (link == softLink) this
    else if (link) copy(
        softLink = true,
        softLow = softRGB[SOFT_LOW], softHigh = softRGB[SOFT_HIGH], softLS = softRGB[SOFT_LS], softHS = softRGB[SOFT_HS],
    )
    else copy(softLink = false, softRGB = List(12) { softValue(0, it % 4) })

/** Resets every curve, intensity, link and soft clip setting. */
fun EditParams.resetCurves(): EditParams = copy(
    curveY = IDENTITY_CURVE, curveR = IDENTITY_CURVE, curveG = IDENTITY_CURVE, curveB = IDENTITY_CURVE,
    curveLinked = true, curveMix = List(4) { 100 }, softLow = 0, softHigh = 100, softLS = 0, softHS = 0,
    softLink = true, softRGB = SOFT_RGB_DEFAULT,
)

fun EditParams.withMix(channel: Int, v: Int): EditParams =
    copy(curveMix = curveMix.toMutableList().also { it[channel] = v.coerceIn(0, 100) })
