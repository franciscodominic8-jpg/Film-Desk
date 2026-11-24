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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.HorizontalDivider
import com.filmdesk.app.model.SOFT_HIGH
import com.filmdesk.app.model.SOFT_HS
import com.filmdesk.app.model.SOFT_LOW
import com.filmdesk.app.model.SOFT_LS
import com.filmdesk.app.model.softValue
import com.filmdesk.app.model.withSoft
import com.filmdesk.app.model.withSoftLink
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
/** Dark button fills for the Y R G B selectors, like Resolve's curve palette. */
private val CHANNEL_DARK = listOf(Color(0xFF3A3A3C), Color(0xFF5A2224), Color(0xFF1F4A2C), Color(0xFF223A66))
private val GRAPH_BG = Color(0xFF1C1D20)

/**
 * Full-screen Curve editor, DaVinci Resolve style:
 *  - big graph with the R, G, B and Y histograms overlapping behind the curves
 *  - Edit: link · Y R G B · eyedropper · ⋮, and an intensity slider + number box per channel
 *  - Soft Clip: link · R G B, then Low, Low Soft, High, High Soft (per channel when unlinked)
 * Phone: photo (same size as the normal editor), graph, then the controls (scroll down for
 * Soft Clip). Unfolded: photo and graph on the left, controls on the right.
 */
@Composable
fun CurveScreen(vm: EditorViewModel) {
    var channel by rememberSaveable { mutableIntStateOf(0) }
    var softCh by rememberSaveable { mutableIntStateOf(0) }
    // Leaving the Curve screen turns the eyedropper off, so hold-to-compare works again.
    DisposableEffect(Unit) { onDispose { vm.pickerOff() } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val wide = maxWidth >= 600.dp
        // Same photo height as the normal editor (top bar, tool panel and tab bar taken off).
        val photoH = (maxHeight - 48.dp - maxHeight * PANEL_FRACTION - 54.dp).coerceAtLeast(150.dp)
        val graphH = (maxHeight - 48.dp - photoH - 52.dp).coerceIn(170.dp, 420.dp)
        Column(Modifier.fillMaxSize()) {
            CurveTopBar(vm)
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        PreviewArea(vm, Modifier.fillMaxWidth().weight(0.45f))
                        Graph(vm, channel, Modifier.fillMaxWidth().weight(0.55f))
                    }
                    Column(
                        Modifier.width(340.dp).fillMaxHeight().background(FD.Bg).verticalScroll(rememberScrollState()),
                    ) {
                        EditSection(vm, channel) { channel = it }
                        HorizontalDivider(color = FD.Line, thickness = 0.5.dp)
                        SoftClipSection(vm, softCh) { softCh = it }
                        Text(
                            "Tap the graph to add points · drag to move · double-tap to delete",
                            color = FD.Muted, fontSize = 11.sp, modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            } else {
                PreviewArea(vm, Modifier.fillMaxWidth().height(photoH))
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    Graph(vm, channel, Modifier.fillMaxWidth().height(graphH))
                    EditSection(vm, channel) { channel = it }
                    HorizontalDivider(color = FD.Line, thickness = 0.5.dp)
                    SoftClipSection(vm, softCh) { softCh = it }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CurveTopBar(vm: EditorViewModel) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            modifier = Modifier.padding(end = 8.dp).height(34.dp),
        ) { Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
    }
}

@Composable
private fun Graph(vm: EditorViewModel, channel: Int, modifier: Modifier) {
    val p = vm.params
    Box(modifier.background(GRAPH_BG)) {
        CurveCanvas(
            params = p,
            channel = channel,
            histogram = vm.histogram,
            sample = vm.curveSample,
            onChange = { vm.updateParams(vm.params.withCurve(channel, it)) },
            onDone = vm::commit,
            modifier = Modifier.fillMaxSize().padding(10.dp),
        )
        Text(
            when {
                vm.curvePicker -> "Tap the photo to see that colour on the curves"
                channel == 0 && p.curveLinked -> "Y · linked"
                channel == 0 -> "Y · brightness only"
                else -> CHANNEL_LABELS[channel]
            },
            color = if (vm.curvePicker) FD.Amber else Color(0xFF8E8D88), fontSize = 10.sp, fontFamily = FD.Mono,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 6.dp),
        )
    }
}

/** Small square channel button (Y R G B), filled brighter when picked. */
@Composable
private fun ChannelButton(i: Int, selected: Boolean, edited: Boolean, description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(30.dp)
            .clip(shape)
            .background(if (selected) (if (i == 0) FD.Text else CHANNEL_COLORS[i].copy(alpha = 0.85f)) else CHANNEL_DARK[i])
            .border(if (edited) 1.5.dp else 0.dp, if (edited) FD.Amber else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            CHANNEL_NAMES[i], fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (selected) FD.Bg else CHANNEL_COLORS[i],
        )
    }
}

@Composable
private fun LinkButton(on: Boolean, description: String, onToggle: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (on) Icons.Filled.Link else Icons.Filled.LinkOff, contentDescription = null,
            tint = if (on) FD.Text else FD.Muted, modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun EditSection(vm: EditorViewModel, channel: Int, onChannel: (Int) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val p = vm.params
    val points = p.curve(channel)
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Edit", color = FD.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.togglePicker() }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Colorize, contentDescription = "Pick a colour from the photo",
                    tint = if (vm.curvePicker) FD.Amber else FD.Muted, modifier = Modifier.size(18.dp),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Curve options", tint = FD.Muted, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = FD.Surface2) {
                    (0..3).filter { it != channel }.forEach { target ->
                        DropdownMenuItem(
                            text = { Text("Copy ${CHANNEL_LABELS[channel]} to ${CHANNEL_LABELS[target]}", color = FD.Text) },
                            onClick = { vm.change(vm.params.withCurve(target, points)); menuOpen = false },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Copy ${CHANNEL_LABELS[channel]} to all", color = FD.Text) },
                        onClick = {
                            var np: EditParams = vm.params
                            for (t in 0..3) np = np.withCurve(t, points)
                            vm.change(np); menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Reset ${CHANNEL_LABELS[channel]} curve", color = FD.Text) },
                        onClick = { vm.change(vm.params.withCurve(channel, IDENTITY_CURVE)); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Reset all curves & soft clip", color = FD.Text) },
                        onClick = { vm.change(vm.params.resetCurves()); menuOpen = false },
                    )
                }
            }
            LinkButton(p.curveLinked, if (p.curveLinked) "Curves linked" else "Curves unlinked") {
                vm.change(vm.params.copy(curveLinked = it))
            }
            CHANNEL_NAMES.indices.forEach { i ->
                val edited = !isIdentityCurve(p.curve(i)) || p.curveMix.getOrElse(i) { 100 } != 100
                ChannelButton(i, channel == i, edited, CHANNEL_LABELS[i] + " curve") {
                    onChannel(i)
                    // Like Resolve: choosing a colour channel breaks the link.
                    if (i != 0 && vm.params.curveLinked) vm.change(vm.params.copy(curveLinked = false))
                }
            }
        }
        // Intensity per channel (100 = full curve)
        (0..3).forEach { i ->
            ValueSliderRow(
                label = null,
                color = CHANNEL_COLORS[i],
                value = p.curveMix.getOrElse(i) { 100 },
                range = 0..100,
                default = 100,
                description = "${CHANNEL_LABELS[i]} intensity",
                onChange = { vm.updateParams(vm.params.withMix(i, it)) },
                onDone = vm::commit,
                onSet = { vm.change(vm.params.withMix(i, it)) },
            )
        }
    }
}

@Composable
private fun SoftClipSection(vm: EditorViewModel, softCh: Int, onSoftCh: (Int) -> Unit) {
    val p = vm.params
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Soft Clip", color = FD.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            LinkButton(p.softLink, if (p.softLink) "Soft clip linked" else "Soft clip per channel") {
                vm.change(vm.params.withSoftLink(it, from = softCh))
            }
            (1..3).forEach { i ->
                val ch = i - 1
                val edited = !p.softLink && (0..3).any { k -> p.softValue(ch, k) != (if (k == SOFT_HIGH) 100 else 0) }
                ChannelButton(i, selected = p.softLink || softCh == ch, edited = edited, description = "${CHANNEL_LABELS[i]} soft clip") {
                    onSoftCh(ch)
                    // Picking one colour works on that channel alone, like Resolve.
                    if (vm.params.softLink) vm.change(vm.params.withSoftLink(false))
                }
            }
        }
        val ch = if (p.softLink) 0 else softCh
        listOf(SOFT_LOW to "Low", SOFT_LS to "Low Soft", SOFT_HIGH to "High", SOFT_HS to "High Soft").forEach { (kind, label) ->
            ValueSliderRow(
                label = label,
                color = Color(0xFFBDBDBA),
                value = p.softValue(ch, kind),
                range = 0..100,
                default = if (kind == SOFT_HIGH) 100 else 0,
                description = "Soft clip $label",
                onChange = { vm.updateParams(vm.params.withSoft(ch, kind, it)) },
                onDone = vm::commit,
                onSet = { vm.change(vm.params.withSoft(ch, kind, it)) },
            )
        }
        Text(
            if (p.softLink) "Low / High set the darkest black and brightest white; the Soft controls roll off crushed shadows and blown highlights."
            else "Editing ${CHANNEL_LABELS[softCh + 1]} only. Tap the link to set all channels together.",
            color = FD.Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Resolve-style row: optional label, thin coloured slider, and a number box you can type in. */
@Composable
private fun ValueSliderRow(
    label: String?,
    color: Color,
    value: Int,
    range: IntRange,
    default: Int,
    description: String,
    onChange: (Int) -> Unit,
    onDone: () -> Unit,
    onSet: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(36.dp)) {
        if (label != null) {
            Text(label, color = FD.Muted, fontSize = 12.sp, modifier = Modifier.width(72.dp))
        }
        ThinSlider(value, range, default, color, onChange, onDone, Modifier.weight(1f).semantics { contentDescription = description })
        Spacer(Modifier.width(8.dp))
        NumberBox(value, range, changed = value != default, onSet = onSet)
    }
}

/**
 * Thin coloured slider. Only a sideways drag changes it (up/down scrolls the page), and the
 * value moves from where it was instead of jumping to the finger. Double-tap: back to [default].
 */
@Composable
private fun ThinSlider(value: Int, range: IntRange, default: Int, color: Color, onChange: (Int) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    val cb by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    val current by rememberUpdatedState(value)
    Canvas(
        modifier
            .height(32.dp)
            .semantics {
                stateDescription = value.toString()
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat())
                setProgress { t -> cb(t.roundToInt().coerceIn(range.first, range.last)); done(); true }
            }
            .pointerInput(range, default) {
                val pad = 9.dp.toPx()
                detectTapGestures(
                    onTap = { pos ->
                        val f = ((pos.x - pad) / (size.width - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        cb((range.first + f * (range.last - range.first)).roundToInt()); done()
                    },
                    onDoubleTap = { cb(default); done() },
                )
            }
            .pointerInput(range) {
                val pad = 9.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = current
                    var dx = 0f
                    val w = (size.width - 2 * pad).coerceAtLeast(1f)
                    fun apply() {
                        cb((start + dx / w * (range.last - range.first)).roundToInt().coerceIn(range.first, range.last))
                    }
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { ch, _ -> ch.consume(); dx = ch.position.x - down.position.x }
                    if (drag != null) {
                        apply()
                        horizontalDrag(drag.id) { ch -> dx = ch.position.x - down.position.x; ch.consume(); apply() }
                        done()
                    }
                }
            }
    ) {
        val pad = 9.dp.toPx()
        val cy = size.height / 2
        val f = (value - range.first).toFloat() / (range.last - range.first)
        val x = pad + f * (size.width - 2 * pad)
        drawLine(color.copy(alpha = 0.35f), Offset(pad, cy), Offset(size.width - pad, cy), 1.5.dp.toPx())
        drawLine(color, Offset(pad, cy), Offset(x, cy), 1.5.dp.toPx())
        drawCircle(color, 7.dp.toPx(), Offset(x, cy))
    }
}

@Composable
private fun NumberBox(value: Int, range: IntRange, changed: Boolean, onSet: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val current by rememberUpdatedState(value)
    val focus = LocalFocusManager.current
    fun apply() {
        val v = text.toIntOrNull()?.coerceIn(range.first, range.last)
        if (v == null) text = current.toString() else if (v != current) onSet(v) else text = v.toString()
    }
    BasicTextField(
        value = text,
        onValueChange = { text = it.filter(Char::isDigit).take(3) },
        singleLine = true,
        textStyle = TextStyle(color = if (changed) FD.Amber else FD.Text, fontFamily = FD.Mono, fontSize = 12.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { apply(); focus.clearFocus() }),
        cursorBrush = SolidColor(FD.Amber),
        modifier = Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0E0E10))
            .border(1.dp, FD.Line, RoundedCornerShape(4.dp))
            .onFocusChanged { if (!it.isFocused) apply() },
        decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { inner() } },
    )
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
        val grid = Color(0xFF2C2D31)
        for (k in 1..7) {
            val c = if (k % 2 == 0) grid else grid.copy(alpha = 0.5f)
            drawLine(c, Offset(w * k / 8f, 0f), Offset(w * k / 8f, h), 1f)
            drawLine(c, Offset(0f, h * k / 8f), Offset(w, h * k / 8f), 1f)
        }
        // R, G, B and Y histograms overlapping, like Resolve's curve display.
        histogram?.let { hg ->
            listOf(
                hg.y to Color(0xFFC9C3AE).copy(alpha = 0.40f),
                hg.r to Color(0xFFE0393C).copy(alpha = 0.34f),
                hg.g to Color(0xFF3FBF6A).copy(alpha = 0.26f),
                hg.b to Color(0xFF3F6FE0).copy(alpha = 0.34f),
            ).forEach { (bins, col) ->
                val path = Path().apply {
                    moveTo(0f, h)
                    for (i in bins.indices) lineTo(w * (i + 0.5f) / bins.size, h - bins[i] * h * 0.92f)
                    lineTo(w, h); close()
                }
                drawPath(path, col)
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
            val r = if (i == active) 8.dp.toPx() else 6.dp.toPx()
            drawCircle(Color(0xFF18191B), r + 1.5.dp.toPx(), c)
            drawCircle(if (i == active) FD.Amber else Color.White, r, c)
            if (i != active) drawCircle(Color(0xFF18191B), r * 0.45f, c)
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
