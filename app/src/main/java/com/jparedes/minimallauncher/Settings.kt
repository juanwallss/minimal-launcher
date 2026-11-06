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
    var textSize by mutableIntStateOf(1)
    var clock24 by mutableStateOf(true)
    var showRecents by mutableStateOf(true)
    // driving mode
    var carAddress by mutableStateOf<String?>(null)   // Bluetooth MAC of the car; null = no auto start
    var carName by mutableStateOf<String?>(null)
    var homeAddress by mutableStateOf("")
    var workAddress by mutableStateOf("")
    var driveContacts by mutableStateOf(emptyList<DriveContact>())
    var barBottom by mutableStateOf(false) // letter bar: right edge (false) or above the favorites (true)

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
        textSize = p.getInt("textSize", 1)
        clock24 = p.getBoolean("clock24", DateFormat.is24HourFormat(ctx)) // default follows the phone
        barBottom = p.getBoolean("barBottom", false)
        showRecents = p.getBoolean("showRecents", true)
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
            .putInt("clockSize", clockSize).putInt("textSize", textSize).putBoolean("clock24", clock24).putBoolean("barBottom", barBottom).putBoolean("showRecents", showRecents)
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
        BasicText(title, style = SmallText.copy(fontSize = 13.sp))
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) = BasicText(
    label,
    Modifier.clickable(interactionSource = null, indication = null, onClick = onClick).padding(vertical = 4.dp),
    style = RowText.copy(fontSize = 19.sp, color = if (selected) Fg else Dim),
)

/** Full-screen settings, opened by long-pressing the time. */
@Composable
fun SettingsScreen(
    onPickPhoto: () -> Unit,
    onBackgroundChanged: () -> Unit,
    apps: List<App>,
    onAskBluetooth: () -> Unit,
    onPickContact: () -> Unit,
    onClose: () -> Unit,
) {
    fun set(change: () -> Unit) { change(); Look.save() }
    var pickingAppFor by remember { mutableStateOf<String?>(null) }
    pickingAppFor?.let { g ->
        AppPicker(apps, onPick = { app -> set { Look.gestures[g] = "app:${app.id}" }; pickingAppFor = null }, onCancel = { pickingAppFor = null })
        return
    }
    val sizes = listOf(R.string.s_small, R.string.s_medium, R.string.s_large).map { stringResource(it) }

    // widthIn keeps lines readable in landscape; in portrait the screen is narrower than the cap anyway
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 36.dp, end = 24.dp, top = 64.dp, bottom = 48.dp).widthIn(max = 620.dp)) {
        BasicText(stringResource(R.string.s_title), style = RowText.copy(fontSize = 40.sp, fontWeight = FontWeight.Thin))

        Section(stringResource(R.string.s_background)) {
            Choice(stringResource(R.string.s_bg_none), Look.background == Look.BG_NONE) { set { Look.background = Look.BG_NONE }; onBackgroundChanged() }
            Choice(stringResource(R.string.s_bg_system), Look.background == Look.BG_SYSTEM) { set { Look.background = Look.BG_SYSTEM }; onBackgroundChanged() }
            // tapping "photo…" always opens the picker, so it doubles as "change photo"
            Choice(stringResource(R.string.s_bg_photo), Look.background == Look.BG_PHOTO, onPickPhoto)
        }
        if (Look.background != Look.BG_NONE) Column(Modifier.padding(top = 30.dp)) {
            BasicText(stringResource(R.string.s_dim), style = SmallText.copy(fontSize = 13.sp))
            DimSlider()
        }
        if (Look.background == Look.BG_PHOTO) Section(stringResource(R.string.s_gray)) {
            Choice(stringResource(R.string.s_on), Look.gray) { set { Look.gray = true } }
            Choice(stringResource(R.string.s_off), !Look.gray) { set { Look.gray = false } }
        }
        Section(stringResource(R.string.s_clock)) {
            sizes.forEachIndexed { i, label -> Choice(label, Look.clockSize == i) { set { Look.clockSize = i } } }
        }
        Section(stringResource(R.string.s_text)) {
            sizes.forEachIndexed { i, label -> Choice(label, Look.textSize == i) { set { Look.textSize = i } } }
        }
        Section(stringResource(R.string.s_bar)) {
            Choice(stringResource(R.string.s_bar_side), !Look.barBottom) { set { Look.barBottom = false } }
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
        Section(stringResource(R.string.s_format)) {
            Choice("24 h", Look.clock24) { set { Look.clock24 = true } }
            Choice("12 h", !Look.clock24) { set { Look.clock24 = false } }
        }
        // Gestures on the empty home screen
        val actions = listOf(
            "none" to R.string.g_none, "lock" to R.string.g_lock, "notifications" to R.string.g_notifications,
            "search" to R.string.g_search, "camera" to R.string.g_camera, "torch" to R.string.g_torch, "drive" to R.string.g_drive,
        )
        val gestureNames = mapOf(
            "double" to R.string.g_double, "up" to R.string.g_up, "down" to R.string.g_down,
            "left" to R.string.g_left, "right" to R.string.g_right,
        )
        BasicText(stringResource(R.string.g_title), Modifier.padding(top = 44.dp), style = RowText.copy(fontSize = 26.sp, fontWeight = FontWeight.Thin))
        Look.GESTURE_DEFAULTS.keys.forEach { g ->
            val current = Look.gestures[g] ?: "none"
            Section(stringResource(gestureNames.getValue(g))) {
                actions.forEach { (action, label) -> Choice(stringResource(label), current == action) { set { Look.gestures[g] = action } } }
                val chosenApp = current.takeIf { it.startsWith("app:") }?.removePrefix("app:")?.let { id -> apps.find { it.id == id } }
                Choice(chosenApp?.label?.lowercase() ?: stringResource(R.string.g_app), chosenApp != null) { pickingAppFor = g }
            }
        }

        // Driving mode
        BasicText(stringResource(R.string.s_drive), Modifier.padding(top = 44.dp), style = RowText.copy(fontSize = 26.sp, fontWeight = FontWeight.Thin))
        DriveSettings(set = ::set, onAskBluetooth = onAskBluetooth, onPickContact = onPickContact, onClose = onClose)

        BasicText(
            stringResource(R.string.s_done),
            Modifier.padding(top = 44.dp).clickable(interactionSource = null, indication = null, onClick = onClose).padding(vertical = 6.dp),
            style = RowText.copy(fontSize = 19.sp),
        )
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
