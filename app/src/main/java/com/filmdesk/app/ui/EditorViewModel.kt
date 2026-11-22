package com.filmdesk.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filmdesk.app.data.Recipe
import com.filmdesk.app.data.RecipeStore
import com.filmdesk.app.engine.BitmapIO
import com.filmdesk.app.engine.ImageEngine
import com.filmdesk.app.engine.ImageStats
import com.filmdesk.app.model.EXPORT_SIZES
import com.filmdesk.app.model.EditParams
import com.filmdesk.app.model.ExportFormat
import com.filmdesk.app.model.FILM_SIMS
import com.filmdesk.app.model.caption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Screen { Library, Editor, Recipes, Export }

enum class EditorTab(val label: String) { Film("Film"), QMenu("Q Menu"), Pro("Pro"), Crop("Crop") }

data class ExportSettings(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 92,
    val size: Int = 0,
    val border: Boolean = false,
    val embedRecipe: Boolean = true,
    /** Copy GPS location from the original photo (off by default for privacy). */
    val keepLocation: Boolean = false,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val store = RecipeStore(app)

    var screen by mutableStateOf(Screen.Library); private set
    private val backStack = ArrayList<Screen>()
    var tab by mutableStateOf(EditorTab.Film)

    var source by mutableStateOf<Bitmap?>(null); private set
    var preview by mutableStateOf<Bitmap?>(null); private set
    /** The unedited photo with the current crop/rotation, for hold-to-compare. */
    var original by mutableStateOf<Bitmap?>(null); private set
    /** Histogram of the current preview, shown behind the tone curve. */
    var histogram by mutableStateOf<ImageEngine.Histogram?>(null); private set
    /** Curve eyedropper: when on, tapping the preview samples a colour. */
    var curvePicker by mutableStateOf(false); private set
    /** Last sampled colour (r, g, b in 0..1), drawn as markers on the curves. */
    var curveSample by mutableStateOf<FloatArray?>(null); private set

    fun togglePicker() {
        curvePicker = !curvePicker
        if (!curvePicker) curveSample = null
    }

    /** Samples the preview at a position given as fractions (0..1) of the image. */
    fun sampleAt(fx: Float, fy: Float) {
        val bmp = preview ?: return
        val x = (fx * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val y = (fy * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        // Average a small 5×5 area so noise and grain don't throw the reading off.
        var r = 0f; var g = 0f; var b = 0f; var n = 0
        for (dy in -2..2) for (dx in -2..2) {
            val px = bmp.getPixel((x + dx).coerceIn(0, bmp.width - 1), (y + dy).coerceIn(0, bmp.height - 1))
            r += (px shr 16) and 255; g += (px shr 8) and 255; b += px and 255; n++
        }
        curveSample = floatArrayOf(r / n / 255f, g / n / 255f, b / n / 255f)
    }
    var loading by mutableStateOf(false); private set
    var rendering by mutableStateOf(false); private set
    var params by mutableStateOf(EditParams()); private set
    val simThumbs = mutableStateListOf<Bitmap?>()
    /** Small preview of each recipe's own look on the current photo, by recipe id. */
    val recipeThumbs = mutableStateMapOf<Long, Bitmap>()

    var recipes by mutableStateOf<List<Recipe>>(emptyList()); private set
    var message by mutableStateOf<String?>(null)
    var exportSettings by mutableStateOf(ExportSettings())
    var exporting by mutableStateOf(false); private set
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    val hasImage: Boolean get() = source != null

    private var sourceUri: Uri? = null
    private var stats = ImageStats.NEUTRAL
    private var thumbSource: Bitmap? = null
    private var committed = EditParams()
    private val undoStack = ArrayDeque<EditParams>()
    private val redoStack = ArrayDeque<EditParams>()
    private var renderJob: Job? = null
    private var thumbJob: Job? = null
    private var recipeThumbJob: Job? = null

    init {
        recipes = store.load()
        repeat(FILM_SIMS.size) { simThumbs.add(null) }
    }

    // ------------------------------------------------------------ navigation

    fun navigate(to: Screen) {
        if (to == screen) return
        backStack.add(screen)
        screen = to
    }

    /** Returns false when there is nothing to go back to (the system closes the app). */
    fun back(): Boolean {
        if (backStack.isEmpty()) {
            if (screen == Screen.Library) return false
            screen = Screen.Library
            return true
        }
        screen = backStack.removeAt(backStack.lastIndex)
        return true
    }

    // ---------------------------------------------------------------- images

    fun openImage(uri: Uri) {
        loading = true
        preview = null
        original = null
        navigate(Screen.Editor)
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val bmp = withContext(Dispatchers.IO) {
                try { BitmapIO.decode(ctx, uri, PREVIEW_EDGE) } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
            }
            if (bmp == null) {
                loading = false
                message = "Couldn't open this photo"
                if (source == null) back()
                return@launch
            }
            sourceUri = uri
            source = bmp
            thumbSource = withContext(Dispatchers.Default) {
                val s = THUMB_EDGE / max(bmp.width, bmp.height).toFloat()
                Bitmap.createScaledBitmap(
                    bmp, (bmp.width * s).roundToInt().coerceAtLeast(1), (bmp.height * s).roundToInt().coerceAtLeast(1), true
                )
            }
            stats = withContext(Dispatchers.Default) { ImageEngine.computeStats(bmp) }
            // Keep the current look (handy for editing a series), reset the crop.
            params = params.copy(aspect = 0, rotation = 0, flipH = false)
            committed = params
            undoStack.clear(); redoStack.clear(); updateUndo()
            loading = false
            requestRender()
            renderThumbs()
            renderRecipeThumbs()
        }
    }

    private fun requestRender() {
        val src = source ?: return
        val p = params
        val st = stats
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            rendering = true
            try {
                delay(12) // coalesce rapid slider moves
                val geo = withContext(Dispatchers.Default) { ImageEngine.applyGeometry(src, p) }
                val out = withContext(Dispatchers.Default) { ImageEngine.render(src, p, st) }
                original = geo
                preview = out
                histogram = withContext(Dispatchers.Default) { ImageEngine.histogram(out) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                message = "Not enough memory to update the preview"
            } catch (e: Exception) {
                message = "Preview failed: ${e.message ?: "unknown error"}"
            } finally {
                if (coroutineContext[Job] == renderJob) rendering = false
            }
        }
    }

    private fun renderThumbs() {
        val src = thumbSource ?: return
        val base = params.copy(simStrength = 1f, aspect = 0, rotation = 0, flipH = false)
        val st = stats
        thumbJob?.cancel()
        thumbJob = viewModelScope.launch {
            delay(150)
            try {
                for (i in FILM_SIMS.indices) {
                    val bmp = withContext(Dispatchers.Default) { ImageEngine.render(src, base.copy(sim = i), st, detail = false) }
                    simThumbs[i] = bmp
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Thumbnails are optional; keep the swatch colours if they can't be made.
            }
        }
    }

    /** Renders every recipe's own look on the current photo (not the current settings). */
    private fun renderRecipeThumbs() {
        val src = thumbSource ?: return
        val st = stats
        val list = recipes
        recipeThumbJob?.cancel()
        recipeThumbJob = viewModelScope.launch {
            delay(300)
            try {
                for (r in list) {
                    val bmp = withContext(Dispatchers.Default) {
                        ImageEngine.render(src, r.params.withoutGeometry(), st, detail = false)
                    }
                    recipeThumbs[r.id] = bmp
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Optional: cards fall back to a colour swatch.
            }
        }
    }

    // ---------------------------------------------------------------- params

    /** Live change (e.g. while dragging a slider). Call [commit] when the gesture ends. */
    fun updateParams(p: EditParams) {
        if (p == params) return
        params = p
        requestRender()
    }

    fun commit() {
        if (params == committed) return
        undoStack.addLast(committed)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        committed = params
        updateUndo()
        renderThumbs()
    }

    /** Discrete change: apply and record in history. */
    fun change(p: EditParams) {
        updateParams(p)
        commit()
    }

    fun undo() {
        commit()
        if (undoStack.isEmpty()) return
        redoStack.addLast(params)
        params = undoStack.removeLast()
        committed = params
        updateUndo(); requestRender(); renderThumbs()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(params)
        params = redoStack.removeLast()
        committed = params
        updateUndo(); requestRender(); renderThumbs()
    }

    private fun updateUndo() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    // --------------------------------------------------------------- recipes

    fun saveRecipe(name: String) {
        val r = Recipe(System.currentTimeMillis(), name.trim().ifEmpty { "My recipe" }.take(60), params.withoutGeometry())
        recipes = listOf(r) + recipes
        store.save(recipes)
        renderRecipeThumbs()
        message = "Saved recipe “${r.name}”"
    }

    fun applyRecipe(r: Recipe) {
        change(r.params.withGeometryOf(params))
        if (hasImage) {
            navigate(Screen.Editor)
        } else {
            message = "“${r.name}” selected — open a photo to use it"
        }
    }

    fun deleteRecipe(r: Recipe) {
        recipes = recipes.filterNot { it.id == r.id }
        store.save(recipes)
        recipeThumbs.remove(r.id)
    }

    fun importRecipe(text: String?) {
        val r = text?.let { RecipeStore.fromShareText(it) }
        if (r == null) {
            message = "No recipe found on the clipboard"
            return
        }
        recipes = listOf(r) + recipes
        store.save(recipes)
        renderRecipeThumbs()
        message = "Imported “${r.name}”"
    }

    // ---------------------------------------------------------------- export

    fun export(share: Boolean) {
        val uri = sourceUri ?: return
        if (exporting) return
        val s = exportSettings
        val p = params
        val st = stats
        exporting = true
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val limit = BitmapIO.maxExportLongEdge()
                val edge = min(EXPORT_SIZES[s.size] ?: limit, limit)
                val full = withContext(Dispatchers.IO) { BitmapIO.decode(ctx, uri, edge) }
                    ?: error("Couldn't read the original photo")
                var out = withContext(Dispatchers.Default) { ImageEngine.render(full, p, st) }
                if (s.border) out = withContext(Dispatchers.Default) { BitmapIO.addBorder(out, p.caption()) }
                val json = if (s.embedRecipe && s.format == ExportFormat.JPEG) p.toJson().toString() else null
                if (share) {
                    withContext(Dispatchers.IO) { BitmapIO.share(ctx, out, s.format, s.quality, uri, json, s.keepLocation) }
                } else {
                    withContext(Dispatchers.IO) { BitmapIO.saveToGallery(ctx, out, s.format, s.quality, json, uri, s.keepLocation) }
                        ?: error("Couldn't save the photo")
                    message = "Saved to Pictures/FilmDesk (${out.width}×${out.height})"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                message = "Not enough memory for this size — choose a smaller export size"
            } catch (e: Exception) {
                message = e.message ?: "Export failed"
            } finally {
                exporting = false
            }
        }
    }

    companion object {
        /** Preview long edge: sharp on phones and foldables, fast enough on low-end chips. */
        const val PREVIEW_EDGE = 1440
        const val THUMB_EDGE = 200
    }
}
