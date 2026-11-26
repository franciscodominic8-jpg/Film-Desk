package com.filmdesk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmdesk.app.ui.theme.FD
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * VSCO-style slider: thin track with tick marks, a mark at the "no change" point, an amber
 * fill from there to the value and a white knob.
 *  - Drag sideways: the knob moves exactly with your finger, wherever you start the drag.
 *    An up/down swipe scrolls the panel instead, so scrolling never changes a value.
 *  - Tap the track to jump there; ‹ › next to the value nudge one step.
 *  - Double-tap goes back to [resetTo] (the default).
 */
@Composable
fun TickSlider(
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    origin: Float = 0f.coerceIn(min, max),
    minLabel: String? = null,
    midLabel: String? = null,
    maxLabel: String? = null,
    color: Color = FD.Amber,
    description: String = "",
    valueText: String? = null,
    resetTo: Float = origin,
) {
    val cb by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(value)
    val reset by rememberUpdatedState(resetTo)
    val stepsTotal = ((max - min) / step).roundToInt().coerceAtLeast(1)
    val ticks = if (stepsTotal <= 24) stepsTotal else 20
    fun snap(v: Float): Float = (min + ((v - min) / step).roundToInt() * step).coerceIn(min, max)
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .semantics {
                    contentDescription = description
                    stateDescription = valueText ?: value.toString()
                    progressBarRangeInfo = ProgressBarRangeInfo(value, min..max, (stepsTotal - 1).coerceAtLeast(0))
                    setProgress { target -> cb(snap(target)); true }
                }
                .pointerInput(min, max, step) {
                    val pad = 14.dp.toPx()
                    fun at(x: Float): Float = min + ((x - pad) / (size.width - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f) * (max - min)
                    detectTapGestures(
                        onTap = { pos -> cb(snap(at(pos.x))) },
                        onDoubleTap = { cb(snap(reset)) },
                    )
                }
                .pointerInput(min, max, step) {
                    val pad = 14.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = current
                        var dx = 0f
                        val w = (size.width - 2 * pad).coerceAtLeast(1f)
                        // 1:1 with the finger: moving the full track width covers the full range.
                        fun apply() { cb(snap(start + dx / w * (max - min))) }
                        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume(); dx = change.position.x - down.position.x
                        }
                        if (drag != null) {
                            apply()
                            horizontalDrag(drag.id) { ch ->
                                dx = ch.position.x - down.position.x
                                ch.consume()
                                apply()
                            }
                        }
                    }
                },
        ) {
            val pad = 14.dp.toPx()
            val w = size.width - 2 * pad
            val cy = size.height / 2
            fun xOf(v: Float) = pad + (v - min) / (max - min) * w
            drawLine(FD.Line, Offset(pad, cy), Offset(pad + w, cy), strokeWidth = 1.5.dp.toPx())
            for (i in 0..ticks) {
                val x = pad + w * i / ticks
                val h = if (i == 0 || i == ticks) 6.dp.toPx() else 4.dp.toPx()
                drawLine(FD.Muted.copy(alpha = 0.6f), Offset(x, cy - h), Offset(x, cy + h), strokeWidth = 1.dp.toPx())
            }
            val ox = xOf(origin)
            drawLine(FD.Text.copy(alpha = 0.8f), Offset(ox, cy - 9.dp.toPx()), Offset(ox, cy + 9.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            val vx = xOf(value.coerceIn(min, max))
            if (abs(vx - ox) > 0.5f) drawLine(color, Offset(ox, cy), Offset(vx, cy), strokeWidth = 2.5.dp.toPx())
            drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(vx, cy))
        }
        if (minLabel != null || maxLabel != null || midLabel != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                SmallMono(minLabel ?: "", Modifier.weight(1f), TextAlign.Start)
                SmallMono(midLabel ?: "", Modifier.weight(1f), TextAlign.Center)
                SmallMono(maxLabel ?: "", Modifier.weight(1f), TextAlign.End)
            }
        }
    }
}

@Composable
private fun SmallMono(text: String, modifier: Modifier, align: TextAlign) {
    Text(text, modifier = modifier, color = FD.Muted, fontSize = 10.sp, fontFamily = FD.Mono, textAlign = align, maxLines = 1)
}

/**
 * Title + big value shown above a panel's slider ("HIGHLIGHT  −1").
 * With [onNudge], ‹ and › beside the value move it one step down / up.
 */
@Composable
fun BigValue(title: String, value: String, changed: Boolean, modifier: Modifier = Modifier, onNudge: ((Int) -> Unit)? = null) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (title.isNotBlank()) {
            Text(title.uppercase(), color = FD.Muted, fontSize = 11.sp, fontFamily = FD.Mono, letterSpacing = 2.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onNudge != null) {
                IconButton(onClick = { onNudge(-1) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "One step less", tint = FD.Muted)
                }
            }
            Text(
                value, color = if (changed) FD.Amber else FD.Text, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, fontFamily = FD.Mono,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            if (onNudge != null) {
                IconButton(onClick = { onNudge(1) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "One step more", tint = FD.Muted)
                }
            }
        }
    }
}

/** Bottom option panel: content, then ✕ · Reset · ✓. */
@Composable
fun OptionPanel(
    onCancel: () -> Unit,
    onReset: (() -> Unit)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().background(FD.Bg)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
        HorizontalDivider(color = FD.Line, thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = FD.Text) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (onReset != null) {
                    TextButton(onClick = onReset) { Text("Reset", color = FD.Text, fontSize = 13.sp, maxLines = 1) }
                }
                if (hint != null) {
                    // Only a hint, not part of the Reset button.
                    Text(hint, color = FD.Muted.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onDone) { Icon(Icons.Filled.Check, contentDescription = "Done", tint = FD.Amber) }
        }
    }
}

/** Round Q-menu dial showing the current value; orange when changed. */
@Composable
fun ValueDial(
    value: String,
    label: String,
    changed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    selected: Boolean = false,
) {
    Column(
        modifier
            .width(64.dp)
            .selectable(selected = selected, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(FD.Bg)
                .border(
                    if (selected) 2.dp else 1.dp,
                    when { selected -> FD.Text; changed && !dimmed -> FD.Amber.copy(alpha = 0.7f); else -> FD.Line },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Long values ("R+2 B−4", "10000K") get two lines or a smaller size so they fit the circle.
            val lines = value.split(' ').filter { it.isNotEmpty() }
            val twoLines = value.length > 6 && lines.size == 2
            val longest = if (twoLines) lines.maxOf { it.length } else value.length
            Text(
                if (twoLines) lines.joinToString("\n") else value,
                maxLines = 2, overflow = TextOverflow.Clip, textAlign = TextAlign.Center,
                lineHeight = 10.sp,
                color = when { dimmed -> FD.Muted.copy(alpha = 0.6f); changed -> FD.Amber; else -> FD.Text },
                fontSize = when { longest >= 7 -> 7.5.sp; longest >= 6 -> 8.5.sp; longest >= 5 -> 9.5.sp; else -> 11.sp },
                fontFamily = FD.Mono, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) FD.Text else FD.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round Pro tool: icon, orange dot when changed, label and value under it. */
@Composable
fun ToolCircle(icon: ImageVector, label: String, value: String, changed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(64.dp).selectable(selected = false, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(50.dp)) {
            Box(
                Modifier.size(46.dp).align(Alignment.Center).clip(CircleShape).background(FD.Bg).border(1.dp, FD.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = FD.Text, modifier = Modifier.size(22.dp)) }
            if (changed) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp).size(7.dp).clip(CircleShape).background(FD.Amber))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = FD.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = if (changed) FD.Amber else FD.Muted, fontSize = 10.sp, fontFamily = FD.Mono, maxLines = 1)
    }
}

/** Picture choice: a small drawn preview with a label; white border when picked. */
@Composable
fun PicTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tileSize: Dp = 54.dp,
    centerText: String? = null,
    draw: DrawScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.width(tileSize + 14.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(tileSize)
                .clip(shape)
                .background(FD.Surface)
                .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else FD.Line, shape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) { draw() }
            if (centerText != null) {
                Text(centerText, color = if (selected) FD.Text else FD.Muted, fontSize = 12.sp, fontFamily = FD.Mono, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label, color = if (selected) FD.Text else FD.Muted, fontSize = 10.sp, textAlign = TextAlign.Center,
            maxLines = 2, lineHeight = 12.sp,
        )
    }
}

/** "+2", "−1", "0" */
fun signedInt(v: Int): String = when {
    v > 0 -> "+$v"
    v < 0 -> "−${-v}"
    else -> "0"
}
