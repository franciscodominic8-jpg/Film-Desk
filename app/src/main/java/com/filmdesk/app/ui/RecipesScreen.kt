package com.filmdesk.app.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmdesk.app.data.Recipe
import com.filmdesk.app.data.RecipeStore
import com.filmdesk.app.model.FILM_SIMS
import com.filmdesk.app.model.summary
import com.filmdesk.app.ui.theme.FD

@Composable
fun RecipesScreen(vm: EditorViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var toDelete by remember { mutableStateOf<Recipe?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FD.Text)
            }
            Column(Modifier.weight(1f)) {
                SectionLabel("Saved looks")
                Text("Recipes", color = FD.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { vm.importRecipe(clipboard.getText()?.text) }) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = FD.Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste", color = FD.Amber)
            }
        }
        Text(
            if (vm.hasImage) "Tap a recipe to apply it to your photo." else "Tap a recipe to select it, then open a photo.",
            color = FD.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(vm.recipes, key = { it.id }) { r ->
                RecipeCard(
                    r,
                    thumb = vm.simThumbs.getOrNull(r.params.sim),
                    onApply = { vm.applyRecipe(r) },
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, RecipeStore.shareText(r))
                        }
                        context.startActivity(Intent.createChooser(send, "Share recipe"))
                    },
                    onDelete = { toDelete = r },
                )
            }
        }
    }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete “${r.name}”?") },
            confirmButton = { TextButton(onClick = { vm.deleteRecipe(r); toDelete = null }) { Text("Delete", color = FD.Amber) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel", color = FD.Muted) } },
            containerColor = FD.Surface,
        )
    }
}

@Composable
private fun RecipeCard(
    r: Recipe,
    thumb: android.graphics.Bitmap?,
    onApply: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val sim = FILM_SIMS[r.params.sim]
    FdCard(Modifier.fillMaxWidth().widthIn(max = 720.dp)) {
        Row(
            Modifier.clickable(onClickLabel = "Apply ${r.name}", onClick = onApply).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).background(Color(sim.swatch))) {
                if (thumb != null) {
                    val img = remember(thumb) { thumb.asImageBitmap() }
                    Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(r.name, color = FD.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sim.name, color = FD.Amber, fontSize = 12.sp)
                Text(r.params.summary(), color = FD.Muted, fontSize = 10.sp, fontFamily = FD.Mono, lineHeight = 14.sp, maxLines = 3)
            }
            Column {
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Share ${r.name}", tint = FD.Muted) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${r.name}", tint = FD.Muted) }
            }
        }
    }
}
