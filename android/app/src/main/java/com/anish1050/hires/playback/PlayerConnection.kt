package com.anish1050.hires.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.anish1050.hires.data.api.HiResApi
import com.anish1050.hires.data.api.Track
import com.anish1050.hires.download.DownloadsRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class NowPlaying(val track: Track? = null, val isPlaying: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0)

/** The UI's handle on the playback service: builds queues and exposes what is playing. */
class PlayerConnection(private val context: Context, private val api: HiResApi, private val facts: AudioFacts, private val downloads: DownloadsRepository) {
    // MediaController must be used on the main thread.
    private val scope = MainScope()
    private val tracksById = ConcurrentHashMap<String, Track>()
    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying

    private val controller by lazy {
        MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            .also { future -> scope.launch { observe(future.await()) } }
    }

    /** Downloaded tracks always play from the encrypted copy: same quality, no data used. */
    fun play(tracks: List<Track>, startIndex: Int = 0, startPositionMs: Long = 0) = scope.launch {
        val offlineIds = downloads.completedIds()
        val items = tracks.map { track ->
            tracksById[track.id] = track
            mediaItem(track, isDownloaded = track.id in offlineIds)
        }
        val player = controller.await()
        player.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), startPositionMs)
        player.prepare()
        player.play()
    }

    fun togglePlay() = withPlayer { if (it.isPlaying) it.pause() else it.play() }
    fun next() = withPlayer { it.seekToNextMediaItem() }
    fun previous() = withPlayer { it.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) = withPlayer { it.seekTo(positionMs) }

    private fun withPlayer(action: (MediaController) -> Unit) = scope.launch { action(controller.await()) }

    private suspend fun mediaItem(track: Track, isDownloaded: Boolean): MediaItem {
        val uri: Uri
        var cacheKey: String? = null
        if (isDownloaded) {
            uri = TrackUris.offline(track.id)
            cacheKey = TrackUris.cacheKey(track.id)
            downloads.find(track.id)?.let { facts.recordOffline(track.id, it.quality, it.bitDepth) }
        } else {
            uri = TrackUris.stream(track.id)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist.name)
            .setAlbumTitle(track.album.title)
            .setArtworkUri(Uri.parse(api.absoluteUrl(track.album.coverUrl)))
            .setExtras(bundleOf(PlaybackService.EXTRA_LOUDNESS to track.loudnessLufs))
            .build()
        val request = MediaItem.RequestMetadata.Builder().setMediaUri(uri).setExtras(bundleOf(PlaybackService.EXTRA_CACHE_KEY to cacheKey)).build()
        return MediaItem.Builder().setMediaId(track.id).setUri(uri).setCustomCacheKey(cacheKey).setMediaMetadata(metadata).setRequestMetadata(request).build()
    }

    private suspend fun observe(player: MediaController) {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publish(player)
        })
        while (true) {
            publish(player)
            delay(if (player.isPlaying) 500 else 2_000)
        }
    }

    private fun publish(player: Player) {
        val mediaId = player.currentMediaItem?.mediaId
        _nowPlaying.value = NowPlaying(
            track = mediaId?.let { tracksById[it] },
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: (mediaId?.let { tracksById[it]?.durationMs } ?: 0),
        )
    }
}
