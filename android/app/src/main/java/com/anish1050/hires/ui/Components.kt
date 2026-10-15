package com.anish1050.hires.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anish1050.hires.container
import com.anish1050.hires.data.api.Track
import com.anish1050.hires.ui.theme.AiGradient
import com.anish1050.hires.ui.theme.Card
import com.anish1050.hires.ui.theme.CardBorder
import com.anish1050.hires.ui.theme.CardGradient
import com.anish1050.hires.ui.theme.Graphite
import com.anish1050.hires.ui.theme.Ink
import com.anish1050.hires.ui.theme.Mint
import com.anish1050.hires.ui.theme.TextMuted
import com.anish1050.hires.ui.theme.TextPrimary

val ScreenPadding = 16.dp

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardGradient)
            .border(1.dp, CardBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) { content() }
}

/** The mockup's two-tone heading: "Sound" white over "Intelligence" grey. */
@Composable
fun SectionHeader(title: String, subtitle: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.headlineMedium, color = TextMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
    }
}

@Composable
fun CircleButton(icon: ImageVector, description: String, onClick: () -> Unit, size: Dp = 40.dp, background: Color = Card) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background).border(1.dp, CardBorder, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(size * 0.45f)) }
}

@Composable
fun RemoteImage(path: String, description: String?, modifier: Modifier = Modifier) {
    val api = LocalContext.current.container.api
    AsyncImage(
        model = api.absoluteUrl(path),
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(Graphite),
    )
}

@Composable
fun ArtistChip(name: String, imagePath: String) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF222225)).padding(start = 3.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(imagePath, null, Modifier.size(16.dp).clip(CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(name, fontSize = 10.sp, color = Color(0xFFBDBDC2), maxLines = 1)
    }
}

/** The Library-style card from the mockup: cover, title, subtitle, artist chip, arrow. */
@Composable
fun TrackCard(track: Track, onClick: () -> Unit, subtitle: String = track.album.title, isDownloaded: Boolean = false) {
    GlassCard(onClick = onClick, modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(track.album.coverUrl, null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                ArtistChip(track.artist.name, track.artist.imageUrl)
            }
            if (isDownloaded) Icon(Icons.Default.Download, "Downloaded", tint = Mint, modifier = Modifier.size(16.dp))
            track.matchPercent?.let { MatchPill(it) }
        }
    }
}

@Composable
fun TrackRow(track: Track, onClick: () -> Unit, leading: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Text(leading, color = TextMuted, modifier = Modifier.width(28.dp), fontSize = 12.sp)
        else RemoteImage(track.album.coverUrl, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist.name, color = TextMuted, fontSize = 11.sp, maxLines = 1)
        }
        track.bestQuality?.takeIf { it.quality == "hires" }?.let { Text("HI-RES", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        track.matchPercent?.let { MatchPill(it) }
    }
}

@Composable
fun MatchPill(percent: Int) {
    Text(
        "$percent%",
        color = Ink,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(10.dp)).background(Mint).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Rose-gradient "AI Discovery" card with count, average match and a dotted arc sparkline. */
@Composable
fun AiDiscoveryCard(count: Int, matchPercent: Int, sparkline: List<Int>, onClick: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(AiGradient).border(1.dp, Color(0x14FFFFFF), shape).clickable(onClick = onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI Discovery", color = Color(0xFFF2E3DE), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFFF2E3DE))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$count", fontSize = 40.sp, fontWeight = FontWeight.Light, color = TextPrimary)
            Text(" tracks", color = Color(0xFFE8D3CD), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp).weight(1f))
            Text("AI Match $matchPercent%", color = Color(0xFFF2E3DE), fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
        Sparkline(sparkline)
    }
}

// Dots along a baseline; the strongest matches are bright and joined by arcs.
@Composable
private fun Sparkline(points: List<Int>) {
    Canvas(Modifier.fillMaxWidth().height(40.dp)) {
        if (points.isEmpty()) return@Canvas
        val step = size.width / 16
        val baseline = size.height - 4.dp.toPx()
        val bright = points.withIndex().filter { it.value >= 90 }.map { it.index }
        bright.zipWithNext().forEach { (a, b) ->
            val left = step * a + step / 2
            val right = step * b + step / 2
            drawArc(Color(0xFFF4D6CC), 180f, 180f, false, topLeft = Offset(left, baseline - (right - left) / 2), size = Size(right - left, right - left), style = Stroke(1.2.dp.toPx()))
        }
        repeat(16) { i ->
            val isBright = i in bright
            drawCircle(Color(0xFFFFE7DE).copy(alpha = if (isBright) 1f else 0.45f), radius = (if (isBright) 3 else 2).dp.toPx(), center = Offset(step * i + step / 2, baseline))
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
