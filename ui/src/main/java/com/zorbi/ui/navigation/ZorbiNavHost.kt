package com.zorbi.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zorbi.core.AppGraph
import com.zorbi.ui.screens.about.AboutScreen
import com.zorbi.ui.screens.calibrator.CalibrateScreen
import com.zorbi.ui.screens.community.CommunityScreen
import com.zorbi.ui.screens.editor.MappingEditorScreen
import com.zorbi.ui.screens.home.HomeScreen
import com.zorbi.ui.screens.library.GameLibraryScreen
import com.zorbi.ui.screens.play.PlayScreen
import com.zorbi.ui.screens.settings.SettingsScreen
import com.zorbi.ui.screens.settings.SettingsStorage
import com.zorbi.ui.screens.splash.SplashScreen
import com.zorbi.ui.screens.wizard.WizardScreen
import com.zorbi.ui.util.GameLauncher

/**
 * Canonical AppNavigation composable launching the application's root navigation graph.
 */
@Composable
fun AppNavigation(
    appGraph: AppGraph,
    modifier: Modifier = Modifier,
    startDestination: String = NavRoutes.SPLASH
) {
    ZorbiNavHost(
        appGraph = appGraph,
        modifier = modifier,
        startDestination = startDestination
    )
}

/**
 * Main Navigation Host for Zorbi.
 * Preserves all FROZEN navigation routes per Master Prompt Section 5.4:
 * home, library, wizard, calibrate/{packageName}, play/{packageName}, community, settings, about.
 */
@Composable
fun ZorbiNavHost(
    appGraph: AppGraph,
    modifier: Modifier = Modifier,
    startDestination: String = NavRoutes.SPLASH
) {
    val navController = rememberNavController()
    ZorbiNavHost(
        appGraph = appGraph,
        navController = navController,
        modifier = modifier,
        startDestination = startDestination
    )
}

@Composable
fun ZorbiNavHost(
    appGraph: AppGraph,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = NavRoutes.SPLASH
) {
    val context = LocalContext.current
    val settingsStorage = remember {
        try {
            SettingsStorage.fromContext(context)
        } catch (e: Exception) {
            null
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val handleLaunchGame: (String) -> Unit = { pkg ->
        if (GameLauncher.isGameInstalled(context, pkg)) {
            coroutineScope.launch {
                try {
                    appGraph.engine.start(pkg, com.zorbi.core.PerfMode.BALANCED)
                } catch (e: Exception) {
                    // Handled safely
                }
            }
        }
        GameLauncher.launchGame(context, pkg)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(durationMillis = 260)
            ) + fadeIn(animationSpec = tween(220))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(durationMillis = 260)
            ) + fadeOut(animationSpec = tween(180))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(durationMillis = 260)
            ) + fadeIn(animationSpec = tween(220))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(durationMillis = 260)
            ) + fadeOut(animationSpec = tween(180))
        }
    ) {
        // Route 0: Animated Splash Screen
        composable(
            route = NavRoutes.SPLASH,
            exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 350))
            }
        ) {
            SplashScreen(
                onTransitionToNext = {
                    val isWizardDone = settingsStorage?.load()?.wizardDone ?: false
                    val targetDestination = if (isWizardDone) NavRoutes.HOME else NavRoutes.WIZARD
                    navController.navigate(targetDestination) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // Route 1: Home
        composable(
            route = NavRoutes.HOME,
            enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 350))
            }
        ) {
            HomeScreen(
                appGraph = appGraph,
                onNavigateToLibrary = { navController.navigate(NavRoutes.LIBRARY) },
                onNavigateToWizard = { navController.navigate(NavRoutes.WIZARD) },
                onNavigateToCalibrate = { pkg -> navController.navigate(NavRoutes.calibrate(pkg)) },
                onNavigateToPlay = handleLaunchGame,
                onNavigateToCommunity = { navController.navigate(NavRoutes.COMMUNITY) },
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToAbout = { navController.navigate(NavRoutes.ABOUT) },
                onNavigateToEditor = { pkg -> navController.navigate(NavRoutes.editor(pkg)) }
            )
        }

        // Route 2: Library
        composable(NavRoutes.LIBRARY) {
            GameLibraryScreen(
                appGraph = appGraph,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCalibrate = { pkg -> navController.navigate(NavRoutes.calibrate(pkg)) },
                onNavigateToPlay = handleLaunchGame,
                onNavigateToCommunity = { navController.navigate(NavRoutes.COMMUNITY) },
                onNavigateToEditor = { pkg -> navController.navigate(NavRoutes.editor(pkg)) }
            )
        }

        // Route 3: Setup Wizard (P3-2 implemented)
        composable(NavRoutes.WIZARD) {
            val navigateToHomeOrPop = {
                if (!navController.popBackStack()) {
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.WIZARD) { inclusive = true }
                    }
                }
            }
            WizardScreen(
                appGraph = appGraph,
                onNavigateBack = navigateToHomeOrPop,
                onFinish = navigateToHomeOrPop
            )
        }

        // Route 4: Calibrate (P3-3 implemented)
        composable(
            route = NavRoutes.CALIBRATE,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            CalibrateScreen(
                packageName = packageName,
                appGraph = appGraph,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPlay = handleLaunchGame
            )
        }

        // Route 5: Play (P3-4 implemented)
        composable(
            route = NavRoutes.PLAY,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType }
            ),
            enterTransition = {
                scaleIn(initialScale = 0.92f, animationSpec = tween(280)) + fadeIn(animationSpec = tween(220))
            },
            exitTransition = {
                scaleOut(targetScale = 0.92f, animationSpec = tween(220)) + fadeOut(animationSpec = tween(180))
            }
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            PlayScreen(
                packageName = packageName,
                appGraph = appGraph,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Route 6: Community (P3-5 implemented)
        composable(NavRoutes.COMMUNITY) {
            CommunityScreen(
                appGraph = appGraph,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLibrary = { navController.navigate(NavRoutes.LIBRARY) }
            )
        }

        // Route 7: Settings (P3-5 implemented)
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWizard = { navController.navigate(NavRoutes.WIZARD) },
                onNavigateToAbout = { navController.navigate(NavRoutes.ABOUT) }
            )
        }

        // Route 8: About (P3-5 implemented)
        composable(NavRoutes.ABOUT) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToWizard = { navController.navigate(NavRoutes.WIZARD) }
            )
        }

        // Route 9: Custom Game Mapping Editor (P3-6 / Editor implemented)
        composable(
            route = NavRoutes.EDITOR,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            MappingEditorScreen(
                packageName = packageName,
                appGraph = appGraph,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLibrary = { navController.navigate(NavRoutes.LIBRARY) }
            )
        }
    }
}
