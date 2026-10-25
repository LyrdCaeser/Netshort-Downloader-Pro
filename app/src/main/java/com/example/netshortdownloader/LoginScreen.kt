package com.example.netshortdownloader

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onCookieCaptured: (String) -> Unit
) {
    val context = LocalContext.current
    val loginUrl = "https://www.netshort.com/login"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đăng nhập tài khoản VIP") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    
                    // Giả mạo Chrome thật để bypass Google Login
                    val defaultAgent = settings.userAgentString
                    settings.userAgentString = defaultAgent.replace("; wv", "")
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url != null && url.contains("netshort.com")) {
                                val cookies = CookieManager.getInstance().getCookie(url)
                                println("NETSHORT_COOKIES: $cookies")
                                
                                if (cookies != null && cookies.length > 20) {
                                    onCookieCaptured(cookies)
                                }
                            }
                        }
                    }
                    loadUrl(loginUrl)
                }
            }
        )
    }
}
