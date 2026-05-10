package dev.matejgroombridge.turntable

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.compose.rememberAsyncImagePainter
import dev.matejgroombridge.turntable.spotify.SpotifyAlbum
import dev.matejgroombridge.turntable.spotify.SpotifyApi
import dev.matejgroombridge.turntable.spotify.SpotifyArtist
import dev.matejgroombridge.turntable.spotify.SpotifyAuthManager
import dev.matejgroombridge.turntable.spotify.SpotifyPlaybackState
import dev.matejgroombridge.turntable.spotify.SpotifyTrack
import dev.matejgroombridge.turntable.ui.theme.AppTheme
import dev.matejgroombridge.turntable.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ScreenPadding = 20.dp
private val CardRadius = 20.dp
private val SmallRadius = 14.dp
private val SettingsRowMinHeight = 56.dp
private const val SpotifyBatchSize = 10

class MainActivity : ComponentActivity() {
    private val spotifyCallbackUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        spotifyCallbackUri.value = intent?.data
        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.System) }
            var amoledMode by remember { mutableStateOf(false) }

            AppTheme(themeMode = themeMode, amoledMode = amoledMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TurntableApp(
                        themeMode = themeMode,
                        amoledMode = amoledMode,
                        onThemeModeChange = { themeMode = it },
                        onAmoledModeChange = { amoledMode = it },
                        spotifyCallbackUri = spotifyCallbackUri.value,
                        onSpotifyCallbackConsumed = { spotifyCallbackUri.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        spotifyCallbackUri.value = intent.data
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Artist(val artist: TurntableArtist) : Screen
    data class Album(val album: TurntableAlbum) : Screen
    data object Search : Screen
    data object Settings : Screen
    data object SignIn : Screen
}

private data class TurntableArtist(
    val id: String,
    val name: String,
    val imageSeed: List<Color>,
    val imageUrl: String? = null,
    val savedLabel: String,
    val monthlyListeners: String,
    val albums: List<TurntableAlbum>,
    val albumsLoaded: Boolean = true,
    val albumOffset: Int = 0,
)

private data class TurntableAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val year: Int,
    val runtime: String,
    val imageSeed: List<Color>,
    val imageUrl: String? = null,
    val tracks: List<String>,
    val tracksLoaded: Boolean = true,
    val totalTracks: Int = tracks.size,
    val sourceLabel: String = "Saved on Spotify",
)

private data class NowPlaying(
    val album: TurntableAlbum,
    val track: String,
    val isPlaying: Boolean,
    val isLiked: Boolean,
)

private data class SpotifySessionSummary(
    val displayName: String,
    val albumCount: Int,
    val artistCount: Int,
)

private data class TurntableLibrary(
    val artists: List<TurntableArtist>,
    val albums: List<TurntableAlbum>,
    val albumOffset: Int = albums.size,
    val artistAfter: String? = artists.lastOrNull()?.id,
)

private data class SpotifySyncResult(
    val library: TurntableLibrary,
    val summary: SpotifySessionSummary,
    val savedAlbumCount: Int,
)

private sealed interface TurntableSearchResult {
    data class ArtistResult(val artist: TurntableArtist) : TurntableSearchResult
    data class AlbumResult(val album: TurntableAlbum) : TurntableSearchResult
    data class TrackResult(val track: SpotifyTrack) : TurntableSearchResult
}

private data class TurntableSearchResults(
    val items: List<TurntableSearchResult> = emptyList(),
    val visibleCount: Int = SpotifyBatchSize,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TurntableApp(
    themeMode: ThemeMode,
    amoledMode: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmoledModeChange: (Boolean) -> Unit,
    spotifyCallbackUri: Uri?,
    onSpotifyCallbackConsumed: () -> Unit,
) {
    val sampleArtists = remember { sampleLibrary() }
    val sampleLibrary = remember(sampleArtists) {
        TurntableLibrary(
            artists = sampleArtists,
            albums = sampleArtists.flatMap { it.albums },
            artistAfter = null,
        )
    }
    var spotifyLibrary by remember { mutableStateOf<TurntableLibrary?>(null) }
    val activeLibrary = spotifyLibrary ?: sampleLibrary
    val feedSeed = remember { System.currentTimeMillis().toInt() }
    var likedAlbumIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var likedArtistIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hiddenAlbumIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hiddenArtistIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val visibleAlbums = remember(activeLibrary.albums, hiddenAlbumIds, feedSeed) {
        val unhidden = activeLibrary.albums.filterNot { it.id in hiddenAlbumIds }
        val saved = unhidden.filter { it.sourceLabel == "Saved on Spotify" }.shuffled(java.util.Random(feedSeed.toLong()))
        val sprinkled = unhidden.filter { it.sourceLabel != "Saved on Spotify" }.shuffled(java.util.Random((feedSeed + 17).toLong())).take(3)
        (saved + sprinkled).distinctBy { it.id }
    }
    val visibleArtists = remember(activeLibrary.artists, feedSeed) {
        activeLibrary.artists.filterNot { it.id in hiddenArtistIds }.shuffled(java.util.Random((feedSeed + 31).toLong()))
    }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val spotifyAuthManager = remember(context) { SpotifyAuthManager(context) }
    var screen: Screen by remember { mutableStateOf(Screen.Home) }
    var spotifyAccessToken by remember { mutableStateOf<String?>(null) }
    var spotifySessionSummary by remember { mutableStateOf<SpotifySessionSummary?>(null) }
    var spotifyStatusMessage by remember { mutableStateOf<String?>(null) }
    var isSpotifyLoading by remember { mutableStateOf(false) }
    val isSignedIn = spotifyAccessToken != null
    var isShuffleEnabled by remember { mutableStateOf(false) }
    var hasSpotifyNowPlaying by remember { mutableStateOf(false) }
    var nowPlaying by remember {
        mutableStateOf(
            NowPlaying(
                album = activeLibrary.albums.first(),
                track = activeLibrary.albums.first().tracks.first(),
                isPlaying = false,
                isLiked = false,
            ),
        )
    }
    var showPlayer by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(TurntableSearchResults()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val previousContentScreen = remember { mutableStateOf<Screen>(Screen.Home) }

    suspend fun currentSpotifyApi(): SpotifyApi? {
        val session = spotifyAuthManager.refreshSessionIfNeeded() ?: return null
        spotifyAccessToken = session.accessToken
        return SpotifyApi(session.accessToken)
    }

    suspend fun activeDeviceId(api: SpotifyApi): String? {
        val devices = api.getAvailableDevices().devices
        if (devices.isEmpty()) {
            spotifyStatusMessage = "Open Spotify on this phone or another device, then try playback again."
            return null
        }
        return devices.firstOrNull { it.isActive && !it.isRestricted }?.id
            ?: devices.firstOrNull { !it.isRestricted }?.id
    }

    suspend fun syncCurrentPlayback(api: SpotifyApi): Boolean {
        val current = runCatching { api.getCurrentPlayback() }.getOrNull()
            ?: runCatching { api.getCurrentlyPlaying() }.getOrNull()
        if (current?.item != null) {
            current.toNowPlaying(index = activeLibrary.albums.size)?.let { synced ->
                nowPlaying = synced
                isShuffleEnabled = current.shuffleState
                hasSpotifyNowPlaying = true
                return true
            }
        }
        runCatching { api.getRecentlyPlayed(limit = 1).items.firstOrNull()?.track }
            .getOrNull()
            ?.let { track ->
                track.toTurntableAlbum(index = activeLibrary.albums.size, sourceLabel = "Recently played")?.let { album ->
                    nowPlaying = NowPlaying(
                        album = album,
                        track = track.name,
                        isPlaying = false,
                        isLiked = false,
                    )
                    hasSpotifyNowPlaying = true
                    return true
                }
            }
        return false
    }

    fun runSpotifyPlaybackAction(action: suspend (SpotifyApi, String?) -> Unit) {
        coroutineScope.launch {
            val api = currentSpotifyApi()
            if (api == null) {
                spotifyStatusMessage = "Connect Spotify before playback."
                screen = Screen.SignIn
                return@launch
            }
            runCatching {
                action(api, activeDeviceId(api))
                syncCurrentPlayback(api)
            }.onFailure { throwable ->
                spotifyStatusMessage = throwable.message ?: "Spotify playback failed. Open Spotify and start playback on a device, then try again."
            }
        }
    }

    fun navigateBack() {
        when (screen) {
            Screen.Home -> Unit
            Screen.Settings -> screen = previousContentScreen.value
            Screen.SignIn,
            Screen.Search -> screen = Screen.Home
            is Screen.Artist,
            is Screen.Album -> screen = Screen.Home
        }
    }

    BackHandler(enabled = screen !is Screen.Home) {
        navigateBack()
    }

    fun toggleAlbumLike(album: TurntableAlbum) {
        val shouldLike = album.id !in likedAlbumIds
        likedAlbumIds = if (shouldLike) likedAlbumIds + album.id else likedAlbumIds - album.id
        if (!album.id.startsWith("sample-")) {
            coroutineScope.launch {
                currentSpotifyApi()?.let { api ->
                    runCatching {
                        if (shouldLike) api.saveAlbum(album.id) else api.removeSavedAlbum(album.id)
                    }.onFailure {
                        likedAlbumIds = if (shouldLike) likedAlbumIds - album.id else likedAlbumIds + album.id
                    }
                }
            }
        }
    }

    fun toggleArtistFollow(artist: TurntableArtist) {
        val shouldFollow = artist.id !in likedArtistIds
        likedArtistIds = if (shouldFollow) likedArtistIds + artist.id else likedArtistIds - artist.id
        if (!artist.id.startsWith("sample-")) {
            coroutineScope.launch {
                currentSpotifyApi()?.let { api ->
                    runCatching {
                        if (shouldFollow) api.followArtist(artist.id) else api.unfollowArtist(artist.id)
                    }.onFailure {
                        likedArtistIds = if (shouldFollow) likedArtistIds - artist.id else likedArtistIds + artist.id
                    }
                }
            }
        }
    }

    fun playSearchTrack(track: SpotifyTrack) {
        val album = track.toTurntableAlbum(index = activeLibrary.albums.size, sourceLabel = "Search") ?: return
        nowPlaying = NowPlaying(
            album = album,
            track = track.name,
            isPlaying = true,
            isLiked = false,
        )
        track.uri?.let { uri ->
            runSpotifyPlaybackAction { api, deviceId -> api.startTrackPlayback(uri, deviceId) }
        }
    }

    fun runSearch(query: String) {
        searchQuery = query
        if (query.isBlank()) {
            searchResults = TurntableSearchResults()
            return
        }
        coroutineScope.launch {
            runCatching {
                val api = currentSpotifyApi() ?: error("Connect Spotify before searching.")
                val results = api.search(query = query, limit = SpotifyBatchSize)
                val artists = results.artists.items.toTurntableArtists(
                    albumsByArtistId = emptyMap(),
                    savedAlbums = activeLibrary.albums,
                    albumsLoaded = false,
                ).map<TurntableArtist, TurntableSearchResult> { TurntableSearchResult.ArtistResult(it) }
                val albums = results.albums.items.mapIndexed { index, album ->
                    album.toTurntableAlbum(index = activeLibrary.albums.size + index, tracks = emptyList(), tracksLoaded = false, sourceLabel = "Search")
                }.withoutShortAlbums().newestFirst().map<TurntableAlbum, TurntableSearchResult> { TurntableSearchResult.AlbumResult(it) }
                val tracks = results.tracks.items.map<SpotifyTrack, TurntableSearchResult> { TurntableSearchResult.TrackResult(it) }
                TurntableSearchResults(
                    items = interleaveSearchResults(tracks, artists, albums),
                    visibleCount = SpotifyBatchSize,
                )
            }.onSuccess { searchResults = it }
                .onFailure { searchResults = TurntableSearchResults() }
        }
    }

    fun playAlbum(album: TurntableAlbum, shuffled: Boolean = isShuffleEnabled) {
        isShuffleEnabled = shuffled
        nowPlaying = NowPlaying(
            album = album,
            track = if (shuffled) album.tracks.shuffled().firstOrNull().orEmpty() else album.tracks.firstOrNull().orEmpty(),
            isPlaying = true,
            isLiked = nowPlaying.album.id == album.id && nowPlaying.isLiked,
        )
        if (!album.id.startsWith("sample-") && !album.id.endsWith("-spotify-profile")) {
            runSpotifyPlaybackAction { api, deviceId ->
                api.setShuffle(shuffled, deviceId)
                api.startAlbumPlayback(album.id, deviceId)
            }
        }
    }

    fun updateAlbumInLibrary(updatedAlbum: TurntableAlbum) {
        spotifyLibrary = spotifyLibrary?.let { library ->
            library.copy(
                albums = library.albums.map { if (it.id == updatedAlbum.id) updatedAlbum else it },
                artists = library.artists.map { artist ->
                    artist.copy(albums = artist.albums.map { if (it.id == updatedAlbum.id) updatedAlbum else it })
                },
            )
        }
        if (nowPlaying.album.id == updatedAlbum.id) {
            nowPlaying = nowPlaying.copy(album = updatedAlbum)
        }
        screen = Screen.Album(updatedAlbum)
    }

    fun updateArtistInLibrary(updatedArtist: TurntableArtist, additionalAlbums: List<TurntableAlbum>) {
        spotifyLibrary = spotifyLibrary?.let { library ->
            library.copy(
                artists = library.artists.map { if (it.id == updatedArtist.id) updatedArtist else it },
                albums = (library.albums + additionalAlbums.take(2)).distinctBy { it.id },
            )
        }
        screen = Screen.Artist(updatedArtist)
    }

    fun loadAlbumTracks(album: TurntableAlbum) {
        if (album.tracksLoaded || album.id.startsWith("sample-")) {
            screen = Screen.Album(album)
            return
        }
        screen = Screen.Album(album)
        coroutineScope.launch {
            spotifyStatusMessage = "Loading ${album.name} tracks…"
            runCatching {
                val api = currentSpotifyApi() ?: error("Connect Spotify before loading tracks.")
                val tracks = api.getAlbumTracksBatch(album.id)
                album.copy(
                    tracks = tracks
                        .sortedWith(compareBy<SpotifyTrack> { it.trackNumber }.thenBy { it.name })
                        .map { it.name }
                        .ifEmpty { album.tracks },
                    tracksLoaded = true,
                )
            }.onSuccess { updatedAlbum ->
                updateAlbumInLibrary(updatedAlbum)
                spotifyStatusMessage = "Loaded ${updatedAlbum.name}"
            }.onFailure { throwable ->
                spotifyStatusMessage = throwable.message ?: "Could not load album tracks."
            }
        }
    }

    fun loadArtistAlbums(artist: TurntableArtist) {
        if (artist.albumsLoaded || artist.id.startsWith("sample-")) {
            screen = Screen.Artist(artist)
            return
        }
        screen = Screen.Artist(artist)
        coroutineScope.launch {
            val api = currentSpotifyApi() ?: run {
                screen = Screen.Artist(artist.copy(albumsLoaded = false))
                return@launch
            }
            runCatching { api.getArtistAlbums(artist.id, limit = SpotifyBatchSize, offset = 0) }
                .onSuccess { firstPage ->
                    val firstAlbums = firstPage.items
                        .distinctBy { it.id }
                        .mapIndexed { index, album ->
                            album.toTurntableAlbum(index = activeLibrary.albums.size + index, tracks = emptyList(), tracksLoaded = false, sourceLabel = "Artist album")
                        }
                        .withoutShortAlbums()
                        .newestFirst()
                    val quickArtist = artist.copy(
                        albums = firstAlbums.take(4),
                        albumsLoaded = firstPage.next == null,
                        albumOffset = firstPage.items.size,
                    )
                    updateArtistInLibrary(quickArtist, firstAlbums)

                    if (firstPage.next != null) {
                        val fetchedAlbums = firstPage.items.toMutableList()
                        var offset = firstPage.items.size
                        do {
                            val page = api.getArtistAlbums(artist.id, limit = SpotifyBatchSize, offset = offset)
                            fetchedAlbums += page.items
                            offset += page.items.size
                        } while (page.next != null && page.items.isNotEmpty())

                        val allAlbums = fetchedAlbums
                            .distinctBy { it.id }
                            .mapIndexed { index, album ->
                                album.toTurntableAlbum(index = activeLibrary.albums.size + index, tracks = emptyList(), tracksLoaded = false, sourceLabel = "Artist album")
                            }
                            .withoutShortAlbums()
                            .newestFirst()
                        val completeArtist = quickArtist.copy(
                            albums = allAlbums,
                            albumsLoaded = true,
                            albumOffset = allAlbums.size,
                        )
                        updateArtistInLibrary(completeArtist, allAlbums)
                    }
                }
                .onFailure {
                    screen = Screen.Artist(artist.copy(albumsLoaded = false))
                }
        }
    }

    fun loadMoreAlbums() {
        coroutineScope.launch {
            runCatching {
                val api = currentSpotifyApi() ?: error("Connect Spotify before loading albums.")
                val library = spotifyLibrary ?: return@launch
                api.getSavedAlbumsBatch(limit = SpotifyBatchSize, offset = library.albumOffset).mapIndexed { index, savedAlbum ->
                    savedAlbum.album.toTurntableAlbum(index = library.albums.size + index, tracks = emptyList(), tracksLoaded = false, sourceLabel = "Saved on Spotify")
                }.withoutShortAlbums().newestFirst()
            }.onSuccess { albums ->
                if (albums.isNotEmpty()) {
                    spotifyLibrary = spotifyLibrary?.let { library ->
                        library.copy(
                            albums = (library.albums + albums).distinctBy { it.id },
                            albumOffset = library.albumOffset + albums.size,
                        )
                    }
                    spotifySessionSummary = spotifySessionSummary?.copy(albumCount = spotifyLibrary?.albums?.size ?: albums.size)
                }
            }
        }
    }

    fun loadMoreArtists() {
        coroutineScope.launch {
            runCatching {
                val api = currentSpotifyApi() ?: error("Connect Spotify before loading artists.")
                val library = spotifyLibrary ?: return@launch
                val batch = api.getFollowedArtistsBatch(limit = SpotifyBatchSize, after = library.artistAfter)
                val artists = batch.artists.toTurntableArtists(
                    albumsByArtistId = emptyMap(),
                    savedAlbums = library.albums,
                    albumsLoaded = false,
                )
                batch to artists
            }.onSuccess { (batch, artists) ->
                if (artists.isNotEmpty()) {
                    spotifyLibrary = spotifyLibrary?.let { library ->
                        library.copy(
                            artists = (library.artists + artists).distinctBy { it.id },
                            artistAfter = batch.nextAfter,
                        )
                    }
                    spotifySessionSummary = spotifySessionSummary?.copy(artistCount = spotifyLibrary?.artists?.size ?: artists.size)
                }
            }
        }
    }

    fun loadSpotifySummary(accessToken: String) {
        coroutineScope.launch {
            isSpotifyLoading = true
            spotifyStatusMessage = "Syncing Spotify library…"
            runCatching {
                val api = SpotifyApi(accessToken)
                val profile = api.getCurrentUserProfile()
                val savedAlbums = api.getSavedAlbumsBatch(limit = SpotifyBatchSize)
                val followedArtistsBatch = api.getFollowedArtistsBatch(limit = SpotifyBatchSize)
                val recentTracks = runCatching { api.getRecentlyPlayed(limit = SpotifyBatchSize).items.map { it.track } }.getOrDefault(emptyList())
                val syncedAlbums = (
                    savedAlbums.mapIndexed { index, savedAlbum ->
                        savedAlbum.album.toTurntableAlbum(index = index, tracks = emptyList(), tracksLoaded = false, sourceLabel = "Saved on Spotify")
                    } + recentTracks.mapIndexedNotNull { index, track ->
                        track.toTurntableAlbum(index = savedAlbums.size + index, sourceLabel = "Recently played")
                    }
                ).distinctBy { it.id }.withoutShortAlbums().newestFirst()
                val recentArtists = recentTracks
                    .flatMap { it.artists }
                    .distinctBy { it.id }
                    .mapNotNull { simpleArtist ->
                        runCatching { api.getArtist(simpleArtist.id) }.getOrNull()
                    }
                    .toTurntableArtists(
                        albumsByArtistId = emptyMap(),
                        savedAlbums = syncedAlbums,
                        albumsLoaded = false,
                        savedLabel = "Recently played",
                    )
                val syncedArtists = (
                    followedArtistsBatch.artists.toTurntableArtists(
                        albumsByArtistId = emptyMap(),
                        savedAlbums = syncedAlbums,
                        albumsLoaded = false,
                        savedLabel = "Followed on Spotify",
                    ) + recentArtists
                ).distinctBy { it.id }
                val syncedLibrary = TurntableLibrary(
                    artists = syncedArtists,
                    albums = syncedAlbums,
                    albumOffset = syncedAlbums.size,
                    artistAfter = followedArtistsBatch.nextAfter,
                )
                SpotifySyncResult(
                    library = syncedLibrary,
                    summary = SpotifySessionSummary(
                        displayName = profile.displayName ?: profile.id,
                        albumCount = syncedAlbums.size,
                        artistCount = syncedArtists.size,
                    ),
                    savedAlbumCount = syncedAlbums.size,
                )
            }.onSuccess { result ->
                spotifyLibrary = result.library
                spotifySessionSummary = result.summary
                likedAlbumIds = result.library.albums.filter { it.sourceLabel == "Saved on Spotify" }.map { it.id }.toSet()
                likedArtistIds = result.library.artists.filter { it.savedLabel == "Followed on Spotify" }.map { it.id }.toSet()
                syncCurrentPlayback(SpotifyApi(accessToken))
                if (!hasSpotifyNowPlaying) {
                    spotifyStatusMessage = "Connected, but Spotify did not return current or recent playback yet."
                } else {
                    spotifyStatusMessage = "Connected as ${result.summary.displayName}"
                }
                screen = Screen.Home
            }.onFailure { throwable ->
                spotifyStatusMessage = throwable.message ?: "Spotify sync failed."
            }
            isSpotifyLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val session = spotifyAuthManager.refreshSessionIfNeeded()
        if (session != null) {
            spotifyAccessToken = session.accessToken
            loadSpotifySummary(session.accessToken)
        } else if (spotifyAuthManager.isConfigured) {
            spotifyStatusMessage = "Please reconnect Spotify so turntable can read current and recent playback."
        }
    }

    LaunchedEffect(isSignedIn) {
        while (isSignedIn) {
            currentSpotifyApi()?.let { api -> syncCurrentPlayback(api) }
            delay(3_000)
        }
    }

    LaunchedEffect(spotifyCallbackUri) {
        val callbackUri = spotifyCallbackUri ?: return@LaunchedEffect
        if (callbackUri.scheme == "turntable" && callbackUri.host == "spotify-auth-callback") {
            isSpotifyLoading = true
            spotifyStatusMessage = "Completing Spotify sign in…"
            runCatching { spotifyAuthManager.exchangeCode(callbackUri) }
                .onSuccess { token ->
                    spotifyAccessToken = token.accessToken
                    loadSpotifySummary(token.accessToken)
                }
                .onFailure { throwable ->
                    spotifyAccessToken = null
                    spotifyStatusMessage = throwable.message ?: "Spotify sign in failed."
                    isSpotifyLoading = false
                    screen = Screen.SignIn
                }
            onSpotifyCallbackConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (screen !is Screen.SignIn && screen !is Screen.Settings && (!isSignedIn || hasSpotifyNowPlaying)) {
                PlaybackBar(
                    nowPlaying = nowPlaying,
                    isShuffleEnabled = isShuffleEnabled,
                    onClick = { showPlayer = true },
                    onTogglePlay = {
                        val shouldPlay = !nowPlaying.isPlaying
                        nowPlaying = nowPlaying.copy(isPlaying = shouldPlay)
                        runSpotifyPlaybackAction { api, deviceId ->
                            if (shouldPlay) api.resumePlayback(deviceId) else api.pausePlayback(deviceId)
                        }
                    },
                    onToggleLike = { nowPlaying = nowPlaying.copy(isLiked = !nowPlaying.isLiked) },
                    onSkip = {
                        nowPlaying = nowPlaying.nextTrack(shuffled = isShuffleEnabled)
                        runSpotifyPlaybackAction { api, deviceId -> api.skipToNext(deviceId) }
                    },
                    onReplay = {
                        nowPlaying = nowPlaying.previousTrack()
                        runSpotifyPlaybackAction { api, deviceId -> api.skipToPrevious(deviceId) }
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = screen) {
                Screen.Home -> HomeScreen(
                    artists = visibleArtists,
                    albums = visibleAlbums,
                    isSignedIn = isSignedIn,
                    onOpenArtist = { loadArtistAlbums(it) },
                    onOpenAlbum = { loadAlbumTracks(it) },
                    onLoadMoreArtists = { loadMoreArtists() },
                    onLoadMoreAlbums = { loadMoreAlbums() },
                    onOpenSearch = { screen = Screen.Search },
                    onOpenSettings = {
                        previousContentScreen.value = screen
                        screen = Screen.Settings
                    },
                    onOpenUser = { screen = Screen.SignIn },
                )

                Screen.Search -> SearchScreen(
                    query = searchQuery,
                    results = searchResults,
                    onBack = { screen = Screen.Home },
                    onQueryChange = { runSearch(it) },
                    onOpenArtist = { loadArtistAlbums(it) },
                    onOpenAlbum = { loadAlbumTracks(it) },
                    onPlayTrack = { playSearchTrack(it) },
                    onLoadMore = { searchResults = searchResults.copy(visibleCount = searchResults.visibleCount + SpotifyBatchSize) },
                )

                is Screen.Artist -> ArtistScreen(
                    artist = current.artist,
                    isSignedIn = isSignedIn,
                    onBack = { screen = Screen.Home },
                    onOpenAlbum = { loadAlbumTracks(it) },
                    isShuffleEnabled = isShuffleEnabled,
                    onPlayArtist = {
                        current.artist.albums.firstOrNull()?.let { album -> playAlbum(album, shuffled = false) }
                    },
                    likedAlbumIds = likedAlbumIds,
                    isArtistLiked = current.artist.id in likedArtistIds,
                    onToggleArtistLike = { toggleArtistFollow(current.artist) },
                    onHideArtist = { hiddenArtistIds = hiddenArtistIds + current.artist.id },
                    onToggleAlbumLike = { album -> toggleAlbumLike(album) },
                    onHideAlbum = { album -> hiddenAlbumIds = hiddenAlbumIds + album.id },
                    onOpenSettings = {
                        previousContentScreen.value = screen
                        screen = Screen.Settings
                    },
                    onOpenUser = { screen = Screen.SignIn },
                )

                is Screen.Album -> AlbumScreen(
                    album = current.album,
                    isSignedIn = isSignedIn,
                    isLoadingTracks = isSignedIn && !current.album.tracksLoaded,
                    onBack = { screen = Screen.Home },
                    onPlayAlbum = { playAlbum(current.album, shuffled = false) },
                    isLiked = current.album.id in likedAlbumIds,
                    onToggleLike = { toggleAlbumLike(current.album) },
                    onHideAlbum = { hiddenAlbumIds = hiddenAlbumIds + current.album.id },
                    onOpenSettings = {
                        previousContentScreen.value = screen
                        screen = Screen.Settings
                    },
                    onOpenUser = { screen = Screen.SignIn },
                )

                Screen.Settings -> SettingsScreen(
                    themeMode = themeMode,
                    amoledMode = amoledMode,
                    onThemeModeChange = onThemeModeChange,
                    onAmoledModeChange = onAmoledModeChange,
                    spotifySessionSummary = spotifySessionSummary,
                    onBack = { screen = previousContentScreen.value },
                )

                Screen.SignIn -> SignInScreen(
                    isConfigured = spotifyAuthManager.isConfigured,
                    isLoading = isSpotifyLoading,
                    statusMessage = spotifyStatusMessage,
                    summary = spotifySessionSummary,
                    onBack = { screen = Screen.Home },
                    onSignIn = {
                        if (!spotifyAuthManager.isConfigured) {
                            spotifyStatusMessage = "Set SPOTIFY_CLIENT_ID before connecting Spotify."
                            return@SignInScreen
                        }
                        spotifyStatusMessage = "Opening Spotify sign in…"
                        runCatching { spotifyAuthManager.buildAuthorizationUri() }
                            .onSuccess { authUri -> context.startActivity(Intent(Intent.ACTION_VIEW, authUri)) }
                            .onFailure { throwable -> spotifyStatusMessage = throwable.message ?: "Could not start Spotify sign in." }
                    },
                )
            }
        }
    }

    if (showPlayer) {
        ModalBottomSheet(
            onDismissRequest = { showPlayer = false },
            sheetState = sheetState,
        ) {
            FullPlayer(
                nowPlaying = nowPlaying,
                isShuffleEnabled = isShuffleEnabled,
                onTogglePlay = {
                    val shouldPlay = !nowPlaying.isPlaying
                    nowPlaying = nowPlaying.copy(isPlaying = shouldPlay)
                    runSpotifyPlaybackAction { api, deviceId ->
                        if (shouldPlay) api.resumePlayback(deviceId) else api.pausePlayback(deviceId)
                    }
                },
                onToggleLike = { nowPlaying = nowPlaying.copy(isLiked = !nowPlaying.isLiked) },
                onToggleShuffle = {
                    isShuffleEnabled = !isShuffleEnabled
                    runSpotifyPlaybackAction { api, deviceId -> api.setShuffle(isShuffleEnabled, deviceId) }
                },
                onSkip = {
                    nowPlaying = nowPlaying.nextTrack(shuffled = isShuffleEnabled)
                    runSpotifyPlaybackAction { api, deviceId -> api.skipToNext(deviceId) }
                },
                onReplay = {
                    nowPlaying = nowPlaying.previousTrack()
                    runSpotifyPlaybackAction { api, deviceId -> api.skipToPrevious(deviceId) }
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    artists: List<TurntableArtist>,
    albums: List<TurntableAlbum>,
    isSignedIn: Boolean,
    onOpenArtist: (TurntableArtist) -> Unit,
    onOpenAlbum: (TurntableAlbum) -> Unit,
    onLoadMoreArtists: () -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUser: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, top = 24.dp, end = ScreenPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            HomeChrome(
                isSignedIn = isSignedIn,
                onOpenSearch = onOpenSearch,
                onOpenSettings = onOpenSettings,
                onOpenUser = onOpenUser,
            )
        }

        item {
            SectionTitle("Artists")
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(artists) { artist ->
                    ArtistCard(artist = artist, onClick = { onOpenArtist(artist) })
                }
                item {
                    LoadMoreCard(label = "Load more", onClick = onLoadMoreArtists)
                }
            }
        }

        item { SectionTitle("Albums") }

        items(albums) { album ->
            AlbumListRow(album = album, onClick = { onOpenAlbum(album) })
        }

        item {
            LoadMoreRow(label = "Load more albums", onClick = onLoadMoreAlbums)
        }
    }
}

@Composable
private fun SearchScreen(
    query: String,
    results: TurntableSearchResults,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenArtist: (TurntableArtist) -> Unit,
    onOpenAlbum: (TurntableAlbum) -> Unit,
    onPlayTrack: (SpotifyTrack) -> Unit,
    onLoadMore: () -> Unit,
) {
    val visibleResults = results.items.take(results.visibleCount)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, top = 24.dp, end = ScreenPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search artists, albums, songs") },
                )
            }
        }

        if (query.isNotBlank()) {
            items(visibleResults) { result ->
                when (result) {
                    is TurntableSearchResult.ArtistResult -> SearchArtistRow(result.artist, onClick = { onOpenArtist(result.artist) })
                    is TurntableSearchResult.AlbumResult -> AlbumListRow(album = result.album, onClick = { onOpenAlbum(result.album) })
                    is TurntableSearchResult.TrackResult -> SongRow(track = result.track, onClick = { onPlayTrack(result.track) })
                }
            }
            if (visibleResults.size < results.items.size) {
                item { LoadMoreRow(label = "Load more", onClick = onLoadMore) }
            }
        }
    }
}

@Composable
private fun ArtistScreen(
    artist: TurntableArtist,
    isSignedIn: Boolean,
    isShuffleEnabled: Boolean,
    onBack: () -> Unit,
    onOpenAlbum: (TurntableAlbum) -> Unit,
    onPlayArtist: () -> Unit,
    isArtistLiked: Boolean,
    onToggleArtistLike: () -> Unit,
    onHideArtist: () -> Unit,
    likedAlbumIds: Set<String>,
    onToggleAlbumLike: (TurntableAlbum) -> Unit,
    onHideAlbum: (TurntableAlbum) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUser: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, top = 24.dp, end = ScreenPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            DetailTopBar(onBack = onBack)
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ArtBlock(colors = artist.imageSeed, modifier = Modifier.size(176.dp), shape = RoundedCornerShape(32.dp), imageUrl = artist.imageUrl)
                Spacer(Modifier.height(16.dp))
                Text(text = artist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${artist.albums.size} albums • ${artist.monthlyListeners}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlaybackIconButton(
                        icon = Icons.Rounded.VisibilityOff,
                        contentDescription = "Hide artist",
                        onClick = onHideArtist,
                    )
                    Button(onClick = onPlayArtist, enabled = artist.albums.isNotEmpty()) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play")
                    }
                    PlaybackIconButton(
                        icon = if (isArtistLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Follow artist",
                        onClick = onToggleArtistLike,
                        selected = isArtistLiked,
                    )
                }
            }
        }
        item { SectionTitle("Albums") }
        items(artist.albums) { album ->
            AlbumListRow(album = album, onClick = { onOpenAlbum(album) })
        }
    }
}

@Composable
private fun AlbumScreen(
    album: TurntableAlbum,
    isSignedIn: Boolean,
    isLoadingTracks: Boolean,
    onBack: () -> Unit,
    onPlayAlbum: () -> Unit,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onHideAlbum: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUser: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, top = 24.dp, end = ScreenPadding, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DetailTopBar(onBack = onBack)
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ArtBlock(colors = album.imageSeed, modifier = Modifier.size(210.dp), shape = RoundedCornerShape(28.dp), imageUrl = album.imageUrl)
                Spacer(Modifier.height(18.dp))
                Text(text = album.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(text = album.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${album.year} • ${album.totalTracks} tracks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlaybackIconButton(
                        icon = Icons.Rounded.VisibilityOff,
                        contentDescription = "Hide album",
                        onClick = onHideAlbum,
                    )
                    Button(onClick = onPlayAlbum) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play")
                    }
                    PlaybackIconButton(
                        icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like album",
                        onClick = onToggleLike,
                        selected = isLiked,
                    )
                }
            }
        }
        item {
            SectionTitle("Tracks")
        }
        items(album.tracks) { track ->
            TrackRow(
                index = album.tracks.indexOf(track) + 1,
                title = track,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    amoledMode: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmoledModeChange: (Boolean) -> Unit,
    spotifySessionSummary: SpotifySessionSummary?,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, top = 24.dp, end = ScreenPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(8.dp))
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }

        item {
            SectionCaption("Appearance")
            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SettingsRowMinHeight)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = mode == themeMode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
                Divider()
                CompactSwitchRow(
                    label = "AMOLED black",
                    checked = amoledMode,
                    onCheckedChange = onAmoledModeChange,
                )
            }
        }

        item {
            SectionCaption("Spotify")
            SettingsCard {
                StaticSettingsRow(
                    label = "Playback backend",
                    trailingText = if (spotifySessionSummary == null) "Not connected" else "Connected",
                )
                Divider()
                StaticSettingsRow(
                    label = "Library sync",
                    trailingText = spotifySessionSummary?.let { "${it.albumCount} albums • ${it.artistCount} artists" } ?: "Not synced",
                )
            }
        }

        item {
            SectionCaption("About")
            SettingsCard {
                StaticSettingsRow(
                    label = "turntable",
                    trailingText = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
            }
        }
    }
}

@Composable
private fun SignInScreen(
    isConfigured: Boolean,
    isLoading: Boolean,
    statusMessage: String?,
    summary: SpotifySessionSummary?,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Connect Spotify", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "Sign in to sync albums, artists, profile details, and playback controls from Spotify while keeping turntable's interface intentionally library-first.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isConfigured) {
                Text(
                    text = "Missing SPOTIFY_CLIENT_ID. Add it to gradle.properties or your environment, then register turntable://spotify-auth-callback in the Spotify developer dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            summary?.let { connected ->
                SettingsCard {
                    StaticSettingsRow(label = "Signed in", trailingText = connected.displayName)
                    Divider()
                    StaticSettingsRow(label = "Albums synced", trailingText = connected.albumCount.toString())
                    Divider()
                    StaticSettingsRow(label = "Artists synced", trailingText = connected.artistCount.toString())
                }
            }
            statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onSignIn,
                enabled = isConfigured && !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLoading) "Connecting…" else "Continue with Spotify")
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Not now")
            }
        }
        Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun HomeChrome(
    isSignedIn: Boolean,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUser: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileButton(isSignedIn = isSignedIn, onClick = onOpenUser)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Rounded.Search, contentDescription = "Search")
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
    }
}

@Composable
private fun ProfileButton(isSignedIn: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        if (isSignedIn) {
            Icon(Icons.Rounded.Person, contentDescription = "Spotify profile")
        } else {
            Icon(Icons.Rounded.Person, contentDescription = "Sign in")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun CompactSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsRowMinHeight)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StaticSettingsRow(label: String, trailingText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsRowMinHeight)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            trailingText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun AlbumCard(album: TurntableAlbum, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
    ) {
        ArtBlock(colors = album.imageSeed, modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(CardRadius), imageUrl = album.imageUrl)
        Spacer(Modifier.height(10.dp))
        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ArtistCard(artist: TurntableArtist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
    ) {
        ArtBlock(colors = artist.imageSeed, modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(CardRadius), imageUrl = artist.imageUrl)
        Spacer(Modifier.height(10.dp))
        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(artist.savedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadMoreCard(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .width(148.dp)
            .aspectRatio(1f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoadMoreRow(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AlbumListRow(
    album: TurntableAlbum,
    isLiked: Boolean = false,
    onClick: () -> Unit,
    onToggleLike: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 78.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtBlock(colors = album.imageSeed, modifier = Modifier.size(58.dp), shape = RoundedCornerShape(SmallRadius), imageUrl = album.imageUrl)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(album.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onToggleLike != null) {
                PlaybackIconButton(
                    icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Like album",
                    onClick = onToggleLike,
                    selected = isLiked,
                )
            }
            if (onHide != null) {
                PlaybackIconButton(
                    icon = Icons.Rounded.VisibilityOff,
                    contentDescription = "Hide album",
                    onClick = onHide,
                )
            }
        }
    }
}

@Composable
private fun SearchArtistRow(artist: TurntableArtist, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtBlock(
                colors = artist.imageSeed,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(SmallRadius),
                imageUrl = artist.imageUrl,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(artist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Artist", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SongRow(track: SpotifyTrack, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtBlock(
                colors = paletteFor(track.name.hashCode()),
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(SmallRadius),
                imageUrl = track.album?.images?.firstOrNull()?.url,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artists.joinToString(", ") { it.name }.ifBlank { track.album?.name.orEmpty() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlaybackIconButton(Icons.Rounded.PlayArrow, "Play song", onClick)
        }
    }
}

@Composable
private fun TrackRow(index: Int, title: String) {
    Surface(
        shape = RoundedCornerShape(SmallRadius),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(index.toString(), modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaybackBar(
    nowPlaying: NowPlaying,
    isShuffleEnabled: Boolean,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleLike: () -> Unit,
    onSkip: () -> Unit,
    onReplay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                when {
                                    totalDrag < -80f -> onSkip()
                                    totalDrag > 80f -> onReplay()
                                }
                            },
                        )
                    }
                    .clickable(onClick = onClick)
                    .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtBlock(colors = nowPlaying.album.imageSeed, modifier = Modifier.size(52.dp), shape = RoundedCornerShape(SmallRadius), imageUrl = nowPlaying.album.imageUrl)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(nowPlaying.track, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(nowPlaying.album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PlaybackIconButton(if (nowPlaying.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", onToggleLike, selected = nowPlaying.isLiked)
                PlaybackIconButton(if (nowPlaying.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", onTogglePlay)
            }
        }
    }
}

@Composable
private fun FullPlayer(
    nowPlaying: NowPlaying,
    isShuffleEnabled: Boolean,
    onTogglePlay: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSkip: () -> Unit,
    onReplay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtBlock(colors = nowPlaying.album.imageSeed, modifier = Modifier.size(260.dp), shape = RoundedCornerShape(34.dp), imageUrl = nowPlaying.album.imageUrl)
        Spacer(Modifier.height(22.dp))
        Text(nowPlaying.track, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(nowPlaying.album.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaybackIconButton(Icons.Rounded.Shuffle, if (isShuffleEnabled) "Unshuffle" else "Shuffle", onToggleShuffle, selected = isShuffleEnabled)
            PlaybackIconButton(Icons.Rounded.SkipPrevious, "Previous", onReplay)
            PlaybackIconButton(if (nowPlaying.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", onTogglePlay)
            PlaybackIconButton(Icons.Rounded.SkipNext, "Next", onSkip)
            PlaybackIconButton(if (nowPlaying.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", onToggleLike)
        }
    }
}

@Composable
private fun PlaybackIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ArtBlock(
    colors: List<Color>,
    modifier: Modifier,
    shape: RoundedCornerShape,
    imageUrl: String? = null,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(colors)),
    ) {
        if (imageUrl != null) {
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun NowPlaying.nextTrack(shuffled: Boolean): NowPlaying {
    val next = if (shuffled) {
        album.tracks.filterNot { it == track }.randomOrNull() ?: track
    } else {
        val index = album.tracks.indexOf(track).takeIf { it >= 0 } ?: -1
        album.tracks[(index + 1).floorMod(album.tracks.size)]
    }
    return copy(track = next, isPlaying = true)
}

private fun NowPlaying.previousTrack(): NowPlaying {
    val index = album.tracks.indexOf(track).takeIf { it >= 0 } ?: 0
    val previous = album.tracks[(index - 1).floorMod(album.tracks.size)]
    return copy(track = previous, isPlaying = true)
}

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other

private fun interleaveSearchResults(vararg groups: List<TurntableSearchResult>): List<TurntableSearchResult> {
    val mixed = mutableListOf<TurntableSearchResult>()
    val maxSize = groups.maxOfOrNull { it.size } ?: 0
    for (index in 0 until maxSize) {
        groups.forEach { group -> group.getOrNull(index)?.let(mixed::add) }
    }
    return mixed.distinctBy { result ->
        when (result) {
            is TurntableSearchResult.AlbumResult -> "album:${result.album.id}"
            is TurntableSearchResult.ArtistResult -> "artist:${result.artist.id}"
            is TurntableSearchResult.TrackResult -> "track:${result.track.id ?: result.track.name}"
        }
    }
}

private fun List<TurntableAlbum>.withoutShortAlbums(): List<TurntableAlbum> = filter { it.totalTracks > 4 }

private fun List<TurntableAlbum>.newestFirst(): List<TurntableAlbum> = sortedByDescending { it.year }

private fun SpotifyTrack.toTurntableAlbum(index: Int, sourceLabel: String): TurntableAlbum? = album?.toTurntableAlbum(
    index = index,
    tracks = listOf(this),
    tracksLoaded = false,
    sourceLabel = sourceLabel,
)

private fun SpotifyPlaybackState.toNowPlaying(index: Int = 0): NowPlaying? {
    val track = item ?: return null
    val album = track.toTurntableAlbum(index = index, sourceLabel = "Currently playing") ?: return null
    return NowPlaying(
        album = album,
        track = track.name,
        isPlaying = isPlaying,
        isLiked = false,
    )
}

private fun List<SpotifyTrack>.toRecentArtists(savedAlbums: List<TurntableAlbum>): List<TurntableArtist> =
    flatMap { track -> track.artists }
        .distinctBy { it.id }
        .mapIndexed { index, artist ->
            TurntableArtist(
                id = artist.id,
                name = artist.name,
                imageSeed = paletteFor(savedAlbums.size + index),
                imageUrl = null,
                savedLabel = "Recently played",
                monthlyListeners = "Spotify artist",
                albums = savedAlbums.filter { album -> album.artist.split(", ").any { it == artist.name } },
                albumsLoaded = false,
            )
        }

private fun SpotifyAlbum.toTurntableAlbum(
    index: Int,
    tracks: List<SpotifyTrack> = emptyList(),
    tracksLoaded: Boolean = tracks.isNotEmpty(),
    sourceLabel: String = "Saved on Spotify",
): TurntableAlbum = TurntableAlbum(
    id = id,
    name = name,
    artist = artists.joinToString(", ") { it.name }.ifBlank { "Unknown artist" },
    year = releaseDate.take(4).toIntOrNull() ?: 0,
    runtime = "${totalTracks} tracks",
    imageSeed = paletteFor(index),
    imageUrl = images.firstOrNull()?.url,
    tracks = tracks
        .sortedWith(compareBy<SpotifyTrack> { it.trackNumber }.thenBy { it.name })
        .map { it.name },
    tracksLoaded = tracksLoaded,
    totalTracks = totalTracks,
    sourceLabel = sourceLabel,
)

private fun List<SpotifyArtist>.toTurntableArtists(
    albumsByArtistId: Map<String, List<TurntableAlbum>>,
    savedAlbums: List<TurntableAlbum>,
    albumsLoaded: Boolean = true,
    savedLabel: String = "Followed on Spotify",
): List<TurntableArtist> = mapIndexed { index, artist ->
    TurntableArtist(
        id = artist.id,
        name = artist.name,
        imageSeed = paletteFor(index + savedAlbums.size),
        imageUrl = artist.images.firstOrNull()?.url,
        savedLabel = savedLabel,
        monthlyListeners = artist.followers?.total?.let { "${it.formatCompact()} followers" } ?: "Spotify artist",
        albums = albumsByArtistId[artist.id]
            ?: savedAlbums.filter { album -> album.artist.split(", ").any { it == artist.name } },
        albumsLoaded = albumsLoaded,
    )
}

private fun Int.formatCompact(): String = when {
    this >= 1_000_000 -> "${this / 1_000_000}.${(this % 1_000_000) / 100_000}m"
    this >= 1_000 -> "${this / 1_000}k"
    else -> toString()
}

private fun paletteFor(index: Int): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFFFFE0E6), Color(0xFFF7A6B5)),
        listOf(Color(0xFFFFE3D1), Color(0xFFFFB48A)),
        listOf(Color(0xFFFFF4C2), Color(0xFFFFE066)),
        listOf(Color(0xFFD1F0DA), Color(0xFF8DD6A4)),
        listOf(Color(0xFFCFE8E4), Color(0xFF8DCDC4)),
        listOf(Color(0xFFD3E8F5), Color(0xFF8FC4E0)),
        listOf(Color(0xFFE3DAF5), Color(0xFFB7A5DD)),
        listOf(Color(0xFFE2E5EA), Color(0xFFB6BCC6)),
    )
    return palettes[index.floorMod(palettes.size)]
}

private fun sampleLibrary(): List<TurntableArtist> {
    val shoreline = TurntableArtist(
        id = "sample-shoreline-motif",
        name = "Shoreline Motif",
        imageSeed = listOf(Color(0xFFD3E8F5), Color(0xFF8FC4E0)),
        savedLabel = "Saved yesterday",
        monthlyListeners = "840k listeners",
        albums = listOf(
            TurntableAlbum(
                id = "sample-low-tide-rituals",
                name = "Low Tide Rituals",
                artist = "Shoreline Motif",
                year = 2025,
                runtime = "42 min",
                imageSeed = listOf(Color(0xFFCFE8E4), Color(0xFF8DCDC4)),
                tracks = listOf("Harbour Light", "Cassette Foam", "Blue Hour", "Walk the Pier", "Low Tide Ritual", "Night Ferry", "Soft Return"),
            ),
            TurntableAlbum(
                id = "sample-windows-open",
                name = "Windows Open",
                artist = "Shoreline Motif",
                year = 2023,
                runtime = "36 min",
                imageSeed = listOf(Color(0xFFFFE3D1), Color(0xFFFFB48A)),
                tracks = listOf("April Static", "Windows Open", "Neighbourhood Rain", "Second Floor", "No Cars After Midnight", "Sunday Signal"),
            ),
        ),
    )
    val marlowe = TurntableArtist(
        id = "sample-marlowe-atlas",
        name = "Marlowe Atlas",
        imageSeed = listOf(Color(0xFFE3DAF5), Color(0xFFB7A5DD)),
        savedLabel = "Saved this week",
        monthlyListeners = "1.2m listeners",
        albums = listOf(
            TurntableAlbum(
                id = "sample-rooms-for-echoes",
                name = "Rooms for Echoes",
                artist = "Marlowe Atlas",
                year = 2024,
                runtime = "48 min",
                imageSeed = listOf(Color(0xFFE3DAF5), Color(0xFFB7A5DD)),
                tracks = listOf("Vestibule", "Rooms for Echoes", "Wide Staircase", "The Guest", "Plaster Moon", "House Lights", "Wake Slowly", "Exit Music"),
            ),
            TurntableAlbum(
                id = "sample-cartographer",
                name = "Cartographer",
                artist = "Marlowe Atlas",
                year = 2021,
                runtime = "39 min",
                imageSeed = listOf(Color(0xFFE2E5EA), Color(0xFFB6BCC6)),
                tracks = listOf("North Line", "Old Compass", "Ink and Dust", "Cartographer", "Relay Tower", "Boundary Stone"),
            ),
        ),
    )
    val velvet = TurntableArtist(
        id = "sample-velvet-transit",
        name = "Velvet Transit",
        imageSeed = listOf(Color(0xFFD1F0DA), Color(0xFF8DD6A4)),
        savedLabel = "Saved last month",
        monthlyListeners = "390k listeners",
        albums = listOf(
            TurntableAlbum(
                id = "sample-late-platform",
                name = "Late Platform",
                artist = "Velvet Transit",
                year = 2022,
                runtime = "44 min",
                imageSeed = listOf(Color(0xFFE2E5EA), Color(0xFF8DCDC4)),
                tracks = listOf("Turnstile", "Green Signal", "Late Platform", "Warm Carriage", "Pocket Map", "Terminus", "Last Train Home"),
            ),
        ),
    )
    return listOf(shoreline, marlowe, velvet)
}
