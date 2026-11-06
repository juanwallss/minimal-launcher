package com.jparedes.minimallauncher

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Build
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** A contact for one-tap calling in driving mode. */
data class DriveContact(val name: String, val number: String)

/**
 * Driving mode on/off. Turned on by connecting to the chosen car Bluetooth (CarReceiver), a gesture, or settings;
 * turned off by disconnecting or "exit". Persisted so it survives the launcher process being restarted.
 */
internal object Drive {
    var active by mutableStateOf(false)
    var permTick by mutableIntStateOf(0) // bumped when Bluetooth permission changes, so settings re-read devices

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun load(ctx: Context) { active = prefs(ctx).getBoolean("driveActive", false) }
    fun set(ctx: Context, on: Boolean) {
        active = on
        prefs(ctx).edit().putBoolean("driveActive", on).apply()
    }
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
            BluetoothDevice.ACTION_ACL_CONNECTED -> Drive.set(ctx, true)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> Drive.set(ctx, false)
        }
    }
}

/** Big outlined target, easy to hit without looking. */
@Composable
private fun RowScope.BigButton(label: String, dim: Boolean = false, onClick: () -> Unit) = Box(
    Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(20.dp))
        .border(1.dp, if (dim) Dim else Fg, RoundedCornerShape(20.dp))
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    BasicText(
        label, Modifier.padding(horizontal = 8.dp),
        style = RowText.copy(fontSize = 20.sp, color = if (dim) Dim else Fg), maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
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
    onNavigate: (String?) -> Unit,   // null = just open the map
    onCall: (DriveContact) -> Unit,
    onDial: () -> Unit,
    onSetup: () -> Unit,
    onExit: () -> Unit,
) {
    val now by produceState(LocalDateTime.now()) {
        while (true) { value = LocalDateTime.now(); delay(1000L * (60 - value.second)) }
    }
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // header: time · driving · exit
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                now.format(DateTimeFormatter.ofPattern(Look.timePattern())).lowercase(),
                style = TextStyle(color = Fg, fontSize = 52.sp, fontFamily = Thin, fontWeight = FontWeight.Thin, shadow = TextShadow),
            )
            BasicText(stringResource(R.string.drive_title), Modifier.padding(start = 16.dp).weight(1f), style = SmallText)
            BasicText(
                stringResource(R.string.drive_exit),
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onExit).padding(12.dp),
                style = RowText.copy(fontSize = 18.sp),
            )
        }

        DriveMusic(Modifier.weight(1f))

        // navigate
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.drive_home), dim = homeAddress.isBlank()) { if (homeAddress.isBlank()) onSetup() else onNavigate(homeAddress) }
            BigButton(stringResource(R.string.drive_work), dim = workAddress.isBlank()) { if (workAddress.isBlank()) onSetup() else onNavigate(workAddress) }
            BigButton(stringResource(R.string.drive_map)) { onNavigate(null) }
        }

        // call: up to 4 favorites (2 per row) + dialer
        val rows = (contacts.map { it as DriveContact? } + listOf<DriveContact?>(null)).chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { c ->
                        if (c != null) BigButton(c.name.lowercase()) { onCall(c) }
                        else BigButton(stringResource(R.string.drive_dial), dim = true, onClick = onDial)
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Big player: title, artist and three large round controls. */
@Composable
private fun DriveMusic(modifier: Modifier) {
    val media = rememberMediaNow()
    val c = media.controller
    val meta = remember(media.tick, c) { c?.metadata }
    val state = remember(media.tick, c) { c?.playbackState }
    val playing = state?.state == PlaybackState.STATE_PLAYING
    val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        if (c == null || title == null) {
            BasicText(stringResource(R.string.music_nothing), style = RowText.copy(fontSize = 22.sp, color = Dim))
            // one big tap to start the last player used
            val ctx = LocalContext.current
            val last = remember { ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString("lastPlayer", null) }
            val label = remember(last) {
                last?.let { runCatching { ctx.packageManager.run { getApplicationLabel(getApplicationInfo(it, 0)).toString().lowercase() } }.getOrNull() }
            }
            if (last != null && label != null) Row(Modifier.padding(top = 20.dp)) {
                BigButton(stringResource(R.string.music_open, label)) {
                    ctx.packageManager.getLaunchIntentForPackage(last)?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                }
            }
            return@Column
        }
        BasicText(title.lowercase(), Modifier.basicMarquee(), style = RowText.copy(fontSize = 30.sp), maxLines = 1)
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        if (artist != null) BasicText(artist.lowercase(), Modifier.padding(top = 4.dp), style = SmallText.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val controls = c.transportControls
        Row(
            Modifier.fillMaxWidth().padding(top = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(84.dp).clip(CircleShape).clickable { controls.skipToPrevious() }, contentAlignment = Alignment.Center) {
                PlayerIcon("prev", Fg, Modifier.size(44.dp))
            }
            Box(
                Modifier.size(112.dp).clip(CircleShape).border(1.5.dp, Fg, CircleShape).clickable { if (playing) controls.pause() else controls.play() },
                contentAlignment = Alignment.Center,
            ) { PlayerIcon(if (playing) "pause" else "play", Fg, Modifier.size(52.dp)) }
            Box(Modifier.size(84.dp).clip(CircleShape).clickable { controls.skipToNext() }, contentAlignment = Alignment.Center) {
                PlayerIcon("next", Fg, Modifier.size(44.dp))
            }
        }
    }
}
