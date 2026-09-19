package com.extremestudios.cinearena.data.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val category: String = "",
    val duration: String = "",
    val director: String = "",
    val castName: String = "",
    val description: String,
    val coverUrl: String,
    val trailerUrl: String = "",
    val movieUrl: String = "",
    val audioUrl: String,
    val uploadedAt: String,
    val status: String,
    val featured: Boolean = false,
    val views: Int = 0,
    val likes: Int = 0,
    val alreadyLiked: Boolean = false,
    val releaseYear: String = "",
    val albumName: String = "",
    val trackNumber: Int? = null
)
