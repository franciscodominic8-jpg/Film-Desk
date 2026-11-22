package com.filmdesk.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.filmdesk.app.data.MediaItem
import com.filmdesk.app.data.MediaRepository
import com.filmdesk.app.ui.theme.FD

private fun galleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Picks photos to add to the Studio: tap to select several, then Import. */
@Composable
fun ImportScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    val perms = remember { galleryPermissions() }
    fun hasAccess() = perms.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    // Android 14+: the user may have allowed only some photos.
    fun partialOnly() = Build.VERSION.SDK_INT >= 34 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasAccess()) }
    var partial by remember { mutableStateOf(partialOnly()) }
    var photos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var refresh by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val inStudio = remember(vm.studio) { vm.studio.map { it.uri.toString() }.toHashSet() }

    fun finish(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        vm.importPhotos(uris)
        vm.back()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        finish(uris)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAccess()
        partial = partialOnly()
        refresh++
    }

    // Re-check when returning to the app (permission may have changed in Settings).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { granted = hasAccess(); partial = partialOnly(); refresh++ }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(granted, refresh) {
        if (granted) photos = MediaRepository.recent(context)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = when {
            maxWidth >= 840.dp -> 6
            maxWidth >= 600.dp -> 5
            else -> 3
        }
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.back() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FD.Text)
                }
                Column(Modifier.weight(1f)) {
                    Text("Import photos", color = FD.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (picked.isEmpty()) "Tap photos to select" else "${picked.size} selected",
                        color = if (picked.isEmpty()) FD.Muted else FD.Amber, fontSize = 12.sp,
                    )
                }
                Button(
                    onClick = { finish(picked.map { it.uri }) },
                    enabled = picked.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = FD.Amber, contentColor = FD.OnAmber),
                ) { Text(if (picked.isEmpty()) "Import" else "Import ${picked.size}", fontWeight = FontWeight.SemiBold) }
            }

            // Open photo card
            FdCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(FD.AmberDim),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = FD.Amber) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("From other apps", color = FD.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Google Photos, Files… choose one or many", color = FD.Muted, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FD.Amber, contentColor = FD.OnAmber),
                    ) { Text("Pick") }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (!granted) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = FD.Muted, modifier = Modifier.size(48.dp))
                    Text("Browse your gallery here", color = FD.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Allow photo access to pick several photos here. You can also use Pick above.",
                        color = FD.Muted, fontSize = 13.sp,
                    )
                    Button(
                        onClick = { permLauncher.launch(perms) },
                        colors = ButtonDefaults.buttonColors(containerColor = FD.Amber, contentColor = FD.OnAmber),
                    ) { Text("Allow photo access") }
                }
            } else {
                if (partial) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Showing only the photos you allowed.",
                            color = FD.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { permLauncher.launch(perms) }) {
                            Text("Select more", color = FD.Amber)
                        }
                    }
                }
            }
            if (granted && photos.isEmpty()) {
                Text("No photos found yet.", color = FD.Muted, modifier = Modifier.padding(24.dp))
            } else if (granted) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text("Recent", color = FD.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${photos.size} photos", color = FD.Muted, fontSize = 12.sp, fontFamily = FD.Mono)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    items(photos, key = { it.id }) { item ->
                        val sel = picked.any { it.id == item.id }
                        MediaThumb(item, selected = sel, already = item.uri.toString() in inStudio) {
                            picked = if (sel) picked.filterNot { it.id == item.id } else picked + item
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaThumb(item: MediaItem, selected: Boolean, already: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, item.id) {
        value = MediaRepository.thumbnail(context, item)
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) FD.AmberDim else FD.Surface)
            .clickable(onClickLabel = "Select photo", onClick = onClick)
            .padding(if (selected) 8.dp else 0.dp)
    ) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(
                img, contentDescription = "Photo",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(if (selected) 4.dp else 0.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        if (already && !selected) {
            Text(
                "In Studio", color = FD.Text, fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xBF0B0B0C)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(FD.Amber)
                    else Modifier.background(Color(0x40000000)).border(2.dp, Color(0xD9F1F0EC), CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = FD.OnAmber, modifier = Modifier.size(16.dp))
        }
    }
}
