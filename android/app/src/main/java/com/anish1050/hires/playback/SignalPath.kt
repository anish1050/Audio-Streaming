package com.anish1050.hires.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.min
import kotlin.math.pow

/** What actually reaches the speaker or DAC, shown on Now Playing. Never more than this. */
data class SignalChain(
    val sourceCodec: String,
    val sourceRate: Int,
    val sourceBits: Int,
    val outputEncoding: String,
    val outputRate: Int,
    val device: String,
) {
    fun label(): String {
        val source = if (sourceBits > 0) "$sourceCodec $sourceBits/${khz(sourceRate)}" else sourceCodec
        if (outputRate == 0) return source
        val resampled = if (outputRate != sourceRate) " (resampled)" else ""
        return "$source → $outputEncoding → ${khz(outputRate)} kHz$resampled → $device"
    }

    private fun khz(hz: Int) = if (hz % 1000 == 0) "${hz / 1000}" else String.format(Locale.US, "%.1f", hz / 1000.0)
}

/** Player volume that brings a track toward -14 LUFS. Never boosts, so quiet masters are untouched. */
fun loudnessVolume(lufs: Float, isEnabled: Boolean): Float = if (!isEnabled) 1f else min(1f, 10f.pow((-14f - lufs) / 20f))

/**
 * Listens to the player's analytics and publishes the chain. Bit depth of
 * compressed FLAC is not in the decoder format, so it comes from the server's
 * answer for the track being played ([sourceBitsFor]).
 */
@OptIn(UnstableApi::class)
class SignalPath(private val sourceBitsFor: (mediaId: String) -> Int, private val currentDevice: () -> String) : AnalyticsListener {
    private val _chain = MutableStateFlow<SignalChain?>(null)
    val chain: StateFlow<SignalChain?> = _chain

    override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        val mediaId = eventTime.timeline.takeIf { !it.isEmpty }
            ?.getWindow(eventTime.windowIndex, androidx.media3.common.Timeline.Window())?.mediaItem?.mediaId.orEmpty()
        _chain.value = SignalChain(
            sourceCodec = codecName(format.sampleMimeType),
            sourceRate = format.sampleRate,
            sourceBits = sourceBitsFor(mediaId),
            outputEncoding = _chain.value?.outputEncoding.orEmpty(),
            outputRate = 0,
            device = currentDevice(),
        )
    }

    override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
        val current = _chain.value ?: return
        _chain.value = current.copy(outputEncoding = encodingName(audioTrackConfig.encoding), outputRate = audioTrackConfig.sampleRate, device = currentDevice())
    }

    fun onOutputDeviceChanged() {
        _chain.value = _chain.value?.copy(device = currentDevice())
    }

    private fun codecName(mime: String?) = when (mime) {
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        else -> mime?.substringAfter('/')?.uppercase(Locale.US) ?: "?"
    }

    private fun encodingName(encoding: Int) = when (encoding) {
        C.ENCODING_PCM_FLOAT -> "float"
        C.ENCODING_PCM_16BIT -> "16-bit"
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> "24-bit"
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> "32-bit"
        else -> "encoded"
    }
}
