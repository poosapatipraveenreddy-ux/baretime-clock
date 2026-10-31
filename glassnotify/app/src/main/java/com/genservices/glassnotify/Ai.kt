package com.genservices.glassnotify

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Optional AI summary. Works with Google Gemini, OpenRouter, NVIDIA, Groq, OpenAI, or your own server.
 * The key, address and model are typed into the settings screen and kept only on this phone.
 *
 * PRIVACY: nothing is sent unless the user taps the "Summarize" chip. Text is never logged.
 */
object Ai {
    const val MIN_MESSAGES = 3                       // the chip appears only for chats with this many messages

    private val io = Executors.newSingleThreadExecutor()

    // small cache: tapping the same chat again is instant and free (keeps the 20 newest)
    private val cache = object : LinkedHashMap<Int, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean = size > 20
    }

    @Volatile private var skip: Set<String>? = null

    fun reload(c: Context) {
        skip = load(c)
    }

    private fun load(c: Context): Set<String> {
        val s = Prefs.text(c, "aiSkip").split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
        skip = s
        return s
    }

    /** Switched on in settings AND a key has been pasted. */
    fun enabled(c: Context) = Prefs.bool(c, "ai", false) && Prefs.text(c, "aiKey").isNotBlank()

    /** Apps the user never wants sent to the AI (banks, private chats). */
    fun skipped(c: Context, pkg: String) = pkg in (skip ?: load(c))

    /** Keeps the last 8 messages, each cut to 300 characters, 2000 characters in total. */
    fun pack(lines: List<String>): String {
        val sb = StringBuilder()
        for (l in lines.takeLast(8)) sb.append(l.take(300)).append('\n')
        return sb.toString().trim().take(2000)
    }

    /** `done` is called on a BACKGROUND thread; post to the main thread inside it if you touch views. */
    fun summarize(c: Context, thread: String, done: (String) -> Unit) {
        val ctx = c.applicationContext
        val h = thread.hashCode()
        val hit = synchronized(cache) { cache[h] }
        if (hit != null) { done(hit); return }
        io.execute {
            val out = try { call(ctx, thread) } catch (e: Exception) { "Couldn't summarize (check key, internet and model)." }
            if (!out.startsWith("Couldn't")) synchronized(cache) { cache[h] = out }
            done(out)
        }
    }

    private fun call(c: Context, thread: String): String {
        val key = Prefs.text(c, "aiKey").trim()
        if (key.isEmpty()) return "Couldn't summarize (no API key in settings)."
        val base = Prefs.text(c, "aiUrl").trim().ifBlank { "https://openrouter.ai/api/v1" }.trimEnd('/')
        if (!base.startsWith("https://") && !base.startsWith("http://")) {
            return "Couldn't summarize (address must start with https://)."
        }
        val model = Prefs.text(c, "aiModel").trim().ifBlank { "google/gemini-flash-1.5" }

        val system = "Summarize this chat in at most 2 short sentences. Mention any question asked, " +
            "and any time, place or action needed. Use the same language as the chat. No preamble."
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 160)
            .put("temperature", 0.2)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", thread)))

        val fullUrl = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val conn = URL(fullUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000          // a slow network must never hang the card
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $key")
            // Helpful headers for OpenRouter
            conn.setRequestProperty("HTTP-Referer", "https://baretime.app")
            conn.setRequestProperty("X-Title", "GlassNotify")

            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val err = try { conn.errorStream?.bufferedReader()?.use { it.readText() } } catch (e: Exception) { null }
                return "Couldn't summarize (error $code${if (!err.isNullOrBlank()) ": $err" else ""})."
            }
            val txt = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(txt).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
        } finally {
            conn.disconnect()
        }
    }
}
