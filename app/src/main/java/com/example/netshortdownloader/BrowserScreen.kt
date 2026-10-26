package com.example.netshortdownloader

import android.annotation.SuppressLint
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Trình duyệt (F12)", fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Đóng") }
                    },
                    actions = {
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            factory = {
                WebView(context).apply {
                    webViewRef = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    
                    // Thiết lập Cookie toàn cục
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    
                    val chromeUserAgent = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.193 Mobile Safari/537.36"
                    settings.userAgentString = chromeUserAgent
                    
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                            val newWebView = WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = chromeUserAgent
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                                
                                webChromeClient = object : WebChromeClient() {
                                    override fun onCloseWindow(window: WebView?) {
                                        // ÉP LƯU COOKIE NGAY LẬP TỨC TRƯỚC KHI ĐÓNG POPUP
                                        cookieManager.flush() 
                                        popupWebView = null 
                                        webViewRef?.reload() // Tải lại web chính
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        return false
                                    }
                                    override fun onPageFinished(view: WebView, url: String) {
                                        // Đóng popup nếu hoàn thành chu trình login
                                        if (url.contains("gsi/transform") || url.contains("close") || url.contains("google.com/search")) {
                                            // ÉP LƯU COOKIE 
                                            cookieManager.flush()
                                            popupWebView = null
                                            webViewRef?.reload()
                                        }
                                    }
                                }
                            }
                            popupWebView = newWebView
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
        
        if (popupWebView != null) {
            Dialog(
                onDismissRequest = { 
                    CookieManager.getInstance().flush() // Ép lưu Cookie nếu người dùng chủ động ấn ra ngoài để đóng
                    popupWebView = null 
                    webViewRef?.reload()
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(), 
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopAppBar(
                            title = { Text("Đăng nhập an toàn", fontSize = 16.sp) },
                            navigationIcon = {
                                IconButton(onClick = { 
                                    CookieManager.getInstance().flush()
                                    popupWebView = null
                                    webViewRef?.reload()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Đóng")
                                }
                            }
                        )
                        AndroidView(
                            factory = { popupWebView!! },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
