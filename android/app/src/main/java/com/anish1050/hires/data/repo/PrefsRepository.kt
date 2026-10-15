package com.anish1050.hires.data.repo

import android.content.Context
import com.anish1050.hires.playback.Quality
import com.anish1050.hires.playback.QualityPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val quality: QualityPrefs = QualityPrefs(),
    val isLoudnessNormalised: Boolean = false,
    val isUsbRateMatching: Boolean = true,
    val isOnboarded: Boolean = false,
)

/** Listening preferences. Not secret, so plain SharedPreferences. */
class PrefsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_WIFI, next.quality.wifi.wire)
            .putString(KEY_CELLULAR, next.quality.cellular.wire)
            .putString(KEY_DOWNLOAD, next.quality.download.wire)
            .putBoolean(KEY_BLUETOOTH_HIRES, next.quality.isHiResOverBluetooth)
            .putBoolean(KEY_LOUDNESS, next.isLoudnessNormalised)
            .putBoolean(KEY_USB, next.isUsbRateMatching)
            .putBoolean(KEY_ONBOARDED, next.isOnboarded)
            .apply()
        _settings.value = next
    }

    private fun read(): Settings {
        val defaults = Settings()
        val quality = { key: String, fallback: Quality -> prefs.getString(key, null)?.let(Quality::fromWire) ?: fallback }
        return Settings(
            quality = QualityPrefs(
                wifi = quality(KEY_WIFI, defaults.quality.wifi),
                cellular = quality(KEY_CELLULAR, defaults.quality.cellular),
                download = quality(KEY_DOWNLOAD, defaults.quality.download),
                isHiResOverBluetooth = prefs.getBoolean(KEY_BLUETOOTH_HIRES, defaults.quality.isHiResOverBluetooth),
            ),
            isLoudnessNormalised = prefs.getBoolean(KEY_LOUDNESS, defaults.isLoudnessNormalised),
            isUsbRateMatching = prefs.getBoolean(KEY_USB, defaults.isUsbRateMatching),
            isOnboarded = prefs.getBoolean(KEY_ONBOARDED, defaults.isOnboarded),
        )
    }

    private companion object {
        const val KEY_WIFI = "quality_wifi"
        const val KEY_CELLULAR = "quality_cellular"
        const val KEY_DOWNLOAD = "quality_download"
        const val KEY_BLUETOOTH_HIRES = "hires_over_bluetooth"
        const val KEY_LOUDNESS = "loudness_normalised"
        const val KEY_USB = "usb_rate_matching"
        const val KEY_ONBOARDED = "onboarded"
    }
}
