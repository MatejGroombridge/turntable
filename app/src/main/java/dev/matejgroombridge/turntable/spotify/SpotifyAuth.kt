package dev.matejgroombridge.turntable.spotify

import android.content.Context
import android.net.Uri
import android.util.Base64
import dev.matejgroombridge.turntable.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.encodedPath
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val SpotifyAccountsHost = "accounts.spotify.com"
private const val SpotifyAuthorizePath = "/authorize"
private const val SpotifyTokenUrl = "https://accounts.spotify.com/api/token"
private const val PendingCodeVerifierKey = "pending_code_verifier"
private const val AccessTokenKey = "access_token"
private const val RefreshTokenKey = "refresh_token"
private const val ExpiresAtKey = "expires_at"
private const val ScopeKey = "scope"
private const val ExpirySafetyWindowMillis = 60_000L

private val SpotifyScopes = listOf(
    "user-library-read",
    "user-library-modify",
    "user-follow-read",
    "user-follow-modify",
    "user-read-private",
    "user-read-email",
    "user-read-playback-state",
    "user-read-currently-playing",
    "user-read-recently-played",
    "user-modify-playback-state",
)

class SpotifyAuthManager(
    context: Context,
    private val clientId: String = BuildConfig.SPOTIFY_CLIENT_ID,
    private val redirectUri: String = BuildConfig.SPOTIFY_REDIRECT_URI,
    private val httpClient: HttpClient = defaultSpotifyHttpClient(),
) {
    private val authPreferences = context.applicationContext.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)

    val isConfigured: Boolean
        get() = clientId.isNotBlank()

    fun buildAuthorizationUri(): Uri {
        check(isConfigured) { "Set SPOTIFY_CLIENT_ID in gradle.properties or the environment." }
        val verifier = generateCodeVerifier()
        authPreferences.edit().putString(PendingCodeVerifierKey, verifier).apply()
        val challenge = verifier.toCodeChallenge()
        return Uri.parse(
            URLBuilder(
                protocol = URLProtocol.HTTPS,
                host = SpotifyAccountsHost,
            ).apply {
                encodedPath = SpotifyAuthorizePath
                parameters.append("client_id", clientId)
                parameters.append("response_type", "code")
                parameters.append("redirect_uri", redirectUri)
                parameters.append("scope", SpotifyScopes.joinToString(" "))
                parameters.append("code_challenge_method", "S256")
                parameters.append("code_challenge", challenge)
            }.buildString(),
        )
    }

    suspend fun exchangeCode(callbackUri: Uri): SpotifyTokenResponse {
        callbackUri.getQueryParameter("error")?.let { error ->
            error("Spotify authorization failed: $error")
        }
        val code = callbackUri.getQueryParameter("code") ?: error("Spotify callback did not include an authorization code.")
        val verifier = authPreferences.getString(PendingCodeVerifierKey, null)
            ?: error("No pending Spotify sign-in was found. Try connecting Spotify again.")
        return httpClient.post(SpotifyTokenUrl) {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", clientId)
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", redirectUri)
                        append("code_verifier", verifier)
                    },
                ),
            )
        }.body<SpotifyTokenResponse>().also { token ->
            authPreferences.edit().remove(PendingCodeVerifierKey).apply()
            saveTokenResponse(token)
        }
    }

    suspend fun refreshSessionIfNeeded(): SpotifyStoredSession? {
        val currentSession = getStoredSession() ?: return null
        if (!currentSession.isExpiringSoon()) return currentSession
        val refreshToken = currentSession.refreshToken ?: return null
        val refreshed = httpClient.post(SpotifyTokenUrl) {
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", clientId)
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                    },
                ),
            )
        }.body<SpotifyTokenResponse>()
        saveTokenResponse(refreshed, fallbackRefreshToken = refreshToken)
        return getStoredSession()
    }

    fun saveTokenResponse(token: SpotifyTokenResponse, fallbackRefreshToken: String? = null) {
        val refreshToken = token.refreshToken ?: fallbackRefreshToken
        authPreferences.edit()
            .putString(AccessTokenKey, token.accessToken)
            .putString(ScopeKey, token.scope ?: SpotifyScopes.joinToString(" "))
            .putLong(ExpiresAtKey, System.currentTimeMillis() + token.expiresIn * 1_000L)
            .apply {
                if (refreshToken != null) putString(RefreshTokenKey, refreshToken)
            }
            .apply()
    }

    fun getStoredSession(): SpotifyStoredSession? {
        val accessToken = authPreferences.getString(AccessTokenKey, null) ?: return null
        val storedScopes = authPreferences.getString(ScopeKey, null)?.split(" ").orEmpty().toSet()
        if (!storedScopes.containsAll(SpotifyScopes)) {
            clearSession()
            return null
        }
        return SpotifyStoredSession(
            accessToken = accessToken,
            refreshToken = authPreferences.getString(RefreshTokenKey, null),
            expiresAtMillis = authPreferences.getLong(ExpiresAtKey, 0L),
        )
    }

    fun clearSession() {
        authPreferences.edit()
            .remove(AccessTokenKey)
            .remove(RefreshTokenKey)
            .remove(ExpiresAtKey)
            .remove(ScopeKey)
            .remove(PendingCodeVerifierKey)
            .apply()
    }
}

@Serializable
data class SpotifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
)

data class SpotifyStoredSession(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long,
) {
    fun isExpiringSoon(): Boolean = System.currentTimeMillis() + ExpirySafetyWindowMillis >= expiresAtMillis
}

fun defaultSpotifyHttpClient(): HttpClient = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

private fun generateCodeVerifier(): String {
    val bytes = ByteArray(64)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

private fun String.toCodeChallenge(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.US_ASCII))
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
