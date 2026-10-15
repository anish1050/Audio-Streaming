package com.anish1050.hires.data.repo

import com.anish1050.hires.data.api.HiResApi
import com.anish1050.hires.data.api.PlayEventDto
import com.anish1050.hires.data.db.PendingPlay
import com.anish1050.hires.data.db.PendingPlayDao
import com.anish1050.hires.playback.Listen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Queues listens locally (they happen offline too) and uploads them in batches; the server ignores duplicates. */
class PlayEventRepository(private val dao: PendingPlayDao, private val api: HiResApi) {
    private val uploadLock = Mutex()

    suspend fun record(listen: Listen, quality: String, isOffline: Boolean) {
        dao.insert(PendingPlay(listen.clientEventId, listen.trackId, listen.msPlayed, listen.startedAt.toString(), quality, isOffline))
        if (dao.count() >= UPLOAD_THRESHOLD) upload()
    }

    /** Safe to call any time; failures leave the queue for the next attempt. */
    suspend fun upload() = uploadLock.withLock {
        runCatching {
            while (true) {
                val batch = dao.oldest(BATCH_SIZE)
                if (batch.isEmpty()) break
                api.postPlays(batch.map { PlayEventDto(it.clientEventId, it.trackId, it.msPlayed, it.startedAt, it.quality, it.isOffline) })
                dao.delete(batch.map { it.clientEventId })
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 200
        const val UPLOAD_THRESHOLD = 20
    }
}
