package com.example.netshortdownloader

import android.content.Context
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
    
    // Khởi tạo bộ nhớ cục bộ (SharedPreferences) để lưu dữ liệu
    val sharedPrefs = remember { context.getSharedPreferences("NetShortPrefs", Context.MODE_PRIVATE) }

    // Các biến trạng thái của giao diện
    var urlInput by remember { mutableStateOf("") }
    var showLogin by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }
    
    // Tự động load Cookie đã lưu từ lần mở app trước đó
    var vipCookie by remember { mutableStateOf(sharedPrefs.getString("VIP_COOKIE", "") ?: "") }
    
    // Các biến trạng thái cho thanh tiến trình tải xuống
    var isDownloading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var progressPercent by remember { mutableStateOf(0) }

    // Điều hướng giao diện
    if (showLogin) {
        LoginScreen(
            onBack = { showLogin = false },
            onCookieCaptured = { cookie ->
                vipCookie = cookie
                // LƯU COOKIE VÀO BỘ NHỚ KHI ĐĂNG NHẬP THÀNH CÔNG
                sharedPrefs.edit().putString("VIP_COOKIE", cookie).apply()
                showLogin = false
                Toast.makeText(context, "Đã lưu phiên đăng nhập!", Toast.LENGTH_SHORT).show()
            }
        )
    } else if (showBrowser) {
        // Mở trình duyệt tích hợp Radar chộp link
        BrowserScreen(
            initialUrl = urlInput,
            onBack = { showBrowser = false },
            onLinkCaught = { caughtUrl -> 
                // Khi Radar tóm được link video, tự động điền vào ô nhập và đóng trình duyệt
                urlInput = caughtUrl
                showBrowser = false
            }
        )
    } else {
        // Giao diện chính
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    actions = {
                        IconButton(onClick = { 
                            // Nếu đã có Cookie, hỏi người dùng có muốn đăng xuất không (nhấn đè để đăng xuất, nhấn thường để đăng nhập lại)
                            showLogin = true 
                        }) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "VIP Login",
                                tint = if (vipCookie.isNotEmpty()) Color(0xFF4CAF50) else Color.Gray
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
                // Hiển thị trạng thái tài khoản VIP
                if (vipCookie.isNotEmpty()) {
                    Text(
                        text = "Đã kết nối tài khoản VIP! Sẵn sàng tải phim.", 
                        color = Color(0xFF4CAF50), 
                        fontSize = 14.sp, 
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Ô nhập URL
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Dán link phim hoặc m3u8 vào đây...") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Link, contentDescription = "Link Icon") },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    enabled = !isDownloading
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Nút tải phim chính
                Button(
                    onClick = {
                        if (urlInput.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập link phim!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isDownloading = true
                        statusText = "Đang khởi tạo..."
                        progressPercent = 0

                        // Gọi hàm tải xuống ngầm
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
                    Text(text = if (isDownloading) "Đang xử lý..." else "Tải Phim Ngay", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Nút mở trình duyệt bắt link tự động (Radar)
                OutlinedButton(
                    onClick = { showBrowser = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                ) {
                    Icon(imageVector = Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Mở Trình Duyệt Bắt Link Tự Động", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                // Hiển thị thanh tiến trình khi đang tải
                if (isDownloading || progressPercent > 0) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = statusText, fontSize = 14.sp, color = Color.LightGray)
                            Text(text = "$progressPercent%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = progressPercent / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF4CAF50),
                            trackColor = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}
