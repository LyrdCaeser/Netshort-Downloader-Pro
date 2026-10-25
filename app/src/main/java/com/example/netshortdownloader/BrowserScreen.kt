package com.example.netshortdownloader

import android.annotation.SuppressLint
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
    onBack: () -> Unit // Hàm đóng trình duyệt
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
                        // Nút thoát trình duyệt hoàn toàn (Dấu X)
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng")
                        }
                    },
                    actions = {
                        // Nút Lùi 1 trang web
                        IconButton(onClick = { 
                            if (webViewRef?.canGoBack() == true) webViewRef?.goBack() 
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Lùi lại")
                        }
                        // Nút Tiến 1 trang web
                        IconButton(onClick = { 
                            if (webViewRef?.canGoForward() == true) webViewRef?.goForward() 
                        }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Tiến tới")
                        }
                        // Nút Tải lại (Refresh)
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
                    
                    // BẬT COOKIE BÊN THỨ 3: Rất quan trọng để Google Login không bị lỗi
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    
                    // LỚP NGỤY TRANG CHROME: Giả mạo 100% là trình duyệt Chrome trên Samsung S23
                    val chromeUserAgent = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.193 Mobile Safari/537.36"
                    settings.userAgentString = chromeUserAgent
                    
                    webChromeClient = WebChromeClient()
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
    }
}
