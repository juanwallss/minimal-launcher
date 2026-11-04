package com.jparedes.minimallauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf(emptyList<App>())
    private var letter by mutableStateOf<String?>(null)
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }
    private var favorites by mutableStateOf(emptySet<String>())

    private fun toggleFavorite(app: App) {
        favorites = if (app.id in favorites) favorites - app.id else favorites + app.id
        prefs.edit().putStringSet("favorites", favorites).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler { letter = null } // back clears the filter; home screen never exits
            val letters = apps.map { it.letter }.distinct()
            Row(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
                Column(
                    Modifier.weight(1f)
                        .then(if (letter == null) Modifier.pointerInput(Unit) { swipeDownForNotifications() } else Modifier)
                        .padding(start = 36.dp, top = 96.dp)
                ) {
                    Clock()
                    Box(Modifier.weight(1f)) {
                        key(letter) { // fresh scroll position per letter
                            LazyColumn(Modifier.fadeEdges()) {
                                items(apps.filter { it.letter == letter }, key = { it.id }) { AppRow(it) }
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
                LetterBar(letters, letter) { letter = it }
            }
        }
    }

    // ponytail: reloads on every resume instead of listening for package changes; cheap enough for a home screen
    override fun onResume() {
        super.onResume()
        letter = null // pressing Home always lands on the clean clock view
        favorites = prefs.getStringSet("favorites", emptySet())!!.toSet()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .sortedBy { it.label.lowercase() }
    }

    // Tap opens; long-press adds/removes from favorites
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun AppRow(app: App) {
        val haptics = LocalHapticFeedback.current
        BasicText(
            app.label.lowercase(),
            Modifier.fillMaxWidth()
                .combinedClickable(
                    interactionSource = null, indication = null,
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); toggleFavorite(app) },
                ) { open(app) }
                .padding(vertical = 11.dp),
            style = TextStyle(
                color = Fg, fontSize = 24.sp, fontFamily = Thin,
                fontWeight = FontWeight.Light, letterSpacing = 0.5.sp,
            ),
        )
    }

    // Tap opens; long-press removes from favorites
    @OptIn(ExperimentalFoundationApi::class)
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
        Image(
            icon, app.label,
            Modifier.size(44.dp).combinedClickable(
                interactionSource = null, indication = null,
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); toggleFavorite(app) },
            ) { open(app) },
            alpha = 0.85f,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        )
    }

    // Swipe down on the clean home view → pull the notification shade
    private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.swipeDownForNotifications() {
        var dragged = 0f
        detectVerticalDragGestures(onDragStart = { dragged = 0f }) { change, dy ->
            dragged += dy
            if (dragged > 80.dp.toPx()) { change.consume(); dragged = Float.NEGATIVE_INFINITY; expandNotifications() }
        }
    }

    // Hidden API, but the standard way launchers do this; needs EXPAND_STATUS_BAR (normal permission)
    @android.annotation.SuppressLint("WrongConstant")
    private fun expandNotifications() = runCatching {
        val sb = getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    }

    private fun open(app: App) {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.pkg, app.activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
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
            style = TextStyle(color = Fg, fontSize = 72.sp, fontFamily = Thin, fontWeight = FontWeight.Thin),
        )
        BasicText(
            now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")).lowercase(),
            style = TextStyle(color = Dim, fontSize = 16.sp, fontFamily = Thin, letterSpacing = 1.sp),
        )
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
