package com.anish1050.hires.ui.paywall

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish1050.hires.billing.Offer
import com.anish1050.hires.container
import com.anish1050.hires.ui.CircleButton
import com.anish1050.hires.ui.ScreenPadding
import com.anish1050.hires.ui.SectionHeader
import com.anish1050.hires.ui.theme.Card
import com.anish1050.hires.ui.theme.CardBorder
import com.anish1050.hires.ui.theme.Ink
import com.anish1050.hires.ui.theme.Lavender
import com.anish1050.hires.ui.theme.Mint
import com.anish1050.hires.ui.theme.Sand
import com.anish1050.hires.ui.theme.TextMuted
import com.anish1050.hires.ui.theme.TextPrimary
import kotlinx.coroutines.launch

private val features = listOf("Up to 24-bit / 192 kHz FLAC", "Downloads for offline listening", "Song recognition", "Daily Mixes and AI Discovery")

@Composable
fun PaywallScreen(activity: ComponentActivity, onClose: () -> Unit) {
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val offers by container.billing.offers.collectAsStateWithLifecycle()
    val entitlement by container.entitlements.current.collectAsStateWithLifecycle()
    var selectedPlan by rememberSaveable { mutableStateOf("annual") }
    var isRestoring by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { container.billing.loadOffers() }
    LaunchedEffect(entitlement.isPremium) { if (entitlement.isPremium) onClose() }

    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
        SectionHeader("Go", "Hi-Res") { CircleButton(Icons.Default.Close, "Close", onClose) }
        features.forEach { feature ->
            Row(Modifier.padding(vertical = 5.dp)) {
                Text("✓", color = Mint, fontWeight = FontWeight.ExtraBold)
                Text(feature, color = Color(0xFFD6D6DA), fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        if (offers.isEmpty()) Text("Plans load from Google Play. Check that Play Store is signed in.", color = TextMuted, fontSize = 12.sp)
        offers.forEach { offer -> PlanCard(offer, offer.basePlanId == selectedPlan) { selectedPlan = offer.basePlanId } }
        Spacer(Modifier.weight(1f))
        val selected = offers.firstOrNull { it.basePlanId == selectedPlan }
        Button(
            onClick = { selected?.let { container.billing.buy(activity, it, container.authStore.userId.orEmpty()) } },
            enabled = selected != null,
            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = Ink),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (selected?.hasFreeTrial == true) "Try 1 week free" else "Subscribe", fontWeight = FontWeight.Bold) }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { isRestoring = true; scope.launch { container.billing.restore(); isRestoring = false } }, enabled = !isRestoring) {
                Text(if (isRestoring) "Restoring…" else "Restore purchases", color = Lavender, fontSize = 12.sp)
            }
        }
        Text("Cancel any time in Google Play. You won't be charged during the free week.", color = TextMuted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp))
    }
}

@Composable
private fun PlanCard(offer: Offer, isSelected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val isAnnual = offer.basePlanId == "annual"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Card).border(1.dp, if (isSelected) Sand else CardBorder, shape).clickable(onClick = onSelect).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isAnnual) "Annual" else "Monthly", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                if (isAnnual) {
                    Box(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp)).background(Sand).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("BEST VALUE", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            val trial = if (offer.hasFreeTrial) "7 days free, then " else ""
            Text("$trial${offer.price}/${if (isAnnual) "year" else "month"}", color = TextMuted, fontSize = 11.sp)
        }
        Text(offer.price, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}
