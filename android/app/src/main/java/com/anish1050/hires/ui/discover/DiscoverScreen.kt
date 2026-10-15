package com.anish1050.hires.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anish1050.hires.container
import com.anish1050.hires.ui.AiDiscoveryCard
import com.anish1050.hires.ui.CircleButton
import com.anish1050.hires.ui.GlassCard
import com.anish1050.hires.ui.LoadedContent
import com.anish1050.hires.ui.ScreenPadding
import com.anish1050.hires.ui.SectionHeader
import com.anish1050.hires.ui.TrackRow
import com.anish1050.hires.ui.rememberLoader
import com.anish1050.hires.ui.theme.Ink
import com.anish1050.hires.ui.theme.Lavender
import com.anish1050.hires.ui.theme.TextMuted

@Composable
fun DiscoverScreen(onRecognize: () -> Unit) {
    val player = LocalContext.current.container.player
    val discover = rememberLoader("discover") { it.api.discover() }
    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
        LoadedContent(discover) { data ->
            val tracks = data.tracks
            LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                item {
                    SectionHeader("AI", "Discovery") { CircleButton(Icons.Default.PlayArrow, "Play all", onClick = { player.play(tracks) }) }
                }
                item {
                    val percents = tracks.mapNotNull { it.matchPercent }
                    AiDiscoveryCard(tracks.size, if (percents.isEmpty()) 0 else percents.average().toInt(), percents.take(16), onClick = { player.play(tracks) })
                }
                item { RecognizeCard(onRecognize) }
                if (tracks.isEmpty()) {
                    item { Text("Play some music and picks made for you will appear here.", color = TextMuted, modifier = Modifier.padding(vertical = 24.dp)) }
                }
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track -> TrackRow(track, onClick = { player.play(tracks, index) }) }
            }
        }
    }
}

@Composable
private fun RecognizeCard(onRecognize: () -> Unit) {
    GlassCard(Modifier.padding(vertical = 12.dp), onClick = onRecognize) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Lavender), contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, null, tint = Ink) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.fillMaxWidth()) {
                Text("Recognize a song", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Listens for 8 seconds", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}
