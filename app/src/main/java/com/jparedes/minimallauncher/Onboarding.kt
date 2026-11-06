package com.jparedes.minimallauncher

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First-run introduction, shown once (Look.onboarded): welcome · make it the home app · how it works ·
 * optional permissions. Every step can be skipped; settings has everything again later.
 */
@Composable
fun Onboarding(resumeTick: Int, onRequest: (String) -> Unit, onMakeHome: (Intent) -> Unit, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = 3
    BackHandler(enabled = step > 0) { step-- }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 36.dp, end = 28.dp, top = 56.dp, bottom = 28.dp)) {
        AnimatedContent(
            step,
            transitionSpec = { (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { if (targetState > initialState) it / 10 else -it / 10 }) togetherWith fadeOut(tween(160)) },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (s) {
                    0 -> Welcome()
                    1 -> MakeHome(resumeTick, onMakeHome)
                    2 -> HowTo()
                    else -> Permissions(resumeTick, onRequest)
                }
            }
        }
        // bottom: skip · dots · next
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                if (step < last) stringResource(R.string.ob_skip) else "",
                Modifier.clickable(interactionSource = null, indication = null, enabled = step < last, onClick = onDone).padding(vertical = 10.dp),
                style = SmallText,
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                for (i in 0..last) Box(Modifier.padding(horizontal = 4.dp).size(5.dp).background(if (i == step) Accent else Dim.copy(alpha = 0.5f), CircleShape))
            }
            BasicText(
                stringResource(if (step < last) R.string.ob_next else R.string.ob_done),
                Modifier.clickable(interactionSource = null, indication = null) { if (step < last) step++ else onDone() }.padding(vertical = 10.dp),
                style = RowText.copy(fontSize = 19.sp, color = Accent),
            )
        }
    }
}

@Composable
private fun Title(text: String) = BasicText(text, Modifier.padding(bottom = 18.dp), style = RowText.copy(fontSize = 34.sp, fontWeight = FontWeight.Thin))

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) = BasicText(text, modifier, style = SmallText.copy(fontSize = 16.sp, lineHeight = 24.sp))

@Composable
private fun Welcome() {
    Spacer(Modifier.height(80.dp))
    BasicText("minimal", style = TextStyle(color = Accent, fontSize = 72.sp, fontFamily = Thin, fontWeight = FontWeight.Thin, shadow = TextShadow))
    Body(stringResource(R.string.ob_welcome), Modifier.padding(top = 12.dp))
}

@Composable
private fun MakeHome(resumeTick: Int, onMakeHome: (Intent) -> Unit) {
    val ctx = LocalContext.current
    val isHome = remember(resumeTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
        else ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName == ctx.packageName
    }
    Title(stringResource(R.string.ob_home_title))
    Body(stringResource(R.string.ob_home_body))
    BasicText(
        stringResource(if (isHome) R.string.ob_home_done else R.string.ob_home_button),
        Modifier.padding(top = 28.dp).clickable(interactionSource = null, indication = null, enabled = !isHome) {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ctx.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME)
            else Intent(Settings.ACTION_HOME_SETTINGS)
            onMakeHome(intent) // the role request has to go through startActivityForResult
        }.padding(vertical = 6.dp),
        style = RowText.copy(fontSize = 21.sp, color = if (isHome) Dim else Accent),
    )
}

@Composable
private fun HowTo() {
    Title(stringResource(R.string.ob_how_title))
    listOf(
        R.string.ob_how_letter, R.string.ob_how_star, R.string.ob_how_up, R.string.ob_how_down,
        R.string.ob_how_double, R.string.ob_how_app, R.string.ob_how_time, R.string.ob_how_home,
    ).forEach { res ->
        // "gesture — what it does": gesture bright, effect dim
        val (gesture, effect) = stringResource(res).split(" — ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        Column(Modifier.padding(bottom = 14.dp)) {
            BasicText(gesture, style = RowText.copy(fontSize = 18.sp))
            BasicText(effect, style = SmallText.copy(fontSize = 14.sp))
        }
    }
}

@Composable
private fun Permissions(resumeTick: Int, onRequest: (String) -> Unit) {
    val ctx = LocalContext.current
    Title(stringResource(R.string.ob_perm_title))
    Body(stringResource(R.string.perm_hint), Modifier.padding(bottom = 12.dp))
    val divider = Dim.copy(alpha = 0.35f)
    // the five that feed the home screen; the driving-mode ones live in settings
    val why = mapOf(
        R.string.p_notifications to R.string.ob_why_notifications, R.string.p_accessibility to R.string.ob_why_accessibility,
        R.string.p_usage to R.string.ob_why_usage, R.string.p_calendar to R.string.ob_why_calendar, R.string.p_location to R.string.ob_why_location,
    )
    permissionStates(resumeTick).filter { it.label in why }.forEachIndexed { i, p ->
        if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(divider))
        Row(
            Modifier.fillMaxWidth().clickable(interactionSource = null, indication = null, enabled = !p.on) {
                // runtime permissions ask in place; the rest open their system screen
                when (p.label) {
                    R.string.p_calendar -> onRequest(Manifest.permission.READ_CALENDAR)
                    R.string.p_location -> onRequest(Manifest.permission.ACCESS_COARSE_LOCATION)
                    else -> runCatching { ctx.startActivity(p.open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(p.label), style = RowText.copy(fontSize = 18.sp))
                BasicText(stringResource(why.getValue(p.label)), style = SmallText.copy(fontSize = 13.sp))
            }
            BasicText(
                stringResource(if (p.on) R.string.state_on else R.string.ob_allow),
                Modifier.padding(start = 12.dp).width(84.dp),
                style = SmallText.copy(color = if (p.on) Dim else Accent),
            )
        }
    }
}
