package com.jparedes.minimallauncher

import android.content.Context
import android.content.SharedPreferences
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import android.app.AppOpsManager
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** User look & feel settings, observable from Compose and saved in prefs. */
internal object Look {
    const val BG_NONE = "none"
    const val BG_SYSTEM = "system"
    const val BG_PHOTO = "photo"

    var background by mutableStateOf(BG_NONE)
    const val DIM_MIN = 80                 // below this the photo fights with the text
    const val DIM_MAX = 98
    var dim by mutableIntStateOf(85)       // % of the theme color laid over the background
    var gray by mutableStateOf(true)       // photo in black & white
    var clockSize by mutableIntStateOf(1)  // 0 small · 1 medium · 2 large
    var clockStyle by mutableIntStateOf(0) // ClockStyle ordinal
    var textSize by mutableIntStateOf(1)
    var clock24 by mutableStateOf(true)
    var showRecents by mutableStateOf(true)
    var showMessages by mutableStateOf(true)
    var showWidgets by mutableStateOf(false)
    // driving mode
    var carAddress by mutableStateOf<String?>(null)   // Bluetooth MAC of the car; null = no auto start
    var carName by mutableStateOf<String?>(null)
    var homeAddress by mutableStateOf("")
    var workAddress by mutableStateOf("")
    var driveContacts by mutableStateOf(emptyList<DriveContact>())
    var barBottom by mutableStateOf(false) // letter bar: right edge (false) or above the favorites (true)
    var barLeft by mutableStateOf(false)   // side bar on the left edge (left-handed)

    // theme = one accent color (+ a faint tint of the background): none, from the wallpaper, or a preset
    const val THEME_NONE = "none"
    const val THEME_WALL = "wall"
    val THEMES = linkedMapOf("amber" to 0xFFE0A15A.toInt(), "sage" to 0xFF9DB89A.toInt(), "sea" to 0xFF7FA7C9.toInt(), "rose" to 0xFFD99AA5.toInt(), "lilac" to 0xFFAFA0D9.toInt())
    var theme by mutableStateOf(THEME_NONE)
    var photoAccent by mutableStateOf<Int?>(null) // main color of the launcher's photo background, set when it loads

    /** gesture → action: none · lock · notifications · search · camera · torch · app:<pkg>/<activity> */
    val GESTURE_DEFAULTS = linkedMapOf("double" to "lock", "up" to "search", "down" to "notifications", "left" to "none", "right" to "none")
    val gestures = mutableStateMapOf<String, String>()

    val clockSp get() = listOf(64f, 88f, 112f)[clockSize]
    val textSp get() = listOf(20f, 24f, 28f)[textSize]
    fun timePattern() = if (clock24) "HH:mm" else "h:mm a"

    private var prefs: SharedPreferences? = null

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).also { prefs = it }
        background = p.getString("bg", BG_NONE)!!
        dim = p.getInt("bgDim", 85).coerceIn(DIM_MIN, DIM_MAX)
        gray = p.getBoolean("bgGray", true)
        clockSize = p.getInt("clockSize", 1)
        clockStyle = p.getInt("clockStyle", 0)
        textSize = p.getInt("textSize", 1)
        clock24 = p.getBoolean("clock24", DateFormat.is24HourFormat(ctx)) // default follows the phone
        barBottom = p.getBoolean("barBottom", false)
        barLeft = p.getBoolean("barLeft", false)
        theme = p.getString("theme", THEME_NONE)!!
        showRecents = p.getBoolean("showRecents", true)
        showMessages = p.getBoolean("showMessages", true)
        showWidgets = p.getBoolean("showWidgets", false)
        carAddress = p.getString("carAddress", null)
        carName = p.getString("carName", null)
        homeAddress = p.getString("homeAddress", "")!!
        workAddress = p.getString("workAddress", "")!!
        driveContacts = p.getString("driveContacts", "")!!.split("\n")
            .mapNotNull { line -> line.split("\t").takeIf { it.size == 2 }?.let { DriveContact(it[0], it[1]) } }
        GESTURE_DEFAULTS.forEach { (g, default) -> gestures[g] = p.getString("g_$g", default)!! }
    }

    fun save() {
        val e = prefs?.edit() ?: return
        e.putString("bg", background).putInt("bgDim", dim).putBoolean("bgGray", gray)
            .putInt("clockSize", clockSize).putInt("clockStyle", clockStyle).putInt("textSize", textSize).putBoolean("clock24", clock24).putBoolean("barBottom", barBottom).putBoolean("barLeft", barLeft).putString("theme", theme).putBoolean("showRecents", showRecents).putBoolean("showMessages", showMessages).putBoolean("showWidgets", showWidgets)
            .putString("carAddress", carAddress).putString("carName", carName)
            .putString("homeAddress", homeAddress).putString("workAddress", workAddress)
            .putString("driveContacts", driveContacts.joinToString("\n") { "${it.name}\t${it.number}" })
        gestures.forEach { (g, action) -> e.putString("g_$g", action) }
        e.apply()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(Modifier.padding(top = 30.dp)) {
        SectionLabel(title)
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

@Composable
private fun SectionLabel(title: String) = BasicText(title, style = SmallText.copy(fontSize = 12.sp, letterSpacing = 1.5.sp))

/** Text option; the chosen one is bright with a thin underline. */
@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val line = Accent
    BasicText(
        label,
        Modifier.clickable(interactionSource = null, indication = null, onClick = onClick).padding(vertical = 4.dp)
            .drawBehind { if (selected) drawLine(line, Offset(0f, size.height + 2.dp.toPx()), Offset(size.width, size.height + 2.dp.toPx()), 1.dp.toPx()) },
        style = RowText.copy(fontSize = 19.sp, color = if (selected) Fg else Dim),
    )
}

/** A theme option: small color dot + name. The wallpaper one shows the color it picked once chosen. */
@Composable
private fun ThemeChoice(label: String, key: String, swatch: Color?, set: (() -> Unit) -> Unit) {
    val on = Look.theme == key
    val dot = swatch ?: if (on) Accent else null
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) Box(Modifier.padding(end = 8.dp).size(9.dp).background(dot, CircleShape))
        Choice(label, on) { set { Look.theme = key } }
    }
}

private enum class SettingsPage { APPEARANCE, HOME, GESTURES, DRIVE, PERMISSIONS }

/**
 * Full-screen settings, opened by long-pressing the time. Opaque (the wallpaper never shows behind it):
 * a list of categories with a one-line summary each, and one page per category.
 */
@Composable
fun SettingsScreen(
    onPickPhoto: () -> Unit,
    onBackgroundChanged: () -> Unit,
    apps: List<App>,
    onAskBluetooth: () -> Unit,
    onPickContact: () -> Unit,
    background: ImageBitmap?,
    resumeTick: Int,          // bumps when the launcher resumes, so permission states re-read after visiting Settings
    onClose: () -> Unit,
) {
    fun set(change: () -> Unit) { change(); Look.save() }
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    var pickingAppFor by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null || pickingAppFor != null) {
        if (pickingAppFor != null) pickingAppFor = null else page = null
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        pickingAppFor?.let { g ->
            AppPicker(apps, onPick = { app -> set { Look.gestures[g] = "app:${app.id}" }; pickingAppFor = null }, onCancel = { pickingAppFor = null })
            return@Box
        }
        AnimatedContent(
            page,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(tween(260)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(160)))
            },
            label = "settingsPage",
        ) { p ->
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())
                    .padding(start = 36.dp, end = 28.dp, top = 48.dp, bottom = 48.dp).widthIn(max = 620.dp)
            ) {
                if (p == null) {
                    BasicText(stringResource(R.string.s_title), Modifier.padding(bottom = 28.dp), style = RowText.copy(fontSize = 44.sp, fontWeight = FontWeight.Thin))
                    Categories(apps, resumeTick) { page = it }
                    BasicText(
                        stringResource(R.string.s_done),
                        Modifier.padding(top = 40.dp).clickable(interactionSource = null, indication = null, onClick = onClose).padding(vertical = 6.dp),
                        style = RowText.copy(fontSize = 19.sp),
                    )
                } else {
                    // ‹ settings  /  page title
                    BasicText(
                        "‹  " + stringResource(R.string.s_title),
                        Modifier.clickable(interactionSource = null, indication = null) { page = null }.padding(vertical = 6.dp),
                        style = SmallText,
                    )
                    BasicText(stringResource(p.title), Modifier.padding(top = 10.dp, bottom = 8.dp), style = RowText.copy(fontSize = 38.sp, fontWeight = FontWeight.Thin))
                    when (p) {
                        SettingsPage.APPEARANCE -> AppearancePage(::set, onPickPhoto, onBackgroundChanged, background)
                        SettingsPage.HOME -> HomePage(::set)
                        SettingsPage.GESTURES -> GesturesPage(::set, apps) { pickingAppFor = it }
                        SettingsPage.DRIVE -> DriveSettings(set = ::set, onAskBluetooth = onAskBluetooth, onPickContact = onPickContact, onClose = onClose)
                        SettingsPage.PERMISSIONS -> PermissionsPage(resumeTick)
                    }
                }
            }
        }
    }
}

private val SettingsPage.title get() = when (this) {
    SettingsPage.APPEARANCE -> R.string.cat_appearance
    SettingsPage.HOME -> R.string.cat_home
    SettingsPage.GESTURES -> R.string.g_title
    SettingsPage.DRIVE -> R.string.s_drive
    SettingsPage.PERMISSIONS -> R.string.cat_permissions
}

private val GESTURE_ACTIONS = listOf(
    "none" to R.string.g_none, "lock" to R.string.g_lock, "notifications" to R.string.g_notifications,
    "search" to R.string.g_search, "camera" to R.string.g_camera, "torch" to R.string.g_torch, "drive" to R.string.g_drive,
)
private val GESTURE_NAMES = mapOf(
    "double" to R.string.g_double, "up" to R.string.g_up, "down" to R.string.g_down,
    "left" to R.string.g_left, "right" to R.string.g_right,
)

@Composable
private fun actionLabel(action: String, apps: List<App>): String =
    if (action.startsWith("app:")) apps.find { it.id == action.removePrefix("app:") }?.label?.lowercase() ?: stringResource(R.string.g_app)
    else stringResource(GESTURE_ACTIONS.firstOrNull { it.first == action }?.second ?: R.string.g_none)

/** Category rows: title, current-values summary, chevron; hairline dividers between. */
@Composable
private fun Categories(apps: List<App>, resumeTick: Int, open: (SettingsPage) -> Unit) {
    val sizes = listOf(R.string.s_small, R.string.s_medium, R.string.s_large).map { stringResource(it) }
    val bg = stringResource(when (Look.background) { Look.BG_PHOTO -> R.string.s_bg_photo_short; Look.BG_SYSTEM -> R.string.s_bg_system; else -> R.string.s_bg_none })
    val perms = permissionStates(resumeTick)
    val rows = listOf(
        SettingsPage.APPEARANCE to listOfNotNull(stringResource(ClockStyle.entries.getOrElse(Look.clockStyle) { ClockStyle.THIN }.label), bg, if (Look.background != Look.BG_NONE) "${Look.dim} %" else null,
            stringResource(R.string.s_clock) + " " + sizes[Look.clockSize], if (Look.clock24) "24 h" else "12 h").joinToString("  ·  "),
        SettingsPage.HOME to listOf(
            stringResource(R.string.s_bar) + " " + stringResource(if (Look.barBottom) R.string.s_bar_bottom else if (Look.barLeft) R.string.s_bar_left else R.string.s_bar_side),
            stringResource(R.string.s_recents) + " " + stringResource(if (Look.showRecents) R.string.s_on else R.string.s_off),
            stringResource(R.string.s_messages) + " " + stringResource(if (Look.showMessages) R.string.s_on else R.string.s_off),
            stringResource(R.string.s_widgets) + " " + stringResource(if (Look.showWidgets) R.string.s_on else R.string.s_off),
        ).joinToString("  ·  "),
        SettingsPage.GESTURES to listOf("double", "up", "down").map { g -> // map is inline, so composable calls are fine here
            stringResource(GESTURE_NAMES.getValue(g)) + ": " + actionLabel(Look.gestures[g] ?: "none", apps)
        }.joinToString("  ·  "),
        SettingsPage.DRIVE to listOf(
            Look.carName?.lowercase() ?: stringResource(R.string.s_no_car),
            stringResource(R.string.s_contacts_count, Look.driveContacts.size),
        ).joinToString("  ·  "),
        SettingsPage.PERMISSIONS to stringResource(R.string.perm_summary, perms.count { it.on }, perms.size),
    )
    val divider = Dim.copy(alpha = 0.35f)
    rows.forEachIndexed { i, (page, summary) ->
        if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(divider))
        Row(
            Modifier.fillMaxWidth().clickable(interactionSource = null, indication = null) { open(page) }.padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(page.title), style = RowText.copy(fontSize = 23.sp))
                BasicText(summary, Modifier.padding(top = 4.dp), style = SmallText.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicText("›", Modifier.padding(start = 12.dp), style = RowText.copy(fontSize = 26.sp, color = Dim))
        }
    }
}

@Composable
private fun AppearancePage(set: (() -> Unit) -> Unit, onPickPhoto: () -> Unit, onBackgroundChanged: () -> Unit, background: ImageBitmap?) {
    val sizes = listOf(R.string.s_small, R.string.s_medium, R.string.s_large).map { stringResource(it) }
    Preview(background)
    Section(stringResource(R.string.s_background)) {
        Choice(stringResource(R.string.s_bg_none), Look.background == Look.BG_NONE) { set { Look.background = Look.BG_NONE }; onBackgroundChanged() }
        Choice(stringResource(R.string.s_bg_system), Look.background == Look.BG_SYSTEM) { set { Look.background = Look.BG_SYSTEM }; onBackgroundChanged() }
        // tapping "photo…" always opens the picker, so it doubles as "change photo"
        Choice(stringResource(R.string.s_bg_photo), Look.background == Look.BG_PHOTO, onPickPhoto)
    }
    if (Look.background != Look.BG_NONE) Column(Modifier.padding(top = 30.dp)) {
        SectionLabel(stringResource(R.string.s_dim))
        DimSlider()
    }
    if (Look.background == Look.BG_PHOTO) Section(stringResource(R.string.s_gray)) {
        Choice(stringResource(R.string.s_on), Look.gray) { set { Look.gray = true } }
        Choice(stringResource(R.string.s_off), !Look.gray) { set { Look.gray = false } }
    }
    Section(stringResource(R.string.s_clock_style)) {
        ClockStyle.entries.forEach { st -> Choice(stringResource(st.label), Look.clockStyle == st.ordinal) { set { Look.clockStyle = st.ordinal } } }
    }
    Section(stringResource(R.string.s_theme)) {
        ThemeChoice(stringResource(R.string.th_none), Look.THEME_NONE, null, set)
        ThemeChoice(stringResource(R.string.th_wall), Look.THEME_WALL, null, set)
        val names = mapOf("amber" to R.string.th_amber, "sage" to R.string.th_sage, "sea" to R.string.th_sea, "rose" to R.string.th_rose, "lilac" to R.string.th_lilac)
        Look.THEMES.forEach { (key, argb) -> ThemeChoice(stringResource(names.getValue(key)), key, Color(argb), set) }
    }
    Section(stringResource(R.string.s_clock)) {
        sizes.forEachIndexed { i, label -> Choice(label, Look.clockSize == i) { set { Look.clockSize = i } } }
    }
    Section(stringResource(R.string.s_text)) {
        sizes.forEachIndexed { i, label -> Choice(label, Look.textSize == i) { set { Look.textSize = i } } }
    }
    Section(stringResource(R.string.s_format)) {
        Choice("24 h", Look.clock24) { set { Look.clock24 = true } }
        Choice("12 h", !Look.clock24) { set { Look.clock24 = false } }
    }
}

/**
 * Live preview of the home background (photo or system wallpaper + dim veil) with a sample clock,
 * since the settings panel itself is opaque. For the system wallpaper the card punches a hole through the
 * (transparent) window so Android's wallpaper shows inside it.
 */
@Composable
private fun Preview(background: ImageBitmap?) {
    val shape = RoundedCornerShape(22.dp)
    val bg = Bg
    Box(
        Modifier.padding(top = 12.dp).fillMaxWidth().height(170.dp).clip(shape).border(0.5.dp, Dim.copy(alpha = 0.5f), shape)
            .drawBehind {
                if (Look.background == Look.BG_SYSTEM) drawRect(Color.Black, blendMode = BlendMode.Clear) // window hole → wallpaper
                else if (Look.background == Look.BG_NONE) drawRect(bg)
            },
    ) {
        if (Look.background == Look.BG_PHOTO && background != null) {
            Image(background, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = if (Look.gray) Grayscale else null)
        }
        if (Look.background != Look.BG_NONE) Box(Modifier.fillMaxSize().background(bg.copy(alpha = Look.dim / 100f)))
        val now = remember { java.time.LocalDateTime.now() }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) {
            val style = ClockStyle.entries.getOrElse(Look.clockStyle) { ClockStyle.THIN }
            val time = now.format(java.time.format.DateTimeFormatter.ofPattern(Look.timePattern())).lowercase()
            val short = now.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")).trimEnd('.')
            ClockFace(style, now, Look.clockSp * 0.55f, band = listOf(time.uppercase(), short.uppercase()))
            val line = when (style) {
                ClockStyle.DAY, ClockStyle.BINARY -> "$time  ·  ${short.lowercase()}"
                ClockStyle.SPLIT -> ""
                else -> now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM")).lowercase()
            }
            if (line.isNotEmpty()) BasicText(line, style = SmallText.copy(fontSize = 12.sp))
        }
    }
}

@Composable
private fun HomePage(set: (() -> Unit) -> Unit) {
    Section(stringResource(R.string.s_bar)) {
        Choice(stringResource(R.string.s_bar_side), !Look.barBottom && !Look.barLeft) { set { Look.barBottom = false; Look.barLeft = false } }
        Choice(stringResource(R.string.s_bar_left), !Look.barBottom && Look.barLeft) { set { Look.barBottom = false; Look.barLeft = true } }
        Choice(stringResource(R.string.s_bar_bottom), Look.barBottom) { set { Look.barBottom = true } }
    }
    Section(stringResource(R.string.s_recents)) {
        val ctx = LocalContext.current
        Choice(stringResource(R.string.show), Look.showRecents) { set { Look.showRecents = true } }
        Choice(stringResource(R.string.hide), !Look.showRecents) { set { Look.showRecents = false } }
        // optional: with usage access the row also sees apps opened from notifications / links
        Choice(stringResource(R.string.s_usage), ctx.hasUsageAccess()) {
            runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    // newest chat on the home view, answerable in place (needs notification access)
    // widget pages under the clock (hidden by default; the music player at the bottom covers the common case)
    Section(stringResource(R.string.s_widgets)) {
        Choice(stringResource(R.string.show), Look.showWidgets) { set { Look.showWidgets = true } }
        Choice(stringResource(R.string.hide), !Look.showWidgets) { set { Look.showWidgets = false } }
    }
    Section(stringResource(R.string.s_messages)) {
        Choice(stringResource(R.string.show), Look.showMessages) { set { Look.showMessages = true } }
        Choice(stringResource(R.string.hide), !Look.showMessages) { set { Look.showMessages = false } }
    }
}

@Composable
private fun GesturesPage(set: (() -> Unit) -> Unit, apps: List<App>, pickApp: (String) -> Unit) {
    Look.GESTURE_DEFAULTS.keys.forEach { g ->
        val current = Look.gestures[g] ?: "none"
        Section(stringResource(GESTURE_NAMES.getValue(g))) {
            GESTURE_ACTIONS.forEach { (action, label) -> Choice(stringResource(label), current == action) { set { Look.gestures[g] = action } } }
            val chosenApp = current.takeIf { it.startsWith("app:") }?.removePrefix("app:")?.let { id -> apps.find { it.id == id } }
            Choice(chosenApp?.label?.lowercase() ?: stringResource(R.string.g_app), chosenApp != null) { pickApp(g) }
        }
    }
}

private class PermissionState(val label: Int, val on: Boolean, val open: Intent)

/** Every optional permission the launcher uses, whether it's on, and where to change it. */
@Composable
private fun permissionStates(resumeTick: Int): List<PermissionState> {
    val ctx = LocalContext.current
    return remember(resumeTick, Drive.permTick) {
        fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        val appDetails = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}"))
        val listeners = android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        listOfNotNull(
            PermissionState(R.string.p_notifications, ctx.packageName in listeners, Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
            PermissionState(R.string.p_accessibility, LockService.instance != null, Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            PermissionState(R.string.p_usage, ctx.hasUsageAccess(), Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)),
            PermissionState(R.string.p_calendar, granted(Manifest.permission.READ_CALENDAR), appDetails),
            PermissionState(R.string.p_location, granted(Manifest.permission.ACCESS_COARSE_LOCATION), appDetails),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PermissionState(R.string.p_nearby, granted(Manifest.permission.BLUETOOTH_CONNECT), appDetails) else null,
            PermissionState(R.string.p_calls, granted(Manifest.permission.CALL_PHONE), appDetails),
        )
    }
}

@Composable
private fun PermissionsPage(resumeTick: Int) {
    val ctx = LocalContext.current
    BasicText(stringResource(R.string.perm_hint), Modifier.padding(top = 4.dp, bottom = 12.dp), style = SmallText.copy(fontSize = 13.sp))
    val divider = Dim.copy(alpha = 0.35f)
    permissionStates(resumeTick).forEachIndexed { i, p ->
        if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(divider))
        Row(
            Modifier.fillMaxWidth().clickable(interactionSource = null, indication = null) {
                runCatching { ctx.startActivity(p.open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(stringResource(p.label), Modifier.weight(1f), style = RowText.copy(fontSize = 18.sp))
            BasicText(stringResource(if (p.on) R.string.state_on else R.string.state_off), style = SmallText.copy(color = if (p.on) Fg else Dim))
        }
    }
}

/** Pick the app a gesture opens. */
@Composable
private fun AppPicker(apps: List<App>, onPick: (App) -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 36.dp, end = 24.dp, top = 64.dp)) {
        BasicText(
            stringResource(R.string.cancel),
            Modifier.clickable(interactionSource = null, indication = null, onClick = onCancel).padding(bottom = 20.dp),
            style = SmallText,
        )
        LazyColumn {
            items(apps, key = { it.id }) { app ->
                BasicText(
                    app.label.lowercase().replaceFirstChar { it.uppercase() },
                    Modifier.fillMaxWidth().clickable(interactionSource = null, indication = null) { onPick(app) }.padding(vertical = 10.dp),
                    style = RowText,
                )
            }
        }
    }
}

/** Dim amount 80–98 %: tap or drag the line. Updates live; saved when the finger lifts. */
@Composable
private fun DimSlider() {
    val fg = Fg
    val dim = Dim
    val span = Look.DIM_MAX - Look.DIM_MIN
    fun valueAt(x: Float, width: Int) = (Look.DIM_MIN + x / width * span).roundToInt().coerceIn(Look.DIM_MIN, Look.DIM_MAX)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier.weight(1f).height(36.dp)
                .pointerInput(Unit) { detectTapGestures { Look.dim = valueAt(it.x, size.width); Look.save() } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = { Look.save() }, onDragCancel = { Look.save() }) { change, _ ->
                        change.consume()
                        Look.dim = valueAt(change.position.x, size.width)
                    }
                }
        ) {
            val y = size.height / 2
            val x = size.width * (Look.dim - Look.DIM_MIN) / span.toFloat()
            drawLine(dim, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(fg, Offset(0f, y), Offset(x, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(fg, radius = 8.dp.toPx(), center = Offset(x, y))
        }
        BasicText("${Look.dim} %", Modifier.padding(start = 18.dp).width(56.dp), style = RowText.copy(fontSize = 19.sp))
    }
}

/** Whether the user granted "usage access" (special permission, toggled in system settings). */
fun Context.hasUsageAccess(): Boolean {
    val ops = getSystemService(AppOpsManager::class.java)
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    } else {
        @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

/** Car (auto start), home / work addresses, favorite contacts, and a manual "start now". */
@SuppressLint("MissingPermission") // bonded devices are only read after BLUETOOTH_CONNECT is granted
@Composable
private fun DriveSettings(set: (() -> Unit) -> Unit, onAskBluetooth: () -> Unit, onPickContact: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Section(stringResource(R.string.s_car)) {
        val granted = Drive.permTick >= 0 && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        if (!granted) {
            Choice(stringResource(R.string.s_car_pick), false, onAskBluetooth)
        } else {
            val bonded = remember(Drive.permTick) {
                runCatching { ctx.getSystemService(BluetoothManager::class.java).adapter.bondedDevices.toList() }.getOrDefault(emptyList())
            }
            Choice(stringResource(R.string.s_bg_none), Look.carAddress == null) { set { Look.carAddress = null; Look.carName = null } }
            bonded.forEach { d ->
                val name = runCatching { d.name }.getOrNull() ?: d.address
                Choice(name.lowercase(), Look.carAddress == d.address) { set { Look.carAddress = d.address; Look.carName = name } }
            }
        }
    }
    AddressField(stringResource(R.string.s_home_addr), Look.homeAddress) { v -> set { Look.homeAddress = v } }
    AddressField(stringResource(R.string.s_work_addr), Look.workAddress) { v -> set { Look.workAddress = v } }
    Section(stringResource(R.string.s_contacts)) {
        Look.driveContacts.forEach { c -> Choice("${c.name.lowercase()}  ×", true) { set { Look.driveContacts = Look.driveContacts - c } } }
        if (Look.driveContacts.size < 4) Choice(stringResource(R.string.s_add_contact), false, onPickContact)
    }
    Section(stringResource(R.string.s_drive_now)) {
        Choice(stringResource(R.string.s_try), Drive.active) { Drive.set(ctx, true); onClose() }
    }
}

@Composable
private fun AddressField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(top = 30.dp)) {
        BasicText(label, style = SmallText.copy(fontSize = 13.sp))
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth().padding(top = 8.dp),
            textStyle = RowText.copy(fontSize = 19.sp),
            cursorBrush = SolidColor(Fg),
            singleLine = true,
            decorationBox = { inner ->
                Box { if (value.isEmpty()) BasicText(stringResource(R.string.s_addr_hint), style = RowText.copy(fontSize = 19.sp, color = Dim)); inner() }
            },
        )
    }
}
