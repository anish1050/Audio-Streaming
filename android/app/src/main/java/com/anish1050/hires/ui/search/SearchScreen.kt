package com.anish1050.hires.ui.search

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anish1050.hires.container
import com.anish1050.hires.data.api.SearchResults
import com.anish1050.hires.ui.RemoteImage
import com.anish1050.hires.ui.ScreenPadding
import com.anish1050.hires.ui.TrackRow
import com.anish1050.hires.ui.theme.CardBorder
import com.anish1050.hires.ui.theme.Lavender
import com.anish1050.hires.ui.theme.TextMuted
import kotlinx.coroutines.delay

private const val DEBOUNCE_MS = 300L

@Composable
fun SearchScreen(onBack: () -> Unit, onAlbum: (String) -> Unit, onArtist: (String) -> Unit) {
    val container = LocalContext.current.container
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Restarting on every keystroke cancels the previous wait, so only a pause in typing searches.
    LaunchedEffect(query) {
        if (query.isBlank()) { results = null; return@LaunchedEffect }
        delay(DEBOUNCE_MS)
        results = runCatching { container.api.search(query.trim()) }.getOrNull()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                placeholder = { Text("Songs, albums, artists") },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Lavender, unfocusedBorderColor = CardBorder),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        val found = results ?: return@Column
        LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
            if (found.artists.isNotEmpty()) item { Heading("Artists") }
            items(found.artists, key = { "artist-${it.id}" }) { artist ->
                ResultRow(artist.name, "Artist", artist.imageUrl, isRound = true) { onArtist(artist.id) }
            }
            if (found.albums.isNotEmpty()) item { Heading("Albums") }
            items(found.albums, key = { "album-${it.id}" }) { album ->
                ResultRow(album.title, "Album", album.coverUrl, isRound = false) { onAlbum(album.id) }
            }
            if (found.tracks.isNotEmpty()) item { Heading("Songs") }
            itemsIndexed(found.tracks, key = { _, t -> "track-${t.id}" }) { index, track -> TrackRow(track, onClick = { container.player.play(found.tracks, index) }) }
            if (found.tracks.isEmpty() && found.albums.isEmpty() && found.artists.isEmpty()) {
                item { Text("Nothing found for \"$query\".", color = TextMuted, modifier = Modifier.padding(vertical = 24.dp)) }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun ResultRow(title: String, kind: String, imagePath: String, isRound: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RemoteImage(imagePath, null, Modifier.size(44.dp).clip(if (isRound) CircleShape else RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(kind, color = TextMuted, fontSize = 11.sp)
        }
    }
}
