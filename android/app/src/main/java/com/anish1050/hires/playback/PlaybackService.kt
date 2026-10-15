package com.anish1050.hires.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.anish1050.hires.MainActivity
import com.anish1050.hires.container
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

/** Hosts the player so music keeps going with the screen off, and reports listens. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val tracker = PlayTracker()
    private val usbMixer by lazy { if (Build.VERSION.SDK_INT >= 34) UsbMixer(getSystemService(AudioManager::class.java)) else null }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = container.signalPath.onOutputDeviceChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = container.signalPath.onOutputDeviceChanged()
    }

    override fun onCreate() {
        super.onCreate()
        val player = buildPlayer(this, container.downloadStore.playerDataSourceFactory)
        player.addAnalyticsListener(container.signalPath)
        player.addAnalyticsListener(usbRateMatcher)
        player.addListener(listenRecorder(player))
        getSystemService(AudioManager::class.java).registerAudioDeviceCallback(deviceCallback, null)

        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).setCallback(mediaItemRestorer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        tracker.flush(SystemClock.elapsedRealtime())?.let(::recordListen)
        getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(deviceCallback)
        if (Build.VERSION.SDK_INT >= 34) usbMixer?.clear()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun listenRecorder(player: ExoPlayer) = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = tracker.onPlayingChanged(isPlaying, SystemClock.elapsedRealtime())

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            tracker.onTrackChanged(mediaItem?.mediaId, SystemClock.elapsedRealtime())?.let(::recordListen)
            val lufs = mediaItem?.mediaMetadata?.extras?.getFloat(EXTRA_LOUDNESS, -14f) ?: -14f
            player.volume = loudnessVolume(lufs, container.prefs.settings.value.isLoudnessNormalised)
        }
    }

    private val usbRateMatcher = object : AnalyticsListener {
        override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
            if (Build.VERSION.SDK_INT >= 34 && container.prefs.settings.value.isUsbRateMatching) usbMixer?.matchTrack(format.sampleRate)
        }
    }

    // Controllers send items without their local URI; rebuild it from the request metadata the app set.
    private val mediaItemRestorer = object : MediaSession.Callback {
        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { item ->
                item.buildUpon()
                    .setUri(item.requestMetadata.mediaUri)
                    .setCustomCacheKey(item.requestMetadata.extras?.getString(EXTRA_CACHE_KEY))
                    .build()
            }.toMutableList())
    }

    private fun recordListen(listen: Listen) {
        val fact = container.audioFacts[listen.trackId]
        container.scope.launch { container.playEvents.record(listen, fact?.quality ?: Quality.LOW.wire, fact?.isOffline ?: false) }
    }

    companion object {
        const val EXTRA_LOUDNESS = "loudness_lufs"
        const val EXTRA_CACHE_KEY = "cache_key"
    }
}
