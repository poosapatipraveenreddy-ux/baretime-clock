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
}
