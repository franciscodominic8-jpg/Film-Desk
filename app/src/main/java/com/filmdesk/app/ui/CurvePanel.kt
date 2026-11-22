package com.filmdesk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmdesk.app.model.EditParams
import com.filmdesk.app.model.IDENTITY_CURVE
import com.filmdesk.app.model.MAX_CURVE_POINTS
import com.filmdesk.app.model.curve
import com.filmdesk.app.model.curveLut
import com.filmdesk.app.model.isIdentityCurve
import com.filmdesk.app.model.resetCurves
import com.filmdesk.app.model.withCurve
import com.filmdesk.app.model.withMix
import com.filmdesk.app.ui.theme.FD
import kotlin.math.hypot
import kotlin.math.roundToInt

private val CHANNEL_NAMES = listOf("Y", "R", "G", "B")
private val CHANNEL_LABELS = listOf("Luma", "Red", "Green", "Blue")
private val CHANNEL_COLORS = listOf(Color(0xFFF1F0EC), Color(0xFFFF5A5A), Color(0xFF4CD07D), Color(0xFF5B9BFF))

/**
 * Full-screen Curve editor (layout A): photo on top, and below it three tabs —
 * Curve | Intensity | Soft Clip — so nothing needs scrolling. Works like DaVinci
 * Resolve's Custom Curves:
 *  - Y R G B drawn together, selected one on top with its points
 *  - Link on = Y moves R, G, B together (YRGB); off = Y changes luma only.
 *    Picking R, G or B unlinks, like Resolve.
 *  - Eyedropper marks a sampled colour on each curve
 *  - ⋮ copies a curve to other channels or resets
 */
@Composable
fun CurveScreen(vm: EditorViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var channel by rememberSaveable { mutableIntStateOf(0) }
    // Leaving the Curve screen turns the eyedropper off, so hold-to-compare works again.
    DisposableEffect(Unit) { onDispose { vm.pickerOff() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Column(Modifier.fillMaxSize()) {
            CurveTopBar(vm)
            if (wide) {
                // Unfolded / tablet / landscape: photo left, controls right.
                Row(Modifier.fillMaxSize()) {
                    PreviewArea(vm, Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, bottom = 12.dp))
                    CurveControls(vm, tab, { tab = it }, channel, { channel = it },
                        Modifier.width(360.dp).fillMaxHeight())
                }
            } else {
                // Same photo height as the normal editor (screen − top bar − tool panel − tab bar);
                // the space of the hidden tab bar goes to the graph.
                val panel = minOf(maxHeight * 0.42f, 360.dp)
                val photoH = (maxHeight - 56.dp - panel - 68.dp).coerceAtLeast(160.dp)
                PreviewArea(vm, Modifier.fillMaxWidth().height(photoH))
                CurveControls(vm, tab, { tab = it }, channel, { channel = it },
                    Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun CurveTopBar(vm: EditorViewModel) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { vm.closeCurve() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Pro", tint = FD.Text)
        }
        Text("Curve", color = FD.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = vm::undo, enabled = vm.canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (vm.canUndo) FD.Text else FD.Line)
        }
        IconButton(onClick = vm::redo, enabled = vm.canRedo) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (vm.canRedo) FD.Text else FD.Line)
        }
        Button(
            onClick = { vm.closeCurve() },
            colors = ButtonDefaults.buttonColors(containerColor = FD.Amber, contentColor = FD.OnAmber),
            modifier = Modifier.padding(end = 8.dp),
        ) { Text("Done", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun CurveControls(
    vm: EditorViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    channel: Int,
    onChannel: (Int) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedTabs(listOf("Curve", "Intensity", "Soft Clip"), tab, onTab)
        when (tab) {
            0 -> CurveTab(vm, channel, onChannel, Modifier.fillMaxWidth().weight(1f))
            1 -> IntensityTab(vm)
            else -> SoftClipTab(vm)
        }
    }
}

@Composable
private fun SegmentedTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FD.Surface)
            .border(1.dp, FD.Line, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) FD.Amber else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 13.sp,
                    color = if (on) FD.OnAmber else FD.Text,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun CurveTab(vm: EditorViewModel, channel: Int, onChannel: (Int) -> Unit, modifier: Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val p = vm.params
    val points = p.curve(channel)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Edit row: link · Y R G B · eyedropper · menu
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (p.curveLinked) FD.AmberDim else FD.Surface2)
                    .border(1.dp, if (p.curveLinked) FD.Amber else FD.Line, CircleShape)
                    .toggleable(value = p.curveLinked, role = Role.Switch, onValueChange = { vm.change(vm.params.copy(curveLinked = it)) })
                    .semantics { contentDescription = if (p.curveLinked) "Curves linked" else "Curves unlinked" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (p.curveLinked) Icons.Filled.Link else Icons.Filled.LinkOff, contentDescription = null,
                    tint = if (p.curveLinked) FD.Amber else FD.Muted, modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            CHANNEL_NAMES.forEachIndexed { i, name ->
                val sel = channel == i
                val edited = !isIdentityCurve(p.curve(i)) || p.curveMix.getOrElse(i) { 100 } != 100
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (sel) CHANNEL_COLORS[i] else FD.Surface2)
                        .border(if (edited) 2.dp else 1.dp, if (edited) FD.Amber else FD.Line, CircleShape)
                        .selectable(selected = sel, role = Role.Tab, onClick = {
                            onChannel(i)
                            // Like Resolve: choosing a colour channel breaks the link.
                            if (i != 0 && vm.params.curveLinked) vm.change(vm.params.copy(curveLinked = false))
                        })
                        .semantics { contentDescription = CHANNEL_LABELS[i] + " curve" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (sel) FD.Bg else CHANNEL_COLORS[i])
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.togglePicker() }) {
                Icon(
                    Icons.Filled.Colorize, contentDescription = "Pick a colour from the photo",
                    tint = if (vm.curvePicker) FD.Amber else FD.Text,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Curve options", tint = FD.Text)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    (0..3).filter { it != channel }.forEach { target ->
                        DropdownMenuItem(
                            text = { Text("Copy ${CHANNEL_LABELS[channel]} to ${CHANNEL_LABELS[target]}") },
                            onClick = { vm.change(vm.params.withCurve(target, points)); menuOpen = false },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Copy ${CHANNEL_LABELS[channel]} to all") },
                        onClick = {
                            var np: EditParams = vm.params
                            for (t in 0..3) np = np.withCurve(t, points)
                            vm.change(np); menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Reset ${CHANNEL_LABELS[channel]} curve") },
                        onClick = { vm.change(vm.params.withCurve(channel, IDENTITY_CURVE)); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Reset all curves & soft clip") },
                        onClick = { vm.change(vm.params.resetCurves()); menuOpen = false },
                    )
                }
            }
        }
        // The graph takes all the width and height that's left (a rectangle, like Resolve).
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF18191B))
                .border(1.dp, FD.Line, RoundedCornerShape(10.dp))
        ) {
                CurveCanvas(
                    params = p,
                    channel = channel,
                    histogram = vm.histogram,
                    sample = vm.curveSample,
                    onChange = { vm.updateParams(vm.params.withCurve(channel, it)) },
                    onDone = vm::commit,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
            Text(
                when {
                    vm.curvePicker -> "Tap the photo to see that colour on the curves"
                    channel == 0 && p.curveLinked -> "Luma · linked"
                    channel == 0 -> "Luma · unlinked (brightness only)"
                    else -> CHANNEL_LABELS[channel]
                },
                color = if (vm.curvePicker) FD.Amber else Color(0xFF8E8D88), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun IntensityTab(vm: EditorViewModel) {
    val p = vm.params
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("How strong each curve is (100 = full)", color = FD.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        (0..3).forEach { i ->
            CompactSlider(
                label = CHANNEL_NAMES[i],
                labelColor = CHANNEL_COLORS[i],
                value = p.curveMix.getOrElse(i) { 100 },
                range = 0..100,
                default = 100,
                onChange = { vm.updateParams(vm.params.withMix(i, it)) },
                onDone = vm::commit,
                onReset = { vm.change(vm.params.withMix(i, 100)) },
            )
        }
    }
}

@Composable
private fun SoftClipTab(vm: EditorViewModel) {
    val p = vm.params
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactSlider("Low", FD.Text, p.softLow, 0..100, 0,
            { vm.updateParams(vm.params.copy(softLow = it.coerceAtMost(vm.params.softHigh - 1))) }, vm::commit,
            { vm.change(vm.params.copy(softLow = 0)) })
        CompactSlider("High", FD.Text, p.softHigh, 0..100, 100,
            { vm.updateParams(vm.params.copy(softHigh = it.coerceAtLeast(vm.params.softLow + 1))) }, vm::commit,
            { vm.change(vm.params.copy(softHigh = 100)) })
        CompactSlider("L.S.", FD.Text, p.softLS, 0..100, 0,
            { vm.updateParams(vm.params.copy(softLS = it)) }, vm::commit,
            { vm.change(vm.params.copy(softLS = 0)) })
        CompactSlider("H.S.", FD.Text, p.softHS, 0..100, 0,
            { vm.updateParams(vm.params.copy(softHS = it)) }, vm::commit,
            { vm.change(vm.params.copy(softHS = 0)) })
        Spacer(Modifier.height(6.dp))
        Text(
            "Low / High set the darkest black and the brightest white.\nL.S. / H.S. soften crushed shadows and blown highlights.",
            color = FD.Muted, fontSize = 12.sp, lineHeight = 18.sp,
        )
    }
}

/** One-line slider with a channel label and a value box, like Resolve's curve palette. */
@Composable
private fun CompactSlider(
    label: String,
    labelColor: Color,
    value: Int,
    range: IntRange,
    default: Int,
    onChange: (Int) -> Unit,
    onDone: () -> Unit,
    onReset: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
        Text(label, color = labelColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            onValueChangeFinished = onDone,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = FD.Amber, activeTrackColor = FD.Amber, inactiveTrackColor = FD.Line),
            modifier = Modifier.weight(1f),
        )
        val changed = value != default
        Text(
            value.toString(),
            color = if (changed) FD.Amber else FD.Text, fontFamily = FD.Mono, fontSize = 13.sp,
            modifier = Modifier
                .padding(start = 8.dp)
                .width(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(FD.Surface2)
                .clickable(enabled = changed, onClickLabel = "Reset $label", onClick = onReset)
                .padding(vertical = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun CurveCanvas(
    params: EditParams,
    channel: Int,
    histogram: com.filmdesk.app.engine.ImageEngine.Histogram?,
    sample: FloatArray?,
    onChange: (List<Float>) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
) {
    val points = params.curve(channel)
    val latest by rememberUpdatedState(points)
    val luts = remember(params.curveY, params.curveR, params.curveG, params.curveB) {
        List(4) { curveLut(params.curve(it), 160) }
    }
    var active by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier
            .pointerInput(channel) {
                detectTapGestures(
                    onTap = { pos ->
                        val pts = latest
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        if (nearest(pts, pos, w, h, 30.dp.toPx()) < 0) {
                            insertPoint(pts, pos, w, h)?.let { (list, _) -> onChange(list); onDone() }
                        }
                    },
                    onDoubleTap = { pos ->
                        val pts = latest
                        val i = nearest(pts, pos, size.width.toFloat(), size.height.toFloat(), 30.dp.toPx())
                        if (i > 0 && i < pts.size / 2 - 1) {
                            val list = pts.toMutableList()
                            list.removeAt(i * 2 + 1); list.removeAt(i * 2)
                            onChange(list); onDone()
                        }
                    },
                )
            }
            .pointerInput(channel) {
                var working = mutableListOf<Float>()
                detectDragGestures(
                    onDragStart = { pos ->
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        working = latest.toMutableList()
                        var i = nearest(working, pos, w, h, 34.dp.toPx())
                        if (i < 0) {
                            insertPoint(working, pos, w, h)?.let { (list, idx) ->
                                working = list.toMutableList(); i = idx; onChange(working.toList())
                            }
                        }
                        active = i
                    },
                    onDrag = { change, _ ->
                        val i = active
                        if (i >= 0) {
                            change.consume()
                            val w = size.width.toFloat(); val h = size.height.toFloat()
                            val n = working.size / 2
                            val y = (1f - change.position.y / h).coerceIn(0f, 1f)
                            val x = when (i) {
                                0 -> 0f
                                n - 1 -> 1f
                                else -> (change.position.x / w).coerceIn(working[(i - 1) * 2] + 0.01f, working[(i + 1) * 2] - 0.01f)
                            }
                            working[i * 2] = x; working[i * 2 + 1] = y
                            onChange(working.toList())
                        }
                    },
                    onDragEnd = { active = -1; onDone() },
                    onDragCancel = { active = -1; onDone() },
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val grid = Color(0xFF2A2B2F)
        for (k in 1..3) {
            drawLine(grid, Offset(w * k / 4f, 0f), Offset(w * k / 4f, h), 1f)
            drawLine(grid, Offset(0f, h * k / 4f), Offset(w, h * k / 4f), 1f)
        }
        // Histograms of all channels, faint, like Resolve's overlaid display.
        histogram?.let { hg ->
            listOf(hg.r to CHANNEL_COLORS[1], hg.g to CHANNEL_COLORS[2], hg.b to CHANNEL_COLORS[3], hg.y to Color(0xFFBFBFBF))
                .forEach { (bins, col) ->
                    val path = Path().apply {
                        moveTo(0f, h)
                        for (i in bins.indices) lineTo(w * (i + 0.5f) / bins.size, h - bins[i] * h * 0.9f)
                        lineTo(w, h); close()
                    }
                    drawPath(path, col.copy(alpha = 0.12f))
                }
        }
        drawLine(Color(0xFF4A4C52), Offset(0f, h), Offset(w, 0f), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        // Other channels' curves underneath, then the selected one on top.
        val order = (0..3).filter { it != channel } + channel
        for (c in order) {
            val lut = luts[c]
            if (c != channel && isIdentityCurve(params.curve(c))) continue
            val path = Path().apply {
                for (k in lut.indices) {
                    val x = w * k / (lut.size - 1f); val y = h * (1f - lut[k])
                    if (k == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            val sel = c == channel
            drawPath(path, CHANNEL_COLORS[c].copy(alpha = if (sel) 1f else 0.45f), style = Stroke(width = (if (sel) 2.5.dp else 1.5.dp).toPx()))
        }
        // Eyedropper markers: where the sampled colour sits on each channel.
        sample?.let { s ->
            val values = floatArrayOf(0.2126f * s[0] + 0.7152f * s[1] + 0.0722f * s[2], s[0], s[1], s[2])
            for (c in 0..3) {
                val x = values[c].coerceIn(0f, 1f) * w
                val lut = luts[c]
                val y = h * (1f - lut[(values[c].coerceIn(0f, 1f) * (lut.size - 1)).roundToInt()])
                drawLine(CHANNEL_COLORS[c].copy(alpha = 0.5f), Offset(x, 0f), Offset(x, h), 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                drawCircle(CHANNEL_COLORS[c], 4.dp.toPx(), Offset(x, y))
            }
        }
        // Control points of the selected channel
        val n = points.size / 2
        for (i in 0 until n) {
            val c = Offset(points[i * 2] * w, (1f - points[i * 2 + 1]) * h)
            val r = if (i == active) 10.dp.toPx() else 7.dp.toPx()
            drawCircle(Color(0xFF18191B), r + 2.dp.toPx(), c)
            drawCircle(if (i == active) FD.Amber else CHANNEL_COLORS[channel], r, c)
        }
    }
}

/** Index of the control point within [radius] px of [pos], or −1. */
private fun nearest(pts: List<Float>, pos: Offset, w: Float, h: Float, radius: Float): Int {
    var best = -1
    var bestD = radius
    for (i in 0 until pts.size / 2) {
        val d = hypot(pts[i * 2] * w - pos.x, (1f - pts[i * 2 + 1]) * h - pos.y)
        if (d <= bestD) { bestD = d; best = i }
    }
    return best
}

/** Adds a point at [pos] (kept in x order). Returns the new list and the point's index, or null. */
private fun insertPoint(pts: List<Float>, pos: Offset, w: Float, h: Float): Pair<List<Float>, Int>? {
    val n = pts.size / 2
    if (n >= MAX_CURVE_POINTS) return null
    val x = (pos.x / w).coerceIn(0.01f, 0.99f)
    val y = (1f - pos.y / h).coerceIn(0f, 1f)
    var idx = n - 1
    for (i in 1 until n) if (x < pts[i * 2]) { idx = i; break }
    if (x - pts[(idx - 1) * 2] < 0.02f || pts[idx * 2] - x < 0.02f) return null
    val list = pts.toMutableList()
    list.add(idx * 2, y)
    list.add(idx * 2, x)
    return list to idx
}
