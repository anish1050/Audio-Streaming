package com.anish1050.hires.playback

import java.time.Instant
import java.util.UUID

/** One listen of one track, before the service adds quality and offline facts. */
data class Listen(val clientEventId: String, val trackId: String, val msPlayed: Long, val startedAt: Instant)

/**
 * Turns player callbacks into listens: only time actually spent playing
 * counts, and anything under 3 seconds is a skip. Times are elapsed-clock
 * milliseconds supplied by the caller.
 */
class PlayTracker(private val newEventId: () -> String = { UUID.randomUUID().toString() }, private val wallClock: () -> Instant = Instant::now) {
    private var trackId: String? = null
    private var startedAt: Instant = Instant.EPOCH
    private var accumulatedMs = 0L
    private var playingSinceMs: Long? = null

    fun onPlayingChanged(isPlaying: Boolean, nowMs: Long) {
        if (isPlaying && playingSinceMs == null) playingSinceMs = nowMs
        if (!isPlaying) playingSinceMs?.let { accumulatedMs += nowMs - it; playingSinceMs = null }
    }

    /** Closes the current track and starts the next; returns the finished listen, if it counts. */
    fun onTrackChanged(nextTrackId: String?, nowMs: Long): Listen? {
        val wasPlaying = playingSinceMs != null
        val finished = finish(nowMs)
        trackId = nextTrackId
        startedAt = wallClock()
        accumulatedMs = 0
        playingSinceMs = if (wasPlaying) nowMs else null
        return finished
    }

    fun flush(nowMs: Long): Listen? = onTrackChanged(trackId, nowMs)

    private fun finish(nowMs: Long): Listen? {
        val id = trackId ?: return null
        val playedMs = accumulatedMs + (playingSinceMs?.let { nowMs - it } ?: 0)
        if (playedMs < MIN_PLAY_MS) return null
        return Listen(newEventId(), id, playedMs, startedAt)
    }

    private companion object {
        const val MIN_PLAY_MS = 3_000L
    }
}
