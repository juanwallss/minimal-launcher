package com.jparedes.minimallauncher

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Build
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import android.os.SystemClock
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Locale
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.annotation.SuppressLint
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.text.style.TextAlign

/** A contact for one-tap calling in driving mode. */
data class DriveContact(val name: String, val number: String)

/**
 * Driving mode on/off. Turned on by a gesture, settings, or answering "yes" when the chosen car Bluetooth connects
 * (CarReceiver sets [ask]); turned off by disconnecting or "exit". Persisted so it survives the launcher process being restarted.
 */
internal object Drive {
    var active by mutableStateOf(false)
    var permTick by mutableIntStateOf(0) // bumped when Bluetooth permission changes, so settings re-read devices

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    var ask by mutableStateOf(false) // car connected: the home view asks whether to turn driving mode on
    fun load(ctx: Context) { prefs(ctx).run { active = getBoolean("driveActive", false); ask = getBoolean("driveAsk", false) } }
    fun set(ctx: Context, on: Boolean) {
        active = on
        ask = false
        prefs(ctx).edit().putBoolean("driveActive", on).putBoolean("driveAsk", false).apply()
    }
    fun setAsk(ctx: Context, on: Boolean) {
        ask = on
        prefs(ctx).edit().putBoolean("driveAsk", on).apply()
    }

    private val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")

    /**
     * Quick WhatsApp message. If [to] wrote recently, answers through WhatsApp's own notification reply: sent with
     * this one tap (returns true). Otherwise opens the chat with [text] typed in — WhatsApp needs the send tap.
     */
    fun quickSend(ctx: Context, to: DriveContact, text: String): Boolean {
        val name = to.name.trim()
        val chat = NotificationDots.messages.value.firstOrNull {
            it.pkg in WHATSAPP && it.reply != null && it.title.trim().equals(name, ignoreCase = true)
        }
        if (chat != null && NotificationDots.reply(ctx, chat, text, fromSuggestion = false)) return true
        val iso = ctx.getSystemService(TelephonyManager::class.java)?.networkCountryIso?.uppercase()?.ifEmpty { null } ?: Locale.getDefault().country
        val phone = (PhoneNumberUtils.formatNumberToE164(to.number, iso) ?: to.number).filter { it.isDigit() }
        val pkg = WHATSAPP.firstOrNull { p -> runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess }
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=${Uri.encode(text)}"))
                    .apply { pkg?.let(::setPackage) }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return false
    }
}

/** Home-view card when the car connects: "driving mode?  yes · no". */
@Composable
internal fun DriveAsk(onYes: () -> Unit, onNo: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Fg.copy(alpha = 0.06f))
            .border(1.dp, Dim.copy(alpha = 0.25f), RoundedCornerShape(22.dp)).padding(18.dp),
    ) {
        BasicText(stringResource(R.string.drive_ask_car), style = SmallText.copy(fontSize = 13.sp))
        BasicText(stringResource(R.string.drive_ask), Modifier.padding(top = 6.dp), style = RowText.copy(fontSize = 22.sp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.drive_yes), onClick = onYes)
            BigButton(stringResource(R.string.drive_no), dim = true, onClick = onNo)
        }
    }
}

/** The three replies for answerable notifications while driving: the user's own, or the defaults. */
@Composable
internal fun driveReplies(): List<String> = Look.driveReplies.ifEmpty {
    listOf(stringResource(R.string.r_1), stringResource(R.string.r_2), stringResource(R.string.r_3))
}

/** The three quick messages: the user's own, or the defaults in the phone's language. */
@Composable
internal fun quickMessages(): List<String> = Look.quickMsgs.ifEmpty {
    listOf(stringResource(R.string.q_1), stringResource(R.string.q_2), stringResource(R.string.q_3))
}

/**
 * Car Bluetooth link up/down → driving mode on/off. Manifest receiver (ACL broadcasts are exempt from the implicit
 * broadcast ban); Android only delivers them once the user granted "Nearby devices" (BLUETOOTH_CONNECT).
 */
class CarReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val car = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString("carAddress", null) ?: return
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        if (device?.address != car) return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> { Drive.load(ctx); if (!Drive.active) Drive.setAsk(ctx, true) }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> Drive.set(ctx, false)
        }
    }
}


// Home-screen look for DriveAsk's buttons (the launcher's own style, not driving mode's)
private val CardShape = RoundedCornerShape(26.dp)
private val CardColor @Composable get() = Fg.copy(alpha = 0.07f)

@Composable
private fun RowScope.BigButton(label: String, dim: Boolean = false, onClick: () -> Unit) = Box(
    Modifier.weight(1f).height(72.dp).clip(CardShape).background(CardColor).clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) { BasicText(label, style = RowText.copy(fontSize = 20.sp, color = if (dim) Dim else Fg), maxLines = 1) }

// ---------------------------------------------------------------- driving screen
// Its own look, always dark whatever the launcher theme: near-black, filled cards, cyan accent, bold type.

private object D {
    val bg = Color(0xFF0A0D12)
    val card = Color(0xFF141922)
    val panel = Color(0xFF10141B)
    val cyan = Color(0xFF3DD6F5)
    val text = Color(0xFFF2F4F7)
    val dim = Color(0xFF8A94A3)
    val track = Color(0xFF262D38)
    val redBg = Color(0xFF3A1B1E)
    val red = Color(0xFFFF7A7A)
    val shape = RoundedCornerShape(28.dp)
}

private fun dText(size: Int, color: Color = D.text, weight: FontWeight = FontWeight.Bold, spacing: Float = 0f) =
    TextStyle(color = color, fontSize = size.sp, fontFamily = FontFamilies[0], fontWeight = weight, letterSpacing = spacing.sp)

/** Small caps-like label over a value: "SPEED", "ARRIVAL". */
@Composable
private fun DLabel(text: String, modifier: Modifier = Modifier) =
    BasicText(text.uppercase(), modifier, style = dText(15, D.dim, spacing = 2f), maxLines = 2)

private enum class Panel { NAV, MESSAGES, PHONE }

/**
 * Driving screen. Portrait: clock · weather, the big center panel, speed + arrival, compact player, bottom bar.
 * Landscape: speed + arrival on the left, the center panel, player + 2×2 buttons on the right.
 * The center panel shows the next turn (from the maps app's notification); Phone and Messages swap it for calls
 * or for the big notifications + quick WhatsApp messages, and tapping the active button again goes back.
 */
@Composable
fun DriveScreen(
    contacts: List<DriveContact>,
    quickTo: DriveContact?,
    weather: String?,              // "24°C · Tijuana"
    speedAllowed: Boolean,         // precise location granted
    onAskSpeed: () -> Unit,
    onOpenMap: () -> Unit,
    onCall: (DriveContact) -> Unit,
    onDial: () -> Unit,
    onExit: () -> Unit,
) {
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1000L * (60 - value.second)) }
    }
    var panel by remember { mutableStateOf(Panel.NAV) }
    fun toggle(p: Panel) { panel = if (panel == p) Panel.NAV else p }
    val speed = rememberSpeed(speedAllowed)
    val nav = NotificationDots.nav.value
    val ctx = LocalContext.current
    val openPlayer = {
        val pm = ctx.packageManager
        val pkg = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString("lastPlayer", null)
        val launch = pkg?.let { pm.getLaunchIntentForPackage(it) }
            ?: pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC), 0)
                .firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        launch?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        Unit
    }
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(now.format(DateTimeFormatter.ofPattern(Look.timePattern())).uppercase(), Modifier.weight(1f), style = dText(30))
            weather?.let { BasicText(it, style = dText(18, D.dim, FontWeight.Normal), maxLines = 1) }
        }
    }
    val center: @Composable (Modifier) -> Unit = { m ->
        Box(m.clip(D.shape).background(D.panel)) {
            when (panel) {
                Panel.NAV -> NavPanel(nav, onOpenMap)
                Panel.MESSAGES -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    quickTo?.let { QuickMessages(it) }
                    DriveNotes()
                }
                Panel.PHONE -> PhonePanel(contacts, onCall, onDial)
            }
        }
    }
    val wide = LocalConfiguration.current.run { screenWidthDp > screenHeightDp || screenWidthDp >= 600 }
    val outer = Modifier.fillMaxSize().background(D.bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 16.dp)
    val gap = Arrangement.spacedBy(14.dp)
    if (!wide) Column(outer, verticalArrangement = gap) {
        header()
        center(Modifier.weight(1f).fillMaxWidth())
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = gap) {
            SpeedCard(speed, speedAllowed, onAskSpeed, wide = false, modifier = Modifier.weight(1.7f).fillMaxHeight())
            ArrivalCard(nav, wide = false, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        PlayerCard(compact = true, onOpenPlayer = openPlayer)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DTile(stringResource(R.string.d_map), "nav", panel == Panel.NAV, Modifier.weight(1f)) { if (panel == Panel.NAV) onOpenMap() else panel = Panel.NAV }
            DTile(stringResource(R.string.d_messages) + badge(), "chat", panel == Panel.MESSAGES, Modifier.weight(1f)) { toggle(Panel.MESSAGES) }
            DTile(stringResource(R.string.d_phone), "phone", panel == Panel.PHONE, Modifier.weight(1f)) { toggle(Panel.PHONE) }
            DTile(stringResource(R.string.d_exit), "x", false, Modifier.weight(1f), exit = true, onClick = onExit)
        }
    } else Row(outer, horizontalArrangement = gap) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = gap) {
            header()
            SpeedCard(speed, speedAllowed, onAskSpeed, wide = true, modifier = Modifier.weight(1f).fillMaxWidth())
            ArrivalCard(nav, wide = true, modifier = Modifier.fillMaxWidth())
        }
        center(Modifier.weight(1.8f).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = gap) {
            PlayerCard(compact = false, onOpenPlayer = openPlayer, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = gap) {
                DTile(stringResource(R.string.d_phone), "phone", panel == Panel.PHONE, Modifier.weight(1f)) { toggle(Panel.PHONE) }
                DTile(stringResource(R.string.d_messages) + badge(), "chat", panel == Panel.MESSAGES, Modifier.weight(1f)) { toggle(Panel.MESSAGES) }
            }
            Row(horizontalArrangement = gap) {
                DTile(stringResource(R.string.d_music), "music", false, Modifier.weight(1f), onClick = openPlayer)
                DTile(stringResource(R.string.d_exit), "x", false, Modifier.weight(1f), exit = true, onClick = onExit)
            }
        }
    }
}

/** " 3" after "Messages" when notifications wait. */
private fun badge(): String = NotificationDots.notes.value.size.let { if (it > 0) " $it" else "" }

/** Speed in km/h from GPS while driving mode is on; null with no recent fix. */
@SuppressLint("MissingPermission") // only requested once precise location is granted
@Composable
private fun rememberSpeed(allowed: Boolean): Int? {
    val ctx = LocalContext.current
    var kmh by remember { mutableStateOf<Int?>(null) }
    var at by remember { mutableStateOf(0L) }
    DisposableEffect(allowed) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val listener = LocationListener { loc ->
            kmh = if (loc.hasSpeed()) (loc.speed * 3.6f).roundToInt() else null
            at = SystemClock.elapsedRealtime()
        }
        if (allowed) runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper()) }
        onDispose { runCatching { lm.removeUpdates(listener) } }
    }
    // a fix older than 5 s (tunnel, GPS lost) shows "—" instead of a frozen number
    LaunchedEffect(at) { if (at > 0) { delay(5000); kmh = null } }
    return kmh
}

@Composable
private fun SpeedCard(kmh: Int?, allowed: Boolean, onAsk: () -> Unit, wide: Boolean, modifier: Modifier) {
    val number = kmh?.toString() ?: "—"
    Column(
        modifier.clip(D.shape).background(D.card).then(if (allowed) Modifier else Modifier.clickable(onClick = onAsk)).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = if (wide) Arrangement.SpaceBetween else Arrangement.Center,
    ) {
        if (wide) DLabel(stringResource(if (allowed) R.string.d_speed else R.string.d_speed_ask))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(number, style = dText(if (wide) 76 else 64, if (kmh == null) D.dim else D.cyan, spacing = -2f).copy(lineHeight = (if (wide) 76 else 64).sp), maxLines = 1)
            BasicText("km/h", Modifier.padding(start = 8.dp, bottom = 10.dp), style = dText(18, D.dim, FontWeight.Normal))
        }
        if (!allowed && !wide) BasicText(stringResource(R.string.d_speed_ask), style = dText(14, D.dim, FontWeight.Normal))
        else if (!wide) DLabel(stringResource(R.string.d_speed), Modifier.padding(top = 4.dp))
    }
}

/** Arrival time + "15 min · 9.2 km", read from the maps app's notification text. */
@Composable
private fun ArrivalCard(nav: Nav?, wide: Boolean, modifier: Modifier) {
    val (eta, rest) = remember(nav) { nav?.let { splitEta(it.sub.ifBlank { it.text }) } ?: (null to "") }
    Column(modifier.clip(D.shape).background(D.card).padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.Center) {
        DLabel(stringResource(R.string.d_arrival))
        if (wide) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(eta ?: "—", Modifier.weight(1f), style = dText(34), maxLines = 1)
            if (rest.isNotEmpty()) BasicText(rest.replace(" · ", "\n"), style = dText(18, D.dim, FontWeight.Normal).copy(textAlign = TextAlign.End))
        } else {
            BasicText(eta ?: "—", Modifier.padding(top = 4.dp), style = dText(30), maxLines = 2)
            if (rest.isNotEmpty()) BasicText(rest, Modifier.padding(top = 2.dp), style = dText(16, D.dim, FontWeight.Normal), maxLines = 2)
        }
    }
}

private val ETA_TIME = Regex("""\b\d{1,2}:\d{2}(\s?[AaPp]\.?\s?[Mm]\.?)?""")

/** "12 min · 4.2 km · arrive 18:42" → ("18:42", "12 min · 4.2 km"). */
internal fun splitEta(s: String): Pair<String?, String> {
    val time = ETA_TIME.find(s)?.value
    val rest = s.split("·", "•").map { it.trim() }.filter { it.isNotEmpty() && (time == null || !it.contains(time)) }.joinToString(" · ")
    return time?.uppercase() to rest
}

/** Center panel while not in calls/messages: the next turn, or a button to open the map. */
@Composable
private fun NavPanel(nav: Nav?, onOpenMap: () -> Unit) {
    val ctx = LocalContext.current
    if (nav == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            DriveIcon("nav", D.dim, Modifier.size(56.dp))
            BasicText(stringResource(R.string.d_no_route), Modifier.padding(top = 16.dp), style = dText(24, D.dim))
            BasicText(
                stringResource(R.string.d_open_map),
                Modifier.padding(top = 22.dp).clip(RoundedCornerShape(50)).background(D.cyan).clickable(onClick = onOpenMap).padding(horizontal = 28.dp, vertical = 16.dp),
                style = dText(19, D.bg),
            )
        }
        return
    }
    val arrow = remember(nav.icon) { nav.icon?.asImageBitmap() }
    Column(Modifier.fillMaxSize().clickable { NotificationDots.send(ctx, nav.open) }.padding(16.dp)) {
        // banner: cyan square with the maneuver arrow · distance / street
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(D.bg.copy(alpha = 0.7f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)).background(D.cyan), contentAlignment = Alignment.Center) {
                if (arrow != null) Image(arrow, null, Modifier.size(48.dp), colorFilter = ColorFilter.tint(D.bg))
                else DriveIcon("turn", D.bg, Modifier.size(40.dp))
            }
            Column(Modifier.padding(start = 18.dp).weight(1f)) {
                BasicText(nav.title.ifBlank { stringResource(R.string.drive_navigating) }, style = dText(34), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (nav.text.isNotBlank()) BasicText(nav.text, Modifier.padding(top = 2.dp), style = dText(19, D.dim, FontWeight.Normal), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // the same arrow, large, in a soft cyan halo (where the map would be)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp).background(D.cyan.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                if (arrow != null) Image(arrow, null, Modifier.size(92.dp), colorFilter = ColorFilter.tint(D.cyan))
                else DriveIcon("turn", D.cyan, Modifier.size(80.dp))
            }
        }
    }
}

/** Favorite contacts as big tiles (initial + name) and the dialer. */
@Composable
private fun PhonePanel(contacts: List<DriveContact>, onCall: (DriveContact) -> Unit, onDial: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (contacts.map { it as DriveContact? } + listOf<DriveContact?>(null)).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { c ->
                    Row(
                        Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(22.dp)).background(D.card)
                            .clickable { if (c != null) onCall(c) else onDial() }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).background(D.cyan.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                            if (c != null) BasicText(c.name.take(1).uppercase(), style = dText(19, D.cyan))
                            else DriveIcon("phone", D.cyan, Modifier.size(22.dp))
                        }
                        BasicText(c?.name ?: stringResource(R.string.d_dial), Modifier.padding(start = 12.dp), style = dText(19), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Bottom bar / grid button: drawn icon over a label. Active = cyan, exit = red. */
@Composable
private fun DTile(label: String, icon: String, active: Boolean, modifier: Modifier, exit: Boolean = false, onClick: () -> Unit) {
    val tint = when { exit -> D.red; active -> D.cyan; else -> D.text }
    Column(
        modifier.height(if (LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp) 76.dp else 92.dp).clip(RoundedCornerShape(24.dp)).background(if (exit) D.redBg else D.card).clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DriveIcon(icon, tint, Modifier.size(30.dp))
        BasicText(label, Modifier.padding(top = 8.dp), style = dText(16, tint), maxLines = 1)
    }
}

/** Line icons for the driving screen. */
@Composable
private fun DriveIcon(kind: String, color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val st = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (kind) {
        "phone" -> drawPath(Path().apply {
            moveTo(w * 0.26f, h * 0.12f); lineTo(w * 0.4f, h * 0.32f); lineTo(w * 0.32f, h * 0.44f)
            quadraticTo(w * 0.42f, h * 0.6f, w * 0.56f, h * 0.68f); lineTo(w * 0.68f, h * 0.6f); lineTo(w * 0.88f, h * 0.74f)
            lineTo(w * 0.78f, h * 0.88f); quadraticTo(w * 0.3f, h * 0.8f, w * 0.12f, h * 0.24f); close()
        }, color, style = st)
        "chat" -> drawPath(Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(w * 0.12f, h * 0.14f, w * 0.88f, h * 0.78f))
            moveTo(w * 0.26f, h * 0.68f); lineTo(w * 0.16f, h * 0.9f); lineTo(w * 0.4f, h * 0.76f)
        }, color, style = st)
        "music" -> {
            drawCircle(color, w * 0.12f, Offset(w * 0.28f, h * 0.76f), style = st)
            drawCircle(color, w * 0.12f, Offset(w * 0.74f, h * 0.68f), style = st)
            drawPath(Path().apply { moveTo(w * 0.4f, h * 0.76f); lineTo(w * 0.4f, h * 0.2f); lineTo(w * 0.86f, h * 0.12f); lineTo(w * 0.86f, h * 0.68f) }, color, style = st)
            drawLine(color, Offset(w * 0.4f, h * 0.34f), Offset(w * 0.86f, h * 0.26f), st.width)
        }
        "x" -> {
            drawLine(color, Offset(w * 0.2f, h * 0.2f), Offset(w * 0.8f, h * 0.8f), st.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.8f, h * 0.2f), Offset(w * 0.2f, h * 0.8f), st.width, StrokeCap.Round)
        }
        "nav" -> drawPath(Path().apply { moveTo(w * 0.88f, h * 0.12f); lineTo(w * 0.12f, h * 0.44f); lineTo(w * 0.48f, h * 0.52f); lineTo(w * 0.56f, h * 0.88f); close() }, color, style = st)
        "turn" -> drawPath(Path().apply {
            moveTo(w * 0.3f, h * 0.9f); lineTo(w * 0.3f, h * 0.5f); quadraticTo(w * 0.3f, h * 0.36f, w * 0.44f, h * 0.36f); lineTo(w * 0.8f, h * 0.36f)
            moveTo(w * 0.64f, h * 0.2f); lineTo(w * 0.8f, h * 0.36f); lineTo(w * 0.64f, h * 0.52f)
        }, color, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Notifications in large type: app · when, who / title, the text. Tap opens it, × dismisses it; chats get one-tap replies. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriveNotes() {
    val ctx = LocalContext.current
    val notes = NotificationDots.notes.value
    if (notes.isEmpty()) {
        BasicText(stringResource(R.string.d_no_notes), Modifier.padding(top = 8.dp), style = dText(22, D.dim))
        return
    }
    val replies = driveReplies().filter { it.isNotBlank() }
    var sent by remember { mutableStateOf<String?>(null) } // key of the notification just answered
    LaunchedEffect(sent) { if (sent != null) { delay(2000); sent = null } }
    notes.take(8).forEach { m ->
        val app = remember(m.pkg) { runCatching { ctx.packageManager.run { getApplicationLabel(getApplicationInfo(m.pkg, 0)).toString() } }.getOrDefault("") }
        val icon = remember(m.pkg) {
            runCatching {
                val d = ctx.packageManager.getApplicationIcon(m.pkg)
                val px = (32 * ctx.resources.displayMetrics.density).toInt()
                android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
                    .also { d.setBounds(0, 0, px, px); d.draw(android.graphics.Canvas(it)) }.asImageBitmap()
            }.getOrNull()
        }
        val ago = (System.currentTimeMillis() - m.time) / 60_000
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(D.card)
                .clickable { NotificationDots.send(ctx, m.open) }.padding(start = 20.dp, top = 16.dp, bottom = 18.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let { Image(it, null, Modifier.padding(end = 8.dp).size(18.dp)) }
                    BasicText(
                        "$app  ·  " + if (ago < 1) stringResource(R.string.msg_now) else stringResource(R.string.msg_min, ago.toInt()),
                        style = dText(15, D.dim, FontWeight.Normal), maxLines = 1,
                    )
                }
                if (m.title.isNotBlank()) BasicText(m.title, Modifier.padding(top = 6.dp), style = dText(24), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m.text.isNotBlank()) BasicText(m.text, Modifier.padding(top = 4.dp), style = dText(20, D.text.copy(alpha = 0.8f), FontWeight.Normal), maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (m.reply != null && replies.isNotEmpty()) FlowRow(
                    Modifier.padding(top = 14.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (sent == m.key) DPill(stringResource(R.string.msg_sent)) {}
                    else replies.forEach { r -> DPill(r) { if (NotificationDots.reply(ctx, m, r, fromSuggestion = false)) sent = m.key } }
                }
            }
            DriveIcon("x", D.dim, Modifier.size(52.dp).clip(CircleShape).clickable { NotificationDots.dismiss(m) }.padding(17.dp))
        }
    }
}

/** Large pill for short actions (replies, quick messages). */
@Composable
private fun DPill(label: String, onClick: () -> Unit) = Box(
    Modifier.height(60.dp).clip(RoundedCornerShape(50)).background(D.cyan.copy(alpha = 0.14f))
        .clickable(onClick = onClick).padding(horizontal = 22.dp),
    contentAlignment = Alignment.Center,
) { BasicText(label, style = dText(17, D.cyan), maxLines = 1) }

/** "WhatsApp to …" and the three quick messages; a sent one shows "sent ✓" for a moment. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickMessages(to: DriveContact) {
    val ctx = LocalContext.current
    var sent by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sent) { if (sent != null) { delay(2000); sent = null } }
    Column {
        DLabel(stringResource(R.string.d_quick, to.name))
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            quickMessages().filter { it.isNotBlank() }.forEach { text ->
                DPill(if (sent == text) stringResource(R.string.msg_sent) else text) { if (Drive.quickSend(ctx, to, text)) sent = text }
            }
        }
    }
}

/**
 * Player card. Compact (portrait): cover · title / artist + progress line · play button.
 * Full (landscape): cover · title / artist, progress with times, previous · play · next. Tap the title to open the app.
 */
@Composable
private fun PlayerCard(compact: Boolean, onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
    val media = rememberMediaNow()
    val c = media.controller
    val meta = remember(media.tick, c) { c?.metadata }
    val state = remember(media.tick, c) { c?.playbackState }
    val playing = state?.state == PlaybackState.STATE_PLAYING
    val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val artist = meta?.run { getString(MediaMetadata.METADATA_KEY_ARTIST) ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) }
    val art = remember(meta) {
        meta?.run { getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: getBitmap(MediaMetadata.METADATA_KEY_ART) ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) }?.asImageBitmap()
    }
    val ctx = LocalContext.current
    val open = { c?.packageName?.let { ctx.packageManager.getLaunchIntentForPackage(it) }?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } ?: onOpenPlayer(); Unit }
    val cover: @Composable (Int) -> Unit = { s ->
        Box(Modifier.size(s.dp).clip(RoundedCornerShape(18.dp)).background(D.track).clickable(onClick = open), contentAlignment = Alignment.Center) {
            if (art != null && title != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else DriveIcon("music", D.dim, Modifier.size((s / 2.6f).dp))
        }
    }
    val names: @Composable (Modifier, Int) -> Unit = { m, size ->
        Column(m.clickable(interactionSource = null, indication = null, onClick = open)) {
            if (title != null) BasicText(title, Modifier.basicMarquee(), style = dText(size), maxLines = 1)
            else BasicText(stringResource(R.string.d_nothing), style = dText(size, D.dim), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (title != null && artist != null) BasicText(artist, Modifier.padding(top = 2.dp), style = dText(size - 6, D.dim, FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val playButton: @Composable (Int) -> Unit = { s ->
        Box(
            Modifier.size(s.dp).clip(CircleShape).background(D.cyan)
                .clickable { if (c == null || title == null) onOpenPlayer() else if (playing) c.transportControls.pause() else c.transportControls.play() },
            contentAlignment = Alignment.Center,
        ) { PlayerIcon(if (playing) "pause" else "play", D.bg, Modifier.size((s * 0.42f).dp)) }
    }
    val progress: @Composable (Modifier, Boolean) -> Unit = { m, times ->
        if (c != null && meta != null) MediaProgress(c, meta, state, playing, media.tick, m, big = !compact, fill = D.cyan, track = D.track, timeColor = D.dim, times = times)
    }
    Column(modifier.fillMaxWidth().clip(D.shape).background(D.card).padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
        if (compact) Row(verticalAlignment = Alignment.CenterVertically) {
            cover(64)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                names(Modifier, 21)
                progress(Modifier.padding(top = 6.dp), false)
            }
            playButton(68)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                cover(60)
                Column(Modifier.padding(start = 14.dp).weight(1f)) { names(Modifier, 20); progress(Modifier.padding(top = 4.dp), false) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(D.track).clickable { c?.transportControls?.skipToPrevious() }, contentAlignment = Alignment.Center) {
                    PlayerIcon("prev", D.text, Modifier.size(26.dp))
                }
                playButton(68)
                Box(Modifier.size(56.dp).clip(CircleShape).background(D.track).clickable { c?.transportControls?.skipToNext() }, contentAlignment = Alignment.Center) {
                    PlayerIcon("next", D.text, Modifier.size(26.dp))
                }
            }
        }
    }
}
