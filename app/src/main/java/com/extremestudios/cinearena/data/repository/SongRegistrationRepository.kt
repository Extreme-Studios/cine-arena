package com.extremestudios.cinearena.data.repository
import com.extremestudios.cinearena.data.model.SongRegistrationPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class SongRegistrationRepository {
    companion object {
        const val GOOGLE_APPS_SCRIPT_ENDPOINT = "https://script.google.com/macros/s/AKfycbz_p1WQF424K1anpzJjTVBcRXeJbN7c-dyqNZBURduIRVXSwezC1RNHvvl6anXi-9RNiA/exec"
        private const val NETWORK_TIMEOUT_MS = 90_000
    }

    suspend fun submitRegistration(payload: SongRegistrationPayload): Result<Unit> = withContext(Dispatchers.IO) {
        if (!GOOGLE_APPS_SCRIPT_ENDPOINT.startsWith("http")) {
            return@withContext Result.failure(IllegalStateException("Endpoint Google Apps Script belum diatur."))
        }

        runCatching {
            val connection = (URL(GOOGLE_APPS_SCRIPT_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val body = JSONObject().apply {
                put("type", "film_registration")
                put("judul_film", payload.title)
                put("release", payload.release)
                put("category", payload.category)
                put("duration", payload.duration)
                put("director", payload.director)
                put("nama_pemain", payload.castName)
                put("synopsis", payload.synopsis)
                put("copyrightAgreement", payload.copyrightAgreement)
                if (!payload.trailerUrl.isNullOrBlank()) {
                    put("trailer_url", payload.trailerUrl)
                    put("trailerUrl", payload.trailerUrl)
                }
                if (!payload.movieUrl.isNullOrBlank()) {
                    put("movie_url", payload.movieUrl)
                    put("mp3Link", payload.movieUrl)
                }
                if (!payload.coverImageBase64.isNullOrBlank()) {
                    put("coverImageBase64", payload.coverImageBase64)
                    put("coverImageFileName", payload.coverImageFileName.orEmpty())
                    put("coverImageMimeType", payload.coverImageMimeType.orEmpty())
                }
            }.toString()

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(body)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                throw IllegalStateException("Gagal kirim data (HTTP $responseCode). $errorBody")
            }
            val responseBody = connection.inputStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            val responseJson = if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()
            val success = responseJson.optBoolean("success", false)

            if (!success) {
                val message = responseJson.optString("message").ifBlank {
                    "Registrasi film gagal diproses."
                }
                throw IllegalStateException(message)
            }
            connection.disconnect()
        }.recoverCatching { throwable ->
            throw IllegalStateException(throwable.message ?: "Terjadi kesalahan saat mengirim registrasi.")
        }
    }
}
