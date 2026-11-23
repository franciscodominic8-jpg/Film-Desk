package com.filmdesk.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.filmdesk.app.engine.BitmapIO
import com.filmdesk.app.model.BUILT_IN_PRESETS
import com.filmdesk.app.model.ExportFormat
import com.filmdesk.app.model.ExportPreset
import com.filmdesk.app.model.ExportSettings
import com.filmdesk.app.model.FILM_SIMS
import com.filmdesk.app.model.MetadataMode
import com.filmdesk.app.model.SHARPEN_LEVELS
import com.filmdesk.app.model.Sharpen
import com.filmdesk.app.model.SizeMode
import com.filmdesk.app.model.WATERMARK_COLORS
import com.filmdesk.app.model.cleanFolder
import com.filmdesk.app.model.formatBytes
import com.filmdesk.app.ui.theme.FD
import kotlin.math.roundToInt

/** Lightroom-style export: presets, file type, size, sharpening, metadata, folder, watermark. */
@Composable
fun ExportScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    val s = vm.exportSettings
    val batch = vm.exportTargets.isNotEmpty()
    fun set(f: (ExportSettings) -> ExportSettings) { vm.exportSettings = f(vm.exportSettings) }

    LaunchedEffect(vm.preview, vm.fullLongEdge) { vm.scheduleEstimate() }

    // Android 7–9 needs the storage permission to write to Pictures/.
    val needsWritePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.export(share = false) else vm.message = "Storage permission is needed to save on this Android version"
    }
    // Android 10+: reading GPS from the original needs ACCESS_MEDIA_LOCATION.
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        set { it.copy(keepLocation = ok) }
        if (!ok) vm.message = "Location permission denied — location won't be copied"
    }
    fun setKeepLocation(on: Boolean) {
        val needsPermission = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) locationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        else set { it.copy(keepLocation = on) }
    }
    fun save() {
        if (needsWritePermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) writeLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else vm.export(share = false)
    }

    var savePreset by remember { mutableStateOf(false) }
    var deletePreset by remember { mutableStateOf<ExportPreset?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var before by remember { mutableStateOf<ExportSettings?>(null) }
    fun open(id: String) { before = vm.exportSettings; sheet = id }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FD.Text)
            }
            Text("Export", color = FD.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                if (batch) "${vm.exportTargets.size} photos" else "1 photo",
                color = if (batch) FD.Amber else FD.Muted, fontSize = 12.sp, fontFamily = FD.Mono,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val mod = Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(horizontal = 16.dp)
            if (batch) BatchSummary(vm, mod.padding(vertical = 8.dp)) else Summary(vm, s, mod.padding(vertical = 8.dp))
            Presets(vm, s, mod, onSave = { savePreset = true }, onDelete = { deletePreset = it })
            Spacer(Modifier.height(10.dp))
            val rowMod = Modifier.fillMaxWidth().widthIn(max = 720.dp)
            SettingRow("File type", fileTypeSummary(s), rowMod) { open("File type") }
            SettingRow("Size", sizeSummary(s), rowMod) { open("Size") }
            SettingRow(
                "Limit file size",
                if (!s.canLimitSize) "—" else if (s.limitSize) "${trimMb(s.limitMb)} MB" else "Off",
                rowMod, enabled = s.canLimitSize,
            ) { open("Limit file size") }
            SettingRow("Output sharpening", if (s.sharpen == Sharpen.Off) "Off" else "${s.sharpen.label} · ${SHARPEN_LEVELS[s.sharpenLevel]}", rowMod) { open("Output sharpening") }
            SettingRow(
                "Metadata",
                if (!s.canWriteMetadata) "None (${s.format.label})"
                else s.metadata.label + (if (s.metadata != MetadataMode.None && s.copyright.isNotBlank()) " · ${s.copyright}" else ""),
                rowMod,
            ) { open("Metadata") }
            SettingRow("Save to", "Pictures/${s.folderPath}", rowMod) { open("Save to") }
            SettingRow("Watermark", if (s.watermark) s.wmText.ifBlank { "© Your Name" } else "Off", rowMod) { open("Watermark") }
            Spacer(Modifier.height(8.dp))
        }

        // Share / Save
        Row(
            Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!batch) {
                OutlinedButton(
                    onClick = { vm.export(share = true) }, enabled = !vm.exporting,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Share", color = FD.Text) }
            }
            Button(
                onClick = ::save, enabled = !vm.exporting,
                colors = ButtonDefaults.buttonColors(containerColor = FD.Amber, contentColor = FD.OnAmber),
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(2f).height(48.dp),
            ) {
                if (vm.exporting) {
                    CircularProgressIndicator(color = FD.OnAmber, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(vm.batchProgress ?: "Exporting…", fontWeight = FontWeight.SemiBold)
                } else {
                    Text(
                        (if (batch) "Save ${vm.exportTargets.size} to " else "Save to ") + "Pictures/${s.folderPath}",
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (savePreset) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { savePreset = false },
            title = { Text("Save preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                        label = { Text("Preset name") }, placeholder = { Text("e.g. Client") }, colors = fieldColors(),
                    )
                    Text(presetSummary(s), color = FD.Muted, fontSize = 11.sp, fontFamily = FD.Mono, lineHeight = 16.sp)
                }
            },
            confirmButton = { TextButton(onClick = { vm.saveExportPreset(name); savePreset = false }) { Text("Save preset", color = FD.Amber) } },
            dismissButton = { TextButton(onClick = { savePreset = false }) { Text("Cancel", color = FD.Muted) } },
            containerColor = FD.Surface,
        )
    }
    deletePreset?.let { p ->
        AlertDialog(
            onDismissRequest = { deletePreset = null },
            title = { Text("Delete preset “${p.name}”?") },
            confirmButton = { TextButton(onClick = { vm.deleteExportPreset(p); deletePreset = null }) { Text("Delete", color = FD.Amber) } },
            dismissButton = { TextButton(onClick = { deletePreset = null }) { Text("Cancel", color = FD.Muted) } },
            containerColor = FD.Surface,
        )
    }

    sheet?.let { id ->
        val d = ExportSettings()
        ExportSheet(
            title = id,
            onCancel = { before?.let { vm.exportSettings = it }; sheet = null },
            onReset = {
                set {
                    when (id) {
                        "File type" -> it.copy(format = d.format, quality = d.quality, webpLossless = d.webpLossless, limitSize = d.limitSize, limitMb = d.limitMb)
                        "Size" -> it.copy(sizeMode = d.sizeMode, longEdge = d.longEdge, shortEdge = d.shortEdge, megapixels = d.megapixels, percent = d.percent)
                        "Limit file size" -> it.copy(limitSize = d.limitSize, limitMb = d.limitMb)
                        "Output sharpening" -> it.copy(sharpen = d.sharpen, sharpenLevel = d.sharpenLevel)
                        "Metadata" -> it.copy(metadata = d.metadata, cameraInfo = d.cameraInfo, keepLocation = d.keepLocation, embedRecipe = d.embedRecipe, copyright = d.copyright)
                        "Save to" -> it.copy(folder = d.folder)
                        else -> it.copy(watermark = d.watermark, wmText = d.wmText, wmPos = d.wmPos, wmSize = d.wmSize, wmOpacity = d.wmOpacity, wmColor = d.wmColor)
                    }
                }
            },
            onDone = { sheet = null },
        ) {
            when (id) {
                "File type" -> {

                        val formats = ExportFormat.entries.filter { it != ExportFormat.HEIF || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P }
                        Segmented(formats.map { it.label }, formats.indexOf(s.format)) { i -> set { it.copy(format = formats[i]) } }
                        if (s.format == ExportFormat.WEBP) {
                            SwitchLine("Lossless", "Bigger file, no quality loss", s.webpLossless) { on -> set { it.copy(webpLossless = on) } }
                        }
                        if (s.format == ExportFormat.PNG) Note("PNG is always lossless.")
                        if (s.format == ExportFormat.HEIF) Note("Smaller files at the same quality.")
                        if (!s.canWriteMetadata) Note("Metadata (camera info, copyright, recipe) is saved in JPEG files only.")
                        if (s.lossy) {
                            BigValue("Quality", s.quality.toString(), true)
                            TickSlider(
                                s.quality.toFloat(), 10f, 100f, 1f,
                                onChange = { v -> set { it.copy(quality = v.roundToInt()) } },
                                origin = 10f, minLabel = "10", maxLabel = "100", description = "Quality",
                            )
                        }
                        if (s.canLimitSize) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Limit file size", color = FD.Text, fontSize = 14.sp)
                                    Text("Highest quality that fits", color = FD.Muted, fontSize = 12.sp)
                                }
                                NumberField(
                                    value = s.limitMb.let { if (it == it.roundToInt().toFloat()) it.roundToInt().toString() else it.toString() },
                                    suffix = "MB", width = 96, decimal = true, enabled = s.limitSize, min = 0.1f,
                                ) { txt -> txt.toFloatOrNull()?.let { v -> set { it.copy(limitMb = v.coerceIn(0.1f, 100f)) } } }
                                Spacer(Modifier.width(8.dp))
                                FdSwitch(s.limitSize) { on -> set { it.copy(limitSize = on) } }
                            }
                        }
            
                }
                "Size" -> {

                        ChipRow(SizeMode.entries.map { it.label }, SizeMode.entries.indexOf(s.sizeMode)) { i -> set { it.copy(sizeMode = SizeMode.entries[i]) } }
                        if (s.sizeMode != SizeMode.Full) {
                            val (value, suffix, quick) = when (s.sizeMode) {
                                SizeMode.LongEdge -> Triple(s.longEdge, "px", listOf(1080, 2048, 4096))
                                SizeMode.ShortEdge -> Triple(s.shortEdge, "px", listOf(1080, 2048, 4096))
                                SizeMode.Megapixels -> Triple(s.megapixels, "MP", listOf(8, 12, 24))
                                else -> Triple(s.percent, "%", listOf(25, 50, 75))
                            }
                            fun setValue(v: Int) = set {
                                when (it.sizeMode) {
                                    SizeMode.LongEdge -> it.copy(longEdge = v.coerceIn(64, 16000))
                                    SizeMode.ShortEdge -> it.copy(shortEdge = v.coerceIn(64, 16000))
                                    SizeMode.Megapixels -> it.copy(megapixels = v.coerceIn(1, 200))
                                    else -> it.copy(percent = v.coerceIn(1, 100))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val min = if (s.sizeMode == SizeMode.LongEdge || s.sizeMode == SizeMode.ShortEdge) 64f else 1f
                                NumberField(value.toString(), suffix, width = 120, key = s.sizeMode.name, min = min) { txt ->
                                    txt.toIntOrNull()?.let(::setValue)
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    quick.forEach { q -> Pill(q.toString(), selected = value == q, onClick = { setValue(q) }) }
                                }
                            }
                        }
                        if (!batch) {
                            val sizes = vm.fullSizes()
                            val out = vm.exportSize()
                            if (sizes != null && out != null) {
                                Text(
                                    "Output ${out.first} × ${out.second} · from ${sizes.first.first} × ${sizes.first.second}",
                                    color = FD.Muted, fontSize = 11.sp, fontFamily = FD.Mono,
                                )
                            }
                        } else {
                            Note("Each photo is sized from its own crop. Photos are never made larger.")
                        }
            
                }
                "Limit file size" -> {
                    if (!s.canLimitSize) Note("Only JPEG and lossy WebP can be limited to a file size.")
                        if (s.canLimitSize) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Limit file size", color = FD.Text, fontSize = 14.sp)
                                    Text("Highest quality that fits", color = FD.Muted, fontSize = 12.sp)
                                }
                                NumberField(
                                    value = s.limitMb.let { if (it == it.roundToInt().toFloat()) it.roundToInt().toString() else it.toString() },
                                    suffix = "MB", width = 96, decimal = true, enabled = s.limitSize, min = 0.1f,
                                ) { txt -> txt.toFloatOrNull()?.let { v -> set { it.copy(limitMb = v.coerceIn(0.1f, 100f)) } } }
                                Spacer(Modifier.width(8.dp))
                                FdSwitch(s.limitSize) { on -> set { it.copy(limitSize = on) } }
                            }
                        }
            
                }
                "Output sharpening" -> {

                        Segmented(Sharpen.entries.map { it.label }, Sharpen.entries.indexOf(s.sharpen)) { i -> set { it.copy(sharpen = Sharpen.entries[i]) } }
                        if (s.sharpen != Sharpen.Off) {
                            Segmented(SHARPEN_LEVELS, s.sharpenLevel) { i -> set { it.copy(sharpenLevel = i) } }
                            Note(
                                when (s.sharpen) {
                                    Sharpen.Screen -> "For phones, computers and social media."
                                    Sharpen.Matte -> "For prints on matte paper."
                                    else -> "For prints on glossy paper."
                                }
                            )
                        }
            
                }
                "Metadata" -> {

                        if (!s.canWriteMetadata) {
                            Note("${s.format.label} files are saved without metadata. Choose JPEG to keep camera info, copyright and the recipe.")
                        } else {
                            Segmented(MetadataMode.entries.map { it.label }, MetadataMode.entries.indexOf(s.metadata)) { i ->
                                set { it.copy(metadata = MetadataMode.entries[i]) }
                            }
                            if (s.metadata == MetadataMode.All) {
                                SwitchLine("Camera info", "Camera, lens, ISO, shutter, date", s.cameraInfo) { on -> set { it.copy(cameraInfo = on) } }
                                SwitchLine("Location", "GPS from the original", s.keepLocation, ::setKeepLocation)
                                SwitchLine("Embed recipe", "Re-import the look later", s.embedRecipe) { on -> set { it.copy(embedRecipe = on) } }
                            }
                            if (s.metadata != MetadataMode.None) {
                                TextLine("Copyright / author", s.copyright, "© Your Name") { t -> set { it.copy(copyright = t.take(120)) } }
                            } else {
                                Note("No camera details, location or copyright are saved in the file.")
                            }
                        }
            
                }
                "Save to" -> {

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pictures/FilmDesk/", color = FD.Muted, fontSize = 14.sp)
                            OutlinedTextField(
                                value = s.folder,
                                // Spaces are fine while typing; the name is tidied when saving.
                                onValueChange = { t -> set { it.copy(folder = t.replace(Regex("[^\\p{L}\\p{N} _-]"), "").take(40)) } },
                                singleLine = true,
                                placeholder = { Text("(no subfolder)", color = FD.Line) },
                                colors = fieldColors(),
                                modifier = Modifier.weight(1f),
                            )
                        }
            
                }
                else -> {

                        SwitchLine("Watermark", "Your name or © on the photo", s.watermark) { on -> set { it.copy(watermark = on) } }
                        if (s.watermark) {
                            WatermarkPreview(vm, s)
                            TextLine("Text", s.wmText, "© Your Name") { t -> set { it.copy(wmText = t.take(80)) } }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Position", color = FD.Text, fontSize = 14.sp)
                                    Text(POSITIONS[s.wmPos], color = FD.Muted, fontSize = 12.sp)
                                }
                                PositionGrid(s.wmPos) { i -> set { it.copy(wmPos = i) } }
                            }
                            LabeledSlider(
                                label = "Size", value = s.wmSize.toFloat(), range = 2f..12f, steps = 9, display = "${s.wmSize}%",
                                changed = true, onChange = { v -> set { it.copy(wmSize = v.roundToInt()) } }, onDone = {},
                            )
                            LabeledSlider(
                                label = "Opacity", value = s.wmOpacity.toFloat(), range = 10f..100f, display = "${s.wmOpacity}%",
                                changed = true, onChange = { v -> set { it.copy(wmOpacity = v.roundToInt()) } }, onDone = {},
                            )
                            ChipRow(WATERMARK_COLORS, s.wmColor) { i -> set { it.copy(wmColor = i) } }
                        }
            
                }
            }
        }
    }
}

private val POSITIONS = listOf(
    "Top left", "Top centre", "Top right", "Middle left", "Centre", "Middle right", "Bottom left", "Bottom centre", "Bottom right",
)

private fun presetSummary(s: ExportSettings): String = buildString {
    append(s.format.label)
    if (s.lossy) append(" ").append(s.quality)
    append(" · ").append(
        when (s.sizeMode) {
            SizeMode.Full -> "Full size"
            SizeMode.LongEdge -> "Long edge ${s.longEdge} px"
            SizeMode.ShortEdge -> "Short edge ${s.shortEdge} px"
            SizeMode.Megapixels -> "${s.megapixels} MP"
            SizeMode.Percent -> "${s.percent}%"
        }
    )
    if (s.sharpen != Sharpen.Off) append(" · Sharpen ").append(s.sharpen.label).append("/").append(SHARPEN_LEVELS[s.sharpenLevel])
    append("\nMetadata: ").append(s.metadata.label)
    if (s.watermark) append(" · Watermark on")
    append("\nFolder: Pictures/").append(s.folderPath)
}

@Composable
private fun Summary(vm: EditorViewModel, s: ExportSettings, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 96.dp, height = 72.dp).clip(RoundedCornerShape(6.dp)).background(FD.Surface)) {
            // In the Crop tab the preview is the uncropped photo, so use the saved Studio thumbnail.
            val thumb = if (vm.tab == EditorTab.Crop) vm.currentId?.let { vm.studioThumbs[it] } ?: vm.preview else vm.preview
            thumb?.let { b ->
                val img = remember(b) { b.asImageBitmap() }
                Image(img, contentDescription = "Edited photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(FILM_SIMS[vm.params.sim].name, color = FD.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val out = vm.exportSize(s)
            val fmt = s.format.label + if (s.lossy) " ${s.quality}" else ""
            Text(
                (out?.let { "${it.first} × ${it.second} · " } ?: "") + fmt,
                color = FD.Muted, fontSize = 11.sp, fontFamily = FD.Mono,
            )
            Text(vm.exportEstimate?.let { "≈ ${formatBytes(it)}" } ?: "Estimating size…", color = FD.Amber, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BatchSummary(vm: EditorViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            vm.exportTargets.take(5).forEach { id ->
                LaunchedEffect(id) { vm.ensureThumb(id) }
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).background(FD.Surface)) {
                    vm.studioThumbs[id]?.let { b ->
                        val img = remember(b) { b.asImageBitmap() }
                        Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
            if (vm.exportTargets.size > 5) Text("+${vm.exportTargets.size - 5}", color = FD.Muted, fontSize = 12.sp, fontFamily = FD.Mono)
        }
        Text("Each photo keeps its own edits", color = FD.Muted, fontSize = 11.sp, fontFamily = FD.Mono)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Presets(vm: EditorViewModel, s: ExportSettings, modifier: Modifier, onSave: () -> Unit, onDelete: (ExportPreset) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (BUILT_IN_PRESETS + vm.exportPresets).forEach { p ->
                val on = p.matches(s)
                Text(
                    p.name,
                    color = if (on) FD.Bg else FD.Text, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) FD.Text else Color.Transparent)
                        .border(1.dp, if (on) FD.Text else FD.Line, RoundedCornerShape(50))
                        .combinedClickable(
                            onClickLabel = "Use ${p.name}",
                            onLongClickLabel = if (p.builtIn) null else "Delete ${p.name}",
                            onClick = { vm.applyExportPreset(p) },
                            onLongClick = if (p.builtIn) null else ({ onDelete(p) }),
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Text(
                "+ Save", color = FD.Amber, fontSize = 13.sp, maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, FD.Amber.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .clickable(onClickLabel = "Save current settings as a preset", onClick = onSave)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        if (vm.exportPresets.isNotEmpty()) Text("Long-press your own preset to delete it.", color = FD.Muted, fontSize = 11.sp)
    }
}

// ------------------------------------------------------------------ small building blocks

private fun trimMb(v: Float): String = if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else v.toString()

private fun fileTypeSummary(s: ExportSettings): String = when {
    s.format == ExportFormat.PNG -> "PNG"
    s.format == ExportFormat.WEBP && s.webpLossless -> "WebP · lossless"
    else -> "${s.format.label} · ${s.quality}"
}

private fun sizeSummary(s: ExportSettings): String = when (s.sizeMode) {
    SizeMode.Full -> "Full size"
    SizeMode.LongEdge -> "Long edge · ${s.longEdge} px"
    SizeMode.ShortEdge -> "Short edge · ${s.shortEdge} px"
    SizeMode.Megapixels -> "${s.megapixels} MP"
    SizeMode.Percent -> "${s.percent}%"
}

/** One line of the export list: name on the left, current value and › on the right. */
@Composable
private fun SettingRow(title: String, value: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(enabled = enabled, onClickLabel = title, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = if (enabled) FD.Text else FD.Muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(
                value, color = FD.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FD.Muted, modifier = Modifier.size(18.dp))
        }
        androidx.compose.material3.HorizontalDivider(color = FD.Line.copy(alpha = 0.6f), thickness = 0.5.dp)
    }
}

/** Bottom sheet for one export setting, with ✕ (undo changes) · Reset · ✓. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(
    title: String,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FD.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                title.uppercase(), color = FD.Muted, fontSize = 12.sp, fontFamily = FD.Mono, letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
            androidx.compose.material3.HorizontalDivider(color = FD.Line, thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = FD.Text) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onReset) { Text("Reset", color = FD.Muted) }
                }
                IconButton(onClick = onDone) { Icon(Icons.Filled.Check, contentDescription = "Done", tint = FD.Amber) }
            }
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(FD.Surface)
            .border(1.dp, FD.Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel(title)
        content()
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FD.Surface2).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) FD.Amber else Color.Transparent)
                    .clickable(onClickLabel = label) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (on) FD.OnAmber else Color(0xFFC9C8C3), fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label -> Pill(label, selected = i == selected, onClick = { onSelect(i) }) }
    }
}

@Composable
private fun SwitchLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = FD.Text, fontSize = 14.sp)
            Text(subtitle, color = FD.Muted, fontSize = 12.sp)
        }
        FdSwitch(checked, onChange)
    }
}

@Composable
private fun FdSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.Switch(
        checked = checked, onCheckedChange = onChange,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = FD.OnAmber, checkedTrackColor = FD.Amber,
            uncheckedThumbColor = FD.Muted, uncheckedTrackColor = FD.Surface2, uncheckedBorderColor = FD.Line,
        ),
    )
}

@Composable
private fun Note(text: String) = Text(text, color = FD.Muted, fontSize = 12.sp)

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FD.Text, unfocusedTextColor = FD.Text, disabledTextColor = FD.Muted,
    focusedBorderColor = FD.Amber, unfocusedBorderColor = FD.Line, disabledBorderColor = FD.Line,
    cursorColor = FD.Amber, focusedLabelColor = FD.Amber, unfocusedLabelColor = FD.Muted,
    focusedContainerColor = FD.Panel, unfocusedContainerColor = FD.Panel, disabledContainerColor = FD.Panel,
)

@Composable
private fun TextLine(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) }, placeholder = { Text(placeholder, color = FD.Line) },
        colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A number box that keeps exactly what is being typed ("2", "20", "204", "2048" or "1.") and
 * only passes on numbers from [min] up; when you leave the box it shows the value in use.
 */
@Composable
private fun NumberField(
    value: String, suffix: String, width: Int, decimal: Boolean = false, enabled: Boolean = true, key: String = "",
    min: Float = 0f, onValue: (String) -> Unit,
) {
    var text by remember(key) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    // Follow changes made elsewhere (e.g. a preset), but not the box's own typing.
    LaunchedEffect(value, focused) {
        if (!focused || text.toFloatOrNull() != value.toFloatOrNull()) text = value
    }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            var clean = t.filter { it.isDigit() || (decimal && it == '.') }.take(6)
            if (decimal && clean.count { it == '.' } > 1) clean = clean.substring(0, clean.lastIndexOf('.'))
            text = clean
            val v = clean.toFloatOrNull()
            if (v != null && v >= min) onValue(clean)
        },
        enabled = enabled,
        singleLine = true,
        suffix = { Text(suffix, color = FD.Muted) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        colors = fieldColors(),
        modifier = Modifier.width(width.dp).onFocusChanged { focused = it.isFocused },
    )
}

@Composable
private fun PositionGrid(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in 0 until 3) {
                val i = r * 3 + c
                Box(
                    Modifier
                        .size(30.dp, 24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (i == selected) FD.Amber else FD.Surface2)
                        .border(1.dp, FD.Line, RoundedCornerShape(5.dp))
                        .clickable(onClickLabel = POSITIONS[i]) { onSelect(i) }
                )
            }
        }
    }
}

/** Approximate live preview of the watermark on the photo. */
@Composable
private fun WatermarkPreview(vm: EditorViewModel, s: ExportSettings) {
    val b = (if (vm.exportTargets.isNotEmpty()) vm.exportTargets.firstOrNull()?.let { vm.studioThumbs[it] }
    else if (vm.tab == EditorTab.Crop) vm.currentId?.let { vm.studioThumbs[it] } else vm.preview) ?: return
    val img = remember(b) { b.asImageBitmap() }
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .then(Modifier.height(220.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val ratio = b.width / b.height.toFloat()
        val w = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
        val h = w / ratio
        Box(Modifier.size(w, h)) {
            Image(img, contentDescription = "Watermark preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            val short = if (w < h) w else h
            val color = when (s.wmColor) { 1 -> Color.Black; 2 -> FD.Amber; else -> Color.White }.copy(alpha = s.wmOpacity / 100f)
            val align = when (s.wmPos) {
                0 -> Alignment.TopStart; 1 -> Alignment.TopCenter; 2 -> Alignment.TopEnd
                3 -> Alignment.CenterStart; 4 -> Alignment.Center; 5 -> Alignment.CenterEnd
                6 -> Alignment.BottomStart; 7 -> Alignment.BottomCenter; else -> Alignment.BottomEnd
            }
            val density = androidx.compose.ui.platform.LocalDensity.current
            val fontSize = with(density) { (short * s.wmSize / 100f).toSp() }
            Text(
                s.wmText.ifBlank { "© Your Name" },
                style = TextStyle(
                    color = color, fontSize = fontSize,
                    shadow = if (s.wmColor != 1) Shadow(Color(0x66000000), blurRadius = 4f) else null,
                ),
                maxLines = 1,
                modifier = Modifier.align(align).padding(short * 0.035f),
            )
        }
    }
}
