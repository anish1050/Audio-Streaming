package com.anish1050.hires

import com.anish1050.hires.data.api.EntitlementDto
import com.anish1050.hires.data.repo.Entitlement
import com.anish1050.hires.data.repo.Tier
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class EntitlementTest {
    private val now = Instant.parse("2026-11-01T00:00:00Z")
    private fun days(n: Long) = Duration.ofDays(n)

    @Test fun premiumWithinBothWindowsPlaysOffline() {
        assertThat(Entitlement(Tier.PREMIUM, now.plus(days(10)), now.minus(days(1))).canPlayOffline(now)).isTrue()
    }

    @Test fun notCheckedInFor30DaysBlocks() {
        assertThat(Entitlement(Tier.PREMIUM, now.plus(days(300)), now.minus(days(31))).canPlayOffline(now)).isFalse()
    }

    @Test fun expiredWithinSevenDaysStillPlays() {
        assertThat(Entitlement(Tier.PREMIUM, now.minus(days(6)), now.minus(days(1))).canPlayOffline(now)).isTrue()
    }

    @Test fun expiredMoreThanSevenDaysBlocks() {
        assertThat(Entitlement(Tier.PREMIUM, now.minus(days(8)), now.minus(days(1))).canPlayOffline(now)).isFalse()
    }

    @Test fun freeNeverPlaysOffline() {
        assertThat(Entitlement.FREE.canPlayOffline(now)).isFalse()
    }

    @Test fun parsesServerDates() {
        val entitlement = Entitlement.from(EntitlementDto("premium", "2026-12-01T00:00:00.000Z", "2026-11-01T00:00:00.000Z"))
        assertThat(entitlement.isPremium).isTrue()
        assertThat(entitlement.expiresAt).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"))
    }
}
