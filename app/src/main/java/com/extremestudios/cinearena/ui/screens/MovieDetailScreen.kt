package com.extremestudios.cinearena.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.extremestudios.cinearena.data.model.Song
import com.extremestudios.cinearena.ui.components.RatingStars
import com.extremestudios.cinearena.ui.components.SongCover

@Composable
fun MovieDetailScreen(
    movie: Song,
    modifier: Modifier = Modifier,
    onWatchTrailer: (String) -> Unit,
    onWatchMovie: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SongCover(
                song = movie,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentScale = ContentScale.Fit
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(movie.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                RatingStars(likes = movie.likes)
                Text("Release: ${movie.releaseYear.ifBlank { "-" }}", style = MaterialTheme.typography.bodyMedium)
                Text("Kategori: ${movie.category.ifBlank { movie.genre.ifBlank { "-" } }}", style = MaterialTheme.typography.bodyMedium)
                Text("Durasi: ${movie.duration.ifBlank { "-" }}", style = MaterialTheme.typography.bodyMedium)
                Text("Director: ${movie.director.ifBlank { movie.artist.ifBlank { "-" } }}", style = MaterialTheme.typography.bodyMedium)
                Text("Nama Pemain: ${movie.castName.ifBlank { "-" }}", style = MaterialTheme.typography.bodyMedium)
                Text("Synopsis:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = movie.description.ifBlank { "-" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (movie.trailerUrl.isNotBlank()) {
            item {
                Button(
                    onClick = { onWatchTrailer(movie.trailerUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Watch Trailer")
                }
            }
        }
        if (movie.movieUrl.isNotBlank() || movie.audioUrl.isNotBlank()) {
            item {
                val fullMovieUrl = movie.movieUrl.ifBlank { movie.audioUrl }
                Button(
                    onClick = { onWatchMovie(fullMovieUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Watch Full Movie")
                }
            }
        }
    }
}
