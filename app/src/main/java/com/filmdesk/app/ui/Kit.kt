package com.filmdesk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
 * fill from there to the value and a white knob. Drag anywhere on it; values snap to [step].
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
) {
    val cb by rememberUpdatedState(onChange)
    val stepsTotal = ((max - min) / step).roundToInt().coerceAtLeast(1)
    val ticks = if (stepsTotal <= 24) stepsTotal else 20
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .semantics { contentDescription = description }
                .pointerInput(min, max, step) {
                    val pad = 14.dp.toPx()
                    fun set(x: Float) {
                        val f = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                        val k = (f * stepsTotal).roundToInt()
                        cb((min + k * step).coerceIn(min, max))
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        set(down.position.x); down.consume()
                        drag(down.id) { ch -> set(ch.position.x); ch.consume() }
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

/** Title + big value shown above a panel's slider ("HIGHLIGHT  −1"). */
@Composable
fun BigValue(title: String, value: String, changed: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title.uppercase(), color = FD.Muted, fontSize = 11.sp, fontFamily = FD.Mono, letterSpacing = 2.sp)
        Text(
            value, color = if (changed) FD.Amber else FD.Text, fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = FD.Mono,
        )
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (onReset != null) {
                    TextButton(onClick = onReset) {
                        Text(if (hint != null) "Reset · $hint" else "Reset", color = FD.Muted, fontSize = 12.sp, maxLines = 1)
                    }
                } else if (hint != null) {
                    Text(hint, color = FD.Muted, fontSize = 12.sp, maxLines = 1)
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
            Text(
                value, maxLines = 1, overflow = TextOverflow.Clip,
                color = when { dimmed -> FD.Muted.copy(alpha = 0.6f); changed -> FD.Amber; else -> FD.Text },
                fontSize = if (value.length > 5) 9.sp else 11.sp, fontFamily = FD.Mono, fontWeight = FontWeight.Bold,
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
