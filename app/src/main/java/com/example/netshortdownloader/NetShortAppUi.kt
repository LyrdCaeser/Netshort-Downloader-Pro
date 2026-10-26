package com.example.netshortdownloader

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetShortDownloaderScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadHelper = remember { DownloadHelper(context) }

    var urlInput by remember { mutableStateOf("") }
    var refererUrl by remember { mutableStateOf("") }
    var selectedMediaUrl by remember { mutableStateOf("") }
    var downloadedMediaUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showLogin by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }
    var sessionReady by remember { mutableStateOf(hasNetShortSession()) }
    var isDownloading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var progressPercent by remember { mutableStateOf(0) }

    fun startDownload(mediaUrl: String, pageUrl: String, fromBrowser: Boolean) {
        if (isDownloading || mediaUrl.isBlank()) return
        val mediaUrlToDownload = mediaUrl.trim()
        isDownloading = true
        statusText = "Đang khởi tạo…"
        progressPercent = 0
        coroutineScope.launch {
            try {
                val saved = downloadHelper.downloadDirectVideo(mediaUrlToDownload, pageUrl) { status, percent ->
                    statusText = status
                    progressPercent = percent
                }
                if (saved) {
                    downloadedMediaUrls = downloadedMediaUrls + mediaUrlToDownload
                    urlInput = ""
                    selectedMediaUrl = ""
                    if (fromBrowser) {
                        refererUrl = pageUrl
                        statusText = "Đã lưu video. Trang NetShort vẫn mở; hãy bấm chọn tập tiếp theo trên trang. Link tập vừa tải đã được bỏ khỏi danh sách."
                        progressPercent = 0
                    } else {
                        refererUrl = ""
                    }
                }
            } finally {
                isDownloading = false
            }
        }
    }

    if (showLogin) {
        LoginScreen(
            onBack = {
                sessionReady = hasNetShortSession()
                showLogin = false
            },
            onLoginComplete = {
                sessionReady = true
                showLogin = false
                Toast.makeText(context, "Đã nhận phiên NetShort từ Android WebView.", Toast.LENGTH_SHORT).show()
            }
        )
    } else if (showBrowser) {
        BrowserScreen(
            initialUrl = urlInput,
            excludedMediaUrls = downloadedMediaUrls,
            selectedMediaUrl = selectedMediaUrl,
            isDownloading = isDownloading,
            downloadStatus = statusText,
            downloadPercent = progressPercent,
            onBack = {
                if (!isDownloading) {
                    showBrowser = false
                    selectedMediaUrl = ""
                }
            },
            onLinkCaught = { caughtUrl, pageUrl ->
                urlInput = caughtUrl
                selectedMediaUrl = caughtUrl
                refererUrl = pageUrl
                statusText = ""
                progressPercent = 0
                Toast.makeText(context, "Đã chọn link. Nhấn Tải & chuyển tập để tải video.", Toast.LENGTH_SHORT).show()
            },
            onDownloadSelected = { startDownload(selectedMediaUrl, refererUrl, fromBrowser = true) }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { showLogin = true }) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Đăng nhập NetShort",
                                tint = if (sessionReady) Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (sessionReady) "Đã phát hiện hoặc xác nhận cookie NetShort trong WebView." else "Đăng nhập NetShort để mở nội dung bạn được phép xem.",
                    color = if (sessionReady) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        refererUrl = ""
                        selectedMediaUrl = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Dán link video trực tiếp đã được cấp quyền…") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Link, contentDescription = "Link video") },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    enabled = !isDownloading
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (urlInput.isBlank()) {
                            Toast.makeText(context, "Hãy nhập link video trực tiếp hoặc bắt link trong môi trường NetShort.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        startDownload(urlInput, refererUrl, fromBrowser = false)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)),
                    enabled = !isDownloading
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (isDownloading) "Đang tải…" else "Tải video trực tiếp", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { showBrowser = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)),
                    enabled = !isDownloading
                ) {
                    Icon(imageVector = Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Mở môi trường NetShort cô lập", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Bộ quét nhận diện link MP4/WebM và playlist HLS từ HTML5 video, Fetch/XHR và tài nguyên WebView. Chỉ tải file video trực tiếp; không giải mã DRM hoặc ghép playlist HLS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (statusText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(26.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(statusText, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Text("$progressPercent%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = progressPercent / 100f,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF4CAF50),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}
