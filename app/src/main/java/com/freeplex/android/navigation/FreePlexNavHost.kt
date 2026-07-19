package com.freeplex.android.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.freeplex.android.core.designsystem.FullPageLoading
import com.freeplex.android.core.designsystem.TvDimens
import com.freeplex.android.core.designsystem.isTvDevice
import com.freeplex.android.core.designsystem.tvNavItem
import com.freeplex.android.feature.auth.AuthStatus
import com.freeplex.android.feature.auth.AuthViewModel
import com.freeplex.android.feature.auth.LoginScreen
import com.freeplex.android.feature.details.DetailsScreen
import com.freeplex.android.feature.home.HomeScreen
import com.freeplex.android.feature.library.LibrariesDrawerViewModel
import com.freeplex.android.feature.library.LibraryScreen
import com.freeplex.android.feature.player.PlayerScreen
import com.freeplex.android.feature.search.SearchScreen
import com.freeplex.android.feature.settings.SettingsScreen

@Composable
fun FreePlexNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val auth by authViewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val librariesVm: LibrariesDrawerViewModel = hiltViewModel()
    val libraries by librariesVm.libraries.collectAsStateWithLifecycle()

    val start = if (auth.status == AuthStatus.Authenticated) Routes.Home else Routes.Login

    if (auth.status == AuthStatus.Restoring) {
        FullPageLoading()
        return
    }

    fun goMain(route: String, restore: Boolean = true) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = restore
        }
    }

    fun goLibrary(id: String) {
        // Do not restoreState — otherwise libraryId args/ViewModel stay stale.
        navController.navigate(Routes.library(id)) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = false
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.Login) {
            LoginScreen(
                onAuthenticated = {
                    librariesVm.refresh()
                    navController.navigate(Routes.Home) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.Home) {
            MainScaffold(
                currentRoute = Routes.Home,
                libraries = libraries.map { Triple(it.id, it.name, it.type) },
                onNavigate = { goMain(it) },
                onLibrary = ::goLibrary,
            ) {
                HomeScreen(onOpenItem = { navController.navigate(Routes.item(it)) })
            }
        }

        composable(
            Routes.Library,
            arguments = listOf(navArgument("libraryId") { type = NavType.StringType }),
        ) {
            val libId = it.arguments?.getString("libraryId")
            MainScaffold(
                currentRoute = Routes.Library,
                libraries = libraries.map { l -> Triple(l.id, l.name, l.type) },
                selectedLibraryId = libId,
                onNavigate = { goMain(it) },
                onLibrary = ::goLibrary,
            ) {
                LibraryScreen(
                    onOpenItem = { id -> navController.navigate(Routes.item(id)) },
                    onSelectLibrary = ::goLibrary,
                )
            }
        }

        composable(
            Routes.Item,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) {
            DetailsScreen(
                onPlay = { itemId, resumeMs, isEpisode ->
                    navController.navigate(Routes.player(itemId, resumeMs, isEpisode))
                },
            )
        }

        composable(Routes.Search) {
            MainScaffold(
                currentRoute = Routes.Search,
                libraries = libraries.map { Triple(it.id, it.name, it.type) },
                onNavigate = { goMain(it) },
                onLibrary = ::goLibrary,
            ) {
                SearchScreen(onOpenItem = { navController.navigate(Routes.item(it)) })
            }
        }

        composable(Routes.Settings) {
            MainScaffold(
                currentRoute = Routes.Settings,
                libraries = libraries.map { Triple(it.id, it.name, it.type) },
                onNavigate = { goMain(it) },
                onLibrary = ::goLibrary,
            ) {
                SettingsScreen(
                    onLoggedOut = {
                        navController.navigate(Routes.Login) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }

        composable(
            route = Routes.Player,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("resumeMs") { type = NavType.StringType; defaultValue = "0" },
                navArgument("isEpisode") { type = NavType.BoolType; defaultValue = false },
            ),
        ) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun MainScaffold(
    currentRoute: String,
    libraries: List<Triple<String, String, String>>,
    selectedLibraryId: String? = null,
    onNavigate: (String) -> Unit,
    onLibrary: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val isTv = isTvDevice()
    val firstLibrary = selectedLibraryId ?: libraries.firstOrNull()?.first

    if (isTv) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            TvSideBar(
                currentRoute = currentRoute,
                libraries = libraries,
                selectedLibraryId = selectedLibraryId,
                onHome = { onNavigate(Routes.Home) },
                onLibrary = onLibrary,
                onSearch = { onNavigate(Routes.Search) },
                onSettings = { onNavigate(Routes.Settings) },
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                content()
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.Home,
                        onClick = { onNavigate(Routes.Home) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.Library,
                        onClick = {
                            if (firstLibrary != null) onLibrary(firstLibrary)
                            else onNavigate(Routes.Settings)
                        },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Library") },
                        label = { Text("Library") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.Search,
                        onClick = { onNavigate(Routes.Search) },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.Settings,
                        onClick = { onNavigate(Routes.Settings) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                content()
            }
        }
    }
}

@Composable
private fun TvSideBar(
    currentRoute: String,
    libraries: List<Triple<String, String, String>>,
    selectedLibraryId: String?,
    onHome: () -> Unit,
    onLibrary: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(TvDimens.sidebarWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = "FreePlex",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        TvSectionLabel("Browse")
        TvNavRow("Home", Icons.Default.Home, currentRoute == Routes.Home, onHome)
        TvNavRow("Search", Icons.Default.Search, currentRoute == Routes.Search, onSearch)
        Spacer(Modifier.height(10.dp))
        TvSectionLabel("Libraries")
        if (libraries.isEmpty()) {
            Text(
                text = "No libraries",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        } else {
            libraries.forEach { (id, name, type) ->
                val icon = when {
                    type.contains("show", ignoreCase = true) ||
                        type.contains("series", ignoreCase = true) ||
                        type.contains("tv", ignoreCase = true) -> Icons.Default.Tv
                    type.contains("movie", ignoreCase = true) -> Icons.Default.Movie
                    else -> Icons.Default.VideoLibrary
                }
                TvNavRow(
                    label = name,
                    icon = icon,
                    selected = currentRoute == Routes.Library && selectedLibraryId == id,
                    onClick = { onLibrary(id) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        TvSectionLabel("Manage")
        TvNavRow("Settings", Icons.Default.Settings, currentRoute == Routes.Settings, onSettings)
    }
}

@Composable
private fun TvSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun TvNavRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvNavItem(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
