package com.example.netshortdownloader

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginComplete: () -> Unit
) {
    val context = LocalContext.current
    var sessionDetected by remember { mutableStateOf(hasNetShortSession()) }
    var hasCookies by remember { mutableStateOf(hasNetShortCookies()) }
    var pageStatus by remember { mutableStateOf("Đang mở trang đăng nhập NetShort…") }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đăng nhập NetShort") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            CookieManager.getInstance().flush()
                            sessionDetected = hasNetShortSession()
                            hasCookies = hasNetShortCookies()
                            if (sessionDetected || hasCookies) onLoginComplete()
                        },
                        enabled = sessionDetected || hasCookies
                    ) {
                        Text("Hoàn tất")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Text(
                text = when {
                    sessionDetected -> "Đã phát hiện cookie phiên. Nhấn Hoàn tất để tiếp tục."
                    hasCookies -> "Không nhận diện được tên cookie đăng nhập. Nếu vừa đăng nhập thành công, bạn có thể xác nhận thủ công."
                    else -> pageStatus
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Phiên đăng nhập được giữ trong cookie jar của Android WebView; ứng dụng không xuất hoặc ghi cookie ra log. Nhà cung cấp có thể chặn đăng nhập nhúng, khi đó hãy dùng phương thức đăng nhập NetShort được hỗ trợ.",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            settings.safeBrowsingEnabled = true
                        }

                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        cookies.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val message = resultMsg ?: return false
                                val popup = WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        settings.safeBrowsingEnabled = true
                                    }
                                    cookies.setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            return request?.url?.scheme?.lowercase() != "https"
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
                                            if (host == "netshort.com" || host?.endsWith(".netshort.com") == true) {
                                                cookies.flush()
                                                sessionDetected = hasNetShortSession()
                                                hasCookies = hasNetShortCookies()
                                            }
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onCloseWindow(window: WebView?) {
                                            cookies.flush()
                                            popupWebView = null
                                            window?.let { closingView -> closingView.post { closingView.destroy() } }
                                        }
                                    }
                                }
                                val transport = message.obj as? WebView.WebViewTransport ?: return false
                                transport.webView = popup
                                message.sendToTarget()
                                popupWebView = popup
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val scheme = request?.url?.scheme?.lowercase()
                                return scheme != "https"
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
                                if (host == "netshort.com" || host?.endsWith(".netshort.com") == true) {
                                    cookies.flush()
                                    sessionDetected = hasNetShortSession()
                                    hasCookies = hasNetShortCookies()
                                    pageStatus = if (sessionDetected) {
                                        "Đã phát hiện cookie có tên giống cookie phiên. Nhấn Hoàn tất để tiếp tục."
                                    } else {
                                        "Hãy đăng nhập trên trang NetShort. Ứng dụng chỉ xác nhận phiên khi cookie đăng nhập xuất hiện."
                                    }
                                }
                            }
                        }
                        loadUrl("https://www.netshort.com/")
                    }
                }
            )
            popupWebView?.let { popup ->
                Dialog(
                    onDismissRequest = {
                        CookieManager.getInstance().flush()
                        popupWebView = null
                        popup.stopLoading()
                        popup.destroy()
                    },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AndroidView(factory = { popup }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** Checks cookie names only; the cookie values remain inside Android's WebView cookie store. */
fun hasNetShortSession(): Boolean {
    val cookies = runCatching {
        CookieManager.getInstance().getCookie("https://www.netshort.com").orEmpty()
    }.getOrDefault("")
    return cookies.split(';').any { entry ->
        val name = entry.substringBefore('=').trim().lowercase()
        val looksLikeSession = name.matches(Regex(".*(session|sid|token|auth|jwt|login|user|uid).*"))
        val isAntiCsrf = name.contains("csrf") || name.contains("xsrf")
        looksLikeSession && !isAntiCsrf
    }
}

/** Lets the user confirm an opaque session cookie without exposing its value to the app UI. */
private fun hasNetShortCookies(): Boolean {
    val cookies = runCatching {
        CookieManager.getInstance().getCookie("https://www.netshort.com").orEmpty()
    }.getOrDefault("")
    return cookies.split(';').any { it.substringBefore('=').trim().isNotEmpty() }
}
