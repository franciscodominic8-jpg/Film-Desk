package com.filmdesk.app.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val ASPECT_ORIGINAL = 0
const val ASPECT_FREE = 8
private const val ASPECT_SQUARE = 1

/** Order of the ratio chips in the Crop tab (2:3 is 3:2 turned with Swap). */
val CROP_RATIO_ORDER = listOf(ASPECT_FREE, ASPECT_ORIGINAL, 1, 2, 3, 5, 6, 7)

/** Composition guides drawn over the photo while cropping (never saved into the photo). */
enum class CropGuide(val label: String, val turns: Int) {
    Off("Off", 1), Thirds("Thirds", 1), Golden("Golden", 1), Spiral("Spiral", 4),
    Diagonal("Diagonal", 1), Triangle("Triangle", 2), Grid("Grid", 1), Center("Center", 1),
}

/** Which part of the crop frame a drag moves. */
enum class CropHandle(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    TL(true, true, false, false), TR(false, true, true, false), BL(true, false, false, true), BR(false, false, true, true),
    L(true, false, false, false), T(false, true, false, false), R(false, false, true, false), B(false, false, false, true),
    Move(false, false, false, false),
}

/** Label of a ratio chip, e.g. "4:5", or "5:4" when turned. */
fun ratioLabel(index: Int, flipped: Boolean): String {
    val label = ASPECTS[index].label
    if (!flipped || index == ASPECT_FREE || index == ASPECT_ORIGINAL || index == ASPECT_SQUARE) return label
    val parts = label.split(':')
    return if (parts.size == 2) parts[1] + ":" + parts[0] else label
}

/** Locked width/height ratio in pixels for a photo of [w]×[h], or null for Free. */
fun EditParams.lockRatio(w: Int, h: Int): Float? {
    val base = when (aspect) {
        ASPECT_FREE -> return null
        ASPECT_ORIGINAL -> w.toFloat() / h
        else -> ASPECTS[aspect.coerceIn(0, ASPECTS.lastIndex)].ratio
    }
    if (base <= 0f) return null
    return if (aspectFlip && aspect != ASPECT_ORIGINAL) 1f / base else base
}

val EditParams.hasCropRect: Boolean get() = cropL > 0f || cropT > 0f || cropR < 1f || cropB < 1f

/** The biggest rectangle of ratio [rf] (width/height in fractions) centred on (cx, cy). */
fun centeredRect(rf: Float, cx: Float = 0.5f, cy: Float = 0.5f): FloatArray {
    var fw = min(1f, rf)
    var fh = fw / rf
    if (fh > 1f) { fh = 1f; fw = rf }
    val l = (cx - fw / 2f).coerceIn(0f, 1f - fw)
    val t = (cy - fh / 2f).coerceIn(0f, 1f - fh)
    return floatArrayOf(l, t, l + fw, t + fh)
}

/**
 * The crop actually used, as fractions [left, top, right, bottom]. Older edits stored only
 * a ratio, which meant a centred crop; that is still honoured when no rectangle is stored.
 */
fun EditParams.cropRect(w: Int, h: Int): FloatArray {
    if (hasCropRect) {
        val l = cropL.coerceIn(0f, 0.99f)
        val t = cropT.coerceIn(0f, 0.99f)
        return floatArrayOf(l, t, cropR.coerceIn(l + 0.01f, 1f), cropB.coerceIn(t + 0.01f, 1f))
    }
    val r = lockRatio(w, h) ?: return floatArrayOf(0f, 0f, 1f, 1f)
    return centeredRect(r * h / w)
}

fun EditParams.withCropRect(r: FloatArray) = copy(cropL = r[0], cropT = r[1], cropR = r[2], cropB = r[3])

/** Zoom that keeps a [w]×[h] frame filled after turning the photo by [degrees]. */
fun straightenScale(degrees: Float, w: Int, h: Int): Float {
    if (w <= 0 || h <= 0) return 1f
    val a = Math.toRadians(abs(degrees).toDouble())
    val long = max(w, h).toDouble() / min(w, h)
    return (cos(a) + long * sin(a)).toFloat()
}

/** What the crop view shows: the whole photo, upright, so the frame can be drawn on top. */
fun EditParams.cropBase() = copy(
    straighten = 0f, cropL = 0f, cropT = 0f, cropR = 1f, cropB = 1f, aspect = ASPECT_FREE, aspectFlip = false,
)

/**
 * New crop rectangle while dragging [handle]. Positions are fractions of the photo; [dx], [dy]
 * are how far the finger moved. [rf] = locked width/height in fractions (null = free).
 */
fun dragCrop(r0: FloatArray, handle: CropHandle, dx: Float, dy: Float, rf: Float?, minW: Float, minH: Float): FloatArray {
    val (l0, t0, r0r, b0) = listOf(r0[0], r0[1], r0[2], r0[3])
    if (handle == CropHandle.Move) {
        val w = r0r - l0; val h = b0 - t0
        val l = (l0 + dx).coerceIn(0f, 1f - w)
        val t = (t0 + dy).coerceIn(0f, 1f - h)
        return floatArrayOf(l, t, l + w, t + h)
    }
    if (rf == null) {
        var l = l0; var t = t0; var r = r0r; var b = b0
        if (handle.left) l = (l0 + dx).coerceIn(0f, r0r - minW)
        if (handle.right) r = (r0r + dx).coerceIn(l0 + minW, 1f)
        if (handle.top) t = (t0 + dy).coerceIn(0f, b0 - minH)
        if (handle.bottom) b = (b0 + dy).coerceIn(t0 + minH, 1f)
        return floatArrayOf(l, t, r, b)
    }
    val minWl = max(minW, minH * rf)
    val corner = (handle.left || handle.right) && (handle.top || handle.bottom)
    if (corner) {
        // The opposite corner stays put.
        val ax = if (handle.left) r0r else l0
        val ay = if (handle.top) b0 else t0
        val px = (if (handle.left) l0 else r0r) + dx
        val py = (if (handle.top) t0 else b0) + dy
        val maxW = min(if (handle.left) ax else 1f - ax, (if (handle.top) ay else 1f - ay) * rf)
        if (maxW < minWl) return r0
        val w = max(abs(px - ax), abs(py - ay) * rf).coerceIn(minWl, maxW)
        val h = w / rf
        val l = if (handle.left) ax - w else ax
        val t = if (handle.top) ay - h else ay
        return floatArrayOf(l, t, l + w, t + h)
    }
    if (handle.left || handle.right) {
        // Width follows the finger; height follows the ratio, centred.
        val ax = if (handle.left) r0r else l0
        val cy = (t0 + b0) / 2f
        val px = (if (handle.left) l0 else r0r) + dx
        val maxW = min(if (handle.left) ax else 1f - ax, 2f * min(cy, 1f - cy) * rf)
        if (maxW < minWl) return r0
        val w = abs(px - ax).coerceIn(minWl, maxW)
        val h = w / rf
        val l = if (handle.left) ax - w else ax
        return floatArrayOf(l, cy - h / 2f, l + w, cy + h / 2f)
    }
    // Top or bottom edge.
    val ay = if (handle.top) b0 else t0
    val cx = (l0 + r0r) / 2f
    val py = (if (handle.top) t0 else b0) + dy
    val minHl = minWl / rf
    val maxH = min(if (handle.top) ay else 1f - ay, 2f * min(cx, 1f - cx) / rf)
    if (maxH < minHl) return r0
    val h = abs(py - ay).coerceIn(minHl, maxH)
    val w = h * rf
    val t = if (handle.top) ay - h else ay
    return floatArrayOf(cx - w / 2f, t, cx + w / 2f, t + h)
}

/** Crop rectangle after turning the photo 90° (clockwise when [cw]). */
fun rotateRect(r: FloatArray, cw: Boolean): FloatArray =
    if (cw) floatArrayOf(1f - r[3], r[0], 1f - r[1], r[2]) else floatArrayOf(r[1], 1f - r[2], r[3], 1f - r[0])
