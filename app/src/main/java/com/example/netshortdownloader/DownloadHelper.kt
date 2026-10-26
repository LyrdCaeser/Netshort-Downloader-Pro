package com.example.netshortdownloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class DownloadHelper(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun downloadDirectVideo(
        mediaUrl: String,
        referer: String,
        onProgress: (status: String, percent: Int) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var savedUri: Uri? = null
            var pendingFile: File? = null
            try {
                val uri = Uri.parse(mediaUrl.trim())
                require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) {
                    "Chỉ hỗ trợ link HTTPS hợp lệ."
                }
                val path = uri.path.orEmpty().lowercase(Locale.ROOT)
                val fromNetShort = isNetShortReferer(referer)
                val host = uri.host.orEmpty().lowercase(Locale.ROOT)
                require(host == "netshort.com" || host.endsWith(".netshort.com") || fromNetShort) {
                    "Chỉ tải link được phát hiện từ NetShort hoặc CDN của trang NetShort."
                }
                require(!path.endsWith(".m3u8")) {
                    "Đây là playlist HLS, không phải file video trực tiếp. HLS/DRM không được giải mã hoặc ghép bởi ứng dụng này."
                }
                val hasVideoExtension = listOf(".mp4", ".m4v", ".webm", ".mov").any(path::endsWith)
                require(hasVideoExtension || fromNetShort) {
                    "Link không trỏ tới file video trực tiếp được hỗ trợ. Hãy mở video trong môi trường NetShort và chọn link media đã phát hiện."
                }

                withContext(Dispatchers.Main) { onProgress("Đang kết nối tới video…", 0) }
                executeFollowingSafeRedirects(uri.toString(), referer).use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("Máy chủ trả về HTTP ${response.code}.")
                    val body = response.body ?: throw IllegalStateException("Máy chủ không trả dữ liệu video.")
                    val contentType = body.contentType()?.toString()?.substringBefore(';')?.lowercase(Locale.ROOT).orEmpty()
                    if (contentType.contains("mpegurl") || contentType == "application/x-mpegurl") {
                        throw IllegalStateException("Máy chủ trả về playlist HLS, không phải file video trực tiếp.")
                    }
                    if (contentType == "text/html" || contentType.startsWith("text/") || contentType.contains("json")) {
                        throw IllegalStateException("Máy chủ trả về trang web/API thay vì video. Hãy bắt link media đang phát trong WebView.")
                    }
                    val supportedVideoTypes = setOf("video/mp4", "video/webm", "video/quicktime", "video/x-m4v")
                    val isKnownVideo = contentType in supportedVideoTypes
                    val isOctetStreamWithExtension = contentType == "application/octet-stream" && hasVideoExtension
                    if (contentType.isNotBlank() && !isKnownVideo && !isOctetStreamWithExtension) {
                        throw IllegalStateException("Kiểu nội dung không phải video: $contentType")
                    }
                    if (contentType.isBlank() && !hasVideoExtension) {
                        throw IllegalStateException("Máy chủ không gửi MIME video cho link không có phần mở rộng.")
                    }

                    val extension = when {
                        contentType == "video/mp4" -> "mp4"
                        contentType.contains("webm") -> "webm"
                        contentType.contains("quicktime") -> "mov"
                        contentType == "video/x-m4v" -> "m4v"
                        else -> path.substringAfterLast('.', "mp4").substringBefore('?').takeIf { it in setOf("mp4", "m4v", "webm", "mov") } ?: "mp4"
                    }
                    val mimeType = when (extension) {
                        "webm" -> "video/webm"
                        "mov" -> "video/quicktime"
                        else -> "video/mp4"
                    }
                    val fileName = "NetShort_${System.currentTimeMillis()}.$extension"
                    val expectedLength = body.contentLength()
                    if (expectedLength > 8L * 1024L * 1024L * 1024L) {
                        throw IllegalStateException("File vượt quá giới hạn 8 GiB.")
                    }
                    val output = createOutput(fileName, mimeType)
                    savedUri = output.first
                    pendingFile = output.third

                    output.second.use { stream ->
                        val input = body.byteStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        val signature = ByteArray(12)
                        var signatureLength = 0
                        var copied = 0L
                        var lastPercent = -1
                        input.use { source ->
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                stream.write(buffer, 0, count)
                                if (signatureLength < signature.size) {
                                    val signatureCount = minOf(count, signature.size - signatureLength)
                                    buffer.copyInto(signature, signatureLength, 0, signatureCount)
                                    signatureLength += signatureCount
                                }
                                copied += count
                                val length = expectedLength
                                if (length > 0) {
                                    val percent = ((copied * 100L) / length).toInt().coerceIn(0, 100)
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        withContext(Dispatchers.Main) {
                                            onProgress("Đang tải $fileName", percent)
                                        }
                                    }
                                }
                            }
                            stream.flush()
                        }
                        if (copied == 0L) throw IllegalStateException("File video nhận được đang rỗng.")
                        if (expectedLength > 0 && copied != expectedLength) {
                            throw IllegalStateException("Tải chưa hoàn tất: nhận $copied/$expectedLength byte.")
                        }
                        val container = if (signatureLength >= 8) {
                            String(signature.copyOfRange(4, 8), Charsets.US_ASCII)
                        } else ""
                        val isWebm = signatureLength >= 4 &&
                            signature[0] == 0x1A.toByte() && signature[1] == 0x45.toByte() &&
                            signature[2] == 0xDF.toByte() && signature[3] == 0xA3.toByte()
                        val validContainer = if (extension == "webm") isWebm else container == "ftyp"
                        if (!validContainer) throw IllegalStateException("Dữ liệu tải xuống không có chữ ký container video hợp lệ.")
                    }
                    finishOutput(savedUri)
                    val destination = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        "Downloads/NetShort"
                    } else {
                        "Android/data/${context.packageName}/files/Download"
                    }
                    withContext(Dispatchers.Main) {
                        onProgress("Đã lưu $fileName trong $destination", 100)
                    }
                }
                true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                savedUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                pendingFile?.let { runCatching { it.delete() } }
                val message = error.message ?: "Lỗi tải video không xác định."
                withContext(Dispatchers.Main) { onProgress("Không tải được: $message", 0) }
                false
            }
        }
    }

    /** Returns (content URI, output stream, legacy file if one was created). */
    private fun createOutput(fileName: String, mimeType: String): Triple<Uri?, OutputStream, File?> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/NetShort")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Không tạo được file trong thư mục Downloads.")
            val stream = try {
                context.contentResolver.openOutputStream(uri, "w")
            } catch (error: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw error
            } ?: run {
                context.contentResolver.delete(uri, null, null)
                throw IllegalStateException("Không mở được file đích.")
            }
            Triple(uri, stream, null)
        } else {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IllegalStateException("Bộ nhớ ngoài không khả dụng.")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException("Không tạo được thư mục tải xuống.")
            }
            val file = File(directory, fileName)
            Triple(null, FileOutputStream(file), file)
        }
    }

    private fun finishOutput(uri: Uri?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (context.contentResolver.update(uri, values, null, null) == 0) {
                throw IllegalStateException("Không thể công bố file trong Downloads.")
            }
        }
    }

    private fun executeFollowingSafeRedirects(startUrl: String, referer: String): okhttp3.Response {
        val initialHost = Uri.parse(startUrl).host
        var currentUrl = startUrl
        var redirects = 0
        while (true) {
            val requestBuilder = Request.Builder()
                .url(currentUrl)
                .header("Accept", "video/*, application/octet-stream;q=0.9, */*;q=0.5")
            val cookie = CookieManager.getInstance().getCookie(currentUrl)
            if (!cookie.isNullOrBlank()) requestBuilder.header("Cookie", cookie)
            if (isNetShortReferer(referer) && Uri.parse(currentUrl).host == initialHost) {
                requestBuilder.header("Referer", referer)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isRedirect) return response
            if (redirects >= 5) {
                response.close()
                throw IllegalStateException("Máy chủ chuyển hướng quá 5 lần.")
            }
            val nextUrl = response.header("Location")?.let { response.request.url.resolve(it) }
            if (nextUrl == null || !nextUrl.isHttps || nextUrl.username.isNotEmpty() || nextUrl.password.isNotEmpty()) {
                response.close()
                throw IllegalStateException("Chuyển hướng không hợp lệ hoặc không dùng HTTPS.")
            }
            val previousHost = response.request.url.host
            if (nextUrl.host != previousHost && !isNetShortReferer(referer)) {
                response.close()
                throw IllegalStateException("Chặn chuyển hướng sang host khác khi không có trang NetShort tham chiếu.")
            }
            response.close()
            currentUrl = nextUrl.toString()
            redirects += 1
        }
    }

    private fun isNetShortReferer(value: String): Boolean {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return uri.scheme == "https" && (host == "netshort.com" || host.endsWith(".netshort.com"))
    }
}
