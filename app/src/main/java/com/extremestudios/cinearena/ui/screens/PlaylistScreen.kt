package com.extremestudios.cinearena.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.extremestudios.cinearena.data.model.Playlist
import com.extremestudios.cinearena.data.model.Song

@Composable
fun PlaylistScreen(
    playlists: List<Playlist>,
    songsById: Map<String, Song>,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRemoveSong: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newPlaylistName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Playlist", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newPlaylistName,
                onValueChange = { newPlaylistName = it },
                label = { Text("Nama playlist baru") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        onCreatePlaylist(newPlaylistName.trim())
                        newPlaylistName = ""
                    }
                }
            ) { Text("Buat") }
        }

        LazyColumn(contentPadding = PaddingValues(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(playlists, key = { it.id }) { playlist ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    renameTarget = playlist
                                    renameText = playlist.name
                                }) { Text("Rename") }
                                TextButton(onClick = { onDeletePlaylist(playlist.id) }) { Text("Delete") }
                            }
                        }
                        if (playlist.songIds.isEmpty()) {
                            Text("Belum ada film di playlist ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            playlist.songIds.forEach { songId ->
                                val song = songsById[songId]
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (song == null) {
                                            Text("Unavailable Song", fontWeight = FontWeight.SemiBold)
                                            Text("songId: $songId", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            Text(song.title, fontWeight = FontWeight.SemiBold)
                                            Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    TextButton(onClick = { onRemoveSong(playlist.id, songId) }) { Text("Hapus") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nama playlist") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenamePlaylist(target.id, renameText.trim())
                        renameTarget = null
                    }
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Batal") }
            }
        )
    }
}
