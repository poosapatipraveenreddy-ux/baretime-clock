package app.baretime.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.RemoteViews

/** Home-screen clock. Redraws once a minute from the phone's own time; no network involved. */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(ctx, mgr, it) }
        schedule(ctx)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        update(ctx, mgr, id)
    }

    override fun onEnabled(ctx: Context) = schedule(ctx)
    override fun onDisabled(ctx: Context) = cancel(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        when (intent.action) {
            ACTION_TICK, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_LOCALE_CHANGED -> {
                refreshAll(ctx)
                schedule(ctx)
            }
        }
    }

    companion object {
        private const val ACTION_TICK = "app.baretime.clock.TICK"

        /** Redraw every widget now (used after "Apply to widget"). */
        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ClockWidgetProvider::class.java))
            ids.forEach { update(ctx, mgr, it) }
            if (ids.isNotEmpty()) schedule(ctx)
        }

        private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            try {
                val (w, h) = sizePx(ctx, mgr.getAppWidgetOptions(id))
                val design = Design.parse(ctx, DesignStore.load(ctx))
                val bmp = ClockRenderer.render(ctx, design, w, h)
                val views = RemoteViews(ctx.packageName, R.layout.widget_clock)
                views.setImageViewBitmap(R.id.clock_img, bmp)
                val open = PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, open)
                mgr.updateAppWidget(id, views)
            } catch (e: Exception) {
                // never let a drawing problem crash the launcher's widget host
            }
        }

        private fun sizePx(ctx: Context, o: Bundle): Pair<Int, Int> {
            val dm = ctx.resources.displayMetrics
            val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            var wDp = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            var hDp = if (land) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            if (wDp <= 0) wDp = 250
            if (hDp <= 0) hDp = 110
            var w = (wDp * dm.density).toInt().coerceAtLeast(40)
            var h = (hDp * dm.density).toInt().coerceAtLeast(20)
            // Android rejects widget bitmaps much larger than ~1.5x the screen; stay well under.
            val maxPx = dm.widthPixels.toLong() * dm.heightPixels * 1.0
            if (w.toLong() * h > maxPx) {
                val k = Math.sqrt(maxPx / (w.toDouble() * h))
                w = (w * k).toInt(); h = (h * k).toInt()
            }
            return w to h
        }

        /** Inexact alarm at the next minute. Re-armed from every tick, so it keeps going. */
        private fun schedule(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L + 300L
            am.setAndAllowWhileIdle(AlarmManager.RTC, next, tickIntent(ctx))
        }

        private fun cancel(ctx: Context) {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(tickIntent(ctx))
        }

        private fun tickIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, 1,
            Intent(ctx, ClockWidgetProvider::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
