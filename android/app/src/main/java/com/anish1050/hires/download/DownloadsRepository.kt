package com.anish1050.hires.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.anish1050.hires.data.api.Track
import com.anish1050.hires.data.db.DownloadDao
import com.anish1050.hires.data.db.DownloadState
import com.anish1050.hires.data.db.DownloadedTrack
import com.anish1050.hires.playback.Quality
import com.anish1050.hires.playback.TrackUris
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class OfflineTrack(val track: Track, val quality: Quality, val bitDepth: Int, val bytes: Long, val state: DownloadState)

/** Owns the list of downloads: Room holds what the screens show, Media3 holds the bytes. */
@OptIn(UnstableApi::class)
class DownloadsRepository(
    private val context: Context,
    private val dao: DownloadDao,
    private val downloadManager: DownloadManager,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val retriedOnce = mutableSetOf<String>()

    val downloads: Flow<List<OfflineTrack>> = dao.observeAll().map { rows ->
        rows.mapNotNull { row -> runCatching { json.decodeFromString(Track.serializer(), row.trackJson) }.getOrNull()?.let { OfflineTrack(it, Quality.fromWire(row.quality), row.bitDepth, row.bytes, row.state) } }
    }

    init {
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                val trackId = download.request.id
                scope.launch {
                    when (download.state) {
                        Download.STATE_COMPLETED -> dao.updateState(trackId, DownloadState.COMPLETED, download.bytesDownloaded)
                        Download.STATE_DOWNLOADING -> dao.updateState(trackId, DownloadState.DOWNLOADING, download.bytesDownloaded)
                        // The presigned link lasts 6 hours; one retry fetches a fresh one.
                        Download.STATE_FAILED -> if (retriedOnce.add(trackId)) resend(download.request) else dao.updateState(trackId, DownloadState.FAILED, download.bytesDownloaded)
                        else -> Unit
                    }
                }
            }
        })
    }

    suspend fun download(track: Track, quality: Quality) {
        val bitDepth = if (quality == Quality.HIRES) track.bestQuality?.bitDepth ?: 24 else 16
        dao.upsert(DownloadedTrack(track.id, json.encodeToString(Track.serializer(), track), quality.wire, bitDepth, 0, DownloadState.QUEUED, System.currentTimeMillis()))
        resend(DownloadRequest.Builder(track.id, TrackUris.download(track.id, quality)).setCustomCacheKey(TrackUris.cacheKey(track.id)).build())
    }

    suspend fun remove(trackId: String) {
        DownloadService.sendRemoveDownload(context, HiResDownloadService::class.java, trackId, false)
        dao.delete(trackId)
    }

    suspend fun removeAll() {
        DownloadService.sendRemoveAllDownloads(context, HiResDownloadService::class.java, false)
        dao.deleteAll()
    }

    suspend fun find(trackId: String): DownloadedTrack? = dao.find(trackId)
    suspend fun completedIds(): Set<String> = dao.completedIds().toSet()

    private fun resend(request: DownloadRequest) {
        DownloadService.sendAddDownload(context, HiResDownloadService::class.java, request, false)
    }
}
