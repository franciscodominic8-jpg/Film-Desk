package com.filmdesk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmdesk.app.model.EditParams
import com.filmdesk.app.model.QSetting
import com.filmdesk.app.model.Q_SETTINGS
import com.filmdesk.app.ui.theme.FD

/** Fujifilm-style Q menu: a grid of camera settings and an editor for the selected one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QMenuPanel(vm: EditorViewModel) {
    var selectedId by rememberSaveable { mutableStateOf("grain") }
    val p = vm.params
    val selected = Q_SETTINGS.firstOrNull { it.id == selectedId } ?: Q_SETTINGS.first()

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = if (maxWidth >= 520.dp) 5 else 4
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Q_SETTINGS.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { s ->
                            QTile(s, p, s.id == selected.id, Modifier.weight(1f)) { selectedId = s.id }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        FdCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(selected.label, color = FD.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(selected.hint, color = FD.Muted, fontSize = 11.sp)
                }
                when (selected) {
                    is QSetting.Choice -> FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        selected.options.forEachIndexed { i, label ->
                            Pill(label, selected = selected.get(p) == i, onClick = { vm.change(selected.set(vm.params, i)) })
                        }
                    }
                    is QSetting.Stepper -> StepperEditor(selected, vm)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { vm.change(vm.params.resetCamera()) }) {
                Text("Reset camera settings", color = FD.Muted)
            }
        }
    }
}

@Composable
private fun QTile(s: QSetting, p: EditParams, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val changed = !s.isDefault(p)
    Column(
        modifier
            .height(60.dp)
            .clip(shape)
            .background(if (selected) FD.AmberDim else FD.Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) FD.Amber else FD.Line, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            s.label.uppercase(), color = FD.Muted, fontSize = 9.sp, letterSpacing = 0.3.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            s.display(p), fontFamily = FD.Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = when { selected -> FD.Amber; changed -> FD.AmberLight; else -> FD.Text },
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StepperEditor(s: QSetting.Stepper, vm: EditorViewModel) {
    val value = s.get(vm.params)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FilledTonalIconButton(
                onClick = { vm.change(s.set(vm.params, s.snap(s.get(vm.params) - s.step))) },
                enabled = value > s.min,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = FD.Surface2, contentColor = FD.Text),
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease ${s.label}") }
            Text(
                s.format(value),
                color = FD.Amber, fontFamily = FD.Mono, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            FilledTonalIconButton(
                onClick = { vm.change(s.set(vm.params, s.snap(s.get(vm.params) + s.step))) },
                enabled = value < s.max,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = FD.Surface2, contentColor = FD.Text),
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = "Increase ${s.label}") }
        }
        LabeledSlider(
            label = "${s.format(s.min)}  …  ${s.format(s.max)}",
            value = value,
            range = s.min..s.max,
            display = "",
            changed = false,
            steps = (s.steps - 1).coerceAtLeast(0),
            onChange = { vm.updateParams(s.set(vm.params, s.snap(it))) },
            onDone = vm::commit,
        )
    }
}

