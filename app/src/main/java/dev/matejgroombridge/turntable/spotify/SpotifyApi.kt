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

    suspend fun getSavedAlbums(limit: Int = 20): SpotifySavedAlbumsPage = httpClient.get("https://api.spotify.com/v1/me/albums") {
        bearerAuth(accessToken)
        parameter("limit", limit.coerceIn(1, 50))
    }.body()

    suspend fun getFollowedArtists(limit: Int = 20): SpotifyFollowedArtistsResponse = httpClient.get("https://api.spotify.com/v1/me/following") {
        bearerAuth(accessToken)
        parameter("type", "artist")
        parameter("limit", limit.coerceIn(1, 50))
    }.body()
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
