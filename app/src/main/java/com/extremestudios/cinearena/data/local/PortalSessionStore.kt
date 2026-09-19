package com.extremestudios.cinearena.data.local

import android.util.Log
import android.content.Context
import com.extremestudios.cinearena.data.model.PortalUserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PortalSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    init {
        ensureInitialized(prefs)
    }

    val sessionFlow: StateFlow<PortalUserSession?> = sharedSessionFlow.asStateFlow()
    val pendingWhatsappFlow: StateFlow<String?> = sharedPendingWhatsappFlow.asStateFlow()
    val lastHubWhatsappFlow: StateFlow<String?> = sharedLastHubWhatsappFlow.asStateFlow()

    fun saveSession(session: PortalUserSession) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_BAND_NAME, session.bandName)
            .putString(KEY_GENRE, session.genre)
            .putString(KEY_CITY, session.city)
            .putString(KEY_WHATSAPP, session.whatsappNormalized)
            .putString(KEY_CREATED_AT, session.createdAt)
            .putString(KEY_STATUS, session.status)
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_LAST_HUB_WHATSAPP, session.whatsappNormalized)
            .remove(KEY_PENDING_WHATSAPP)
            .apply()
        sharedSessionFlow.value = session
        sharedPendingWhatsappFlow.value = null
        sharedLastHubWhatsappFlow.value = session.whatsappNormalized
        Log.d("HubAuth", "saved session userId=${session.userId}, status=${session.status}")
    }

    fun savePendingWhatsapp(whatsapp: String) {
        prefs.edit()
            .putString(KEY_PENDING_WHATSAPP, whatsapp)
            .putString(KEY_LAST_HUB_WHATSAPP, whatsapp)
            .putBoolean(KEY_LOGGED_IN, false)
            .remove(KEY_USER_ID)
            .remove(KEY_BAND_NAME)
            .remove(KEY_GENRE)
            .remove(KEY_CITY)
            .remove(KEY_WHATSAPP)
            .remove(KEY_CREATED_AT)
            .remove(KEY_STATUS)
            .apply()
        sharedSessionFlow.value = null
        sharedPendingWhatsappFlow.value = whatsapp
        sharedLastHubWhatsappFlow.value = whatsapp
    }

    fun clearPendingWhatsapp() {
        prefs.edit().remove(KEY_PENDING_WHATSAPP).apply()
        sharedPendingWhatsappFlow.value = null
    }

    fun clearLastHubWhatsapp() {
        prefs.edit().remove(KEY_LAST_HUB_WHATSAPP).apply()
        sharedLastHubWhatsappFlow.value = null
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_LOGGED_IN)
            .remove(KEY_USER_ID)
            .remove(KEY_BAND_NAME)
            .remove(KEY_GENRE)
            .remove(KEY_CITY)
            .remove(KEY_WHATSAPP)
            .remove(KEY_CREATED_AT)
            .remove(KEY_STATUS)
            .apply()
        sharedSessionFlow.value = null
    }

    companion object {
        private const val PREFS_NAME = "portal_session_store"
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_BAND_NAME = "band_name"
        private const val KEY_GENRE = "genre"
        private const val KEY_CITY = "city"
        private const val KEY_WHATSAPP = "whatsapp"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_STATUS = "status"
        private const val KEY_PENDING_WHATSAPP = "pending_whatsapp"
        private const val KEY_LAST_HUB_WHATSAPP = "last_hub_whatsapp"

        private var initialized = false
        private val sharedSessionFlow = MutableStateFlow<PortalUserSession?>(null)
        private val sharedPendingWhatsappFlow = MutableStateFlow<String?>(null)
        private val sharedLastHubWhatsappFlow = MutableStateFlow<String?>(null)

        @Synchronized
        private fun ensureInitialized(prefs: android.content.SharedPreferences) {
            if (initialized) return
            sharedSessionFlow.value = loadSession(prefs)
            sharedPendingWhatsappFlow.value = loadPendingWhatsapp(prefs)
            sharedLastHubWhatsappFlow.value = loadLastHubWhatsapp(prefs)
            initialized = true
        }

        private fun loadSession(prefs: android.content.SharedPreferences): PortalUserSession? {
            if (!prefs.getBoolean(KEY_LOGGED_IN, false)) return null
            val userId = readStringCompat(prefs, KEY_USER_ID)
            val bandName = prefs.getString(KEY_BAND_NAME, "").orEmpty()
            val genre = prefs.getString(KEY_GENRE, "").orEmpty()
            val city = prefs.getString(KEY_CITY, "").orEmpty()
            val whatsapp = prefs.getString(KEY_WHATSAPP, "").orEmpty()
            val createdAt = prefs.getString(KEY_CREATED_AT, "").orEmpty()
            val status = prefs.getString(KEY_STATUS, "ACTIVE").orEmpty()
            if (userId.isBlank() || bandName.isBlank() || whatsapp.isBlank()) return null
            return PortalUserSession(
                userId = userId,
                bandName = bandName,
                genre = genre,
                city = city,
                whatsappNormalized = whatsapp,
                createdAt = createdAt,
                status = status
            )
        }

        private fun readStringCompat(
            prefs: android.content.SharedPreferences,
            key: String
        ): String = when (val value = prefs.all[key]) {
            is String -> value
            is Number -> value.toString()
            else -> ""
        }

        private fun loadPendingWhatsapp(prefs: android.content.SharedPreferences): String? =
            prefs.getString(KEY_PENDING_WHATSAPP, null)?.takeIf { it.isNotBlank() }

        private fun loadLastHubWhatsapp(prefs: android.content.SharedPreferences): String? =
            prefs.getString(KEY_LAST_HUB_WHATSAPP, null)?.takeIf { it.isNotBlank() }
    }
}
