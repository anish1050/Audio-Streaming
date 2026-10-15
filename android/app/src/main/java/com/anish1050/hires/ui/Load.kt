package com.anish1050.hires.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anish1050.hires.AppContainer
import com.anish1050.hires.container
import com.anish1050.hires.ui.theme.Lavender
import com.anish1050.hires.ui.theme.TextMuted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/** One owner for "fetch, show, retry" so read-only screens need no ViewModel of their own. Survives rotation. */
class Loader<T>(private val fetch: suspend () -> T) : ViewModel() {
    private val _state = MutableStateFlow<Load<T>>(Load.Loading)
    val state: StateFlow<Load<T>> = _state

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            if (_state.value !is Load.Ready) _state.value = Load.Loading
            _state.value = try {
                Load.Ready(fetch())
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Load.Failed("Couldn't reach the server. Check your connection.")
            } catch (e: Exception) {
                Load.Failed("Something went wrong.")
            }
        }
    }
}

@Composable
fun <T> rememberLoader(key: String, fetch: suspend (AppContainer) -> T): Loader<T> {
    val container = LocalContext.current.container
    return viewModel(key = key, factory = viewModelFactory { initializer { Loader { fetch(container) } } })
}

@Composable
fun <T> LoadedContent(loader: Loader<T>, content: @Composable (T) -> Unit) {
    val state by loader.state.collectAsStateWithLifecycle()
    when (val current = state) {
        is Load.Ready -> content(current.data)
        Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Lavender) }
        is Load.Failed -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(current.message, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = loader::reload) { Text("Try again", color = Lavender) }
        }
    }
}
