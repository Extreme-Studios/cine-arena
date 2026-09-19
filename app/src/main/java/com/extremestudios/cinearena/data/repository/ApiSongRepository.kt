package com.extremestudios.cinearena.data.repository

import android.util.Log
import com.extremestudios.cinearena.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ApiSongRepository {
    private var cachedSongs: List<Song> = emptyList()
    private var lastFetchAtMs: Long = 0L
    private var cachedLikeUserId: String? = null

    suspend fun fetchSongs(likeUserId: String? = null): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val normalizedLikeUserId = likeUserId?.trim().orEmpty().ifBlank { null }
            if (cachedSongs.isNotEmpty() && cachedLikeUserId == normalizedLikeUserId && now - lastFetchAtMs < CACHE_TTL_MS) {
                Log.d(TAG, "Using in-memory cache. count=${cachedSongs.size}")
                return@runCatching cachedSongs
            }
            val requestUrl = buildString {
                append(SONGS_ENDPOINT)
                append("?ts=${System.currentTimeMillis()}")
                if (!normalizedLikeUserId.isNullOrBlank()) {
                    append("&user_id=${java.net.URLEncoder.encode(normalizedLikeUserId, "UTF-8")}")
                }
            }
            val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code saat mengambil data film.")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val root = JSONObject(body)
            if (!root.optBoolean("success", false)) throw IllegalStateException("Response API tidak sukses.")
            val songsArray = root.optJSONArray("films")
                ?: root.optJSONArray("movies")
                ?: root.optJSONArray("songs")
                ?: root.optJSONArray("data")
                ?: root.optJSONObject("data")?.optJSONArray("films")
                ?: root.optJSONObject("data")?.optJSONArray("songs")
                ?: throw IllegalStateException("Field films tidak ditemukan.")

            Log.d(TAG, "Raw films from API: ${songsArray.length()}")
            var activeCount = 0
            val songs = buildList {
                for (i in 0 until songsArray.length()) {
                    val item = songsArray.optJSONObject(i) ?: continue
                    val title = readString(item, "title", "film_title", "movie_title", "judul_film", "song_title", "judul_lagu")
                    val artist = readString(item, "director", "creator", "artist", "band_name", "artist_name", "bandName", "submitterName")
                    val status = readString(item, "status").ifBlank { "approved" }
                    val isActive = status.equals("approved", ignoreCase = true)
                    if (!isActive) continue
                    activeCount++

                    val rawId = readString(item, "id", "film_id", "movie_id", "song_id")
                    val createdAt = readString(item, "created_at", "uploaded_at", "uploadedAt", "updated_at")
                    val coverUrl = readString(item, "poster_url", "cover_url", "coverUrl", "cover")
                    val movieUrl = readString(item, "movie_url", "video_url", "videoUrl", "media_url", "audio_url", "audioUrl", "audio")
                    val trailerUrl = readString(item, "trailer_url", "trailerUrl")
                    val albumName = readString(item, "collection_name", "album_name", "albumName")
                    val trackNumber = readString(item, "episode_number", "track_number", "trackNumber").toIntOrNull()
                    val resolvedId = rawId.ifBlank {
                        listOf(title, artist, createdAt, i.toString()).joinToString("#")
                    }
                    Log.d(TAG, "Film from API title=$title poster_url=$coverUrl movie_url=$movieUrl category=${readString(item, "category")}")

                    add(
                        Song(
                            id = resolvedId,
                            title = title,
                            artist = artist,
                            genre = readString(item, "category", "genre"),
                            category = readString(item, "category", "genre"),
                            duration = readString(item, "duration"),
                            director = readString(item, "director", "creator", "artist"),
                            castName = readString(item, "nama_pemain", "cast", "cast_name", "pemain"),
                            description = readString(item, "synopsis", "description", "desc", "notes"),
                            coverUrl = coverUrl,
                            trailerUrl = trailerUrl,
                            movieUrl = movieUrl,
                            audioUrl = movieUrl,
                            uploadedAt = createdAt,
                            status = status,
                            featured = readString(item, "featured").equals("yes", ignoreCase = true),
                            views = readString(item, "views").toIntOrNull() ?: 0,
                            likes = readString(item, "likes").toIntOrNull() ?: 0,
                            alreadyLiked = item.optBoolean("already_liked", false),
                            releaseYear = readString(item, "release", "tahun_release", "tahun_pembuatan", "release_year", "year", "releaseYear"),
                            albumName = albumName,
                            trackNumber = trackNumber
                        )
                    )
                }
            }
                .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<Song> { parseCreatedAtMillis(it.uploadedAt) }
                        .thenBy { it.title.lowercase(Locale.US) }
                )

            Log.d(TAG, "Active films after status filter: $activeCount")
            Log.d(TAG, "Films after validation/dedupe/sort: ${songs.size}")
            cachedSongs = songs
            lastFetchAtMs = now
            cachedLikeUserId = normalizedLikeUserId
            songs
        }
    }

    fun getGenres(songs: List<Song>): List<String> = listOf("Semua") + fixedCategories
    fun getArtists(songs: List<Song>): List<String> = songs.map { it.artist }.filter { it.isNotBlank() }.distinct().sorted()

    fun getFallbackSongs(): List<Song> = emptyList()

    suspend fun updateFilmLike(filmId: String, userId: String, whatsapp: String): Result<FilmLikeResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (filmId.trim().isBlank()) throw IllegalStateException("film_id kosong.")
            if (userId.trim().isBlank() || whatsapp.trim().isBlank()) {
                throw IllegalStateException("Login diperlukan untuk Like.")
            }
            val connection = (URL(SONGS_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val body = JSONObject().apply {
                put("type", "film_like")
                put("film_id", filmId)
                put("userId", userId)
                put("whatsapp", whatsapp)
            }.toString()
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val responseJson = JSONObject(responseBody)
            if (!responseJson.optBoolean("success", false)) {
                throw IllegalStateException(responseJson.optString("message").ifBlank { "Gagal update like." })
            }
            cachedSongs = emptyList()
            lastFetchAtMs = 0L
            FilmLikeResult(
                totalLikes = responseJson.optInt("total_likes", responseJson.optInt("likes", 0)),
                alreadyLiked = responseJson.optBoolean("already_liked", true)
            )
        }
    }

    suspend fun updateFilmView(filmId: String?, movieUrl: String?): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(SONGS_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val body = JSONObject().apply {
                put("type", "film_view")
                if (!filmId.isNullOrBlank()) put("film_id", filmId)
                if (!movieUrl.isNullOrBlank()) put("movie_url", movieUrl)
            }.toString()
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val responseJson = JSONObject(responseBody)
            if (!responseJson.optBoolean("success", false)) {
                throw IllegalStateException(responseJson.optString("message").ifBlank { "Gagal update view." })
            }
            responseJson.optInt("views", 0)
        }
    }

    companion object {
        private const val TAG = "ApiSongRepository"
        private const val CACHE_TTL_MS = 60_000L
        private const val SONGS_ENDPOINT = "https://script.google.com/macros/s/AKfycbz_p1WQF424K1anpzJjTVBcRXeJbN7c-dyqNZBURduIRVXSwezC1RNHvvl6anXi-9RNiA/exec"
    }
}

data class FilmLikeResult(
    val totalLikes: Int,
    val alreadyLiked: Boolean
)

private fun readString(item: JSONObject, vararg keys: String): String {
    keys.forEach { key ->
        if (item.has(key)) {
            val value = item.opt(key)
            if (value != null && value != JSONObject.NULL) {
                val text = value.toString().trim()
                if (text.isNotBlank()) return text
            }
        }
    }
    return ""
}

private fun parseCreatedAtMillis(value: String): Long {
    if (value.isBlank()) return Long.MIN_VALUE
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(value)?.time
        }.getOrNull()
    } ?: Long.MIN_VALUE
}
    private val fixedCategories = listOf(
        "Horror",
        "Action",
        "Drama",
        "Fantasy",
        "Sci Fi",
        "Education",
        "Culture"
    )
