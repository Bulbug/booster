package com.gameboostx.app.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gameboostx.app.GameBoostApplication
import com.gameboostx.app.ui.advanced.AdvancedScreen
import com.gameboostx.app.ui.advanced.AdvancedViewModel
import com.gameboostx.app.ui.boost.BoostScreen
import com.gameboostx.app.ui.boost.BoostViewModel
import com.gameboostx.app.ui.games.GamesScreen
import com.gameboostx.app.ui.games.GamesViewModel
import com.gameboostx.app.ui.home.HomeScreen
import com.gameboostx.app.ui.home.HomeViewModel
import com.gameboostx.app.ui.session.SessionScreen
import com.gameboostx.app.ui.session.SessionViewModel
import com.gameboostx.app.ui.settings.SettingsScreen
import com.gameboostx.app.ui.settings.SettingsViewModel
import com.gameboostx.app.viewmodel.GameBoostViewModelFactory

private sealed class Destination(val route: String, val label: String) {
    data object Home : Destination("home", "Home")
    data object Games : Destination("games", "Games")
    data object Advanced : Destination("advanced", "Advanced")
    data object Settings : Destination("settings", "Settings")
}

private const val BOOST_ROUTE = "boost/{packageName}"

@Composable
fun GameBoostNavHost(app: GameBoostApplication) {
    val navController = rememberNavController()
    val factory = GameBoostViewModelFactory(app)
    val bottomDestinations = listOf(Destination.Home, Destination.Games, Destination.Advanced, Destination.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                bottomDestinations.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                when (dest) {
                                    Destination.Home -> Icons.Filled.Home
                                    Destination.Games -> Icons.Filled.SportsEsports
                                    Destination.Advanced -> Icons.Filled.Build
                                    Destination.Settings -> Icons.Filled.Settings
                                },
                                contentDescription = dest.label,
                            )
                        },
                        label = { Text(dest.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Home.route) {
                val vm: HomeViewModel = viewModel(factory = factory)
                HomeScreen(vm)
            }
            composable(Destination.Games.route) {
                val vm: GamesViewModel = viewModel(factory = factory)
                GamesScreen(
                    viewModel = vm,
                    onBoostClick = { packageName -> navController.navigate("boost/$packageName") },
                    onLaunchClick = { packageName -> launchGame(app, packageName) },
                    onSessionClick = { game ->
                        app.pendingSessionGame = game
                        navController.navigate("session")
                    },
                )
            }
            composable(Destination.Advanced.route) {
                val vm: AdvancedViewModel = viewModel(factory = factory)
                AdvancedScreen(vm)
            }
            composable(Destination.Settings.route) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(vm)
            }
            composable(
                route = BOOST_ROUTE,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                val vm: BoostViewModel = viewModel(
                    factory = GameBoostViewModelFactory(app, packageName),
                    key = "boost_$packageName",
                )
                BoostScreen(
                    viewModel = vm,
                    onLaunchClick = { launchGame(app, packageName) },
                )
            }
            composable("session") {
                val pending = app.pendingSessionGame
                if (pending == null) {
                    navController.popBackStack()
                } else {
                    val vm: SessionViewModel = viewModel(factory = factory, key = "session_${pending.packageName}")
                    SessionScreen(
                        viewModel = vm,
                        packageName = pending.packageName,
                        displayName = pending.displayName,
                        profile = pending.profile,
                        onEnded = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun launchGame(app: GameBoostApplication, packageName: String) {
    app.gameLibraryManager.launchIntentFor(packageName)?.let { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }
}
