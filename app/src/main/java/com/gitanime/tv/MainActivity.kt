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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.launch

private const val BASE_URL = "https://gitanime-web.vercel.app/"

class MainActivity : ComponentActivity() {

    private var webViewRef: WebView? = null

    // Target pointer (container pixels)
    private val targetX = mutableStateOf(0f)
    private val targetY = mutableStateOf(0f)
    private var containerWidthPx: Int = 0
    private var containerHeightPx: Int = 0

    private val helperJs = (
        "(function(){\n" +
            "if(window._tvHelper) return;\n" +
            "function biggestIframe(){var ifs=[].slice.call(document.querySelectorAll('iframe'));var best=null,ba=0;for(var i=0;i<ifs.length;i++){var r=ifs[i].getBoundingClientRect();var a=r.width*r.height;if(a>ba){ba=a;best=ifs[i];}}return best;}\n" +
            "function injectCss(){var s=document.getElementById('tvfs-style'); if(s) return s; s=document.createElement('style'); s.id='tvfs-style'; s.innerHTML='html,body{background:#000!important;overflow:hidden!important} .tvfs-target{position:fixed!important;left:0;top:0;width:100vw!important;height:100vh!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important}'; document.head.appendChild(s); return s;}\n" +
            "window._tvHelper={\n" +
            " move:function(x,y){var e=new MouseEvent('mousemove',{clientX:x,clientY:y,bubbles:true});document.dispatchEvent(e);var el=document.elementFromPoint(x,y);if(el){el.dispatchEvent(new MouseEvent('mousemove',{clientX:x,clientY:y,bubbles:true}));}},\n" +
            " click:function(x,y){var el=document.elementFromPoint(x,y);if(!el) return false;var o={clientX:x,clientY:y,bubbles:true,cancelable:true};['pointerdown','mousedown','pointerup','mouseup','click'].forEach(function(t){try{el.dispatchEvent(new MouseEvent(t,o));}catch(e){}});return true;},\n" +
            " toggleFS:function(){var v=document.querySelector('video');injectCss(); if(v){ if(v.classList.contains('tvfs-target')){v.classList.remove('tvfs-target'); return true;} v.classList.add('tvfs-target'); return true;} var f=biggestIframe(); if(f){ if(f.classList.contains('tvfs-target')){f.classList.remove('tvfs-target'); return true;} f.classList.add('tvfs-target'); return true;} return false;},\n" +
            " playPause:function(){var v=document.querySelector('video');if(!v) return false; if(v.paused) v.play(); else v.pause(); return true;}\n" +
            "};\n" +
        "})();"
        )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            androidx.compose.material3.MaterialTheme {
                val bgColor = androidx.compose.material3.MaterialTheme.colorScheme.background
                val density = LocalDensity.current
                val pointerRadiusDp = 12.dp
                val pointerRadiusPx = with(density) { pointerRadiusDp.toPx() }

                // Animated pointer values
                val scope = rememberCoroutineScope()
                val cursorX = remember { Animatable(0f) }
                val cursorY = remember { Animatable(0f) }

                fun animatePointer() {
                    scope.launch {
                        cursorX.animateTo(
                            targetX.value.coerceIn(0f, (containerWidthPx - 1).toFloat()),
                            animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
                        )
                    }
                    scope.launch {
                        cursorY.animateTo(
                            targetY.value.coerceIn(0f, (containerHeightPx - 1).toFloat()),
                            animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
                        )
                    }
                }

                androidx.compose.material3.Surface(
                    modifier = Modifier.background(bgColor),
                    color = bgColor
                ) {
                    val context = LocalContext.current

                    var isFullScreen by remember { mutableStateOf(false) }
                    var customView: View? by remember { mutableStateOf(null) }
                    var customViewCallback: WebChromeClient.CustomViewCallback? by remember { mutableStateOf(null) }

                    Box(
                        modifier = Modifier
                            .background(bgColor)
                            .onGloballyPositioned {
                                containerWidthPx = it.size.width
                                containerHeightPx = it.size.height
                                if (cursorX.value == 0f && cursorY.value == 0f) {
                                    val cx = containerWidthPx / 2f
                                    val cy = containerHeightPx / 2f
                                    targetX.value = cx
                                    targetY.value = cy
                                    scope.launch { cursorX.snapTo(cx) }
                                    scope.launch { cursorY.snapTo(cy) }
                                }
                            }
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

                                val initJs = "(function(){document.body.style.cursor='none';})();"

                                wv.webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.evaluateJavascript(initJs, null)
                                        view?.evaluateJavascript(helperJs, null)
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
                                webViewRef = wv
                                wv
                            },
                            update = { wv ->
                                wv.setBackgroundColor(bgColor.toArgb())
                            }
                        )

                        // Virtual cursor overlay (24dp circle)
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (cursorX.value - pointerRadiusPx).toInt().coerceAtLeast(0),
                                        (cursorY.value - pointerRadiusPx).toInt().coerceAtLeast(0)
                                    )
                                }
                                .size(pointerRadiusDp * 2)
                                .border(2.dp, ComposeColor(0xFF00BCD4), CircleShape)
                        )

                        BackHandler(enabled = true) {
                            when {
                                isFullScreen -> {
                                    (webViewRef?.webChromeClient as? WebChromeClient)?.onHideCustomView()
                                }
                                webViewRef?.canGoBack() == true -> webViewRef?.goBack()
                                else -> finish()
                            }
                        }

                        LaunchedEffect(targetX.value, targetY.value) {
                            animatePointer()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webViewRef?.requestFocus(View.FOCUS_DOWN)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val base = 60f
            val step = base + (event.repeatCount * 18f)
            val edge = 40f
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    targetY.value = (targetY.value + step).coerceAtMost((containerHeightPx - 1).toFloat())
                    if (targetY.value > containerHeightPx - edge) {
                        webViewRef?.evaluateJavascript("window.scrollBy(0, 150);", null)
                    }
                    sendMoveToPage()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    targetY.value = (targetY.value - step).coerceAtLeast(0f)
                    if (targetY.value < edge) {
                        webViewRef?.evaluateJavascript("window.scrollBy(0, -150);", null)
                    }
                    sendMoveToPage()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    targetX.value = (targetX.value - step).coerceAtLeast(0f)
                    if (targetX.value < edge) {
                        webViewRef?.evaluateJavascript("window.scrollBy(-120, 0);", null)
                    }
                    sendMoveToPage()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    targetX.value = (targetX.value + step).coerceAtMost((containerWidthPx - 1).toFloat())
                    if (targetX.value > containerWidthPx - edge) {
                        webViewRef?.evaluateJavascript("window.scrollBy(120, 0);", null)
                    }
                    sendMoveToPage()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                    sendClickToPage()
                    return true
                }
                // Fullscreen (CSS-based) fallback
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    sendToggleFullscreen()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    webViewRef?.evaluateJavascript("(function(){" + helperJs + "; window._tvHelper&&window._tvHelper.playPause();})();", null)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    webViewRef?.evaluateJavascript("(function(){var v=document.querySelector('video'); if(v) v.play();})();", null)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> {
                    webViewRef?.evaluateJavascript("(function(){var v=document.querySelector('video'); if(v) v.pause();})();", null)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    webViewRef?.evaluateJavascript("(function(){var v=document.querySelector('video'); if(v){try{v.currentTime = Math.min(v.duration||1e9, v.currentTime + 10);}catch(e){}}})();", null)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    webViewRef?.evaluateJavascript("(function(){var v=document.querySelector('video'); if(v){try{v.currentTime = Math.max(0, v.currentTime - 10);}catch(e){}}})();", null)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun sendMoveToPage() {
        val x = targetX.value
        val y = targetY.value
        val js = """
            (function(){
              $helperJs
              var dpr = window.devicePixelRatio||1;
              var x = ${x};
              var y = ${y};
              window._tvHelper && window._tvHelper.move(x/dpr, y/dpr);
            })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(js, null)
    }

    private fun sendClickToPage() {
        val x = targetX.value
        val y = targetY.value
        val js = """
            (function(){
              $helperJs
              var dpr = window.devicePixelRatio||1;
              var x = ${x};
              var y = ${y};
              window._tvHelper && window._tvHelper.click(x/dpr, y/dpr);
            })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(js, null)
    }

    private fun sendToggleFullscreen() {
        val js = """
            (function(){
               $helperJs
               window._tvHelper && window._tvHelper.toggleFS();
            })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(js, null)
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
        enterPipIfPossible()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }
}
