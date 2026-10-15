package com.anish1050.hires

import com.anish1050.hires.playback.Network
import com.anish1050.hires.playback.Output
import com.anish1050.hires.playback.PlayTracker
import com.anish1050.hires.playback.Quality
import com.anish1050.hires.playback.QualityPolicy
import com.anish1050.hires.playback.QualityPrefs
import com.anish1050.hires.playback.SignalChain
import com.anish1050.hires.playback.loudnessVolume
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QualityPolicyTest {
    private val prefs = QualityPrefs()

    @Test fun freeUserAsksForLow() =
        assertThat(QualityPolicy.choose(false, prefs, Network.WIFI, Output.USB_DAC)).isEqualTo(Quality.LOW)

    @Test fun premiumOnWifiGetsHiRes() =
        assertThat(QualityPolicy.choose(true, prefs, Network.WIFI, Output.WIRED)).isEqualTo(Quality.HIRES)

    @Test fun mobileDataDefaultsToLossless() =
        assertThat(QualityPolicy.choose(true, prefs, Network.CELLULAR, Output.WIRED)).isEqualTo(Quality.LOSSLESS)

    @Test fun bluetoothCapsAtLossless() =
        assertThat(QualityPolicy.choose(true, prefs, Network.WIFI, Output.BLUETOOTH)).isEqualTo(Quality.LOSSLESS)

    @Test fun bluetoothOptInKeepsHiRes() =
        assertThat(QualityPolicy.choose(true, prefs.copy(isHiResOverBluetooth = true), Network.WIFI, Output.BLUETOOTH)).isEqualTo(Quality.HIRES)

    @Test fun wireNamesRoundTrip() = Quality.entries.forEach { assertThat(Quality.fromWire(it.wire)).isEqualTo(it) }
}

class SignalPathTest {
    @Test fun usbChainIsSpelledOut() {
        val chain = SignalChain("FLAC", 96000, 24, "float", 96000, "USB DAC")
        assertThat(chain.label()).isEqualTo("FLAC 24/96 → float → 96 kHz → USB DAC")
    }

    @Test fun resamplingIsShown() {
        val chain = SignalChain("FLAC", 44100, 16, "float", 48000, "Phone speaker")
        assertThat(chain.label()).isEqualTo("FLAC 16/44.1 → float → 48 kHz (resampled) → Phone speaker")
    }

    @Test fun opusHasNoBitDepth() {
        assertThat(SignalChain("Opus", 48000, 0, "", 0, "Wired").label()).isEqualTo("Opus")
    }

    @Test fun loudnessOffIsUnity() = assertThat(loudnessVolume(-8f, isEnabled = false)).isEqualTo(1f)

    @Test fun loudTrackIsTurnedDown() = assertThat(loudnessVolume(-8f, isEnabled = true)).isWithin(0.001f).of(0.501f)

    @Test fun quietTrackIsNeverBoosted() = assertThat(loudnessVolume(-20f, isEnabled = true)).isEqualTo(1f)
}

class PlayTrackerTest {
    private val tracker = PlayTracker(newEventId = { "e1" })

    @Test fun countsOnlyPlayingTime() {
        tracker.onTrackChanged("t1", 0)
        tracker.onPlayingChanged(true, 0)
        tracker.onPlayingChanged(false, 10_000)
        tracker.onPlayingChanged(true, 20_000)
        val listen = tracker.onTrackChanged("t2", 25_000)!!
        assertThat(listen.trackId).isEqualTo("t1")
        assertThat(listen.msPlayed).isEqualTo(15_000)
    }

    @Test fun skipsUnderThreeSeconds() {
        tracker.onTrackChanged("t1", 0)
        tracker.onPlayingChanged(true, 0)
        assertThat(tracker.onTrackChanged("t2", 2_000)).isNull()
    }

    @Test fun keepsCountingAcrossAutoAdvance() {
        tracker.onTrackChanged("t1", 0)
        tracker.onPlayingChanged(true, 0)
        tracker.onTrackChanged("t2", 5_000)
        val second = tracker.flush(9_000)!!
        assertThat(second.trackId).isEqualTo("t2")
        assertThat(second.msPlayed).isEqualTo(4_000)
    }
}
