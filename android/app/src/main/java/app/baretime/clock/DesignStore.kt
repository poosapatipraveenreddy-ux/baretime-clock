package app.baretime.clock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** The one design every widget shows. Stored as the designer's own JSON. */
object DesignStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("baretime", Context.MODE_PRIVATE)
    fun load(ctx: Context): String? = prefs(ctx).getString("design", null)
    fun save(ctx: Context, json: String) { prefs(ctx).edit().putString("design", json).apply() }
    fun tapMode(ctx: Context): String = prefs(ctx).getString("tap", "none") ?: "none"
    fun setTapMode(ctx: Context, mode: String) {
        prefs(ctx).edit().putString("tap", if (mode == "clock" || mode == "app") mode else "none").apply()
    }
    fun widgetIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, ClockWidgetProvider::class.java))
}
