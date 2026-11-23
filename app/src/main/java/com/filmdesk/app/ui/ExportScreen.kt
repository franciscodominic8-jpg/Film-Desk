package com.filmdesk.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.filmdesk.app.engine.BitmapIO
import com.filmdesk.app.model.EXPORT_SIZES
import com.filmdesk.app.model.ExportFormat
import com.filmdesk.app.model.FILM_SIMS
import com.filmdesk.app.ui.theme.FD
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    val s = vm.exportSettings
    val maxEdge = remember { BitmapIO.maxExportLongEdge() }

    // Android 7–9 needs the storage permission to write to Pictures/.
    val needsWritePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.export(share = false) else vm.message = "Storage permission is needed to save on this Android version"
    }
    // Android 10+: reading GPS from the original needs ACCESS_MEDIA_LOCATION.
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.exportSettings = vm.exportSettings.copy(keepLocation = ok)
        if (!ok) vm.message = "Location permission denied — location won't be copied"
    }
    fun setKeepLocation(on: Boolean) {
        val needsPermission = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) locationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        else vm.exportSettings = vm.exportSettings.copy(keepLocation = on)
    }
    fun save() {
        if (needsWritePermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) writeLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else vm.export(share = false)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FD.Text)
            }
            Text("Export", color = FD.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 120.dp, height = 90.dp).clip(RoundedCornerShape(6.dp)).background(FD.Surface)) {
                    vm.preview?.let { b ->
                        val img = remember(b) { b.asImageBitmap() }
                        Image(img, contentDescription = "Edited photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(FILM_SIMS[vm.params.sim].name, color = FD.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Full-resolution render on save", color = FD.Muted, fontSize = 12.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Format")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { f ->
                        Pill(f.label, selected = s.format == f, onClick = { vm.exportSettings = s.copy(format = f) })
                    }
                }
            }

            if (s.format == ExportFormat.JPEG) {
                LabeledSlider(
                    label = "Quality",
                    value = s.quality.toFloat(),
                    range = 60f..100f,
                    display = s.quality.toString(),
                    changed = true,
                    onChange = { vm.exportSettings = vm.exportSettings.copy(quality = it.roundToInt()) },
                    onDone = {},
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Size (long edge)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXPORT_SIZES.forEachIndexed { i, edge ->
                        val label = if (edge == null) "Full (up to ${maxEdge}px)" else "${edge}px"
                        Pill(label, selected = s.size == i, onClick = { vm.exportSettings = s.copy(size = i) })
                    }
                }
            }

            FdCard(Modifier.fillMaxWidth().widthIn(max = 720.dp)) {
                ToggleRow(
                    "Embed recipe in metadata", "Re-import the exact look later (JPEG only)",
                    s.embedRecipe && s.format == ExportFormat.JPEG,
                    { vm.exportSettings = vm.exportSettings.copy(embedRecipe = it) },
                    enabled = s.format == ExportFormat.JPEG,
                )
                HorizontalDivider(color = FD.Line)
                ToggleRow(
                    "Keep location", "Copy GPS from the original photo (camera info is always kept, JPEG only)",
                    s.keepLocation && s.format == ExportFormat.JPEG,
                    { setKeepLocation(it) },
                    enabled = s.format == ExportFormat.JPEG,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier.fillMaxWidth().background(FD.Panel).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { vm.export(share = true) },
                enabled = !vm.exporting,
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = FD.Text)
                Spacer(Modifier.width(8.dp))
                Text("Share", color = FD.Text)
            }
            Button(
                onClick = { save() },
                enabled = !vm.exporting,
                colors = ButtonDefaults.buttonColors(containerColor = FD.Amber, contentColor = FD.OnAmber),
                modifier = Modifier.weight(1.4f).height(52.dp),
            ) {
                if (vm.exporting) {
                    CircularProgressIndicator(color = FD.OnAmber, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Rendering…", fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Save to gallery", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
