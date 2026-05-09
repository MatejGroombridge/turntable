package dev.matejgroombridge.turntable.spotify

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SpotifyApi(
    private val accessToken: String,
    private val httpClient: HttpClient = defaultSpotifyHttpClient(),
) {
    suspend fun getCurrentUserProfile(): SpotifyUserProfile = httpClient.get("https://api.spotify.com/v1/me") {
        bearerAuth(accessToken)
    }.body()

    suspend fun getSavedAlbums(limit: Int = 20, offset: Int = 0): SpotifySavedAlbumsPage = httpClient.get("https://api.spotify.com/v1/me/albums") {
        bearerAuth(accessToken)
        parameter("limit", limit.coerceIn(1, 50))
        parameter("offset", offset.coerceAtLeast(0))
    }.body()

    suspend fun getSavedAlbumsBatch(limit: Int = 10, offset: Int = 0): List<SpotifySavedAlbum> =
        getSavedAlbums(limit = limit.coerceIn(1, 10), offset = offset).items

    suspend fun getFollowedArtists(limit: Int = 20, after: String? = null): SpotifyFollowedArtistsResponse = httpClient.get("https://api.spotify.com/v1/me/following") {
        bearerAuth(accessToken)
        parameter("type", "artist")
        parameter("limit", limit.coerceIn(1, 50))
        after?.let { parameter("after", it) }
    }.body()

    suspend fun getFollowedArtistsBatch(limit: Int = 10, after: String? = null): List<SpotifyArtist> =
        getFollowedArtists(limit = limit.coerceIn(1, 10), after = after).artists.items

    suspend fun getArtistAlbums(artistId: String, limit: Int = 20, offset: Int = 0): SpotifyArtistAlbumsPage = httpClient.get("https://api.spotify.com/v1/artists/$artistId/albums") {
        bearerAuth(accessToken)
        parameter("include_groups", "album,single")
        parameter("limit", limit.coerceIn(1, 50))
        parameter("offset", offset.coerceAtLeast(0))
    }.body()

    suspend fun getArtistAlbumsBatch(artistId: String, limit: Int = 10): List<SpotifyAlbum> =
        getArtistAlbums(artistId = artistId, limit = limit.coerceIn(1, 10), offset = 0)
            .items
            .distinctBy { it.id }

    suspend fun getAlbumTracks(albumId: String, limit: Int = 50, offset: Int = 0): SpotifyAlbumTracksPage = httpClient.get("https://api.spotify.com/v1/albums/$albumId/tracks") {
        bearerAuth(accessToken)
        parameter("limit", limit.coerceIn(1, 50))
        parameter("offset", offset.coerceAtLeast(0))
    }.body()

    suspend fun getAlbumTracksBatch(albumId: String): List<SpotifyTrack> =
        getAlbumTracks(albumId = albumId, limit = 50, offset = 0).items
}

@Serializable
data class SpotifyUserProfile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val images: List<SpotifyImage> = emptyList(),
)

@Serializable
data class SpotifySavedAlbumsPage(
    val items: List<SpotifySavedAlbum> = emptyList(),
    val next: String? = null,
)

@Serializable
data class SpotifySavedAlbum(
    @SerialName("added_at") val addedAt: String,
    val album: SpotifyAlbum,
)

@Serializable
data class SpotifyFollowedArtistsResponse(
    val artists: SpotifyArtistsPage,
)

@Serializable
data class SpotifyArtistsPage(
    val items: List<SpotifyArtist> = emptyList(),
    val next: String? = null,
    val cursors: SpotifyCursors? = null,
)

@Serializable
data class SpotifyCursors(
    val after: String? = null,
)

@Serializable
data class SpotifyArtistAlbumsPage(
    val items: List<SpotifyAlbum> = emptyList(),
    val next: String? = null,
)

@Serializable
data class SpotifyAlbumTracksPage(
    val items: List<SpotifyTrack> = emptyList(),
    val next: String? = null,
)

@Serializable
data class SpotifyAlbum(
    val id: String,
    val name: String,
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("total_tracks") val totalTracks: Int = 0,
    val images: List<SpotifyImage> = emptyList(),
    val artists: List<SpotifySimpleArtist> = emptyList(),
)

@Serializable
data class SpotifyTrack(
    val id: String? = null,
    val name: String,
    @SerialName("track_number") val trackNumber: Int = 0,
)

@Serializable
data class SpotifyArtist(
    val id: String,
    val name: String,
    val images: List<SpotifyImage> = emptyList(),
    val followers: SpotifyFollowers? = null,
)

@Serializable
data class SpotifySimpleArtist(
    val id: String,
    val name: String,
)

@Serializable
data class SpotifyImage(
    val url: String,
    val height: Int? = null,
    val width: Int? = null,
)

@Serializable
data class SpotifyFollowers(
    val total: Int = 0,
)
