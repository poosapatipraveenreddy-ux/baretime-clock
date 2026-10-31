package com.genservices.baretime

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Tiny local storage: the design the widget draws, and the updated designer files. */
object Store {
    private const val PREFS = "baretime"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveTheme(c: Context, json: String) {
        prefs(c).edit().putString("theme", json).apply()
    }

    fun theme(c: Context): JSONObject =
        try { JSONObject(prefs(c).getString("theme", null) ?: "{}") } catch (e: Exception) { JSONObject() }

    // ---- designer files downloaded by the updater (overlay on top of the bundled copy) ----
    fun webDir(c: Context) = File(c.filesDir, "web")

    fun designerVersion(c: Context): String {
        prefs(c).getString("designer_version", null)?.let { if (File(webDir(c), "index.html").exists()) return it }
        return bundledVersion(c)
    }

    fun setDesignerVersion(c: Context, v: String) {
        prefs(c).edit().putString("designer_version", v).apply()
    }

    fun clearDesignerOverride(c: Context) {
        prefs(c).edit().remove("designer_version").apply()
        webDir(c).deleteRecursively()
    }

    /** The version string is the VERSION constant in sw.js, so there is one number to bump. */
    fun bundledVersion(c: Context): String = try {
        val txt = c.assets.open("sw.js").bufferedReader().use { it.readText() }
        Regex("VERSION\\s*=\\s*'([^']+)'").find(txt)?.groupValues?.get(1) ?: "bundled"
    } catch (e: Exception) { "bundled" }

    fun lastCheck(c: Context): Long = prefs(c).getLong("last_check", 0L)
    fun setLastCheck(c: Context, t: Long) { prefs(c).edit().putLong("last_check", t).apply() }
}
