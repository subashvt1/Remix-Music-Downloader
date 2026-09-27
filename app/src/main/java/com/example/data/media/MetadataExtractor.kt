package com.example.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ExtractedMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val year: String?,
    val genre: String?,
    val embeddedArtFileUri: String?,
    val hasEmbeddedArt: Boolean
)

object MetadataExtractor {

    suspend fun extract(
        context: Context,
        fileUri: Uri,
        fallbackTitle: String = "Unknown Track",
        fallbackArtist: String = "Unknown Artist"
    ): ExtractedMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var title = fallbackTitle
        var artist = fallbackArtist
        var album = "Unknown Album"
        var durationMs = 0L
        var year: String? = null
        var genre: String? = null
        var embeddedArtUri: String? = null
        var hasArt = false

        try {
            retriever.setDataSource(context, fileUri)

            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                if (it.isNotBlank()) title = it.trim()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                if (it.isNotBlank()) artist = it.trim()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let {
                if (it.isNotBlank()) album = it.trim()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                durationMs = it.toLongOrNull() ?: 0L
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let {
                if (it.isNotBlank()) genre = it.trim()
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let {
                if (it.isNotBlank()) year = it.take(4)
            }

            // Extract embedded album art picture
            val artBytes = retriever.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                val artDir = File(context.filesDir, "album_art").apply { mkdirs() }
                val artFile = File(artDir, "embedded_${System.currentTimeMillis()}_${fileUri.hashCode()}.jpg")
                FileOutputStream(artFile).use { fos ->
                    fos.write(artBytes)
                    fos.flush()
                }
                embeddedArtUri = Uri.fromFile(artFile).toString()
                hasArt = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }

        ExtractedMetadata(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            year = year,
            genre = genre,
            embeddedArtFileUri = embeddedArtUri,
            hasEmbeddedArt = hasArt
        )
    }

    suspend fun saveArtFromUrl(
        context: Context,
        imageUrl: String,
        trackId: Long
    ): String? = withContext(Dispatchers.IO) {
        try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(imageUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val bytes = response.body?.bytes() ?: return@withContext null
            val artDir = File(context.filesDir, "album_art").apply { mkdirs() }
            val artFile = File(artDir, "hd_art_${trackId}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(artFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            Uri.fromFile(artFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
