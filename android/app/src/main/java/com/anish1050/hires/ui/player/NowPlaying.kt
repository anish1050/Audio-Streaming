package com.anish1050.hires.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish1050.hires.container
import com.anish1050.hires.ui.RemoteImage
import com.anish1050.hires.ui.formatDuration
import com.anish1050.hires.ui.theme.Card
import com.anish1050.hires.ui.theme.CardBorder
import com.anish1050.hires.ui.theme.Graphite
import com.anish1050.hires.ui.theme.Ink
import com.anish1050.hires.ui.theme.Lavender
import com.anish1050.hires.ui.theme.Mint
import com.anish1050.hires.ui.theme.TextMuted
import com.anish1050.hires.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val player = LocalContext.current.container.player
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    val track = nowPlaying.track ?: return
    Row(
        Modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Card)
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp)).clickable(onClick = onOpen).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(track.album.coverUrl, null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist.name, color = TextMuted, fontSize = 11.sp, maxLines = 1)
        }
        IconButton(onClick = player::togglePlay) {
            Icon(if (nowPlaying.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (nowPlaying.isPlaying) "Pause" else "Play", tint = TextPrimary)
        }
    }
}

@Composable
fun NowPlayingSheet(onUpgrade: () -> Unit) {
    val container = LocalContext.current.container
    val nowPlaying by container.player.nowPlaying.collectAsStateWithLifecycle()
    val chain by container.signalPath.chain.collectAsStateWithLifecycle()
    val entitlement by container.entitlements.current.collectAsStateWithLifecycle()
    val downloads by container.downloads.downloads.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val track = nowPlaying.track ?: return
    var isLiked by remember(track.id) { mutableStateOf(false) }
    val isDownloaded = downloads.any { it.track.id == track.id }
    LaunchedEffect(track.id) { isLiked = runCatching { container.api.libraryTracks().any { it.id == track.id } }.getOrDefault(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RemoteImage(track.album.coverUrl, track.album.title, Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(RoundedCornerShape(28.dp)))
        Spacer(Modifier.height(18.dp))
        Text(track.title, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${track.artist.name} · ${track.album.title}", color = TextMuted, fontSize = 13.sp, maxLines = 1)
        chain?.let {
            Text(
                it.label(),
                color = Mint,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(Graphite).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        val progress = if (nowPlaying.durationMs > 0) nowPlaying.positionMs.toFloat() / nowPlaying.durationMs else 0f
        DialScrubber(progress, onSeek = { container.player.seekTo((it * nowPlaying.durationMs).toLong()) })
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(nowPlaying.positionMs), color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text("−" + formatDuration((nowPlaying.durationMs - nowPlaying.positionMs).coerceAtLeast(0)), color = TextMuted, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = container.player::previous) { Icon(Icons.Default.SkipPrevious, "Previous", tint = TextPrimary) }
            Box(Modifier.size(64.dp).clip(CircleShape).background(TextPrimary).clickable(onClick = container.player::togglePlay), contentAlignment = Alignment.Center) {
                Icon(if (nowPlaying.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (nowPlaying.isPlaying) "Pause" else "Play", tint = Ink, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = container.player::next) { Icon(Icons.Default.SkipNext, "Next", tint = TextPrimary) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SheetAction(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isLiked) "Liked" else "Like", tint = if (isLiked) Lavender else TextPrimary) {
                val liking = !isLiked
                isLiked = liking
                scope.launch { runCatching { if (liking) container.api.saveToLibrary("track", track.id) else container.api.removeFromLibrary("track", track.id) } }
            }
            SheetAction(if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download, if (isDownloaded) "Downloaded" else "Download") {
                when {
                    isDownloaded -> Unit
                    !entitlement.isPremium -> onUpgrade()
                    else -> scope.launch { container.downloads.download(track, container.prefs.settings.value.quality.download) }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = TextPrimary, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = tint)
        Text(label, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
