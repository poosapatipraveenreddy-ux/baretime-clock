package com.genservices.baretime

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Hosts the BareTime designer (the same web page as the website) inside the app.
 * The page is served from files bundled in the APK (or from newer files the updater
 * downloaded), through a private address, so it works with no internet.
 * "Apply to widget" in the page calls the small bridge below.
 */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private var chooserCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        const val HOST = "baretime.app.local"
        private const val REQ_FILE = 7
        private val TOKEN = Regex("^[A-Za-z0-9_-]{1,8000}$")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.setBackgroundColor(0xFF0B0B0F.toInt())
        web.addJavascriptInterface(Bridge(), "BareTimeAndroid")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                if (u.host == HOST) return serve(u.path ?: "/")
                // The designer needs no internet, so nothing else is allowed to load.
                return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != HOST
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                chooserCallback?.onReceiveValue(null)
                chooserCallback = cb
                return try {
                    startActivityForResult(params.createIntent(), REQ_FILE)
                    true
                } catch (e: Exception) {
                    chooserCallback = null
                    cb.onReceiveValue(null)
                    true
                }
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity).setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }

            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
                val input = EditText(this@MainActivity)
                input.setText(defaultValue ?: "")
                AlertDialog.Builder(this@MainActivity).setMessage(message).setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(input.text.toString()) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }
        }

        load(intent, false)

        // Quietly look for a newer designer at most twice a day.
        if (System.currentTimeMillis() - Store.lastCheck(this) > 12L * 3600_000L) {
            val app = applicationContext
            Thread { Updater.check(app) }.start()
        }
    }

    // ------------------------------------------------------------ page loading
    private fun load(i: Intent?, fresh: Boolean) {
        var url = "https://$HOST/index.html"
        val d = i?.data
        var deep = false
        if (d != null && d.scheme == "baretime") {
            val t = d.getQueryParameter("t")
            if (t != null && TOKEN.matches(t)) {
                url += "?r=${System.currentTimeMillis()}#t=$t"
                deep = true
            }
        }
        if (deep || !fresh) web.loadUrl(url)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent, true)
    }

    private fun serve(rawPath: String): WebResourceResponse {
        var rel = rawPath.trimStart('/')
        if (rel.isEmpty()) rel = "index.html"
        if (rel.contains("..")) return notFound()
        val stream: InputStream? = try {
            val dir = Store.webDir(this)
            val f = File(dir, rel)
            if (f.isFile && f.canonicalPath.startsWith(dir.canonicalPath + File.separator)) FileInputStream(f)
            else assets.open(rel)
        } catch (e: Exception) { null }
        if (stream == null) return notFound()
        return WebResourceResponse(mimeOf(rel), if (isText(rel)) "utf-8" else null, 200, "OK",
            mapOf("Cache-Control" to "no-cache"), stream)
    }

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun mimeOf(p: String): String = when (p.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "js" -> "application/javascript"
        "css" -> "text/css"
        "json", "webmanifest" -> "application/json"
        "png" -> "image/png"
        "svg" -> "image/svg+xml"
        "ttf" -> "font/ttf"
        else -> "application/octet-stream"
    }

    private fun isText(p: String) = mimeOf(p).let { it.startsWith("text/") || it == "application/javascript" || it == "application/json" }

    // ------------------------------------------------------------ lifecycle
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.__btBack && window.__btBack()) || false") { r ->
            if (r != "true") {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            chooserCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            chooserCallback = null
        } else {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { web.onPause(); super.onPause() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }

    // ------------------------------------------------------------ bridge used by the page
    inner class Bridge {
        @JavascriptInterface
        fun applyToWidget(json: String): String {
            if (json.length > 30_000) return "That design is too large."
            try { JSONObject(json) } catch (e: Exception) { return "That design couldn’t be read." }
            val app = applicationContext
            Store.saveTheme(app, json)
            ClockWidgetProvider.updateAll(app)
            ClockWidgetProvider.schedule(app)
            return if (ClockWidgetProvider.ids(app).isEmpty())
                "Saved. Now tap “Add widget” to put it on your home screen."
            else "Applied. Your home-screen widget now uses this design."
        }

        @JavascriptInterface
        fun addWidget(): String {
            val m = AppWidgetManager.getInstance(this@MainActivity)
            if (m.isRequestPinAppWidgetSupported) {
                m.requestPinAppWidget(ComponentName(this@MainActivity, ClockWidgetProvider::class.java), null, null)
                return "Choose where to place the widget."
            }
            return "Long-press your home screen, open Widgets and pick BareTime."
        }

        @JavascriptInterface
        fun designerVersion(): String = Store.designerVersion(applicationContext)

        @JavascriptInterface
        fun checkUpdates(): String {
            val r = Updater.check(applicationContext)
            val url = r.apkUrl
            if (url != null) {
                runOnUiThread { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { /* no browser */ } }
                return r.message + " Opening the download page."
            }
            return r.message
        }

        @JavascriptInterface
        fun setKeepScreenOn(on: Boolean) {
            runOnUiThread {
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        @JavascriptInterface
        fun copyText(text: String) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("BareTime", text))
        }

        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                try { startActivity(Intent.createChooser(send, "Share BareTime design")) } catch (e: Exception) { /* ignore */ }
            }
        }
    }
}
