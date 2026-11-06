package com.jparedes.minimallauncher

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clock faces (settings → appearance → clock style). */
enum class ClockStyle(val label: Int) {
    THIN(R.string.st_thin),    // hairline digits (the original)
    BOLD(R.string.st_bold),    // heavy, tight digits
    DAY(R.string.st_day),      // huge weekday "Mon." — time and date go to the line below
    SPLIT(R.string.st_split),  // wide spaced weekday cut by a band holding time · date · temperature
}

/**
 * Fonts put blank space (side bearing) before the first glyph, and it grows with size — so big text looked
 * indented next to the small lines under it. Measure the real ink edge and pull the text back by that much.
 */
@Composable
fun Modifier.alignInk(text: String, size: TextUnit, weight: Int): Modifier {
    val ctx = LocalContext.current
    val paint = remember { Paint().apply { typeface = ctx.resources.getFont(R.font.jost) } }
    val px = with(LocalDensity.current) { size.toPx() }
    val left = remember(text, px, weight) {
        paint.textSize = px
        paint.fontVariationSettings = "'wght' $weight"
        Rect().also { paint.getTextBounds(text, 0, text.length, it) }.left
    }
    return offset { IntOffset(-left, 0) }
}

/**
 * The big element of the clock in the chosen [style]. [actions] carries tap/long-press handlers.
 * [band] is what SPLIT writes inside its cut: e.g. ["13:50", "29 DEC", "4°"].
 */
@Composable
fun ClockFace(style: ClockStyle, now: LocalDateTime, sp: Float, band: List<String>, actions: Modifier = Modifier) {
    val color = Fg
    when (style) {
        ClockStyle.THIN, ClockStyle.BOLD -> Row(verticalAlignment = Alignment.Bottom) {
            val time = now.format(DateTimeFormatter.ofPattern(if (Look.clock24) "HH:mm" else "h:mm"))
            val bold = style == ClockStyle.BOLD
            // the new minute rolls in from below while the old one drifts up and fades
            AnimatedContent(
                time,
                transitionSpec = {
                    (slideInVertically(tween(450)) { it / 4 } + fadeIn(tween(450))) togetherWith
                        (slideOutVertically(tween(350)) { -it / 4 } + fadeOut(tween(300))) using SizeTransform(clip = false)
                },
                label = "clock",
            ) { t ->
                BasicText(
                    t,
                    Modifier.alignInk(t, sp.sp, if (bold) 900 else 100).then(actions),
                    style = TextStyle(
                        color = color, fontSize = sp.sp, fontFamily = Thin, shadow = TextShadow,
                        fontWeight = if (bold) FontWeight.Black else FontWeight.Thin,
                        letterSpacing = if (bold) (-sp * 0.04f).sp else 0.sp,
                    ),
                )
            }
            if (!Look.clock24) BasicText(
                now.format(DateTimeFormatter.ofPattern("a")).lowercase(),
                Modifier.padding(start = 10.dp, bottom = (sp * 0.2f).dp),
                style = SmallText.copy(fontSize = 18.sp),
            )
        }

        ClockStyle.DAY -> {
            val day = now.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).trimEnd('.').replaceFirstChar { it.uppercase() } + "."
            BasicText(
                day,
                Modifier.alignInk(day, (sp * 1.05f).sp, 900).then(actions),
                style = TextStyle(color = color, fontSize = (sp * 1.05f).sp, fontFamily = Thin, fontWeight = FontWeight.Black, letterSpacing = (-sp * 0.03f).sp, shadow = TextShadow),
            )
        }

        ClockStyle.SPLIT -> {
            val day = now.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).trimEnd('.').uppercase()
            Box(actions) {
                // hairline weekday, widely spaced, with a horizontal band cut out of its middle
                BasicText(
                    day,
                    Modifier.alignInk(day, sp.sp, 100)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            val band = size.height * 0.2f
                            drawRect(Color.Black, Offset(0f, size.height * 0.53f - band / 2), Size(size.width, band), blendMode = BlendMode.Clear)
                        },
                    style = TextStyle(color = color, fontSize = sp.sp, fontFamily = Thin, fontWeight = FontWeight.Thin, letterSpacing = (sp * 0.4f).sp, shadow = TextShadow),
                )
                // time · date · temperature written inside the cut, spread across the word
                Row(
                    Modifier.matchParentSize().padding(top = (sp * 0.06f).dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    band.forEach { BasicText(it, style = SmallText.copy(color = color, fontSize = (sp * 0.17f).coerceIn(11f, 18f).sp, letterSpacing = 2.sp)) }
                }
            }
        }
    }
}
