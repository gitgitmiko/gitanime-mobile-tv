package com.gitanime.tv

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

private const val BASE_URL = "https://gitanime-web.vercel.app/"

class MainActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            androidx.compose.material3.MaterialTheme {
                val bgColor = androidx.compose.material3.MaterialTheme.colorScheme.background
                androidx.compose.material3.Surface(
                    modifier = Modifier.background(bgColor),
                    color = bgColor
                ) {
                    val context = LocalContext.current

                    var webView: WebView? by remember { mutableStateOf(null) }
                    var isFullScreen by remember { mutableStateOf(false) }
                    var customView: View? by remember { mutableStateOf(null) }
                    var customViewCallback: WebChromeClient.CustomViewCallback? by remember { mutableStateOf(null) }

                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.background(bgColor)
                    ) {
                        AndroidView(
                            modifier = Modifier,
                            factory = {
                                val wv = WebView(context)
                                wv.setBackgroundColor(Color.BLACK)

                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

                                wv.settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    loadsImagesAutomatically = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    userAgentString =
                                        "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                }

                                wv.isFocusable = true
                                wv.isFocusableInTouchMode = true
                                wv.requestFocus(View.FOCUS_DOWN)

                                val initFocusJs = (
                                    "(function(){" +
                                        "document.body.style.outline='none';" +
                                        "var css=':focus{outline: 2px solid #00BCD4 !important; outline-offset:2px}';" +
                                        "var s=document.createElement('style'); s.innerHTML=css; document.head.appendChild(s);" +
                                        "})();"
                                    )

                                wv.setOnKeyListener { _, keyCode, event ->
                                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                                    when (keyCode) {
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            wv.evaluateJavascript("(function(){var el=document.activeElement||document.body; if(el && el.nextElementSibling){el.nextElementSibling.focus(); el.nextElementSibling.scrollIntoView({block:'center'}); return true;} window.scrollBy(0, 150); return false;})();", null)
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            wv.evaluateJavascript("(function(){var el=document.activeElement||document.body; if(el && el.previousElementSibling){el.previousElementSibling.focus(); el.previousElementSibling.scrollIntoView({block:'center'}); return true;} window.scrollBy(0, -150); return false;})();", null)
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            wv.evaluateJavascript("window.scrollBy(-150, 0);", null)
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            wv.evaluateJavascript("window.scrollBy(150, 0);", null)
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                                            val js = """
                                                (function(){
                                                  var x = Math.floor(window.innerWidth/2), y = Math.floor(window.innerHeight/2);
                                                  var el = (document.activeElement && document.activeElement !== document.body) ? document.activeElement : document.elementFromPoint(x,y);
                                                  if(!el) return false;
                                                  try {
                                                    var evt = new MouseEvent('click', {bubbles:true, cancelable:true, view: window});
                                                    el.dispatchEvent(evt);
                                                    if (el.tagName === 'VIDEO') {
                                                      if (el.paused) el.play(); else el.pause();
                                                    }
                                                  } catch(e) {}
                                                  return true;
                                                })();
                                            """.trimIndent()
                                            wv.evaluateJavascript(js, null)
                                            true
                                        }
                                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                            wv.evaluateJavascript("(function(){var v=document.querySelector('video'); if(!v) return false; if(v.paused) v.play(); else v.pause(); return true;})();", null)
                                            true
                                        }
                                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                            wv.evaluateJavascript("(function(){var v=document.querySelector('video'); if(v) v.play();})();", null)
                                            true
                                        }
                                        KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> {
                                            wv.evaluateJavascript("(function(){var v=document.querySelector('video'); if(v) v.pause();})();", null)
                                            true
                                        }
                                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                            wv.evaluateJavascript("(function(){var v=document.querySelector('video'); if(v){try{v.currentTime = Math.min(v.duration||1e9, v.currentTime + 10);}catch(e){}}})();", null)
                                            true
                                        }
                                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                            wv.evaluateJavascript("(function(){var v=document.querySelector('video'); if(v){try{v.currentTime = Math.max(0, v.currentTime - 10);}catch(e){}}})();", null)
                                            true
                                        }
                                        KeyEvent.KEYCODE_WINDOW -> {
                                            enterPipIfPossible()
                                            true
                                        }
                                        else -> false
                                    }
                                }

                                wv.webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.evaluateJavascript(initFocusJs, null)
                                    }
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
                                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) { handler?.proceed() }
                                }

                                wv.webChromeClient = object : WebChromeClient() {
                                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                        if (customView != null) {
                                            callback?.onCustomViewHidden()
                                            return
                                        }
                                        customView = view
                                        customViewCallback = callback
                                        isFullScreen = true

                                        this@MainActivity.window.decorView.systemUiVisibility = (
                                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                            )

                                        (this@MainActivity.window.decorView as? ViewGroup)?.let { root ->
                                            root.addView(
                                                view,
                                                ViewGroup.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                            )
                                        }
                                    }

                                    override fun onHideCustomView() {
                                        customView?.let { v ->
                                            (this@MainActivity.window.decorView as? ViewGroup)?.removeView(v)
                                        }
                                        customView = null
                                        customViewCallback?.onCustomViewHidden()
                                        customViewCallback = null
                                        isFullScreen = false

                                        this@MainActivity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                                    }
                                }

                                wv.loadUrl(BASE_URL)
                                webView = wv
                                wv
                            },
                            update = { wv ->
                                wv.setBackgroundColor(bgColor.toArgb())
                            }
                        )

                        BackHandler(enabled = true) {
                            when {
                                isFullScreen -> {
                                    (webView?.webChromeClient as? WebChromeClient)?.onHideCustomView()
                                }
                                webView?.canGoBack() == true -> webView?.goBack()
                                else -> finish()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun enterPipIfPossible() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto masuk PiP saat user menekan Home
        enterPipIfPossible()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        // Bisa ditambahkan penyesuaian UI saat PiP jika diperlukan
    }
}
