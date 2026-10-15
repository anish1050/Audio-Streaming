package com.anish1050.hires.recognize

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Records a short mono 16 kHz clip for recognition. The bytes are only ever held in memory. */
class MicRecorder {
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(seconds: Int = 8): ByteArray = withContext(Dispatchers.IO) {
        val totalBytes = SAMPLE_RATE * 2 * seconds
        val bufferSize = maxOf(AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 8192)
        // UNPROCESSED skips noise suppression that smears music; not every phone offers it.
        val recorder = openRecorder(MediaRecorder.AudioSource.UNPROCESSED, bufferSize) ?: openRecorder(MediaRecorder.AudioSource.MIC, bufferSize)
            ?: error("microphone unavailable")
        val clip = ByteArray(totalBytes)
        var recorded = 0
        try {
            recorder.startRecording()
            while (recorded < totalBytes && coroutineContext.isActive) {
                val count = recorder.read(clip, recorded, totalBytes - recorded)
                if (count <= 0) break
                recorded += count
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
        clip.copyOf(recorded)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun openRecorder(source: Int, bufferSize: Int): AudioRecord? =
        AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            .takeIf { it.state == AudioRecord.STATE_INITIALIZED }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
