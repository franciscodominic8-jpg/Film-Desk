package com.filmdesk.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmdesk.app.model.ASPECTS
import com.filmdesk.app.model.FILM_SIMS
import com.filmdesk.app.model.FilmSim
import com.filmdesk.app.ui.theme.FD
import kotlin.math.roundToInt

@Composable
fun EditorScreen(vm: EditorViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Wide = unfolded foldable, tablet, or phone in landscape.
        val wide = maxWidth >= 600.dp
        val panelWidth = if (maxWidth >= 840.dp) 380.dp else 320.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    EditorTopBar(vm)
                    PreviewArea(vm, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp))
                    EditorTabs(vm)
                }
                Box(
                    Modifier
                        .width(panelWidth)
                        .fillMaxHeight()
                        .background(FD.Panel)
                ) { ToolPanel(vm, Modifier.fillMaxSize()) }
            }
        } else {
            val panelHeight = minOf(maxHeight * 0.42f, 360.dp)
            Column(Modifier.fillMaxSize()) {
                EditorTopBar(vm)
                PreviewArea(vm, Modifier.weight(1f).fillMaxWidth())
                ToolPanel(vm, Modifier.fillMaxWidth().height(panelHeight))
                EditorTabs(vm)
            }
        }
    }
}

@Composable
private fun EditorTopBar(vm: EditorViewModel) {
    var showSave by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { vm.back() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FD.Text)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = vm::undo, enabled = vm.canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (vm.canUndo) FD.Text else FD.Line)
        }
        IconButton(onClick = vm::redo, enabled = vm.canRedo) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (vm.canRedo) FD.Text else FD.Line)
        }
        IconButton(onClick = { showSave = true }) {
            Icon(Icons.Filled.BookmarkAdd, contentDescription = "Save as recipe", tint = FD.Text)
        }
        Button(
            onClick = { vm.navigate(Screen.Export) },
            enabled = vm.hasImage,
            colors = ButtonDefaults.buttonColors(containerColor = FD.Amber, contentColor = FD.OnAmber),
            modifier = Modifier.padding(end = 8.dp),
        ) { Text("Export", fontWeight = FontWeight.SemiBold) }
    }

    if (showSave) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Save as recipe") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    label = { Text("Recipe name") },
                    placeholder = { Text("e.g. Street Chrome") },
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.saveRecipe(name); showSave = false }) { Text("Save", color = FD.Amber) }
            },
            dismissButton = {
                TextButton(onClick = { showSave = false }) { Text("Cancel", color = FD.Muted) }
            },
            containerColor = FD.Surface,
        )
    }
}

@Composable
private fun PreviewArea(vm: EditorViewModel, modifier: Modifier) {
    var showOriginal by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    showOriginal = true
                    tryAwaitRelease()
                    showOriginal = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        val bmp: Bitmap? = if (showOriginal) vm.source else (vm.preview ?: vm.source)
        if (vm.loading || bmp == null) {
            CircularProgressIndicator(color = FD.Amber)
        } else {
            val img = remember(bmp) { bmp.asImageBitmap() }
            Image(img, contentDescription = "Photo preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        val sim = FILM_SIMS[vm.params.sim]
        Badge(
            if (showOriginal) "ORIGINAL" else "${sim.short.uppercase()} · ${(vm.params.simStrength * 100).roundToInt()}%",
            Modifier.align(Alignment.TopStart).padding(10.dp),
        )
        if (vm.rendering && !vm.loading) {
            CircularProgressIndicator(
                color = FD.Amber, strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(16.dp),
            )
        }
        if (!vm.loading && bmp != null) {
            Badge("HOLD TO COMPARE", Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC0B0B0C))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = FD.Text, fontSize = 11.sp, fontFamily = FD.Mono,
    )
}

@Composable
private fun EditorTabs(vm: EditorViewModel) {
    Row(
        Modifier.fillMaxWidth().height(68.dp).background(FD.Panel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorTab.entries.forEach { t ->
            val selected = vm.tab == t
            val icon: ImageVector = when (t) {
                EditorTab.Film -> Icons.Filled.FilterVintage
                EditorTab.QMenu -> Icons.Filled.GridView
                EditorTab.Pro -> Icons.Filled.Tune
                EditorTab.Crop -> Icons.Filled.Crop
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = selected, role = Role.Tab, onClick = { vm.tab = t }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) FD.Amber else FD.Muted)
                Spacer(Modifier.height(4.dp))
                Text(
                    t.label, fontSize = 11.sp,
                    color = if (selected) FD.Amber else FD.Muted,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ToolPanel(vm: EditorViewModel, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        when (vm.tab) {
            EditorTab.Film -> FilmPanel(vm)
            EditorTab.QMenu -> QMenuPanel(vm)
            EditorTab.Pro -> ProPanel(vm)
            EditorTab.Crop -> CropPanel(vm)
        }
    }
}

// ------------------------------------------------------------------ Film tab

@Composable
private fun FilmPanel(vm: EditorViewModel) {
    val p = vm.params
    val sim = FILM_SIMS[p.sim]
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { listState.scrollToItem((p.sim - 1).coerceAtLeast(0)) }
    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionLabel("Film simulation · ${p.sim + 1}/${FILM_SIMS.size}")
            Text(sim.name, color = FD.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(sim.description, color = FD.Muted, fontSize = 13.sp)
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(FILM_SIMS) { i, s ->
                SimChip(s, vm.simThumbs.getOrNull(i), selected = i == p.sim) { vm.change(vm.params.copy(sim = i)) }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            LabeledSlider(
                label = "Strength",
                value = p.simStrength,
                range = 0f..1f,
                display = "${(p.simStrength * 100).roundToInt()}%",
                changed = p.simStrength != 1f,
                onChange = { vm.updateParams(vm.params.copy(simStrength = (it * 100).roundToInt() / 100f)) },
                onDone = vm::commit,
                onReset = { vm.change(vm.params.copy(simStrength = 1f)) },
            )
        }
    }
}

@Composable
private fun SimChip(sim: FilmSim, thumb: Bitmap?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .width(76.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(76.dp).height(58.dp)
                .clip(shape)
                .background(Color(sim.swatch))
                .border(if (selected) 2.dp else 1.dp, if (selected) FD.Amber else FD.Line, shape)
        ) {
            if (thumb != null) {
                val img = remember(thumb) { thumb.asImageBitmap() }
                Image(img, contentDescription = null, modifier = Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            sim.short, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
            color = if (selected) FD.Amber else FD.Muted,
        )
    }
}

// ------------------------------------------------------------------ Crop tab

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CropPanel(vm: EditorViewModel) {
    val p = vm.params
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Aspect ratio")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ASPECTS.forEachIndexed { i, a ->
                Pill(a.label, selected = p.aspect == i, onClick = { vm.change(vm.params.copy(aspect = i)) })
            }
        }
        SectionLabel("Rotate & flip")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.change(vm.params.copy(rotation = (vm.params.rotation + 270) % 360)) }) {
                Icon(Icons.Filled.RotateLeft, contentDescription = null, tint = FD.Text)
                Spacer(Modifier.width(6.dp)); Text("Left", color = FD.Text)
            }
            OutlinedButton(onClick = { vm.change(vm.params.copy(rotation = (vm.params.rotation + 90) % 360)) }) {
                Icon(Icons.Filled.RotateRight, contentDescription = null, tint = FD.Text)
                Spacer(Modifier.width(6.dp)); Text("Right", color = FD.Text)
            }
            OutlinedButton(onClick = { vm.change(vm.params.copy(flipH = !vm.params.flipH)) }) {
                Icon(Icons.Filled.Flip, contentDescription = null, tint = if (p.flipH) FD.Amber else FD.Text)
                Spacer(Modifier.width(6.dp)); Text("Flip", color = FD.Text)
            }
        }
        Text("Crops are centred. Free-form crop handles are planned for the next version.", color = FD.Muted, fontSize = 12.sp)
    }
}
