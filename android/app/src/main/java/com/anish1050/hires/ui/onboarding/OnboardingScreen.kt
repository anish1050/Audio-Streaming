package com.anish1050.hires.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.anish1050.hires.AppContainer
import com.anish1050.hires.BuildConfig
import com.anish1050.hires.container
import com.anish1050.hires.data.api.ArtistRef
import com.anish1050.hires.ui.ScreenPadding
import com.anish1050.hires.ui.SectionHeader
import com.anish1050.hires.ui.theme.Card
import com.anish1050.hires.ui.theme.CardBorder
import com.anish1050.hires.ui.theme.Ink
import com.anish1050.hires.ui.theme.Lavender
import com.anish1050.hires.ui.theme.TextMuted
import com.anish1050.hires.ui.theme.TextPrimary
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private const val MIN_PICKS = 3

/** Sign in with Google, then pick genres and artists so recommendations work from the first day. */
@Composable
fun OnboardingScreen(activity: ComponentActivity) {
    val container = LocalContext.current.container
    val isSignedIn by container.authStore.isSignedIn.collectAsState()
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().padding(ScreenPadding)) {
        if (isSignedIn) TastePicker(container) else SignIn(activity, container)
    }
}

@Composable
private fun SignIn(activity: ComponentActivity, container: AppContainer) {
    val scope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Hear every", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Text("detail.", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TextMuted)
        Text("Lossless and hi-res up to 24-bit / 192 kHz, offline downloads and song recognition.", color = TextMuted, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = {
                isWorking = true
                error = null
                scope.launch {
                    error = runCatching { signInWithGoogle(activity, container) }.exceptionOrNull()?.let { "Sign-in didn't finish. Try again." }
                    isWorking = false
                }
            },
            enabled = !isWorking,
            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = Ink),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (isWorking) CircularProgressIndicator(color = Ink, modifier = Modifier.height(20.dp)) else Text("Continue with Google", fontWeight = FontWeight.Bold)
        }
        error?.let { Text(it, color = TextMuted, modifier = Modifier.padding(top = 12.dp)) }
    }
}

private suspend fun signInWithGoogle(activity: ComponentActivity, container: AppContainer) {
    val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
    val response = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
    val credential = response.credential
    check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "unexpected credential" }
    val result = container.api.signInWithGoogle(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    container.authStore.userId = result.user.id
    container.authStore.accessToken = result.accessToken
    runCatching { container.entitlements.refresh() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TastePicker(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var genres by remember { mutableStateOf(emptyList<String>()) }
    var artists by remember { mutableStateOf(emptyList<ArtistRef>()) }
    var pickedGenres by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var pickedArtists by rememberSaveable { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) {
        runCatching { container.api.home() }.onSuccess { home ->
            genres = home.genres
            artists = (home.featured + home.dailyMixes.flatMap { it.tracks }).map { it.artist }.distinctBy { it.id }
        }
    }
    val finish = { container.prefs.update { it.copy(isOnboarded = true) } }

    Column(Modifier.fillMaxSize()) {
        SectionHeader("Your", "taste")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("Pick genres you love", color = TextMuted, modifier = Modifier.padding(vertical = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.forEach { genre -> Chip(genre, genre in pickedGenres) { pickedGenres = pickedGenres.toggle(genre) } }
            }
            Text("And some artists", color = TextMuted, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                artists.forEach { artist -> Chip(artist.name, artist.id in pickedArtists) { pickedArtists = pickedArtists.toggle(artist.id) } }
            }
        }
        // A brand-new catalog may have fewer than three of either, so "Skip" is always allowed.
        val canSave = pickedGenres.isNotEmpty() && (pickedGenres.size + pickedArtists.size) >= MIN_PICKS
        Button(
            onClick = { scope.launch { runCatching { container.api.saveTaste(pickedGenres.toList(), pickedArtists.toList()) }; finish() } },
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = Ink),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Start listening", fontWeight = FontWeight.Bold) }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Text("Skip", color = TextMuted, modifier = Modifier.clickable { finish() }.padding(8.dp))
        }
    }
}

@Composable
private fun Chip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Text(
        label,
        color = if (isSelected) Ink else TextPrimary,
        fontSize = 13.sp,
        modifier = Modifier.clip(shape).background(if (isSelected) Lavender else Card).border(1.dp, CardBorder, shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
