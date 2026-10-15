package com.anish1050.hires.playback

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import androidx.annotation.RequiresApi

/**
 * Android 14+: asks the USB DAC to run at the track's own sample rate, so a
 * 96 kHz track is not resampled to the mixer's default 48 kHz. Volume is still
 * applied by Android. True bit-perfect output needs a custom audio sink (phase 2).
 */
@RequiresApi(34)
class UsbMixer(private val audioManager: AudioManager) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun matchTrack(sampleRate: Int): Boolean {
        val device = usbOutput() ?: return false
        val best = audioManager.getSupportedMixerAttributes(device)
            .filter { it.format.sampleRate == sampleRate && it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_DEFAULT }
            .maxByOrNull { bitDepth(it.format.encoding) } ?: return false
        return audioManager.setPreferredMixerAttributes(attributes, device, best)
    }

    fun clear() {
        usbOutput()?.let { audioManager.clearPreferredMixerAttributes(attributes, it) }
    }

    private fun usbOutput(): AudioDeviceInfo? = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }

    private fun bitDepth(encoding: Int) = when (encoding) {
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 32
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_16BIT -> 16
        else -> 0
    }
}
