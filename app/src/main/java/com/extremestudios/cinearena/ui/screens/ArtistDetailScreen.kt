package com.extremestudios.cinearena.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.extremestudios.cinearena.data.model.Song
import com.extremestudios.cinearena.ui.components.SongCover
import com.extremestudios.cinearena.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    songs: List<Song>,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (String) -> Unit
) {
    val targetArtist = artistName.trim()
    val artistSongs = remember(songs, targetArtist) {
        songs.filter { it.artist.trim().equals(targetArtist, ignoreCase = true) }
    }
    val albumGroups = remember(artistSongs) {
        artistSongs
            .filter { it.albumName.isNotBlank() }
            .groupBy { it.albumName.trim() }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    val singles = remember(artistSongs) {
        artistSongs
            .filter { it.albumName.isBlank() }
            .sortedWith(compareByDescending<Song> { it.uploadedAt }.thenBy { it.title.lowercase() })
    }

    LaunchedEffect(targetArtist, artistSongs.size, albumGroups.size, singles.size) {
        Log.d(
            "ArtistDetailScreen",
            "artist=$targetArtist, songsCount=${artistSongs.size}, albums=${albumGroups.size}, singles=${singles.size}, emptyStateShown=${artistSongs.isEmpty()}"
        )
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(targetArtist, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(android.R.drawable.ic_menu_revert), contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        Text(
            text = "Total film: ${artistSongs.size}",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        if (artistSongs.isEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Belum ada film dari kreator ini",
                    style = MaterialTheme.typography.bodyLarge
                )
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (albumGroups.isNotEmpty()) {
                    item {
                        BandDetailSectionTitle("Koleksi")
                    }
                    items(albumGroups.entries.toList(), key = { it.key }) { (albumName, albumSongs) ->
                        AlbumListItem(
                            albumName = albumName,
                            songCount = albumSongs.size,
                            coverSong = albumSongs.firstOrNull(),
                            onClick = { onAlbumClick(albumName) }
                        )
                    }
                }

                if (singles.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        BandDetailSectionTitle("Film Lepas")
                    }
                    items(singles, key = { it.id }) { song ->
                        SongWithReleaseYear(song = song, onSongClick = onSongClick)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    artistName: String,
    albumName: String,
    songs: List<Song>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit
) {
    val albumSongs = remember(songs, artistName, albumName) {
        songs
            .filter {
                it.artist.trim().equals(artistName.trim(), ignoreCase = true) &&
                    it.albumName.trim().equals(albumName.trim(), ignoreCase = true)
            }
            .sortedWith(
                compareBy<Song> { it.trackNumber ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
    }

    LaunchedEffect(artistName, albumName, albumSongs.size) {
        Log.d("AlbumDetailScreen", "artist=$artistName, album=$albumName, songsCount=${albumSongs.size}")
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = {
                Column {
                    Text(albumName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(android.R.drawable.ic_menu_revert), contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "${albumSongs.size} film",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (albumSongs.isEmpty()) {
                item {
                    Text(
                        text = "Belum ada film di album ini",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                items(albumSongs, key = { it.id }) { song ->
                    TrackListItem(
                        song = song,
                        onClick = { onSongClick(song, albumSongs) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BandDetailSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AlbumListItem(
    albumName: String,
    songCount: Int,
    coverSong: Song?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (coverSong != null) {
                SongCover(song = coverSong, modifier = Modifier.size(64.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$songCount film",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SongWithReleaseYear(
    song: Song,
    onSongClick: (Song) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SongListItem(song = song, onClick = { onSongClick(song) })
        if (song.releaseYear.isNotBlank()) {
            Text(
                text = "Rilis: ${song.releaseYear}",
                modifier = Modifier.padding(top = 4.dp, start = 8.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun TrackListItem(
    song: Song,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = song.trackNumber?.toString() ?: "-",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        SongCover(song = song, modifier = Modifier.size(54.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.releaseYear.isNotBlank()) {
                Text(
                    text = song.releaseYear,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
