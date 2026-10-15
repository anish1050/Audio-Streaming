package com.anish1050.hires.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import com.anish1050.hires.container
import com.anish1050.hires.data.api.AlbumRef
import com.anish1050.hires.data.api.ArtistRef
import com.anish1050.hires.data.api.DiscoverPreview
import com.anish1050.hires.data.api.Track
import com.anish1050.hires.ui.AiDiscoveryCard
import com.anish1050.hires.ui.CircleButton
import com.anish1050.hires.ui.GlassCard
import com.anish1050.hires.ui.LoadedContent
import com.anish1050.hires.ui.RemoteImage
import com.anish1050.hires.ui.ScreenPadding
import com.anish1050.hires.ui.SectionHeader
import com.anish1050.hires.ui.TrackCard
import com.anish1050.hires.ui.rememberLoader
import com.anish1050.hires.ui.theme.Mint
import com.anish1050.hires.ui.theme.TextMuted
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private data class LibraryContent(val tracks: List<Track>, val albums: List<AlbumRef>, val artists: List<ArtistRef>, val discover: DiscoverPreview)

@Composable
fun LibraryScreen(onSearch: () -> Unit, onSettings: () -> Unit, onDiscover: () -> Unit, onDownloads: () -> Unit, onAlbum: (String) -> Unit, onArtist: (String) -> Unit) {
    val container = LocalContext.current.container
    val downloads by container.downloads.downloads.collectAsStateWithLifecycle(emptyList())
    val library = rememberLoader("library") { c ->
        coroutineScope {
            val tracks = async { c.api.libraryTracks() }
            val albums = async { c.api.libraryAlbums() }
            val artists = async { c.api.libraryArtists() }
            val home = async { c.api.home() }
            LibraryContent(tracks.await(), albums.await(), artists.await(), home.await().discoverPreview)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
        SectionHeader("Music", "Library") {
            CircleButton(Icons.Default.Search, "Search", onSearch)
            CircleButton(Icons.Default.Person, "Settings", onSettings)
        }
        LoadedContent(library) { content ->
            val downloadedIds = downloads.map { it.track.id }.toSet()
            LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                itemsIndexed(content.tracks.take(3), key = { _, t -> t.id }) { index, track ->
                    TrackCard(track, onClick = { container.player.play(content.tracks, index) }, isDownloaded = track.id in downloadedIds)
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    AiDiscoveryCard(content.discover.count, content.discover.matchPercent, content.discover.sparkline, onClick = onDiscover)
                }
                item {
                    GlassCard(Modifier.padding(top = 12.dp), onClick = onDownloads) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, null, tint = Mint)
                            Text("Downloads", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp).weight(1f))
                            Text("${downloads.size}", color = TextMuted)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextMuted)
                        }
                    }
                }
                if (content.tracks.size > 3) {
                    item { LibrarySection("Liked songs") }
                    itemsIndexed(content.tracks.drop(3), key = { _, t -> "more-${t.id}" }) { index, track ->
                        TrackCard(track, onClick = { container.player.play(content.tracks, index + 3) }, isDownloaded = track.id in downloadedIds)
                    }
                }
                if (content.albums.isNotEmpty()) {
                    item { LibrarySection("Albums") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(content.albums, key = { it.id }) { album -> CoverTile(album.title, album.coverUrl, RoundedCornerShape(16.dp)) { onAlbum(album.id) } }
                        }
                    }
                }
                if (content.artists.isNotEmpty()) {
                    item { LibrarySection("Artists") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(content.artists, key = { it.id }) { artist -> CoverTile(artist.name, artist.imageUrl, CircleShape) { onArtist(artist.id) } }
                        }
                    }
                }
                if (content.tracks.isEmpty() && content.albums.isEmpty()) {
                    item { Text("Tap the heart on Now Playing to save songs here.", color = TextMuted, modifier = Modifier.padding(vertical = 24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LibrarySection(title: String) {
    Text(title, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun CoverTile(title: String, imagePath: String, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).clickable(onClick = onClick)) {
        RemoteImage(imagePath, title, Modifier.size(112.dp).clip(shape))
        Text(title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}
