package app.baretime.clock

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Serves the designer to the WebView from local storage only:
 *   web/      newest downloaded copy (filesDir), else the copy bundled in the APK
 *   fonts/    fonts bundled in the APK
 * and answers Google Fonts CSS requests from the bundled fonts, so nothing needs the network.
 */
class SiteHandler(private val ctx: Context) {

    val web = WebViewAssetLoader.PathHandler { path ->
        val rel = (clean(path) ?: return@PathHandler null).ifEmpty { "index.html" }
        openWeb(rel)?.let { WebResourceResponse(mime(rel), "utf-8", it) }
    }

    val fonts = WebViewAssetLoader.PathHandler { path ->
        val rel = clean(path) ?: return@PathHandler null
        try { WebResourceResponse(mime(rel), null, ctx.assets.open("fonts/$rel")) } catch (e: FileNotFoundException) { null }
    }

    private fun openWeb(rel: String): InputStream? {
        WebUpdater.activeDir(ctx)?.let { dir ->
            val f = File(dir, rel)
            if (f.isFile && f.canonicalPath.startsWith(dir.canonicalPath + File.separator)) return FileInputStream(f)
        }
        return try { ctx.assets.open("web/$rel") } catch (e: FileNotFoundException) { null }
    }

    /** Returns a safe relative path, or null for anything suspicious. */
    private fun clean(path: String): String? {
        val p = path.trimStart('/')
        if (p.contains("..") || p.contains('\\')) return null
        return p
    }

    companion object {
        private val types = mapOf(
            "html" to "text/html", "js" to "text/javascript", "css" to "text/css", "json" to "application/json",
            "svg" to "image/svg+xml", "png" to "image/png", "webmanifest" to "application/manifest+json",
            "ttf" to "font/ttf", "md" to "text/plain"
        )
        fun mime(name: String) = types[name.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

        fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

        /** Answer https://fonts.googleapis.com/css2?family=... with @font-face rules for the bundled files. */
        fun fontCss(ctx: Context, url: Uri): WebResourceResponse {
            val book = FontBook.faces(ctx)
            val sb = StringBuilder()
            for (fam in url.getQueryParameters("family")) {
                val name = fam.substringBefore(':').replace('+', ' ')
                val faces = book[name] ?: continue
                val base = "https://${MainActivity.DOMAIN}/fonts/"
                val seen = HashSet<String>()
                for (f in faces) {
                    if (f.variable) {
                        if (!seen.add(f.file)) continue
                        val ws = faces.filter { it.file == f.file }.map { it.weight }
                        val minW = ws.minOrNull() ?: f.weight
                        val maxW = ws.maxOrNull() ?: f.weight
                        sb.append("@font-face{font-family:'$name';font-style:normal;font-weight:$minW $maxW;")
                    } else {
                        sb.append("@font-face{font-family:'$name';font-style:normal;font-weight:${f.weight};")
                    }
                    sb.append("font-display:swap;src:url(${base}${f.file}) format('truetype');}\n")
                }
            }
            return WebResourceResponse("text/css", "utf-8", 200, "OK",
                mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(sb.toString().toByteArray()))
        }
    }
}
