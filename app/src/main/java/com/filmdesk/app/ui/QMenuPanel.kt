package com.filmdesk.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmdesk.app.model.EditParams
import com.filmdesk.app.model.QSetting
import com.filmdesk.app.model.Q_SETTINGS
import com.filmdesk.app.model.effectiveDisplay
import com.filmdesk.app.model.lockReason
import com.filmdesk.app.ui.theme.FD
import kotlinx.coroutines.delay

/**
 * Fujifilm-style Q menu. Tapping a tile opens its editor directly under that row
 * (tap again or "Done" to close), so nothing is ever at the bottom of a long list.
 */
@Composable
fun QMenuPanel(vm: EditorViewModel) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val p = vm.params

    BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
        val cols = if (maxWidth >= 520.dp) 5 else 4
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Q_SETTINGS.chunked(cols).forEach { row ->
                val openIndex = row.indexOfFirst { it.id == selectedId }
                Column(Modifier.animateContentSize()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { s ->
                            QTile(s, p, s.id == selectedId, Modifier.weight(1f)) {
                                selectedId = if (selectedId == s.id) null else s.id
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    if (openIndex >= 0) {
                        InlineEditor(
                            setting = row[openIndex],
                            column = openIndex,
                            columns = cols,
                            vm = vm,
                            onClose = { selectedId = null },
                        )
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
}

@Composable
private fun QTile(s: QSetting, p: EditParams, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    // The open tile loses its bottom corners so it joins the editor below it.
    val shape = if (selected) RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp) else RoundedCornerShape(10.dp)
    val changed = !s.isDefault(p)
    val locked = s.lockReason(p) != null
    Column(
        modifier
            .height(60.dp)
            .clip(shape)
            .background(if (selected) FD.AmberDim else FD.Surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) FD.Amber else FD.Line, shape)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.label.uppercase(), color = FD.Muted, fontSize = 9.sp, letterSpacing = 0.3.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (locked) {
                Icon(
                    Icons.Filled.Lock, contentDescription = "Not active",
                    tint = FD.Muted, modifier = Modifier.padding(start = 2.dp).size(10.dp),
                )
            }
        }
        Text(
            s.effectiveDisplay(p), fontFamily = FD.Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = when {
                locked -> FD.Muted
                selected -> FD.Amber
                changed -> FD.AmberLight
                else -> FD.Text
            },
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun InlineEditor(setting: QSetting, column: Int, columns: Int, vm: EditorViewModel, onClose: () -> Unit) {
    // Square the corner that sits under the open tile, round the rest.
    val r = 12.dp
    val shape = RoundedCornerShape(
        topStart = if (column == 0) 0.dp else r,
        topEnd = if (column == columns - 1) 0.dp else r,
        bottomStart = r,
        bottomEnd = r,
    )
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(setting.id) {
        delay(60) // let the panel expand first
        requester.bringIntoView()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .clip(shape)
            .background(FD.AmberDim)
            .border(1.5.dp, FD.Amber, shape)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(setting.label, color = FD.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(setting.hint, color = FD.Muted, fontSize = 11.sp, textAlign = TextAlign.End)
        }
        val reason = setting.lockReason(vm.params)
        if (reason != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = FD.Muted, modifier = Modifier.size(16.dp))
                Text(
                    "Now: ${setting.effectiveDisplay(vm.params)}",
                    color = FD.Text, fontFamily = FD.Mono, fontSize = 14.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(reason, color = FD.Muted, fontSize = 13.sp)
        } else when (setting) {
            is QSetting.Choice -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                setting.options.forEachIndexed { i, label ->
                    Pill(label, selected = setting.get(vm.params) == i, onClick = { vm.change(setting.set(vm.params, i)) })
                }
            }
            is QSetting.Stepper -> StepperEditor(setting, vm)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (reason == null) {
                TextButton(onClick = { vm.change(resetOne(setting, vm.params)) }) {
                    Text("Reset", color = FD.Muted)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = FD.Amber, contentColor = FD.OnAmber),
            ) { Text("Done", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Puts one setting back to its default without touching anything else. */
private fun resetOne(s: QSetting, p: EditParams): EditParams {
    val d = EditParams()
    return when {
        s.id == "kelvin" -> p.copy(kelvin = d.kelvin) // don't switch White Balance to Kelvin
        s is QSetting.Choice -> s.set(p, s.get(d))
        s is QSetting.Stepper -> s.set(p, s.get(d))
        else -> p
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
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
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
