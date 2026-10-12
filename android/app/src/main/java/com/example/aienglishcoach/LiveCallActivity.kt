package com.example.aienglishcoach

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

class LiveCallActivity : ComponentActivity() {
    private var webView: WebView? = null
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCall() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) openCall()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun openCall() {
        val backend = intent.getStringExtra(EXTRA_BACKEND_URL)?.trimEnd('/')
        if (backend.isNullOrBlank() || !backend.startsWith("https://")) { finish(); return }
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        val audio = request.resources.filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }.toTypedArray()
                        if (audio.isNotEmpty()) request.grant(audio) else request.deny()
                    }
                }
            }
            loadUrl("https://appassets.androidplatform.net/assets/realtime.html?backend=${Uri.encode(backend)}")
        }
        setContentView(webView)
    }

    override fun onDestroy() {
        webView?.apply { loadUrl("about:blank"); stopLoading(); destroy() }
        webView = null
        super.onDestroy()
    }

    companion object { const val EXTRA_BACKEND_URL = "backend_url" }
}
