package com.butter.professorscinema

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var uiWebView: WebView
    private lateinit var cinebyWebView: WebView
    private lateinit var cinebyContainer: LinearLayout
    private lateinit var cinebyUrlBar: TextView
    private lateinit var storage: StorageBridge
    private lateinit var cinebySaveBtn: Button

    // Shared state so the JS bridge can trigger save
    var lastTrackedUrl: String = ""
    var lastTrackedMeta: String = "" // JSON string

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        goFullscreen()

        uiWebView      = findViewById(R.id.webView)
        cinebyWebView  = findViewById(R.id.cinebyWebView)
        cinebyContainer = findViewById(R.id.cinebyContainer)
        cinebyUrlBar   = findViewById(R.id.cinebyUrlBar)
        cinebySaveBtn  = findViewById(R.id.cinebySaveBtn)
        storage        = StorageBridge(this)

        // ---- UI WebView (home screen) ----
        configureWebView(uiWebView, isUi = true)
        uiWebView.addJavascriptInterface(storage, "AndroidStorage")
        uiWebView.addJavascriptInterface(NavigationBridge(), "AndroidNav")
        uiWebView.loadUrl("file:///android_asset/web/index.html")

        // ---- Cineby WebView ----
        configureWebView(cinebyWebView, isUi = false)
        cinebyWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url != "about:blank") {
                    cinebyUrlBar.text = url
                    // Notify UI WebView so it can track history
                    uiWebView.post {
                        val escaped = url.replace("'", "\\'")
                        uiWebView.evaluateJavascript(
                            "if(window.onCinebyUrlChange) window.onCinebyUrlChange('$escaped');",
                            null
                        )
                    }
                }
            }
        }

        // ---- Top bar buttons ----
        findViewById<Button>(R.id.cinebyHomeBtn).setOnClickListener { goHome() }
        findViewById<Button>(R.id.cinebyBackBtn).setOnClickListener {
            if (cinebyWebView.canGoBack()) cinebyWebView.goBack()
        }
        findViewById<Button>(R.id.cinebyForwardBtn).setOnClickListener {
            if (cinebyWebView.canGoForward()) cinebyWebView.goForward()
        }
        findViewById<Button>(R.id.cinebyReloadBtn).setOnClickListener {
            cinebyWebView.reload()
        }
        cinebySaveBtn.setOnClickListener {
            // Tell UI WebView to save the current item
            uiWebView.evaluateJavascript(
                "if(window.saveCurrentFromAndroid) window.saveCurrentFromAndroid();",
                null
            )
        }
    }

    // Called by NavigationBridge (from UI WebView JS)
    fun openCinebyUrl(url: String) {
        runOnUiThread {
            cinebyContainer.visibility = View.VISIBLE
            cinebyUrlBar.text = url
            if (cinebyWebView.url != url) {
                cinebyWebView.loadUrl(url)
            }
        }
    }

    fun goHome() {
        runOnUiThread {
            cinebyContainer.visibility = View.GONE
            // Notify home screen to refresh
            uiWebView.evaluateJavascript("if(window.onReturnHome) window.onReturnHome();", null)
        }
    }

    // JS interface injected into the UI WebView so JS can ask Kotlin to open a URL
    inner class NavigationBridge {
        @JavascriptInterface
        fun openUrl(url: String) {
            openCinebyUrl(url)
        }

        @JavascriptInterface
        fun goHome() {
            this@MainActivity.goHome()
        }

        @JavascriptInterface
        fun isInCinebyView(): Boolean {
            return cinebyContainer.visibility == View.VISIBLE
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(wv: WebView, isUi: Boolean) {
        val s: WebSettings = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.userAgentString =
            "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                android.util.Log.d("WebView", "${msg?.message()} @ ${msg?.sourceId()}:${msg?.lineNumber()}")
                return true
            }
        }

        if (!isUi) {
            wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
            }
        }
    }

    private fun goFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    override fun onBackPressed() {
        if (cinebyContainer.visibility == View.VISIBLE) {
            if (cinebyWebView.canGoBack()) {
                cinebyWebView.goBack()
            } else {
                goHome()
            }
            return
        }
        uiWebView.evaluateJavascript(
            "(function(){ if(window.handleAndroidBack) return window.handleAndroidBack(); return false; })()"
        ) { result ->
            if (result != "true") super.onBackPressed()
        }
    }
}

// ===================
// STORAGE BRIDGE
// ===================
class StorageBridge(private val context: Context) {

    private val prefs = context.getSharedPreferences("cineby_data", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun getHistory(): String = prefs.getString("history", "[]") ?: "[]"

    @JavascriptInterface
    fun addHistory(entryJson: String): String {
        val newEntry = JSONObject(entryJson)
        val newUrl = newEntry.optString("url")
        val current = JSONArray(prefs.getString("history", "[]"))
        val updated = JSONArray()
        for (i in 0 until current.length()) {
            val item = current.getJSONObject(i)
            if (item.optString("url") != newUrl) updated.put(item)
        }
        val result = JSONArray()
        result.put(newEntry)
        for (i in 0 until updated.length()) {
            if (i >= 49) break
            result.put(updated.getJSONObject(i))
        }
        prefs.edit().putString("history", result.toString()).apply()
        return result.toString()
    }

    @JavascriptInterface
    fun removeHistory(url: String): String {
        val current = JSONArray(prefs.getString("history", "[]"))
        val updated = JSONArray()
        for (i in 0 until current.length()) {
            val item = current.getJSONObject(i)
            if (item.optString("url") != url) updated.put(item)
        }
        prefs.edit().putString("history", updated.toString()).apply()
        return updated.toString()
    }

    @JavascriptInterface
    fun clearHistory(): String {
        prefs.edit().putString("history", "[]").apply()
        return "[]"
    }

    @JavascriptInterface
    fun getBookmarks(): String = prefs.getString("bookmarks", "[]") ?: "[]"

    @JavascriptInterface
    fun addBookmark(entryJson: String): String {
        val newEntry = JSONObject(entryJson)
        val newUrl = newEntry.optString("url")
        val current = JSONArray(prefs.getString("bookmarks", "[]"))
        val updated = JSONArray()
        for (i in 0 until current.length()) {
            val item = current.getJSONObject(i)
            if (item.optString("url") != newUrl) updated.put(item)
        }
        val result = JSONArray()
        result.put(newEntry)
        for (i in 0 until updated.length()) {
            result.put(updated.getJSONObject(i))
        }
        prefs.edit().putString("bookmarks", result.toString()).apply()
        return result.toString()
    }

    @JavascriptInterface
    fun removeBookmark(url: String): String {
        val current = JSONArray(prefs.getString("bookmarks", "[]"))
        val updated = JSONArray()
        for (i in 0 until current.length()) {
            val item = current.getJSONObject(i)
            if (item.optString("url") != url) updated.put(item)
        }
        prefs.edit().putString("bookmarks", updated.toString()).apply()
        return updated.toString()
    }

    @JavascriptInterface
    fun getApiKey(): String = prefs.getString("tmdbApiKey", "") ?: ""

    @JavascriptInterface
    fun setApiKey(key: String): Boolean {
        prefs.edit().putString("tmdbApiKey", key).apply()
        return true
    }

    @JavascriptInterface
    fun getStartUrl(): String = prefs.getString("startUrl", "https://www.cineby.app") ?: "https://www.cineby.app"

    @JavascriptInterface
    fun setStartUrl(url: String): Boolean {
        prefs.edit().putString("startUrl", url).apply()
        return true
    }

    @JavascriptInterface
    fun getStoragePath(): String = "Android SharedPreferences (internal)"
}
