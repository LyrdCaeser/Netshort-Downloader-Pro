package com.example.netshortdownloader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private data class MediaLinkCandidate(
    val url: String,
    val mimeType: String,
    val detectionSource: String,
    val pageTitle: String
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    initialUrl: String,
    excludedMediaUrls: Set<String> = emptySet(),
    selectedMediaUrl: String = "",
    isDownloading: Boolean = false,
    downloadStatus: String = "",
    downloadPercent: Int = 0,
    onBack: () -> Unit,
    onLinkCaught: (String, String) -> Unit,
    onDownloadSelected: () -> Unit = {}
) {
    val context = LocalContext.current
    val defaultUrl = remember(initialUrl) { normalizeNetShortUrl(initialUrl) ?: "https://www.netshort.com/" }
    var currentUrl by remember { mutableStateOf(defaultUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val candidates = remember { mutableStateListOf<MediaLinkCandidate>() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val trustedNetShortDocument = remember { AtomicBoolean(false) }
    val excludedMediaUrlsRef = remember { AtomicReference(excludedMediaUrls) }
    val observedPageUrl = remember { AtomicReference(defaultUrl) }
    var episodePopup by remember { mutableStateOf<WebView?>(null) }

    SideEffect { excludedMediaUrlsRef.set(excludedMediaUrls) }

    fun closeEpisodePopup() {
        episodePopup?.let { popup ->
            popup.stopLoading()
            popup.destroy()
        }
        episodePopup = null
    }

    fun addCandidate(url: String?, mimeType: String?, source: String?, title: String?) {
        if (!trustedNetShortDocument.get()) return
        val value = url?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return
        if (value in excludedMediaUrlsRef.get()) return
        val type = mimeType.orEmpty().substringBefore(';').trim().lowercase()
        val path = runCatching { Uri.parse(value).path.orEmpty().lowercase() }.getOrDefault("")
        val isMedia = path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm") ||
            path.endsWith(".mov") || path.endsWith(".m3u8") || type.startsWith("video/") ||
            type.contains("mpegurl") || type.contains("vnd.apple.mpegurl")
        if (!isMedia) return
        mainHandler.post {
            if (candidates.none { it.url == value }) {
                candidates.add(
                    0,
                    MediaLinkCandidate(
                        url = value,
                        mimeType = type.ifBlank { "Không rõ định dạng" },
                        detectionSource = source?.takeIf { it.isNotBlank() } ?: "WebView",
                        pageTitle = title.orEmpty()
                    )
                )
            }
        }
    }

    fun openNetShortUrl(rawUrl: String) {
        val normalized = normalizeNetShortUrl(rawUrl)
        if (normalized == null) {
            Toast.makeText(context, "Chỉ được mở NetShort trong môi trường cô lập này.", Toast.LENGTH_LONG).show()
            return
        }
        currentUrl = normalized
        webViewRef?.loadUrl(normalized)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Môi trường NetShort cô lập", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("WebView Android · chỉ NetShort và đăng nhập", fontSize = 11.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, enabled = !isDownloading) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng")
                        }
                    },
                    actions = {
                        IconButton(onClick = { openNetShortUrl("https://www.netshort.com/") }) {
                            Icon(Icons.Default.Home, contentDescription = "NetShort")
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    singleLine = true,
                    placeholder = { Text("Dán link NetShort...") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { openNetShortUrl(currentUrl) })
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        WebView(context).apply {
                        webViewRef = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        // NetShort dùng một số liên kết target="_blank"/window.open
                        // cho nút "Toàn tập". Cho phép WebView tạo cửa sổ tạm để
                        // hiển thị bộ chọn tập mà không thay thế player chính.
                        settings.setSupportMultipleWindows(true)
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            settings.safeBrowsingEnabled = true
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        val mediaBridge = object {
                            @JavascriptInterface
                            fun onMediaFound(url: String, mimeType: String, source: String, title: String) {
                                if (trustedNetShortDocument.get()) addCandidate(url, mimeType, source, title)
                            }

                            @JavascriptInterface
                            fun onPageChanged(url: String) {
                                if (!trustedNetShortDocument.get()) return
                                val normalized = normalizeNetShortUrl(url) ?: return
                                if (observedPageUrl.getAndSet(normalized) != normalized) {
                                    mainHandler.post {
                                        currentUrl = normalized
                                        candidates.clear()
                                    }
                                }
                            }
                        }
                        addJavascriptInterface(mediaBridge, "NetShortMediaCapture")

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message
                            ): Boolean {
                                val popup = WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.javaScriptCanOpenWindowsAutomatically = true
                                    settings.setSupportMultipleWindows(false)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webChromeClient = WebChromeClient()
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(
                                            popupView: WebView,
                                            url: String,
                                            favicon: android.graphics.Bitmap?
                                        ) {
                                            // Keep the episode selector separate from the player.
                                            if (url != "about:blank" && !isAllowedNetShortNavigation(url)) {
                                                popupView.stopLoading()
                                            }
                                            super.onPageStarted(popupView, url, favicon)
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            popupView: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val url = request?.url?.toString() ?: return true
                                            if (openExternalNavigation(context, url)) return true
                                            return !isAllowedNetShortNavigation(url)
                                        }
                                    }
                                }

                                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                transport.webView = popup
                                resultMsg.sendToTarget()
                                mainHandler.post { episodePopup = popup }
                                return true
                            }

                            override fun onCloseWindow(window: WebView) {
                                if (window === episodePopup) closeEpisodePopup()
                                else super.onCloseWindow(window)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                val isTrusted = isNetShortHost(Uri.parse(url).host)
                                trustedNetShortDocument.set(isTrusted)
                                if (isTrusted) {
                                    view.addJavascriptInterface(mediaBridge, "NetShortMediaCapture")
                                } else {
                                    view.removeJavascriptInterface("NetShortMediaCapture")
                                }
                                super.onPageStarted(view, url, favicon)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return true
                                if (openExternalNavigation(context, url)) return true
                                return !isAllowedNetShortNavigation(url)
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                val normalized = normalizeNetShortUrl(url)
                                if (normalized != null) {
                                    if (observedPageUrl.getAndSet(normalized) != normalized) candidates.clear()
                                    currentUrl = normalized
                                    installMediaCapture(view, view.title.orEmpty())
                                }
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                super.onLoadResource(view, url)
                                addCandidate(url, null, "tài nguyên trang", null)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                val uri = request?.url ?: return null
                                addCandidate(uri.toString(), null, "yêu cầu mạng", null)
                                return null
                            }
                        }
                        setDownloadListener { url, _, mimeType, _, _ ->
                            addCandidate(url, mimeType, "liên kết tải của WebView", webViewRef?.title)
                        }
                        loadUrl(defaultUrl)
                        }
                    },
                    update = { webView -> webViewRef = webView }
                )

                episodePopup?.let { popup ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { popup },
                        update = { }
                    )
                    IconButton(
                        onClick = ::closeEpisodePopup,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng danh sách tập")
                    }
                }
            }

            if (downloadStatus.isNotBlank() && selectedMediaUrl.isBlank()) {
                Text(
                    text = downloadStatus,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (candidates.isNotEmpty()) {
                Text(
                    text = "Link video phát hiện được (${candidates.size})",
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp),
                    style = MaterialTheme.typography.titleSmall
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp).padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(candidates, key = { it.url }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(item.mimeType, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    item.url,
                                    maxLines = 2,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (item.pageTitle.isNotBlank()) {
                                    Text(item.pageTitle, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Text(item.detectionSource, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                    Button(onClick = { onLinkCaught(item.url, currentUrl) }, enabled = !isDownloading) {
                                        Text("Dùng link")
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                }
            } else {
                Text(
                    text = "Mở video trong NetShort để quét link trực tiếp. Nội dung DRM hoặc link không được cấp quyền sẽ không được giải mã.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (selectedMediaUrl.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Sẵn sàng tải tập này", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(selectedMediaUrl, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        if (downloadStatus.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(downloadStatus, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        }
                        if (isDownloading) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = downloadPercent / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = onDownloadSelected, enabled = !isDownloading) {
                                Text(if (isDownloading) "Đang tải…" else "Tải video")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun normalizeNetShortUrl(rawValue: String): String? {
    val value = rawValue.trim().let {
        if (it.isBlank()) "https://www.netshort.com/"
        else if (it.startsWith("https://", true)) it
        else if (it.startsWith("http://", true)) return null
        else "https://$it"
    }
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    if (!isNetShortHost(uri.host) || uri.userInfo != null) return null
    return uri.toString()
}

private fun isNetShortHost(host: String?): Boolean {
    val normalized = host?.lowercase()?.trimEnd('.') ?: return false
    return normalized == "netshort.com" || normalized.endsWith(".netshort.com")
}

private fun isAllowedNetShortNavigation(url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    if (uri.scheme != "https" || uri.userInfo != null) return false
    val host = uri.host?.lowercase() ?: return false
    if (isNetShortHost(host)) return true
    return host in setOf(
        "accounts.google.com",
        "www.google.com",
        "appleid.apple.com",
        "www.facebook.com",
        "m.facebook.com"
    )
}

/**
 * Lets Android handle app links and intent:// URLs just as a normal browser does.
 * HTTP(S) navigation remains governed by the WebView allowlist and returns false.
 */
private fun openExternalNavigation(context: Context, rawUrl: String): Boolean {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return true
    val scheme = uri.scheme?.lowercase().orEmpty()
    if (scheme == "http" || scheme == "https" || scheme.isBlank()) return false

    val intent = runCatching {
        if (scheme == "intent") {
            Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
    }.getOrNull() ?: run {
        Toast.makeText(context, "Liên kết ứng dụng không hợp lệ.", Toast.LENGTH_SHORT).show()
        return true
    }

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        val fallback = intent.getStringExtra("browser_fallback_url")
        if (!fallback.isNullOrBlank()) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(fallback)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                Toast.makeText(context, "Không tìm thấy ứng dụng để mở liên kết tập.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Cần mở liên kết này bằng ứng dụng NetShort.", Toast.LENGTH_LONG).show()
        }
        true
    }
}

private fun installMediaCapture(view: WebView, title: String) {
    val safeTitle = JSONObject.quote(title)
    val script = """
        (function() {
          if (window.__netShortMediaCaptureInstalled) return;
          window.__netShortMediaCaptureInstalled = true;
          var pageTitle = $safeTitle;
          function likelyMedia(url, mime) {
            if (!url) return false;
            var path = String(url).split('?')[0].split('#')[0].toLowerCase();
            var type = String(mime || '').toLowerCase();
            return /\.(mp4|m4v|webm|mov|m3u8)$/.test(path) || type.indexOf('video/') === 0 || type.indexOf('mpegurl') >= 0;
          }
          function send(url, mime, source) {
            if (likelyMedia(url, mime) && window.NetShortMediaCapture) {
              window.NetShortMediaCapture.onMediaFound(String(url), String(mime || ''), String(source || 'trình duyệt'), pageTitle);
            }
          }
          function scanDom() {
            if (window.NetShortMediaCapture && window.NetShortMediaCapture.onPageChanged) {
              window.NetShortMediaCapture.onPageChanged(location.href);
            }
            document.querySelectorAll('video').forEach(function(video) {
              send(video.currentSrc || video.src, 'video/*', 'HTML5 video');
              video.querySelectorAll('source').forEach(function(source) { send(source.src, source.type || 'video/*', 'HTML5 source'); });
            });
            document.querySelectorAll('source[src]').forEach(function(source) { send(source.src, source.type || '', 'DOM source'); });
          }
          var originalFetch = window.fetch;
          if (originalFetch) {
            window.fetch = function() {
              return originalFetch.apply(this, arguments).then(function(response) {
                var responseUrl = response.url || (arguments[0] && arguments[0].url) || '';
                var type = response.headers ? response.headers.get('content-type') || '' : '';
                send(responseUrl, type, 'Fetch / MIME');
                if (/json|text/i.test(type)) {
                  response.clone().text().then(function(body) {
                    var normalized = body.replace(/\\\//g, '/').replace(/\\u0026/gi, '&');
                    var matches = normalized.match(/https?:\/\/[^\s"'<>\\]+/gi) || [];
                    matches.forEach(function(candidate) {
                      var cleaned = candidate.replace(/[),;]+$/, '');
                      send(cleaned, '', 'Fetch response');
                    });
                  }).catch(function() {});
                }
                return response;
              });
            };
          }
          var originalOpen = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function() {
            this.addEventListener('load', function() {
              var type = '';
              try { type = this.getResponseHeader('content-type') || ''; } catch (e) {}
              send(this.responseURL || '', type, 'XHR / MIME');
              if (/json|text/i.test(type)) {
                try {
                  var normalized = this.responseText.replace(/\\\//g, '/').replace(/\\u0026/gi, '&');
                  var matches = normalized.match(/https?:\/\/[^\s"'<>\\]+/gi) || [];
                  matches.forEach(function(candidate) {
                    var cleaned = candidate.replace(/[),;]+$/, '');
                    send(cleaned, '', 'XHR response');
                  });
                } catch (e) {}
              }
            });
            return originalOpen.apply(this, arguments);
          };
          var observer = new MutationObserver(scanDom);
          if (document.documentElement) observer.observe(document.documentElement, {childList: true, subtree: true, attributes: true});
          document.addEventListener('loadedmetadata', scanDom, true);
          scanDom();
          setInterval(scanDom, 2500);
        })();
    """.trimIndent()
    view.evaluateJavascript(script, null)
}
