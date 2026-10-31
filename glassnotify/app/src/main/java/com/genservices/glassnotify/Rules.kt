package com.genservices.glassnotify

import android.content.Context
import java.util.Calendar

/**
 * All "should this notification show?" rules, loaded ONCE into memory.
 * Call Rules.reload(context) after a setting changes (MainActivity already does).
 * Order: blocked -> keyword block -> VIP / keyword allow (breaks quiet hours) -> silent -> quiet hours -> normal.
 */
object Rules {
    const val HIDE = 0
    const val NORMAL = 1
    const val VIP = 2

    class Snap(
        val blocked: Set<String>,
        val vip: Set<String>,
        val silent: Set<String>,
        val hideText: Set<String>,
        val kwBlock: List<String>,
        val kwAllow: List<String>,
        val quiet: Boolean,
        val fromHour: Int,
        val toHour: Int
    ) {
        private fun quietNow(): Boolean {
            if (!quiet) return false
            val cal = Calendar.getInstance()
            val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val a = fromHour * 60
            val b = toHour * 60
            return if (a <= b) m in a until b else (m >= a || m < b)
        }

        /** haystack = title + text. Returns HIDE, NORMAL or VIP. */
        fun decide(pkg: String, haystack: String): Int {
            if (pkg in blocked) return HIDE
            val low = haystack.lowercase()
            for (k in kwBlock) if (low.contains(k)) return HIDE
            var strong = pkg in vip
            if (!strong) for (k in kwAllow) if (low.contains(k)) { strong = true; break }
            if (strong) return VIP
            if (pkg in silent) return HIDE
            if (quietNow()) return HIDE
            return NORMAL
        }
    }

    @Volatile private var snap: Snap? = null

    fun ensure(c: Context): Snap = snap ?: reload(c)

    fun reload(c: Context): Snap {
        val n = Snap(
            blocked = pkgs(Prefs.text(c, "blocked")),
            vip = pkgs(Prefs.text(c, "vip")),
            silent = pkgs(Prefs.text(c, "silent")),
            hideText = pkgs(Prefs.text(c, "hideText")),
            kwBlock = words(Prefs.text(c, "kwBlock")),
            kwAllow = words(Prefs.text(c, "kwAllow")),
            quiet = Prefs.bool(c, "quiet", false),
            fromHour = Prefs.num(c, "quietFrom", 22),
            toHour = Prefs.num(c, "quietTo", 7)
        )
        snap = n
        return n
    }

    private fun pkgs(s: String): Set<String> =
        s.split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()

    private fun words(s: String): List<String> =
        s.split(',', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}
