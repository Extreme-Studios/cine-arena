package com.extremestudios.cinearena.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.extremestudios.cinearena.data.model.Song
import com.extremestudios.cinearena.data.repository.MovieRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MovieUiState(
    val movies: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MovieViewModel(
    private val repository: MovieRepository = MovieRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(MovieUiState(isLoading = true))
    val uiState: StateFlow<MovieUiState> = _uiState.asStateFlow()

    fun loadMovies() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.fetchMovies()
                .onSuccess { movies ->
                    _uiState.value = MovieUiState(movies = movies, isLoading = false, errorMessage = null)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Gagal memuat data film."
                    )
                }
        }
    }
}

