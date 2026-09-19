package com.extremestudios.cinearena.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import com.extremestudios.cinearena.data.model.Song
import com.extremestudios.cinearena.ui.components.MoviePosterCard
import com.extremestudios.cinearena.ui.components.SongCover

@Composable
fun HomeScreen(
    songs: List<Song>,
    artists: List<String>,
    errorMessage: String?,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onSongClick: (Song) -> Unit,
    onArtistClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val dragAccum = remember { floatArrayOf(0f) }
    val featured = remember(songs) { songs.firstOrNull() }
    val featuredMovies = remember(songs) { songs.filter { it.featured } }
    val newcomerMovies = remember(songs) { songs.take(10) }
    val newcomerIds = remember(newcomerMovies) { newcomerMovies.map { it.id }.toSet() }
    val categories = remember(songs) { listOf("Semua") + songs.map { it.category }.filter { it.isNotBlank() }.distinct().sorted() }
    var selectedCategory by remember(songs) { mutableStateOf("Semua") }
    val filteredMovies = remember(songs, selectedCategory) {
        val baseList = songs.filterNot { newcomerIds.contains(it.id) }
        if (selectedCategory == "Semua") baseList else baseList.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }
    val showEmptyState = songs.isEmpty() && !isLoading

    LaunchedEffect(showEmptyState, songs.size) {
        Log.d("HomeScreen", "filmsCount=${songs.size}, emptyStateShown=$showEmptyState")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                StreamingHero(
                    featured = featured,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onPlay = { featured?.let(onSongClick) }
                )
            }
            if (showEmptyState) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Katalog presentasi belum tersedia.",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
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
            item {
                PosterRail(
                    title = "Pendatang Baru",
                    films = newcomerMovies,
                    onSongClick = onSongClick
                )
            }
            item {
                PosterRail(
                    title = "Featured Movies",
                    films = featuredMovies,
                    onSongClick = onSongClick
                )
            }
            item {
                CategoryFilterRow(
                    categories = categories,
                    selected = selectedCategory,
                    onSelected = { selectedCategory = it }
                )
            }
            item {
                PosterRail(
                    title = "Daftar Film",
                    films = filteredMovies,
                    onSongClick = onSongClick
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(categories, key = { it }) { category ->
            val isSelected = category == selected
            Surface(
                modifier = Modifier.clickable { onSelected(category) },
                color = if (isSelected) Color(0xFFE9C277) else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = category,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (isSelected) Color.Black else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StreamingHero(
    featured: Song?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(Color.Black)
    ) {
        if (featured != null) {
            SongCover(song = featured, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC050505), Color(0xFF050505))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 20.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh Home",
                        tint = Color.White
                    )
                }
            }
            Text(
                text = "CINE ARENA",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Where Stories Begin...",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFFFA0A9),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PosterRail(
    title: String,
    films: List<Song>,
    onSongClick: (Song) -> Unit
) {
    if (films.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(films, key = { it.id }) { song ->
                MoviePosterCard(song = song, onClick = { onSongClick(song) })
            }
        }
    }
}

@Composable
private fun HomeSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White
    )
}
