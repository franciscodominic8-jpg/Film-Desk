package com.filmdesk.app.ui

import android.app.Application
import android.content.Intent
import android.provider.OpenableColumns
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filmdesk.app.data.Recipe
import com.filmdesk.app.data.RecipeStore
import com.filmdesk.app.data.StudioItem
import com.filmdesk.app.data.StudioStore
import com.filmdesk.app.engine.BitmapIO
import com.filmdesk.app.engine.ImageEngine
import com.filmdesk.app.engine.ImageStats
import com.filmdesk.app.model.EXPORT_SIZES
import com.filmdesk.app.model.EditParams
import com.filmdesk.app.model.ExportFormat
import com.filmdesk.app.model.FILM_SIMS
import com.filmdesk.app.model.ASPECT_FREE
import com.filmdesk.app.model.ASPECT_ORIGINAL
import com.filmdesk.app.model.CropGuide
import com.filmdesk.app.model.CropHandle
import com.filmdesk.app.model.PastePart
import com.filmdesk.app.model.centeredRect
import com.filmdesk.app.model.cropBase
import com.filmdesk.app.model.cropRect
import com.filmdesk.app.model.lockRatio
import com.filmdesk.app.model.rotateRect
import com.filmdesk.app.model.withCropRect
import com.filmdesk.app.model.pasteFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Library is the Studio (imported photos); Import is the gallery picker. */
enum class Screen { Library, Import, Editor, Recipes, Export }

enum class EditorTab(val label: String) { Film("Film"), QMenu("Q Menu"), Pro("Pro"), Crop("Crop") }

data class ExportSettings(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 92,
    val size: Int = 0,
    val embedRecipe: Boolean = true,
    /** Copy GPS location from the original photo (off by default for privacy). */
    val keepLocation: Boolean = false,
)

/** Edits on the clipboard, from the photo called [fromName]. */
data class CopiedEdits(val params: EditParams, val fromName: String, val fromId: Long?)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val store = RecipeStore(app)
    private val studioStore = StudioStore(app)

    // ---------------------------------------------------------------- studio

    /** Imported photos, newest first. Each keeps its own edits. */
    var studio by mutableStateOf<List<StudioItem>>(emptyList()); private set
    /** Edited thumbnails, by studio id. */
    val studioThumbs = mutableStateMapOf<Long, Bitmap>()
    /** Studio photos that can't be read any more (deleted from the phone or access lost). */
    val studioMissing = mutableStateMapOf<Long, Boolean>()
    /** The studio photo open in the editor. */
    var currentId by mutableStateOf<Long?>(null); private set
    var selection by mutableStateOf<Set<Long>>(emptySet()); private set
    val selecting: Boolean get() = selection.isNotEmpty()
    var clipboard by mutableStateOf<CopiedEdits?>(null); private set
    /** Photos that just received pasted edits (outlined for a moment). */
    var justPasted by mutableStateOf<Set<Long>>(emptySet()); private set
    var batchProgress by mutableStateOf<String?>(null); private set
    /** A snackbar "Undo" offer: shown when [message] equals the first value. */
    var undoOffer by mutableStateOf<Pair<String, () -> Unit>?>(null)

    private var studioUndo: List<StudioItem>? = null
    private val thumbGate = Semaphore(2)
    private val studioThumbJobs = HashMap<Long, Job>()
    /** Thumbnail ids in the order they were loaded, to keep only the most recent in memory. */
    private val thumbOrder = LinkedHashSet<Long>()
    private var saveJob: Job? = null
    private var pastedJob: Job? = null

    var screen by mutableStateOf(Screen.Library); private set
    private val backStack = ArrayList<Screen>()
    private var tabState by mutableStateOf(EditorTab.Film)
    /** The Crop tab shows the whole upright photo with the frame on top, so it renders differently. */
    var tab: EditorTab
        get() = tabState
        set(v) {
            val was = tabState
            tabState = v
            if ((was == EditorTab.Crop) != (v == EditorTab.Crop)) requestRender()
        }

    // ------------------------------------------------------------------ crop
    var cropGuide by mutableStateOf(CropGuide.Thirds)
    /** Direction of the spiral / triangle guide (tap the chip again to turn it). */
    var guideTurn by mutableIntStateOf(0)
    /** The frame handle being dragged, or null. */
    var cropHandle by mutableStateOf<CropHandle?>(null)
    var straightening by mutableStateOf(false)
    /** Long edge of the full-size photo, for showing the crop size in pixels. */
    var fullLongEdge by mutableIntStateOf(0); private set
    /** The settings the current preview was drawn with (the crop view waits for an upright one). */
    var previewFor by mutableStateOf<EditParams?>(null); private set
    /** Bumped by Reset so the crop view also resets its zoom. */
    var cropResets by mutableIntStateOf(0); private set

    fun pickGuide(g: CropGuide) {
        if (g == cropGuide) guideTurn = (guideTurn + 1) % g.turns else { cropGuide = g; guideTurn = 0 }
    }

    /** Size of the photo the crop frame is drawn on (after 90° turns). */
    private fun cropBaseSize(): Pair<Int, Int>? {
        val src = source ?: return null
        return if (params.rotation % 180 == 0) src.width to src.height else src.height to src.width
    }

    fun setCropRatio(index: Int) {
        val (w, h) = cropBaseSize() ?: return
        val p = params
        val cur = p.cropRect(w, h)
        var next = p.copy(aspect = index, aspectFlip = if (index == p.aspect) p.aspectFlip else false)
        val ratio = next.lockRatio(w, h)
        if (ratio != null) {
            next = next.withCropRect(centeredRect(ratio * h / w, (cur[0] + cur[2]) / 2f, (cur[1] + cur[3]) / 2f))
        }
        change(next)
    }

    fun swapCropRatio() {
        val (w, h) = cropBaseSize() ?: return
        val p = params
        val cur = p.cropRect(w, h)
        val cx = (cur[0] + cur[2]) / 2f
        val cy = (cur[1] + cur[3]) / 2f
        if (p.aspect == ASPECT_FREE) {
            // Free: the frame's width and height (in pixels) swap.
            val pxW = (cur[2] - cur[0]) * w
            val pxH = (cur[3] - cur[1]) * h
            var fw = pxH / w
            var fh = pxW / h
            val k = minOf(1f, 1f / fw, 1f / fh)
            fw *= k; fh *= k
            val l = (cx - fw / 2f).coerceIn(0f, 1f - fw)
            val t = (cy - fh / 2f).coerceIn(0f, 1f - fh)
            change(p.withCropRect(floatArrayOf(l, t, l + fw, t + fh)))
            return
        }
        val next = p.copy(aspectFlip = !p.aspectFlip)
        val ratio = next.lockRatio(w, h) ?: return
        change(next.withCropRect(centeredRect(ratio * h / w, cx, cy)))
    }

    fun rotateCrop(cw: Boolean) {
        val (w, h) = cropBaseSize() ?: return
        val p = params
        val r = rotateRect(p.cropRect(w, h), cw)
        val turnsRatio = p.aspect != ASPECT_FREE && p.aspect != ASPECT_ORIGINAL && p.aspect != 1
        change(
            p.copy(
                rotation = (p.rotation + if (cw) 90 else 270) % 360,
                aspectFlip = if (turnsRatio) !p.aspectFlip else p.aspectFlip,
            ).withCropRect(r)
        )
    }

    fun flipCrop() {
        val (w, h) = cropBaseSize() ?: return
        val p = params
        val r = p.cropRect(w, h)
        // flipH mirrors before the 90° turn, so on a turned photo it looks like a vertical flip.
        val m = if (p.rotation % 180 == 0) floatArrayOf(1f - r[2], r[1], 1f - r[0], r[3])
        else floatArrayOf(r[0], 1f - r[3], r[2], 1f - r[1])
        change(p.copy(flipH = !p.flipH, straighten = -p.straighten).withCropRect(m))
    }

    fun resetCrop() {
        cropResets++
        change(params.withGeometryOf(EditParams()))
    }

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

    /** True while the full-screen Curve editor is open. */
    var curveMode by mutableStateOf(false); private set

    fun openCurve() { curveMode = true }

    fun closeCurve() {
        pickerOff()
        curveMode = false
    }

    /** Where the eyedropper was tapped, as fractions of the (cropped) image. */
    private var samplePos: Pair<Float, Float>? = null

    fun togglePicker() {
        curvePicker = !curvePicker
        if (!curvePicker) { curveSample = null; samplePos = null }
    }

    fun pickerOff() {
        if (curvePicker) togglePicker()
    }

    /** Eyedropper tap at a position given as fractions (0..1) of the image. */
    fun sampleAt(fx: Float, fy: Float) {
        samplePos = fx to fy
        resample()
    }

    /** Reads the colour at the eyedropper position as it enters the curves (like Resolve). */
    private fun resample() {
        val pos = samplePos ?: return
        val geo = original ?: return
        val p = params
        val st = stats
        viewModelScope.launch {
            val v = withContext(Dispatchers.Default) {
                try { ImageEngine.samplePreCurve(geo, p, st, pos.first, pos.second) } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }
            }
            if (samplePos == pos && v != null) curveSample = v
        }
    }
    var loading by mutableStateOf(false); private set
    var rendering by mutableStateOf(false); private set
    var params by mutableStateOf(EditParams()); private set
    val simThumbs = mutableStateListOf<Bitmap?>()
    /** Small preview of each recipe's own look on the current photo, by recipe id. */
    val recipeThumbs = mutableStateMapOf<Long, Bitmap>()

    var recipes by mutableStateOf<List<Recipe>>(emptyList()); private set
    var message by mutableStateOf<String?>(null)
    private val exportPrefs = app.getSharedPreferences("export", android.content.Context.MODE_PRIVATE)
    private var exportState by mutableStateOf(loadExportSettings())
    /** Remembered between app starts. */
    var exportSettings: ExportSettings
        get() = exportState
        set(v) {
            exportState = v
            exportPrefs.edit()
                .putString("format", v.format.name).putInt("quality", v.quality).putInt("size", v.size)
                .putBoolean("embedRecipe", v.embedRecipe).putBoolean("keepLocation", v.keepLocation)
                .apply()
        }

    private fun loadExportSettings(): ExportSettings {
        val d = ExportSettings()
        return try {
            ExportSettings(
                format = ExportFormat.entries.firstOrNull { it.name == exportPrefs.getString("format", null) } ?: d.format,
                quality = exportPrefs.getInt("quality", d.quality).coerceIn(60, 100),
                size = exportPrefs.getInt("size", d.size).coerceIn(0, EXPORT_SIZES.lastIndex),
                embedRecipe = exportPrefs.getBoolean("embedRecipe", d.embedRecipe),
                keepLocation = exportPrefs.getBoolean("keepLocation", d.keepLocation),
            )
        } catch (e: Exception) {
            d
        }
    }

    /** Recipes opened from the Studio apply to these selected photos. */
    var recipeTargets by mutableStateOf<Set<Long>>(emptySet()); private set
    var recipesFromStudio by mutableStateOf(false); private set

    fun openRecipes() {
        recipesFromStudio = screen != Screen.Editor
        recipeTargets = if (recipesFromStudio) selection else emptySet()
        navigate(Screen.Recipes)
    }
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
        studio = studioStore.load()
        val loaded = studio
        viewModelScope.launch(Dispatchers.IO) { studioStore.cleanCopies(loaded) }
        // Thumbnails load lazily as grid cells appear (see ensureThumb).
    }

    // ------------------------------------------------------------ navigation

    fun navigate(to: Screen) {
        if (to == screen) return
        if (screen == Screen.Editor) flushSave()
        backStack.add(screen)
        screen = to
    }

    /** Returns false when there is nothing to go back to (the system closes the app). */
    fun back(): Boolean {
        if (screen == Screen.Editor) flushSave()
        if (backStack.isEmpty()) {
            if (screen == Screen.Library) return false
            screen = Screen.Library
            return true
        }
        screen = backStack.removeAt(backStack.lastIndex)
        return true
    }

    // ---------------------------------------------------------------- images

    /**
     * Opens a photo shared to the app ("Share" / "Edit with"). Those links stop working when
     * the app closes, so a private copy is added to the Studio (once per photo).
     */
    fun openImage(uri: Uri) {
        studio.firstOrNull { it.source == uri.toString() || it.uri == uri }?.let { openItem(it); return }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val copy = withContext(Dispatchers.IO) { studioStore.copyIn(ctx, uri) }
            if (copy == null) {
                message = "Couldn't open this photo"
                return@launch
            }
            val item = importPhotos(listOf(copy), quiet = true, source = uri.toString()).firstOrNull() ?: return@launch
            openItem(item)
        }
    }

    /** Opens a Studio photo with its own saved edits (a new photo starts with none). */
    fun openItem(item: StudioItem) {
        flushSave()
        clearSelection()
        val start = studio.firstOrNull { it.id == item.id }?.params ?: EditParams()
        loadImage(item.uri, item.id, start)
    }

    private fun loadImage(uri: Uri, id: Long, start: EditParams) {
        loading = true
        preview = null
        previewFor = null
        original = null
        navigate(Screen.Editor)
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val bmp = withContext(Dispatchers.IO) {
                try { BitmapIO.decode(ctx, uri, PREVIEW_EDGE) } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
            }
            // Full size first, so the crop size shows real pixels from the start.
            if (bmp != null) fullLongEdge = withContext(Dispatchers.IO) { BitmapIO.longEdge(ctx, uri) }
            if (bmp == null) {
                loading = false
                message = "Couldn't open this photo"
                studioMissing[id] = true
                back()
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
            currentId = id
            params = start
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
        val p = if (tabState == EditorTab.Crop) params.cropBase() else params
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
                previewFor = p
                histogram = withContext(Dispatchers.Default) { ImageEngine.histogram(out) }
                resample()
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
        val base = params.withoutGeometry().copy(simStrength = 1f)
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
        val sameImage = tabState == EditorTab.Crop && p.cropBase() == params.cropBase()
        params = p
        if (sameImage) return // the crop view draws the frame and straighten itself
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
        scheduleSave()
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
        updateUndo(); requestRender(); renderThumbs(); scheduleSave()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(params)
        params = redoStack.removeLast()
        committed = params
        updateUndo(); requestRender(); renderThumbs(); scheduleSave()
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
        if (!recipesFromStudio) {
            change(r.params.withGeometryOf(params))
            back()
            return
        }
        val targets = recipeTargets.filter { id -> studio.any { it.id == id } }.toSet()
        if (targets.isEmpty()) {
            message = "Select photos in the Studio first (long-press a photo), then tap Recipe"
            return
        }
        val before = studio
        val updated = studio.map { item ->
            if (item.id !in targets) item else {
                val p = r.params.withGeometryOf(item.params ?: EditParams())
                item.copy(params = p.takeIf { it != EditParams() })
            }
        }
        updateStudio(updated)
        updated.filter { it.id in targets }.forEach { refreshThumb(it) }
        clearSelection()
        recipeTargets = emptySet()
        syncCurrent()
        back()
        offerUndo(
            if (targets.size == 1) "Applied “${r.name}” to 1 photo" else "Applied “${r.name}” to ${targets.size} photos",
            before,
        )
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

    /** Renders one photo at export size and saves or shares it. Returns the output size. */
    private suspend fun exportOne(uri: Uri, p: EditParams, knownStats: ImageStats?, s: ExportSettings, share: Boolean): String {
        val ctx = getApplication<Application>()
        val limit = BitmapIO.maxExportLongEdge()
        val edge = min(EXPORT_SIZES[s.size] ?: limit, limit)
        val full = withContext(Dispatchers.IO) { BitmapIO.decode(ctx, uri, edge) }
            ?: error("Couldn't read the original photo")
        val st = knownStats ?: withContext(Dispatchers.Default) { ImageEngine.computeStats(full) }
        val out = withContext(Dispatchers.Default) { ImageEngine.render(full, p, st) }
        val json = if (s.embedRecipe && s.format == ExportFormat.JPEG) p.toJson().toString() else null
        if (share) {
            withContext(Dispatchers.IO) { BitmapIO.share(ctx, out, s.format, s.quality, uri, json, s.keepLocation) }
        } else {
            withContext(Dispatchers.IO) { BitmapIO.saveToGallery(ctx, out, s.format, s.quality, json, uri, s.keepLocation) }
                ?: error("Couldn't save the photo")
        }
        return "${out.width}×${out.height}"
    }

    fun export(share: Boolean) {
        val uri = sourceUri ?: return
        if (exporting) return
        val s = exportSettings
        val p = params
        val st = stats
        exporting = true
        viewModelScope.launch {
            try {
                val size = exportOne(uri, p, st, s, share)
                if (!share) message = "Saved to Pictures/FilmDesk ($size)"
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

    // ----------------------------------------------------------- studio ops

    private fun updateStudio(list: List<StudioItem>) {
        studio = list
        val snapshot = list
        viewModelScope.launch(Dispatchers.IO) { synchronized(studioStore) { studioStore.save(snapshot) } }
    }

    /** Adds photos to the Studio (skipping ones already there). Returns the items for [uris]. */
    fun importPhotos(uris: List<Uri>, quiet: Boolean = false, source: String? = null): List<StudioItem> {
        if (uris.isEmpty()) return emptyList()
        val cr = getApplication<Application>().contentResolver
        val byUri = studio.associateBy { it.uri.toString() }
        var nextId = max(System.currentTimeMillis(), (studio.maxOfOrNull { it.id } ?: 0L) + 1)
        val fresh = ArrayList<StudioItem>()
        val result = uris.distinct().map { uri ->
            byUri[uri.toString()] ?: run {
                // Keeps access after a restart for photos picked with the system picker.
                try { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
                StudioItem(id = nextId++, uri = uri, source = source).also { fresh += it }
            }
        }
        if (fresh.isNotEmpty()) {
            updateStudio(fresh + studio)
            fresh.forEach { refreshThumb(it) }
        }
        if (!quiet) message = when (fresh.size) {
            0 -> "Already in your Studio"
            1 -> "Imported 1 photo"
            else -> "Imported ${fresh.size} photos"
        }
        return result
    }

    /** Draws a Studio thumbnail with that photo's own edits, and caches it on disk. */
    private fun refreshThumb(item: StudioItem, useCache: Boolean = false) {
        studioThumbJobs[item.id]?.cancel()
        studioThumbJobs[item.id] = viewModelScope.launch {
            thumbGate.withPermit {
                val ctx = getApplication<Application>()
                if (useCache) {
                    val cached = withContext(Dispatchers.IO) { studioStore.readThumb(item.id) }
                    if (cached != null) {
                        // Still on the phone? (It may have been deleted from the Gallery.)
                        val exists = withContext(Dispatchers.IO) {
                            try { ctx.contentResolver.openFileDescriptor(item.uri, "r")?.use { true } ?: false } catch (e: Exception) { false }
                        }
                        if (exists) studioMissing.remove(item.id) else studioMissing[item.id] = true
                        putThumb(item.id, cached)
                        return@launch
                    }
                }
                val src = withContext(Dispatchers.IO) {
                    try { BitmapIO.decode(ctx, item.uri, STUDIO_THUMB_EDGE) } catch (e: Throwable) { null }
                }
                if (src == null) { studioMissing[item.id] = true; return@launch }
                studioMissing.remove(item.id)
                val p = item.params
                val out = if (p == null) src else withContext(Dispatchers.Default) {
                    try {
                        ImageEngine.render(src, p, ImageEngine.computeStats(src), detail = false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        src
                    }
                }
                putThumb(item.id, out)
                withContext(Dispatchers.IO) { studioStore.writeThumb(item.id, out) }
            }
        }
    }

    private fun putThumb(id: Long, bmp: Bitmap) {
        studioThumbs[id] = bmp
        thumbOrder.remove(id)
        thumbOrder.add(id)
        while (thumbOrder.size > MAX_THUMBS_IN_MEMORY) {
            val oldest = thumbOrder.first()
            thumbOrder.remove(oldest)
            studioThumbs.remove(oldest)
        }
    }

    /** Called by a grid cell that needs its thumbnail. */
    fun ensureThumb(id: Long) {
        if (studioThumbs.containsKey(id) || studioThumbJobs[id]?.isActive == true) return
        studio.firstOrNull { it.id == id }?.let { refreshThumb(it, useCache = true) }
    }

    private fun scheduleSave() {
        val id = currentId ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            saveCurrent(id)
        }
    }

    /** Writes pending editor changes to the open Studio photo right away. */
    fun flushSave() {
        saveJob?.cancel()
        currentId?.let { saveCurrent(it) }
    }

    private fun saveCurrent(id: Long) {
        if (id != currentId || source == null) return
        val stored = params.takeIf { it != EditParams() }
        val item = studio.firstOrNull { it.id == id } ?: return
        if (item.params == stored) return
        val updated = item.copy(params = stored)
        updateStudio(studio.map { if (it.id == id) updated else it })
        refreshThumb(updated)
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "file") {
            // Private copy: "<time>_<original name>".
            return uri.lastPathSegment?.substringAfter('_')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "this photo"
        }
        val name = try {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
        return name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "this photo"
    }

    fun toggleSelect(id: Long) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun selectAll(ids: Collection<Long>) { selection = ids.toSet() }

    fun clearSelection() { selection = emptySet() }

    fun copyEdits(id: Long) {
        val item = studio.firstOrNull { it.id == id } ?: return
        clipboard = CopiedEdits(item.params ?: EditParams(), displayName(item.uri), id)
        message = "Edits copied — select photos and tap Paste"
    }

    /** Copy in the editor: the open photo's current edits. */
    fun copyCurrent() {
        val item = studio.firstOrNull { it.id == currentId }
        clipboard = CopiedEdits(params, item?.let { displayName(it.uri) } ?: "this photo", currentId)
        message = "Edits copied"
    }

    fun clearClipboard() { clipboard = null }

    /** Paste in the editor: applies the chosen parts to the open photo (undo works as usual). */
    fun pasteCurrent(parts: Set<PastePart>) {
        val c = clipboard ?: return
        change(params.pasteFrom(c.params, parts))
        message = "Edits pasted"
    }

    private fun offerUndo(text: String, before: List<StudioItem>) {
        studioUndo = before
        undoOffer = text to { undoStudio() }
        message = text
    }

    fun undoStudio() {
        val before = studioUndo ?: return
        studioUndo = null
        val now = studio.associateBy { it.id }
        updateStudio(before)
        before.filter { now[it.id] != it }.forEach { refreshThumb(it) }
        syncCurrent()
    }

    /**
     * If a Studio action changed the photo that is loaded in the editor, load its new edits
     * so a later change (e.g. applying a recipe) doesn't bring back the old ones.
     */
    private fun syncCurrent() {
        val id = currentId ?: return
        if (source == null) return
        val item = studio.firstOrNull { it.id == id } ?: return
        val p = item.params ?: EditParams()
        if (p == params) return
        saveJob?.cancel()
        params = p
        committed = p
        undoStack.clear(); redoStack.clear(); updateUndo()
        requestRender(); renderThumbs()
    }

    /** The photos Paste will change: the selection, without the photo the edits came from. */
    val pasteTargets: Set<Long>
        get() {
            val from = clipboard?.fromId
            val t = if (from == null) selection else selection - from
            return t.ifEmpty { selection }
        }

    fun pasteToSelection(parts: Set<PastePart>) {
        val c = clipboard ?: return
        val targets = pasteTargets
        if (targets.isEmpty() || parts.isEmpty()) return
        val before = studio
        val updated = studio.map { item ->
            if (item.id !in targets) item else {
                val p = (item.params ?: EditParams()).pasteFrom(c.params, parts)
                item.copy(params = p.takeIf { it != EditParams() })
            }
        }
        updateStudio(updated)
        updated.filter { it.id in targets }.forEach { refreshThumb(it) }
        clearSelection()
        syncCurrent()
        justPasted = targets
        pastedJob?.cancel()
        pastedJob = viewModelScope.launch { delay(4000); justPasted = emptySet() }
        offerUndo(if (targets.size == 1) "Pasted to 1 photo" else "Pasted to ${targets.size} photos", before)
    }

    fun resetSelection() {
        val targets = selection
        if (targets.isEmpty()) return
        val before = studio
        val updated = studio.map { if (it.id in targets) it.copy(params = null) else it }
        updateStudio(updated)
        updated.filter { it.id in targets }.forEach { refreshThumb(it) }
        clearSelection()
        syncCurrent()
        offerUndo("Reset ${targets.size} photo" + if (targets.size == 1) "" else "s", before)
    }

    fun removeSelection() {
        val targets = selection
        if (targets.isEmpty()) return
        val before = studio
        updateStudio(studio.filterNot { it.id in targets })
        targets.forEach { id ->
            studioThumbJobs.remove(id)?.cancel()
            studioThumbs.remove(id)
            thumbOrder.remove(id)
            studioMissing.remove(id)
            viewModelScope.launch(Dispatchers.IO) { studioStore.deleteThumb(id) }
        }
        if (currentId in targets) { flushSave(); currentId = null }
        clearSelection()
        offerUndo("Removed ${targets.size} from Studio (originals are kept)", before)
    }

    /** Saves every selected photo with its own edits, using the current export settings. */
    fun exportSelection() {
        if (exporting) return
        val items = studio.filter { it.id in selection }
        if (items.isEmpty()) return
        val s = exportSettings
        exporting = true
        clearSelection()
        viewModelScope.launch {
            var ok = 0
            try {
                for ((i, item) in items.withIndex()) {
                    batchProgress = "Exporting ${i + 1} of ${items.size}…"
                    try {
                        exportOne(item.uri, item.params ?: EditParams(), null, s, share = false)
                        ok++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // Skip photos that can't be read or don't fit in memory.
                    }
                }
            } finally {
                batchProgress = null
                exporting = false
            }
            message = if (ok == items.size) "Saved $ok to Pictures/FilmDesk" else "Saved $ok of ${items.size} to Pictures/FilmDesk"
        }
    }

    companion object {
        const val STUDIO_THUMB_EDGE = 360
        /** ~0.4 MB each: about 60 MB at most, whatever the size of the Studio. */
        const val MAX_THUMBS_IN_MEMORY = 150
        /** Preview long edge: sharp on phones and foldables, fast enough on low-end chips. */
        const val PREVIEW_EDGE = 1440
        const val THUMB_EDGE = 200
    }
}
