package com.genservices.glassnotify

import android.content.Context

/** Small settings store. Read once per notification from memory-cached SharedPreferences, so it is instant. */
object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("glassnotify", Context.MODE_PRIVATE)

    fun top(c: Context) = p(c).getBoolean("top", true)
    fun setTop(c: Context, v: Boolean) = p(c).edit().putBoolean("top", v).apply()

    fun seconds(c: Context) = p(c).getInt("seconds", 5)
    fun setSeconds(c: Context, v: Int) = p(c).edit().putInt("seconds", v).apply()

    fun blur(c: Context) = p(c).getBoolean("blur", true)
    fun setBlur(c: Context, v: Boolean) = p(c).edit().putBoolean("blur", v).apply()

    /** Package names (comma separated) that must never show a card, e.g. com.whatsapp */
    fun blockedText(c: Context) = p(c).getString("blocked", "") ?: ""
    fun setBlockedText(c: Context, v: String) = p(c).edit().putString("blocked", v).apply()
    fun blocked(c: Context): Set<String> =
        blockedText(c).split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    // ---- generic helpers (used by Rules, Sound, History and the settings screen) ----
    fun bool(c: Context, key: String, def: Boolean) = p(c).getBoolean(key, def)
    fun setBool(c: Context, key: String, v: Boolean) = p(c).edit().putBoolean(key, v).apply()
    fun num(c: Context, key: String, def: Int) = p(c).getInt(key, def)
    fun setNum(c: Context, key: String, v: Int) = p(c).edit().putInt(key, v).apply()
    fun text(c: Context, key: String) = p(c).getString(key, "") ?: ""
    fun setText(c: Context, key: String, v: String) = p(c).edit().putString(key, v).apply()

    // ---- named shortcuts ----
    fun accentOn(c: Context) = bool(c, "accent", true)       // tint the card with the app's color
    fun pill(c: Context) = bool(c, "pill", false)            // small card: sender only
    fun hideOriginal(c: Context) = bool(c, "hideOrig", false) // remove the system notification when a card shows
    fun historyOn(c: Context) = bool(c, "history", false)    // off by default: nothing is stored unless the user turns it on
    fun quickRepliesOn(c: Context) = bool(c, "quickRepliesOn", true)
    fun quickRepliesText(c: Context) = text(c, "quickReplies").ifBlank { "👍 OK, On my way, Call you later" }
    fun quickReplies(c: Context): List<String> =
        quickRepliesText(c).split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
}
