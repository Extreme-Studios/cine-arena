package com.extremestudios.cinearena.navigation

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.extremestudios.cinearena.data.local.PortalSessionStore
import com.extremestudios.cinearena.data.local.PlaylistLocalStore
import com.extremestudios.cinearena.data.model.PortalPost
import com.extremestudios.cinearena.data.model.Song
import com.extremestudios.cinearena.data.repository.ApiSongRepository
import com.extremestudios.cinearena.data.repository.PortalRepository
import com.extremestudios.cinearena.data.repository.PlaylistRepository
import com.extremestudios.cinearena.playback.PlaybackManager
import com.extremestudios.cinearena.ui.components.SongCover
import com.extremestudios.cinearena.ui.BannerAdView
import com.extremestudios.cinearena.ui.PORTAL_REWARD_COOLDOWN_MS
import com.extremestudios.cinearena.ui.PORTAL_REWARD_PREFERENCE_NAME
import com.extremestudios.cinearena.ui.REWARD_AD_UNIT_ID
import com.extremestudios.cinearena.ui.WATCH_MOVIE_REWARD_COOLDOWN_KEY
import com.extremestudios.cinearena.ui.rememberRewardedSubmissionGate
import com.extremestudios.cinearena.ui.screens.AboutScreen
import com.extremestudios.cinearena.ui.screens.AlbumDetailScreen
import com.extremestudios.cinearena.ui.screens.ArtistDetailScreen
import com.extremestudios.cinearena.ui.screens.HomeScreen
import com.extremestudios.cinearena.ui.screens.LibraryScreen
import com.extremestudios.cinearena.ui.screens.MovieDetailScreen
import com.extremestudios.cinearena.ui.screens.NowPlayingScreen
import com.extremestudios.cinearena.ui.screens.PortalPosterSubmissionScreen
import com.extremestudios.cinearena.ui.screens.PortalKitaScreen
import com.extremestudios.cinearena.ui.screens.PortalPostDetailScreen
import com.extremestudios.cinearena.ui.screens.SubmitSongScreen
import com.extremestudios.cinearena.ui.screens.VideoPlayerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class AppScreen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : AppScreen("home", "Home", Icons.Default.Home)
    data object Library : AppScreen("library", "Library", Icons.AutoMirrored.Filled.List)
    data object NowPlaying : AppScreen("now_playing", "", Icons.Default.PlayArrow)
    data object PortalKita : AppScreen("portal_kita", "Poster Board", Icons.AutoMirrored.Filled.List)
    data object About : AppScreen("about", "About", Icons.Default.Info)
}

private data class VideoPlayRequest(
    val title: String,
    val url: String,
    val filmId: String?,
    val countViewAfter30m: Boolean
)

@Composable
fun CineArenaApp(repository: ApiSongRepository = ApiSongRepository()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val playlistRepository = remember { PlaylistRepository(PlaylistLocalStore(context)) }
    val portalRepository = remember { PortalRepository() }
    val portalSessionStore = remember { PortalSessionStore(context) }
    val portalSession by portalSessionStore.sessionFlow.collectAsState(initial = null)
    val pendingHubWhatsapp by portalSessionStore.pendingWhatsappFlow.collectAsState(initial = null)
    val lastHubWhatsapp by portalSessionStore.lastHubWhatsappFlow.collectAsState(initial = null)

    val tabs = listOf(
        AppScreen.Home,
        AppScreen.Library,
        AppScreen.NowPlaying,
        AppScreen.PortalKita,
        AppScreen.About
    )
    var currentTab by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    val tabHistory = remember { mutableStateListOf<AppScreen>().apply { add(AppScreen.Home) } }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedMovie by remember { mutableStateOf<Song?>(null) }
    var videoToPlay by remember { mutableStateOf<VideoPlayRequest?>(null) }
    var selectedPortalPost by remember { mutableStateOf<PortalPost?>(null) }
    var showPortalSubmission by remember { mutableStateOf(false) }
    var showRegistrationFromAbout by remember { mutableStateOf(false) }
    var returnToLibraryAfterLogin by remember { mutableStateOf(false) }
    val watchMovieRewardGate = rememberRewardedSubmissionGate(
        context = context,
        adUnitId = REWARD_AD_UNIT_ID,
        preferenceName = PORTAL_REWARD_PREFERENCE_NAME,
        cooldownKey = WATCH_MOVIE_REWARD_COOLDOWN_KEY,
        cooldownMs = PORTAL_REWARD_COOLDOWN_MS
    )
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoadingSongs by remember { mutableStateOf(true) }
    var isRefreshingSongs by remember { mutableStateOf(false) }
    var songsError by remember { mutableStateOf<String?>(null) }
    val genres = remember(songs) { repository.getGenres(songs) }
    val artists = remember(songs) { repository.getArtists(songs) }
    val playlists by playlistRepository.playlists.collectAsState(initial = emptyList())
    var portalPosts by remember { mutableStateOf<List<PortalPost>>(emptyList()) }
    var isLoadingPortal by remember { mutableStateOf(true) }
    var portalError by remember { mutableStateOf<String?>(null) }
    val portalCategories = remember { portalRepository.getCategories() }
    val currentPlayingSong by PlaybackManager.currentSong.collectAsState()
    val isPlayingNow by PlaybackManager.isPlaying.collectAsState()
    val currentPositionMs by PlaybackManager.positionMs.collectAsState()
    val currentDurationMs by PlaybackManager.durationMs.collectAsState()

    fun navigateTo(screen: AppScreen) {
        selectedArtist = null
        selectedAlbum = null
        selectedMovie = null
        videoToPlay = null
        selectedPortalPost = null
        showPortalSubmission = false
        showRegistrationFromAbout = false
        if (currentTab != screen) {
            currentTab = screen
            tabHistory.add(screen)
        }
    }

    fun openSong(song: Song) {
        selectedArtist = null
        selectedAlbum = null
        selectedMovie = song
    }

    fun openSongFromQueue(queue: List<Song>, song: Song) {
        selectedArtist = null
        selectedAlbum = null
        if (song.audioUrl.isNotBlank()) {
            PlaybackManager.playSong(queue, song, autoPlay = true)
        }
        navigateTo(AppScreen.NowPlaying)
    }

    fun refreshSongs(showBlocking: Boolean) {
        scope.launch {
            if (showBlocking) isLoadingSongs = true else isRefreshingSongs = true
            val activeLikeUserId = portalSession
                ?.takeIf { it.status.equals("ACTIVE", ignoreCase = true) }
                ?.userId
            val result = repository.fetchSongs(activeLikeUserId)
            result.onSuccess { apiSongs ->
                songs = apiSongs
                songsError = null
                Log.d("CineArenaApp", "API success. films count=${apiSongs.size}")
                Log.d("CineArenaApp", "API success overwrote film state/cache. fullList=${apiSongs.size}")
                Log.d("CineArenaApp", "Home song count=${apiSongs.take(10).size}")
                Log.d("CineArenaApp", "Library film count=${apiSongs.size}")
                if (apiSongs.isEmpty()) {
                    Log.d("CineArenaApp", "Rendering empty film state")
                }
            }.onFailure { err ->
                songsError = err.message ?: "Gagal mengambil data film dari server."
                Log.d("CineArenaApp", "API failed. Keeping current in-memory list. count=${songs.size}")
            }
            isLoadingSongs = false
            isRefreshingSongs = false
        }
    }

    fun refreshPortalPosts(showBlocking: Boolean = true) {
        scope.launch {
            if (showBlocking) isLoadingPortal = true
            val result = portalRepository.fetchActivePosts()
            result.onSuccess { posts ->
                portalPosts = posts
                portalError = null
            }.onFailure { error ->
                portalError = error.message ?: "Gagal memuat Poster Board."
                if (showBlocking && portalPosts.isEmpty()) {
                    portalPosts = emptyList()
                }
            }
            isLoadingPortal = false
        }
    }

    fun syncHubAccessState() {
        val watchWhatsapp = portalSession?.whatsappNormalized ?: pendingHubWhatsapp ?: lastHubWhatsapp
        if (watchWhatsapp.isNullOrBlank()) return
        scope.launch {
            val result = portalRepository.checkHubStatus(watchWhatsapp)
            result.onSuccess { account ->
                when {
                    account.status.equals("ACTIVE", ignoreCase = true) -> {
                        portalSessionStore.saveSession(account)
                        portalSessionStore.clearPendingWhatsapp()
                        Log.d("HubAuth", "saved session userId=${account.userId}, status=ACTIVE")
                    }
                    account.status.equals("PENDING", ignoreCase = true) -> {
                        portalSessionStore.clearSession()
                        portalSessionStore.savePendingWhatsapp(account.whatsappNormalized)
                    }
                    account.status.equals("BLOCKED", ignoreCase = true) -> {
                        portalSessionStore.clearSession()
                        portalSessionStore.clearPendingWhatsapp()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        PlaybackManager.init(context)
        refreshSongs(showBlocking = true)
        refreshPortalPosts(showBlocking = true)
        syncHubAccessState()
    }

    LaunchedEffect(portalSession?.userId, portalSession?.status) {
        refreshSongs(showBlocking = false)
    }

    LaunchedEffect(currentTab, songs.size) {
        Log.d("CineArenaApp", "Selected tab=${currentTab.route}, songsCount=${songs.size}")
    }

    LaunchedEffect(Unit) {
        while (true) {
            PlaybackManager.tick()
            delay(500)
        }
    }

    DisposableEffect(lifecycleOwner, portalSession?.whatsappNormalized, pendingHubWhatsapp, lastHubWhatsapp) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncHubAccessState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        when {
            videoToPlay != null -> videoToPlay = null
            selectedMovie != null -> selectedMovie = null
            showPortalSubmission -> showPortalSubmission = false
            showRegistrationFromAbout -> showRegistrationFromAbout = false
            selectedPortalPost != null -> selectedPortalPost = null
            selectedAlbum != null -> selectedAlbum = null
            selectedArtist != null -> selectedArtist = null
            tabHistory.size > 1 -> {
                tabHistory.removeAt(tabHistory.lastIndex)
                currentTab = tabHistory.last()
            }
            else -> activity?.moveTaskToBack(true)
        }
    }

    Scaffold(
        bottomBar = {
            if (videoToPlay == null) {
                Column {
                    if (currentPlayingSong != null && currentTab != AppScreen.NowPlaying) {
                        MiniPlayerBar(
                            song = currentPlayingSong ?: return@Column,
                            isPlaying = isPlayingNow,
                            progress = if (currentDurationMs > 0L) {
                                (currentPositionMs.toFloat() / currentDurationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f,
                            onOpen = { navigateTo(AppScreen.NowPlaying) },
                            onPlayPause = { PlaybackManager.togglePlayPause() },
                            onPrevious = { PlaybackManager.previous() },
                            onNext = { PlaybackManager.next() }
                        )
                    }
                    BannerAdView()
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 8.dp
                    ) {
                        tabs.forEach { screen ->
                            NavigationBarItem(
                                selected = currentTab == screen,
                                onClick = { navigateTo(screen) },
                                icon = { Icon(screen.icon, contentDescription = screen.label) },
                                label = if (screen.label.isBlank()) null else ({
                                    Text(
                                        text = screen.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }),
                                alwaysShowLabel = screen.label.isNotBlank(),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val modifier = if (videoToPlay != null) Modifier else Modifier.padding(innerPadding)
        if (videoToPlay != null) {
            VideoPlayerScreen(
                title = videoToPlay?.title.orEmpty(),
                rawUrl = videoToPlay?.url.orEmpty(),
                countViewAfter30m = videoToPlay?.countViewAfter30m == true,
                onQualifiedView = {
                    val req = videoToPlay ?: return@VideoPlayerScreen
                    scope.launch {
                        repository.updateFilmView(req.filmId, req.url)
                        refreshSongs(showBlocking = false)
                    }
                },
                modifier = modifier,
                onBack = { videoToPlay = null }
            )
        } else if (selectedMovie != null) {
            MovieDetailScreen(
                movie = selectedMovie ?: return@Scaffold,
                modifier = modifier,
                onWatchTrailer = { url ->
                    val movie = selectedMovie
                    videoToPlay = VideoPlayRequest(
                        title = movie?.title ?: "Trailer",
                        url = url,
                        filmId = movie?.id,
                        countViewAfter30m = false
                    )
                },
                onWatchMovie = { url ->
                    val movie = selectedMovie
                    watchMovieRewardGate.show(
                        activity,
                        {
                            videoToPlay = VideoPlayRequest(
                                title = movie?.title ?: "Full Movie",
                                url = url,
                                filmId = movie?.id,
                                countViewAfter30m = true
                            )
                        },
                        { message ->
                            Log.d("WatchMovieReward", message)
                        },
                        {}
                    )
                }
            )
        } else if (showPortalSubmission) {
            PortalPosterSubmissionScreen(
                modifier = modifier,
                onBack = { showPortalSubmission = false },
                onSubmitted = {
                    showPortalSubmission = false
                    refreshPortalPosts(showBlocking = true)
                }
            )
        } else if (showRegistrationFromAbout) {
            SubmitSongScreen(
                modifier = modifier,
                onBack = { showRegistrationFromAbout = false }
            )
        } else selectedPortalPost?.let { post ->
            PortalPostDetailScreen(
                post = post,
                modifier = modifier,
                onBack = { selectedPortalPost = null }
            )
        } ?: selectedAlbum?.let { (artistName, albumName) ->
            AlbumDetailScreen(
                artistName = artistName,
                albumName = albumName,
                songs = songs,
                modifier = modifier,
                onBack = { selectedAlbum = null },
                onSongClick = { song, queue -> openSongFromQueue(queue, song) }
            )
        } ?: selectedArtist?.let { artistName ->
            ArtistDetailScreen(
                artistName = artistName,
                songs = songs,
                errorMessage = songsError,
                modifier = modifier,
                onBack = { selectedArtist = null },
                onSongClick = { openSong(it) },
                onAlbumClick = { albumName -> selectedAlbum = artistName to albumName }
            )
        } ?: when (currentTab) {
            AppScreen.Home -> HomeScreen(
                songs = songs,
                artists = artists,
                errorMessage = songsError,
                isLoading = isLoadingSongs,
                isRefreshing = isRefreshingSongs,
                onRefresh = { refreshSongs(showBlocking = false) },
                modifier = modifier,
                onSongClick = { openSong(it) },
                onArtistClick = { selectedArtist = it }
            )
            AppScreen.Library -> LibraryScreen(
                songs = songs,
                genres = genres,
                playlists = playlists,
                errorMessage = songsError,
                isLoading = isLoadingSongs,
                isRefreshing = isRefreshingSongs,
                onRefresh = { refreshSongs(showBlocking = false) },
                modifier = modifier,
                onSongClick = { openSong(it) },
                onArtistClick = { selectedArtist = it },
                onAddSongToPlaylist = { playlistId, songId ->
                    scope.launch { playlistRepository.addSongToPlaylist(playlistId, songId) }
                },
                canLike = portalSession?.status.equals("ACTIVE", ignoreCase = true),
                onLoginForLike = {
                    returnToLibraryAfterLogin = true
                    navigateTo(AppScreen.About)
                },
                onLikeFilm = { song ->
                    val session = portalSession
                    if (session == null || !session.status.equals("ACTIVE", ignoreCase = true)) {
                        returnToLibraryAfterLogin = true
                        navigateTo(AppScreen.About)
                        return@LibraryScreen
                    }
                    scope.launch {
                        val statusResult = portalRepository.checkHubStatus(session.whatsappNormalized)
                        val activeSession = statusResult.getOrNull()
                        if (activeSession == null || !activeSession.status.equals("ACTIVE", ignoreCase = true)) {
                            portalSessionStore.clearSession()
                            returnToLibraryAfterLogin = true
                            navigateTo(AppScreen.About)
                            return@launch
                        }
                        portalSessionStore.saveSession(activeSession)
                        repository.updateFilmLike(
                            filmId = song.id,
                            userId = activeSession.userId,
                            whatsapp = activeSession.whatsappNormalized
                        )
                        refreshSongs(showBlocking = false)
                    }
                }
            )
            AppScreen.NowPlaying -> NowPlayingScreen(
                playlists = playlists,
                modifier = modifier,
                onOpenLibrary = { navigateTo(AppScreen.Library) },
                onArtistClick = { selectedArtist = it },
                onAddSongToPlaylist = { playlistId, songId ->
                    scope.launch { playlistRepository.addSongToPlaylist(playlistId, songId) }
                }
            )
            AppScreen.PortalKita -> PortalKitaScreen(
                posts = portalPosts,
                categories = portalCategories,
                isLoading = isLoadingPortal,
                errorMessage = portalError,
                modifier = modifier,
                onRefresh = { refreshPortalPosts(showBlocking = true) },
                onPostPreview = { selectedPortalPost = it },
                onOpenPosterSubmission = { showPortalSubmission = true }
            )
            AppScreen.About -> AboutScreen(
                modifier = modifier,
                openLoginOnStart = returnToLibraryAfterLogin,
                onLoginSuccess = {
                    if (returnToLibraryAfterLogin) {
                        returnToLibraryAfterLogin = false
                        currentTab = AppScreen.Library
                        tabHistory.add(AppScreen.Library)
                        refreshSongs(showBlocking = false)
                    }
                },
                onOpenRegistration = { showRegistrationFromAbout = true }
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onOpen)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongCover(song = song, modifier = Modifier.size(44.dp))
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(34.dp)) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_media_previous),
                            contentDescription = "Previous"
                        )
                    }
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_media_next),
                            contentDescription = "Next"
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
