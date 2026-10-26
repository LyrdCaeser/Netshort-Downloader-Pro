package com.example.netshortdownloader

import android.annotation.SuppressLint
import android.os.Message
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    initialUrl: String,
    onBack: () -> Unit,
    onLinkCaught: (String) -> Unit // Callback để trả link về màn hình chính
) {
    val context = LocalContext.current
    var currentUrl by remember { mutableStateOf(if (initialUrl.isNotEmpty()) initialUrl else "https://google.com") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Radar bắt link Tự Động", fontSize = 18.sp) },
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
                    placeholder = { Text("Nhập link phim...") },
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
                    
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    
                    val chromeUserAgent = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.193 Mobile Safari/537.36"
                    settings.userAgentString = chromeUserAgent

                    // Cầu nối giao tiếp giữa JavaScript (Web) và Kotlin (App)
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onVideoFound(url: String) {
                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(context, "BẮT ĐƯỢC LINK VIP!", Toast.LENGTH_SHORT).show()
                                onLinkCaught(url) // Trả link về giao diện tải
                            }
                        }
                    }, "AndroidSniffer")
                    
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                            val newWebView = WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = chromeUserAgent
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                                
                                webChromeClient = object : WebChromeClient() {
                                    override fun onCloseWindow(window: WebView?) {
                                        cookieManager.flush() 
                                        popupWebView = null 
                                        webViewRef?.reload() 
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean { return false }
                                    override fun onPageFinished(view: WebView, url: String) {
                                        if (url.contains("gsi/transform") || url.contains("close") || url.contains("google.com/search")) {
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
                            
                            // ĐOẠN SCRIPT RADAR: Cài cắm vào Fetch và XHR để nghe lén API lấy link
                            val injectSniffer = """
                                javascript:(function() {
                                    if (window.hasInjectedSniffer) return;
                                    window.hasInjectedSniffer = true;

                                    function sendToAndroid(videoUrl) {
                                        if(window.AndroidSniffer && videoUrl) {
                                            window.AndroidSniffer.onVideoFound(videoUrl);
                                        }
                                    }

                                    function extractLink(text) {
                                        const unescaped = text.replace(/\\/g, '');
                                        const match = unescaped.match(/(https?:\/\/[^"']+\.(?:mp4|m3u8)[^"']*)/i);
                                        if (match) sendToAndroid(match[1]);
                                    }

                                    // Nghe lén Fetch API
                                    const originalFetch = window.fetch;
                                    window.fetch = async function() {
                                        const response = await originalFetch.apply(this, arguments);
                                        const clone = response.clone();
                                        const reqUrl = response.url || '';
                                        
                                        if (reqUrl.includes('.m3u8') || reqUrl.includes('.mp4')) {
                                            sendToAndroid(reqUrl);
                                        } else if (reqUrl.includes('episode_info') || reqUrl.includes('play_url')) {
                                            clone.text().then(text => extractLink(text)).catch(e => {});
                                        }
                                        return response;
                                    };

                                    // Nghe lén XMLHttpRequest
                                    const originalXhrOpen = XMLHttpRequest.prototype.open;
                                    XMLHttpRequest.prototype.open = function(method, reqUrl) {
                                        this.addEventListener('load', function() {
                                            if (typeof reqUrl === 'string') {
                                                if (reqUrl.includes('.m3u8') || reqUrl.includes('.mp4')) {
                                                    sendToAndroid(reqUrl);
                                                } else if (reqUrl.includes('episode_info') || reqUrl.includes('play_url')) {
                                                    try { extractLink(this.responseText); } catch(e) {}
                                                }
                                            }
                                        });
                                        originalXhrOpen.apply(this, arguments);
                                    };
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(injectSniffer, null)
                        }
                    }
                    
                    loadUrl(currentUrl)
                }
            },
            update = { webView -> webViewRef = webView }
        )
        
        if (popupWebView != null) {
            Dialog(
                onDismissRequest = { 
                    CookieManager.getInstance().flush()
                    popupWebView = null 
                    webViewRef?.reload()
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopAppBar(
                            title = { Text("Đăng nhập an toàn", fontSize = 16.sp) },
                            navigationIcon = {
                                IconButton(onClick = { 
                                    CookieManager.getInstance().flush()
                                    popupWebView = null
                                    webViewRef?.reload()
                                }) { Icon(Icons.Default.Close, contentDescription = "Đóng") }
                            }
                        )
                        AndroidView(factory = { popupWebView!! }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
