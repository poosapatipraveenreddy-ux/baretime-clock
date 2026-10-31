package com.genservices.baretime

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps the designer inside the app fresh without a new APK.
 *
 * It reads  <UPDATE_BASE_URL>designer.json :
 *   { "version": "1.2.0",
 *     "files": ["index.html", "app.js", "styles.css"],
 *     "apk": { "versionCode": 2, "versionName": "1.0.1", "url": "https://github.com/.../releases/latest" } }
 *
 * If "version" differs from the installed designer, every listed file is downloaded into a
 * temporary folder and swapped in only when ALL of them arrived, so a bad connection can
 * never leave a half-updated designer. The new designer is used the next time the app opens.
 * Anything not listed (fonts, icons) keeps coming from the copy bundled inside the APK.
 */
object Updater {
    class Result(val message: String, val apkUrl: String? = null)

    private val SAFE_PATH = Regex("^[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*$")
    private const val MAX_FILE = 3_000_000
    private const val MAX_FILES = 40

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        c.instanceFollowRedirects = true
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val out = java.io.ByteArrayOutputStream()
            c.inputStream.use { ins ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_FILE) throw IllegalStateException("file too large")
                }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }

    fun check(ctx: Context): Result {
        val base = BuildConfig.UPDATE_BASE_URL
        if (!base.startsWith("https://") || base.contains("YOUR-GITHUB-USERNAME")) {
            return Result("Updates aren’t set up yet. Set the update address in the app settings file (see README).")
        }
        try {
            val meta = JSONObject(String(get(base + "designer.json"), Charsets.UTF_8))
            Store.setLastCheck(ctx, System.currentTimeMillis())

            var apkUrl: String? = null
            var apkNote = ""
            meta.optJSONObject("apk")?.let { apk ->
                if (apk.optInt("versionCode", 0) > BuildConfig.VERSION_CODE) {
                    val u = apk.optString("url")
                    if (u.startsWith("https://")) {
                        apkUrl = u
                        apkNote = " A new app version (${apk.optString("versionName")}) is available."
                    }
                }
            }

            val latest = meta.optString("version", "")
            val current = Store.designerVersion(ctx)
            if (latest.isEmpty() || latest == current) {
                return Result("The designer is up to date ($current).$apkNote", apkUrl)
            }

            val files = meta.optJSONArray("files") ?: return Result("Update list was empty.$apkNote", apkUrl)
            if (files.length() == 0 || files.length() > MAX_FILES) return Result("Update list looked wrong, so it was skipped.", apkUrl)

            val tmp = File(ctx.filesDir, "web_new")
            tmp.deleteRecursively()
            tmp.mkdirs()
            for (i in 0 until files.length()) {
                val rel = files.getString(i)
                if (!SAFE_PATH.matches(rel)) throw IllegalStateException("bad path")
                val dest = File(tmp, rel)
                dest.parentFile?.mkdirs()
                dest.writeBytes(get(base + rel))
            }
            if (!File(tmp, "index.html").exists()) throw IllegalStateException("index.html missing")

            val live = Store.webDir(ctx)
            live.deleteRecursively()
            if (!tmp.renameTo(live)) throw IllegalStateException("could not apply update")
            Store.setDesignerVersion(ctx, latest)
            return Result("Designer updated to $latest. Close and reopen the app to use it.$apkNote", apkUrl)
        } catch (e: Exception) {
            File(ctx.filesDir, "web_new").deleteRecursively()
            return Result("Couldn’t check for updates (${e.message ?: "offline"}). Everything still works.")
        }
    }
}
