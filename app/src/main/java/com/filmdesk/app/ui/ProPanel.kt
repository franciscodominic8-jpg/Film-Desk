package com.filmdesk.app.ui

import androidx.compose.ui.unit.sp
import com.filmdesk.app.model.GLOW_SLIDERS
import com.filmdesk.app.model.GLOW_STYLES
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.filmdesk.app.model.HSL_NAMES
import com.filmdesk.app.model.HSL_SWATCHES
import com.filmdesk.app.model.PRO_GROUPS
import com.filmdesk.app.model.ProSlider
import com.filmdesk.app.model.hslSliders
import com.filmdesk.app.ui.theme.FD

/** Lightroom-style adjustments on top of the film look. */
@Composable
fun ProPanel(vm: EditorViewModel) {
    var group by rememberSaveable { mutableIntStateOf(0) }
    var band by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRO_GROUPS.forEachIndexed { i, g ->
                // Curve opens its own full-screen editor instead of a group of sliders.
                if (g.label == "Curve") Pill(g.label, selected = false, onClick = { vm.openCurve() })
                else Pill(g.label, selected = group == i, onClick = { group = i })
            }
        }

        val g = PRO_GROUPS[group]
        val sliders: List<ProSlider> = if (g.label == "HSL") hslSliders(band) else g.sliders

        if (g.label == "Curve") Unit
        else Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (g.label == "HSL") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    HSL_SWATCHES.forEachIndexed { i, c ->
                        val sel = band == i
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(3.dp, if (sel) FD.Text else FD.Bg, CircleShape)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .selectable(selected = sel, role = Role.RadioButton, onClick = { band = i })
                                .semantics { contentDescription = HSL_NAMES[i] }
                        )
                    }
                }
                SectionLabel(HSL_NAMES[band], Modifier.padding(top = 4.dp))
            }
            sliders.forEach { s -> ProSliderRow(vm, s) }
            if (g.label == "Effects" && vm.params.glow > 0) {
                // Glow style and its finer controls.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    GLOW_STYLES.forEachIndexed { i, label ->
                        Pill(label, selected = vm.params.glowStyle == i, onClick = { vm.change(vm.params.copy(glowStyle = i)) })
                    }
                }
                Text(
                    when (vm.params.glowStyle) {
                        1 -> "Red-orange glow around highlights, like CineStill film."
                        2 -> "Soft haze over the whole photo, like a mist filter."
                        else -> "Soft bloom around bright areas."
                    },
                    color = FD.Muted, fontSize = 12.sp,
                )
                GLOW_SLIDERS.forEach { s -> ProSliderRow(vm, s) }
            }
            TextButton(onClick = { vm.change(vm.params.resetPro()) }, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                Text("Reset pro adjustments", color = FD.Muted)
            }
        }
    }
}

@Composable
private fun ProSliderRow(vm: EditorViewModel, s: ProSlider) {
    val v = s.get(vm.params)
    LabeledSlider(
        label = s.label,
        value = v.toFloat(),
        range = s.min.toFloat()..s.max.toFloat(),
        display = if (v > 0 && s.min < 0) "+$v" else v.toString().replace("-", "−"),
        changed = v != s.def,
        onChange = { vm.updateParams(s.set(vm.params, Math.round(it))) },
        onDone = vm::commit,
        onReset = { vm.change(s.set(vm.params, s.def)) },
    )
}
