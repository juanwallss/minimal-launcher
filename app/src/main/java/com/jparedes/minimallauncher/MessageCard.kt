package com.jparedes.minimallauncher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Pill = RoundedCornerShape(50)

/**
 * Newest chat notification on the clean home view: who · when, the message, the app's own actions
 * (mark as read, mute…), suggested replies, and a reply field. [more] = how many other chats wait.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageCard(m: Message, more: Int, icon: ImageBitmap?, appName: String, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var draft by remember(m.key) { mutableStateOf("") }
    var sent by remember(m.key) { mutableStateOf(false) }
    LaunchedEffect(sent) { if (sent) { delay(1800); sent = false } }
    fun reply(text: String, suggested: Boolean) {
        if (text.isBlank()) return
        if (NotificationDots.reply(ctx, m, text.trim(), suggested)) { draft = ""; sent = true; focus.clearFocus() }
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Fg.copy(alpha = 0.06f))
            .border(1.dp, Dim.copy(alpha = 0.25f), RoundedCornerShape(22.dp)).padding(18.dp),
    ) {
        // header: app · when · (+n)                                    ×
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Image(it, null, Modifier.size(16.dp), colorFilter = Grayscale, alpha = 0.8f) }
            val ago = (System.currentTimeMillis() - m.time) / 60_000
            val whenText = if (ago < 1) stringResource(R.string.msg_now) else stringResource(R.string.msg_min, ago.toInt())
            BasicText("${appName.lowercase()}  ·  $whenText", Modifier.padding(start = 8.dp).weight(1f), style = SmallText.copy(fontSize = 13.sp), maxLines = 1)
            if (more > 0) BasicText(
                "+$more", Modifier.clickable(interactionSource = null, indication = null) { onMore() }.padding(horizontal = 10.dp),
                style = SmallText.copy(fontSize = 13.sp, color = Fg),
            )
            val c = Dim
            Canvas(Modifier.size(28.dp).clip(CircleShape).clickable { NotificationDots.dismiss(m) }.padding(9.dp)) {
                drawLine(c, Offset.Zero, Offset(size.width, size.height), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
        // who and what: tap opens the chat
        Column(Modifier.padding(top = 8.dp).clickable(interactionSource = null, indication = null) { NotificationDots.send(ctx, m.open) }) {
            BasicText(m.title, style = RowText.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.text.isNotBlank()) BasicText(
                m.text, Modifier.padding(top = 4.dp), style = SmallText.copy(color = Fg.copy(alpha = 0.8f), letterSpacing = 0.3.sp),
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        // the app's actions (filled) and suggested replies (outlined)
        if (m.actions.isNotEmpty() || (m.reply != null && m.suggestions.isNotEmpty())) FlowRow(
            Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            m.actions.forEach { a -> Chip(a.title.toString().lowercase(), filled = true) { NotificationDots.send(ctx, a.actionIntent) } }
            if (m.reply != null) m.suggestions.forEach { s -> Chip(s.toString(), filled = false) { reply(s.toString(), suggested = true) } }
        }
        // free reply → the app's own reply action
        if (m.reply != null) AnimatedContent(sent, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "sent") { done ->
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().border(1.dp, Dim.copy(alpha = 0.4f), Pill).padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (done) BasicText(stringResource(R.string.msg_sent), Modifier.padding(vertical = 11.dp), style = SmallText.copy(color = Fg))
                else {
                    BasicTextField(
                        draft, { draft = it }, Modifier.weight(1f).padding(vertical = 11.dp),
                        textStyle = SmallText.copy(color = Fg, letterSpacing = 0.3.sp), cursorBrush = SolidColor(Fg),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { reply(draft, suggested = false) }),
                        decorationBox = { inner -> Box { if (draft.isEmpty()) BasicText(stringResource(R.string.msg_reply), style = SmallText); inner() } },
                    )
                    // paper-plane arrow
                    val c = if (draft.isBlank()) Dim else Fg
                    Canvas(Modifier.size(36.dp).clip(CircleShape).clickable { reply(draft, suggested = false) }.padding(10.dp)) {
                        val w = 1.5.dp.toPx()
                        val (l, t, r, b) = listOf(0f, 0f, size.width, size.height)
                        drawLine(c, Offset(l, t), Offset(r, b / 2), w, StrokeCap.Round)
                        drawLine(c, Offset(r, b / 2), Offset(l, b), w, StrokeCap.Round)
                        drawLine(c, Offset(l, b), Offset(r * 0.3f, b / 2), w, StrokeCap.Round)
                        drawLine(c, Offset(r * 0.3f, b / 2), Offset(l, t), w, StrokeCap.Round)
                        drawLine(c, Offset(r * 0.3f, b / 2), Offset(r, b / 2), w, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, filled: Boolean, onClick: () -> Unit) = BasicText(
    label,
    Modifier.clip(Pill)
        .then(if (filled) Modifier.background(Fg.copy(alpha = 0.08f)) else Modifier.border(1.dp, Dim.copy(alpha = 0.5f), Pill))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    style = SmallText.copy(color = Fg, fontSize = 14.sp, letterSpacing = 0.5.sp),
    maxLines = 1,
)
