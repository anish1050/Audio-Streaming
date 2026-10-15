package com.anish1050.hires.data.repo

import com.anish1050.hires.data.api.AuthStore
import com.anish1050.hires.data.api.EntitlementDto
import com.anish1050.hires.data.api.HiResApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

enum class Tier { FREE, PREMIUM }

data class Entitlement(val tier: Tier, val expiresAt: Instant?, val verifiedAt: Instant?) {
    val isPremium: Boolean get() = tier == Tier.PREMIUM

    /** Downloads play offline until 7 days after expiry, and only while the app checked in within 30 days. */
    fun canPlayOffline(now: Instant): Boolean {
        if (tier != Tier.PREMIUM || expiresAt == null || verifiedAt == null) return false
        val limit = minOf(expiresAt.plus(OFFLINE_EXPIRY_GRACE), verifiedAt.plus(OFFLINE_CHECK_IN))
        return now.isBefore(limit)
    }

    companion object {
        val FREE = Entitlement(Tier.FREE, null, null)
        private val OFFLINE_EXPIRY_GRACE = Duration.ofDays(7)
        private val OFFLINE_CHECK_IN = Duration.ofDays(30)

        fun from(dto: EntitlementDto) = Entitlement(
            tier = if (dto.tier == "premium") Tier.PREMIUM else Tier.FREE,
            expiresAt = dto.expiresAt?.let(Instant::parse),
            verifiedAt = dto.verifiedAt?.let(Instant::parse),
        )
    }
}

/** The one owner of "is this user Premium": refreshed from the server, cached for offline use. */
class EntitlementRepository(private val api: HiResApi, private val auth: AuthStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _current = MutableStateFlow(cached())
    val current: StateFlow<Entitlement> = _current

    suspend fun refresh() {
        store(api.me().entitlement)
    }

    fun store(dto: EntitlementDto) {
        auth.entitlementJson = json.encodeToString(EntitlementDto.serializer(), dto)
        _current.value = Entitlement.from(dto)
    }

    fun clear() {
        _current.value = Entitlement.FREE
    }

    private fun cached(): Entitlement = auth.entitlementJson
        ?.let { runCatching { Entitlement.from(json.decodeFromString(EntitlementDto.serializer(), it)) }.getOrNull() }
        ?: Entitlement.FREE
}
