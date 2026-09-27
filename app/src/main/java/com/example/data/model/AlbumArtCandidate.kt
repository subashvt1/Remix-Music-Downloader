package com.example.data.model

data class AlbumArtCandidate(
    val artworkUrl: String,
    val highResArtworkUrl: String,
    val trackName: String,
    val artistName: String,
    val collectionName: String,
    val releaseYear: String? = null,
    val primaryGenreName: String? = null,
    val matchScore: Int = 0, // Confidence match 0..100
    val source: String = "Apple iTunes Music Catalog"
)

enum class DownloadErrorType(val userTitle: String) {
    NETWORK_TIMEOUT("Network Timeout"),
    FILE_NOT_FOUND("File Not Found (404)"),
    SERVER_ERROR("Server Error"),
    ACCESS_DENIED("Access Denied (403)"),
    NO_INTERNET("No Internet Connection"),
    STORAGE_WRITE_ERROR("Storage Write Error"),
    SECURITY_SSL_ERROR("SSL / Security Error"),
    INVALID_URL("Invalid URL"),
    GENERIC_ERROR("Download Failed")
}

data class DownloadProgress(
    val isDownloading: Boolean = false,
    val trackTitle: String = "",
    val progressPercent: Int = 0,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val statusText: String = "Idle",
    val error: String? = null,
    val errorType: DownloadErrorType? = null,
    val isFailed: Boolean = false
)
