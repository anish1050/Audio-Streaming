package com.anish1050.hires.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish1050.hires.AppContainer
import com.anish1050.hires.BuildConfig
import com.anish1050.hires.container
import com.anish1050.hires.data.api.Credit
import com.anish1050.hires.playback.Quality
import com.anish1050.hires.ui.GlassCard
import com.anish1050.hires.ui.ScreenPadding
import com.anish1050.hires.ui.SectionHeader
import com.anish1050.hires.ui.theme.Card
import com.anish1050.hires.ui.theme.Ink
import com.anish1050.hires.ui.theme.Lavender
import com.anish1050.hires.ui.theme.TextMuted
import com.anish1050.hires.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val qualityChoices = listOf(Quality.HIRES to "Hi-Res", Quality.LOSSLESS to "CD", Quality.HIGH to "High", Quality.LOW to "Data saver")

@Composable
fun SettingsScreen(onBack: () -> Unit, onDownloads: () -> Unit, onUpgrade: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val settings by container.prefs.settings.collectAsStateWithLifecycle()
    val entitlement by container.entitlements.current.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isConfirmingDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding).verticalScroll(rememberScrollState()).padding(bottom = 160.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        SectionHeader("Your", "Settings")

        Label("Plan")
        GlassCard(onClick = if (entitlement.isPremium) null else onUpgrade) {
            val renewal = entitlement.expiresAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
            Text(if (entitlement.isPremium) "Premium · renews or ends $renewal" else "Free · tap to try Premium free for a week", fontSize = 13.sp)
        }
        if (entitlement.isPremium) {
            Link("Manage subscription in Google Play") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=premium&package=${BuildConfig.APPLICATION_ID}")))
            }
        }
        Link("Restore purchases") { scope.launch { container.billing.restore() } }

        Label("Streaming on Wi-Fi")
        QualityPicker(settings.quality.wifi) { q -> container.prefs.update { it.copy(quality = it.quality.copy(wifi = q)) } }
        Label("Streaming on mobile data")
        QualityPicker(settings.quality.cellular) { q -> container.prefs.update { it.copy(quality = it.quality.copy(cellular = q)) } }
        Label("Downloads")
        QualityPicker(settings.quality.download) { q -> container.prefs.update { it.copy(quality = it.quality.copy(download = q)) } }
        Link("Manage downloads", onDownloads)

        Label("Sound")
        Toggle("Hi-Res over Bluetooth", "Bluetooth re-encodes audio, so this mostly spends data", settings.quality.isHiResOverBluetooth) { on ->
            container.prefs.update { it.copy(quality = it.quality.copy(isHiResOverBluetooth = on)) }
        }
        Toggle("Even out loudness", "Turns loud masters down to -14 LUFS; never boosts", settings.isLoudnessNormalised) { on -> container.prefs.update { it.copy(isLoudnessNormalised = on) } }
        if (Build.VERSION.SDK_INT >= 34) {
            Toggle("Match USB DAC sample rate", "Plays each track at its own rate through USB DACs", settings.isUsbRateMatching) { on -> container.prefs.update { it.copy(isUsbRateMatching = on) } }
        }

        Label("Account")
        Link("Sign out") { container.signOut() }
        Link("Delete account and data") { isConfirmingDelete = true }
        Label("About")
        Text("Version ${BuildConfig.VERSION_NAME}", color = TextMuted, fontSize = 11.sp)
        Credits(container)
    }

    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("Your library, listening history and downloads are removed. An active Google Play subscription must be cancelled in Play separately.") },
            confirmButton = { TextButton(onClick = { isConfirmingDelete = false; scope.launch { deleteAccount(container) } }) { Text("Delete", color = Lavender) } },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Creative Commons BY tracks must credit artist and licence. */
@Composable
private fun Credits(container: AppContainer) {
    var credits by remember { mutableStateOf(emptyList<Credit>()) }
    LaunchedEffect(Unit) { credits = runCatching { container.api.credits() }.getOrDefault(emptyList()) }
    if (credits.isEmpty()) return
    Label("Music credits")
    credits.forEach { credit ->
        Text("${credit.title} by ${credit.artist}. ${credit.attribution.ifBlank { credit.licence }}", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
    }
}

private suspend fun deleteAccount(container: AppContainer) {
    runCatching { container.api.deleteAccount() }.onSuccess {
        container.downloads.removeAll()
        container.db.pendingPlayDao().deleteAll()
        container.prefs.update { it.copy(isOnboarded = false) }
        container.signOut()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(text, color = Lavender, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp))
}

@Composable
private fun QualityPicker(selected: Quality, onSelect: (Quality) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        qualityChoices.forEach { (quality, label) ->
            val isSelected = quality == selected
            Text(
                label,
                color = if (isSelected) Ink else TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (isSelected) TextPrimary else Card).clickable { onSelect(quality) }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun Toggle(title: String, detail: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp)
            Text(detail, color = TextMuted, fontSize = 11.sp)
        }
        Switch(checked = isOn, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Lavender, checkedThumbColor = Ink))
    }
}
