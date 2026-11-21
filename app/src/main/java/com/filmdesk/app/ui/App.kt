package com.filmdesk.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.filmdesk.app.ui.theme.FD
import com.filmdesk.app.ui.theme.FilmDeskTheme

@Composable
fun FilmDeskApp(vm: EditorViewModel) {
    FilmDeskTheme {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm.message) {
            val m = vm.message ?: return@LaunchedEffect
            snackbar.showSnackbar(m)
            vm.message = null
        }
        BackHandler(enabled = vm.screen != Screen.Library) { vm.back() }

        Box(
            Modifier
                .fillMaxSize()
                .background(FD.Bg)
                .safeDrawingPadding()
        ) {
            when (vm.screen) {
                Screen.Library -> LibraryScreen(vm)
                Screen.Editor -> EditorScreen(vm)
                Screen.Recipes -> RecipesScreen(vm)
                Screen.Export -> ExportScreen(vm)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
        }
    }
}
