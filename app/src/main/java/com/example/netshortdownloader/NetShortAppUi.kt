package com.example.netshortdownloader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetShortDownloaderScreen() {
    var urlInput by remember { mutableStateOf("") }
    var showLogin by remember { mutableStateOf(false) }
    var vipCookie by remember { mutableStateOf("") }
    
    if (showLogin) {
        LoginScreen(
            onBack = { showLogin = false },
            onCookieCaptured = { cookie ->
                vipCookie = cookie
                showLogin = false
            }
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
                                contentDescription = "VIP Login",
                                tint = if (vipCookie.isNotEmpty()) Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                if (vipCookie.isNotEmpty()) {
                    Text(
                        text = "Đã lấy Cookie VIP thành công! Sẵn sàng tải phim.",
                        color = Color(0xFF4CAF50),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(id = R.string.url_hint)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Link, contentDescription = "Link Icon")
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        // TODO: Gọi logic tải xuống (yt-dlp/OkHttp) kèm header Cookie = vipCookie
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download, 
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.btn_download),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
