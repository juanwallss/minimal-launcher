package com.jparedes.minimallauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.os.Build
import android.provider.CalendarContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume

/** Native widget pages are stored as "n:<kind>"; system widgets as "w:<appWidgetId>". */
enum class NativeWidget(val key: String, val label: String) {
    AGENDA("n:agenda", "agenda"),
    MUSIC("n:music", "música"),
    WEATHER("n:weather", "clima");

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } }
}

@Composable
private fun Header(text: String) = BasicText(text, Modifier.padding(bottom = 10.dp), style = SmallText.copy(fontSize = 13.sp))

@Composable
private fun Hint(text: String, onClick: () -> Unit) = BasicText(
    text, Modifier.clickable(interactionSource = null, indication = null, onClick = onClick).padding(vertical = 6.dp), style = SmallText,
)

private fun Modifier.tap(onClick: () -> Unit) = clickable(interactionSource = null, indication = null, onClick = onClick)

// ---------------------------------------------------------------- Agenda

data class CalEvent(val title: String, val begin: LocalDateTime, val allDay: Boolean)

/** Events overlapping [now, now + hours], soonest first. All-day events are stored in UTC midnight. */
fun Context.upcomingEvents(hours: Int, limit: Int): List<CalEvent> {
    if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .also { ContentUris.appendId(it, now); ContentUris.appendId(it, now + hours * 3_600_000L) }.build()
    return runCatching {
        contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
            "${CalendarContract.Instances.END} >= ?", arrayOf(now.toString()),
            "${CalendarContract.Instances.ALL_DAY} DESC, ${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val allDay = c.getInt(2) == 1
                    val zone = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
                    add(CalEvent((c.getString(0) ?: "").lowercase(), LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(1)), zone), allDay))
                }
            }.sortedBy { it.begin }.take(limit)
        } ?: emptyList()
    }.getOrDefault(emptyList())
}

/** [events] null = no calendar permission yet. */
@Composable
fun AgendaWidget(events: List<CalEvent>?, onAsk: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().tap(if (events == null) onAsk else onOpen)) {
        Header("agenda")
        when {
            events == null -> Hint("toca para ver tu calendario", onAsk)
            events.isEmpty() -> BasicText("nada en las próximas 48 h", style = SmallText)
            else -> events.forEach { e ->
                val today = LocalDate.now()
                val day = when (e.begin.toLocalDate()) {
                    today -> "hoy"
                    today.plusDays(1) -> "mañana"
                    else -> e.begin.format(DateTimeFormatter.ofPattern("EEE d", Locale("es"))).lowercase()
                }
                val time = if (e.allDay) "todo el día" else e.begin.format(DateTimeFormatter.ofPattern("HH:mm"))
                Column(Modifier.padding(bottom = 10.dp)) {
                    BasicText(e.title.ifBlank { "(sin título)" }, style = RowText.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText("$day · $time", style = SmallText.copy(fontSize = 13.sp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Music

/** Reads media sessions through the notification-listener grant (NotificationDots) the user already gave. */
@Composable
fun MusicWidget(onNeedAccess: () -> Unit) {
    val ctx = LocalContext.current
    val sessions = remember { ctx.getSystemService(MediaSessionManager::class.java) }
    val listenerComponent = remember { ComponentName(ctx, NotificationDots::class.java) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var tick by remember { mutableIntStateOf(0) } // bumps on metadata / playback changes
    var denied by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { tick++ }
            override fun onPlaybackStateChanged(state: PlaybackState?) { tick++ }
        }
        fun pick(list: List<MediaController>?) {
            controller?.unregisterCallback(callback)
            controller = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
            controller?.registerCallback(callback)
            tick++
        }
        val onChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        try {
            pick(sessions.getActiveSessions(listenerComponent))
            sessions.addOnActiveSessionsChangedListener(onChanged, listenerComponent)
        } catch (_: SecurityException) {
            denied = true
        }
        onDispose {
            runCatching { sessions.removeOnActiveSessionsChangedListener(onChanged) }
            controller?.unregisterCallback(callback)
        }
    }

    val c = controller
    val meta = remember(tick, c) { c?.metadata }
    val playing = remember(tick, c) { c?.playbackState?.state == PlaybackState.STATE_PLAYING }
    val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)

    Column(Modifier.fillMaxSize()) {
        Header("música")
        when {
            denied -> Hint("activa el acceso a notificaciones", onNeedAccess)
            c == null || title == null -> BasicText("nada sonando", style = SmallText)
            else -> {
                val openApp = { ctx.packageManager.getLaunchIntentForPackage(c.packageName)?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }; Unit }
                BasicText(title.lowercase(), Modifier.tap(openApp), style = RowText.copy(fontSize = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                if (artist != null) BasicText(artist.lowercase(), Modifier.padding(top = 2.dp), style = SmallText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    val controls = c.transportControls
                    BasicText("anterior", Modifier.tap { controls.skipToPrevious() }, style = SmallText.copy(color = Fg))
                    BasicText(if (playing) "pausar" else "reproducir", Modifier.tap { if (playing) controls.pause() else controls.play() }, style = SmallText.copy(color = Fg))
                    BasicText("siguiente", Modifier.tap { controls.skipToNext() }, style = SmallText.copy(color = Fg))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Weather

data class Weather(val temp: Int, val code: Int, val max: Int, val min: Int, val place: String?, val at: Long) {
    fun toJson() = JSONObject().put("temp", temp).put("code", code).put("max", max).put("min", min).put("place", place ?: "").put("at", at).toString()

    companion object {
        fun fromJson(s: String?) = runCatching {
            JSONObject(s!!).run { Weather(getInt("temp"), getInt("code"), getInt("max"), getInt("min"), getString("place").ifBlank { null }, getLong("at")) }
        }.getOrNull()
    }
}

/** WMO weather codes (what Open-Meteo returns) → short Spanish words. */
fun weatherWord(code: Int) = when (code) {
    0 -> "despejado"
    1, 2 -> "algo nublado"
    3 -> "nublado"
    45, 48 -> "niebla"
    in 51..57 -> "llovizna"
    in 61..67 -> "lluvia"
    in 71..77 -> "nieve"
    in 80..82 -> "chubascos"
    85, 86 -> "nevadas"
    in 95..99 -> "tormenta"
    else -> "—"
}

fun Context.hasLocation() = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Coarse location → Open-Meteo (free, no account). Only the rounded coordinates (~1 km) leave the phone.
 * Returns null on any failure (no location, no network, timeout).
 */
@SuppressLint("MissingPermission")
suspend fun Context.fetchWeather(): Weather? = withContext(Dispatchers.IO) {
    if (!hasLocation()) return@withContext null
    runCatching {
        val lm = getSystemService(LocationManager::class.java)
        val providers = lm.getProviders(true)
        var loc: Location? = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if ((loc == null || System.currentTimeMillis() - loc.time > 3 * 3_600_000L) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER } ?: providers.firstOrNull()
            if (provider != null) loc = withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { cont -> lm.getCurrentLocation(provider, null, mainExecutor) { cont.resume(it) } }
            } ?: loc
        }
        loc ?: return@runCatching null
        val lat = String.format(Locale.US, "%.2f", loc.latitude)
        val lon = String.format(Locale.US, "%.2f", loc.longitude)
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        )
        val json = (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 5_000; readTimeout = 5_000
            try { inputStream.bufferedReader().readText() } finally { disconnect() }
        }.let(::JSONObject)
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        @Suppress("DEPRECATION")
        val place = runCatching { Geocoder(this@fetchWeather, Locale("es")).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.locality }.getOrNull()
        Weather(
            temp = Math.round(current.getDouble("temperature_2m")).toInt(),
            code = current.getInt("weather_code"),
            max = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            min = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            place = place?.lowercase(),
            at = System.currentTimeMillis(),
        )
    }.getOrNull()
}

/** [weather] null + [loading] = fetching; null + !loading = failed. */
@Composable
fun WeatherWidget(hasPermission: Boolean, weather: Weather?, loading: Boolean, onAsk: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().tap(if (hasPermission) onRefresh else onAsk)) {
        Header("clima")
        when {
            !hasPermission -> Hint("toca para usar tu ubicación aproximada", onAsk)
            weather == null -> BasicText(if (loading) "cargando…" else "sin datos · toca para reintentar", style = SmallText)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("${weather.temp}°", style = RowText.copy(fontSize = 64.sp, fontWeight = FontWeight.Thin))
                    Column(Modifier.padding(start = 18.dp)) {
                        BasicText(weatherWord(weather.code), style = RowText.copy(fontSize = 18.sp))
                        BasicText("máx ${weather.max}° · mín ${weather.min}°", style = SmallText.copy(fontSize = 13.sp))
                    }
                }
                weather.place?.let { BasicText(it, Modifier.padding(top = 6.dp), style = SmallText.copy(fontSize = 13.sp)) }
            }
        }
    }
}
