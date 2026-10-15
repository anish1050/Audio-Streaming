package com.anish1050.hires.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.anish1050.hires.data.api.HiResApi

/**
 * Float output is the line that makes this a hi-res player: without it Media3
 * converts high-resolution PCM to 16-bit before it reaches Android's mixer.
 * Float32 carries 24-bit samples exactly.
 */
@OptIn(UnstableApi::class)
fun buildPlayer(context: Context, dataSourceFactory: DataSource.Factory): ExoPlayer {
    val renderers = DefaultRenderersFactory(context).setEnableAudioFloatOutput(true)
    val musicAttributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
    return ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setAudioAttributes(musicAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
}

/**
 * Turns app://stream/<id> and app://download/<id> into presigned R2 URLs when
 * ExoPlayer opens them. Runs on the loading thread, so blocking is correct.
 */
@OptIn(UnstableApi::class)
fun resolvingFactory(api: HiResApi, upstream: DataSource.Factory, facts: AudioFacts, streamQuality: () -> Quality): DataSource.Factory =
    ResolvingDataSource.Factory(upstream) { spec ->
        val uri = spec.uri
        val trackId = uri.lastPathSegment
        if (uri.scheme != TrackUris.SCHEME || trackId == null) return@Factory spec
        if (uri.host == TrackUris.HOST_DOWNLOAD) {
            val audio = api.audioUrlBlocking(trackId, uri.getQueryParameter("quality") ?: Quality.HIRES.wire, "download")
            return@Factory spec.withUri(Uri.parse(audio.url))
        }
        // Streams, and offline items whose download went missing.
        val quality = streamQuality().wire
        val url = facts.reusableUrl(trackId, quality) ?: api.audioUrlBlocking(trackId, quality, "stream").also { facts.recordStream(trackId, it) }.url
        spec.withUri(Uri.parse(url))
    }
