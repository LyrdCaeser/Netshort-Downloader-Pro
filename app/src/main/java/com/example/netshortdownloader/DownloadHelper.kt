package com.example.netshortdownloader

import android.content.Context
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadHelper(private val context: Context) {
    private val client = OkHttpClient()

    suspend fun extractAndDownload(
        shareUrl: String, 
        vipCookie: String,
        onProgress: (status: String, percent: Int) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            onProgress("Đang bóc tách link phim...", 0)
        }

        withContext(Dispatchers.IO) {
            try {
                // 1. Gọi request kèm Cookie VIP để lấy mã nguồn trang web
                val request = Request.Builder()
                    .url(shareUrl)
                    .addHeader("Cookie", vipCookie)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                // 2. Bóc tách tất cả các link video tìm được trong trang
                val videoUrls = parseAllVideoUrls(responseBody)

                if (videoUrls.isEmpty()) {
                    // Nếu không tìm thấy link ẩn, tải trực tiếp link gốc
                    withContext(Dispatchers.Main) {
                        onProgress("Không tìm thấy link ẩn, đang tải link gốc...", 10)
                    }
                    downloadFile(shareUrl, vipCookie, "Netshort_Video_${System.currentTimeMillis()}.mp4", onProgress)
                } else {
                    // Hiện số file bóc tách được
                    val totalFiles = videoUrls.size
                    withContext(Dispatchers.Main) {
                        onProgress("Đã bóc tách thành công $totalFiles file video!", 5)
                    }

                    // Tiến hành tải từng file và cộng dồn tiến trình
                    for ((index, url) in videoUrls.withIndex()) {
                        val fileName = "Netshort_Ep_${index + 1}_${System.currentTimeMillis()}.mp4"
                        
                        downloadFile(url, vipCookie, fileName) { status, subPercent ->
                            // Tính phần trăm tổng thể dựa trên số file
                            val overallPercent = ((index * 100) + subPercent) / totalFiles
                            val fileInfo = "Đang tải file ${index + 1}/$totalFiles ($subPercent%)"
                            
                            kotlinx.coroutines.runBlocking(Dispatchers.Main) {
                                onProgress(fileInfo, overallPercent)
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onProgress("Tải hoàn tất tất cả $totalFiles file!", 100)
                        Toast.makeText(context, "Đã tải xong $totalFiles file về thư mục Download!", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onProgress("Lỗi: ${e.message}", 0)
                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun parseAllVideoUrls(sourceCode: String): List<String> {
        val urls = mutableSetOf<String>()
        // Quét tất cả các link .mp4 và .m3u8 có trong mã nguồn
        val regexMp4 = """(https?://[^\s"']+?\.mp4)""".toRegex()
        regexMp4.findAll(sourceCode).forEach { match ->
            urls.add(match.value)
        }
        val regexM3u8 = """(https?://[^\s"']+?\.m3u8)""".toRegex()
        regexM3u8.findAll(sourceCode).forEach { match ->
            urls.add(match.value)
        }
        return urls.toList()
    }

    private suspend fun downloadFile(
        url: String, 
        cookie: String, 
        fileName: String,
        onProgress: (status: String, percent: Int) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", cookie)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body ?: throw Exception("Phản hồi trống từ server")
        val contentLength = body.contentLength()

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val file = File(downloadsDir, fileName)

        body.byteStream().use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Long = 0
                var read: Int
                var lastPercent = 0

                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        val percent = ((bytesRead * 100) / contentLength).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            withContext(Dispatchers.Main) {
                                onProgress("Đang tải...", percent)
                            }
                        }
                    }
                }
                outputStream.flush()
            }
        }
    }
}
