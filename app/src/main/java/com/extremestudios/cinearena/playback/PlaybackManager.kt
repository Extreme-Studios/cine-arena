@file:OptIn(UnstableApi::class)

package com.extremestudios.cinearena.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import com.extremestudios.cinearena.data.model.Song
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackMode {
    NORMAL, SHUFFLE, REPEAT_ONE, REPEAT_ALL;

    fun next(): PlaybackMode = when (this) {
        NORMAL -> SHUFFLE
        SHUFFLE -> REPEAT_ONE
        REPEAT_ONE -> REPEAT_ALL
        REPEAT_ALL -> NORMAL
    }
}

object PlaybackManager {
    private const val TAG = "PlaybackManager"
    private val targetBandFrequencies = listOf(60_000, 230_000, 910_000, 3_000_000, 14_000_000)

    private var applicationContext: Context? = null
    private var player: ExoPlayer? = null
    private var listener: Player.Listener? = null
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var mappedBands: List<Short> = emptyList()

    private var queue: List<Song> = emptyList()
    private var currentIndex: Int = -1

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _mode = MutableStateFlow(PlaybackMode.NORMAL)
    val mode = _mode.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId = _audioSessionId.asStateFlow()

    private val _hasPrevious = MutableStateFlow(false)
    val hasPrevious = _hasPrevious.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext = _hasNext.asStateFlow()

    private val _equalizerSettings = MutableStateFlow(EqualizerSettings())
    val equalizerSettings = _equalizerSettings.asStateFlow()

    private val _equalizerSupported = MutableStateFlow(true)
    val equalizerSupported = _equalizerSupported.asStateFlow()

    private val _autoVolumeSupported = MutableStateFlow(true)
    val autoVolumeSupported = _autoVolumeSupported.asStateFlow()

    private val _audioEffectMessage = MutableStateFlow<String?>(null)
    val audioEffectMessage = _audioEffectMessage.asStateFlow()

    fun init(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
            _equalizerSettings.value = PlaybackSettingsStore.load(context.applicationContext)
        }
        if (player != null) return

        val cacheDataSourceFactory = MediaCacheManager.getCacheDataSourceFactory(context.applicationContext)
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                        .build()
                )
                .build()
        }
        val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                reapplyAudioEffectsIfReady()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                _durationMs.value = (player?.duration ?: 0L).coerceAtLeast(0L)
                if (playbackState == Player.STATE_READY) {
                    reapplyAudioEffectsIfReady()
                }
                if (playbackState == Player.STATE_ENDED) {
                    handleSongEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _error.value = error.message ?: "Streaming gagal diputar."
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                _audioSessionId.value = audioSessionId
                attachAudioEffects(audioSessionId)
            }
        }
        listener = playerListener
        player?.addListener(playerListener)
        reapplyAudioEffectsIfReady()
    }

    fun tick() {
        _positionMs.value = (player?.currentPosition ?: 0L).coerceAtLeast(0L)
        _durationMs.value = (player?.duration ?: 0L).coerceAtLeast(0L)
    }

    fun setQueueAndPlay(songQueue: List<Song>, startIndex: Int, autoPlay: Boolean = true) {
        queue = songQueue.filter { it.audioUrl.isNotBlank() }
        if (queue.isEmpty()) {
            _error.value = "Belum ada film yang bisa diputar."
            updateSkipState()
            return
        }
        currentIndex = startIndex.coerceIn(0, queue.lastIndex)
        playCurrent(autoPlay)
    }

    fun playSong(songQueue: List<Song>, song: Song, autoPlay: Boolean = true) {
        val playableQueue = songQueue.filter { it.audioUrl.isNotBlank() }
        val idx = playableQueue.indexOfFirst { it.id == song.id }
        if (idx < 0) {
            _error.value = "Film ini belum memiliki file media."
            return
        }
        setQueueAndPlay(playableQueue, idx, autoPlay)
    }

    fun togglePlayPause() {
        val activePlayer = player ?: return
        if (_currentSong.value == null && queue.isNotEmpty()) {
            playCurrent(autoPlay = true)
            return
        }
        if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play()
    }

    fun seekBy(deltaMs: Long) {
        val activePlayer = player ?: return
        val duration = activePlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val newPosition = (activePlayer.currentPosition + deltaMs).coerceIn(0L, duration)
        activePlayer.seekTo(newPosition)
        tick()
    }

    fun seekTo(positionMs: Long) {
        val activePlayer = player ?: return
        val duration = activePlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = positionMs.coerceIn(0L, duration)
        activePlayer.seekTo(target)
        _positionMs.value = target
        _durationMs.value = (activePlayer.duration).coerceAtLeast(0L)
    }

    fun previous() {
        if (queue.isEmpty()) return
        currentIndex = when (_mode.value) {
            PlaybackMode.SHUFFLE -> queue.indices.random()
            PlaybackMode.REPEAT_ALL -> if (currentIndex <= 0) queue.lastIndex else currentIndex - 1
            else -> if (currentIndex > 0) currentIndex - 1 else currentIndex
        }
        playCurrent(autoPlay = true)
    }

    fun next() {
        if (queue.isEmpty()) return
        currentIndex = when (_mode.value) {
            PlaybackMode.SHUFFLE -> queue.indices.random()
            PlaybackMode.REPEAT_ONE -> currentIndex
            PlaybackMode.REPEAT_ALL -> if (currentIndex >= queue.lastIndex) 0 else currentIndex + 1
            PlaybackMode.NORMAL -> if (currentIndex < queue.lastIndex) currentIndex + 1 else currentIndex
        }
        playCurrent(autoPlay = true)
    }

    fun cycleMode() {
        _mode.value = _mode.value.next()
        updateSkipState()
    }

    fun setMode(mode: PlaybackMode) {
        _mode.value = mode
        updateSkipState()
    }

    fun repeatIconRes(mode: PlaybackMode): Int = when (mode) {
        PlaybackMode.NORMAL -> android.R.drawable.ic_menu_close_clear_cancel
        PlaybackMode.REPEAT_ONE -> android.R.drawable.ic_menu_revert
        PlaybackMode.REPEAT_ALL -> android.R.drawable.ic_menu_rotate
        PlaybackMode.SHUFFLE -> android.R.drawable.ic_menu_sort_by_size
    }

    fun updateEqualizerBand(index: Int, valueDb: Float) {
        updateAudioSettings(_equalizerSettings.value.withBand(index, valueDb), applyEqualizer = true, applyLoudness = false)
    }

    fun applyPreset(preset: EqualizerPreset) {
        updateAudioSettings(
            _equalizerSettings.value.withLevels(preset.levels),
            applyEqualizer = true,
            applyLoudness = false
        )
    }

    fun setAutoVolumeEnabled(enabled: Boolean) {
        updateAudioSettings(
            _equalizerSettings.value.copy(autoVolumeEnabled = enabled),
            applyEqualizer = false,
            applyLoudness = true
        )
    }

    fun setAutoVolumeStrength(strength: AutoVolumeStrength) {
        updateAudioSettings(
            _equalizerSettings.value.copy(autoVolumeStrength = strength),
            applyEqualizer = false,
            applyLoudness = true
        )
    }

    fun clearAudioEffectMessage() {
        _audioEffectMessage.value = null
    }

    fun modeIconRes(): Int = when (_mode.value) {
        PlaybackMode.NORMAL -> android.R.drawable.ic_menu_more
        PlaybackMode.SHUFFLE -> android.R.drawable.ic_menu_sort_by_size
        PlaybackMode.REPEAT_ONE -> android.R.drawable.ic_menu_revert
        PlaybackMode.REPEAT_ALL -> android.R.drawable.ic_menu_rotate
    }

    private fun playCurrent(autoPlay: Boolean) {
        val song = queue.getOrNull(currentIndex) ?: return
        val activePlayer = player ?: return
        _currentSong.value = song
        _error.value = null
        Log.d(TAG, "Playing with cache data source for url=${song.audioUrl}")
        activePlayer.setMediaItem(MediaItem.fromUri(song.audioUrl))
        activePlayer.prepare()
        activePlayer.playWhenReady = autoPlay
        if (autoPlay) activePlayer.play()
        reapplyAudioEffectsIfReady()
        updateSkipState()
    }

    private fun handleSongEnded() {
        when (_mode.value) {
            PlaybackMode.REPEAT_ONE -> playCurrent(autoPlay = true)
            PlaybackMode.REPEAT_ALL, PlaybackMode.SHUFFLE -> next()
            PlaybackMode.NORMAL -> {
                if (currentIndex < queue.lastIndex) {
                    next()
                } else {
                    _isPlaying.value = false
                }
            }
        }
    }

    private fun updateSkipState() {
        val canLoop = queue.size > 1 && (_mode.value == PlaybackMode.REPEAT_ALL || _mode.value == PlaybackMode.SHUFFLE)
        _hasPrevious.value = queue.isNotEmpty() && (currentIndex > 0 || canLoop)
        _hasNext.value = queue.isNotEmpty() && (currentIndex < queue.lastIndex || canLoop || _mode.value == PlaybackMode.REPEAT_ONE)
    }

    private fun updateAudioSettings(
        settings: EqualizerSettings,
        applyEqualizer: Boolean,
        applyLoudness: Boolean
    ) {
        _equalizerSettings.value = settings
        applicationContext?.let { PlaybackSettingsStore.save(it, settings) }
        if (applyEqualizer) {
            applyEqualizerSettings()
        }
        if (applyLoudness) {
            applyAutoVolumeSettings()
        }
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        releaseAudioEffects()
        if (audioSessionId <= 0) return
        attachEqualizer(audioSessionId)
        attachAutoVolume(audioSessionId)
    }

    private fun attachEqualizer(audioSessionId: Int) {
        try {
            val audioEqualizer = Equalizer(0, audioSessionId).apply { enabled = true }
            equalizer = audioEqualizer
            mappedBands = mapBands(audioEqualizer)
            _equalizerSupported.value = true
            applyEqualizerSettings()
        } catch (_: Throwable) {
            equalizer = null
            mappedBands = emptyList()
            _equalizerSupported.value = false
            _audioEffectMessage.value = "Equalizer tidak didukung di perangkat ini"
        }
    }

    private fun attachAutoVolume(audioSessionId: Int) {
        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
            _autoVolumeSupported.value = true
            applyAutoVolumeSettings()
        } catch (_: Throwable) {
            loudnessEnhancer = null
            _autoVolumeSupported.value = false
            if (_equalizerSettings.value.autoVolumeEnabled) {
                _audioEffectMessage.value = "Auto Volume tidak didukung di perangkat ini"
            }
        }
    }

    private fun applyEqualizerSettings() {
        val audioEqualizer = equalizer ?: return
        val minLevel = audioEqualizer.bandLevelRange.first().toInt()
        val maxLevel = audioEqualizer.bandLevelRange.last().toInt()
        val levels = _equalizerSettings.value.bandLevels()
        mappedBands.forEachIndexed { index, band ->
            val desiredLevelMb = (levels.getOrElse(index) { 0f } * 100f).roundToInt().coerceIn(minLevel, maxLevel)
            runCatching {
                audioEqualizer.setBandLevel(band, desiredLevelMb.toShort())
            }.onFailure {
                _audioEffectMessage.value = "Equalizer tidak didukung di perangkat ini"
            }
        }
        runCatching { audioEqualizer.enabled = true }
    }

    private fun applyAutoVolumeSettings() {
        val enhancer = loudnessEnhancer ?: return
        val settings = _equalizerSettings.value
        runCatching {
            enhancer.setTargetGain(settings.autoVolumeStrength.gainMb)
            enhancer.enabled = settings.autoVolumeEnabled
        }.onFailure {
            _autoVolumeSupported.value = false
            if (settings.autoVolumeEnabled) {
                _audioEffectMessage.value = "Auto Volume tidak didukung di perangkat ini"
            }
        }
    }

    private fun mapBands(audioEqualizer: Equalizer): List<Short> {
        val availableBands = (0 until audioEqualizer.numberOfBands.toInt()).map { it.toShort() }.toMutableList()
        return targetBandFrequencies.map { target ->
            val selectedBand = availableBands.minByOrNull { band ->
                abs(audioEqualizer.getCenterFreq(band) - target)
            } ?: availableBands.firstOrNull() ?: 0
            availableBands.remove(selectedBand)
            selectedBand
        }
    }

    private fun reapplyAudioEffectsIfReady() {
        val audioSessionId = player?.audioSessionId ?: _audioSessionId.value
        if (audioSessionId > 0) {
            attachAudioEffects(audioSessionId)
        }
    }

    private fun releaseAudioEffects() {
        runCatching { equalizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        loudnessEnhancer = null
        mappedBands = emptyList()
    }
}
