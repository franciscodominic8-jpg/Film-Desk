package com.filmdesk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmdesk.app.model.ASPECT_FREE
import com.filmdesk.app.model.CROP_RATIO_ORDER
import com.filmdesk.app.model.CropGuide
import com.filmdesk.app.model.CropHandle
import com.filmdesk.app.model.cropRect
import com.filmdesk.app.model.dragCrop
import com.filmdesk.app.model.lockRatio
import com.filmdesk.app.model.ratioLabel
import com.filmdesk.app.model.straightenScale
import com.filmdesk.app.model.withCropRect
import com.filmdesk.app.ui.theme.FD
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ------------------------------------------------------------------ photo + frame

/**
 * Crop view: the whole upright photo (with its look), turned live by Straighten, the crop
 * frame with handles on top, and a composition guide inside the frame. Pinch to zoom.
 */
@Composable
internal fun CropView(vm: EditorViewModel, modifier: Modifier) {
    val bmp = vm.preview ?: vm.source
    Box(modifier.background(Color.Black).clipToBounds(), contentAlignment = Alignment.Center) {
        if (vm.loading || bmp == null) {
            CircularProgressIndicator(color = FD.Amber)
            return@Box
        }
        val img = remember(bmp) { bmp.asImageBitmap() }
        val iw = bmp.width
        val ih = bmp.height
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Two fingers: zoom in for a precise crop (one finger moves the frame).
                    detectTransformGestures { _, dPan, dZoom, _ ->
                        zoom = (zoom * dZoom).coerceIn(1f, 4f)
                        pan = if (zoom <= 1.001f) Offset.Zero else Offset(
                            (pan.x + dPan.x).coerceIn(-size.width * (zoom - 1) / 2f, size.width * (zoom - 1) / 2f),
                            (pan.y + dPan.y).coerceIn(-size.height * (zoom - 1) / 2f, size.height * (zoom - 1) / 2f),
                        )
                    }
                }
                .padding(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val fit = min(constraints.maxWidth / iw.toFloat(), constraints.maxHeight / ih.toFloat())
            val wDp = with(density) { (iw * fit).toDp() }
            val hDp = with(density) { (ih * fit).toDp() }
            val p = vm.params
            Box(
                Modifier
                    .size(wDp, hDp)
                    .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }
            ) {
                val s = straightenScale(p.straighten, iw, ih)
                Image(
                    img, contentDescription = "Photo",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .graphicsLayer { rotationZ = p.straighten; scaleX = s; scaleY = s },
                )
                CropOverlay(vm, iw, ih, Modifier.fillMaxSize())
            }
        }
        Badge(cropBadge(vm, iw, ih), Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        if (vm.cropGuide.turns > 1) {
            Badge(
                "${vm.cropGuide.label.uppercase()} · TAP AGAIN TO TURN",
                Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
    }
}

private fun cropBadge(vm: EditorViewModel, iw: Int, ih: Int): String {
    val p = vm.params
    if (vm.straightening) return signedDeg(p.straighten) + " · STRAIGHTEN"
    val r = p.cropRect(iw, ih)
    val k = if (vm.fullLongEdge > 0) vm.fullLongEdge / max(iw, ih).toFloat() else 1f
    val pw = ((r[2] - r[0]) * iw * k).roundToInt()
    val ph = ((r[3] - r[1]) * ih * k).roundToInt()
    val name = if (p.aspect == ASPECT_FREE) "FREE" else ratioLabel(p.aspect, p.aspectFlip).uppercase().replace(":", " : ")
    return "$name · $pw × $ph"
}

private fun signedDeg(v: Float): String {
    val t = String.format(java.util.Locale.US, "%.1f°", abs(v))
    return when { v > 0.05f -> "+$t"; v < -0.05f -> "−$t"; else -> "0.0°" }
}

@Composable
private fun CropOverlay(vm: EditorViewModel, iw: Int, ih: Int, modifier: Modifier) {
    val params by rememberUpdatedState(vm.params)
    Canvas(
        modifier.pointerInput(iw, ih) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val start = params
                val r0 = start.cropRect(iw, ih)
                val handle = hitHandle(r0, down.position, w, h, 30.dp.toPx()) ?: return@awaitEachGesture
                val rf = start.lockRatio(iw, ih)?.let { it * ih / iw }
                val minW = 48.dp.toPx() / w
                val minH = 48.dp.toPx() / h
                var dragging = false
                while (true) {
                    val ev = awaitPointerEvent()
                    if (ev.changes.size > 1) break            // second finger: let pinch-zoom take over
                    val ch = ev.changes.first()
                    if (!ch.pressed) break
                    val d = ch.position - down.position
                    if (!dragging && d.getDistance() > viewConfiguration.touchSlop) {
                        dragging = true
                        vm.cropHandle = handle
                    }
                    if (dragging) {
                        ch.consume()
                        val nr = dragCrop(r0, handle, d.x / w, d.y / h, rf, minW, minH)
                        vm.updateParams(vm.params.withCropRect(nr))
                    }
                }
                vm.cropHandle = null
                if (dragging) vm.commit()
            }
        }
    ) {
        val r = params.cropRect(iw, ih)
        val box = Rect(r[0] * size.width, r[1] * size.height, r[2] * size.width, r[3] * size.height)
        // Dim everything outside the frame.
        val shade = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRect(box)
        }
        drawPath(shade, Color(0xA6000000))
        if (vm.straightening || vm.cropHandle != null && vm.cropGuide == CropGuide.Off) {
            drawGuide(CropGuide.Grid, 0, box, Color(0xB3FFFFFF), 0.8.dp.toPx())
        }
        drawGuide(vm.cropGuide, vm.guideTurn, box, Color(0xC0FFFFFF), 1.dp.toPx())
        drawRect(Color(0xE6FFFFFF), box.topLeft, box.size, style = Stroke(1.dp.toPx()))
        drawHandles(box, vm.cropHandle)
    }
}

private fun hitHandle(r: FloatArray, pos: Offset, w: Float, h: Float, hit: Float): CropHandle? {
    val l = r[0] * w; val t = r[1] * h; val rr = r[2] * w; val b = r[3] * h
    fun near(x: Float, y: Float) = hypot(pos.x - x, pos.y - y) <= hit
    when {
        near(l, t) -> return CropHandle.TL
        near(rr, t) -> return CropHandle.TR
        near(l, b) -> return CropHandle.BL
        near(rr, b) -> return CropHandle.BR
    }
    val inX = pos.x in l..rr
    val inY = pos.y in t..b
    return when {
        inY && abs(pos.x - l) <= hit -> CropHandle.L
        inY && abs(pos.x - rr) <= hit -> CropHandle.R
        inX && abs(pos.y - t) <= hit -> CropHandle.T
        inX && abs(pos.y - b) <= hit -> CropHandle.B
        inX && inY -> CropHandle.Move
        else -> null
    }
}

private fun DrawScope.drawHandles(box: Rect, active: CropHandle?) {
    val len = 18.dp.toPx()
    val sw = 3.5.dp.toPx()
    fun col(h: CropHandle) = if (active == h) FD.Amber else Color.White
    fun corner(x: Float, y: Float, sx: Float, sy: Float, h: CropHandle) {
        val c = col(h)
        drawLine(c, Offset(x, y), Offset(x + sx * len, y), sw, StrokeCap.Square)
        drawLine(c, Offset(x, y), Offset(x, y + sy * len), sw, StrokeCap.Square)
    }
    corner(box.left, box.top, 1f, 1f, CropHandle.TL)
    corner(box.right, box.top, -1f, 1f, CropHandle.TR)
    corner(box.left, box.bottom, 1f, -1f, CropHandle.BL)
    corner(box.right, box.bottom, -1f, -1f, CropHandle.BR)
    val e = 12.dp.toPx()
    val cx = box.center.x
    val cy = box.center.y
    if (box.width > 4 * len) {
        drawLine(col(CropHandle.T), Offset(cx - e, box.top), Offset(cx + e, box.top), sw, StrokeCap.Round)
        drawLine(col(CropHandle.B), Offset(cx - e, box.bottom), Offset(cx + e, box.bottom), sw, StrokeCap.Round)
    }
    if (box.height > 4 * len) {
        drawLine(col(CropHandle.L), Offset(box.left, cy - e), Offset(box.left, cy + e), sw, StrokeCap.Round)
        drawLine(col(CropHandle.R), Offset(box.right, cy - e), Offset(box.right, cy + e), sw, StrokeCap.Round)
    }
}

// ------------------------------------------------------------------ composition guides

/** Draws a composition guide inside [b]. [turn] mirrors the spiral (4 ways) or triangle (2 ways). */
internal fun DrawScope.drawGuide(g: CropGuide, turn: Int, b: Rect, c: Color, sw: Float) {
    if (g == CropGuide.Off || b.width < 2f || b.height < 2f) return
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, col: Color = c, w: Float = sw) =
        drawLine(col, Offset(b.left + x1 * b.width, b.top + y1 * b.height), Offset(b.left + x2 * b.width, b.top + y2 * b.height), w)
    clipRect(b.left, b.top, b.right, b.bottom) {
        when (g) {
            CropGuide.Thirds -> {
                val t = 1f / 3f
                line(t, 0f, t, 1f); line(2 * t, 0f, 2 * t, 1f); line(0f, t, 1f, t); line(0f, 2 * t, 1f, 2 * t)
            }
            CropGuide.Golden -> {
                val q = 0.382f
                line(q, 0f, q, 1f); line(1 - q, 0f, 1 - q, 1f); line(0f, q, 1f, q); line(0f, 1 - q, 1f, 1 - q)
            }
            CropGuide.Grid -> {
                val gc = c.copy(alpha = c.alpha * 0.6f)
                for (i in 1..5) { val f = i / 6f; line(f, 0f, f, 1f, gc, sw * 0.8f); line(0f, f, 1f, f, gc, sw * 0.8f) }
            }
            CropGuide.Diagonal -> {
                line(0f, 0f, 1f, 1f); line(1f, 0f, 0f, 1f)
                if (b.width > b.height) {
                    val k = b.height / b.width
                    val faint = c.copy(alpha = c.alpha * 0.5f)
                    line(0f, 0f, k, 1f, faint); line(1f, 0f, 1f - k, 1f, faint)
                }
            }
            CropGuide.Center -> {
                val faint = c.copy(alpha = c.alpha * 0.55f)
                line(0.5f, 0f, 0.5f, 1f, faint); line(0f, 0.5f, 1f, 0.5f, faint)
                drawCircle(c, min(b.width, b.height) * 0.08f, b.center, style = Stroke(sw))
            }
            CropGuide.Triangle -> mirrored(turn % 2 == 1, false, b) {
                // One diagonal plus the perpendiculars to it from the other two corners.
                val a = Offset(b.left, b.bottom)
                val d = Offset(b.right, b.top)
                val dir = d - a
                val len2 = dir.x * dir.x + dir.y * dir.y
                fun foot(p: Offset): Offset {
                    val t = ((p.x - a.x) * dir.x + (p.y - a.y) * dir.y) / len2
                    return Offset(a.x + t * dir.x, a.y + t * dir.y)
                }
                drawLine(c, a, d, sw)
                drawLine(c, b.topLeft, foot(b.topLeft), sw)
                drawLine(c, b.bottomRight, foot(b.bottomRight), sw)
            }
            CropGuide.Spiral -> mirrored(turn == 1 || turn == 2, turn >= 2, b) { goldenSpiral(b, c, sw) }
            CropGuide.Off -> Unit
        }
    }
}

private inline fun DrawScope.mirrored(x: Boolean, y: Boolean, b: Rect, block: DrawScope.() -> Unit) {
    if (!x && !y) block() else scale(if (x) -1f else 1f, if (y) -1f else 1f, b.center) { block() }
}

/** Golden spiral: quarter arcs in ever smaller golden sub-rectangles. */
private fun DrawScope.goldenSpiral(b: Rect, c: Color, sw: Float) {
    var x = b.left; var y = b.top; var w = b.width; var h = b.height
    val g = 0.618f
    val start = if (h > w) 1 else 0
    val faint = c.copy(alpha = c.alpha * 0.35f)
    for (i in 0 until 9) {
        val pX: Float; val pY: Float; val pW: Float; val pH: Float
        val cx: Float; val cy: Float; val angle: Float
        when ((i + start) % 4) {
            0 -> { pW = w * g; pH = h; pX = x; pY = y; cx = pX + pW; cy = pY + pH; angle = 180f; x += pW; w -= pW }
            1 -> { pW = w; pH = h * g; pX = x; pY = y; cx = pX; cy = pY + pH; angle = 270f; y += pH; h -= pH }
            2 -> { pW = w * g; pH = h; pX = x + w - pW; pY = y; cx = pX; cy = pY; angle = 0f; w -= pW }
            else -> { pW = w; pH = h * g; pX = x; pY = y + h - pH; cx = pX + pW; cy = pY; angle = 90f; h -= pH }
        }
        if (pW < 1f || pH < 1f) break
        drawRect(faint, Offset(pX, pY), Size(pW, pH), style = Stroke(sw * 0.7f))
        drawArc(
            c, angle, 90f, useCenter = false,
            topLeft = Offset(cx - pW, cy - pH), size = Size(pW * 2, pH * 2), style = Stroke(sw * 1.3f),
        )
    }
}

// ------------------------------------------------------------------ tools panel

@Composable
internal fun CropTools(vm: EditorViewModel) {
    val p = vm.params
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Straighten
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Straighten", color = FD.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    signedDeg(p.straighten),
                    color = if (abs(p.straighten) > 0.05f) FD.Amber else FD.Muted,
                    fontSize = 13.sp, fontFamily = FD.Mono,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = "Reset straighten") {
                        vm.change(vm.params.copy(straighten = 0f))
                    }.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            StraightenRuler(vm)
        }

        // Ratio
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Ratio", Modifier.weight(1f))
            if (p.aspect != ASPECT_FREE) Text("${ratioLabel(p.aspect, p.aspectFlip)} locked", color = FD.Amber, fontSize = 12.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CROP_RATIO_ORDER.forEach { i ->
                Pill(
                    ratioLabel(i, i == p.aspect && p.aspectFlip),
                    selected = p.aspect == i,
                    onClick = { vm.setCropRatio(i) },
                )
            }
        }

        // Composition
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Composition", Modifier.weight(1f))
            if (vm.cropGuide.turns > 1) Text("Tap ${vm.cropGuide.label} again to turn", color = FD.Muted, fontSize = 12.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CropGuide.entries.forEach { g -> GuideChip(g, selected = vm.cropGuide == g, turn = if (vm.cropGuide == g) vm.guideTurn else 0) { vm.pickGuide(g) } }
        }

        // Turn, flip, swap, reset
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(Icons.Filled.RotateLeft, "Rotate left") { vm.rotateCrop(cw = false) }
            ToolButton(Icons.Filled.RotateRight, "Rotate right") { vm.rotateCrop(cw = true) }
            ToolButton(Icons.Filled.Flip, "Flip", on = p.flipH) { vm.flipCrop() }
            ToolButton(Icons.Filled.SwapHoriz, "Swap W/H") { vm.swapCropRatio() }
            ToolButton(Icons.Filled.RestartAlt, "Reset") { vm.resetCrop() }
        }
    }
}

/** Ruler from −45° to +45°: drag sideways; double-tap to level. */
@Composable
private fun StraightenRuler(vm: EditorViewModel) {
    val pxPerDeg = with(LocalDensity.current) { 7.dp.toPx() }
    val params by rememberUpdatedState(vm.params)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { vm.change(vm.params.copy(straighten = 0f)) })
            }
            .pointerInput(Unit) {
                var raw = 0f
                detectHorizontalDragGestures(
                    onDragStart = { raw = params.straighten; vm.straightening = true },
                    onDragEnd = { vm.straightening = false; vm.commit() },
                    onDragCancel = { vm.straightening = false; vm.commit() },
                ) { ch, dx ->
                    ch.consume()
                    raw = (raw - dx / pxPerDeg).coerceIn(-45f, 45f)
                    // Snap to level near 0°, otherwise 0.1° steps.
                    val v = if (abs(raw) < 0.4f) 0f else (raw * 10f).roundToInt() / 10f
                    vm.updateParams(vm.params.copy(straighten = v))
                }
            }
    ) {
        val v = params.straighten
        val mid = size.width / 2f
        for (i in -45..45) {
            val x = mid + (i - v) * pxPerDeg
            if (x < 0f || x > size.width) continue
            val big = i % 5 == 0
            drawLine(
                if (i == 0) FD.Text else Color(0xFF6B6B67),
                Offset(x, if (big) 6.dp.toPx() else 12.dp.toPx()), Offset(x, 24.dp.toPx()),
                if (big) 1.5.dp.toPx() else 1.dp.toPx(),
            )
        }
        drawLine(FD.Amber, Offset(mid, 0f), Offset(mid, 28.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun GuideChip(g: CropGuide, selected: Boolean, turn: Int, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = g.label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val stroke = if (selected) FD.Amber else Color(0xFFC9C8C3)
        Box(
            Modifier
                .size(58.dp, 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) FD.AmberDim else FD.Surface)
                .border(if (selected) 2.dp else 1.dp, if (selected) FD.Amber else FD.Line, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(44.dp, 28.dp)) {
                val b = Rect(Offset.Zero, size)
                if (g == CropGuide.Off) {
                    drawLine(Color(0xFF6B6B67), Offset(6f, size.height - 4f), Offset(size.width - 6f, 4f), 1.5.dp.toPx())
                } else {
                    drawRect(stroke.copy(alpha = 0.5f), style = Stroke(1.dp.toPx()))
                    drawGuide(g, turn, b, stroke, 1.2.dp.toPx())
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            g.label, fontSize = 11.sp, color = if (selected) FD.Amber else FD.Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun RowScope.ToolButton(icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) FD.AmberDim else FD.Surface)
            .border(1.dp, if (on) FD.Amber else FD.Line, RoundedCornerShape(12.dp))
            .clickable(onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (on) FD.Amber else Color(0xFFC9C8C3), modifier = Modifier.size(20.dp))
        Text(label, fontSize = 10.sp, color = Color(0xFFC9C8C3), maxLines = 1)
    }
}
