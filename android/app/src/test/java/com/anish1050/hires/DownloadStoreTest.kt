package com.anish1050.hires

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.test.utils.FakeDataSet
import androidx.media3.test.utils.FakeDataSource
import androidx.test.core.app.ApplicationProvider
import com.anish1050.hires.download.DownloadStore
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@UnstableApi
@RunWith(RobolectricTestRunner::class)
// Robolectric 4.14 simulates up to API 35; the cache code under test is version-independent.
@Config(sdk = [35])
class DownloadStoreTest {
    private val plain = ByteArray(64 * 1024) { (it % 251).toByte() }

    @Test fun downloadedBytesReadBackExactlyButAreEncryptedOnDisk() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val upstream = FakeDataSource.Factory().setFakeDataSet(FakeDataSet().setData("app://download/t1", plain))
        val directory = File(context.cacheDir, "downloads-test").apply { deleteRecursively() }
        val store = DownloadStore(context, ByteArray(16) { 7 }, upstream, directory)
        val spec = DataSpec.Builder().setUri(Uri.parse("app://download/t1")).setKey("track:t1").build()

        val writer = store.downloaderDataSourceFactory.createDataSource()
        writer.open(spec)
        DataSourceUtil.readToEnd(writer)
        writer.close()

        val reader = store.playerDataSourceFactory.createDataSource()
        reader.open(spec)
        val readBack = DataSourceUtil.readToEnd(reader)
        reader.close()
        assertThat(readBack).isEqualTo(plain)

        val onDisk = directory.walk().first { it.isFile && it.name.endsWith(".exo") }.readBytes()
        assertThat(onDisk.size).isEqualTo(plain.size)
        assertThat(onDisk).isNotEqualTo(plain)
        store.cache.release()
    }

    @Test fun streamsNeverWriteIntoTheDownloadCache() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val upstream = FakeDataSource.Factory().setFakeDataSet(FakeDataSet().setData("app://stream/t2", plain))
        val directory = File(context.cacheDir, "downloads-test-2").apply { deleteRecursively() }
        val store = DownloadStore(context, ByteArray(16) { 7 }, upstream, directory)

        val reader = store.playerDataSourceFactory.createDataSource()
        reader.open(DataSpec(Uri.parse("app://stream/t2")))
        assertThat(DataSourceUtil.readToEnd(reader)).isEqualTo(plain)
        reader.close()
        assertThat(store.cache.keys).isEmpty()
        store.cache.release()
    }
}
