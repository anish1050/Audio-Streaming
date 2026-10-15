package com.anish1050.hires

import com.anish1050.hires.ui.stats.peakWindow
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PeakWindowTest {
    @Test fun lateEvening() = assertThat(peakWindow(22)).isEqualTo("9 PM–12 AM")
    @Test fun aroundMidnight() = assertThat(peakWindow(0)).isEqualTo("11 PM–2 AM")
    @Test fun afternoon() = assertThat(peakWindow(13)).isEqualTo("12 PM–3 PM")
}
