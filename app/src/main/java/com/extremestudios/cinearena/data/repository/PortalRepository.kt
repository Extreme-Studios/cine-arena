package com.extremestudios.cinearena.data.repository

import android.util.Log
import com.extremestudios.cinearena.data.model.PortalPost
import com.extremestudios.cinearena.data.model.PortalUserSession
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PortalRepository {
    fun getCategories(): List<String> = listOf(
        "All",
        "Event",
        "Promo Film",
        "Rilis Baru",
        "Festival"
    )

    fun getSubmissionCategories(): List<String> = getCategories().filter { it != "All" }

    fun normalizeWhatsapp(rawInput: String): Result<String> = runCatching {
        val compact = rawInput
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace("+", "")
            .trim()

        val normalized = when {
            compact.startsWith("0") -> "62${compact.drop(1)}"
            compact.startsWith("8") -> "62$compact"
            compact.startsWith("62") -> compact
            else -> throw IllegalStateException("Nomor WhatsApp tidak valid. Gunakan nomor Indonesia aktif.")
        }

        if (!normalized.all { it.isDigit() } || normalized.length !in 10..15) {
            throw IllegalStateException("Nomor WhatsApp tidak valid. Gunakan nomor Indonesia aktif.")
        }
        normalized
    }

    suspend fun registerHubBand(
        namaKreator: String,
        asalSekolah: String,
        whatsappRaw: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val whatsapp = normalizeWhatsapp(whatsappRaw).getOrThrow()
            val payload = JSONObject().apply {
                put("type", "hub_register")
                put("nama_kreator", namaKreator)
                put("asal_sekolah", asalSekolah)
                put("whatsapp", whatsapp)
            }
            val response = postJson(payload)
            if (!response.optBoolean("success", false)) {
                throw IllegalStateException(
                    response.optString("message").ifBlank { "Gagal mendaftarkan kreator." }
                )
            }
            (response.optJSONObject("user")?.optString("whatsapp") ?: "").ifBlank { whatsapp }
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Gagal mendaftarkan kreator.")
        }
    }

    suspend fun submitVideoUpload(
        judulVideo: String,
        genre: String,
        asalSekolah: String,
        whatsappRaw: String,
        videoUrl: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val whatsapp = normalizeWhatsapp(whatsappRaw).getOrThrow()
            val response = postJson(JSONObject().apply {
                put("type", "video_upload")
                put("judul_video", judulVideo)
                put("genre", genre)
                put("asal_sekolah", asalSekolah)
                put("whatsapp", whatsapp)
                put("video_url", videoUrl)
            })
            if (!response.optBoolean("success", false)) {
                throw IllegalStateException(response.optString("message").ifBlank { "Upload video gagal dikirim." })
            }
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Upload video gagal dikirim.")
        }
    }

    suspend fun loginHubBand(
        whatsappRaw: String,
    ): Result<PortalUserSession> = withContext(Dispatchers.IO) {
        runCatching {
            val account = requestHubAccount(type = "hub_login", whatsappRaw = whatsappRaw)
            val status = account.status
            if (!status.equals("ACTIVE", ignoreCase = true)) {
                Log.d("HubAuth", "login rejected reason=status_$status")
                throw IllegalStateException(
                    when {
                        status.equals("PENDING", ignoreCase = true) -> "Registrasi Anda masih menunggu approval admin."
                        status.equals("BLOCKED", ignoreCase = true) -> "Akun Anda diblokir admin."
                        else -> "Akun Cine Arena belum aktif."
                    }
                )
            }
            Log.d("HubAuth", "login accepted reason=status_ACTIVE userId=${account.userId}")
            account
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Gagal login Cine Arena.")
        }
    }

    suspend fun checkHubStatus(
        whatsappRaw: String,
    ): Result<PortalUserSession> = withContext(Dispatchers.IO) {
        runCatching {
            requestHubAccount(type = "hub_check_status", whatsappRaw = whatsappRaw)
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Gagal mengecek status Cine Arena.")
        }
    }

    suspend fun fetchActivePosts(): Result<List<PortalPost>> = withContext(Dispatchers.IO) {
        runCatching {
            val requestUrl = "$GOOGLE_APPS_SCRIPT_ENDPOINT?type=portal_posts&ts=${System.currentTimeMillis()}"
            val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code saat mengambil data portal.")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val normalizedBody = body.trimStart()
            if (normalizedBody.startsWith("<!DOCTYPE", ignoreCase = true) || normalizedBody.startsWith("<html", ignoreCase = true)) {
                throw IllegalStateException("Endpoint belum mengembalikan JSON. Deploy ulang Apps Script sebagai Web App (Who has access: Anyone).")
            }

            val root = JSONObject(body)
            val candidates = listOfNotNull(
                root.optJSONArray("portal_posts"),
                root.optJSONArray("posts"),
                root.optJSONArray("portalPosts")
            )
            val postsArray = candidates.firstOrNull() ?: JSONArray()
            buildList {
                for (i in 0 until postsArray.length()) {
                    val item = postsArray.optJSONObject(i) ?: continue
                    val status = item.optString("status").ifBlank { "PENDING" }
                    if (!status.equals("ACTIVE", ignoreCase = true) && !status.equals("active", ignoreCase = true)) continue
                    val posterUrl = item.optString("poster_url")
                    if (posterUrl.isBlank()) continue
                    add(
                        PortalPost(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            title = item.optString("title").ifBlank {
                                item.optString("band_name").ifBlank { item.optString("category") }
                            },
                            submitterName = item.optString("submitterName").ifBlank {
                                item.optString("band_name").ifBlank {
                                    item.optString("submitter_name").ifBlank { "Poster Board" }
                                }
                            },
                            category = item.optString("category"),
                            posterUrl = posterUrl,
                            targetUrl = item.optString("target_url"),
                            shortCaption = item.optString("short_caption").ifBlank { item.optString("caption") },
                            eventDate = item.optString("event_date"),
                            status = status,
                            createdAt = item.optString("created_at")
                        )
                    )
                }
            }
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Gagal memuat Poster Board.")
        }
    }

    suspend fun reportPoster(post: PortalPost, reason: String, reporterWhatsapp: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().apply {
                put("type", "portal_report")
                put("postId", post.id)
                put("postTitle", post.title)
                put("category", post.category)
                put("reason", reason)
                put("reporterWhatsapp", reporterWhatsapp)
                put("reportedLink", post.posterUrl)
                put("reportedDriveLink", post.posterUrl)
            }
            val response = postJson(payload)
            if (!response.optBoolean("success", false)) {
                throw IllegalStateException(response.optString("message").ifBlank { "Laporan gagal dikirim." })
            }
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Laporan gagal dikirim.")
        }
    }

    suspend fun submitPoster(
        session: PortalUserSession,
        category: String,
        posterImageBase64: String,
        posterImageFileName: String,
        posterImageMimeType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (session.userId.trim().isBlank()) {
                Log.e("PortalKirim", "Invalid hub session userId=${session.userId} for ${session.whatsappNormalized}")
                throw IllegalStateException("ID akun Cine Arena tidak valid. Cek status lagi.")
            }
            submitPortalMetadata(
                session = session,
                category = category,
                posterImageBase64 = posterImageBase64,
                posterImageFileName = posterImageFileName,
                posterImageMimeType = posterImageMimeType
            )
        }.recoverCatching {
            throw IllegalStateException(it.message ?: "Gagal mengirim poster.")
        }
    }

    private fun submitPortalMetadata(
        session: PortalUserSession,
        category: String,
        posterImageBase64: String,
        posterImageFileName: String,
        posterImageMimeType: String
    ) {
        val payload = JSONObject().apply {
            put("type", "portal_post")
            put("userId", session.userId)
            put("bandName", session.bandName)
            put("whatsapp", session.whatsappNormalized)
            put("category", category)
            put("title", "$category - ${session.bandName}".trim())
            put("shortCaption", "Poster $category dari ${session.bandName}".trim())
            put("eventDate", "")
            put("posterImageBase64", posterImageBase64)
            put("posterImageFileName", posterImageFileName)
            put("posterImageMimeType", posterImageMimeType)
        }
        Log.d(
            "PortalKirim",
            "payload={type=portal_post,userId=${session.userId},bandName=${session.bandName},whatsapp=${session.whatsappNormalized},category=$category,fileName=$posterImageFileName,mimeType=$posterImageMimeType,base64Length=${posterImageBase64.length}}"
        )
        val response = postJson(payload)
        Log.d("PortalKirim", "raw response=${response.toString()}")
        if (!response.optBoolean("success", false)) {
            throw IllegalStateException(
                response.optString("message").ifBlank { "Metadata poster gagal disimpan." }
            )
        }
    }

    private fun postJson(payload: JSONObject): JSONObject {
        val payloadType = payload.optString("type")
        if (payloadType == "hub_register" || payloadType == "hub_login" || payloadType == "hub_check_status") {
            Log.d("HubAuth", "payload=${payload.toString()}")
        }
        val connection = (URL(GOOGLE_APPS_SCRIPT_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(payload.toString())
        }

        val responseCode = connection.responseCode
        val responseBody = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()

        if (payloadType == "hub_register" || payloadType == "hub_login" || payloadType == "hub_check_status") {
            Log.d("HubAuth", "raw response=$responseBody")
        }

        if (responseCode !in 200..299) {
            throw IllegalStateException("HTTP $responseCode saat mengirim poster. $responseBody")
        }
        val normalizedBody = responseBody.trimStart()
        if (normalizedBody.startsWith("<!DOCTYPE", ignoreCase = true) || normalizedBody.startsWith("<html", ignoreCase = true)) {
            throw IllegalStateException("Endpoint belum mengembalikan JSON. Deploy ulang Apps Script sebagai Web App (Who has access: Anyone).")
        }
        return JSONObject(responseBody.ifBlank { "{\"success\":false}" })
    }

    private fun requestHubAccount(type: String, whatsappRaw: String): PortalUserSession {
        val whatsapp = normalizeWhatsapp(whatsappRaw).getOrThrow()
        val payload = JSONObject().apply {
            put("type", type)
            put("whatsapp", whatsapp)
        }
        val response = postJson(payload)
        if (!response.optBoolean("success", false)) {
            throw IllegalStateException(
                response.optString("message").ifBlank {
                    when (type) {
                        "hub_login" -> "Gagal login Cine Arena."
                        "hub_check_status" -> "Info akun Cine Arena tidak ditemukan."
                        else -> "Gagal mengecek status Cine Arena."
                    }
                }
            )
        }

        val user = response.optJSONObject("user")
            ?: throw IllegalStateException("Info akun Cine Arena tidak ditemukan.")
        val normalizedStatus = normalizeHubStatus(user.optString("status"))
        val parsedUserId = parseHubUserId(user, normalizedStatus)
        Log.d("HubAuth", "parsed user id=$parsedUserId")
        Log.d("HubAuth", "parsed status=$normalizedStatus")
        Log.d("HubAuth", "login status decision=${if (normalizedStatus.equals("ACTIVE", ignoreCase = true)) "accepted" else "not_active"}")
        return PortalUserSession(
            userId = parsedUserId,
            bandName = user.optString("bandName").ifBlank { user.optString("nama_kreator") },
            genre = user.optString("genre").ifBlank { user.optString("asal_sekolah") },
            city = user.optString("city").ifBlank { user.optString("asal_sekolah") },
            whatsappNormalized = user.optString("whatsapp").ifBlank { whatsapp },
            createdAt = user.optString("createdAt"),
            status = normalizedStatus
        )
    }

    private fun parseHubUserId(user: JSONObject, normalizedStatus: String): String {
        val rawId = user.opt("id")?.toString()?.trim().orEmpty()
        if (rawId.isBlank()) {
            Log.e("HubAuth", "Invalid HUB user id from backend. rawId=$rawId status=$normalizedStatus")
            if (normalizedStatus.equals("ACTIVE", ignoreCase = true)) {
                throw IllegalStateException("ID akun Cine Arena tidak valid.")
            }
        }
        return rawId
    }

    private fun normalizeHubStatus(rawStatus: String): String = when (rawStatus.trim().uppercase()) {
        "ACTIVE" -> "ACTIVE"
        "BLOCKED" -> "BLOCKED"
        else -> "PENDING"
    }

    companion object {
        private const val GOOGLE_APPS_SCRIPT_ENDPOINT =
            "https://script.google.com/macros/s/AKfycbz_p1WQF424K1anpzJjTVBcRXeJbN7c-dyqNZBURduIRVXSwezC1RNHvvl6anXi-9RNiA/exec"
    }
}
