package com.anish1050.hires.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anish1050.hires.data.api.Stats
import com.anish1050.hires.ui.CircleButton
import com.anish1050.hires.ui.GlassCard
import com.anish1050.hires.ui.LoadedContent
import com.anish1050.hires.ui.RemoteImage
import com.anish1050.hires.ui.ScreenPadding
import com.anish1050.hires.ui.SectionHeader
import com.anish1050.hires.ui.rememberLoader
import com.anish1050.hires.ui.theme.Card
import com.anish1050.hires.ui.theme.CardBorder
import com.anish1050.hires.ui.theme.Graphite
import com.anish1050.hires.ui.theme.Ink
import com.anish1050.hires.ui.theme.Lavender
import com.anish1050.hires.ui.theme.Mint
import com.anish1050.hires.ui.theme.Sand
import com.anish1050.hires.ui.theme.TextMuted
import com.anish1050.hires.ui.theme.TextPrimary

private val genreColors = listOf(Lavender, Sand, Mint, Graphite)

@Composable
fun StatsScreen(onSearch: () -> Unit, onSettings: () -> Unit) {
    var period by rememberSaveable { mutableStateOf("7d") }
    val stats = rememberLoader("stats-$period") { it.api.stats(period) }
    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
        SectionHeader("Sound", "Intelligence") {
            CircleButton(Icons.Default.Search, "Search", onSearch)
            CircleButton(Icons.Default.Person, "Settings", onSettings)
        }
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("7d" to "Week", "30d" to "Month").forEach { (value, label) ->
                val isSelected = value == period
                Text(
                    label,
                    color = if (isSelected) Ink else TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (isSelected) TextPrimary else Card).clickable { period = value }.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        LoadedContent(stats) { data -> StatsContent(data) }
    }
}

@Composable
private fun StatsContent(stats: Stats) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 140.dp)) {
        GlassCard(Modifier.padding(bottom = 12.dp)) {
            Column {
                Text("Listening Time", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD6D6DA))
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    val totalMinutes = stats.totalMs / 60_000
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        BigNumber("${totalMinutes / 60}", "h")
                        BigNumber("${totalMinutes % 60}", "m")
                    }
                    ArtistStack(stats.topArtists)
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Meta("Song Played", "${stats.songsPlayed}", Modifier.weight(1f))
                    Meta("Peak Time", stats.peakHour?.let(::peakWindow) ?: "–")
                }
            }
        }
        GlassCard {
            Column {
                Text("Genre Distribution", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD6D6DA))
                if (stats.genres.isEmpty()) {
                    Text("Play a few tracks and your mix shows up here.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 40.dp))
                } else {
                    GenreGrid(stats.genres)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        stats.genres.forEachIndexed { index, genre -> LegendChip(genre.genre, genreColors[index % genreColors.size]) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigNumber(value: String, unit: String) {
    Text(value, fontSize = 44.sp, fontWeight = FontWeight.Light, color = TextPrimary)
    Text(unit, fontSize = 14.sp, color = TextMuted, modifier = Modifier.padding(start = 2.dp, end = 8.dp, bottom = 8.dp))
}

@Composable
private fun ArtistStack(artists: List<Stats.TopArtist>) {
    Row {
        artists.take(3).forEachIndexed { index, artist ->
            RemoteImage(artist.imageUrl, artist.name, Modifier.offset(x = (-12 * index).dp).size(34.dp).clip(CircleShape).border(2.dp, Card, CircleShape))
        }
        val more = artists.size - 3
        if (more > 0) {
            Box(Modifier.offset(x = (-36).dp).size(34.dp).clip(CircleShape).background(Color(0xFF2A2A2E)).border(2.dp, Card, CircleShape), contentAlignment = Alignment.Center) {
                Text("+$more", fontSize = 10.sp, color = Color(0xFFDDDDDD))
            }
        }
    }
}

@Composable
private fun Meta(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier) {
        Text(label, fontSize = 11.sp, color = TextMuted)
        Text(" $value", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

/** Mockup-style pills on a faint grid: one row per slot, pill length by share of listening. */
@Composable
private fun GenreGrid(genres: List<Stats.GenreShare>) {
    val total = genres.sumOf { it.ms }.coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(170.dp).padding(vertical = 12.dp)) {
        val rows = 7
        val rowHeight = size.height / rows
        repeat(rows + 1) { r -> drawLine(CardBorder, Offset(0f, r * rowHeight), Offset(size.width, r * rowHeight), 1f) }
        val pillHeight = 12.dp.toPx()
        // Each genre gets pills spread over the rows, so the picture reads at a glance; positions are fixed per index.
        genres.forEachIndexed { index, genre ->
            val share = genre.ms.toFloat() / total
            val pills = (1 + (share * 6).toInt()).coerceAtMost(4)
            repeat(pills) { p ->
                val row = (index * 2 + p * 3) % rows
                val width = size.width * (0.14f + 0.2f * share)
                val x = ((index * 0.27f + p * 0.41f) % 1f) * (size.width - width)
                drawRoundRect(genreColors[index % genreColors.size], Offset(x, row * rowHeight + (rowHeight - pillHeight) / 2), Size(width, pillHeight), CornerRadius(pillHeight / 2))
            }
        }
    }
}

@Composable
private fun LegendChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Color(0xFFC7C7CC))
    }
}

/** 22 -> "9 PM–12 AM": the three hours around the busiest one. */
fun peakWindow(hour: Int): String {
    fun label(h: Int): String {
        val normalised = ((h % 24) + 24) % 24
        val twelve = if (normalised % 12 == 0) 12 else normalised % 12
        return "$twelve ${if (normalised < 12) "AM" else "PM"}"
    }
    return "${label(hour - 1)}–${label(hour + 2)}"
}
