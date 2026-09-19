package com.extremestudios.cinearena.data.repository

import com.extremestudios.cinearena.data.model.Song

class MovieRepository(
    private val api: ApiSongRepository = ApiSongRepository()
) {
    suspend fun fetchMovies(): Result<List<Song>> = api.fetchSongs()
}

