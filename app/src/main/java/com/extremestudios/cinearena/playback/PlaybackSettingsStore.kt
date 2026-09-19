package com.extremestudios.cinearena.playback

import android.content.Context

enum class AutoVolumeStrength(val gainMb: Int) {
    LOW(700),
    MEDIUM(1200),
    HIGH(1800);

    companion object {
        fun fromName(value: String?): AutoVolumeStrength {
            return entries.firstOrNull { it.name == value } ?: MEDIUM
        }
    }
}

enum class EqualizerPreset(val label: String, val levels: List<Float>) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    BASS_BOOST("Bass Boost", listOf(6f, 4f, 1f, -1f, -2f)),
    VOCAL("Vocal", listOf(-2f, 1f, 4f, 4f, 2f)),
    ROCK("Rock", listOf(5f, 3f, 1f, 2f, 4f)),
    TREBLE_BOOST("Treble Boost", listOf(-3f, -1f, 1f, 5f, 6f))
}

data class EqualizerSettings(
    val low: Float = 0f,
    val lowMid: Float = 0f,
    val mid: Float = 0f,
    val highMid: Float = 0f,
    val high: Float = 0f,
    val autoVolumeEnabled: Boolean = false,
    val autoVolumeStrength: AutoVolumeStrength = AutoVolumeStrength.MEDIUM
) {
    fun bandLevels(): List<Float> = listOf(low, lowMid, mid, highMid, high)

    fun withBand(index: Int, value: Float): EqualizerSettings {
        val clamped = value.coerceIn(-12f, 12f)
        return when (index) {
            0 -> copy(low = clamped)
            1 -> copy(lowMid = clamped)
            2 -> copy(mid = clamped)
            3 -> copy(highMid = clamped)
            4 -> copy(high = clamped)
            else -> this
        }
    }

    fun withLevels(levels: List<Float>): EqualizerSettings {
        return copy(
            low = levels.getOrElse(0) { 0f }.coerceIn(-12f, 12f),
            lowMid = levels.getOrElse(1) { 0f }.coerceIn(-12f, 12f),
            mid = levels.getOrElse(2) { 0f }.coerceIn(-12f, 12f),
            highMid = levels.getOrElse(3) { 0f }.coerceIn(-12f, 12f),
            high = levels.getOrElse(4) { 0f }.coerceIn(-12f, 12f)
        )
    }
}

object PlaybackSettingsStore {
    private const val PREFS_NAME = "playback_settings"
    private const val KEY_LOW = "eq_low"
    private const val KEY_LOW_MID = "eq_low_mid"
    private const val KEY_MID = "eq_mid"
    private const val KEY_HIGH_MID = "eq_high_mid"
    private const val KEY_HIGH = "eq_high"
    private const val KEY_AUTO_VOLUME_ENABLED = "auto_volume_enabled"
    private const val KEY_AUTO_VOLUME_STRENGTH = "auto_volume_strength"

    fun load(context: Context): EqualizerSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return EqualizerSettings(
            low = prefs.getFloat(KEY_LOW, 0f),
            lowMid = prefs.getFloat(KEY_LOW_MID, 0f),
            mid = prefs.getFloat(KEY_MID, 0f),
            highMid = prefs.getFloat(KEY_HIGH_MID, 0f),
            high = prefs.getFloat(KEY_HIGH, 0f),
            autoVolumeEnabled = prefs.getBoolean(KEY_AUTO_VOLUME_ENABLED, false),
            autoVolumeStrength = AutoVolumeStrength.fromName(
                prefs.getString(KEY_AUTO_VOLUME_STRENGTH, AutoVolumeStrength.MEDIUM.name)
            )
        )
    }

    fun save(context: Context, settings: EqualizerSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_LOW, settings.low)
            .putFloat(KEY_LOW_MID, settings.lowMid)
            .putFloat(KEY_MID, settings.mid)
            .putFloat(KEY_HIGH_MID, settings.highMid)
            .putFloat(KEY_HIGH, settings.high)
            .putBoolean(KEY_AUTO_VOLUME_ENABLED, settings.autoVolumeEnabled)
            .putString(KEY_AUTO_VOLUME_STRENGTH, settings.autoVolumeStrength.name)
            .apply()
    }
}
