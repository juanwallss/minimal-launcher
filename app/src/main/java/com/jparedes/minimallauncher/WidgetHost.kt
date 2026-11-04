package com.jparedes.minimallauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/** Hosts other apps' widgets; every view it creates reports long-presses and swipes back to the launcher. */
class LauncherWidgetHost(
    context: Context,
    private val onLongPress: (Int) -> Unit,
    private val onSwipe: (dx: Float, dy: Float) -> Unit,
) : AppWidgetHost(context, HOST_ID) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        GestureWidgetView(context, { onLongPress(appWidgetId) }, onSwipe)

    companion object { const val HOST_ID = 1024 }
}

/**
 * Widgets swallow touches, so the Compose pager and the home gestures never see them.
 * This view watches the gesture while the widget handles it:
 *  - long-press (without moving) → onLongPress, and the rest of the gesture is stolen so the widget doesn't also click
 *  - horizontal swipe → stolen from the widget, reported as onSwipe (the launcher turns it into a page change)
 *  - vertical swipe on an empty part of the widget → onSwipe (notifications / search, like the rest of home).
 *    Vertical swipes over the widget's own content are left alone so scrollable widgets keep working.
 */
class GestureWidgetView(
    context: Context,
    private val onLongPress: () -> Unit,
    private val onSwipe: (dx: Float, dy: Float) -> Unit,
) : AppWidgetHostView(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private var horizontal = false
    private val longPress = Runnable {
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress()
    }

    private fun track(ev: MotionEvent, ownsGesture: Boolean) {
        val dx = ev.x - downX
        val dy = ev.y - downY
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; longPressed = false; horizontal = false
                removeCallbacks(longPress)
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(dx) > slop || abs(dy) > slop) {
                removeCallbacks(longPress)
                if (!longPressed && abs(dx) > abs(dy)) horizontal = true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (!longPressed && (horizontal || (ownsGesture && abs(dy) > slop * 3))) onSwipe(dx, dy)
            }
            MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        track(ev, ownsGesture = false)
        return longPressed || horizontal
    }

    // Reached when no child took the touch (empty area) or after we intercepted; keep receiving the gesture.
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev, ownsGesture = true)
        return true
    }
}
