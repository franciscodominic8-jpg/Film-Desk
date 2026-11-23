package com.filmdesk.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmdesk.app.data.StudioItem
import com.filmdesk.app.model.DEFAULT_PASTE_PARTS
import com.filmdesk.app.model.FILM_SIMS
import com.filmdesk.app.model.PastePart
import com.filmdesk.app.model.copySummary
import com.filmdesk.app.ui.theme.FD

private enum class StudioFilter(val label: String) { All("All"), Edited("Edited"), NotEdited("Not edited") }

/** The start screen: imported photos, each shown with its own edits. */
@Composable
fun StudioScreen(vm: EditorViewModel) {
    var filter by rememberSaveable { mutableStateOf(StudioFilter.All) }
    var showPaste by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Android 7–9 needs the storage permission to save into Pictures/.
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.exportSelection() else vm.message = "Storage permission is needed to save on this Android version"
    }
    fun exportSelected() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) writeLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else vm.exportSelection()
    }
    val all = vm.studio
    val shown = when (filter) {
        StudioFilter.All -> all
        StudioFilter.Edited -> all.filter { it.edited }
        StudioFilter.NotEdited -> all.filterNot { it.edited }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = when {
            maxWidth >= 840.dp -> 6
            maxWidth >= 600.dp -> 5
            else -> 3
        }
        Column(Modifier.fillMaxSize()) {
            if (vm.selecting) SelectionTopBar(vm, shown) else StudioHeader(vm)

            vm.batchProgress?.let { p ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(p, color = FD.Amber, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = FD.Amber, trackColor = FD.Line)
                }
            }

            val clip = vm.clipboard
            if (vm.selecting && clip != null) ClipboardBanner(vm, clip)

            if (all.isEmpty()) {
                EmptyStudio(vm)
                return@Column
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val edited = all.count { it.edited }
                StudioFilter.entries.forEach { f ->
                    val n = when (f) {
                        StudioFilter.All -> all.size
                        StudioFilter.Edited -> edited
                        StudioFilter.NotEdited -> all.size - edited
                    }
                    Pill("${f.label} · $n", selected = filter == f, onClick = {
                        // Photos hidden by the filter must not stay selected.
                        if (filter != f) vm.clearSelection()
                        filter = f
                    })
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (shown.isEmpty()) {
                    Text(
                        if (filter == StudioFilter.Edited) "No edited photos yet." else "Every photo here has edits.",
                        color = FD.Muted, modifier = Modifier.padding(24.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 3.dp, end = 3.dp, bottom = 96.dp),
                ) {
                    items(shown, key = { it.id }) { item -> StudioCell(vm, item) }
                }
                if (!vm.selecting) {
                    Row(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(18.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(FD.Amber)
                            .clickable { vm.navigate(Screen.Import) }
                            .padding(start = 16.dp, end = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = FD.OnAmber)
                        Spacer(Modifier.width(8.dp))
                        Text("Import", color = FD.OnAmber, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }

            if (vm.selecting) SelectionActions(vm, onPaste = { showPaste = true }, onExport = ::exportSelected)
        }
    }

    val clip = vm.clipboard
    if (showPaste && clip != null) {
        val n = vm.pasteTargets.size
        PasteSheet(
            title = if (n == 1) "Paste edits to 1 photo" else "Paste edits to $n photos",
            from = clip.fromName,
            confirm = if (n == 1) "Paste to 1 photo" else "Paste to $n photos",
            onDismiss = { showPaste = false },
            onPaste = { parts -> showPaste = false; vm.pasteToSelection(parts) },
        )
    }
}

@Composable
private fun StudioHeader(vm: EditorViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionLabel("Film Desk")
            Text("Studio", color = FD.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { vm.openRecipes() }) {
            Icon(Icons.Filled.FilterVintage, contentDescription = null, tint = FD.Amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Recipes", color = FD.Text)
        }
    }
}

@Composable
private fun SelectionTopBar(vm: EditorViewModel, shown: List<StudioItem>) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(FD.Surface).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::clearSelection) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel selection", tint = FD.Text)
        }
        Text(
            "${vm.selection.size} selected", color = FD.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { vm.selectAll(shown.map { it.id }) }) {
            Icon(Icons.Filled.SelectAll, contentDescription = "Select all", tint = FD.Text)
        }
    }
}

@Composable
private fun ClipboardBanner(vm: EditorViewModel, clip: CopiedEdits) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FD.AmberDim)
            .border(1.dp, FD.Amber, RoundedCornerShape(12.dp))
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = clip.fromId?.let { vm.studioThumbs[it] }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(FD.Surface2)) {
            if (thumb != null) {
                val img = remember(thumb) { thumb.asImageBitmap() }
                Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Edits copied from ${clip.fromName}", color = FD.Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                clip.params.copySummary(), color = FD.Muted, fontSize = 11.sp, fontFamily = FD.Mono,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = vm::clearClipboard) {
            Icon(Icons.Filled.Close, contentDescription = "Clear copied edits", tint = FD.Muted, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudioCell(vm: EditorViewModel, item: StudioItem) {
    val selected = item.id in vm.selection
    val copiedFrom = vm.selecting && vm.clipboard?.fromId == item.id
    val bmp = vm.studioThumbs[item.id]
    val missing = vm.studioMissing[item.id] == true
    LaunchedEffect(item.id, bmp == null) { if (bmp == null) vm.ensureThumb(item.id) }
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(1.5.dp)
            .background(if (selected) FD.AmberDim else Color.Transparent)
            .combinedClickable(
                onClickLabel = if (vm.selecting) "Select" else "Edit photo",
                onLongClickLabel = "Select",
                onClick = { if (vm.selecting) vm.toggleSelect(item.id) else vm.openItem(item) },
                onLongClick = { vm.toggleSelect(item.id) },
            )
            .padding(if (selected) 10.dp else 0.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(if (selected) 6.dp else 0.dp))
                .background(FD.Surface)
                .then(if (item.id in vm.justPasted) Modifier.border(2.dp, FD.Amber) else Modifier)
        ) {
            when {
                missing -> Column(
                    Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = FD.Muted)
                    Text("Not on phone", color = FD.Muted, fontSize = 10.sp)
                }
                bmp != null -> {
                    val img = remember(bmp) { bmp.asImageBitmap() }
                    Image(img, contentDescription = "Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            val p = item.params
            if (p != null) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xBF0B0B0C))
                        .padding(start = 3.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = "Edited", tint = FD.Amber, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        FILM_SIMS[p.sim].short, color = FD.Amber, fontSize = 9.sp, fontFamily = FD.Mono,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (copiedFrom) {
            Text(
                "COPIED", color = FD.OnAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(FD.Amber)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (vm.selecting) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) Modifier.background(FD.Amber)
                        else Modifier.background(Color(0x40000000)).border(2.dp, Color(0xD9F1F0EC), CircleShape)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = FD.OnAmber, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SelectionActions(vm: EditorViewModel, onPaste: () -> Unit, onExport: () -> Unit) {
    val one = vm.selection.singleOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .background(FD.Panel)
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(Icons.Filled.ContentCopy, "Copy", enabled = one != null) { one?.let(vm::copyEdits) }
        ActionButton(Icons.Filled.ContentPaste, "Paste", enabled = vm.clipboard != null, highlight = vm.clipboard != null, onClick = onPaste)
        ActionButton(Icons.Filled.FilterVintage, "Recipe") { vm.openRecipes() }
        ActionButton(Icons.Filled.Download, "Export", enabled = !vm.exporting, onClick = onExport)
        ActionButton(Icons.Filled.RestartAlt, "Reset") { vm.resetSelection() }
        ActionButton(Icons.Filled.Delete, "Remove") { vm.removeSelection() }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionButton(
    icon: ImageVector, label: String, enabled: Boolean = true, highlight: Boolean = false, onClick: () -> Unit,
) {
    val c = when {
        !enabled -> FD.Line
        highlight -> FD.Amber
        else -> FD.Text
    }
    Column(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c)
        Spacer(Modifier.height(4.dp))
        Text(label, color = c, fontSize = 11.sp, fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun EmptyStudio(vm: EditorViewModel) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = FD.Muted, modifier = Modifier.size(52.dp))
        Text("Your Studio is empty", color = FD.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Import photos to edit them. Each photo keeps its own edits, and you can copy edits from one photo and paste them onto others.",
            color = FD.Muted, fontSize = 13.sp,
        )
        Button(
            onClick = { vm.navigate(Screen.Import) },
            colors = ButtonDefaults.buttonColors(containerColor = FD.Amber, contentColor = FD.OnAmber),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Import photos", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** "What to paste" sheet, used by the Studio and the editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteSheet(
    title: String,
    from: String,
    confirm: String,
    onDismiss: () -> Unit,
    onPaste: (Set<PastePart>) -> Unit,
) {
    var parts by rememberSaveable { mutableStateOf(DEFAULT_PASTE_PARTS.map { it.name }.toSet()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FD.Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(title, color = FD.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("From $from · choose what to paste", color = FD.Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("What to paste", Modifier.weight(1f))
                val allOn = parts.size == PastePart.entries.size
                TextButton(onClick = {
                    parts = if (allOn) emptySet() else PastePart.entries.map { it.name }.toSet()
                }) { Text(if (allOn) "Clear all" else "Select all", color = FD.Amber, fontWeight = FontWeight.SemiBold) }
            }
            PastePart.entries.forEach { part ->
                val on = part.name in parts
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { parts = if (on) parts - part.name else parts + part.name }
                        .padding(vertical = 11.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .then(if (on) Modifier.background(FD.Amber) else Modifier.border(2.dp, FD.Muted, RoundedCornerShape(6.dp))),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = FD.OnAmber, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(part.title, color = FD.Text, fontSize = 15.sp)
                        Text(part.subtitle, color = FD.Muted, fontSize = 12.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("Cancel", color = FD.Text)
                }
                Button(
                    onClick = { onPaste(PastePart.entries.filter { it.name in parts }.toSet()) },
                    enabled = parts.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = FD.Amber, contentColor = FD.OnAmber),
                    modifier = Modifier.weight(2f).height(48.dp),
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(confirm, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
