package com.anish1050.hires.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shapes of the backend, snake_case on the wire. Image URLs arrive as
// paths like /api/images/albums/x and are made absolute by HiResApi.absoluteUrl.

@Serializable
data class ArtistRef(val id: String, val name: String, @SerialName("image_url") val imageUrl: String)

@Serializable
data class AlbumRef(val id: String, val title: String, @SerialName("cover_url") val coverUrl: String, val year: Int = 0)

@Serializable
data class QualityInfo(val quality: String, val codec: String, @SerialName("sample_rate") val sampleRate: Int, @SerialName("bit_depth") val bitDepth: Int)

@Serializable
data class Track(
    val id: String,
    val title: String,
    @SerialName("track_no") val trackNo: Int = 0,
    @SerialName("duration_ms") val durationMs: Long,
    val explicit: Boolean = false,
    val genres: List<String> = emptyList(),
    @SerialName("loudness_lufs") val loudnessLufs: Float = -14f,
    val album: AlbumRef,
    val artist: ArtistRef,
    @SerialName("best_quality") val bestQuality: QualityInfo? = null,
    /** Only on Discover results. */
    @SerialName("match_percent") val matchPercent: Int? = null,
)

@Serializable
data class AuthResult(@SerialName("access_token") val accessToken: String, val user: User) {
    @Serializable
    data class User(val id: String, val email: String, val name: String, val picture: String = "")
}

@Serializable
data class EntitlementDto(
    val tier: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("verified_at") val verifiedAt: String? = null,
)

@Serializable
data class Me(val user: Profile, val entitlement: EntitlementDto) {
    @Serializable
    data class Profile(
        val id: String,
        val email: String,
        val name: String,
        val picture: String = "",
        @SerialName("taste_genres") val tasteGenres: List<String> = emptyList(),
        @SerialName("taste_artist_ids") val tasteArtistIds: List<String> = emptyList(),
    )
}

@Serializable
data class AudioUrl(
    val url: String,
    val quality: String,
    val codec: String,
    @SerialName("sample_rate") val sampleRate: Int,
    @SerialName("bit_depth") val bitDepth: Int,
    val bytes: Long = 0,
    @SerialName("loudness_lufs") val loudnessLufs: Float = -14f,
)

@Serializable
data class DailyMix(val id: String, val title: String, val subtitle: String, val tracks: List<Track>)

@Serializable
data class DiscoverPreview(val count: Int, @SerialName("match_percent") val matchPercent: Int, val sparkline: List<Int>)

@Serializable
data class Home(
    val featured: List<Track>,
    val recent: List<Track>,
    @SerialName("daily_mixes") val dailyMixes: List<DailyMix>,
    @SerialName("discover_preview") val discoverPreview: DiscoverPreview,
    val genres: List<String>,
)

@Serializable
data class Discover(val tracks: List<Track>)

@Serializable
data class SearchResults(val tracks: List<Track>, val albums: List<AlbumRef>, val artists: List<ArtistRef>)

@Serializable
data class AlbumDetail(
    val id: String,
    val title: String,
    @SerialName("cover_url") val coverUrl: String,
    val year: Int,
    val genres: List<String> = emptyList(),
    val artist: ArtistRef? = null,
    val tracks: List<Track>,
)

@Serializable
data class ArtistDetail(
    val id: String,
    val name: String,
    @SerialName("image_url") val imageUrl: String,
    val bio: String = "",
    val albums: List<AlbumRef>,
    @SerialName("top_tracks") val topTracks: List<Track>,
)

@Serializable
data class Stats(
    val period: String,
    @SerialName("total_ms") val totalMs: Long,
    @SerialName("songs_played") val songsPlayed: Int,
    @SerialName("peak_hour") val peakHour: Int? = null,
    @SerialName("top_artists") val topArtists: List<TopArtist>,
    val genres: List<GenreShare>,
) {
    @Serializable
    data class TopArtist(val id: String, val name: String, @SerialName("image_url") val imageUrl: String, val ms: Long)

    @Serializable
    data class GenreShare(val genre: String, val ms: Long)
}

@Serializable
data class PlayEventDto(
    @SerialName("client_event_id") val clientEventId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("ms_played") val msPlayed: Long,
    @SerialName("started_at") val startedAt: String,
    val quality: String,
    val offline: Boolean,
)

@Serializable
data class Recognition(val match: Match? = null, val external: External? = null) {
    @Serializable
    data class Match(val track: Track, @SerialName("offset_ms") val offsetMs: Long, val confidence: Double)

    @Serializable
    data class External(val title: String, val artist: String)
}

@Serializable
data class Credit(@SerialName("track_id") val trackId: String, val title: String, val artist: String, val licence: String, val attribution: String)
