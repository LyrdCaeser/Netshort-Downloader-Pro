package com.example.netshortdownloader

import android.annotation.SuppressLint
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    initialUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentUrl by remember { mutableStateOf(if (initialUrl.isNotEmpty()) initialUrl else "https://google.com") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Trình duyệt (F12)", fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Đóng") }
                    },
                    actions = {
                        // Nút Home (Trang chủ) - Cứu cánh khi bị kẹt trang trắng
                        IconButton(onClick = { webViewRef?.loadUrl("https://google.com") }) {
                            Icon(Icons.Default.Home, contentDescription = "Trang chủ")
                        }
                        IconButton(onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Lùi lại")
                        }
                        IconButton(onClick = { if (webViewRef?.canGoForward() == true) webViewRef?.goForward() }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Tiến tới")
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Tải lại")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                
                OutlinedTextField(
                    value = currentUrl,
                    onValueChange = { currentUrl = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    placeholder = { Text("Nhập link web...") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            var loadUrl = currentUrl
                            if (!loadUrl.startsWith("http://") && !loadUrl.startsWith("https://")) {
                                loadUrl = "https://$loadUrl"
                                currentUrl = loadUrl
                            }
                            webViewRef?.loadUrl(loadUrl)
                        }
                    )
                )
            }
        }
    ) { paddingValues ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            factory = {
                WebView(context).apply {
                    webViewRef = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    
                    // Cấp phép cho WebView mở đa cửa sổ (Popup) y như Chrome
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    
                    val chromeUserAgent = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.193 Mobile Safari/537.36"
                    settings.userAgentString = chromeUserAgent
                    
                    webChromeClient = object : WebChromeClient() {
                        // Xử lý luồng mở Tab mới của Google Login
                        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                            val newWebView = WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.userAgentString = chromeUserAgent
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String) {
                                        // Nếu popup đi đến trang transform, tự động đóng nó lại và tải lại web chính
                                        if (url.contains("gsi/transform") || url.contains("close")) {
                                            webViewRef?.loadUrl("https://google.com")
                                        }
                                    }
                                }
                            }
                            val transport = resultMsg?.obj as WebView.WebViewTransport
                            transport.webView = newWebView
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            currentUrl = url 
                            
                            // Cơ chế tự động bẻ lái dự phòng
                            if (url.contains("gsi/transform")) {
                                loadUrl("https://google.com")
                                return
                            }
                            
                            val injectEruda = """
                                javascript:(function () { 
                                    if (window.eruda) return;
                                    var script = document.createElement('script'); 
                                    script.src = 'https://cdn.jsdelivr.net/npm/eruda'; 
                                    document.body.appendChild(script); 
                                    script.onload = function () { 
                                        eruda.init(); 
                                    } 
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(injectEruda, null)
                        }
                    }
                    
                    loadUrl(currentUrl)
                }
            },
            update = { webView ->
                webViewRef = webView
            }
        )
    }
}
