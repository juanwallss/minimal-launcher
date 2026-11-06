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
import androidx.compose.ui.platform.LocalContext
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Spacer
import android.provider.ContactsContract
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
internal val Bg @Composable get(): Color {
    val base = if (isSystemInDarkTheme()) Color(0xFF0A0A0A) else Color(0xFFF4F2EE)
    return if (Look.theme == Look.THEME_NONE) base else androidx.compose.ui.graphics.lerp(base, Accent, 0.06f) // faint tint of the theme
}
/** Theme accent (settings → appearance → theme): clock, chosen letter, bubble, underlines. Plain [Fg] when no theme. */
internal val Accent @Composable get(): Color {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val raw = when (val t = Look.theme) {
        Look.THEME_NONE -> null
        // wallpaper: the launcher's own photo, else the phone's wallpaper (Material You palette on Android 12+)
        Look.THEME_WALL -> Look.photoAccent?.takeIf { Look.background == Look.BG_PHOTO }
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ctx.getColor(android.R.color.system_accent1_200)
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) android.app.WallpaperManager.getInstance(ctx).getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
            else null
        else -> Look.THEMES[t]
    } ?: return Fg
    // same lightness for every source: bright and soft on dark, deeper on light
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(raw, it) }
    hsv[1] = hsv[1].coerceIn(0.3f, 0.55f)
    hsv[2] = if (dark) 0.88f else 0.5f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
internal val Fg @Composable get() = if (isSystemInDarkTheme()) Color(0xFFE8E6E1) else Color(0xFF1C1B1A)
internal val Dim @Composable get() = when {
    Look.background == Look.BG_NONE -> if (isSystemInDarkTheme()) Color(0xFF6B6965) else Color(0xFF8E8B86)
    else -> if (isSystemInDarkTheme()) Color(0xFFB5B2AC) else Color(0xFF4F4C48) // stronger over images
}
/** Soft halo in the theme color behind text, only when there's an image behind it. */
internal val TextShadow @Composable get() = if (Look.background == Look.BG_NONE) null else Shadow(Bg.copy(alpha = 0.75f), blurRadius = 10f)

// Variable fonts (OFL), one file each, instantiated at the weights we use (settings → appearance → font).
// Space Grotesk only goes 300–700: thinner/heavier requests clamp to its ends.
internal val FONTS = listOf("jost" to R.font.jost, "fraunces" to R.font.fraunces, "jetbrains mono" to R.font.jetbrains_mono, "space grotesk" to R.font.space_grotesk)
@OptIn(ExperimentalTextApi::class)
internal val FontFamilies = FONTS.map { (_, res) ->
    FontFamily(
        listOf(FontWeight.Thin, FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal, FontWeight.Bold, FontWeight.Black).map {
            Font(res, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
        }
    )
}
/** The chosen font (name kept from when it was always thin Jost). */
internal val Thin get() = FontFamilies.getOrElse(Look.font) { FontFamilies[0] }
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
    private val askAny = registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumes++ }
    private val askHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resumes++ }
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
            val bmp = withContext(Dispatchers.IO) { runCatching { importBackground(uri) }.getOrNull()?.also { Look.photoAccent = photoAccent(it) } }
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
        // Debug builds only: `adb shell am start -n com.jparedes.minimallauncher/.MainActivity --ei wx_test <WMO code>`
        // forces the inline weather glyph so each icon can be checked; any normal Home press clears it.
        weatherTestCode = if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.hasExtra("wx_test"))
            intent.getIntExtra("wx_test", 0) else null
        moonTestPhase = if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.hasExtra("moon"))
            intent.getFloatExtra("moon", 0f) else null
        // debug: --ei clock_style <0..3> previews a clock face without saving it (Home press keeps it until restart)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.hasExtra("clock_style"))
            Look.clockStyle = intent.getIntExtra("clock_style", 0)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            intent.getStringExtra("theme")?.let { Look.theme = it }
            if (intent.hasExtra("font")) Look.font = intent.getIntExtra("font", 0)
            if (intent.getBooleanExtra("onboarding", false)) Look.onboarded = false
            if (intent.hasExtra("bar_left")) Look.barLeft = intent.getBooleanExtra("bar_left", false)
        }
        // debug: --ez msg_test true shows a fake chat in the message card (gone with the next real notification change)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.getBooleanExtra("msg_test", false)) {
            val pi = android.app.PendingIntent.getBroadcast(this, 0, Intent("test.noop").setPackage(packageName), android.app.PendingIntent.FLAG_MUTABLE)
            val input = android.app.RemoteInput.Builder("text").build()
            NotificationDots.messages.value = listOf(
                Message("test", packageName, "Niklas", "Hi, what's up?", System.currentTimeMillis(),
                    android.app.Notification.Action.Builder(null, "Reply", pi).addRemoteInput(input).build(),
                    listOf(android.app.Notification.Action.Builder(null, "Mark as read", pi).build(), android.app.Notification.Action.Builder(null, "Mute", pi).build()),
                    listOf("Nothing much", "Not much, you?", "How are you?"), null),
                Message("test2", packageName, "x", "", 0, null, emptyList(), emptyList(), null),
            )
        }
        // debug: --ez drive true|false switches driving mode
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.hasExtra("drive"))
            Drive.set(this, intent.getBooleanExtra("drive", false))
    }
    private var weatherTestCode by mutableStateOf<Int?>(null)
    private var moonTestPhase by mutableStateOf<Float?>(null) // debug: --ef moon <0..1> forces night + that phase

    // --- Driving mode helpers
    private val askBluetooth = registerForActivityResult(ActivityResultContracts.RequestPermission()) { Drive.permTick++ }

    /** Contact picker on phone numbers: the returned row is readable without READ_CONTACTS. */
    private val pickPhone = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        runCatching {
            contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
                ?.use { c -> if (c.moveToFirst()) DriveContact(c.getString(0) ?: c.getString(1), c.getString(1)) else null }
        }.getOrNull()?.let { contact ->
            Look.driveContacts = (Look.driveContacts.filter { it.number != contact.number } + contact).take(4)
            Look.save()
        }
    }

    private var pendingCall: String? = null
    private val askCall = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingCall?.let { number -> safeStart(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
        pendingCall = null
    }

    /** Calls straight away once CALL_PHONE is granted (asked the first time); otherwise opens the dialer with the number. */
    private fun call(number: String) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            safeStart(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
        } else {
            pendingCall = number
            askCall.launch(Manifest.permission.CALL_PHONE)
        }
    }

    /** Turn-by-turn in Google Maps when available, otherwise whatever maps app handles geo: (e.g. Waze). */
    private fun navigate(address: String?) {
        val q = Uri.encode(address ?: "")
        val turnByTurn = address != null && runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!turnByTurn) safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(if (address == null) "geo:0,0" else "geo:0,0?q=$q")))
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
        if (weatherLoading || !hasLocation()) return
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
                    Box(Modifier.size(5.dp).background(if (i == pager.currentPage) Accent else Dim, CircleShape))
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
    private var recentMenu by mutableStateOf<String?>(null) // recent app id whose long-press menu is open

    // Clearing works for both sources: the launcher's own history is edited, and for usage data
    // we remember "ignore uses before this moment" (whole row, or per app) since system history can't be deleted.
    private fun dismissRecent(app: App) {
        val history = prefs.getString("recent", "")!!.split("\n").filter { it.isNotEmpty() && it != app.id }
        prefs.edit().putString("recent", history.joinToString("\n")).putLong("rd:${app.pkg}", System.currentTimeMillis()).apply()
        loadRecents()
    }

    private fun clearRecents() {
        prefs.edit().putString("recent", "").putLong("recentClearedAt", System.currentTimeMillis()).apply()
        loadRecents()
    }

    private fun toggle(set: Set<String>, app: App, pref: String) =
        (if (app.id in set) set - app.id else set + app.id).also { prefs.edit().putStringSet(pref, it).apply() }

    private fun saveFavorites(list: List<String>) {
        favorites = list
        prefs.edit().putString("favOrder", list.joinToString("\n")).apply()
    }

    private fun toggleFavorite(app: App) = saveFavorites(if (app.id in favorites) favorites - app.id else favorites + app.id)

    private fun resetView() { letter = null; query = null; menuFor = null; widgetMenu = null; choosingWidget = false; recentMenu = null }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Look.load(this)
        Drive.load(this)
        launcherApps.registerCallback(packageWatcher)
        runCatching { cameraManager.registerTorchCallback(torchCallback, null) }
        applyWindowBackground()
        if (Look.background == Look.BG_PHOTO) lifecycleScope.launch {
            bgImage = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(bgFile.path)?.also { Look.photoAccent = photoAccent(it) }?.asImageBitmap() }
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
                letter == STAR -> favorites.mapNotNull { id -> apps.find { it.id == id } }
                else -> visible.filter { it.letter == letter }
            }
            val clean = letter == null && q == null
            // ★ (favorites) heads the bar when there are any
            val barLetters = (if (favorites.isNotEmpty()) listOf(STAR) else emptyList()) + visible.map { it.letter }.distinct()
            // Layers: theme color, or photo / system wallpaper with a theme-colored veil so text stays readable
            Box(Modifier.fillMaxSize().then(if (Look.background == Look.BG_NONE) Modifier.background(Bg) else Modifier)) {
            if (Look.background == Look.BG_PHOTO) bgImage?.let {
                Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = if (Look.gray) Grayscale else null)
            }
            if (Look.background != Look.BG_NONE) Box(Modifier.fillMaxSize().background(Bg.copy(alpha = Look.dim / 100f)))
            if (!Look.onboarded) Onboarding(
                resumeTick = resumes,
                onRequest = { askAny.launch(it) },
                onMakeHome = { i -> runCatching { askHome.launch(i) }.onFailure { safeStart(Intent(Settings.ACTION_HOME_SETTINGS)) } },
                onDone = { Look.onboarded = true; Look.save() },
            ) else AnimatedContent(
                settingsOpen,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInVertically(tween(260)) { if (targetState) it / 12 else -it / 12 }) togetherWith fadeOut(tween(180))
                },
                label = "settings",
            ) { inSettings ->
            if (inSettings) SettingsScreen(
                onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onBackgroundChanged = ::applyWindowBackground,
                apps = apps,
                onAskBluetooth = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
                },
                onPickContact = { runCatching { pickPhone.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) } },
                background = bgImage,
                resumeTick = resumes,
                onClose = { settingsOpen = false },
            ) else if (Drive.active) DriveScreen(
                homeAddress = Look.homeAddress,
                workAddress = Look.workAddress,
                contacts = Look.driveContacts,
                onNavigate = ::navigate,
                onCall = { call(it.number) },
                onDial = { safeStart(Intent(Intent.ACTION_DIAL)) },
                onSetup = { settingsOpen = true },
                onExit = { Drive.set(this@MainActivity, false) },
                favorites = favorites.mapNotNull { id -> apps.find { it.id == id } },
                icon = ::iconOf,
                onOpen = ::open,
            ) else {
            // Pieces shared by the portrait (one column) and landscape (two panes) layouts
            val listPane: @Composable (Modifier) -> Unit = { m ->
                    Box(m) {
                        Crossfade(clean, animationSpec = tween(220), label = "home") { isClean ->
                            if (isClean) Column {
                                // widgets (optional) under the clock, then the favorites
                                if (Look.showWidgets) WidgetArea()
                                val favState = rememberLazyListState()
                                LazyColumn(Modifier.fadeEdges(favState), favState) { items(favorites.mapNotNull { id -> apps.find { it.id == id } }, key = { it.id }) { AppRow(it) } }
                            }
                            else key(letter, q == null) { // fresh scroll position (and entrance animation) per letter / search
                                val openedAt = remember { SystemClock.uptimeMillis() }
                                val listState = rememberLazyListState()
                                LazyColumn(Modifier.fadeEdges(listState), listState) {
                                    itemsIndexed(listed, key = { _, app -> app.id }) { i, app -> Box(Modifier.enterAnim(i, openedAt)) { AppRow(app) } }
                                }
                            }
                        }
                    }
            }
            val recentsRow: @Composable () -> Unit = {
                    if (clean) MiniPlayer(Modifier.padding(top = 12.dp, end = 24.dp))
                    // Recent apps: small, dimmer icons so they read apart from favorites; only on the clean home view
                    if (clean && Look.showRecents && recents.isNotEmpty()) Column(Modifier.padding(top = 12.dp)) {
                        BasicText(stringResource(R.string.recents), style = SmallText.copy(fontSize = 12.sp))
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            val haptics = LocalHapticFeedback.current
                            recents.forEach { app ->
                                Image(
                                    iconOf(app), app.label,
                                    Modifier.size(34.dp).combinedClickable(
                                        interactionSource = null, indication = null,
                                        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); recentMenu = app.id },
                                    ) { open(app) },
                                    alpha = if (recentMenu == app.id) 1f else 0.6f, colorFilter = Grayscale,
                                )
                            }
                        }
                        // long-press menu: drop this one, or wipe the row
                        recents.firstOrNull { it.id == recentMenu }?.let { app ->
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                                MenuItem(stringResource(R.string.remove)) { dismissRecent(app) }
                                MenuItem(stringResource(R.string.clear_all)) { clearRecents() }
                            }
                        }
                    }
            }
            val bottomBar: @Composable () -> Unit = {
                    if (Look.barBottom) Box(Modifier.padding(top = 12.dp, end = 20.dp)) {
                        LetterBar(barLetters, letter, horizontal = true) { letter = it; query = null; menuFor = null }
                    }
            }
            val messageCard: @Composable () -> Unit = {
                    // newest chat notification, answerable in place; only on the clean home view
                    val msgs = NotificationDots.messages.value
                    // appears: room opens, card drops in and settles; a newer chat pushes the old card up and out
                    AnimatedVisibility(
                        clean && Look.showMessages && msgs.isNotEmpty(),
                        enter = expandVertically(tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(260, delayMillis = 80)) +
                            slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { -it / 5 } +
                            scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 0.94f),
                        exit = fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.96f) + shrinkVertically(tween(240, delayMillis = 60)),
                    ) {
                        AnimatedContent(
                            msgs.firstOrNull(), contentKey = { it?.key },
                            transitionSpec = {
                                (fadeIn(tween(240, delayMillis = 60)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 4 }) togetherWith
                                    (fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 4 }) using SizeTransform(clip = false)
                            },
                            label = "msg",
                        ) { m ->
                            if (m != null) {
                                val app = apps.firstOrNull { it.pkg == m.pkg }
                                MessageCard(m, msgs.size - 1, app?.let(::iconOf), app?.label ?: m.pkg, onMore = ::expandNotifications, modifier = Modifier.padding(end = 28.dp, bottom = 16.dp))
                            }
                        }
                    }
            }
            val homeGesturesMod = if (clean) Modifier.pointerInput(Unit) { homeGestures() } else Modifier
            val wide = LocalConfiguration.current.run { screenWidthDp > screenHeightDp }
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (Look.barLeft && !Look.barBottom) LetterBar(barLetters, letter, left = true) { letter = it; query = null; menuFor = null }
                val side = if (Look.barLeft && !Look.barBottom) Modifier.padding(start = 8.dp, end = 20.dp) else Modifier.padding(start = 36.dp)
                if (!wide) Column(Modifier.weight(1f).then(homeGesturesMod).then(side).padding(top = 40.dp)) {
                    Clock()
                    if (q != null) SearchField(q) { listed.firstOrNull()?.let(::open) }
                    messageCard()
                    listPane(Modifier.weight(1f))
                    recentsRow()
                    bottomBar()
                    Spacer(Modifier.height(28.dp))
                } else Row(Modifier.weight(1f).then(homeGesturesMod).then(side).padding(top = 20.dp)) {
                    // landscape: clock and recents on the left; widgets / list / search on the right
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Clock(bottomGap = 12.dp, maxSp = 88f)
                        Spacer(Modifier.weight(1f))
                        recentsRow()
                        bottomBar()
                        Spacer(Modifier.height(20.dp))
                    }
                    Column(Modifier.weight(1.3f).fillMaxHeight().padding(start = 24.dp, top = 12.dp)) {
                        if (q != null) SearchField(q) { listed.firstOrNull()?.let(::open) }
                        messageCard()
                        listPane(Modifier.weight(1f))
                    }
                }
                if (!Look.barBottom && !Look.barLeft) LetterBar(barLetters, letter) { letter = it; query = null; menuFor = null }
            }
            } // home
            } // settings ↔ home
            } // layers
        }
    }

    // App list is (re)built only on start and when a package is installed / updated / removed —
    // not on every Home press (loading ~150 labels each time was the main per-resume cost).
    private var appsDirty = true
    private val launcherApps by lazy { getSystemService(android.content.pm.LauncherApps::class.java) }
    private val packageWatcher = object : android.content.pm.LauncherApps.Callback() {
        private fun changed(vararg pkgs: String) {
            pkgs.forEach { pkg -> iconCache.keys.removeAll { it.startsWith("$pkg/") } } // new icon after an update
            appsDirty = true
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) { reloadApps(); loadRecents() }
        }
        override fun onPackageAdded(packageName: String, user: android.os.UserHandle) = changed(packageName)
        override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: android.os.UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = changed(*packageNames)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean) = changed(*packageNames)
    }

    private fun reloadApps() {
        appsDirty = false
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .sortedBy { it.label.lowercase() }
    }

    override fun onDestroy() {
        runCatching { launcherApps.unregisterCallback(packageWatcher) }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        resetView() // pressing Home always lands on the clean clock view
        // Hide the status bar (time, battery, wifi); swiping from the top edge shows it briefly
        WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
        hidden = prefs.getStringSet("hidden", emptySet())!!.toSet()
        if (appsDirty) reloadApps()
        Drive.load(this) // the car receiver may have switched it while we were in the background
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
            action == "drive" -> Drive.set(this, true)
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
        val press = remember { MutableInteractionSource() }
        val pressed by press.collectIsPressedAsState()
        val pressAlpha by animateFloatAsState(if (pressed) 0.45f else 1f, tween(120), label = "press")
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .graphicsLayer { alpha = pressAlpha }
                    .combinedClickable(
                        interactionSource = press, indication = null,
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
            AnimatedVisibility(
                menuFor == app.id,
                enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(150)) + fadeOut(tween(120)),
            ) {
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
        Modifier.clickable(interactionSource = null, indication = null) { menuFor = null; widgetMenu = null; choosingWidget = false; recentMenu = null; onClick() }
            .padding(vertical = 6.dp),
        style = SmallText,
    )

    // Plain map cache; an app's entries are dropped when it's updated (see packageWatcher)
    private val iconCache = HashMap<String, ImageBitmap>()

    private fun iconOf(app: App) = iconCache.getOrPut(app.id) {
        val d = runCatching { packageManager.getActivityIcon(ComponentName(app.pkg, app.activity)) }
            .getOrElse { packageManager.defaultActivityIcon }
        val px = (48 * resources.displayMetrics.density).toInt()
        Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also {
            d.setBounds(0, 0, px, px); d.draw(Canvas(it))
        }.asImageBitmap()
    }


    // Tap time → alarms, tap date → calendar, long-press date → notification-dot access
    @Composable
    private fun Clock(bottomGap: Dp = 64.dp, maxSp: Float = Float.MAX_VALUE) {
        val clockSp = minOf(Look.clockSp, maxSp)
        val now by produceState(LocalDateTime.now()) {
            while (true) {
                value = LocalDateTime.now()
                delay(1000L * (60 - value.second)) // wake at the next minute
            }
        }
        Column(Modifier.padding(bottom = bottomGap)) {
            val style = ClockStyle.entries.getOrElse(Look.clockStyle) { ClockStyle.THIN }
            val timeText = now.format(DateTimeFormatter.ofPattern(Look.timePattern())).lowercase()
            val shortDate = now.format(DateTimeFormatter.ofPattern("d MMM")).trimEnd('.')
            // big element: tap → alarms, long-press → launcher settings
            ClockFace(
                style, now, clockSp,
                band = listOfNotNull(timeText.uppercase(), shortDate.uppercase(), weather?.let { "${it.temp}°" }),
                actions = Modifier.combinedClickable(
                    interactionSource = null, indication = null,
                    onLongClick = { settingsOpen = true },
                ) { safeStart(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
            )
            // line under it: the date (DAY also carries the time; SPLIT already shows time/date/temp in its band)
            val info = Look.info // what the owner chose to show under the time (settings → home)
            val showDate = Look.INFO_DATE in info
            val date = when (style) {
                ClockStyle.DAY, ClockStyle.BINARY -> if (showDate) "$timeText  ·  ${shortDate.lowercase()}" else timeText
                ClockStyle.SPLIT -> ""
                else -> if (showDate) now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")).lowercase() else ""
            }
            Row(Modifier.padding(top = if (style == ClockStyle.SPLIT) 6.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                if (date.isNotEmpty()) BasicText(
                    date,
                    Modifier.alignInk(date, 16.sp, 400).combinedClickable(
                        interactionSource = null, indication = null,
                        onLongClick = { safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    ) { safeStart(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)) },
                    style = SmallText.copy(fontSize = 16.sp),
                )
                // current temperature with a sun / cloud / rain glyph; tap refreshes it
                val wx = weather
                val wxCode = weatherTestCode ?: wx?.code
                if (Look.INFO_WEATHER in info && wx != null && wxCode != null && style != ClockStyle.SPLIT) Row(
                    Modifier.padding(start = if (date.isEmpty()) 0.dp else 14.dp).clickable(interactionSource = null, indication = null) { refreshWeather(force = true) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val kind = weatherKind(wxCode)
                    // clear sky at night → the moon in today's phase
                    if (kind == WeatherKind.SUN && (moonTestPhase != null || wx.isNight(now))) MoonIcon(moonTestPhase ?: moonPhase(), Dim, Modifier.size(20.dp))
                    else WeatherIcon(kind, Dim, Modifier.size(20.dp))
                    BasicText("${wx.temp}°", Modifier.padding(start = 5.dp), style = SmallText.copy(fontSize = 16.sp))
                }
                // sound · vibrate · silent; tap shows the system volume panel (where the mode can be switched)
                // wifi (with its signal) · mobile data · offline; tap opens the system internet panel
                if (Look.INFO_NET in info) {
                    val (kind, level) = network(now)
                    NetIcon(
                        kind, level, Dim,
                        Modifier.padding(start = if (date.isEmpty()) 0.dp else 10.dp).size(26.dp).clip(CircleShape)
                            .clickable(interactionSource = null, indication = null) {
                                safeStart(Intent(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS))
                            }
                            .padding(5.dp),
                    )
                }
                if (Look.INFO_RINGER in info) RingerIcon(
                    ringerMode(), Dim,
                    Modifier.padding(start = if (date.isEmpty()) 0.dp else 12.dp).size(26.dp).clip(CircleShape)
                        .clickable(interactionSource = null, indication = null) {
                            audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                        }
                        .padding(5.dp),
                )
            }
            // battery · next event (tap: grant calendar access, then opens the calendar) · • n notifications
            val (level, charging) = battery()
            val event = if (Look.INFO_EVENT in info) remember(now, resumes) { nextEvent() } else null
            val alarm = if (Look.INFO_ALARM in info) remember(now, resumes) { nextAlarm() } else null
            val notified = if (Look.INFO_NOTIF in info) NotificationDots.packages.value.size else 0
            val tomorrow = stringResource(R.string.tomorrow)
            fun clock(at: LocalDateTime) = (if (at.toLocalDate() != now.toLocalDate()) "$tomorrow " else "") +
                at.format(DateTimeFormatter.ofPattern(Look.timePattern())).lowercase()
            val status = listOfNotNull(
                    if (Look.INFO_BATTERY in info) stringResource(if (charging) R.string.battery_charging else R.string.battery, level) else null,
                    alarm?.let { stringResource(R.string.alarm_at, clock(it)) },
                    event?.let { (title, at) -> "${clock(at)} $title" },
                ).joinToString("  ·  ")
            if (status.isNotEmpty() || notified > 0) Row(Modifier.padding(top = 6.dp).alignInk(status, 14.sp, 400)) {
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
                    (if (status.isEmpty()) "" else "  ·  ") + "• $notified",
                    Modifier.clickable(interactionSource = null, indication = null) { expandNotifications() },
                    style = SmallText.copy(fontSize = 14.sp, color = Fg),
                )
            }
        }
    }

    private val audio by lazy { getSystemService(AudioManager::class.java) }

    /** Current ringer mode, kept live with the system broadcast. */
    @Composable
    private fun ringerMode(): Int {
        var mode by remember { mutableIntStateOf(audio.ringerMode) }
        DisposableEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) { mode = audio.ringerMode }
            }
            val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(receiver, filter, RECEIVER_EXPORTED)
            else registerReceiver(receiver, filter)
            onDispose { unregisterReceiver(receiver) }
        }
        return mode
    }

    /** Network kind (NET_*) and signal level 0–3, kept live with the default-network callback. */
    @Composable
    private fun network(now: LocalDateTime): Pair<Int, Int> {
        val cm = remember { getSystemService(android.net.ConnectivityManager::class.java) }
        fun kindOf(c: android.net.NetworkCapabilities?) = when {
            c == null -> NET_NONE
            c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> NET_WIFI
            c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> NET_CELL
            else -> NET_OTHER
        }
        var caps by remember { mutableStateOf(cm.getNetworkCapabilities(cm.activeNetwork)) }
        DisposableEffect(Unit) {
            val cb = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(n: android.net.Network, c: android.net.NetworkCapabilities) { caps = c }
                override fun onLost(n: android.net.Network) { caps = null }
            }
            runCatching { cm.registerDefaultNetworkCallback(cb, android.os.Handler(mainLooper)) }
            onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
        }
        val kind = kindOf(caps)
        val level = remember(kind, caps, now) {
            when (kind) {
                NET_WIFI -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps!!.signalStrength != android.net.NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED)
                    @Suppress("DEPRECATION") android.net.wifi.WifiManager.calculateSignalLevel(caps!!.signalStrength, 4) else 3
                // phone signal 0–4 → 0–3 (no permission needed); refreshed once a minute with the clock
                NET_CELL -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    ((getSystemService(android.telephony.TelephonyManager::class.java).signalStrength?.level ?: 4) * 3 + 3) / 4 else 3
                else -> 0
            }
        }
        return kind to level
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

    // Next alarm set in the clock app, if it rings within 24h
    private fun nextAlarm(): LocalDateTime? {
        val at = getSystemService(android.app.AlarmManager::class.java).nextAlarmClock?.triggerTime ?: return null
        if (at - System.currentTimeMillis() > 24 * 3_600_000L) return null
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(at), java.time.ZoneId.systemDefault())
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
        if (!Look.showRecents) { recents = emptyList(); return }
        // snapshot UI state on the main thread, do the work on a background thread
        val byPkg = apps.associateBy { it.pkg }
        val byId = apps.associateBy { it.id }
        val fav = favorites
        val hid = hidden
        lifecycleScope.launch {
            val ordered = withContext(Dispatchers.Default) {
                if (hasUsageAccess()) recentPackages().mapNotNull { byPkg[it] }
                else prefs.getString("recent", "")!!.split("\n").mapNotNull { byId[it] }
            }
            recents = ordered.filter { it.id !in fav && it.id !in hid }.distinctBy { it.pkg }.take(5)
        }
    }

    // Usage history cache: the first call reads 3 days of events, later calls only what happened since the last read.
    private val usageLast = HashMap<String, Long>() // package → last time it came to the foreground
    private var usageReadUntil = 0L

    private fun recentPackages(): List<String> = synchronized(usageLast) {
        val usm = getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val window = 3 * 86_400_000L
        val events = usm.queryEvents(if (usageReadUntil == 0L) now - window else usageReadUntil, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION") // == ACTIVITY_RESUMED (API 29) with the same value
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && e.packageName != packageName) usageLast[e.packageName] = e.timeStamp
        }
        usageReadUntil = now
        usageLast.entries.removeAll { it.value < now - window }
        // apply "remove" / "clear all" cut-offs to the view, not the cache
        val clearedAt = prefs.getLong("recentClearedAt", 0)
        usageLast.entries.filter { (pkg, time) -> time > maxOf(clearedAt, prefs.getLong("rd:$pkg", 0)) }
            .sortedByDescending { it.value }.map { it.key }
    }
}

/** Main color of a photo (for the wallpaper theme), or null before Android 8.1. */
private fun photoAccent(bmp: android.graphics.Bitmap): Int? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) runCatching { android.app.WallpaperColors.fromBitmap(bmp).primaryColor.toArgb() }.getOrNull() else null

internal const val STAR = "★" // bar entry that lists the favorites

/** Thin five-pointed star outline (the favorites entry of the letter bar). */
@Composable
private fun StarIcon(color: Color, modifier: Modifier) = Canvas(modifier) {
    val c = Offset(size.width / 2, size.height * 0.54f)
    val outer = size.minDimension / 2
    val path = Path()
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) outer else outer * 0.45f
        val a = Math.toRadians(-90.0 + k * 36.0)
        val p = Offset(c.x + r * kotlin.math.cos(a).toFloat(), c.y + r * kotlin.math.sin(a).toFloat())
        if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color, style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
}

// A–Z strip: vertical at the right edge, or horizontal above the favorites (settings → letter bar).
// Tap or slide along it to pick a letter; while touching, a bubble with the letter follows the finger.
@Composable
private fun LetterBar(letters: List<String>, selected: String?, horizontal: Boolean = false, left: Boolean = false, onSelect: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var touch by remember { mutableStateOf<Float?>(null) } // finger position along the strip, null when not touching
    // landscape leaves ~380dp of height for ~22 letters: smaller margins and letters so they all fit
    val tight = LocalConfiguration.current.run { screenWidthDp > screenHeightDp }
    val pad = if (horizontal) 0.dp else if (tight) 12.dp else 48.dp
    val strip = Modifier.pointerInput(letters, horizontal) {
        var last: String? = null
        fun length() = (if (horizontal) size.width else size.height).toFloat()
        fun pick(pos: Float) {
            if (letters.isEmpty()) return
            touch = pos.coerceIn(0f, length())
            val l = letters[(pos / length() * letters.size).toInt().coerceIn(letters.indices)]
            if (l != last) {
                when (Look.barHaptics) { // settings → home → vibration
                    1 -> haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    2 -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    3 -> haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                }
                last = l; onSelect(l)
            }
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
    val accent = Accent
    val dim = Dim
    val shadow = TextShadow
    var stripLen by remember { mutableFloatStateOf(0f) }
    @Composable
    fun Letter(l: String, i: Int) {
        val on = l == selected
        val scale by animateFloatAsState(if (on) 1.55f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "letter")
        val base by animateColorAsState(if (on) accent else dim, tween(160), label = "letterColor")
        // Bend: letters near the finger lean toward the screen (left for the side bar, up for the bottom one),
        // falling off smoothly (gaussian over ~2–3 letters) and easing back when the finger lifts.
        val t = touch
        val near = if (t == null || stripLen <= 0f) 0f else {
            val cell = stripLen / letters.size
            val d = (t - (i + 0.5f) * cell) / (cell * 2.4f)
            kotlin.math.exp(-d * d)
        }
        val bend by animateFloatAsState(near, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessHigh), label = "bend") // tracks the finger closely
        val bent = Modifier.graphicsLayer {
            val s = scale * (1f + 0.18f * bend)
            scaleX = s; scaleY = s
            if (horizontal) translationY = -bend * 14.dp.toPx() else translationX = (if (left) bend else -bend) * 24.dp.toPx()
        }
        val color = if (on) base else androidx.compose.ui.graphics.lerp(base, fg, 0.55f * bend)
        if (l == STAR) StarIcon(color, bent.size(if (tight && !horizontal) 10.dp else 12.dp))
        else BasicText(
            l.lowercase(), bent,
            style = TextStyle(
                color = color, fontFamily = Thin, shadow = shadow, fontSize = if (tight && !horizontal) 11.sp else 13.sp,
                fontWeight = if (on) FontWeight.Normal else FontWeight.Light,
            ),
        )
    }
    BoxWithConstraints(if (horizontal) Modifier.fillMaxWidth().height(40.dp) else Modifier.width(44.dp).fillMaxHeight()) {
        // side bar: compact 22dp cells, sitting low (thumb reach) — not stretched over the whole height
        val stripH = minOf(maxHeight - pad * 2, 22.dp * letters.size)
        val top = pad + (maxHeight - pad * 2 - stripH) * 0.8f
        if (horizontal) {
            // fixed-width cells so the strip doesn't shift when the selected letter grows
            Row(Modifier.fillMaxSize().onSizeChanged { stripLen = it.width.toFloat() }.then(strip), verticalAlignment = Alignment.CenterVertically) {
                letters.forEachIndexed { i, l -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Letter(l, i) } }
            }
        } else {
            // fixed-height cells: every letter fits and the finger→letter mapping (pos / height * count) matches the drawing
            Column(Modifier.fillMaxWidth().padding(top = top).height(stripH).onSizeChanged { stripLen = it.height.toFloat() }.then(strip)) {
                letters.forEachIndexed { i, l -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Letter(l, i) } }
            }
        }
        // Bubble: grows out of the strip (from under the finger) to its spot beside it, and sinks back on release.
        // The last letter/position are kept so the bubble can finish its way back after the finger lifts.
        val t = touch
        val bubble = 60.dp
        val emerge = remember { Animatable(0f) }
        var lastPos by remember { mutableFloatStateOf(0f) }
        var lastLetter by remember { mutableStateOf<String?>(null) }
        if (t != null) lastPos = t
        if (t != null && selected != null) lastLetter = selected
        LaunchedEffect(t != null) {
            if (t != null) emerge.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
            else emerge.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
        }
        val shown = lastLetter
        if (emerge.value > 0.01f && shown != null) {
            Box(
                Modifier
                    .offset {
                        val p = emerge.value
                        val b = bubble.toPx()
                        if (horizontal) {
                            val fromY = (40.dp.toPx() - b) / 2         // centered on the strip
                            val toY = -(b + 26.dp.toPx())              // resting spot above it (clear of the bent letters)
                            IntOffset((lastPos - b / 2).toInt(), (fromY + (toY - fromY) * p).toInt())
                        } else {
                            val fromX = (44.dp.toPx() - b) / 2         // centered on the strip
                            val toX = if (left) 44.dp.toPx() + 28.dp.toPx() else -(b + 28.dp.toPx()) // resting spot beside it, toward the screen
                            IntOffset((fromX + (toX - fromX) * p).toInt(), (lastPos + top.toPx() - b / 2).toInt())
                        }
                    }
                    .graphicsLayer {
                        val p = emerge.value
                        val scale = 0.2f + 0.8f * p                    // spring overshoot gives a small pop
                        scaleX = scale; scaleY = scale
                        alpha = p.coerceIn(0f, 1f)
                    }
                    .requiredSize(bubble) // the strip is only 44dp wide / 40dp tall: plain size() got squashed into an oval
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (shown == STAR) StarIcon(Bg, Modifier.size(26.dp))
                else BasicText(shown.uppercase(), style = TextStyle(color = Bg, fontSize = 30.sp, fontFamily = Thin, fontWeight = FontWeight.Normal))
            }
        }
    }
}

/**
 * Staggered entrance for list rows: fade in while rising a little — once, when the list opens.
 * Rows first composed later than that (scrolled into view) just appear, so scrolling never animates.
 */
@Composable
private fun Modifier.enterAnim(index: Int, listOpenedAt: Long): Modifier {
    val animate = remember { SystemClock.uptimeMillis() - listOpenedAt < 150 } // part of the first screenful
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    if (animate) LaunchedEffect(Unit) {
        delay(minOf(index, 10) * 28L)
        progress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
    }
    return graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * 18.dp.toPx() }
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
// Fades an edge only while there's more list behind it — at the end of the list the last row stays fully visible
@Composable
private fun Modifier.fadeEdges(state: LazyListState): Modifier {
    val top by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, tween(150), label = "fadeTop")
    val bottom by animateFloatAsState(if (state.canScrollForward) 1f else 0f, tween(150), label = "fadeBottom")
    return graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 1f - top), 0.06f to Color.Black,
                    0.85f to Color.Black, 1f to Color.Black.copy(alpha = 1f - bottom),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}

internal const val NET_NONE = 0
internal const val NET_WIFI = 1
internal const val NET_CELL = 2
internal const val NET_OTHER = 3 // ethernet, VPN-only…: shown as wifi

/** Thin wifi fan or phone-signal bars; arcs/bars above [level] (0–3) are faint. Offline: faint bars crossed out. */
@Composable
private fun NetIcon(kind: Int, level: Int, color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val sw = 1.4.dp.toPx()
    val faint = color.copy(alpha = 0.3f)
    if (kind == NET_WIFI || kind == NET_OTHER) {
        val c = Offset(w / 2, h * 0.82f)
        drawCircle(color, sw * 0.9f, c)
        for (i in 1..3) {
            val r = h * 0.22f * i
            drawArc(if (i <= level || kind == NET_OTHER) color else faint, 225f, 90f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
        }
    } else {
        // four rising bars (strokes), filled up to the level; lowest one always on when connected
        for (i in 0 until 4) {
            val x = w * (0.2f + i * 0.2f)
            val top = h * (0.72f - i * 0.16f)
            val on = kind == NET_CELL && i <= level
            drawLine(if (on) color else faint, Offset(x, h * 0.82f), Offset(x, top), sw * 1.3f, StrokeCap.Round)
        }
        if (kind == NET_NONE) drawLine(color, Offset(w * 0.12f, h * 0.18f), Offset(w * 0.88f, h * 0.86f), sw, StrokeCap.Round)
    }
}

/** Hand-drawn thin bell (sound), bell with a slash (silent), or phone with waves (vibrate) — matches the Jost strokes. */
@Composable
private fun RingerIcon(mode: Int, color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (mode == AudioManager.RINGER_MODE_VIBRATE) {
        drawRoundRect(color, Offset(w * 0.34f, h * 0.14f), Size(w * 0.32f, h * 0.72f), CornerRadius(w * 0.06f), style = stroke)
        listOf(0.16f, 0.06f).forEachIndexed { i, x ->
            val y0 = h * (0.34f - i * 0.08f); val y1 = h * (0.66f + i * 0.08f)
            drawLine(color, Offset(w * x + w * 0.04f, y0), Offset(w * x + w * 0.04f, y1), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * (1 - x) - w * 0.04f, y0), Offset(w * (1 - x) - w * 0.04f, y1), stroke.width, StrokeCap.Round)
        }
    } else {
        val bell = Path().apply {
            moveTo(w * 0.2f, h * 0.72f)
            lineTo(w * 0.8f, h * 0.72f)
            quadraticTo(w * 0.7f, h * 0.62f, w * 0.7f, h * 0.45f)
            cubicTo(w * 0.7f, h * 0.22f, w * 0.3f, h * 0.22f, w * 0.3f, h * 0.45f)
            quadraticTo(w * 0.3f, h * 0.62f, w * 0.2f, h * 0.72f)
            close()
        }
        drawPath(bell, color, style = stroke)
        drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.24f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.43f, h * 0.82f), Offset(w * 0.57f, h * 0.82f), stroke.width, StrokeCap.Round)
        if (mode == AudioManager.RINGER_MODE_SILENT) drawLine(color, Offset(w * 0.18f, h * 0.15f), Offset(w * 0.82f, h * 0.88f), stroke.width, StrokeCap.Round)
    }
}
