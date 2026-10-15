package com.anish1050.hires.playback

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

// Highest-fidelity route wins when several outputs are connected, matching what Android routes media to.
fun currentOutput(audioManager: AudioManager): Output {
    val types = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
    return when {
        AudioDeviceInfo.TYPE_USB_DEVICE in types || AudioDeviceInfo.TYPE_USB_HEADSET in types -> Output.USB_DAC
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES in types || AudioDeviceInfo.TYPE_WIRED_HEADSET in types -> Output.WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in types -> Output.BLUETOOTH
        else -> Output.BUILT_IN
    }
}

fun outputLabel(output: Output) = when (output) {
    Output.USB_DAC -> "USB DAC"
    Output.WIRED -> "Wired"
    Output.BLUETOOTH -> "Bluetooth"
    Output.BUILT_IN -> "Phone speaker"
}

fun currentNetwork(connectivity: ConnectivityManager): Network {
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return Network.OFFLINE
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Network.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Network.CELLULAR
        else -> Network.WIFI
    }
}
