package com.jparedes.minimallauncher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf

// Tracks which packages have dismissable notifications; only package names, never content
class NotificationDots : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onListenerDisconnected() { packages.value = emptySet() }

    private fun refresh() {
        packages.value = runCatching { activeNotifications.filter { it.isClearable }.map { it.packageName }.toSet() }
            .getOrDefault(emptySet())
    }

    companion object { val packages = mutableStateOf(emptySet<String>()) }
}
