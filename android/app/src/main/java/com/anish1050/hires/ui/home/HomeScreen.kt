package com.anish1050.hires.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish1050.hires.container
import com.anish1050.hires.data.api.Home
import com.anish1050.hires.data.api.Track
import com.anish1050.hires.ui.CircleButton
import com.anish1050.hires.ui.LoadedContent
import com.anish1050.hires.ui.RemoteImage
import com.anish1050.hires.ui.ScreenPadding
import com.anish1050.hires.ui.SectionHeader
import com.anish1050.hires.ui.TrackRow
import com.anish1050.hires.ui.player.DialScrubber
import com.anish1050.hires.ui.rememberLoader
import com.anish1050.hires.ui.theme.Ink
import com.anish1050.hires.ui.theme.TextMuted
import com.anish1050.hires.ui.theme.TextPrimary
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(onSearch: () -> Unit, onLibrary: () -> Unit, onSettings: () -> Unit) {
    val home = rememberLoader("home") { it.api.home() }
    LoadedContent(home) { data -> HomeContent(data, onSearch, onLibrary, onSettings) }
}

@Composable
private fun HomeContent(home: Home, onSearch: () -> Unit, onLibrary: () -> Unit, onSettings: () -> Unit) {
    val player = LocalContext.current.container.player
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    var selectedMix by rememberSaveable { mutableIntStateOf(0) }
    val mix = home.dailyMixes.getOrNull(selectedMix)
    // Without listening history or taste there are no mixes yet; Featured stands in.
    val heroTracks = mix?.tracks?.takeIf { it.isNotEmpty() } ?: home.featured

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(620.dp)) {
                heroTracks.firstOrNull()?.let { HeroBackdrop(it) }
                Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
                    SectionHeader("Featured", "Sounds") {
                        CircleButton(Icons.Default.LibraryMusic, "Library", onLibrary)
                        CircleButton(Icons.Default.Person, "Settings", onSettings)
                    }
                    Spacer(Modifier.height(90.dp))
                    MixTitle(kicker = mix?.title?.uppercase() ?: "FEATURED", line1 = (mix?.subtitle ?: "Hi-Res").uppercase(), line2 = if (mix != null) "MIX" else "PICKS")
                    CoverCarousel(heroTracks, onPlay = { index -> player.play(heroTracks, index) })
                    val progress = if (nowPlaying.durationMs > 0) nowPlaying.positionMs.toFloat() / nowPlaying.durationMs else 0f
                    DialScrubber(progress, onSeek = { player.seekTo((it * nowPlaying.durationMs).toLong()) })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = player::previous) { Icon(Icons.Default.SkipPrevious, "Previous", tint = TextPrimary) }
                        Spacer(Modifier.width(60.dp))
                        IconButton(onClick = player::next) { Icon(Icons.Default.SkipNext, "Next", tint = TextPrimary) }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        CircleButton(Icons.Default.Search, "Search", onSearch)
                        CircleButton(
                            if (nowPlaying.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (nowPlaying.isPlaying) "Pause" else "Play mix",
                            onClick = { if (nowPlaying.track == null) player.play(heroTracks) else player.togglePlay() },
                        )
                        CircleButton(Icons.Default.LibraryMusic, "Library", onLibrary)
                    }
                }
            }
        }
        if (home.dailyMixes.size > 1) {
            item { SectionTitle("Your Daily Mixes") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = ScreenPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(home.dailyMixes) { index, dailyMix ->
                        val isSelected = index == selectedMix
                        Text(
                            "${dailyMix.title} · ${dailyMix.subtitle}",
                            color = if (isSelected) Ink else TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (isSelected) TextPrimary else Color(0xFF1B1B1E))
                                .clickable { selectedMix = index }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        if (home.recent.isNotEmpty()) {
            item { SectionTitle("Recently played") }
            items(home.recent, key = { "recent-${it.id}" }) { track -> TrackRowPadded(track) { player.play(home.recent, home.recent.indexOf(track)) } }
        }
        if (home.featured.isNotEmpty()) {
            item { SectionTitle("Featured") }
            items(home.featured, key = { "featured-${it.id}" }) { track -> TrackRowPadded(track) { player.play(home.featured, home.featured.indexOf(track)) } }
        }
    }
}

@Composable
private fun HeroBackdrop(track: Track) {
    Box(Modifier.fillMaxSize()) {
        RemoteImage(track.album.coverUrl, null, Modifier.fillMaxSize().blur(40.dp))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.35f), Ink.copy(alpha = 0.2f), Ink), endY = 1600f)))
    }
}

@Composable
private fun MixTitle(kicker: String, line1: String, line2: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(kicker, fontSize = 10.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDDDDDD))
        Text(line1, fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Text(line2, fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF9A9AA0))
    }
}

/** Circular covers; the centred one is scaled up like the mockup. Tap a cover to play from there. */
@Composable
private fun CoverCarousel(tracks: List<Track>, onPlay: (Int) -> Unit) {
    if (tracks.isEmpty()) return
    val pager = rememberPagerState { tracks.size }
    HorizontalPager(
        state = pager,
        pageSize = PageSize.Fixed(84.dp),
        contentPadding = PaddingValues(horizontal = 140.dp),
        pageSpacing = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(120.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { page ->
        val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceAtMost(1f)
        val scale = 1.3f - 0.5f * distance
        RemoteImage(
            tracks[page].album.coverUrl,
            tracks[page].title,
            Modifier.size(84.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = 1f - 0.3f * distance }
                .clip(CircleShape).border(2.dp, Color(0x14FFFFFF), CircleShape).clickable { onPlay(page) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp))
}

@Composable
private fun TrackRowPadded(track: Track, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = ScreenPadding)) { TrackRow(track, onClick) }
}
