package com.example.data.network

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.model.DownloadErrorType
import com.example.data.model.DownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class MusicDownloadService(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {

    data class DownloadResult(
        val success: Boolean,
        val contentUri: Uri?,
        val filePath: String,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val errorMessage: String? = null,
        val errorType: DownloadErrorType? = null
    )

    suspend fun downloadAudioFile(
        url: String,
        suggestedFileName: String? = null,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank() || (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true))) {
            val err = "Invalid URL: Must start with http:// or https://"
            onProgress(DownloadProgress(isDownloading = false, error = err, errorType = DownloadErrorType.INVALID_URL, isFailed = true))
            return@withContext DownloadResult(false, null, "", "", 0, "", err, DownloadErrorType.INVALID_URL)
        }

        onProgress(DownloadProgress(isDownloading = true, progressPercent = 0, statusText = "Connecting to server..."))

        var targetUri: Uri? = null
        var targetFile: File? = null
        var outputStream: OutputStream? = null

        try {
            val request = Request.Builder()
                .url(trimmedUrl)
                .header("User-Agent", "EduMusic-Downloader/1.0 (Android)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                val (errorType, friendlyMsg) = when (code) {
                    404, 410 -> DownloadErrorType.FILE_NOT_FOUND to "Audio file not found on server (HTTP $code). Please check the link."
                    401, 403 -> DownloadErrorType.ACCESS_DENIED to "Access forbidden or authorization required to download (HTTP $code)."
                    408, 504 -> DownloadErrorType.NETWORK_TIMEOUT to "Server request timed out (HTTP $code). Please try again."
                    in 500..599 -> DownloadErrorType.SERVER_ERROR to "The audio server encountered an error (HTTP $code). Please try again later."
                    else -> DownloadErrorType.GENERIC_ERROR to "Server returned HTTP status $code (${response.message})."
                }
                onProgress(DownloadProgress(isDownloading = false, error = friendlyMsg, errorType = errorType, isFailed = true))
                return@withContext DownloadResult(false, null, "", "", 0, "", friendlyMsg, errorType)
            }

            val body = response.body
            if (body == null) {
                val err = "Empty response received from audio server"
                onProgress(DownloadProgress(isDownloading = false, error = err, errorType = DownloadErrorType.SERVER_ERROR, isFailed = true))
                return@withContext DownloadResult(false, null, "", "", 0, "", err, DownloadErrorType.SERVER_ERROR)
            }

            val totalBytes = body.contentLength()
            val rawContentType = body.contentType()?.toString() ?: "audio/mpeg"
            val mimeType = when {
                rawContentType.contains("ogg", ignoreCase = true) || trimmedUrl.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
                rawContentType.contains("wav", ignoreCase = true) || trimmedUrl.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                rawContentType.contains("m4a", ignoreCase = true) || trimmedUrl.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                rawContentType.contains("flac", ignoreCase = true) || trimmedUrl.endsWith(".flac", ignoreCase = true) -> "audio/flac"
                else -> "audio/mpeg"
            }

            val ext = when (mimeType) {
                "audio/ogg" -> ".ogg"
                "audio/wav" -> ".wav"
                "audio/mp4" -> ".m4a"
                "audio/flac" -> ".flac"
                else -> ".mp3"
            }

            val fileName = (suggestedFileName?.takeIf { it.isNotBlank() } ?: extractFileNameFromUrl(trimmedUrl))
                .let { if (!it.endsWith(ext, ignoreCase = true)) "$it$ext" else it }
                .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")

            onProgress(DownloadProgress(isDownloading = true, trackTitle = fileName, progressPercent = 5, statusText = "Preparing file in Music/EduMusic..."))

            // Save to MediaStore (Music directory) on Android 10+ (API 29+)
            var filePath = ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/EduMusic")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val collectionUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                targetUri = resolver.insert(collectionUri, contentValues)
                if (targetUri != null) {
                    try {
                        outputStream = resolver.openOutputStream(targetUri)
                        filePath = "Music/EduMusic/$fileName"
                    } catch (e: Exception) {
                        // In case opening output stream fails, clean up pending record
                        resolver.delete(targetUri, null, null)
                        targetUri = null
                        throw e
                    }
                }
            }

            // Fallback for pre-Q or if MediaStore insert failed
            if (outputStream == null) {
                val musicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "EduMusic"
                ).apply { mkdirs() }
                val target = File(musicDir, fileName)
                targetFile = target
                filePath = target.absolutePath
                targetUri = Uri.fromFile(target)
                outputStream = FileOutputStream(target)
            }

            var bytesReadTotal = 0L
            val inputStream: InputStream = body.byteStream()
            val buffer = ByteArray(8 * 1024)

            outputStream!!.use { out ->
                inputStream.use { input ->
                    var read: Int
                    var lastUpdatePercent = 0
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        bytesReadTotal += read
                        if (totalBytes > 0) {
                            val percent = ((bytesReadTotal * 100) / totalBytes).toInt().coerceIn(0, 99)
                            if (percent != lastUpdatePercent) {
                                lastUpdatePercent = percent
                                onProgress(
                                    DownloadProgress(
                                        isDownloading = true,
                                        trackTitle = fileName,
                                        progressPercent = percent,
                                        bytesRead = bytesReadTotal,
                                        totalBytes = totalBytes,
                                        statusText = "Downloading: $percent%"
                                    )
                                )
                            }
                        }
                    }
                    out.flush()
                }
            }

            // Mark file as complete in MediaStore on API 29+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetUri != null) {
                val resolver = context.contentResolver
                val completeValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                resolver.update(targetUri, completeValues, null, null)
            }

            onProgress(
                DownloadProgress(
                    isDownloading = false,
                    trackTitle = fileName,
                    progressPercent = 100,
                    bytesRead = bytesReadTotal,
                    totalBytes = totalBytes,
                    statusText = "Completed"
                )
            )

            DownloadResult(
                success = true,
                contentUri = targetUri,
                filePath = filePath,
                fileName = fileName,
                fileSize = bytesReadTotal,
                mimeType = mimeType
            )
        } catch (e: Throwable) {
            e.printStackTrace()

            // Corrupt file prevention: Remove any partial / empty files created
            try {
                outputStream?.close()
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetUri != null) {
                try {
                    context.contentResolver.delete(targetUri, null, null)
                } catch (_: Exception) {}
            }
            if (targetFile != null && targetFile.exists()) {
                try {
                    targetFile.delete()
                } catch (_: Exception) {}
            }

            val (errorType, friendlyMsg) = when (e) {
                is SocketTimeoutException, is InterruptedIOException -> {
                    DownloadErrorType.NETWORK_TIMEOUT to "Connection timed out. The server took too long to respond. Please check your internet connection."
                }
                is UnknownHostException, is ConnectException -> {
                    DownloadErrorType.NO_INTERNET to "Unable to reach audio server. Please check your internet connection or verify the URL."
                }
                is SSLException -> {
                    DownloadErrorType.SECURITY_SSL_ERROR to "Secure connection (SSL/TLS) failed while contacting the server."
                }
                is IOException -> {
                    val msg = e.message.orEmpty()
                    if (msg.contains("ENOSPC", ignoreCase = true) || msg.contains("No space", ignoreCase = true) || msg.contains("write failed", ignoreCase = true)) {
                        DownloadErrorType.STORAGE_WRITE_ERROR to "Storage write error: Insufficient space on device storage."
                    } else {
                        DownloadErrorType.STORAGE_WRITE_ERROR to "Storage error while writing audio file: ${e.localizedMessage ?: "I/O error"}"
                    }
                }
                is SecurityException -> {
                    DownloadErrorType.STORAGE_WRITE_ERROR to "Permission denied: Unable to write to device Music folder."
                }
                else -> {
                    DownloadErrorType.GENERIC_ERROR to (e.localizedMessage ?: "Unexpected download error occurred")
                }
            }

            onProgress(
                DownloadProgress(
                    isDownloading = false,
                    error = friendlyMsg,
                    errorType = errorType,
                    isFailed = true
                )
            )

            DownloadResult(
                success = false,
                contentUri = null,
                filePath = "",
                fileName = "",
                fileSize = 0,
                mimeType = "",
                errorMessage = friendlyMsg,
                errorType = errorType
            )
        }
    }

    private fun extractFileNameFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val lastSegment = uri.lastPathSegment ?: "audio_track_${System.currentTimeMillis()}"
            lastSegment
        } catch (e: Exception) {
            "track_${System.currentTimeMillis()}.mp3"
        }
    }
}
