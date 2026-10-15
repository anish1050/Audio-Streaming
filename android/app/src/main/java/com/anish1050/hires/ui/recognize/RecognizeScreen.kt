package com.anish1050.hires.ui.recognize

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anish1050.hires.AppContainer
import com.anish1050.hires.R
import com.anish1050.hires.container
import com.anish1050.hires.data.api.Recognition
import com.anish1050.hires.ui.CircleButton
import com.anish1050.hires.ui.ScreenPadding
import com.anish1050.hires.ui.TrackCard
import com.anish1050.hires.ui.formatDuration
import com.anish1050.hires.ui.theme.Ink
import com.anish1050.hires.ui.theme.Lavender
import com.anish1050.hires.ui.theme.TextMuted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val LISTEN_SECONDS = 8

sealed interface RecognizeState {
    data object Idle : RecognizeState
    data object Listening : RecognizeState
    data object Matching : RecognizeState
    data class Done(val result: Recognition) : RecognizeState
    data class Failed(val message: String) : RecognizeState
}

/** Keeps a recording or match in progress alive across rotation. */
class RecognizeViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<RecognizeState>(RecognizeState.Idle)
    val state: StateFlow<RecognizeState> = _state

    @android.annotation.SuppressLint("MissingPermission") // The screen only calls this after the permission is granted.
    fun listen() {
        if (_state.value == RecognizeState.Listening || _state.value == RecognizeState.Matching) return
        viewModelScope.launch {
            _state.value = RecognizeState.Listening
            _state.value = try {
                val clip = container.micRecorder.record(LISTEN_SECONDS)
                _state.value = RecognizeState.Matching
                RecognizeState.Done(container.api.recognize(clip))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RecognizeState.Failed("Couldn't listen or reach the server. Try again.")
            }
        }
    }

    fun request(title: String, artist: String) {
        viewModelScope.launch { runCatching { container.api.requestTrack(title, artist) } }
    }
}

@Composable
fun RecognizeScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val viewModel: RecognizeViewModel = viewModel(factory = viewModelFactory { initializer { RecognizeViewModel(container) } })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> if (isGranted) viewModel.listen() }
    val start = {
        val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (isGranted) viewModel.listen() else askPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { CircleButton(Icons.Default.Close, "Close", onClose) }
        Spacer(Modifier.height(60.dp))
        PulseButton(isActive = state == RecognizeState.Listening, onClick = start)
        Spacer(Modifier.height(56.dp))
        val (title, subtitle) = when (val current = state) {
            RecognizeState.Idle -> "Tap to recognize" to stringResource(R.string.mic_rationale)
            RecognizeState.Listening -> "Listening…" to "Hold your phone near the music"
            RecognizeState.Matching -> "Matching…" to "Comparing with our catalog"
            is RecognizeState.Done -> if (current.result.match != null) "Found it" to "" else "Not in our catalog yet" to ""
            is RecognizeState.Failed -> "Something went wrong" to current.message
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        if (subtitle.isNotEmpty()) Text(subtitle, color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        (state as? RecognizeState.Done)?.result?.let { result -> RecognitionResult(result, container, onRequest = viewModel::request) }
    }
}

@Composable
private fun RecognitionResult(result: Recognition, container: AppContainer, onRequest: (String, String) -> Unit) {
    Spacer(Modifier.height(24.dp))
    val match = result.match
    if (match != null) {
        // The clip ended 8 seconds after it started, so join the song where the room is now.
        val nowAtMs = match.offsetMs + LISTEN_SECONDS * 1000
        TrackCard(match.track, onClick = { container.player.play(listOf(match.track), 0, nowAtMs) }, subtitle = "at ${formatDuration(match.offsetMs)} · tap to play from here")
        return
    }
    result.external?.let { external ->
        Text("${external.title} · ${external.artist}", fontWeight = FontWeight.Bold)
        TextButton(onClick = { onRequest(external.title, external.artist) }) { Text("Request it", color = Lavender) }
    }
}

@Composable
private fun PulseButton(isActive: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(1f, if (isActive) 1.25f else 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(220.dp).scale(pulse).clip(CircleShape).background(Lavender.copy(alpha = 0.06f)))
        Box(Modifier.size(180.dp).scale(pulse).clip(CircleShape).background(Lavender.copy(alpha = 0.12f)))
        Box(Modifier.size(140.dp).clip(CircleShape).background(Lavender).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, "Recognize", tint = Ink, modifier = Modifier.size(48.dp))
        }
    }
}
