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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clock faces (settings → appearance → clock style). */
enum class ClockStyle(val label: Int) {
    THIN(R.string.st_thin),    // hairline digits (the original)
    BOLD(R.string.st_bold),    // heavy, tight digits
    DAY(R.string.st_day),      // huge weekday "Mon." — time and date go to the line below
    SPLIT(R.string.st_split),  // wide spaced weekday cut by a band holding time · date · temperature
    ANALOG(R.string.st_analog),     // thin ring with two hands
    STACK(R.string.st_stack),       // heavy hours over hairline minutes
    PROGRESS(R.string.st_progress), // thin time + a line showing how much of the day has gone
    BINARY(R.string.st_binary),     // one column of dots per digit (8·4·2·1), the time in words goes to the line below
}

/**
 * Fonts put blank space (side bearing) before the first glyph, and it grows with size — so big text looked
 * indented next to the small lines under it. Measure the real ink edge and pull the text back by that much.
 */
@Composable
fun Modifier.alignInk(text: String, size: TextUnit, weight: Int): Modifier {
    val ctx = LocalContext.current
    val font = Look.font
    val paint = remember(font) { Paint().apply { typeface = ctx.resources.getFont(FONTS.getOrElse(font) { FONTS[0] }.second) } }
    val px = with(LocalDensity.current) { size.toPx() }
    val left = remember(text, px, weight, paint) {
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
    val color = Accent
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

        ClockStyle.ANALOG -> {
            val dim = Dim
            Canvas(Modifier.padding(vertical = 8.dp).size((sp * 1.5f).dp).then(actions)) {
                val c = center
                val r = size.minDimension / 2 - 1.dp.toPx()
                fun at(turns: Float, len: Float): Offset {
                    val a = turns * 2 * PI.toFloat() - PI.toFloat() / 2
                    return c + Offset(cos(a) * len, sin(a) * len)
                }
                drawCircle(dim.copy(alpha = 0.5f), r, c, style = Stroke(1.dp.toPx()))
                for (k in 0 until 12) if (k % 3 == 0) drawLine(dim, at(k / 12f, r - 7.dp.toPx()), at(k / 12f, r - 2.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
                val min = now.minute + now.second / 60f
                drawLine(color, c, at((now.hour % 12 + min / 60f) / 12f, r * 0.5f), 3.dp.toPx(), StrokeCap.Round)
                drawLine(color, c, at(min / 60f, r * 0.86f), 1.2.dp.toPx(), StrokeCap.Round)
                drawCircle(color, 3.dp.toPx(), c)
            }
        }

        ClockStyle.STACK -> {
            val h = now.format(DateTimeFormatter.ofPattern(if (Look.clock24) "HH" else "h"))
            val m = now.format(DateTimeFormatter.ofPattern("mm"))
            val big = sp * 1.1f
            val tight = TextStyle(color = color, fontSize = big.sp, fontFamily = Thin, shadow = TextShadow)
            // the font's line box is much taller than its digits: give each line only the digit height
            val digitBox = Modifier.layout { m, c ->
                val p = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val h = (big * 0.78f).sp.roundToPx()
                layout(p.width, h) { p.place(0, h - p[FirstBaseline]) } // baseline sits on the bottom edge
            }
            Column(actions.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy((big * 0.08f).dp)) {
                BasicText(h, Modifier.alignInk(h, big.sp, 900).then(digitBox), style = tight.copy(fontWeight = FontWeight.Black, letterSpacing = (-big * 0.04f).sp))
                BasicText(m, Modifier.alignInk(m, big.sp, 100).then(digitBox), style = tight.copy(fontWeight = FontWeight.Thin))
            }
        }

        ClockStyle.PROGRESS -> Column {
            ClockFace(ClockStyle.THIN, now, sp, band, actions)
            val f = (now.hour * 60 + now.minute) / 1440f
            val dim = Dim
            Row(Modifier.padding(top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width((sp * 2.4f).dp).height(8.dp)) {
                    val y = size.height / 2
                    val x = size.width * f
                    drawLine(dim.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    drawLine(color, Offset(0f, y), Offset(x, y), 1.dp.toPx())
                    drawCircle(color, 3.5.dp.toPx(), Offset(x, y))
                }
                BasicText("${(f * 100).roundToInt()} %", Modifier.padding(start = 12.dp), style = SmallText.copy(fontSize = 13.sp))
            }
        }

        ClockStyle.BINARY -> {
            val digits = now.format(DateTimeFormatter.ofPattern(if (Look.clock24) "HHmm" else "hhmm")).map { it - '0' }
            val off = Dim.copy(alpha = 0.25f)
            val d = (sp * 0.13f).dp                                // dot size
            val cols = listOf(0f, 2.2f, 6.4f, 8.6f)                // hours pair, gap, minutes pair (in dots)
            Canvas(Modifier.padding(vertical = 10.dp).size(d * 9.6f, d * 7.6f).then(actions)) {
                val u = d.toPx()
                digits.forEachIndexed { i, digit ->
                    for (row in 0 until 4) {
                        val bit = 3 - row                          // top row = 8
                        val on = digit shr bit and 1 == 1
                        drawCircle(if (on) color else off, u / 2, Offset(cols[i] * u + u / 2, row * 2.2f * u + u / 2))
                    }
                }
            }
        }
    }
}
