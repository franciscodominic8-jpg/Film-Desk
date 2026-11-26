package com.filmdesk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.Vignette
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmdesk.app.model.EditParams
import com.filmdesk.app.model.GLOW_SLIDERS
import com.filmdesk.app.model.GLOW_STYLES
import com.filmdesk.app.model.HSL_NAMES
import com.filmdesk.app.model.HSL_SWATCHES
import com.filmdesk.app.model.PRO_GROUPS
import com.filmdesk.app.model.ProSlider
import com.filmdesk.app.model.hasCurves
import com.filmdesk.app.model.hasSoftClip
import com.filmdesk.app.model.hslSliders
import com.filmdesk.app.ui.theme.FD

/** One round tool in the Pro tab. [slider] is null for HSL, Curve and Glow, which have their own panels. */
private class ProTool(val id: String, val label: String, val icon: ImageVector, val minLabel: String? = null, val maxLabel: String? = null)

private class ProSection(val title: String, val tools: List<ProTool>)

private val PRO_SECTIONS = listOf(
    ProSection("Light", listOf(
        ProTool("Contrast", "Contrast", Icons.Filled.Contrast),
        ProTool("Highlights", "Highlights", Icons.Filled.WbSunny),
        ProTool("Shadows", "Shadows", Icons.Filled.Brightness4),
        ProTool("Whites", "Whites", Icons.Filled.BrightnessHigh),
        ProTool("Blacks", "Blacks", Icons.Filled.BrightnessLow),
        ProTool("Fade", "Fade", Icons.Filled.Gradient),
    )),
    ProSection("Colour", listOf(
        ProTool("Temperature", "Temp", Icons.Filled.Thermostat, "cool", "warm"),
        ProTool("Tint", "Tint", Icons.Filled.Palette, "green", "magenta"),
        ProTool("Vibrance", "Vibrance", Icons.Filled.InvertColors),
        ProTool("Saturation", "Saturation", Icons.Filled.ColorLens),
        ProTool("HSL", "HSL", Icons.Filled.Tonality),
    )),
    ProSection("Advanced & effects", listOf(
        ProTool("Curve", "Curve", Icons.Filled.ShowChart),
        ProTool("Vignette", "Vignette", Icons.Filled.Vignette, "dark", "light"),
        ProTool("Glow", "Glow", Icons.Filled.AutoAwesome),
    )),
)

private fun slider(label: String): ProSlider? =
    PRO_GROUPS.asSequence().flatMap { it.sliders.asSequence() }.firstOrNull { it.label == label }

private fun fmt(v: Int, signedRange: Boolean): String = if (signedRange) signedInt(v) else v.toString()

private fun hslChanged(p: EditParams) = p.hslHue.any { it != 0 } || p.hslSat.any { it != 0 } || p.hslLum.any { it != 0 }

private fun toolState(t: ProTool, p: EditParams): Pair<String, Boolean> = when (t.id) {
    "HSL" -> if (hslChanged(p)) "edited" to true else "–" to false
    "Curve" -> if (p.hasCurves || p.hasSoftClip) "edited" to true else "–" to false
    else -> {
        val s = slider(t.id)
        if (s == null) "–" to false else {
            val v = s.get(p)
            fmt(v, s.min < 0) to (v != s.def)
        }
    }
}

/** Lightroom-style adjustments on top of the film look, as round tools grouped like VSCO. */
@Composable
fun ProPanel(vm: EditorViewModel) {
    val p = vm.params
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        PRO_SECTIONS.forEachIndexed { si, sec ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sec.title.uppercase(), color = FD.Muted, fontSize = 10.sp, fontFamily = FD.Mono, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                if (si == 0) {
                    TextButton(onClick = { vm.change(vm.params.resetPro()) }) { Text("Reset all", color = FD.Muted, fontSize = 11.sp) }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                val cols = (maxWidth / 68.dp).toInt().coerceIn(4, 8)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sec.tools.chunked(cols).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { t ->
                                val (value, changed) = toolState(t, p)
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    ToolCircle(t.icon, t.label, value, changed, onClick = {
                                        if (t.id == "Curve") vm.openCurve() else vm.openSheet("pro:${t.id}")
                                    })
                                }
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- option panels

@Composable
fun ProSheet(vm: EditorViewModel, id: String) {
    val tool = PRO_SECTIONS.flatMap { it.tools }.firstOrNull { it.id == id } ?: return
    when (id) {
        "HSL" -> HslSheet(vm)
        "Glow" -> GlowSheet(vm)
        else -> {
            val s = slider(id) ?: return
            val v = s.get(vm.params)
            OptionPanel(
                onCancel = vm::cancelSheet,
                onReset = { vm.updateParams(s.set(vm.params, s.def)) },
                onDone = vm::doneSheet,
                hint = "Hold photo to compare",
            ) {
                BigValue(s.label, fmt(v, s.min < 0), v != s.def, onNudge = { k ->
                    vm.updateParams(s.set(vm.params, (s.get(vm.params) + k).coerceIn(s.min, s.max)))
                })
                TickSlider(
                    v.toFloat(), s.min.toFloat(), s.max.toFloat(), 1f,
                    onChange = { vm.updateParams(s.set(vm.params, it.toInt())) },
                    origin = s.def.toFloat(),
                    minLabel = tool.minLabel ?: fmt(s.min, s.min < 0),
                    midLabel = if (s.min < 0) "0" else null,
                    maxLabel = tool.maxLabel ?: fmt(s.max, s.min < 0),
                    description = s.label, valueText = fmt(v, s.min < 0),
                )
            }
        }
    }
}

/** Label + value row above a slider (value orange when changed). */
@Composable
private fun SliderRow(label: String, v: Int, def: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 4.dp)) {
            Text(label, color = FD.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(fmt(v, min < 0), color = if (v != def) FD.Amber else FD.Muted, fontSize = 12.sp, fontFamily = FD.Mono)
        }
        TickSlider(
            v.toFloat(), min.toFloat(), max.toFloat(), 1f, onChange = { onChange(it.toInt()) },
            origin = def.toFloat(), description = label, valueText = fmt(v, min < 0),
        )
    }
}

@Composable
private fun HslSheet(vm: EditorViewModel) {
    var band by rememberSaveable { mutableIntStateOf(0) }
    val p = vm.params
    OptionPanel(
        onCancel = vm::cancelSheet,
        onReset = { vm.updateParams(vm.params.copy(hslHue = List(8) { 0 }, hslSat = List(8) { 0 }, hslLum = List(8) { 0 })) },
        onDone = vm::doneSheet,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HSL_SWATCHES.forEachIndexed { i, c ->
                val sel = band == i
                val edited = p.hslHue[i] != 0 || p.hslSat[i] != 0 || p.hslLum[i] != 0
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (sel) Color.White else if (edited) FD.Amber else Color.Transparent, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { band = i })
                        .semantics { contentDescription = HSL_NAMES[i] }
                )
            }
        }
        Text(HSL_NAMES[band].uppercase(), color = FD.Muted, fontSize = 10.sp, fontFamily = FD.Mono, letterSpacing = 1.5.sp)
        hslSliders(band).forEach { s ->
            SliderRow(s.label, s.get(p), 0, s.min, s.max) { vm.updateParams(s.set(vm.params, it)) }
        }
    }
}

@Composable
private fun GlowSheet(vm: EditorViewModel) {
    val p = vm.params
    OptionPanel(
        onCancel = vm::cancelSheet,
        onReset = {
            vm.updateParams(vm.params.copy(glow = 0, glowStyle = 0, glowThreshold = 70, glowSize = 40, glowWarmth = 30))
        },
        onDone = vm::doneSheet,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            GLOW_STYLES.forEachIndexed { i, label ->
                val sel = p.glowStyle == i
                Text(
                    label,
                    color = if (sel) FD.Bg else FD.Text, fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) FD.Text else Color.Transparent)
                        .border(1.dp, if (sel) FD.Text else FD.Line, RoundedCornerShape(50))
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { vm.updateParams(vm.params.copy(glowStyle = i)) })
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        Text(
            when (p.glowStyle) {
                1 -> "Red-orange glow around highlights, like CineStill film."
                2 -> "Soft haze over the whole photo, like a mist filter."
                else -> "Soft bloom around bright areas."
            },
            color = FD.Muted, fontSize = 11.sp,
        )
        SliderRow("Amount", p.glow, 0, 0, 100) { vm.updateParams(vm.params.copy(glow = it)) }
        GLOW_SLIDERS.forEach { s ->
            SliderRow(s.label, s.get(p), s.def, s.min, s.max) { vm.updateParams(s.set(vm.params, it)) }
        }
    }
}
