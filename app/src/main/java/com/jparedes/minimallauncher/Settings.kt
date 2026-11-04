package com.jparedes.minimallauncher

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    var dim by mutableIntStateOf(40)       // % of the theme color laid over the background
    var gray by mutableStateOf(true)       // photo in black & white
    var clockSize by mutableIntStateOf(1)  // 0 small · 1 medium · 2 large
    var textSize by mutableIntStateOf(1)
    var clock24 by mutableStateOf(true)

    val clockSp get() = listOf(64f, 88f, 112f)[clockSize]
    val textSp get() = listOf(20f, 24f, 28f)[textSize]
    fun timePattern() = if (clock24) "HH:mm" else "h:mm a"

    private var prefs: SharedPreferences? = null

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE).also { prefs = it }
        background = p.getString("bg", BG_NONE)!!
        dim = p.getInt("bgDim", 40)
        gray = p.getBoolean("bgGray", true)
        clockSize = p.getInt("clockSize", 1)
        textSize = p.getInt("textSize", 1)
        clock24 = p.getBoolean("clock24", DateFormat.is24HourFormat(ctx)) // default follows the phone
    }

    fun save() {
        prefs?.edit()?.putString("bg", background)?.putInt("bgDim", dim)?.putBoolean("bgGray", gray)
            ?.putInt("clockSize", clockSize)?.putInt("textSize", textSize)?.putBoolean("clock24", clock24)?.apply()
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
fun SettingsScreen(onPickPhoto: () -> Unit, onBackgroundChanged: () -> Unit, onClose: () -> Unit) {
    fun set(change: () -> Unit) { change(); Look.save() }
    val sizes = listOf(R.string.s_small, R.string.s_medium, R.string.s_large).map { stringResource(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 36.dp, end = 24.dp, top = 64.dp, bottom = 48.dp)) {
        BasicText(stringResource(R.string.s_title), style = RowText.copy(fontSize = 40.sp, fontWeight = FontWeight.Thin))

        Section(stringResource(R.string.s_background)) {
            Choice(stringResource(R.string.s_bg_none), Look.background == Look.BG_NONE) { set { Look.background = Look.BG_NONE }; onBackgroundChanged() }
            Choice(stringResource(R.string.s_bg_system), Look.background == Look.BG_SYSTEM) { set { Look.background = Look.BG_SYSTEM }; onBackgroundChanged() }
            // tapping "photo…" always opens the picker, so it doubles as "change photo"
            Choice(stringResource(R.string.s_bg_photo), Look.background == Look.BG_PHOTO, onPickPhoto)
        }
        if (Look.background != Look.BG_NONE) Section(stringResource(R.string.s_dim)) {
            listOf(0, 20, 40, 60, 80).forEach { d -> Choice("$d %", Look.dim == d) { set { Look.dim = d } } }
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
        Section(stringResource(R.string.s_format)) {
            Choice("24 h", Look.clock24) { set { Look.clock24 = true } }
            Choice("12 h", !Look.clock24) { set { Look.clock24 = false } }
        }
        BasicText(
            stringResource(R.string.s_done),
            Modifier.padding(top = 44.dp).clickable(interactionSource = null, indication = null, onClick = onClose).padding(vertical = 6.dp),
            style = RowText.copy(fontSize = 19.sp),
        )
    }
}
