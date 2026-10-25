package com.example.netshortdownloader

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var showLogin by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }
    var vipCookie by remember { mutableStateOf("") }
    
    var isDownloading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var progressPercent by remember { mutableStateOf(0) }

    if (showLogin) {
        LoginScreen(
            onBack = { showLogin = false },
            onCookieCaptured = { cookie ->
                vipCookie = cookie
                showLogin = false
            }
        )
    } else if (showBrowser) {
        BrowserScreen(
            initialUrl = urlInput,
            onBack = { showBrowser = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    actions = {
                        IconButton(onClick = { showLogin = true }) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "VIP Login",
                                tint = if (vipCookie.isNotEmpty()) Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                    }
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
                if (vipCookie.isNotEmpty()) {
                    Text("Đã kết nối tài khoản VIP!", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(id = R.string.url_hint)) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Link, contentDescription = "Link Icon") },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    enabled = !isDownloading
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        if (urlInput.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập link phim!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isDownloading = true
                        statusText = "Đang khởi tạo..."
                        progressPercent = 0

                        coroutineScope.launch {
                            downloadHelper.extractAndDownload(urlInput, vipCookie) { status, percent ->
                                statusText = status
                                progressPercent = percent
                                if (percent >= 100) isDownloading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)),
                    enabled = !isDownloading
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isDownloading) "Đang xử lý..." else stringResource(id = R.string.btn_download), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Nút mở trình duyệt bắt link
                OutlinedButton(
                    onClick = { showBrowser = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                ) {
                    Icon(imageVector = Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Mở Trình Duyệt Bắt Link (F12)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                if (isDownloading || progressPercent > 0) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = statusText, fontSize = 14.sp, color = Color.Gray)
                            Text(text = "$progressPercent%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = progressPercent / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF4CAF50),
                            trackColor = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}
