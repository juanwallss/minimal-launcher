package com.jparedes.minimallauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
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

data class App(val label: String, val pkg: String, val activity: String) {
    val id = "$pkg/$activity"
    val letter: String = label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"
}

// Palette / type — tweak here
private val Bg = Color(0xFF0A0A0A)
private val Fg = Color(0xFFE8E6E1)      // warm off-white
private val Dim = Color(0xFF6B6965)     // muted gray for secondary text

// Jost (variable font, OFL) — one file, instantiated at the weights we use
@OptIn(ExperimentalTextApi::class)
private val Thin = FontFamily(
    listOf(FontWeight.Thin, FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal).map {
        Font(R.font.jost, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
    }
)
private val RowText = TextStyle(color = Fg, fontSize = 24.sp, fontFamily = Thin, fontWeight = FontWeight.Light, letterSpacing = 0.5.sp)
private val SmallText = TextStyle(color = Dim, fontSize = 15.sp, fontFamily = Thin, letterSpacing = 1.sp)

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf(emptyList<App>())
    private var letter by mutableStateOf<String?>(null)
    private var query by mutableStateOf<String?>(null)   // null = not searching
    private var menuFor by mutableStateOf<String?>(null) // app id whose long-press menu is open
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }
    private var favorites by mutableStateOf(emptySet<String>())
    private var hidden by mutableStateOf(emptySet<String>())

    private fun toggle(set: Set<String>, app: App, pref: String) =
        (if (app.id in set) set - app.id else set + app.id).also { prefs.edit().putStringSet(pref, it).apply() }

    private fun resetView() { letter = null; query = null; menuFor = null }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler { if (menuFor != null) menuFor = null else resetView() } // home screen never exits
            val visible = apps.filter { it.id !in hidden }
            val q = query
            val listed = when {
                q != null -> if (q.isBlank()) emptyList() else apps.filter { it.label.contains(q.trim(), ignoreCase = true) }
                else -> visible.filter { it.letter == letter }
            }
            val clean = letter == null && q == null
            Row(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
                Column(
                    Modifier.weight(1f)
                        .then(if (clean) Modifier.pointerInput(Unit) { homeGestures() } else Modifier)
                        .padding(start = 36.dp, top = 96.dp)
                ) {
                    Clock()
                    if (q != null) SearchField(q) { listed.firstOrNull()?.let(::open) }
                    Box(Modifier.weight(1f)) {
                        key(letter, q == null) { // fresh scroll position per letter / search
                            LazyColumn(Modifier.fadeEdges()) {
                                items(listed, key = { it.id }) { AppRow(it) }
                            }
                        }
                    }
                    // Favorites pinned at the bottom as grayscale icons, always visible
                    Row(
                        Modifier.padding(top = 16.dp, bottom = 32.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        apps.filter { it.id in favorites }.forEach { FavoriteIcon(it) }
                    }
                }
                LetterBar(visible.map { it.letter }.distinct(), letter) { letter = it; query = null; menuFor = null }
            }
        }
    }

    // ponytail: reloads on every resume instead of listening for package changes; cheap enough for a home screen
    override fun onResume() {
        super.onResume()
        resetView() // pressing Home always lands on the clean clock view
        favorites = prefs.getStringSet("favorites", emptySet())!!.toSet()
        hidden = prefs.getStringSet("hidden", emptySet())!!.toSet()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .sortedBy { it.label.lowercase() }
    }

    // Clean home view: swipe down → notifications, swipe up → search, double tap → lock
    private suspend fun PointerInputScope.homeGestures() = coroutineScope {
        launch { detectTapGestures(onDoubleTap = { lockScreen() }) }
        var dragged = 0f
        var fired = false
        detectVerticalDragGestures(onDragStart = { dragged = 0f; fired = false }) { change, dy ->
            dragged += dy
            if (fired) return@detectVerticalDragGestures
            val threshold = 80.dp.toPx()
            if (dragged > threshold) { fired = true; change.consume(); expandNotifications() }
            if (dragged < -threshold) { fired = true; change.consume(); query = "" }
        }
    }

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
                Box { if (q.isEmpty()) BasicText("buscar", style = RowText.copy(color = Dim)); inner() }
            },
        )
    }

    // Tap opens; long-press shows: favorito · info · ocultar · desinstalar
    @Composable
    private fun AppRow(app: App) {
        val haptics = LocalHapticFeedback.current
        val dot = app.pkg in NotificationDots.packages.value
        Column {
            BasicText(
                app.label.lowercase() + if (dot) "  •" else "",
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        interactionSource = null, indication = null,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuFor = if (menuFor == app.id) null else app.id
                        },
                    ) { open(app) }
                    .padding(vertical = 11.dp),
                style = if (app.id in hidden) RowText.copy(color = Dim) else RowText,
            )
            if (menuFor == app.id) {
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    MenuItem(if (app.id in favorites) "quitar favorito" else "favorito") {
                        favorites = toggle(favorites, app, "favorites")
                    }
                    MenuItem("info") {
                        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                    }
                    MenuItem(if (app.id in hidden) "mostrar" else "ocultar") {
                        hidden = toggle(hidden, app, "hidden")
                    }
                    MenuItem("desinstalar") {
                        safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
                    }
                }
            }
        }
    }

    @Composable
    private fun MenuItem(label: String, onClick: () -> Unit) = BasicText(
        label,
        Modifier.clickable(interactionSource = null, indication = null) { menuFor = null; onClick() }
            .padding(vertical = 6.dp),
        style = SmallText,
    )

    // Tap opens; long-press removes from favorites
    @Composable
    private fun FavoriteIcon(app: App) {
        val haptics = LocalHapticFeedback.current
        val icon = remember(app.id) {
            val d = packageManager.getActivityIcon(ComponentName(app.pkg, app.activity))
            val px = (48 * resources.displayMetrics.density).toInt()
            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also {
                d.setBounds(0, 0, px, px); d.draw(Canvas(it))
            }.asImageBitmap()
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                icon, app.label,
                Modifier.size(44.dp).combinedClickable(
                    interactionSource = null, indication = null,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        favorites = toggle(favorites, app, "favorites")
                    },
                ) { open(app) },
                alpha = 0.85f,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            )
            val dot = app.pkg in NotificationDots.packages.value
            Box(Modifier.padding(top = 6.dp).size(4.dp).background(if (dot) Fg else Color.Transparent, CircleShape))
        }
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
            BasicText(
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                Modifier.clickable(interactionSource = null, indication = null) {
                    safeStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                },
                style = TextStyle(color = Fg, fontSize = 72.sp, fontFamily = Thin, fontWeight = FontWeight.Thin),
            )
            BasicText(
                now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")).lowercase(),
                Modifier.combinedClickable(
                    interactionSource = null, indication = null,
                    onLongClick = { safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                ) { safeStart(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)) },
                style = SmallText.copy(fontSize = 16.sp),
            )
        }
    }

    private fun lockScreen() {
        val service = LockService.instance
        if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(this, "Activa \"Minimal: doble toque para bloquear\" en Accesibilidad", Toast.LENGTH_LONG).show()
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

    private fun open(app: App) = safeStart(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(app.pkg, app.activity)
    )
}

// Vertical A–Z strip: tap or drag your thumb along it to pick a letter
@Composable
private fun LetterBar(letters: List<String>, selected: String?, onSelect: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(
        Modifier.width(44.dp).fillMaxHeight().padding(vertical = 48.dp)
            .pointerInput(letters) {
                fun pick(y: Float) {
                    if (letters.isEmpty()) return
                    val l = letters[(y / size.height * letters.size).toInt().coerceIn(letters.indices)]
                    if (l != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(l)
                }
                awaitEachGesture {
                    pick(awaitFirstDown().position.y)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { pick(it.position.y); it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { l ->
            val on = l == selected
            BasicText(
                l.lowercase(),
                style = TextStyle(
                    color = if (on) Fg else Dim, fontFamily = Thin,
                    fontSize = if (on) 20.sp else 13.sp,
                    fontWeight = if (on) FontWeight.Normal else FontWeight.Light,
                ),
            )
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
