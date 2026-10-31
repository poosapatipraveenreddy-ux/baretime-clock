package com.genservices.glassnotify

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Optional history, kept ONLY on this phone in one small text file (about 500 newest items).
 * Everything runs on a background thread so the card is never delayed.
 */
object History {
    class Item(val time: Long, val pkg: String, val app: String, val title: String, val text: String)

    private const val FILE = "history.jsonl"
    private const val MAX = 500
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var count = -1
    private var lastKey = ""

    fun add(c: Context, pkg: String, app: String, title: String, text: String) {
        val key = "$pkg|$title|$text"
        if (key == lastKey) return                    // apps often re-post the same notification
        lastKey = key
        val dir = c.applicationContext.filesDir
        val now = System.currentTimeMillis()
        io.execute {
            try {
                val f = File(dir, FILE)
                val line = JSONObject().put("t", now).put("p", pkg).put("a", app).put("s", title).put("m", text).toString()
                f.appendText(line + "\n")
                count = if (count < 0) f.readLines().size else count + 1
                if (count > MAX + 100) {                   // trim in batches, not on every add
                    val keep = f.readLines().takeLast(MAX)
                    f.writeText(keep.joinToString("\n") + "\n")
                    count = keep.size
                }
            } catch (e: Exception) {
                // history is optional
            }
        }
    }

    /** Newest first. `done` runs on the main thread. */
    fun load(c: Context, done: (List<Item>) -> Unit) {
        val dir = c.applicationContext.filesDir
        io.execute {
            val out = ArrayList<Item>()
            try {
                val f = File(dir, FILE)
                if (f.exists()) {
                    for (line in f.readLines()) {
                        try {
                            val o = JSONObject(line)
                            out.add(Item(o.getLong("t"), o.getString("p"), o.getString("a"), o.getString("s"), o.getString("m")))
                        } catch (e: Exception) {
                            // skip a damaged line
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            out.reverse()
            main.post { done(out) }
        }
    }

    fun clear(c: Context) {
        val dir = c.applicationContext.filesDir
        lastKey = ""
        io.execute {
            try { File(dir, FILE).delete() } catch (e: Exception) { /* ignore */ }
            count = 0
        }
    }
}
