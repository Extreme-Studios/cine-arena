package com.extremestudios.cinearena.ui.screens
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.extremestudios.cinearena.data.model.Playlist
import com.extremestudios.cinearena.playback.AutoVolumeStrength
import com.extremestudios.cinearena.playback.EqualizerPreset
import com.extremestudios.cinearena.playback.PlaybackMode
import com.extremestudios.cinearena.playback.PlaybackManager
import com.extremestudios.cinearena.ui.components.SongCover
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playlists: List<Playlist>,
    modifier: Modifier = Modifier,
    onOpenLibrary: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAddSongToPlaylist: (playlistId: String, songId: String) -> Unit
) {
    val currentSong by PlaybackManager.currentSong.collectAsState()
    val isPlaying by PlaybackManager.isPlaying.collectAsState()
    val isBuffering by PlaybackManager.isBuffering.collectAsState()
    val positionMs by PlaybackManager.positionMs.collectAsState()
    val durationMs by PlaybackManager.durationMs.collectAsState()
    val mode by PlaybackManager.mode.collectAsState()
    val playerError by PlaybackManager.error.collectAsState()
    val hasPrevious by PlaybackManager.hasPrevious.collectAsState()
    val hasNext by PlaybackManager.hasNext.collectAsState()
    val equalizerSettings by PlaybackManager.equalizerSettings.collectAsState()
    val equalizerSupported by PlaybackManager.equalizerSupported.collectAsState()
    val autoVolumeSupported by PlaybackManager.autoVolumeSupported.collectAsState()
    val audioEffectMessage by PlaybackManager.audioEffectMessage.collectAsState()
    var equaliserExpanded by remember { mutableStateOf(true) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var isTimelineDragging by remember { mutableStateOf(false) }
    var timelineDragMs by remember { mutableStateOf(0f) }

    val safeDurationMs = durationMs.coerceAtLeast(1L)
    val effectivePositionMs = if (isTimelineDragging) timelineDragMs.toLong() else positionMs
    val positionSeconds = (effectivePositionMs / 1000L).toInt()
    val durationSeconds = (durationMs / 1000L).toInt()

    LaunchedEffect(currentSong?.id) {
        isTimelineDragging = false
        timelineDragMs = 0f
    }

    LaunchedEffect(positionMs, durationMs, isTimelineDragging) {
        if (!isTimelineDragging) {
            timelineDragMs = positionMs.coerceIn(0L, safeDurationMs).toFloat()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)
                )
            )
    ) {
        if (currentSong == null) {
            EmptyNowPlayingState(onOpenLibrary = onOpenLibrary)
            return@Box
        }

        val song = currentSong ?: return@Box
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                TopAppBar(
                    title = {
                        Text(
                            text = "Sedang Diputar",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
            item {
                Surface(
                    modifier = Modifier.size(280.dp),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 16.dp
                ) {
                    SongCover(song = song, modifier = Modifier.fillMaxSize())
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onArtistClick(song.artist) }
                    )
                    Spacer(Modifier.height(12.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(song.genre) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            labelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    RepeatModeRow(
                        currentMode = mode,
                        onModeSelected = { targetMode -> PlaybackManager.setMode(targetMode) },
                        onPlaylistClick = { showPlaylistDialog = true }
                    )
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = timelineDragMs.coerceIn(0f, safeDurationMs.toFloat()),
                        onValueChange = { newValue ->
                            isTimelineDragging = true
                            timelineDragMs = newValue.coerceIn(0f, safeDurationMs.toFloat())
                        },
                        onValueChangeFinished = {
                            PlaybackManager.seekTo(timelineDragMs.toLong())
                            isTimelineDragging = false
                        },
                        valueRange = 0f..safeDurationMs.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )
                    LinearProgressIndicator(
                        progress = {
                            (positionSeconds.coerceIn(0, durationSeconds.coerceAtLeast(1))).toFloat() /
                                durationSeconds.coerceAtLeast(1).toFloat()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(positionSeconds), style = MaterialTheme.typography.labelMedium)
                        Text(formatDuration(durationSeconds), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { PlaybackManager.previous() }, enabled = hasPrevious) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_media_previous),
                            contentDescription = "Previous",
                            modifier = Modifier.size(32.dp),
                            tint = if (hasPrevious) Color.White else Color.Gray
                        )
                    }
                    IconButton(onClick = { PlaybackManager.seekBy(-5_000L) }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_media_rew),
                            contentDescription = "Rewind",
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { PlaybackManager.togglePlayPause() },
                        enabled = song.audioUrl.isNotBlank(),
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) android.R.drawable.ic_media_pause
                                else android.R.drawable.ic_media_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { PlaybackManager.seekBy(5_000L) }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_media_ff),
                            contentDescription = "Forward",
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { PlaybackManager.next() }, enabled = hasNext) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_media_next),
                            contentDescription = "Next",
                            modifier = Modifier.size(32.dp),
                            tint = if (hasNext) Color.White else Color.Gray
                        )
                    }
                }
            }
            if (isBuffering) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Buffering...")
                    }
                }
            }
            playerError?.let { errorText ->
                item {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            audioEffectMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { PlaybackManager.clearAudioEffectMessage() }
                    )
                }
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { equaliserExpanded = !equaliserExpanded },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Graphic Equaliser",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painter = painterResource(
                                if (equaliserExpanded) android.R.drawable.arrow_up_float
                                else android.R.drawable.arrow_down_float
                            ),
                            contentDescription = null
                        )
                    }
                }
            }
            if (equaliserExpanded) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!equalizerSupported) {
                            Text(
                                text = "Equalizer tidak didukung di perangkat ini",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            EqualizerPresetRow(onPresetSelected = { PlaybackManager.applyPreset(it) })
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val values = equalizerSettings.bandLevels()
                                GraphicEqualizerBar("60Hz", values[0]) { PlaybackManager.updateEqualizerBand(0, it) }
                                GraphicEqualizerBar("230Hz", values[1]) { PlaybackManager.updateEqualizerBand(1, it) }
                                GraphicEqualizerBar("910Hz", values[2]) { PlaybackManager.updateEqualizerBand(2, it) }
                                GraphicEqualizerBar("3kHz", values[3]) { PlaybackManager.updateEqualizerBand(3, it) }
                                GraphicEqualizerBar("14kHz", values[4]) { PlaybackManager.updateEqualizerBand(4, it) }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auto Volume",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Switch(
                                checked = equalizerSettings.autoVolumeEnabled,
                                onCheckedChange = { PlaybackManager.setAutoVolumeEnabled(it) },
                                enabled = autoVolumeSupported,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        if (!autoVolumeSupported) {
                            Text(
                                text = "Auto Volume tidak didukung di perangkat ini",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        AutoVolumeStrengthRow(
                            current = equalizerSettings.autoVolumeStrength,
                            onSelected = { PlaybackManager.setAutoVolumeStrength(it) }
                        )
                    }
                }
            }
        }

        if (showPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showPlaylistDialog = false },
                title = { Text("Tambah ke Playlist") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (playlists.isEmpty()) {
                            Text("Belum ada playlist. Buat playlist dulu di tab Playlist.")
                        } else {
                            playlists.forEach { playlist ->
                                TextButton(
                                    onClick = {
                                        onAddSongToPlaylist(playlist.id, song.id)
                                        showPlaylistDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(playlist.name)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlaylistDialog = false }) {
                        Text("Tutup")
                    }
                }
            )
        }
    }
}

@Composable
private fun RepeatModeRow(
    currentMode: PlaybackMode,
    onModeSelected: (PlaybackMode) -> Unit,
    onPlaylistClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            PlaybackMode.NORMAL to "Repeat Off",
            PlaybackMode.REPEAT_ONE to "Repeat One",
            PlaybackMode.REPEAT_ALL to "Repeat All"
        ).forEach { (mode, label) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (currentMode == mode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.clickable { onModeSelected(mode) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(PlaybackManager.repeatIconRes(mode)),
                        contentDescription = label,
                        tint = if (currentMode == mode) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            Color.White.copy(alpha = 0.9f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.clickable(onClick = onPlaylistClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "Tambah ke playlist",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyNowPlayingState(onOpenLibrary: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Belum ada film diputar",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Pilih film dari Film Library untuk mulai memutar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onOpenLibrary) {
            Text("Pilih Film")
        }
    }
}

@Composable
private fun EqualizerPresetRow(onPresetSelected: (EqualizerPreset) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EqualizerPreset.entries.forEach { preset ->
            FilterChip(
                selected = false,
                onClick = { onPresetSelected(preset) },
                label = { Text(preset.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun AutoVolumeStrengthRow(
    current: AutoVolumeStrength,
    onSelected: (AutoVolumeStrength) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AutoVolumeStrength.entries.forEach { strength ->
            FilterChip(
                selected = current == strength,
                onClick = { onSelected(strength) },
                label = { Text(strength.name.lowercase().replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun GraphicEqualizerBar(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val min = -12f
    val max = 12f
    val range = max - min
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(50.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .width(36.dp)
                .height(190.dp)
                .pointerInput(value) {
                    fun updateFromY(y: Float) {
                        val thumbRadius = 12.dp.toPx()
                        val top = thumbRadius
                        val bottom = size.height.toFloat() - thumbRadius
                        val clampedY = y.coerceIn(top, bottom)
                        val normalized = 1f - ((clampedY - top) / (bottom - top))
                        val newValue = (min + normalized * range).coerceIn(min, max)
                        onValueChange(newValue)
                    }

                    detectTapGestures(onTap = { offset -> updateFromY(offset.y) })
                }
                .pointerInput(value) {
                    fun updateFromY(y: Float) {
                        val thumbRadius = 12.dp.toPx()
                        val top = thumbRadius
                        val bottom = size.height.toFloat() - thumbRadius
                        val clampedY = y.coerceIn(top, bottom)
                        val normalized = 1f - ((clampedY - top) / (bottom - top))
                        val newValue = (min + normalized * range).coerceIn(min, max)
                        onValueChange(newValue)
                    }

                    detectDragGestures(
                        onDragStart = { offset -> updateFromY(offset.y) },
                        onDrag = { change, _ -> updateFromY(change.position.y) }
                    )
                }
        ) {
            val normalized = ((value - min) / range).coerceIn(0f, 1f)

            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
            ) {
                val thumbRadius = 12.dp.toPx()
                val trackWidth = 8.dp.toPx()
                val trackTop = thumbRadius
                val trackBottom = size.height - thumbRadius
                val trackHeight = trackBottom - trackTop
                val centerX = size.width / 2f
                val thumbY = trackTop + (1f - normalized) * trackHeight
                val zeroY = trackTop + 0.5f * trackHeight
                drawLine(
                    color = Color(0x66FFFFFF),
                    start = Offset(centerX - 14.dp.toPx(), zeroY),
                    end = Offset(centerX + 14.dp.toPx(), zeroY),
                    strokeWidth = 2.dp.toPx()
                )
                drawRoundRect(
                    color = Color(0xFF4B4B4B),
                    topLeft = Offset(centerX - trackWidth / 2f, trackTop),
                    size = Size(trackWidth, trackHeight),
                    cornerRadius = CornerRadius(trackWidth, trackWidth)
                )
                drawRoundRect(
                    color = Color(0xFFA100FF),
                    topLeft = Offset(centerX - trackWidth / 2f, thumbY),
                    size = Size(trackWidth, trackBottom - thumbY),
                    cornerRadius = CornerRadius(trackWidth, trackWidth)
                )
                drawCircle(
                    color = Color(0xFFA100FF),
                    radius = thumbRadius,
                    center = Offset(centerX, thumbY)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "${value.roundToInt()} dB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0:00"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
