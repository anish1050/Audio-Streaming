package com.anish1050.hires.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish1050.hires.container
import com.anish1050.hires.ui.CircleButton
import com.anish1050.hires.ui.LoadedContent
import com.anish1050.hires.ui.RemoteImage
import com.anish1050.hires.ui.ScreenPadding
import com.anish1050.hires.ui.TrackRow
import com.anish1050.hires.ui.rememberLoader
import com.anish1050.hires.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(albumId: String, onBack: () -> Unit, onArtist: (String) -> Unit, onUpgrade: () -> Unit) {
    val container = LocalContext.current.container
    val entitlement by container.entitlements.current.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val album = rememberLoader("album-$albumId") { it.api.album(albumId) }
    LoadedContent(album) { detail ->
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = ScreenPadding), contentPadding = PaddingValues(bottom = 160.dp)) {
            item { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            item { RemoteImage(detail.coverUrl, detail.title, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp))) }
            item {
                Text(detail.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp))
                detail.artist?.let { artist ->
                    Text("${artist.name} · ${detail.year}", color = TextMuted, modifier = Modifier.clickable { onArtist(artist.id) }.padding(vertical = 4.dp))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircleButton(Icons.Default.PlayArrow, "Play album", onClick = { container.player.play(detail.tracks) }, size = 48.dp)
                    CircleButton(Icons.Default.FavoriteBorder, "Save album", onClick = { scope.launch { runCatching { container.api.saveToLibrary("album", detail.id) } } }, size = 48.dp)
                    CircleButton(Icons.Default.Download, "Download album", size = 48.dp, onClick = {
                        if (!entitlement.isPremium) onUpgrade()
                        else scope.launch { detail.tracks.forEach { container.downloads.download(it, container.prefs.settings.value.quality.download) } }
                    })
                }
            }
            itemsIndexed(detail.tracks, key = { _, t -> t.id }) { index, track -> TrackRow(track, onClick = { container.player.play(detail.tracks, index) }, leading = "${track.trackNo}") }
        }
    }
}

@Composable
fun ArtistScreen(artistId: String, onBack: () -> Unit, onAlbum: (String) -> Unit) {
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val artist = rememberLoader("artist-$artistId") { it.api.artist(artistId) }
    LoadedContent(artist) { detail ->
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = ScreenPadding), contentPadding = PaddingValues(bottom = 160.dp)) {
            item { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    RemoteImage(detail.imageUrl, detail.name, Modifier.size(160.dp).clip(CircleShape))
                    Text(detail.name, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
                    if (detail.bio.isNotBlank()) Text(detail.bio, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircleButton(Icons.Default.PlayArrow, "Play top songs", onClick = { container.player.play(detail.topTracks) }, size = 48.dp)
                        CircleButton(Icons.Default.FavoriteBorder, "Follow", onClick = { scope.launch { runCatching { container.api.saveToLibrary("artist", detail.id) } } }, size = 48.dp)
                    }
                }
            }
            item { Text("Top songs", color = TextMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp)) }
            itemsIndexed(detail.topTracks, key = { _, t -> t.id }) { index, track -> TrackRow(track, onClick = { container.player.play(detail.topTracks, index) }) }
            if (detail.albums.isNotEmpty()) {
                item { Text("Albums", color = TextMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(detail.albums, key = { it.id }) { album ->
                            Column(Modifier.width(120.dp).clickable { onAlbum(album.id) }) {
                                RemoteImage(album.coverUrl, album.title, Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)))
                                Text(album.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                Text("${album.year}", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
