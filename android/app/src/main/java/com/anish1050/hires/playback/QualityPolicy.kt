package com.anish1050.hires.playback

/** Same names and order as the server's quality ladder (shared/qualities.json). */
enum class Quality(val wire: String) {
    PREVIEW("preview"), LOW("low"), HIGH("high"), LOSSLESS("lossless"), HIRES("hires");

    companion object {
        fun fromWire(value: String): Quality = entries.firstOrNull { it.wire == value } ?: LOW
    }
}

enum class Network { WIFI, CELLULAR, OFFLINE }

enum class Output { BUILT_IN, WIRED, USB_DAC, BLUETOOTH }

data class QualityPrefs(
    val wifi: Quality = Quality.HIRES,
    val cellular: Quality = Quality.LOSSLESS,
    val download: Quality = Quality.HIRES,
    val isHiResOverBluetooth: Boolean = false,
)

object QualityPolicy {
    /** What to ask the server for. It falls back to the best tier that exists, and gives free users previews. */
    fun choose(isPremium: Boolean, prefs: QualityPrefs, network: Network, output: Output): Quality {
        if (!isPremium) return Quality.LOW
        val wanted = if (network == Network.CELLULAR) prefs.cellular else prefs.wifi
        // Bluetooth re-encodes to a lossy codec, so hi-res only spends data.
        val isBluetoothCapped = output == Output.BLUETOOTH && !prefs.isHiResOverBluetooth
        return if (isBluetoothCapped) minOf(wanted, Quality.LOSSLESS) else wanted
    }
}
