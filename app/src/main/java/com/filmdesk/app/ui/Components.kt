package com.filmdesk.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmdesk.app.ui.theme.FD

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = FD.Muted,
        fontSize = 11.sp,
        fontFamily = FD.Mono,
        letterSpacing = 1.5.sp,
    )
}

/** Rounded selectable pill used for options throughout the app. */
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) FD.Amber else FD.Surface2)
            .border(1.dp, if (selected) FD.Amber else FD.Line, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) FD.OnAmber else FD.Text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    changed: Boolean,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    steps: Int = 0,
    onReset: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = FD.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            val valueMod = if (onReset != null && changed) {
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = "Reset $label", onClick = onReset)
            } else Modifier
            Text(
                display,
                modifier = valueMod.padding(horizontal = 6.dp, vertical = 4.dp),
                color = if (changed) FD.Amber else FD.Muted,
                fontFamily = FD.Mono,
                fontSize = 13.sp,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = FD.Amber,
                activeTrackColor = FD.Amber,
                inactiveTrackColor = FD.Line,
                activeTickColor = FD.OnAmber,
                inactiveTickColor = FD.Muted,
            ),
        )
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) FD.Text else FD.Muted, fontSize = 14.sp)
            Text(subtitle, color = FD.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = FD.OnAmber,
                checkedTrackColor = FD.Amber,
                uncheckedThumbColor = FD.Muted,
                uncheckedTrackColor = FD.Surface2,
                uncheckedBorderColor = FD.Line,
            ),
        )
    }
}

val PanelShape = RoundedCornerShape(16.dp)

@Composable
fun FdCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(PanelShape)
            .background(FD.Surface)
            .border(1.dp, FD.Line, PanelShape),
        verticalArrangement = Arrangement.Top,
    ) { content() }
}
