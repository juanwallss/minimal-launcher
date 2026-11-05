package com.jparedes.minimallauncher

import android.Manifest
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.view.WindowManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.CalendarContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.offset
import android.appwidget.AppWidgetManager
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.time.Instant
import java.time.ZoneId
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.provider.MediaStore
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import kotlin.math.abs
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val REQ_PICK = 101
private const val REQ_BIND = 102
private const val REQ_CONFIG = 103

data class App(val label: String, val pkg: String, val activity: String) {
    val id = "$pkg/$activity"
    val letter: String = label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"
}

// Palette / type — tweak here
// Follows the phone's dark mode: warm off-white on near-black, or the inverse by day
internal val Bg @Composable get() = if (isSystemInDarkTheme()) Color(0xFF0A0A0A) else Color(0xFFF4F2EE)
internal val Fg @Composable get() = if (isSystemInDarkTheme()) Color(0xFFE8E6E1) else Color(0xFF1C1B1A)
internal val Dim @Composable get() = when {
    Look.background == Look.BG_NONE -> if (isSystemInDarkTheme()) Color(0xFF6B6965) else Color(0xFF8E8B86)
    else -> if (isSystemInDarkTheme()) Color(0xFFB5B2AC) else Color(0xFF4F4C48) // stronger over images
}
/** Soft halo in the theme color behind text, only when there's an image behind it. */
internal val TextShadow @Composable get() = if (Look.background == Look.BG_NONE) null else Shadow(Bg.copy(alpha = 0.75f), blurRadius = 10f)

// Jost (variable font, OFL) — one file, instantiated at the weights we use
@OptIn(ExperimentalTextApi::class)
internal val Thin = FontFamily(
    listOf(FontWeight.Thin, FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal).map {
        Font(R.font.jost, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
    }
)
internal val RowText @Composable get() = TextStyle(color = Fg, fontSize = Look.textSp.sp, fontFamily = Thin, fontWeight = FontWeight.Light, letterSpacing = 0.5.sp, shadow = TextShadow)
internal val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
internal val SmallText @Composable get() = TextStyle(color = Dim, fontSize = 15.sp, fontFamily = Thin, letterSpacing = 1.sp, shadow = TextShadow)

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf(emptyList<App>())
    private var letter by mutableStateOf<String?>(null)
    private var query by mutableStateOf<String?>(null)   // null = not searching
    private var menuFor by mutableStateOf<String?>(null) // app id whose long-press menu is open
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }
    private var favorites by mutableStateOf(emptyList<String>()) // ordered app ids
    private var resumes by mutableStateOf(0) // bumps on every resume so the status line refreshes
    private val askCalendar = registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumes++ }

    // --- Widget area: one paged space. Pages are "w:<appWidgetId>" (other apps) or "n:<kind>" (built-in, see NativeWidgets.kt)
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val widgetHost by lazy {
        LauncherWidgetHost(applicationContext, onLongPress = { widgetMenu = "w:$it" }) { dx, dy ->
            when {
                kotlin.math.abs(dx) > kotlin.math.abs(dy) -> pageBy(if (dx < 0) 1 else -1)
                dy > 0 -> runGesture("down")
                else -> runGesture("up")
            }
        }
    }
    private var pages by mutableStateOf(emptyList<String>())
    private var widgetMenu by mutableStateOf<String?>(null) // page whose long-press menu is open
    private var choosingWidget by mutableStateOf(false)    // inline "which widget?" row is open
    private var pendingWidget = -1
    private var pageBy: (Int) -> Unit = {} // set by the pager; widget views can't reach Compose state directly

    // --- Settings & background
    private var settingsOpen by mutableStateOf(false)
    private var bgImage by mutableStateOf<ImageBitmap?>(null)
    private val bgFile by lazy { File(filesDir, "background.jpg") }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { importBackground(uri) }.getOrNull() }
            if (bmp != null) {
                bgImage = bmp.asImageBitmap()
                Look.background = Look.BG_PHOTO; Look.save()
                applyWindowBackground()
            }
        }
    }

    /** Copies the chosen photo into app storage, scaled to just cover the screen (picker URIs don't last). */
    private fun importBackground(uri: Uri): android.graphics.Bitmap {
        val dm = resources.displayMetrics
        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                val scale = maxOf(dm.widthPixels.toFloat() / info.size.width, dm.heightPixels.toFloat() / info.size.height).coerceAtMost(1f)
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // so it can be re-encoded below
            } // ImageDecoder also applies the photo's EXIF rotation
        } else {
            contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        }
        bgFile.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        return bmp
    }

    /** The window is always transparent (see themes.xml); this only decides whether Android draws the wallpaper behind it. */
    private fun applyWindowBackground() {
        if (Look.background == Look.BG_SYSTEM) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    }

    // Home pressed while the launcher is already in front: close whatever is open
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        settingsOpen = false
        resetView()
    }

    private var weather by mutableStateOf<Weather?>(null)
    private var weatherLoading by mutableStateOf(false)
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) refreshWeather(force = true) }

    private fun savePages(list: List<String>) {
        pages = list
        prefs.edit().putString("pages", list.joinToString(",")).apply()
    }

    private fun addNative(w: NativeWidget) {
        savePages(pages + w.key)
        if (w == NativeWidget.WEATHER) refreshWeather(force = true)
    }

    // ponytail: refreshes at most every 30 min, only while the launcher is open — no background job
    private fun refreshWeather(force: Boolean = false) {
        if (NativeWidget.WEATHER.key !in pages || weatherLoading || !hasLocation()) return
        val cached = weather ?: Weather.fromJson(prefs.getString("weather", null)).also { weather = it }
        if (!force && cached != null && System.currentTimeMillis() - cached.at < 30 * 60_000L) return
        weatherLoading = true
        lifecycleScope.launch {
            fetchWeather()?.let { weather = it; prefs.edit().putString("weather", it.toJson()).apply() }
            weatherLoading = false
        }
    }

    // Other apps' widgets: 1) system picker (it binds the widget itself) → 2) widget's config screen, if any → 3) keep it
    private fun addSystemWidget() {
        pendingWidget = widgetHost.allocateAppWidgetId()
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget)
                // some pickers crash without these two
                .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, arrayListOf())
                .putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, arrayListOf()),
            REQ_PICK,
        )
    }

    private fun removePage(page: String) {
        if (page.startsWith("w:")) page.removePrefix("w:").toIntOrNull()?.let(widgetHost::deleteAppWidgetId)
        savePages(pages - page)
    }

    @Deprecated("Activity result API doesn't cover AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val id = pendingWidget
        if (id == -1 || (requestCode != REQ_PICK && requestCode != REQ_BIND && requestCode != REQ_CONFIG)) return
        fun cancel() { widgetHost.deleteAppWidgetId(id); pendingWidget = -1 }
        fun keep() { savePages(pages + "w:$id"); pendingWidget = -1 }
        if (resultCode != RESULT_OK) return cancel()
        when (requestCode) {
            REQ_PICK, REQ_BIND -> {
                val info = widgetManager.getAppWidgetInfo(id)
                when {
                    info == null -> {
                        // Not bound yet: ask the user for permission to place it (only when we know the provider)
                        val provider = data?.component
                        if (requestCode == REQ_PICK && provider != null && !widgetManager.bindAppWidgetIdIfAllowed(id, provider)) {
                            @Suppress("DEPRECATION")
                            startActivityForResult(
                                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider),
                                REQ_BIND,
                            )
                        } else if (widgetManager.getAppWidgetInfo(id) != null) keep() else cancel()
                    }
                    info.configure != null -> runCatching {
                        widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
                    }.onFailure { keep() }
                    else -> keep()
                }
            }
            REQ_CONFIG -> keep()
        }
    }

    override fun onStart() { super.onStart(); widgetHost.startListening() }
    override fun onStop() { super.onStop(); runCatching { widgetHost.stopListening() } }

    /** "agenda · música · clima · otra app…" — built-ins not added yet, plus the system picker. */
    @Composable
    private fun WidgetChooser() {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            NativeWidget.entries.filter { it.key !in pages }.forEach { w -> MenuItem(stringResource(w.label)) { addNative(w) } }
            MenuItem(stringResource(R.string.other_app)) { addSystemWidget() }
        }
    }

    @Composable
    private fun WidgetPage(page: String) {
        val haptics = LocalHapticFeedback.current
        val longPress = Modifier.longPressFirst { haptics.performHapticFeedback(HapticFeedbackType.LongPress); widgetMenu = page }
        val calendar = { safeStart(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)); Unit }
        when (NativeWidget.of(page)) {
            NativeWidget.AGENDA -> Box(longPress) {
                val events = remember(resumes) { if (hasCalendar()) upcomingEvents(48, 3) else null }
                AgendaWidget(events, onAsk = { askCalendar.launch(Manifest.permission.READ_CALENDAR) }, onOpen = calendar)
            }
            NativeWidget.MUSIC -> Box(longPress) {
                MusicWidget(onNeedAccess = { safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
            }
            NativeWidget.WEATHER -> Box(longPress) {
                WeatherWidget(
                    hasPermission = hasLocation(), weather = weather, loading = weatherLoading,
                    onAsk = { askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    onRefresh = { refreshWeather(force = true) },
                )
            }
            null -> {
                val id = page.removePrefix("w:").toInt()
                AndroidView(
                    factory = { ctx ->
                        widgetHost.createView(ctx, id, widgetManager.getAppWidgetInfo(id)).apply {
                            val w = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
                            @Suppress("DEPRECATION") updateAppWidgetSize(null, w, 200, w, 200)
                        }
                    },
                    Modifier.fillMaxSize(),
                )
            }
        }
    }

    @Composable
    private fun WidgetArea() {
        if (pages.isEmpty()) {
            if (choosingWidget) WidgetChooser()
            else BasicText(stringResource(R.string.widget_plus), Modifier.clickable(interactionSource = null, indication = null) { choosingWidget = true }.padding(vertical = 8.dp), style = SmallText)
            return
        }
        val pager = rememberPagerState { pages.size }
        val scope = rememberCoroutineScope()
        SideEffect {
            pageBy = { d -> scope.launch { pager.animateScrollToPage((pager.currentPage + d).coerceIn(0, pages.lastIndex)) } }
        }
        Column {
            // ponytail: fixed page height; tall widgets get clipped — make it per-widget (info.minHeight) if that bites
            HorizontalPager(pager, Modifier.fillMaxWidth().height(200.dp), pageSpacing = 24.dp, key = { pages[it] }) { WidgetPage(pages[it]) }
            // page dots
            if (pages.size > 1) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { i ->
                    Box(Modifier.size(5.dp).background(if (i == pager.currentPage) Fg else Dim, CircleShape))
                }
            }
            when {
                choosingWidget -> WidgetChooser()
                widgetMenu != null -> Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    val page = widgetMenu!!
                    MenuItem(stringResource(R.string.remove)) { removePage(page) }
                    MenuItem(stringResource(R.string.add_widget)) { choosingWidget = true }
                }
            }
        }
    }

    private var hidden by mutableStateOf(emptySet<String>())
    private var recents by mutableStateOf(emptyList<App>())

    private fun toggle(set: Set<String>, app: App, pref: String) =
        (if (app.id in set) set - app.id else set + app.id).also { prefs.edit().putStringSet(pref, it).apply() }

    private fun saveFavorites(list: List<String>) {
        favorites = list
        prefs.edit().putString("favOrder", list.joinToString("\n")).apply()
    }

    private fun toggleFavorite(app: App) = saveFavorites(if (app.id in favorites) favorites - app.id else favorites + app.id)

    private fun resetView() { letter = null; query = null; menuFor = null; widgetMenu = null; choosingWidget = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Look.load(this)
        runCatching { cameraManager.registerTorchCallback(torchCallback, null) }
        applyWindowBackground()
        if (Look.background == Look.BG_PHOTO) lifecycleScope.launch {
            bgImage = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(bgFile.path)?.asImageBitmap() }
        }
        setContent {
            BackHandler { // home screen never exits
                when {
                    settingsOpen -> settingsOpen = false
                    menuFor != null -> menuFor = null
                    else -> resetView()
                }
            }
            val visible = apps.filter { it.id !in hidden }
            val q = query
            val listed = when {
                q != null -> if (q.isBlank()) emptyList() else apps.filter { it.label.contains(q.trim(), ignoreCase = true) }
                else -> visible.filter { it.letter == letter }
            }
            val clean = letter == null && q == null
            // Layers: theme color, or photo / system wallpaper with a theme-colored veil so text stays readable
            Box(Modifier.fillMaxSize().then(if (Look.background == Look.BG_NONE) Modifier.background(Bg) else Modifier)) {
            if (Look.background == Look.BG_PHOTO) bgImage?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = if (Look.gray) Grayscale else null)
            }
            if (Look.background != Look.BG_NONE) Box(Modifier.fillMaxSize().background(Bg.copy(alpha = Look.dim / 100f)))
            if (settingsOpen) SettingsScreen(
                onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onBackgroundChanged = ::applyWindowBackground,
                apps = apps,
                onClose = { settingsOpen = false },
            ) else
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Column(
                    Modifier.weight(1f)
                        .then(if (clean) Modifier.pointerInput(Unit) { homeGestures() } else Modifier)
                        .padding(start = 36.dp, top = 40.dp)
                ) {
                    Clock()
                    if (q != null) SearchField(q) { listed.firstOrNull()?.let(::open) }
                    Box(Modifier.weight(1f)) {
                        if (clean) WidgetArea()
                        else key(letter, q == null) { // fresh scroll position per letter / search
                            LazyColumn(Modifier.fadeEdges()) {
                                items(listed, key = { it.id }) { AppRow(it) }
                            }
                        }
                    }
                    // Recent apps: small, dimmer icons so they read apart from favorites; only on the clean home view
                    if (clean && Look.showRecents && recents.isNotEmpty()) Column(Modifier.padding(top = 12.dp)) {
                        BasicText(stringResource(R.string.recents), style = SmallText.copy(fontSize = 12.sp))
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            recents.forEach { app ->
                                Image(
                                    iconOf(app), app.label,
                                    Modifier.size(34.dp).clickable(interactionSource = null, indication = null) { open(app) },
                                    alpha = 0.6f, colorFilter = Grayscale,
                                )
                            }
                        }
                    }
                    if (Look.barBottom) Box(Modifier.padding(top = 12.dp, end = 20.dp)) {
                        LetterBar(visible.map { it.letter }.distinct(), letter, horizontal = true) { letter = it; query = null; menuFor = null }
                    }
                    // Favorites pinned at the bottom as grayscale icons, always visible
                    Row(
                        Modifier.padding(top = 16.dp, bottom = 32.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        favorites.mapNotNull { id -> apps.find { it.id == id } }.forEach { key(it.id) { FavoriteIcon(it) } }
                    }
                }
                if (!Look.barBottom) LetterBar(visible.map { it.letter }.distinct(), letter) { letter = it; query = null; menuFor = null }
            }
            } // layers
        }
    }

    // ponytail: reloads on every resume instead of listening for package changes; cheap enough for a home screen
    override fun onResume() {
        super.onResume()
        resetView() // pressing Home always lands on the clean clock view
        // Hide the status bar (time, battery, wifi); swiping from the top edge shows it briefly
        WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
        hidden = prefs.getStringSet("hidden", emptySet())!!.toSet()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .sortedBy { it.label.lowercase() }
        // Ordered list; first run migrates the old unordered set alphabetically
        favorites = prefs.getString("favOrder", null)?.split("\n")?.filter { it.isNotEmpty() }
            ?: prefs.getStringSet("favorites", emptySet())!!.sortedBy { id -> apps.find { it.id == id }?.label?.lowercase() ?: id }
        // Ordered pages; first run migrates the old "widgets" list. Drop system widgets whose app was uninstalled.
        pages = (prefs.getString("pages", null)?.split(",")
            ?: prefs.getString("widgets", "")!!.split(",").filter { it.isNotEmpty() }.map { "w:$it" })
            .filter { it.isNotEmpty() }
            .filter { p -> NativeWidget.of(p) != null || p.removePrefix("w:").toIntOrNull()?.let { widgetManager.getAppWidgetInfo(it) } != null }
        refreshWeather()
        loadRecents()
        resumes++
    }

    // Clean home view: swipe down → notifications, swipe up → search, double tap → lock
    // Each gesture runs whatever action is chosen for it in settings (Look.gestures)
    private suspend fun PointerInputScope.homeGestures() = coroutineScope {
        val threshold = 80.dp.toPx()
        launch { detectTapGestures(onDoubleTap = { runGesture("double") }) }
        launch {
            var dragged = 0f
            var fired = false
            detectHorizontalDragGestures(onDragStart = { dragged = 0f; fired = false }) { change, dx ->
                dragged += dx
                if (fired) return@detectHorizontalDragGestures
                if (dragged > threshold) { fired = true; change.consume(); runGesture("right") }
                if (dragged < -threshold) { fired = true; change.consume(); runGesture("left") }
            }
        }
        var dragged = 0f
        var fired = false
        detectVerticalDragGestures(onDragStart = { dragged = 0f; fired = false }) { change, dy ->
            dragged += dy
            if (fired) return@detectVerticalDragGestures
            if (dragged > threshold) { fired = true; change.consume(); runGesture("down") }
            if (dragged < -threshold) { fired = true; change.consume(); runGesture("up") }
        }
    }

    private fun runGesture(gesture: String) {
        val action = Look.gestures[gesture] ?: return
        when {
            action == "lock" -> lockScreen()
            action == "notifications" -> expandNotifications()
            action == "search" -> query = ""
            action == "camera" -> safeStart(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            action == "torch" -> toggleTorch()
            action.startsWith("app:") -> action.removePrefix("app:").split("/").let { (pkg, cls) ->
                safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(pkg, cls))
            }
        }
    }

    // Flashlight: CameraManager needs no permission for the torch; the callback tracks its real state
    private val cameraManager by lazy { getSystemService(CameraManager::class.java) }
    private val torchId by lazy {
        runCatching { cameraManager.cameraIdList.firstOrNull { cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull()
    }
    private var torchOn = false
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == torchId) torchOn = enabled }
    }
    private fun toggleTorch() { torchId?.let { runCatching { cameraManager.setTorchMode(it, !torchOn) } } }

    @Composable
    private fun SearchField(q: String, onGo: () -> Unit) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() } // opens the keyboard
        BasicTextField(
            q, { query = it; menuFor = null },
            Modifier.fillMaxWidth().padding(bottom = 16.dp).focusRequester(focus),
            textStyle = RowText,
            cursorBrush = SolidColor(Fg),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            decorationBox = { inner ->
                Box { if (q.isEmpty()) BasicText(stringResource(R.string.search), style = RowText.copy(color = Dim)); inner() }
            },
        )
    }

    // Tap opens; long-press shows: favorito · info · ocultar · desinstalar
    @Composable
    private fun AppRow(app: App) {
        val haptics = LocalHapticFeedback.current
        val dot = app.pkg in NotificationDots.packages.value
        val isHidden = app.id in hidden
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        interactionSource = null, indication = null,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuFor = if (menuFor == app.id) null else app.id
                        },
                    ) { open(app) }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Image(iconOf(app), null, Modifier.size(30.dp), alpha = if (isHidden) 0.4f else 0.8f, colorFilter = Grayscale)
                BasicText(
                    app.label.lowercase().replaceFirstChar { it.uppercase() } + if (dot) "  •" else "",
                    style = if (isHidden) RowText.copy(color = Dim) else RowText,
                )
            }
            if (menuFor == app.id) {
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    MenuItem(stringResource(if (app.id in favorites) R.string.fav_remove else R.string.fav_add)) {
                        toggleFavorite(app)
                    }
                    MenuItem(stringResource(R.string.info)) {
                        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                    }
                    MenuItem(stringResource(if (app.id in hidden) R.string.show else R.string.hide)) {
                        hidden = toggle(hidden, app, "hidden")
                    }
                    MenuItem(stringResource(R.string.uninstall)) {
                        safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
                    }
                }
            }
        }
    }

    @Composable
    private fun MenuItem(label: String, onClick: () -> Unit) = BasicText(
        label,
        Modifier.clickable(interactionSource = null, indication = null) { menuFor = null; widgetMenu = null; choosingWidget = false; onClick() }
            .padding(vertical = 6.dp),
        style = SmallText,
    )

    // ponytail: plain map cache, never evicted; fine for a few hundred apps (stale icon after an app update until the launcher restarts)
    private val iconCache = HashMap<String, ImageBitmap>()

    private fun iconOf(app: App) = iconCache.getOrPut(app.id) {
        val d = runCatching { packageManager.getActivityIcon(ComponentName(app.pkg, app.activity)) }
            .getOrElse { packageManager.defaultActivityIcon }
        val px = (48 * resources.displayMetrics.density).toInt()
        Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also {
            d.setBounds(0, 0, px, px); d.draw(Canvas(it))
        }.asImageBitmap()
    }

    // Tap opens. Long-press + drag sideways reorders; drag up and release removes it.
    @Composable
    private fun FavoriteIcon(app: App) {
        val haptics = LocalHapticFeedback.current
        val density = LocalDensity.current
        val slot = with(density) { 72.dp.toPx() }       // icon (44) + gap (28)
        val dropAt = with(density) { -90.dp.toPx() }
        var dx by remember { mutableFloatStateOf(0f) }
        var dy by remember { mutableFloatStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        var dragEndedAt by remember { mutableStateOf(0L) }
        fun reset() { dragging = false; dx = 0f; dy = 0f; dragEndedAt = SystemClock.uptimeMillis() }
        fun move(from: Int, to: Int) {
            favorites = favorites.toMutableList().apply { add(to, removeAt(from)) }
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                iconOf(app), app.label,
                Modifier.size(44.dp)
                    .graphicsLayer {
                        translationX = dx
                        translationY = dy.coerceAtMost(0f)
                        val scale = if (dragging) 1.15f else 1f
                        scaleX = scale; scaleY = scale
                        alpha = if (dy < dropAt) 0.25f else 1f
                    }
                    .pointerInput(app.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = true; dx = 0f; dy = 0f; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragEnd = { if (dy < dropAt) toggleFavorite(app) else saveFavorites(favorites); reset() },
                            onDragCancel = { saveFavorites(favorites); reset() },
                        ) { change, d ->
                            change.consume()
                            dx += d.x; dy += d.y
                            val i = favorites.indexOf(app.id)
                            if (dx > slot / 2 && i in 0 until favorites.lastIndex) { move(i, i + 1); dx -= slot }
                            else if (dx < -slot / 2 && i > 0) { move(i, i - 1); dx += slot }
                        }
                    }
                    .clickable(interactionSource = null, indication = null) {
                        // a long-press that ends in place must not also open the app
                        if (!dragging && SystemClock.uptimeMillis() - dragEndedAt > 300) open(app)
                    },
                alpha = 0.85f,
                colorFilter = Grayscale,
            )
            val dot = app.pkg in NotificationDots.packages.value
            Box(Modifier.padding(top = 6.dp).size(4.dp).background(if (dot) Fg else Color.Transparent, CircleShape))
        }
    }

    // Fonts put blank space (side bearing) before the first glyph, and it grows with size —
    // so the big thin clock looked indented next to the date. Measure the real ink edge and pull it back.
    private val inkPaint by lazy { android.graphics.Paint().apply { typeface = resources.getFont(R.font.jost) } }

    @Composable
    private fun Modifier.alignInk(text: String, size: TextUnit, weight: Int): Modifier {
        val px = with(LocalDensity.current) { size.toPx() }
        val left = remember(text, px, weight) {
            inkPaint.textSize = px
            inkPaint.fontVariationSettings = "'wght' $weight"
            android.graphics.Rect().also { inkPaint.getTextBounds(text, 0, text.length, it) }.left
        }
        return offset { IntOffset(-left, 0) }
    }

    // Tap time → alarms, tap date → calendar, long-press date → notification-dot access
    @Composable
    private fun Clock() {
        val now by produceState(LocalDateTime.now()) {
            while (true) {
                value = LocalDateTime.now()
                delay(1000L * (60 - value.second)) // wake at the next minute
            }
        }
        Column(Modifier.padding(bottom = 64.dp)) {
            val time = now.format(DateTimeFormatter.ofPattern(if (Look.clock24) "HH:mm" else "h:mm"))
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(
                    time,
                    // tap → alarms, long-press → launcher settings
                    Modifier.alignInk(time, Look.clockSp.sp, 100).combinedClickable(
                        interactionSource = null, indication = null,
                        onLongClick = { settingsOpen = true },
                    ) { safeStart(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
                    style = TextStyle(color = Fg, fontSize = Look.clockSp.sp, fontFamily = Thin, fontWeight = FontWeight.Thin, shadow = TextShadow),
                )
                if (!Look.clock24) BasicText(
                    now.format(DateTimeFormatter.ofPattern("a")).lowercase(),
                    Modifier.padding(start = 10.dp, bottom = (Look.clockSp * 0.2f).dp),
                    style = SmallText.copy(fontSize = 18.sp),
                )
            }
            val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")).lowercase()
            BasicText(
                date,
                Modifier.alignInk(date, 16.sp, 400).combinedClickable(
                    interactionSource = null, indication = null,
                    onLongClick = { safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                ) { safeStart(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)) },
                style = SmallText.copy(fontSize = 16.sp),
            )
            // battery · next event (tap: grant calendar access, then opens the calendar) · • n notifications
            val (level, charging) = battery()
            val event = remember(now, resumes) { nextEvent() }
            val notified = NotificationDots.packages.value.size
            val status = buildString {
                    append(stringResource(if (charging) R.string.battery_charging else R.string.battery, level))
                    event?.let { (title, at) ->
                        append("  ·  ")
                        if (at.toLocalDate() != now.toLocalDate()) append(stringResource(R.string.tomorrow)).append(' ')
                        append(at.format(DateTimeFormatter.ofPattern(Look.timePattern())).lowercase()).append(' ').append(title)
                    }
                }
            Row(Modifier.padding(top = 6.dp).alignInk(status, 14.sp, 400)) {
                BasicText(
                    status,
                    Modifier.weight(1f, fill = false).clickable(interactionSource = null, indication = null) {
                        if (hasCalendar()) safeStart(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                        else askCalendar.launch(Manifest.permission.READ_CALENDAR)
                    },
                    style = SmallText.copy(fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // How many apps have notifications; tap opens the shade
                if (notified > 0) BasicText(
                    "  ·  • $notified",
                    Modifier.clickable(interactionSource = null, indication = null) { expandNotifications() },
                    style = SmallText.copy(fontSize = 14.sp, color = Fg),
                )
            }
        }
    }

    @Composable
    private fun battery(): Pair<Int, Boolean> {
        var state by remember { mutableStateOf(0 to false) }
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    state = level to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
                }
            }
            registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { receiver.onReceive(this@MainActivity, it) }
            onDispose { unregisterReceiver(receiver) }
        }
        return state
    }

    private fun hasCalendar() = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    // Next timed (non all-day) event starting within 24h, or null
    // ponytail: queried on the main thread once a minute; one tiny indexed query
    private fun nextEvent(): Pair<String, LocalDateTime>? {
        if (!hasCalendar()) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, now); ContentUris.appendId(it, now + 24 * 3_600_000L) }.build()
        return runCatching {
            contentResolver.query(
                uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                "${CalendarContract.Instances.ALL_DAY} = 0 AND ${CalendarContract.Instances.BEGIN} >= ?",
                arrayOf(now.toString()), "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                if (!c.moveToFirst()) null
                else (c.getString(0) ?: "").lowercase() to LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(1)), ZoneId.systemDefault())
            }
        }.getOrNull()
    }

    private fun lockScreen() {
        val service = LockService.instance
        if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(this, R.string.lock_toast, Toast.LENGTH_LONG).show()
            safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    // Hidden API, but the standard way launchers do this; needs EXPAND_STATUS_BAR (normal permission)
    @android.annotation.SuppressLint("WrongConstant")
    private fun expandNotifications() = runCatching {
        val sb = getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    }

    private fun safeStart(intent: Intent) = runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    private fun open(app: App) {
        safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(app.pkg, app.activity))
        // launcher's own history: used for the recents row when usage access isn't granted
        val history = listOf(app.id) + (prefs.getString("recent", "")!!.split("\n").filter { it.isNotEmpty() && it != app.id })
        prefs.edit().putString("recent", history.take(12).joinToString("\n")).apply()
    }

    /**
     * Last apps used, newest first, without favorites or hidden apps. With usage access granted it's every app
     * the phone brought to the foreground in the last 3 days; otherwise only what was opened from this launcher.
     */
    private fun loadRecents() {
        val byPkg = apps.associateBy { it.pkg }
        val byId = apps.associateBy { it.id }
        val ordered = if (hasUsageAccess()) {
            recentPackages().mapNotNull { byPkg[it] }
        } else {
            prefs.getString("recent", "")!!.split("\n").mapNotNull { byId[it] }
        }
        recents = ordered.filter { it.id !in favorites && it.id !in hidden }.distinctBy { it.pkg }.take(5)
    }

    // ponytail: reads 3 days of usage events on the main thread at each resume; fine at this volume, move off-thread if it stutters
    private fun recentPackages(): List<String> {
        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 3 * 86_400_000L, now)
        val last = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION") // == ACTIVITY_RESUMED (API 29) with the same value
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && e.packageName != packageName) last[e.packageName] = e.timeStamp
        }
        return last.entries.sortedByDescending { it.value }.map { it.key }
    }
}

// A–Z strip: vertical at the right edge, or horizontal above the favorites (settings → letter bar).
// Tap or slide along it to pick a letter; while touching, a bubble with the letter follows the finger.
@Composable
private fun LetterBar(letters: List<String>, selected: String?, horizontal: Boolean = false, onSelect: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var touch by remember { mutableStateOf<Float?>(null) } // finger position along the strip, null when not touching
    val pad = if (horizontal) 0.dp else 48.dp
    val strip = Modifier.pointerInput(letters, horizontal) {
        var last: String? = null
        fun length() = (if (horizontal) size.width else size.height).toFloat()
        fun pick(pos: Float) {
            if (letters.isEmpty()) return
            touch = pos.coerceIn(0f, length())
            val l = letters[(pos / length() * letters.size).toInt().coerceIn(letters.indices)]
            if (l != last) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); last = l; onSelect(l) }
        }
        // Nothing is selected on touch-down (a back swipe from the edge, or a swipe across the strip, starts here too).
        // Select while sliding along the strip, or on a tap (on release); movement across the strip selects nothing.
        awaitEachGesture {
            try {
                last = null // first pick always selects (selected may be stale in this closure)
                val down = awaitFirstDown()
                var sliding = false
                var across = false
                val slop = viewConfiguration.touchSlop
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val d = change.position - down.position
                    val alongD = if (horizontal) d.x else d.y
                    val acrossD = if (horizontal) d.y else d.x
                    if (!sliding && !across && (abs(alongD) > slop || abs(acrossD) > slop)) {
                        if (abs(alongD) >= abs(acrossD)) sliding = true else across = true
                    }
                    if (sliding) { pick(if (horizontal) change.position.x else change.position.y); change.consume() }
                    else if (!change.pressed && !across && change.uptimeMillis - down.uptimeMillis < 500) {
                        pick(if (horizontal) down.position.x else down.position.y) // tap
                    }
                } while (event.changes.any { it.pressed })
            } finally {
                touch = null
            }
        }
    }
    val fg = Fg
    val dim = Dim
    val shadow = TextShadow
    @Composable
    fun Letter(l: String) {
        val on = l == selected
        BasicText(
            l.lowercase(),
            style = TextStyle(
                color = if (on) fg else dim, fontFamily = Thin, shadow = shadow,
                fontSize = if (on) 20.sp else 13.sp,
                fontWeight = if (on) FontWeight.Normal else FontWeight.Light,
            ),
        )
    }
    Box(if (horizontal) Modifier.fillMaxWidth().height(40.dp) else Modifier.width(44.dp).fillMaxHeight()) {
        if (horizontal) {
            // fixed-width cells so the strip doesn't shift when the selected letter grows
            Row(Modifier.fillMaxSize().then(strip), verticalAlignment = Alignment.CenterVertically) {
                letters.forEach { l -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Letter(l) } }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(vertical = pad).then(strip),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { letters.forEach { Letter(it) } }
        }
        // Bubble: inverted circle beside the strip (left of it, or above it), following the finger
        val t = touch
        if (t != null && selected != null) {
            val bubble = 60.dp
            Box(
                Modifier
                    .offset {
                        if (horizontal) IntOffset((t - bubble.toPx() / 2).toInt(), -(bubble + 14.dp).roundToPx())
                        else IntOffset(-(bubble + 28.dp).roundToPx(), (t + pad.toPx() - bubble.toPx() / 2).toInt())
                    }
                    .size(bubble)
                    .background(fg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(selected.uppercase(), style = TextStyle(color = Bg, fontSize = 30.sp, fontFamily = Thin, fontWeight = FontWeight.Normal))
            }
        }
    }
}

// Long-press that wins over the children: watched in the Initial pass (before any clickable inside sees the touch),
// and once it fires the rest of the gesture is swallowed so the child doesn't also get a click.
// A normal tap or a drag (pager swipe) passes through untouched.
private fun Modifier.longPressFirst(onLongPress: () -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            }
            true
        }
        if (released == null) { // timed out with the finger still down and still → long press
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

// Soft fade at top and bottom so the list dissolves at its edges
private fun Modifier.fadeEdges() = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.06f to Color.Black,
                0.85f to Color.Black, 1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
