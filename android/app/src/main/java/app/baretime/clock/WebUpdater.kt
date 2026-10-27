package app.baretime.clock

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Keeps the designer up to date without a new APK.
 *  1. Reads <SITE_URL>/site.json (version + SHA-256 of every file).
 *  2. If newer, downloads every file into a temp folder and checks every hash.
 *  3. Only when ALL files are verified does it switch to the new folder (used from the next launch).
 * A failed or partial download never changes what the app shows.
 */
object WebUpdater {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("baretime_web", Context.MODE_PRIVATE)

    fun bundledVersion(ctx: Context): String = try {
        JSONObject(ctx.assets.open("web/site.json").bufferedReader().use { it.readText() }).optString("version", "0")
    } catch (e: Exception) { "0" }

    /** Folder of the downloaded copy, if it is newer than the one inside the APK. */
    fun activeDir(ctx: Context): File? {
        val v = prefs(ctx).getString("active", null) ?: return null
        if (compare(v, bundledVersion(ctx)) <= 0) return null   // a newer APK shipped newer files
        val dir = File(ctx.filesDir, "web/$v")
        return if (File(dir, "index.html").isFile) dir else null
    }

    fun currentVersion(ctx: Context): String = prefs(ctx).getString("active", null)?.takeIf { activeDir(ctx) != null } ?: bundledVersion(ctx)

    /** Compares "1.10.0" style versions numerically. */
    fun compare(a: String, b: String): Int {
        val x = a.trimStart('v').split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.trimStart('v').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun checkInBackground(ctx: Context) {
        val app = ctx.applicationContext
        Thread { try { run(app) } catch (e: Exception) { /* offline or bad upload: keep what we have */ } }.start()
    }

    private fun run(ctx: Context) {
        val site = BuildConfig.SITE_URL
        if (site.isBlank()) return
        val base = if (site.endsWith("/")) site else "$site/"
        val manifest = JSONObject(String(fetch(URL("${base}site.json?t=${System.currentTimeMillis()}"), 200_000)))
        val version = manifest.getString("version")
        if (compare(version, currentVersion(ctx)) <= 0) return
        if (manifest.optInt("minBridge", 1) > BuildConfig.BRIDGE_LEVEL) return   // needs a newer app first

        val files = manifest.getJSONArray("files")
        val tmp = File(ctx.filesDir, "web-tmp")
        tmp.deleteRecursively(); tmp.mkdirs()
        var total = 0L
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val path = f.getString("path")
            if (path.startsWith("/") || path.contains("..") || path.contains('\\')) return
            val data = fetch(URL(base + path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }), 8_000_000)
            total += data.size
            if (total > 25_000_000) return
            if (sha256(data) != f.getString("sha256").lowercase()) { tmp.deleteRecursively(); return }
            val out = File(tmp, path)
            out.parentFile?.mkdirs()
            out.writeBytes(data)
        }
        if (!File(tmp, "index.html").isFile) { tmp.deleteRecursively(); return }

        val dest = File(ctx.filesDir, "web/$version")
        dest.deleteRecursively(); dest.parentFile?.mkdirs()
        if (!tmp.renameTo(dest)) { tmp.deleteRecursively(); return }
        prefs(ctx).edit().putString("active", version).apply()
        File(ctx.filesDir, "web").listFiles()?.filter { it.name != version }?.forEach { it.deleteRecursively() }
    }

    private fun fetch(url: URL, maxBytes: Int): ByteArray {
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            c.inputStream.use { ins ->
                val bytes = ins.readBytes()
                if (bytes.size > maxBytes) throw IllegalStateException("too large")
                return bytes
            }
        } finally { c.disconnect() }
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** Latest GitHub release newer than this APK? Returns (tag, url) or null. */
    fun latestApk(): Pair<String, String>? {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) return null
        return try {
            val j = JSONObject(String(fetch(URL("https://api.github.com/repos/$repo/releases/latest"), 500_000)))
            val tag = j.getString("tag_name")
            if (compare(tag, BuildConfig.VERSION_NAME) > 0) tag to j.getString("html_url") else null
        } catch (e: Exception) { null }
    }
}
