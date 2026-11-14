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

// Driving mode look: soft filled cards (no outlines), large radius, accent only where it matters (play, icons, nav).
private val CardShape = RoundedCornerShape(26.dp)
private val CardColor @Composable get() = Fg.copy(alpha = 0.07f)

/** Big filled tile with an optional drawn icon (home · work · map · phone) or a contact's initial; easy to hit without looking. */
@Composable
private fun RowScope.BigButton(label: String, dim: Boolean = false, icon: String? = null, initial: Char? = null, onClick: () -> Unit) = Row(
    Modifier.weight(1f).height(84.dp).clip(CardShape).background(CardColor)
        .clickable(onClick = onClick).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = if (icon == null && initial == null) Arrangement.Center else Arrangement.Start,
) {
    val tint = if (dim) Dim else Accent
    if (icon != null) DriveIcon(icon, tint, Modifier.padding(end = 12.dp).size(30.dp))
    if (initial != null) Box(Modifier.padding(end = 12.dp).size(40.dp).background(tint.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
        BasicText(initial.uppercase(), style = RowText.copy(fontSize = 18.sp, color = tint))
    }
    BasicText(label, style = RowText.copy(fontSize = 20.sp, color = if (dim) Dim else Fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Thin line icons for the driving tiles. */
@Composable
private fun DriveIcon(kind: String, color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val st = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (kind) {
        "home" -> drawPath(Path().apply {
            moveTo(w * 0.12f, h * 0.48f); lineTo(w * 0.5f, h * 0.14f); lineTo(w * 0.88f, h * 0.48f)
            moveTo(w * 0.22f, h * 0.4f); lineTo(w * 0.22f, h * 0.86f); lineTo(w * 0.78f, h * 0.86f); lineTo(w * 0.78f, h * 0.4f)
            moveTo(w * 0.42f, h * 0.86f); lineTo(w * 0.42f, h * 0.62f); lineTo(w * 0.58f, h * 0.62f); lineTo(w * 0.58f, h * 0.86f)
        }, color, style = st)
        "work" -> {
            drawRoundRect(color, Offset(w * 0.1f, h * 0.32f), Size(w * 0.8f, h * 0.52f), CornerRadius(w * 0.08f), style = st)
            drawPath(Path().apply { moveTo(w * 0.36f, h * 0.32f); lineTo(w * 0.36f, h * 0.18f); lineTo(w * 0.64f, h * 0.18f); lineTo(w * 0.64f, h * 0.32f) }, color, style = st)
            drawLine(color, Offset(w * 0.1f, h * 0.54f), Offset(w * 0.9f, h * 0.54f), st.width)
        }
        "map" -> {
            drawPath(Path().apply {
                moveTo(w * 0.5f, h * 0.9f)
                cubicTo(w * 0.2f, h * 0.56f, w * 0.18f, h * 0.44f, w * 0.18f, h * 0.38f)
                cubicTo(w * 0.18f, h * 0.18f, w * 0.34f, h * 0.08f, w * 0.5f, h * 0.08f)
                cubicTo(w * 0.66f, h * 0.08f, w * 0.82f, h * 0.18f, w * 0.82f, h * 0.38f)
                cubicTo(w * 0.82f, h * 0.44f, w * 0.8f, h * 0.56f, w * 0.5f, h * 0.9f); close()
            }, color, style = st)
            drawCircle(color, w * 0.1f, Offset(w * 0.5f, h * 0.38f), style = st)
        }
        "phone" -> drawPath(Path().apply {
            moveTo(w * 0.26f, h * 0.12f); lineTo(w * 0.4f, h * 0.32f); lineTo(w * 0.32f, h * 0.44f)
            quadraticTo(w * 0.42f, h * 0.6f, w * 0.56f, h * 0.68f); lineTo(w * 0.68f, h * 0.6f); lineTo(w * 0.88f, h * 0.74f)
            lineTo(w * 0.78f, h * 0.88f); quadraticTo(w * 0.3f, h * 0.8f, w * 0.12f, h * 0.24f); close()
        }, color, style = st)
    }
}

/**
 * Driving screen: clock, big player, navigation (home / work / map) and favorite contacts.
 * Everything is large, high-contrast and one tap away.
 */
@Composable
fun DriveScreen(
    homeAddress: String,
    workAddress: String,
    contacts: List<DriveContact>,
    quickTo: DriveContact? = null,
    onNavigate: (String?) -> Unit,   // null = just open the map
    onCall: (DriveContact) -> Unit,
    onDial: () -> Unit,
    onSetup: () -> Unit,
    onExit: () -> Unit,
    favorites: List<App> = emptyList(),
    icon: (App) -> ImageBitmap = { error("no icons") },
    onOpen: (App) -> Unit = {},
) {
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1000L * (60 - value.second)) }
    }
    val wide = LocalConfiguration.current.run { screenWidthDp > screenHeightDp || screenWidthDp >= 600 }
    val header: @Composable () -> Unit = {
        // header: time · driving · exit
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    now.format(DateTimeFormatter.ofPattern(Look.timePattern())).lowercase(),
                    style = TextStyle(color = Fg, fontSize = 52.sp, fontFamily = Thin, fontWeight = FontWeight.Light, shadow = TextShadow),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Accent, CircleShape))
                    BasicText(
                        stringResource(R.string.drive_title) + "  ·  " + now.format(DateTimeFormatter.ofPattern("EEE d MMM")).lowercase().replace(".", ""),
                        Modifier.padding(start = 8.dp), style = SmallText,
                    )
                }
            }
            BasicText(
                stringResource(R.string.drive_exit),
                Modifier.clip(RoundedCornerShape(50)).background(CardColor).clickable(onClick = onExit).padding(horizontal = 20.dp, vertical = 12.dp),
                style = RowText.copy(fontSize = 18.sp),
            )
        }

    }
    val navAndCalls: @Composable () -> Unit = {
        // navigate (while already navigating, only "nearby" stays: room for the nav card and the player)
        if (NotificationDots.nav.value == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.drive_home), dim = homeAddress.isBlank(), icon = "home") { if (homeAddress.isBlank()) onSetup() else onNavigate(homeAddress) }
            BigButton(stringResource(R.string.drive_work), dim = workAddress.isBlank(), icon = "work") { if (workAddress.isBlank()) onSetup() else onNavigate(workAddress) }
            BigButton(stringResource(R.string.drive_map), icon = "map") { onNavigate(null) }
        }
        Nearby()
        quickTo?.let { QuickMessages(it) }

        // call: up to 4 favorites (2 per row) + dialer
        val rows = (contacts.map { it as DriveContact? } + listOf<DriveContact?>(null)).chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { c ->
                        if (c != null) BigButton(c.name.lowercase(), initial = c.name.firstOrNull()) { onCall(c) }
                        else BigButton(stringResource(R.string.drive_dial), dim = true, icon = "phone", onClick = onDial)
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        // the launcher's favorites, big and in grayscale, to open other apps without leaving driving mode
        if (favorites.isNotEmpty()) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            favorites.forEach { app ->
                Image(
                    icon(app), app.label,
                    Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(CardColor).clickable { onOpen(app) }.padding(10.dp),
                )
            }
        }
    }
    val outer = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 28.dp, vertical = 20.dp)
    // portrait: the player keeps its full size (squeezed into the leftover space its buttons shrank to nothing);
    // when everything doesn't fit, the screen scrolls instead
    if (!wide) BoxWithConstraints(outer) {
        Column(Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                header()
                NotificationDots.nav.value?.let { NavCard(it) }
            }
            DriveMusic(Modifier.padding(vertical = 28.dp))
            Pages(Modifier) { Column(verticalArrangement = Arrangement.spacedBy(28.dp)) { navAndCalls() } }
        }
    } else Row(outer, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        // landscape (phone on a car mount): player on the left, destinations and calls on the right
        Column(Modifier.weight(1f).fillMaxHeight()) {
            header()
            NotificationDots.nav.value?.let { NavCard(it, Modifier.padding(top = 16.dp)) }
            DriveMusic(Modifier.weight(1f))
        }
        Pages(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) { navAndCalls() }
        }
    }
}

/** Next turn while the maps app navigates (read from its notification); tap goes back to the map. */
@Composable
private fun NavCard(n: Nav, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Row(
        modifier.fillMaxWidth().clip(CardShape).background(Accent.copy(alpha = 0.16f))
            .clickable { NotificationDots.send(ctx, n.open) }.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val arrow = remember(n.icon) { n.icon?.asImageBitmap() }
        arrow?.let { Image(it, null, Modifier.padding(end = 16.dp).size(56.dp), colorFilter = ColorFilter.tint(Accent)) }
        Column(Modifier.weight(1f)) {
            BasicText(n.title.ifBlank { stringResource(R.string.drive_navigating) }, style = RowText.copy(fontSize = 28.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.text.isNotBlank()) BasicText(n.text, Modifier.padding(top = 2.dp), style = RowText.copy(fontSize = 18.sp, color = Fg.copy(alpha = 0.8f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (n.sub.isNotBlank()) BasicText(n.sub, Modifier.padding(top = 4.dp), style = SmallText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Two pages under the player — the big buttons and the notifications in large type — swiped sideways or picked by name. */
@Composable
private fun Pages(modifier: Modifier, buttons: @Composable () -> Unit) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val count = NotificationDots.notes.value.size
    Column(modifier) {
        Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(stringResource(R.string.drive_page_buttons), stringResource(R.string.drive_page_notes) + if (count > 0) "  $count" else "")
                .forEachIndexed { i, label ->
                    val on = pager.currentPage == i
                    BasicText(
                        label,
                        Modifier.clip(RoundedCornerShape(50)).then(if (on) Modifier.background(Fg.copy(alpha = 0.08f)) else Modifier)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = SmallText.copy(fontSize = 16.sp, color = if (on) Fg else Dim),
                    )
                }
        }
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 28.dp, verticalAlignment = Alignment.Top) { page ->
            if (page == 0) buttons() else DriveNotes()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** Notifications in large type: app · when, who / title, the text. Tap opens it, × dismisses it. */
@Composable
private fun DriveNotes() {
    val ctx = LocalContext.current
    val notes = NotificationDots.notes.value
    if (notes.isEmpty()) {
        BasicText(stringResource(R.string.drive_no_notes), Modifier.padding(top = 12.dp), style = RowText.copy(fontSize = 22.sp, color = Dim))
        return
    }
    val replies = driveReplies().filter { it.isNotBlank() }
    var sent by remember { mutableStateOf<String?>(null) } // key of the notification just answered
    LaunchedEffect(sent) { if (sent != null) { delay(2000); sent = null } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        notes.take(8).forEach { m ->
            val app = remember(m.pkg) { runCatching { ctx.packageManager.run { getApplicationLabel(getApplicationInfo(m.pkg, 0)).toString().lowercase() } }.getOrDefault("") }
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
                Modifier.fillMaxWidth().clip(CardShape).background(CardColor)
                    .clickable { NotificationDots.send(ctx, m.open) }.padding(start = 20.dp, top = 16.dp, bottom = 18.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        icon?.let { Image(it, null, Modifier.padding(end = 8.dp).size(16.dp), colorFilter = Grayscale, alpha = 0.8f) }
                        BasicText(
                            "$app  ·  " + if (ago < 1) stringResource(R.string.msg_now) else stringResource(R.string.msg_min, ago.toInt()),
                            style = SmallText.copy(fontSize = 15.sp), maxLines = 1,
                        )
                    }
                    if (m.title.isNotBlank()) BasicText(m.title, Modifier.padding(top = 6.dp), style = RowText.copy(fontSize = 26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (m.text.isNotBlank()) BasicText(
                        m.text, Modifier.padding(top = 4.dp), style = RowText.copy(fontSize = 21.sp, color = Fg.copy(alpha = 0.8f)),
                        maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                    // answerable (chats): one tap sends a reply through the app's own reply action
                    if (m.reply != null && replies.isNotEmpty()) FlowRow(
                        Modifier.padding(top = 14.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (sent == m.key) BigPill(stringResource(R.string.msg_sent)) {}
                        else replies.forEach { r -> BigPill(r) { if (NotificationDots.reply(ctx, m, r, fromSuggestion = false)) sent = m.key } }
                    }
                }
                // dismiss
                val c = Dim
                Canvas(Modifier.size(52.dp).clip(CircleShape).clickable { NotificationDots.dismiss(m) }.padding(17.dp)) {
                    drawLine(c, Offset.Zero, Offset(size.width, size.height), 1.5.dp.toPx(), StrokeCap.Round)
                    drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), 1.5.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

/** Large filled pill for a horizontal row of short actions. */
@Composable
private fun BigPill(label: String, onClick: () -> Unit) = Box(
    Modifier.height(64.dp).clip(RoundedCornerShape(50)).background(CardColor)
        .clickable(onClick = onClick).padding(horizontal = 24.dp),
    contentAlignment = Alignment.Center,
) { BasicText(label, style = RowText.copy(fontSize = 18.sp), maxLines = 1) }

/** "nearby": gas, coffee, parking — opens the maps app searching around you. */
@Composable
private fun Nearby() {
    val ctx = LocalContext.current
    Column {
        BasicText(stringResource(R.string.drive_nearby), style = SmallText)
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(R.string.near_gas, R.string.near_coffee, R.string.near_parking).forEach { id ->
                val label = stringResource(id)
                BigPill(label) {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(label)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
}

/** "whatsapp to …" and the three quick messages as big pills; a sent one shows "sent ✓" for a moment. */
@Composable
private fun QuickMessages(to: DriveContact) {
    val ctx = LocalContext.current
    var sent by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sent) { if (sent != null) { delay(2000); sent = null } }
    Column {
        BasicText(stringResource(R.string.drive_quick, to.name.lowercase()), style = SmallText)
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            quickMessages().filter { it.isNotBlank() }.forEach { text ->
                BigPill(if (sent == text) stringResource(R.string.msg_sent) else text) { if (Drive.quickSend(ctx, to, text)) sent = text }
            }
        }
    }
}

/**
 * Big player: cover, title, artist · app, progress and three large round controls.
 * Swipe the title sideways = next / previous song; long-press play/pause = forward 30 s.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DriveMusic(modifier: Modifier) {
    val media = rememberMediaNow()
    val c = media.controller
    val meta = remember(media.tick, c) { c?.metadata }
    val state = remember(media.tick, c) { c?.playbackState }
    val playing = state?.state == PlaybackState.STATE_PLAYING
    val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
    val art = remember(meta) {
        meta?.run { getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: getBitmap(MediaMetadata.METADATA_KEY_ART) ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) }?.asImageBitmap()
    }
    BoxWithConstraints(modifier.fillMaxWidth().clip(CardShape).background(CardColor)) {
    val tight = maxHeight < 330.dp // landscape with the nav card: no room for the cover
    // the cover, blurred and veiled, tints the card (blur needs Android 12; older ones just get the veil)
    if (art != null && title != null) {
        Image(art, null, Modifier.matchParentSize().blur(48.dp), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Bg.copy(alpha = 0.7f)))
    }
    Column(Modifier.fillMaxWidth().padding(22.dp).then(if (constraints.hasBoundedHeight) Modifier.fillMaxHeight() else Modifier), verticalArrangement = Arrangement.Center) {
        if (c == null || title == null) {
            BasicText(stringResource(R.string.music_nothing), style = RowText.copy(fontSize = 22.sp, color = Dim))
            // installed music players (apps that declare themselves as the music app category), last used first
            val ctx = LocalContext.current
            val players = remember {
                val pm = ctx.packageManager
                val last = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString("lastPlayer", null)
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC), 0)
                    .map { it.activityInfo.packageName }.distinct()
                    .mapNotNull { pkg -> pm.getLaunchIntentForPackage(pkg)?.let { pkg to it } }
                    .sortedBy { (pkg, _) -> if (pkg == last) 0 else 1 }
                    .take(4)
                    .map { (pkg, launch) -> pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().lowercase() to launch }
            }
            if (players.isNotEmpty()) Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                players.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (label, launch) ->
                            BigButton(label) { runCatching { ctx.startActivity(Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            return@Column
        }
        // cover · title / artist · app — tap opens the player app
        val ctx = LocalContext.current
        val app = remember(c.packageName) { runCatching { ctx.packageManager.run { getApplicationLabel(getApplicationInfo(c.packageName, 0)).toString().lowercase() } }.getOrNull() }
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val drag = remember { Animatable(0f) } // the title follows the finger, then springs back
        Row(
            Modifier.offset { IntOffset(drag.value.roundToInt(), 0) }
                .pointerInput(c) {
                    val skipAt = 64.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val dx = drag.value
                            if (dx <= -skipAt) c.transportControls.skipToNext() else if (dx >= skipAt) c.transportControls.skipToPrevious()
                            if (kotlin.math.abs(dx) >= skipAt) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { drag.animateTo(0f, spring()) }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
                    ) { change, dx -> change.consume(); scope.launch { drag.snapTo((drag.value + dx * 0.6f).coerceIn(-200f * density, 200f * density)) } }
                }
                .clickable(interactionSource = null, indication = null) {
                    ctx.packageManager.getLaunchIntentForPackage(c.packageName)?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (art != null && !tight) Image(art, null, Modifier.padding(end = 20.dp).size(104.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                BasicText(title.lowercase(), Modifier.basicMarquee(), style = RowText.copy(fontSize = 30.sp), maxLines = 1)
                val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                val sub = listOfNotNull(artist?.lowercase(), app).joinToString("  ·  ")
                if (sub.isNotEmpty()) BasicText(sub, Modifier.padding(top = 4.dp), style = SmallText.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MediaProgress(c, meta, state, playing, media.tick, Modifier.padding(top = 20.dp), big = true)
        val controls = c.transportControls
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(CardColor).clickable { controls.skipToPrevious() }, contentAlignment = Alignment.Center) {
                PlayerIcon("prev", Fg, Modifier.size(40.dp))
            }
            Box(
                Modifier.size(112.dp).clip(CircleShape).background(Accent).combinedClickable(
                    onLongClick = {
                        state?.let { st ->
                            val pos = if (playing) st.position + ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong() else st.position
                            controls.seekTo(pos + 30_000)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                ) { if (playing) controls.pause() else controls.play() },
                contentAlignment = Alignment.Center,
            ) { PlayerIcon(if (playing) "pause" else "play", Bg, Modifier.size(52.dp)) }
            Box(Modifier.size(84.dp).clip(CircleShape).background(CardColor).clickable { controls.skipToNext() }, contentAlignment = Alignment.Center) {
                PlayerIcon("next", Fg, Modifier.size(40.dp))
            }
        }
    }
    }
}
