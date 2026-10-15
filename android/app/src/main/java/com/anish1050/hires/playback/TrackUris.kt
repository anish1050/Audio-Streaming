package com.anish1050.hires.playback

import android.net.Uri

/**
 * Media items never hold presigned URLs (they expire in 15 minutes while a
 * queue can last hours). They hold these app URIs, resolved when ExoPlayer
 * opens them. Only downloads and offline playback use the "track:" cache key,
 * so streams never read from or fill the download cache.
 */
object TrackUris {
    const val SCHEME = "app"
    const val HOST_STREAM = "stream"
    const val HOST_OFFLINE = "offline"
    const val HOST_DOWNLOAD = "download"

    fun stream(trackId: String): Uri = Uri.parse("$SCHEME://$HOST_STREAM/$trackId")
    fun offline(trackId: String): Uri = Uri.parse("$SCHEME://$HOST_OFFLINE/$trackId")
    fun download(trackId: String, quality: Quality): Uri = Uri.parse("$SCHEME://$HOST_DOWNLOAD/$trackId?quality=${quality.wire}")
    fun cacheKey(trackId: String) = "track:$trackId"
}
