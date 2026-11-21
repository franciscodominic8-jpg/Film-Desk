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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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

@Composable
fun LibraryScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    val perms = remember { galleryPermissions() }
    fun hasAccess() = perms.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    var granted by remember { mutableStateOf(hasAccess()) }
    var photos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var refresh by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.openImage(uri)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAccess()
        refresh++
    }

    // Re-check when returning to the app (permission may have changed in Settings).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { granted = hasAccess(); refresh++ }
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
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Film Desk")
                    Text("Library", color = FD.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { vm.navigate(Screen.Recipes) }) {
                    Icon(Icons.Filled.FilterVintage, contentDescription = null, tint = FD.Amber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Recipes", color = FD.Text)
                }
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
                        Text("Open a photo", color = FD.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("JPEG, HEIF, PNG or WebP from any app", color = FD.Muted, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
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
                        "Allow photo access to see your pictures in Film Desk. You can also just use Pick above.",
                        color = FD.Muted, fontSize = 13.sp,
                    )
                    Button(
                        onClick = { permLauncher.launch(perms) },
                        colors = ButtonDefaults.buttonColors(containerColor = FD.Amber, contentColor = FD.OnAmber),
                    ) { Text("Allow photo access") }
                }
            } else if (photos.isEmpty()) {
                Text("No photos found yet.", color = FD.Muted, modifier = Modifier.padding(24.dp))
            } else {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text("Recent", color = FD.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${photos.size} photos", color = FD.Muted, fontSize = 12.sp, fontFamily = FD.Mono)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    items(photos, key = { it.id }) { item -> MediaThumb(item) { vm.openImage(item.uri) } }
                }
            }
        }
    }
}

@Composable
private fun MediaThumb(item: MediaItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, item.id) {
        value = MediaRepository.thumbnail(context, item)
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(FD.Surface)
            .clickable(onClickLabel = "Edit photo", onClick = onClick)
    ) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(img, contentDescription = "Photo", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
