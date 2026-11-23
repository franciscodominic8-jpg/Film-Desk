package com.filmdesk.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmdesk.app.model.CHROME_OPTIONS
import com.filmdesk.app.model.DRP_OPTIONS
import com.filmdesk.app.model.DR_OPTIONS
import com.filmdesk.app.model.EditParams
import com.filmdesk.app.model.FILM_SIMS
import com.filmdesk.app.model.GRAIN_OPTIONS
import com.filmdesk.app.model.Q_SETTINGS
import com.filmdesk.app.model.QSetting
import com.filmdesk.app.model.SKIN_OPTIONS
import com.filmdesk.app.model.WB_PRESETS
import com.filmdesk.app.model.WbType
import com.filmdesk.app.model.effectiveDisplay
import com.filmdesk.app.model.effectiveTone
import com.filmdesk.app.model.lockReason
import com.filmdesk.app.model.signed
import com.filmdesk.app.model.simCode
import com.filmdesk.app.ui.theme.FD
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** One round dial of the Q menu. [settings] are the Q_SETTINGS ids it covers. */
private class QDial(val id: String, val label: String, val settings: List<String>)

private val Q_DIALS = listOf(
    QDial("sim", "Film Sim", listOf("sim")),
    QDial("exp", "Exposure", listOf("exp")),
    QDial("grain", "Grain", listOf("grain")),
    QDial("cc", "Color Chr.", listOf("cc")),
    QDial("ccb", "CC FX Blue", listOf("ccb")),
    QDial("wb", "White Bal.", listOf("wb", "kelvin")),
    QDial("wbs", "WB Shift", listOf("wbr", "wbb")),
    QDial("dr", "Dyn. Range", listOf("dr")),
    QDial("drp", "D-Range Pri.", listOf("drp")),
    QDial("hl", "Highlight", listOf("hl")),
    QDial("sh", "Shadow", listOf("sh")),
    QDial("color", "Color", listOf("color")),
    QDial("sharp", "Sharpness", listOf("sharp")),
    QDial("nr", "High ISO NR", listOf("nr")),
    QDial("clarity", "Clarity", listOf("clarity")),
    QDial("mono", "Mono WC/MG", listOf("mwc", "mmg")),
    QDial("skin", "Smooth Skin", listOf("skin")),
)

private fun setting(id: String): QSetting = Q_SETTINGS.first { it.id == id }

private fun dialChanged(d: QDial, p: EditParams): Boolean = d.settings.any { !setting(it).isDefault(p) }

private fun dialLock(d: QDial, p: EditParams): String? = setting(d.settings.first()).lockReason(p)

private val GRAIN_CODES = listOf("OFF", "W·S", "W·L", "S·S", "S·L")

private fun expShort(t: Int): String {
    if (t == 0) return "±0"
    val a = abs(t)
    val frac = when (a % 3) { 1 -> "⅓"; 2 -> "⅔"; else -> "" }
    val whole = if (a / 3 > 0) (a / 3).toString() else ""
    return (if (t > 0) "+" else "−") + whole + frac
}

private fun wbShort(p: EditParams): String {
    val w = WB_PRESETS[p.wb.coerceIn(0, WB_PRESETS.lastIndex)]
    return when (w.type) {
        WbType.AS_SHOT -> "SHOT"
        WbType.AUTO -> "AUTO"
        WbType.CUSTOM -> "${p.kelvin}K"
        WbType.UNDERWATER -> "UW"
        WbType.KELVIN -> when (w.label) {
            "Daylight" -> "DAY"
            "Shade" -> "SHADE"
            "Fluorescent 1" -> "F1"
            "Fluorescent 2" -> "F2"
            "Fluorescent 3" -> "F3"
            "Incandescent" -> "INC"
            else -> w.label.take(4).uppercase()
        }
    }
}

private fun dialValue(d: QDial, p: EditParams): String = when (d.id) {
    "sim" -> simCode(p.sim)
    "exp" -> expShort(p.exposureThirds)
    "grain" -> GRAIN_CODES[p.grain.coerceIn(0, 4)]
    "cc" -> CHROME_OPTIONS[p.colorChrome].uppercase()
    "ccb" -> CHROME_OPTIONS[p.colorChromeBlue].uppercase()
    "wb" -> wbShort(p)
    "wbs" -> if (p.wbShiftR == 0 && p.wbShiftB == 0) "0" else "R${signed(p.wbShiftR.toFloat())} B${signed(p.wbShiftB.toFloat())}"
    "drp" -> DRP_OPTIONS[p.dRangePriority].uppercase()
    "mono" -> if (!FILM_SIMS[p.sim].isMono) "–" else if (p.monoWC == 0 && p.monoMG == 0) "0" else "${signed(p.monoWC.toFloat())} ${signed(p.monoMG.toFloat())}"
    "skin" -> SKIN_OPTIONS[p.smoothSkin].uppercase()
    else -> setting(d.settings.first()).effectiveDisplay(p)
}

/** Fujifilm-style Q menu: a grid of round dials showing every setting; tap one to change it. */
@Composable
fun QMenuPanel(vm: EditorViewModel) {
    val p = vm.params
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Q MENU", color = FD.Muted, fontSize = 10.sp, fontFamily = FD.Mono, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.change(vm.params.resetCamera()) }) {
                Text("Reset all", color = FD.Muted, fontSize = 11.sp)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            val cols = (maxWidth / 68.dp).toInt().coerceIn(4, 8)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Q_DIALS.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { d ->
                            val lock = dialLock(d, p)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                ValueDial(
                                    value = dialValue(d, p),
                                    label = d.label,
                                    changed = dialChanged(d, p),
                                    dimmed = lock != null,
                                    onClick = { vm.openSheet("q:${d.id}") },
                                )
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- option panels

@Composable
fun QSheet(vm: EditorViewModel, id: String) {
    val d = Q_DIALS.firstOrNull { it.id == id } ?: return
    val p = vm.params
    val lock = dialLock(d, p)
    val onReset: (() -> Unit)? = if (lock != null) null else ({ vm.updateParams(resetDial(d, vm.params)) })
    OptionPanel(onCancel = vm::cancelSheet, onReset = onReset, onDone = vm::doneSheet) {
        if (lock != null) {
            BigValue(d.label, dialValue(d, p), false)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = FD.Muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(lock, color = FD.Muted, fontSize = 12.sp)
            }
        } else when (d.id) {
            "sim" -> {
                BigValue("Film simulation", FILM_SIMS[p.sim].name, p.sim != 0)
                SimStrip(vm, onPick = { vm.updateParams(vm.params.copy(sim = it)) })
            }
            "grain" -> ChoiceTiles(d.label, GRAIN_OPTIONS.map { it.replace(" · ", "\n") }, p.grain, { vm.updateParams(vm.params.copy(grain = it)) }) { i -> grainTile(i) }
            "cc" -> ChoiceTiles(d.label, CHROME_OPTIONS, p.colorChrome, { vm.updateParams(vm.params.copy(colorChrome = it)) }) { i ->
                chromeTile(i, Color(0xFFFF7A2E), Color(0xFFD9301F))
            }
            "ccb" -> ChoiceTiles(d.label, CHROME_OPTIONS, p.colorChromeBlue, { vm.updateParams(vm.params.copy(colorChromeBlue = it)) }) { i ->
                chromeTile(i, Color(0xFF5AA8FF), Color(0xFF1E5FD0))
            }
            "dr" -> ChoiceTiles(d.label, DR_OPTIONS, p.dynamicRange, { vm.updateParams(vm.params.copy(dynamicRange = it)) }) { i -> drTile(i) }
            "drp" -> ChoiceTiles(d.label, DRP_OPTIONS, p.dRangePriority, { vm.updateParams(vm.params.copy(dRangePriority = it)) },
                text = { i -> listOf("OFF", "W", "S", "AUTO")[i] })
            "skin" -> ChoiceTiles(d.label, SKIN_OPTIONS, p.smoothSkin, { vm.updateParams(vm.params.copy(smoothSkin = it)) }) { i -> skinTile(i) }
            "wb", "wbs" -> WhiteBalancePanel(vm)
            "mono" -> {
                BigValue("Monochromatic color", "WC ${signed(p.monoWC.toFloat())}  MG ${signed(p.monoMG.toFloat())}", p.monoWC != 0 || p.monoMG != 0)
                ShiftPad(
                    x = p.monoWC, y = p.monoMG,
                    onChange = { x, y -> vm.updateParams(vm.params.copy(monoWC = x, monoMG = y)) },
                    left = "Cool", right = "Warm", top = "M", bottom = "G",
                    leftColor = Color(0xFF6FA8FF), rightColor = Color(0xFFFFA24D), topColor = Color(0xFFE05BD1), bottomColor = Color(0xFF5BD17A),
                )
            }
            else -> {
                val s = setting(d.settings.first()) as QSetting.Stepper
                val v = s.get(p)
                BigValue(d.label, if (d.id == "exp") expShort(p.exposureThirds) else s.format(v), !s.isDefault(p))
                val minL = if (d.id == "exp") "−3" else s.format(s.min)
                val maxL = if (d.id == "exp") "+3" else s.format(s.max)
                TickSlider(
                    v, s.min, s.max, s.step,
                    onChange = { vm.updateParams(s.set(vm.params, s.snap(it))) },
                    minLabel = minL, midLabel = "0", maxLabel = maxL,
                    description = d.label,
                )
                Text(s.hint, color = FD.Muted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            }
        }
    }
}

/** Puts one dial's settings back to default without touching anything else. */
private fun resetDial(d: QDial, p: EditParams): EditParams {
    val z = EditParams()
    return when (d.id) {
        "wb" -> p.copy(wb = z.wb, kelvin = z.kelvin, wbShiftR = z.wbShiftR, wbShiftB = z.wbShiftB)
        "wbs" -> p.copy(wbShiftR = z.wbShiftR, wbShiftB = z.wbShiftB)
        "mono" -> p.copy(monoWC = 0, monoMG = 0)
        else -> {
            var q = p
            d.settings.forEach { id ->
                q = when (val s = setting(id)) {
                    is QSetting.Choice -> s.set(q, s.get(z))
                    is QSetting.Stepper -> s.set(q, s.get(z))
                }
            }
            q
        }
    }
}

@Composable
private fun ChoiceTiles(
    title: String,
    labels: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    text: ((Int) -> String)? = null,
    draw: (DrawScope.(Int) -> Unit)? = null,
) {
    BigValue(title, labels[selected.coerceIn(0, labels.lastIndex)].replace("\n", " "), selected != 0)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
    ) {
        labels.forEachIndexed { i, l ->
            PicTile(l, selected = i == selected, onClick = { onPick(i) }, centerText = text?.invoke(i)) {
                draw?.invoke(this, i)
            }
        }
    }
}

// ---------------------------------------------------------------- tile pictures

private fun DrawScope.grainTile(i: Int) {
    drawRect(Color(0xFF7A7A78))
    if (i == 0) return
    val strong = i >= 3
    val large = i == 2 || i == 4
    val rnd = Random(7 + i)
    val n = if (strong) 260 else 130
    val r = (if (large) 1.6f else 0.9f) * density
    repeat(n) {
        val c = if (rnd.nextBoolean()) Color(0xFF2E2E2C) else Color(0xFFC9C9C4)
        drawCircle(c.copy(alpha = if (strong) 0.9f else 0.6f), r, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height))
    }
}

private fun DrawScope.chromeTile(i: Int, light: Color, deep: Color) {
    val dark = when (i) { 1 -> 0.25f; 2 -> 0.45f; else -> 0f }
    fun shade(c: Color) = Color(c.red * (1 - dark), c.green * (1 - dark), c.blue * (1 - dark))
    drawRect(Brush.verticalGradient(listOf(light, deep)))
    if (dark > 0f) drawRect(Brush.verticalGradient(listOf(light, shade(deep))), topLeft = Offset(0f, size.height * 0.5f), size = Size(size.width, size.height * 0.5f))
}

private fun DrawScope.drTile(i: Int) {
    drawRect(Color(0xFF141416))
    val w = size.width; val h = size.height; val pad = w * 0.18f
    val knee = when (i) { 1 -> 0.7f; 2 -> 0.5f; else -> 1f }
    val path = Path()
    for (k in 0..30) {
        val x = k / 30f
        var y = x
        if (x > knee) { val t = (x - knee) / (1 - knee); y = knee + (1 - knee) * (1 - (1 - t) * (1 - t)) * 0.75f + (1 - knee) * 0.25f * t }
        val px = pad + x * (w - 2 * pad)
        val py = h - pad - y * (h - 2 * pad)
        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(path, Color.White, style = Stroke(1.5f * density))
}

private fun DrawScope.skinTile(i: Int) {
    drawRect(Color(0xFFD7A688))
    val rnd = Random(3)
    val n = when (i) { 0 -> 70; 1 -> 30; else -> 0 }
    repeat(n) {
        drawCircle(Color(0xFF9C6248).copy(alpha = 0.6f), 1.2f * density, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height))
    }
}

// ---------------------------------------------------------------- white balance

@Composable
private fun WhiteBalancePanel(vm: EditorViewModel) {
    val p = vm.params
    val preset = WB_PRESETS[p.wb.coerceIn(0, WB_PRESETS.lastIndex)]
    BigValue("White balance", if (preset.type == WbType.CUSTOM) "${p.kelvin}K" else preset.label, p.wb != 0)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        WB_PRESETS.forEachIndexed { i, w ->
            val short = when (w.type) {
                WbType.AS_SHOT -> "SHOT"; WbType.AUTO -> "AUTO"; WbType.CUSTOM -> "K"; WbType.UNDERWATER -> "UW"
                WbType.KELVIN -> when (w.label) {
                    "Daylight" -> "☀"; "Shade" -> "SHD"; "Fluorescent 1" -> "F1"; "Fluorescent 2" -> "F2"
                    "Fluorescent 3" -> "F3"; "Incandescent" -> "INC"; else -> w.label.take(3)
                }
            }
            val label = when (w.label) {
                "Fluorescent 1" -> "Fluor. 1"; "Fluorescent 2" -> "Fluor. 2"; "Fluorescent 3" -> "Fluor. 3"
                "Incandescent" -> "Incand."; else -> w.label
            }
            ValueDial(short, label, changed = false, selected = p.wb == i, onClick = { vm.updateParams(vm.params.copy(wb = i)) })
        }
    }
    if (preset.type == WbType.CUSTOM) {
        TickSlider(
            p.kelvin.toFloat(), 2500f, 10000f, 100f,
            onChange = { vm.updateParams(vm.params.copy(kelvin = it.roundToInt())) },
            origin = 5500f, minLabel = "2500K", midLabel = "5500K", maxLabel = "10000K",
            description = "Kelvin",
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ShiftPad(
            x = p.wbShiftR, y = p.wbShiftB,
            onChange = { x, y -> vm.updateParams(vm.params.copy(wbShiftR = x, wbShiftB = y)) },
            left = "C", right = "R", top = "B", bottom = "Y",
            leftColor = Color(0xFF4FD1D9), rightColor = Color(0xFFFF5A5A), topColor = Color(0xFF5A8CFF), bottomColor = Color(0xFFFFD24D),
            modifier = Modifier.weight(1f),
            padSize = 140.dp,
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text("WB SHIFT", color = FD.Muted, fontSize = 10.sp, fontFamily = FD.Mono, letterSpacing = 1.5.sp)
            Text("R ${signed(p.wbShiftR.toFloat())}", color = if (p.wbShiftR != 0) FD.Amber else FD.Text, fontFamily = FD.Mono, fontSize = 14.sp)
            Text("B ${signed(p.wbShiftB.toFloat())}", color = if (p.wbShiftB != 0) FD.Amber else FD.Text, fontFamily = FD.Mono, fontSize = 14.sp)
            TextButton(onClick = { vm.updateParams(vm.params.copy(wbShiftR = 0, wbShiftB = 0)) }) {
                Text("Centre", color = FD.Muted, fontSize = 11.sp)
            }
        }
    }
}

/**
 * 2D pad from −9 to +9 on both axes (like the camera's WB shift grid).
 * x grows to the right, y grows upwards. Drag the dot; double-check values with the readout.
 */
@Composable
internal fun ShiftPad(
    x: Int,
    y: Int,
    onChange: (Int, Int) -> Unit,
    left: String, right: String, top: String, bottom: String,
    leftColor: Color, rightColor: Color, topColor: Color, bottomColor: Color,
    modifier: Modifier = Modifier,
    padSize: Dp = 150.dp,
) {
    val cb by rememberUpdatedState(onChange)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(padSize)) {
            Canvas(
                Modifier
                    .size(padSize)
                    .background(Color(0xFF0E0E10))
                    .border(1.dp, FD.Line)
                    .semantics { contentDescription = "$left $right $top $bottom shift" }
                    .pointerInput(Unit) {
                        fun set(o: Offset) {
                            val cell = size.width / 19f
                            val nx = ((o.x / cell).toInt() - 9).coerceIn(-9, 9)
                            val ny = (9 - (o.y / cell).toInt()).coerceIn(-9, 9)
                            cb(nx, ny)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            set(down.position); down.consume()
                            drag(down.id) { ch -> set(ch.position); ch.consume() }
                        }
                    }
            ) {
                val cell = size.width / 19f
                for (i in 0..19) {
                    val c = if (i == 9 || i == 10) FD.Line.copy(alpha = 0.9f) else FD.Line.copy(alpha = 0.45f)
                    drawLine(c, Offset(i * cell, 0f), Offset(i * cell, size.height), 0.5f * density)
                    drawLine(c, Offset(0f, i * cell), Offset(size.width, i * cell), 0.5f * density)
                }
                val mid = size.width / 2
                drawLine(FD.Muted.copy(alpha = 0.6f), Offset(mid, 0f), Offset(mid, size.height), 1f * density)
                drawLine(FD.Muted.copy(alpha = 0.6f), Offset(0f, mid), Offset(size.width, mid), 1f * density)
                val cx = (x + 9 + 0.5f) * cell
                val cy = (9 - y + 0.5f) * cell
                drawCircle(FD.Amber, cell * 0.65f, Offset(cx, cy))
            }
            PadLabel(top, topColor, Modifier.align(Alignment.TopCenter))
            PadLabel(bottom, bottomColor, Modifier.align(Alignment.BottomCenter))
            PadLabel(left, leftColor, Modifier.align(Alignment.CenterStart))
            PadLabel(right, rightColor, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun PadLabel(text: String, color: Color, modifier: Modifier) {
    Text(text, color = color, fontSize = 9.sp, fontFamily = FD.Mono, fontWeight = FontWeight.Bold, modifier = modifier.padding(3.dp))
}
