package com.jparedes.minimallauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class App(val label: String, val pkg: String, val activity: String)

// Palette / type — tweak here
private val Bg = Color(0xFF0A0A0A)
private val Fg = Color(0xFFE8E6E1)      // warm off-white
private val Dim = Color(0xFF6B6965)     // muted gray for secondary text
private val Thin = FontFamily.SansSerif

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf(emptyList<App>())
    private var resumes by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler { } // home screen: back does nothing
            val bars = WindowInsets.safeDrawing.asPaddingValues()
            val list = rememberLazyListState()
            LaunchedEffect(resumes) { list.scrollToItem(0) } // back to the clock on every Home press
            LazyColumn(
                Modifier.fillMaxSize().background(Bg).fadeEdges(),
                state = list,
                contentPadding = PaddingValues(
                    start = 36.dp, end = 36.dp,
                    top = bars.calculateTopPadding() + 96.dp,
                    bottom = bars.calculateBottomPadding() + 120.dp,
                ),
            ) {
                item { Clock() }
                items(apps, key = { it.pkg + "/" + it.activity }) { app ->
                    BasicText(
                        app.label.lowercase(),
                        Modifier.fillMaxWidth()
                            .clickable(interactionSource = null, indication = null) { open(app) }
                            .padding(vertical = 11.dp),
                        style = TextStyle(
                            color = Fg, fontSize = 24.sp, fontFamily = Thin,
                            fontWeight = FontWeight.Light, letterSpacing = 0.5.sp,
                        ),
                    )
                }
            }
        }
    }

    // ponytail: reloads on every resume instead of listening for package changes; cheap enough for a home screen
    override fun onResume() {
        super.onResume()
        resumes++
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { App(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .sortedBy { it.label.lowercase() }
    }

    private fun open(app: App) {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.pkg, app.activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
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

// Soft fade at top and bottom so the list dissolves under the system bars
private fun Modifier.fadeEdges() = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.12f to Color.Black,
                0.85f to Color.Black, 1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
