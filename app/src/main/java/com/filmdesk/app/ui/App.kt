package com.filmdesk.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
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
            val undo = vm.undoOffer?.takeIf { it.first == m }
            val r = snackbar.showSnackbar(
                m,
                actionLabel = if (undo != null) "Undo" else null,
                duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) undo?.second?.invoke()
            vm.message = null
            if (vm.undoOffer === undo) vm.undoOffer = null
        }
        BackHandler(enabled = vm.screen != Screen.Library || vm.curveMode || vm.selecting) {
            when {
                vm.curveMode -> vm.closeCurve()
                vm.screen == Screen.Library && vm.selecting -> vm.clearSelection()
                else -> vm.back()
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(FD.Bg)
                .safeDrawingPadding()
        ) {
            when (vm.screen) {
                Screen.Library -> StudioScreen(vm)
                Screen.Import -> ImportScreen(vm)
                Screen.Editor -> EditorScreen(vm)
                Screen.Recipes -> RecipesScreen(vm)
                Screen.Export -> ExportScreen(vm)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
        }
    }
}
