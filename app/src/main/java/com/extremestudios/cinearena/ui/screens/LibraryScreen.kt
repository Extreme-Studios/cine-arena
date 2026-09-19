package com.extremestudios.cinearena.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.extremestudios.cinearena.data.model.Playlist
import com.extremestudios.cinearena.data.model.Song
import com.extremestudios.cinearena.ui.components.SongListItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    songs: List<Song>,
    genres: List<String>,
    playlists: List<Playlist>,
    errorMessage: String?,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    canLike: Boolean = false,
    onSongClick: (Song) -> Unit,
    onArtistClick: (String) -> Unit,
    onAddSongToPlaylist: (playlistId: String, songId: String) -> Unit,
    onLoginForLike: () -> Unit = {},
    onLikeFilm: (Song) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("Semua") }
    var songToAdd by remember { mutableStateOf<Song?>(null) }
    val filteredSongs = songs.filter { song ->
        val matchesQuery = query.isBlank() || song.title.contains(query, true) || song.artist.contains(query, true)
        val matchesGenre = selectedGenre == "Semua" || song.genre == selectedGenre
        matchesQuery && matchesGenre
    }
    val showLibraryEmptyState = songs.isEmpty() && !isLoading
    val showFilteredEmptyState = songs.isNotEmpty() && filteredSongs.isEmpty()

    LaunchedEffect(songs.size, filteredSongs.size, selectedGenre, query, showLibraryEmptyState, showFilteredEmptyState) {
        Log.d(
            "LibraryScreen",
            "songsCount=${songs.size}, filteredCount=${filteredSongs.size}, emptyStateShown=${showLibraryEmptyState || showFilteredEmptyState}"
        )
        if (showLibraryEmptyState || showFilteredEmptyState) {
            Log.d("LibraryScreen", "Rendering empty film state")
        }
    }

    val listState = rememberLazyListState()
    val dragAccum = remember { floatArrayOf(0f) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(isRefreshing) {
                detectVerticalDragGestures { _, dragAmount ->
                    val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    if (!isRefreshing && atTop && dragAmount > 0f) {
                        dragAccum[0] += dragAmount
                        if (dragAccum[0] > 120f) {
                            dragAccum[0] = 0f
                            onRefresh()
                        }
                    } else if (dragAmount < 0f) {
                        dragAccum[0] = 0f
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    text = "Film Library",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing
                ) {
                    Text(if (isRefreshing) "Refreshing..." else "Refresh")
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cari film atau kreator...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { genre ->
                        FilterChip(
                            selected = selectedGenre == genre,
                            onClick = {
                                selectedGenre = genre
                            },
                            label = { Text(genre) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = null,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (showLibraryEmptyState) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Library masih kosong.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 32.dp)
                            )
                            errorMessage?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                items(filteredSongs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        onClick = { onSongClick(song) },
                        onArtistClick = onArtistClick,
                        onAddToPlaylist = { songToAdd = song },
                        canLike = canLike,
                        onLoginForLike = onLoginForLike,
                        onLike = onLikeFilm
                    )
                }
                if (showFilteredEmptyState) {
                    item {
                        Text(
                            text = "Tidak ada film yang cocok untuk pencarian atau kategori ini.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 32.dp)
                        )
                    }
                }
            }
        }
    }

    songToAdd?.let { selectedSong ->
        AlertDialog(
            onDismissRequest = { songToAdd = null },
            title = { Text("Tambah ke Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (playlists.isEmpty()) {
                        Text("Belum ada playlist. Tambahkan film nanti saat fitur playlist aktif lagi.")
                    } else {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    onAddSongToPlaylist(playlist.id, selectedSong.id)
                                    songToAdd = null
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
                TextButton(onClick = { songToAdd = null }) { Text("Tutup") }
            }
        )
    }
}
