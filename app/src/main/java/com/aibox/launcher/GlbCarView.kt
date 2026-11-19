package com.aibox.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.File

/**
 * 載入 3D 車輛與痛車塗裝的高效能 3D 互動旋轉檢視器 (WebView + Three.js / WebGL)
 * 支援 360 度手動與巡航旋轉、特斯拉彩虹大道、GPS 移動/轉彎角度連動與視角平滑過渡。
 */
class GlbCarView(context: Context) : FrameLayout(context) {
    val webView = WebView(context)

    inner class AndroidBridge {
        @JavascriptInterface
        fun pickDecalImage() {
            (context as? HomeActivity)?.launchDecalImagePicker()
        }

        @JavascriptInterface
        fun openApps() {
            (context as? HomeActivity)?.runOnUiThread {
                context.startActivity(Intent(context, AppsActivity::class.java))
            }
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(LAYER_TYPE_HARDWARE, null)
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (url.contains("custom_car.glb")) {
                    val customFile = File(context.filesDir, "custom_car.glb")
                    if (customFile.exists()) {
                        return WebResourceResponse("model/gltf-binary", "UTF-8", customFile.inputStream())
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        @Suppress("DEPRECATION")
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val rainbowOn = Store.rainbowRoadOn(context)
        val customGlb = File(context.filesDir, "custom_car.glb")
        if (customGlb.exists()) {
            webView.loadUrl("file:///android_asset/car_viewer.html?model=custom_car.glb&rainbow=$rainbowOn")
        } else {
            webView.loadUrl("file:///android_asset/car_viewer.html?model=carnew.glb&rainbow=$rainbowOn")
        }
    }

    fun setGpsData(speedKmh: Float, bearingDeg: Float) {
        webView.post {
            webView.evaluateJavascript("setGpsData($speedKmh, $bearingDeg)", null)
        }
    }

    fun setGpsSpeed(speedKmh: Float) {
        webView.post {
            webView.evaluateJavascript("setGpsSpeed($speedKmh)", null)
        }
    }

    fun loadDecalDataUrl(dataUrl: String) {
        webView.post {
            webView.evaluateJavascript("setDecalDataUrl('$dataUrl')", null)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        return super.onInterceptTouchEvent(ev)
    }
}
