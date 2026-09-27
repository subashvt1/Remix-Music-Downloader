package com.example.data.network

import com.example.data.model.AlbumArtCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.max

class AlbumArtSearchApi(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun searchAccurateArtwork(
        title: String,
        artist: String = ""
    ): List<AlbumArtCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<AlbumArtCandidate>()
        try {
            // Clean up title and artist (remove file extensions, parenthetical noise like "(Official Audio)")
            val cleanTitle = cleanSearchTerm(title)
            val cleanArtist = cleanSearchTerm(artist)
            val combinedQuery = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle
            val encodedQuery = URLEncoder.encode(combinedQuery.trim(), StandardCharsets.UTF_8.toString())
            
            val url = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=10"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "EduMusic-Downloader/1.0 (Android)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return@withContext emptyList()

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val trackName = item.optString("trackName", "")
                val artistName = item.optString("artistName", "")
                val collectionName = item.optString("collectionName", "")
                val artworkUrl100 = item.optString("artworkUrl100", "")
                val releaseDate = item.optString("releaseDate", "")
                val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else null
                val genre = item.optString("primaryGenreName", null)

                if (artworkUrl100.isNotBlank()) {
                    // iTunes artwork URLs contain /100x100bb.jpg - replace with 1000x1000bb.jpg for ultra HD
                    val highResUrl = artworkUrl100
                        .replace("100x100bb.jpg", "1000x1000bb.jpg")
                        .replace("100x100bb.png", "1000x1000bb.png")
                        .replace("60x60bb.jpg", "1000x1000bb.jpg")

                    val score = calculateMatchConfidence(
                        queryTitle = cleanTitle,
                        queryArtist = cleanArtist,
                        resultTitle = trackName,
                        resultArtist = artistName
                    )

                    candidates.add(
                        AlbumArtCandidate(
                            artworkUrl = artworkUrl100,
                            highResArtworkUrl = highResUrl,
                            trackName = trackName.ifBlank { title },
                            artistName = artistName.ifBlank { artist },
                            collectionName = collectionName.ifBlank { "Single / Album" },
                            releaseYear = year,
                            primaryGenreName = genre,
                            matchScore = score
                        )
                    )
                }
            }

            // Sort highest matching candidates first
            candidates.sortByDescending { it.matchScore }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        candidates
    }

    private fun cleanSearchTerm(term: String): String {
        return term
            .replace(Regex("\\.(mp3|wav|ogg|m4a|aac|flac)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?official.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?audio.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\(.*?video.*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[_\\-]+"), " ")
            .trim()
    }

    private fun calculateMatchConfidence(
        queryTitle: String,
        queryArtist: String,
        resultTitle: String,
        resultArtist: String
    ): Int {
        val qT = queryTitle.lowercase().trim()
        val qA = queryArtist.lowercase().trim()
        val rT = resultTitle.lowercase().trim()
        val rA = resultArtist.lowercase().trim()

        var score = 50
        if (qT.isNotBlank() && (rT.contains(qT) || qT.contains(rT))) {
            score += 30
        }
        if (qT == rT) {
            score += 15
        }
        if (qA.isNotBlank()) {
            if (rA.contains(qA) || qA.contains(rA)) {
                score += 15
            }
            if (qA == rA) {
                score += 10
            }
        }
        return score.coerceIn(0, 100)
    }
}
