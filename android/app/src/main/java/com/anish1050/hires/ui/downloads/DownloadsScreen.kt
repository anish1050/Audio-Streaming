package com.anish1050.hires.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish1050.hires.container
import com.anish1050.hires.data.db.DownloadState
import com.anish1050.hires.ui.GlassCard
import com.anish1050.hires.ui.RemoteImage
import com.anish1050.hires.ui.ScreenPadding
import com.anish1050.hires.ui.SectionHeader
import com.anish1050.hires.ui.theme.Lavender
import com.anish1050.hires.ui.theme.Mint
import com.anish1050.hires.ui.theme.TextMuted
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val downloads by container.downloads.downloads.collectAsStateWithLifecycle(emptyList())
    val entitlement by container.entitlements.current.collectAsStateWithLifecycle()
    val canPlay = entitlement.canPlayOffline(Instant.now())
    val completed = downloads.filter { it.state == DownloadState.COMPLETED }
    val totalBytes = downloads.sumOf { it.bytes }

    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        SectionHeader("Your", "Downloads") {
            if (downloads.isNotEmpty()) TextButton(onClick = { scope.launch { container.downloads.removeAll() } }) { Text("Remove all", color = Lavender) }
        }
        Text("${downloads.size} tracks · ${formatBytes(totalBytes)} on this phone", color = TextMuted, fontSize = 12.sp)
        if (!canPlay && downloads.isNotEmpty()) {
            GlassCard(Modifier.padding(top = 12.dp)) { Text("Go online to renew Premium; downloads play again once it's confirmed.", fontSize = 12.sp) }
        }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp)) {
            itemsIndexed(downloads, key = { _, d -> d.track.id }) { _, download ->
                val isReady = download.state == DownloadState.COMPLETED
                val isPlayable = canPlay && isReady
                Row(
                    Modifier.fillMaxWidth().alpha(if (isPlayable) 1f else 0.5f)
                        .clickable(enabled = isPlayable) { container.player.play(completed.map { it.track }, completed.indexOf(download)) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteImage(download.track.album.coverUrl, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(download.track.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val status = when (download.state) {
                            DownloadState.COMPLETED -> "${download.quality.wire.uppercase(Locale.US)} · ${formatBytes(download.bytes)}"
                            DownloadState.FAILED -> "Failed, tap the download button to retry"
                            else -> "Downloading…"
                        }
                        Text(status, color = if (isReady) Mint else TextMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { scope.launch { container.downloads.remove(download.track.id) } }) { Icon(Icons.Default.Delete, "Remove download", tint = TextMuted) }
                }
            }
        }
        if (canPlay && completed.isNotEmpty()) {
            TextButton(onClick = { container.player.play(completed.map { it.track }) }) { Text("Play all downloads", color = Lavender) }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.0f MB", bytes / 1e6)
    else -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
}
