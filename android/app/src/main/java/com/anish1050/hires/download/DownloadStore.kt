package com.anish1050.hires.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.AesCipherDataSink
import androidx.media3.datasource.AesCipherDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Downloaded audio lives in app-private storage, AES-encrypted per cache span.
 * One cache, two views: the downloader writes encrypted spans; the player only
 * reads them. Streams use keys that never match a download, so they always go
 * to the network and never fill the phone.
 */
// ponytail: AES-CTR keyed per install stops copying files off the phone; a
// label deal that demands Widevine moves downloads to DASH + Widevine.
@OptIn(UnstableApi::class)
class DownloadStore(context: Context, key: ByteArray, upstream: DataSource.Factory, directory: File = File(context.filesDir, "downloads")) {
    val databaseProvider = StandaloneDatabaseProvider(context)
    val cache = SimpleCache(directory, NoOpCacheEvictor(), databaseProvider)

    private val encryptedRead = DataSource.Factory { AesCipherDataSource(key, FileDataSource()) }

    val downloaderDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheReadDataSourceFactory(encryptedRead)
        .setCacheWriteDataSinkFactory { AesCipherDataSink(key, CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE)) }

    val playerDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setCacheReadDataSourceFactory(encryptedRead)
        .setCacheWriteDataSinkFactory(null)
}
