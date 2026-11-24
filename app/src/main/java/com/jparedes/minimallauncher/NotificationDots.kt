package com.jparedes.minimallauncher

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf

/** A chat notification that can be answered from the home screen. Held in memory only, never saved. */
data class Message(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val reply: Notification.Action?,          // the app's own "reply" action (free-text RemoteInput)
    val actions: List<Notification.Action>,   // other plain actions: mark as read, mute…
    val suggestions: List<CharSequence>,      // system smart replies + the app's own canned choices
    val open: PendingIntent?,
)

// Which packages have dismissable notifications (dots, • n), plus the newest chat messages for the reply card
class NotificationDots : NotificationListenerService() {
    override fun onListenerConnected() { instance = this; refresh() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh() // smart replies arrive here, a bit after the post
    override fun onListenerDisconnected() { instance = null; packages.value = emptySet(); messages.value = emptyList() }

    private fun refresh() {
        val active = runCatching { activeNotifications.toList() }.getOrDefault(emptyList())
        packages.value = active.filter { it.isClearable }.map { it.packageName }.toSet()
        val ranking = currentRanking
        messages.value = active.mapNotNull { sbn -> toMessage(sbn, ranking) }.sortedByDescending { it.time }
    }

    private fun toMessage(sbn: StatusBarNotification, rankings: RankingMap?): Message? {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || !sbn.isClearable) return null
        val all = n.actions.orEmpty()
        val reply = all.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
        if (reply == null && n.category != Notification.CATEGORY_MESSAGE) return null
        val e = n.extras
        val title = (e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: e.getCharSequence(Notification.EXTRA_TITLE))?.toString() ?: return null
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val smart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val r = Ranking()
            if (rankings?.getRanking(sbn.key, r) == true) r.smartReplies else emptyList()
        } else emptyList()
        val canned = reply?.remoteInputs?.flatMap { it.choices.orEmpty().toList() }.orEmpty()
        return Message(
            sbn.key, sbn.packageName, title, text, sbn.postTime, reply,
            all.filter { it.remoteInputs.isNullOrEmpty() && !it.title.isNullOrBlank() },
            (smart + canned).distinctBy { it.toString() }.take(3),
            n.contentIntent,
        )
    }

    companion object {
        val packages = mutableStateOf(emptySet<String>())
        val messages = mutableStateOf(emptyList<Message>())
        private var instance: NotificationDots? = null

        fun dismiss(m: Message) { runCatching { instance?.cancelNotification(m.key) } }

        /** Sends [text] through the app's own reply action, as the notification shade would. */
        fun reply(ctx: Context, m: Message, text: String, fromSuggestion: Boolean): Boolean {
            val action = m.reply ?: return false
            val inputs = action.remoteInputs
            val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
            val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(inputs, intent, results)
            RemoteInput.setResultsSource(intent, if (fromSuggestion) RemoteInput.SOURCE_CHOICE else RemoteInput.SOURCE_FREE_FORM_INPUT)
            return send(ctx, action.actionIntent, intent)
        }

        fun send(ctx: Context, pi: PendingIntent?, fill: Intent? = null): Boolean = runCatching {
            pi ?: return false
            // we're in the foreground, so let the app's own activity start (Android 14+ needs the opt-in)
            val opts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                @Suppress("DEPRECATION") ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
            else null
            pi.send(ctx, 0, fill, null, null, null, opts)
            true
        }.getOrDefault(false)
    }
}
