package com.genservices.baretime

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews

/**
 * The home-screen widget. It draws the saved design into a picture once a minute,
 * using only the phone's own clock, so it works with no internet at all.
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateOne(context, manager, id)
        schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        updateOne(context, manager, id)
    }

    override fun onEnabled(context: Context) { schedule(context) }

    override fun onDisabled(context: Context) { cancel(context) }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                updateAll(context)
                schedule(context)
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.genservices.baretime.TICK"

        fun ids(context: Context): IntArray {
            val m = AppWidgetManager.getInstance(context)
            return m.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
        }

        fun updateAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            for (id in ids(context)) updateOne(context, m, id)
        }

        private fun updateOne(context: Context, m: AppWidgetManager, id: Int) {
            val opts = m.getAppWidgetOptions(id)
            val density = context.resources.displayMetrics.density
            var wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            var hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            if (wDp <= 0) wDp = 250
            if (hDp <= 0) hDp = 100
            var w = (wDp * density).toInt().coerceAtLeast(40)
            var h = (hDp * density).toInt().coerceAtLeast(40)
            // Widget pictures have a memory limit; keep them small.
            val maxPixels = 600_000.0
            val px = w.toDouble() * h
            if (px > maxPixels) {
                val k = Math.sqrt(maxPixels / px)
                w = (w * k).toInt().coerceAtLeast(40)
                h = (h * k).toInt().coerceAtLeast(40)
            }

            val rv = RemoteViews(context.packageName, R.layout.widget_clock)
            try {
                val bmp = ClockRenderer.render(context, Store.theme(context), w, h)
                rv.setImageViewBitmap(R.id.clock_img, bmp)
            } catch (e: Exception) {
                // Leave the previous picture in place rather than crash the home screen.
            }
            val open = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            m.updateAppWidget(id, rv)
        }

        private fun tickIntent(context: Context): PendingIntent {
            val i = Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /** Wake up at the start of the next minute. */
        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (ids(context).isEmpty()) { cancel(context); return }
            val now = System.currentTimeMillis()
            val next = (now / 60_000L + 1) * 60_000L + 300L
            val pi = tickIntent(context)
            try {
                val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
            }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(tickIntent(context))
        }
    }
}
