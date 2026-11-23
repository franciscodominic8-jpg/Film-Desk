package com.filmdesk.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ContentCopy
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

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import com.filmdesk.app.model.simCode
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie

@Composable
fun EditorScreen(vm: EditorViewModel) {
    if (vm.curveMode) {
        CurveScreen(vm)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // Wide = unfolded foldable, tablet, or phone in landscape.
        val wide = maxWidth >= 600.dp
        val panelWidth = if (maxWidth >= 840.dp) 380.dp else 330.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    EditorTopBar(vm)
                    if (vm.tab == EditorTab.Crop) CropView(vm, Modifier.weight(1f).fillMaxWidth())
                    else PreviewArea(vm, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp))
                    EditorTabs(vm)
                }
                Column(
                    Modifier
                        .width(panelWidth)
                        .fillMaxHeight()
                        .background(FD.Bg)
                ) {
                    Spacer(Modifier.height(48.dp))
                    BottomArea(vm, Modifier.fillMaxWidth().weight(1f, fill = false))
                }
            }
        } else {
            val maxPanel = maxHeight * 0.46f
            Column(Modifier.fillMaxSize()) {
                EditorTopBar(vm)
                if (vm.tab == EditorTab.Crop) CropView(vm, Modifier.weight(1f).fillMaxWidth())
                else PreviewArea(vm, Modifier.weight(1f).fillMaxWidth())
                BottomArea(vm, Modifier.fillMaxWidth().heightIn(max = maxPanel))
                EditorTabs(vm)
            }
        }
    }
}

/** Under the photo: the open option panel, or the current tab's tools. */
@Composable
private fun BottomArea(vm: EditorViewModel, modifier: Modifier) {
    Box(modifier) {
        val sheet = vm.sheet
        when {
            sheet == null -> when (vm.tab) {
                EditorTab.Film -> FilmTab(vm)
                EditorTab.QMenu -> QMenuPanel(vm)
                EditorTab.Pro -> ProPanel(vm)
                EditorTab.Crop -> Column(Modifier.verticalScroll(rememberScrollState())) { CropTools(vm) }
            }
            sheet == "film" -> FilmStrengthSheet(vm)
            sheet.startsWith("q:") -> QSheet(vm, sheet.removePrefix("q:"))
            sheet.startsWith("pro:") -> ProSheet(vm, sheet.removePrefix("pro:"))
            else -> FilmTab(vm)
        }
    }
}

@Composable
private fun EditorTopBar(vm: EditorViewModel) {
    var showSave by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val btn = Modifier.size(40.dp)
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { vm.back() }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FD.Text)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = vm::undo, enabled = vm.canUndo, modifier = btn) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (vm.canUndo) FD.Text else FD.Line)
        }
        IconButton(onClick = vm::redo, enabled = vm.canRedo, modifier = btn) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (vm.canRedo) FD.Text else FD.Line)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = btn) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = FD.Text)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = FD.Surface2) {
                DropdownMenuItem(
                    text = { Text("Copy edits", color = FD.Text) },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = FD.Text) },
                    enabled = vm.hasImage,
                    onClick = { menu = false; vm.copyCurrent() },
                )
                val canPaste = vm.clipboard != null && vm.hasImage
                DropdownMenuItem(
                    text = { Text("Paste edits", color = if (canPaste) FD.Text else FD.Muted) },
                    leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = if (canPaste) FD.Text else FD.Muted) },
                    enabled = canPaste,
                    onClick = { menu = false; showPaste = true },
                )
                HorizontalDivider(color = FD.Line)
                DropdownMenuItem(
                    text = { Text("Apply a recipe", color = FD.Text) },
                    leadingIcon = { Icon(Icons.Filled.FilterVintage, contentDescription = null, tint = FD.Text) },
                    onClick = { menu = false; vm.openRecipes() },
                )
                DropdownMenuItem(
                    text = { Text("Save as recipe", color = FD.Text) },
                    leadingIcon = { Icon(Icons.Filled.BookmarkAdd, contentDescription = null, tint = FD.Text) },
                    onClick = { menu = false; showSave = true },
                )
            }
        }
        TextButton(
            onClick = { vm.openExport() },
            enabled = vm.hasImage,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(
                "Export", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1,
                color = if (vm.hasImage) FD.Amber else FD.Muted,
            )
        }
    }

    val clip = vm.clipboard
    if (showPaste && clip != null) {
        PasteSheet(
            title = "Paste edits to this photo",
            from = clip.fromName,
            confirm = "Paste",
            onDismiss = { showPaste = false },
            onPaste = { parts -> showPaste = false; vm.pasteCurrent(parts) },
        )
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
internal fun PreviewArea(vm: EditorViewModel, modifier: Modifier) {
    var showOriginal by remember { mutableStateOf(false) }
    val picking = vm.curvePicker
    val imgW = vm.preview?.width ?: 0
    val imgH = vm.preview?.height ?: 0
    Box(
        modifier
            .background(Color.Black)
            .pointerInput(picking, imgW, imgH) {
                if (picking && imgW > 0 && imgH > 0) {
                    // Curve eyedropper: map the tap from the fitted image to the bitmap.
                    detectTapGestures(onTap = { pos ->
                        val bw = size.width.toFloat(); val bh = size.height.toFloat()
                        val scale = minOf(bw / imgW, bh / imgH)
                        val dw = imgW * scale; val dh = imgH * scale
                        val fx = (pos.x - (bw - dw) / 2f) / dw
                        val fy = (pos.y - (bh - dh) / 2f) / dh
                        if (fx in 0f..1f && fy in 0f..1f) vm.sampleAt(fx, fy)
                    })
                } else {
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val bmp: Bitmap? = if (showOriginal) (vm.original ?: vm.source) else (vm.preview ?: vm.source)
        if (vm.loading || bmp == null) {
            CircularProgressIndicator(color = FD.Amber)
        } else {
            val img = remember(bmp) { bmp.asImageBitmap() }
            Image(img, contentDescription = "Photo preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        val sim = FILM_SIMS[vm.params.sim]
        Text(
            if (showOriginal) "ORIGINAL" else sim.short.uppercase() + if (vm.params.simStrength < 1f) " · ${(vm.params.simStrength * 100).roundToInt()}%" else "",
            modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 12.dp, vertical = 8.dp),
            color = FD.Text.copy(alpha = 0.85f), fontSize = 10.sp, fontFamily = FD.Mono, letterSpacing = 1.sp,
        )
        if (vm.rendering && !vm.loading) {
            CircularProgressIndicator(
                color = FD.Amber, strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(14.dp),
            )
        }
        if (picking && !vm.loading && bmp != null) {
            Badge("TAP TO SAMPLE A COLOUR", Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }
    }
}

@Composable
internal fun Badge(text: String, modifier: Modifier) {
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
        Modifier.fillMaxWidth().height(54.dp).background(Color.Black),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorTab.entries.forEach { t ->
            val selected = vm.tab == t
            val icon: ImageVector = when (t) {
                EditorTab.Film -> Icons.Filled.Movie
                EditorTab.QMenu -> Icons.Filled.GridView
                EditorTab.Pro -> Icons.Filled.Tune
                EditorTab.Crop -> Icons.Filled.Crop
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = selected, role = Role.Tab, onClick = {
                        vm.tab = t
                        if (vm.curvePicker) vm.togglePicker()
                    }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) FD.Text else FD.Muted.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(2.dp))
                Text(
                    t.label, fontSize = 10.sp,
                    color = if (selected) FD.Text else FD.Muted.copy(alpha = 0.6f),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Film tab

@Composable
private fun FilmTab(vm: EditorViewModel) {
    val p = vm.params
    val sim = FILM_SIMS[p.sim]
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FILM SIMULATION", color = FD.Muted, fontSize = 10.sp, fontFamily = FD.Mono, letterSpacing = 1.5.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    sim.name, color = FD.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text("${p.sim + 1}/${FILM_SIMS.size}", color = FD.Muted, fontSize = 10.sp, fontFamily = FD.Mono)
            }
            Text(
                "${sim.description} · strength ${(p.simStrength * 100).roundToInt()}%",
                color = FD.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        SimStrip(vm, onPick = { i ->
            if (i == vm.params.sim) vm.openSheet("film") else vm.change(vm.params.copy(sim = i))
        })
        Text(
            "Tap the selected film again for strength",
            color = FD.Muted.copy(alpha = 0.7f), fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** Row of film simulation thumbnails with their codes and a family colour bar. */
@Composable
internal fun SimStrip(vm: EditorViewModel, onPick: (Int) -> Unit) {
    val p = vm.params
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { listState.scrollToItem((p.sim - 1).coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        itemsIndexed(FILM_SIMS) { i, s ->
            SimTile(i, s, vm.simThumbs.getOrNull(i), selected = i == p.sim) { onPick(i) }
        }
    }
}

@Composable
private fun SimTile(index: Int, sim: FilmSim, thumb: Bitmap?, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(62.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .background(Color(sim.swatch))
                .border(if (selected) 2.dp else 0.dp, if (selected) Color.White else Color.Transparent)
        ) {
            if (thumb != null) {
                val img = remember(thumb) { thumb.asImageBitmap() }
                Image(img, contentDescription = sim.name, modifier = Modifier.fillMaxSize().padding(if (selected) 2.dp else 0.dp), contentScale = ContentScale.Crop)
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(sim.swatch)))
        Spacer(Modifier.height(4.dp))
        Text(
            simCode(index), fontSize = 10.sp, maxLines = 1, fontFamily = FD.Mono, textAlign = TextAlign.Center,
            color = if (selected) Color.White else FD.Muted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun FilmStrengthSheet(vm: EditorViewModel) {
    val p = vm.params
    val pct = (p.simStrength * 100).roundToInt()
    OptionPanel(
        onCancel = vm::cancelSheet,
        onReset = { vm.updateParams(vm.params.copy(simStrength = 1f)) },
        onDone = vm::doneSheet,
        hint = "Hold photo to compare",
    ) {
        BigValue("${FILM_SIMS[p.sim].name} · strength", "$pct%", pct != 100)
        TickSlider(
            pct.toFloat(), 0f, 100f, 1f,
            onChange = { vm.updateParams(vm.params.copy(simStrength = it / 100f)) },
            origin = 100f, minLabel = "0%", midLabel = null, maxLabel = "100%",
            description = "Film strength",
        )
    }
}
