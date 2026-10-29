package app.baretime.clock

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

/** Hosts the BareTime designer (the website) in a WebView, fully offline. */
class MainActivity : Activity() {
    companion object {
        const val DOMAIN = "appassets.androidplatform.net"
        private const val REQ_FILE = 11
    }

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val site = SiteHandler(this)
        val loader = WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler("/web/", site.web)
            .addPathHandler("/fonts/", site.fonts)
            .build()

        web = WebView(this)
        setContentView(web)
        web.setBackgroundColor(0xFF0B0B0F.toInt())
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true            // localStorage + IndexedDB (settings, themes, wallpaper)
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                return when (u.host) {
                    DOMAIN -> if (u.path == "/web/sw.js") SiteHandler.notFound()   // no service worker in the app
                              else loader.shouldInterceptRequest(u)
                    "fonts.googleapis.com" -> SiteHandler.fontCss(this@MainActivity, u)
                    else -> SiteHandler.notFound()                                  // the designer never needs the internet
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == DOMAIN) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (e: Exception) { /* no browser */ }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb
                return try { startActivityForResult(params.createIntent(), REQ_FILE); true }
                catch (e: Exception) { fileCallback = null; false }
            }
        }
        web.addJavascriptInterface(Bridge(), "BareTimeAndroid")
        web.loadUrl("https://$DOMAIN/web/index.html")

        WebUpdater.checkInBackground(this)
        checkForNewApk()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // First close the designer panel if it is open, otherwise leave the app.
        web.evaluateJavascript(
            "(function(){if(document.body.classList.contains('panel-open')){document.getElementById('btnClose').click();return 1}return 0})()"
        ) { r -> if (r != "1") finish() }
    }

    override fun onDestroy() { web.destroy(); super.onDestroy() }

    private fun checkForNewApk() {
        Thread {
            val latest = WebUpdater.latestApk() ?: return@Thread
            val prefs = getSharedPreferences("baretime_web", MODE_PRIVATE)
            if (prefs.getString("apk_dismissed", null) == latest.first) return@Thread
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                AlertDialog.Builder(this)
                    .setTitle("New version ${latest.first}")
                    .setMessage("A new BareTime version is available. Download it and tap the file to install over this one.")
                    .setPositiveButton("Download") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.second))) }
                    .setNegativeButton("Later") { _, _ -> prefs.edit().putString("apk_dismissed", latest.first).apply() }
                    .show()
            }
        }.start()
    }

    /** What the designer page can ask the app to do. Only our own bundled page ever loads in this WebView. */
    inner class Bridge {
        /** Saves the design and redraws all widgets. Returns how many widgets exist, or -1 on a bad design. */
        @JavascriptInterface
        fun applyToWidget(json: String): Int {
            if (json.length > 20_000) return -1
            try { JSONObject(json) } catch (e: Exception) { return -1 }
            DesignStore.save(this@MainActivity, json)
            ClockWidgetProvider.refreshAll(this@MainActivity)
            return DesignStore.widgetIds(this@MainActivity).size
        }

        /** Asks the launcher to pin a BareTime widget. False if the launcher can't do that. */
        @JavascriptInterface
        fun pinWidget(): Boolean {
            val mgr = AppWidgetManager.getInstance(this@MainActivity)
            if (!mgr.isRequestPinAppWidgetSupported) return false
            runOnUiThread { mgr.requestPinAppWidget(ComponentName(this@MainActivity, ClockWidgetProvider::class.java), null, null) }
            return true
        }

        /** Opens Android's screen saver settings so the user can pick "BareTime clock". */
        @JavascriptInterface
        fun openScreenSaverSettings(): Boolean {
            runOnUiThread {
                try { startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) }
                catch (e: Exception) {
                    try { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) } catch (e2: Exception) { /* none */ }
                }
            }
            return true
        }

        @JavascriptInterface
        fun setTapAction(mode: String) {
            DesignStore.setTapMode(this@MainActivity, mode)
            ClockWidgetProvider.refreshAll(this@MainActivity)
        }

        @JavascriptInterface
        fun getTapAction(): String = DesignStore.tapMode(this@MainActivity)

        /** Public website address, used for share links. */
        @JavascriptInterface
        fun siteUrl(): String = BuildConfig.SITE_URL

        @JavascriptInterface
        fun bridgeLevel(): Int = BuildConfig.BRIDGE_LEVEL
    }
}
