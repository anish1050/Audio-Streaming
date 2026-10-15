package com.anish1050.hires.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anish1050.hires.container
import com.anish1050.hires.ui.catalog.AlbumScreen
import com.anish1050.hires.ui.catalog.ArtistScreen
import com.anish1050.hires.ui.discover.DiscoverScreen
import com.anish1050.hires.ui.downloads.DownloadsScreen
import com.anish1050.hires.ui.home.HomeScreen
import com.anish1050.hires.ui.library.LibraryScreen
import com.anish1050.hires.ui.onboarding.OnboardingScreen
import com.anish1050.hires.ui.paywall.PaywallScreen
import com.anish1050.hires.ui.player.MiniPlayer
import com.anish1050.hires.ui.player.NowPlayingSheet
import com.anish1050.hires.ui.recognize.RecognizeScreen
import com.anish1050.hires.ui.search.SearchScreen
import com.anish1050.hires.ui.settings.SettingsScreen
import com.anish1050.hires.ui.stats.StatsScreen
import com.anish1050.hires.ui.theme.Ink

object Routes {
    const val HOME = "home"
    const val STATS = "stats"
    const val DISCOVER = "discover"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val PAYWALL = "paywall"
    const val RECOGNIZE = "recognize"

    fun album(id: String) = "album/$id"
    fun artist(id: String) = "artist/$id"
}

private val tabs = listOf(Routes.HOME to Icons.Default.MusicNote, Routes.STATS to Icons.Default.GraphicEq, Routes.DISCOVER to Icons.Default.DonutLarge)

@Composable
fun HiResApp(activity: ComponentActivity) {
    val container = LocalContext.current.container
    val isSignedIn by container.authStore.isSignedIn.collectAsStateWithLifecycle()
    val settings by container.prefs.settings.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (!isSignedIn || !settings.isOnboarded) {
            OnboardingScreen(activity)
            return@Box
        }
        // Off the launch path: refresh Premium status and send listens queued while offline.
        LaunchedEffect(Unit) {
            runCatching { container.entitlements.refresh() }
            container.playEvents.upload()
        }
        SignedInApp(activity)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInApp(activity: ComponentActivity) {
    val nav = rememberNavController()
    var isNowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) { AppNavHost(nav, activity) }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MiniPlayer(onOpen = { isNowPlayingOpen = true })
            if (currentRoute in tabs.map { it.first }) TabBar(currentRoute, onSelect = { route -> nav.navigate(route) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true } })
        }
    }

    if (isNowPlayingOpen) {
        ModalBottomSheet(onDismissRequest = { isNowPlayingOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Ink) {
            NowPlayingSheet(onUpgrade = { isNowPlayingOpen = false; nav.navigate(Routes.PAYWALL) })
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, activity: ComponentActivity) {
    val openAlbum = { id: String -> nav.navigate(Routes.album(id)) }
    val openArtist = { id: String -> nav.navigate(Routes.artist(id)) }
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(onSearch = { nav.navigate(Routes.SEARCH) }, onLibrary = { nav.navigate(Routes.LIBRARY) }, onSettings = { nav.navigate(Routes.SETTINGS) }) }
        composable(Routes.STATS) { StatsScreen(onSearch = { nav.navigate(Routes.SEARCH) }, onSettings = { nav.navigate(Routes.SETTINGS) }) }
        composable(Routes.DISCOVER) { DiscoverScreen(onRecognize = { nav.navigate(Routes.RECOGNIZE) }) }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onSearch = { nav.navigate(Routes.SEARCH) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onDiscover = { nav.navigate(Routes.DISCOVER) },
                onDownloads = { nav.navigate(Routes.DOWNLOADS) },
                onAlbum = openAlbum,
                onArtist = openArtist,
            )
        }
        composable(Routes.SEARCH) { SearchScreen(onBack = nav::popBackStack, onAlbum = openAlbum, onArtist = openArtist) }
        composable(Routes.ALBUM, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            AlbumScreen(entry.arguments?.getString("id").orEmpty(), onBack = nav::popBackStack, onArtist = openArtist, onUpgrade = { nav.navigate(Routes.PAYWALL) })
        }
        composable(Routes.ARTIST, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            ArtistScreen(entry.arguments?.getString("id").orEmpty(), onBack = nav::popBackStack, onAlbum = openAlbum)
        }
        composable(Routes.DOWNLOADS) { DownloadsScreen(onBack = nav::popBackStack) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = nav::popBackStack, onDownloads = { nav.navigate(Routes.DOWNLOADS) }, onUpgrade = { nav.navigate(Routes.PAYWALL) }) }
        composable(Routes.PAYWALL) { PaywallScreen(activity, onClose = nav::popBackStack) }
        composable(Routes.RECOGNIZE) { RecognizeScreen(onClose = nav::popBackStack) }
    }
}

@Composable
private fun TabBar(currentRoute: String?, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.padding(top = 8.dp)
            .background(com.anish1050.hires.ui.theme.Card, androidx.compose.foundation.shape.RoundedCornerShape(30.dp))
            .padding(5.dp),
    ) {
        tabs.forEach { (route, icon) ->
            val isSelected = route == currentRoute
            CircleButton(icon, route, onClick = { onSelect(route) }, background = if (isSelected) com.anish1050.hires.ui.theme.Graphite else com.anish1050.hires.ui.theme.Card)
        }
    }
}
