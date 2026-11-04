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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
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
enum class NativeWidget(val key: String, @StringRes val label: Int) {
    AGENDA("n:agenda", R.string.widget_agenda),
    MUSIC("n:music", R.string.widget_music),
    WEATHER("n:weather", R.string.widget_weather);

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
        Header(stringResource(R.string.widget_agenda))
        when {
            events == null -> Hint(stringResource(R.string.agenda_ask), onAsk)
            events.isEmpty() -> BasicText(stringResource(R.string.agenda_empty), style = SmallText)
            else -> events.forEach { e ->
                val today = LocalDate.now()
                val starts = e.begin.toLocalDate()
                val day = when {
                    !starts.isAfter(today) -> stringResource(R.string.today) // includes multi-day events that started earlier
                    starts == today.plusDays(1) -> stringResource(R.string.tomorrow)
                    else -> e.begin.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())).lowercase()
                }
                val time = if (e.allDay) stringResource(R.string.all_day) else e.begin.format(DateTimeFormatter.ofPattern(Look.timePattern())).lowercase()
                Column(Modifier.padding(bottom = 10.dp)) {
                    BasicText(e.title.ifBlank { stringResource(R.string.untitled) }, style = RowText.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText("$day · $time", style = SmallText.copy(fontSize = 13.sp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Music

private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

/** Player-style icons drawn by hand so they match the thin type (no icon library). */
@Composable
private fun PlayerIcon(kind: String, color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height
    fun triangle(left: Float, right: Float) = Path().apply { moveTo(left, h * 0.2f); lineTo(right, h * 0.5f); lineTo(left, h * 0.8f); close() }
    when (kind) {
        "play" -> drawPath(triangle(w * 0.32f, w * 0.78f), color)
        "pause" -> { drawRect(color, Offset(w * 0.3f, h * 0.22f), Size(w * 0.12f, h * 0.56f)); drawRect(color, Offset(w * 0.58f, h * 0.22f), Size(w * 0.12f, h * 0.56f)) }
        "next" -> { drawPath(triangle(w * 0.25f, w * 0.65f), color); drawRect(color, Offset(w * 0.67f, h * 0.2f), Size(w * 0.07f, h * 0.6f)) }
        "prev" -> { drawPath(Path().apply { moveTo(w * 0.75f, h * 0.2f); lineTo(w * 0.35f, h * 0.5f); lineTo(w * 0.75f, h * 0.8f); close() }, color); drawRect(color, Offset(w * 0.26f, h * 0.2f), Size(w * 0.07f, h * 0.6f)) }
    }
}

/**
 * Media player page: grayscale cover, title (scrolls if long), artist · app, seekable progress bar with times,
 * and previous / play-pause / next. Reads media sessions through the notification-listener grant (NotificationDots).
 * With nothing playing it offers to open the last player used.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicWidget(onNeedAccess: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
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
            controller?.let { it.registerCallback(callback); prefs.edit().putString("lastPlayer", it.packageName).apply() }
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
    val state = remember(tick, c) { c?.playbackState }
    val playing = state?.state == PlaybackState.STATE_PLAYING
    val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
    fun appLabel(pkg: String) = runCatching { ctx.packageManager.run { getApplicationLabel(getApplicationInfo(pkg, 0)).toString().lowercase() } }.getOrNull()
    fun openApp(pkg: String) { ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }

    // Position ticks once a second while playing (PlaybackState only reports the last known position)
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(playing, tick) { while (playing) { now = SystemClock.elapsedRealtime(); delay(1000) } }

    Column(Modifier.fillMaxSize()) {
        when {
            denied -> { Header(stringResource(R.string.widget_music)); Hint(stringResource(R.string.music_access), onNeedAccess) }
            c == null || title == null -> {
                Header(stringResource(R.string.widget_music))
                BasicText(stringResource(R.string.music_nothing), style = SmallText)
                prefs.getString("lastPlayer", null)?.let { pkg -> appLabel(pkg)?.let { Hint(stringResource(R.string.music_open, it), onClick = { openApp(pkg) }) } }
            }
            else -> {
                // cover + title / artist · app
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.tap { openApp(c.packageName) }) {
                    val art = remember(meta) {
                        (meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.asImageBitmap()
                    }
                    if (art != null) {
                        Image(art, null, Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop, colorFilter = Grayscale)
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        BasicText(title.lowercase(), Modifier.basicMarquee(), style = RowText.copy(fontSize = 21.sp), maxLines = 1)
                        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                        BasicText(
                            listOfNotNull(artist?.lowercase(), appLabel(c.packageName)).joinToString("  ·  "),
                            Modifier.padding(top = 2.dp), style = SmallText.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // progress: tap or drag the bar to seek (only if the player allows seeking)
                val duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION)
                if (duration > 0 && state != null) {
                    val position = if (playing) state.position + ((now - state.lastPositionUpdateTime) * state.playbackSpeed).toLong() else state.position
                    var dragFraction by remember { mutableStateOf<Float?>(null) }
                    val fraction = dragFraction ?: (position.toFloat() / duration).coerceIn(0f, 1f)
                    val canSeek = state.actions and PlaybackState.ACTION_SEEK_TO != 0L
                    val fg = Fg; val dim = Dim
                    Canvas(
                        Modifier.fillMaxWidth().padding(top = 16.dp).height(18.dp)
                            .then(if (!canSeek) Modifier else Modifier.pointerInput(duration) {
                                detectTapGestures { c.transportControls.seekTo((it.x / size.width * duration).toLong()) }
                            }.pointerInput(duration) {
                                detectHorizontalDragGestures(
                                    onDragEnd = { dragFraction?.let { c.transportControls.seekTo((it * duration).toLong()) }; dragFraction = null },
                                    onDragCancel = { dragFraction = null },
                                ) { change, _ -> change.consume(); dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
                            })
                    ) {
                        val y = size.height / 2
                        drawLine(dim, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(fg, Offset(0f, y), Offset(size.width * fraction, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawCircle(fg, radius = (if (dragFraction != null) 7 else 4).dp.toPx(), center = Offset(size.width * fraction, y))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        BasicText(fmt((fraction * duration).toLong()), style = SmallText.copy(fontSize = 12.sp))
                        Spacer(Modifier.weight(1f))
                        BasicText(fmt(duration), style = SmallText.copy(fontSize = 12.sp))
                    }
                }

                // previous · play/pause · next — big round targets
                val controls = c.transportControls
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PlayerIcon("prev", Fg, Modifier.size(44.dp).clip(CircleShape).tap { controls.skipToPrevious() }.padding(10.dp))
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).border(1.dp, Fg, CircleShape).tap { if (playing) controls.pause() else controls.play() },
                        contentAlignment = Alignment.Center,
                    ) { PlayerIcon(if (playing) "pause" else "play", Fg, Modifier.size(26.dp)) }
                    PlayerIcon("next", Fg, Modifier.size(44.dp).clip(CircleShape).tap { controls.skipToNext() }.padding(10.dp))
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

/** WMO weather codes (what Open-Meteo returns) → short word, translated via resources. */
@StringRes
fun weatherWord(code: Int) = when (code) {
    0 -> R.string.wx_clear
    1, 2 -> R.string.wx_partly
    3 -> R.string.wx_cloudy
    45, 48 -> R.string.wx_fog
    in 51..57 -> R.string.wx_drizzle
    in 61..67 -> R.string.wx_rain
    in 71..77 -> R.string.wx_snow
    in 80..82 -> R.string.wx_showers
    85, 86 -> R.string.wx_snow_showers
    in 95..99 -> R.string.wx_storm
    else -> R.string.wx_unknown
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
        val place = runCatching { Geocoder(this@fetchWeather, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.locality }.getOrNull()
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
        Header(stringResource(R.string.widget_weather))
        when {
            !hasPermission -> Hint(stringResource(R.string.weather_ask), onAsk)
            weather == null -> BasicText(stringResource(if (loading) R.string.loading else R.string.weather_failed), style = SmallText)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("${weather.temp}°", style = RowText.copy(fontSize = 64.sp, fontWeight = FontWeight.Thin))
                    Column(Modifier.padding(start = 18.dp)) {
                        BasicText(stringResource(weatherWord(weather.code)), style = RowText.copy(fontSize = 18.sp))
                        BasicText(stringResource(R.string.weather_range, weather.max, weather.min), style = SmallText.copy(fontSize = 13.sp))
                    }
                }
                weather.place?.let { BasicText(it, Modifier.padding(top = 6.dp), style = SmallText.copy(fontSize = 13.sp)) }
            }
        }
    }
}
