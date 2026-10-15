package com.anish1050.hires.download

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.anish1050.hires.R
import com.anish1050.hires.container

@OptIn(UnstableApi::class)
class HiResDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.downloads_channel,
    0,
) {
    override fun getDownloadManager(): DownloadManager = container.downloadManager

    // Downloads resume when the app or service next starts; no job scheduler in phase 1.
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        container.downloadNotifications.buildProgressNotification(this, R.drawable.ic_launcher_foreground, null, null, downloads, notMetRequirements)

    companion object {
        const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 7
    }
}
