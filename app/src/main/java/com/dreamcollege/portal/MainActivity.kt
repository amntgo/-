package com.dreamcollege.portal

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

private const val HOME = "https://dic-portal.dreamcampus-erp-sd.com/"
private const val HOST = "dreamcampus-erp-sd.com"
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private val NAVY = 0xFF0B1730.toInt()
private val NAVY2 = 0xFF16315F.toInt()
private val GOLD = 0xFFE2B44F.toInt()

class MainActivity : ComponentActivity() {
    private lateinit var outer: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var web: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var bar: ProgressBar
    private lateinit var splash: FrameLayout
    private lateinit var errorView: LinearLayout
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pageOk = true
    private var splashHiding = false
    private val t0 = System.currentTimeMillis()
    private val handler = Handler(Looper.getMainLooper())

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
            fileCallback = null
        }

    // الخطوط: كايرو للعناوين وتجوال للنصوص (تُنزَّل أثناء البناء؛ وإن غابت يُستخدم خط النظام)
    private fun font(path: String, wght: Int? = null): Typeface = try {
        if (wght != null && Build.VERSION.SDK_INT >= 26) {
            Typeface.Builder(assets, path).setFontVariationSettings("'wght' $wght").build() ?: Typeface.DEFAULT
        } else Typeface.createFromAsset(assets, path)
    } catch (e: Exception) { Typeface.DEFAULT }

    private val cairoBold by lazy { font("fonts/Cairo.ttf", 800) }
    private val tajawal by lazy { font("fonts/Tajawal-Regular.ttf") }
    private val tajawalMed by lazy { font("fonts/Tajawal-Medium.ttf") }
    private val tajawalBold by lazy { font("fonts/Tajawal-Bold.ttf") }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        buildUi()
        setContentView(outer)
        ViewCompat.setOnApplyWindowInsetsListener(outer) { _, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            content.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(HOME)
        handler.postDelayed({ hideSplash() }, 12000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUi() {
        outer = FrameLayout(this).apply { setBackgroundColor(NAVY) }
        content = FrameLayout(this)

        web = WebView(this).apply {
            setBackgroundColor(NAVY)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                allowFileAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                userAgentString = userAgentString + " DreamCollegeApp/1.0"
            }
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url
                    val scheme = u.scheme ?: ""
                    val host = u.host ?: ""
                    if ((scheme == "http" || scheme == "https") && (host == HOST || host.endsWith(".$HOST"))) return false
                    openExternal(u)
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    pageOk = true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    swipe.isRefreshing = false
                    bar.visibility = View.GONE
                    if (pageOk) errorView.visibility = View.GONE
                    hideSplash()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        pageOk = false
                        errorView.visibility = View.VISIBLE
                        swipe.isRefreshing = false
                        hideSplash()
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    bar.progress = newProgress
                    bar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }

                override fun onShowFileChooser(
                    webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = callback
                    return try {
                        fileLauncher.launch(params.createIntent())
                        true
                    } catch (e: Exception) {
                        fileCallback = null
                        false
                    }
                }
            }
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                try {
                    val req = DownloadManager.Request(Uri.parse(url))
                    req.setMimeType(mimeType)
                    CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("cookie", it) }
                    req.addRequestHeader("User-Agent", userAgent)
                    val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    req.setTitle(name)
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                    Toast.makeText(this@MainActivity, "بدأ تنزيل الملف", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    openExternal(Uri.parse(url))
                }
            }
        }

        swipe = SwipeRefreshLayout(this).apply {
            addView(web, ViewGroup.LayoutParams(MATCH, MATCH))
            setColorSchemeColors(GOLD)
            setProgressBackgroundColorSchemeColor(NAVY2)
            setOnRefreshListener { web.reload() }
            setOnChildScrollUpCallback { _, _ -> web.scrollY > 0 }
        }
        content.addView(swipe, FrameLayout.LayoutParams(MATCH, MATCH))

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(GOLD)
            progressBackgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            visibility = View.GONE
        }
        content.addView(bar, FrameLayout.LayoutParams(MATCH, dp(3), Gravity.TOP))

        errorView = buildErrorView()
        content.addView(errorView, FrameLayout.LayoutParams(MATCH, MATCH))

        outer.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        splash = buildSplash()
        outer.addView(splash, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun label(text: String, size: Float, color: Int, face: Typeface) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = face
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
    }

    private fun logoBitmap(): Bitmap? = try {
        assets.open("logo.png").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }

    private fun buildErrorView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setBackgroundColor(NAVY)
        visibility = View.GONE
        isClickable = true
        setPadding(dp(32), dp(32), dp(32), dp(32))
        addView(label("📡", 54f, Color.WHITE, tajawal))
        addView(label("تعذّر الاتصال بالمنصة", 22f, Color.WHITE, cairoBold).apply { setPadding(0, dp(16), 0, dp(8)) })
        addView(label("تحقق من اتصالك بالإنترنت ثم حاول مرة أخرى.", 15f, 0xFFB8C3D9.toInt(), tajawal))
        addView(Button(this@MainActivity).apply {
            text = "إعادة المحاولة"
            typeface = tajawalBold
            textSize = 16f
            isAllCaps = false
            setTextColor(NAVY)
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(GOLD) }
            setPadding(dp(32), 0, dp(32), 0)
            setOnClickListener {
                pageOk = true
                errorView.visibility = View.GONE
                val u = web.url
                if (u == null || u == "about:blank") web.loadUrl(HOME) else web.reload()
            }
        }, LinearLayout.LayoutParams(WRAP, dp(48)).apply { topMargin = dp(28) })
    }

    private fun buildSplash(): FrameLayout {
        val s = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(NAVY, NAVY2))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            isClickable = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), 0)
        }
        val bmp = logoBitmap()
        val logo = ImageView(this).apply {
            if (bmp != null) {
                setImageBitmap(bmp)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                setPadding(dp(20), dp(20), dp(20), dp(20))
                scaleType = ImageView.ScaleType.FIT_CENTER
            } else {
                setImageResource(R.drawable.ic_launcher_foreground)
            }
            alpha = 0f; scaleX = 0.7f; scaleY = 0.7f
        }
        col.addView(logo, LinearLayout.LayoutParams(dp(140), dp(140)))
        val title = label("منصة كلية دريم العالمية", 26f, Color.WHITE, cairoBold).apply { alpha = 0f }
        col.addView(title, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(28) })
        val sub = label("الطلاب  •  هيئة التدريس  •  الموظفون", 15f, GOLD, tajawalMed).apply { alpha = 0f }
        col.addView(sub, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(10) })
        val tag = label("التواصل · المحتوى الدراسي · التعليم الإلكتروني", 13f, 0xFFB8C3D9.toInt(), tajawal).apply { alpha = 0f }
        col.addView(tag, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(6) })
        val spin = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(GOLD)
            alpha = 0f
        }
        col.addView(spin, LinearLayout.LayoutParams(dp(30), dp(30)).apply { topMargin = dp(36) })
        s.addView(col, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
        val foot = label("كلية دريم العالمية © 2026", 12f, 0xFF8796B3.toInt(), tajawal)
        s.addView(foot, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(40) })

        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start()
        title.animate().alpha(1f).setStartDelay(300).setDuration(600).start()
        sub.animate().alpha(1f).setStartDelay(500).setDuration(600).start()
        tag.animate().alpha(1f).setStartDelay(650).setDuration(600).start()
        spin.animate().alpha(1f).setStartDelay(800).setDuration(500).start()
        return s
    }

    private fun hideSplash() {
        if (splashHiding) return
        splashHiding = true
        val wait = maxOf(0L, 2000L - (System.currentTimeMillis() - t0))
        handler.postDelayed({
            splash.animate().alpha(0f).setDuration(400).withEndAction { splash.visibility = View.GONE }.start()
        }, wait)
    }

    private fun openExternal(u: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, u))
        } catch (e: Exception) {
            Toast.makeText(this, "لا يوجد تطبيق لفتح هذا الرابط", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}
