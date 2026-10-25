package com.example.netshortdownloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadHelper(private val context: Context) {
    private val client = OkHttpClient()

    // Hàm gọi mạng để bóc tách link gốc và tải
    suspend fun extractAndDownload(shareUrl: String, vipCookie: String) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Dùng OkHttp gọi tới link Share kèm Cookie VIP để lấy source HTML/JSON
                val request = Request.Builder()
                    .url(shareUrl)
                    .addHeader("Cookie", vipCookie) // Bơm VIP Cookie vào đây
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                // 2. Bóc tách link video gốc (.mp4 hoặc .m3u8) từ source code
                val realVideoUrl = parseVideoUrl(responseBody)
                
                // Nếu tìm thấy link MP4, hoặc nếu shareUrl đã là link trực tiếp
                val finalUrl = realVideoUrl.ifEmpty { shareUrl }

                // 3. Bơm link gốc và VIP Cookie vào trình tải xuống của Android (DownloadManager)
                withContext(Dispatchers.Main) {
                    executeAndroidDownload(finalUrl, vipCookie, "Netshort_VIP_Video_${System.currentTimeMillis()}.mp4")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi bóc tách: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseVideoUrl(sourceCode: String): String {
        // Sử dụng chuỗi 3 dấu nháy kép """ để tránh lỗi cú pháp trong Kotlin
        val regex = """(https?://[^\s"']+?\.mp4)""".toRegex()
        val match = regex.find(sourceCode)
        if (match != null) return match.value

        return ""
    }

    private fun executeAndroidDownload(url: String, cookie: String, fileName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Đang tải Netshort VIP")
                .setDescription("Đang tải phim về máy...")
                // QUAN TRỌNG: Gắn VIP Cookie vào request tải để Server Netshort nhả file chất lượng cao
                .addRequestHeader("Cookie", cookie) 
                .addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            
            Toast.makeText(context, "Bắt đầu tải xuống. Kiểm tra thanh thông báo!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi DownloadManager: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
