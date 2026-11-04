package com.jparedes.minimallauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class App(val label: String, val pkg: String, val activity: String)

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf(emptyList<App>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler { } // home screen: back does nothing
            LazyColumn(
                Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()
                    .padding(horizontal = 24.dp)
            ) {
                items(apps, key = { it.pkg + "/" + it.activity }) { app ->
                    BasicText(
                        app.label,
                        Modifier.fillMaxWidth().clickable { open(app) }.padding(vertical = 12.dp),
                        style = TextStyle(color = Color.White, fontSize = 20.sp),
                    )
                }
            }
        }
    }

    // ponytail: reloads on every resume instead of listening for package changes; cheap enough for a home screen
    override fun onResume() {
        super.onResume()
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
