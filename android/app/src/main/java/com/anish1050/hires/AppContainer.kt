package com.anish1050.hires

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.scheduler.Requirements
import com.anish1050.hires.billing.PlayBilling
import com.anish1050.hires.data.api.AuthStore
import com.anish1050.hires.data.api.HiResApi
import com.anish1050.hires.data.db.HiResDatabase
import com.anish1050.hires.data.repo.EntitlementRepository
import com.anish1050.hires.data.repo.PlayEventRepository
import com.anish1050.hires.data.repo.PrefsRepository
import com.anish1050.hires.download.DownloadStore
import com.anish1050.hires.download.DownloadsRepository
import com.anish1050.hires.download.HiResDownloadService
import com.anish1050.hires.playback.AudioFacts
import com.anish1050.hires.playback.PlayerConnection
import com.anish1050.hires.playback.Quality
import com.anish1050.hires.playback.QualityPolicy
import com.anish1050.hires.playback.SignalPath
import com.anish1050.hires.playback.currentNetwork
import com.anish1050.hires.playback.currentOutput
import com.anish1050.hires.playback.outputLabel
import com.anish1050.hires.playback.resolvingFactory
import com.anish1050.hires.recognize.MicRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.Executors

// Manual wiring instead of a DI framework: one object, no codegen, nothing on
// the launch path until a screen asks for it.
@OptIn(UnstableApi::class)
class AppContainer(private val context: Context) {
    // Process-lifetime scope for work that must outlive the screen that started it.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val authStore: AuthStore by lazy { AuthStore(context) }
    val api: HiResApi by lazy { HiResApi(BuildConfig.API_BASE_URL, authStore) }
    val db: HiResDatabase by lazy { HiResDatabase.build(context) }
    val prefs: PrefsRepository by lazy { PrefsRepository(context) }
    val entitlements: EntitlementRepository by lazy { EntitlementRepository(api, authStore) }
    val playEvents: PlayEventRepository by lazy { PlayEventRepository(db.pendingPlayDao(), api) }
    val billing: PlayBilling by lazy { PlayBilling(context, api, entitlements, scope) }
    val micRecorder: MicRecorder by lazy { MicRecorder() }

    private val audioManager by lazy { context.getSystemService(AudioManager::class.java) }
    private val connectivity by lazy { context.getSystemService(ConnectivityManager::class.java) }

    val audioFacts = AudioFacts()
    val signalPath by lazy { SignalPath({ audioFacts[it]?.bitDepth ?: 0 }, { outputLabel(currentOutput(audioManager)) }) }

    fun streamQuality(): Quality = QualityPolicy.choose(entitlements.current.value.isPremium, prefs.settings.value.quality, currentNetwork(connectivity), currentOutput(audioManager))

    val downloadStore: DownloadStore by lazy {
        DownloadStore(context, authStore.downloadKey, resolvingFactory(api, OkHttpDataSource.Factory(api.client), audioFacts, ::streamQuality))
    }

    // Built from the encrypting factory: DownloadManager's convenience constructor would write plaintext.
    val downloadManager: DownloadManager by lazy {
        DownloadManager(
            context,
            DefaultDownloadIndex(downloadStore.databaseProvider),
            DefaultDownloaderFactory(downloadStore.downloaderDataSourceFactory, Executors.newFixedThreadPool(2)),
        ).apply {
            maxParallelDownloads = 2
            requirements = Requirements(Requirements.NETWORK)
        }
    }
    val downloadNotifications by lazy { DownloadNotificationHelper(context, HiResDownloadService.CHANNEL_ID) }
    val downloads: DownloadsRepository by lazy { DownloadsRepository(context, db.downloadDao(), downloadManager, scope) }
    val player: PlayerConnection by lazy { PlayerConnection(context, api, audioFacts, downloads) }

    /** Keeps downloads and their key: a returning user can play them after signing back in. */
    fun signOut() {
        authStore.clear()
        entitlements.clear()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as HiResApp).container
