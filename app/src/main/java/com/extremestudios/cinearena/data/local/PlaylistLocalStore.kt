package com.extremestudios.cinearena.data.local

import android.content.Context
import com.extremestudios.cinearena.data.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PlaylistLocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("playlist_store", Context.MODE_PRIVATE)
    private val _playlistsFlow = MutableStateFlow(parsePlaylists(prefs.getString("playlists_json", "").orEmpty()))
    val playlistsFlow: StateFlow<List<Playlist>> = _playlistsFlow.asStateFlow()

    suspend fun createPlaylist(name: String) {
        val updated = _playlistsFlow.value + Playlist(id = UUID.randomUUID().toString(), name = name, songIds = emptyList())
        save(updated)
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        val updated = _playlistsFlow.value.map { if (it.id == playlistId) it.copy(name = newName) else it }
        save(updated)
    }

    suspend fun deletePlaylist(playlistId: String) {
        val updated = _playlistsFlow.value.filterNot { it.id == playlistId }
        save(updated)
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        val updated = _playlistsFlow.value.map { playlist ->
            if (playlist.id == playlistId && songId !in playlist.songIds) {
                playlist.copy(songIds = playlist.songIds + songId)
            } else {
                playlist
            }
        }
        save(updated)
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        val updated = _playlistsFlow.value.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(songIds = playlist.songIds.filterNot { it == songId })
            else playlist
        }
        save(updated)
    }

    private fun save(playlists: List<Playlist>) {
        prefs.edit().putString("playlists_json", serializePlaylists(playlists)).apply()
        _playlistsFlow.value = playlists
    }

    private fun parsePlaylists(raw: String): List<Playlist> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val songsArray = obj.optJSONArray("songIds") ?: JSONArray()
                    val songIds = buildList {
                        for (j in 0 until songsArray.length()) add(songsArray.optString(j))
                    }
                    add(Playlist(id = obj.optString("id"), name = obj.optString("name"), songIds = songIds))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializePlaylists(playlists: List<Playlist>): String {
        val arr = JSONArray()
        playlists.forEach { playlist ->
            arr.put(
                JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("songIds", JSONArray(playlist.songIds))
                }
            )
        }
        return arr.toString()
    }
}
