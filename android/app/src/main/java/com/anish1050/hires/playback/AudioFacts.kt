package com.anish1050.hires.playback

import android.os.SystemClock
import com.anish1050.hires.data.api.AudioUrl
import java.util.concurrent.ConcurrentHashMap

/** What is known about the audio of a track being played: filled when a stream resolves or an offline item is queued. */
data class AudioFact(val quality: String, val bitDepth: Int, val isOffline: Boolean, val url: String = "", val resolvedAtMs: Long = 0)

/**
 * Single owner of per-track audio facts, shared by the stream resolver
 * (writer), the signal-path badge and play recording (readers).
 */
class AudioFacts {
    private val byTrackId = ConcurrentHashMap<String, AudioFact>()

    operator fun get(trackId: String): AudioFact? = byTrackId[trackId]

    fun recordStream(trackId: String, audio: AudioUrl) {
        byTrackId[trackId] = AudioFact(audio.quality, audio.bitDepth, isOffline = false, url = audio.url, resolvedAtMs = SystemClock.elapsedRealtime())
    }

    fun recordOffline(trackId: String, quality: String, bitDepth: Int) {
        byTrackId[trackId] = AudioFact(quality, bitDepth, isOffline = true)
    }

    /** A still-valid stream URL for this quality, so seeks do not call the API again. Links last 15 minutes; reuse for 10. */
    fun reusableUrl(trackId: String, quality: String): String? {
        val fact = byTrackId[trackId] ?: return null
        val isFresh = SystemClock.elapsedRealtime() - fact.resolvedAtMs < REUSE_MS
        return fact.url.takeIf { it.isNotEmpty() && isFresh && fact.quality == quality }
    }

    private companion object {
        const val REUSE_MS = 10 * 60 * 1000L
    }
}
