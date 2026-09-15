package org.opensources.courses.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.opensources.courses.feature.homeassistant.presentation.HomeAssistantRoute
import org.opensources.courses.feature.lists.presentation.ListsRoute
import org.opensources.courses.feature.onboarding.presentation.WelcomeRoute
import org.opensources.courses.feature.settings.presentation.SettingsRoute
import org.opensources.courses.feature.shopping.presentation.ShoppingRoute

@Composable
fun CoursesNavHost(
    showWelcome: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    // Chosen once: finishing the welcome screen must not rebuild the graph.
    val startDestination: Any = remember { if (showWelcome) WelcomeDestination else ShoppingDestination() }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // Painted in the app theme: the window behind follows the system theme, and pixel rounding
        // may leave a hairline between the two sliding screens.
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        enterTransition = { screenEnter() },
        exitTransition = { screenExit() },
        popEnterTransition = { screenPopEnter() },
        popExitTransition = { screenPopExit() },
    ) {
        composable<WelcomeDestination> {
            WelcomeRoute(
                onStart = { navController.openShoppingAfterWelcome() },
                onConnectHomeAssistant = {
                    navController.openShoppingAfterWelcome()
                    navController.navigate(HomeAssistantDestination)
                },
            )
        }
        composable<ShoppingDestination> {
            ShoppingRoute(
                onOpenLists = { navController.navigate(ListsDestination) },
                onOpenSettings = { navController.navigate(SettingsDestination) },
            )
        }
        composable<ListsDestination> {
            ListsRoute(
                onBack = { navController.popBackStack() },
                onOpenList = { listId ->
                    navController.navigate(ShoppingDestination(listId)) {
                        popUpTo<ShoppingDestination> { inclusive = true }
                    }
                },
            )
        }
        composable<SettingsDestination> {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenHomeAssistant = { navController.navigate(HomeAssistantDestination) },
            )
        }
        composable<HomeAssistantDestination> {
            HomeAssistantRoute(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.openShoppingAfterWelcome() {
    navigate(ShoppingDestination()) {
        popUpTo<WelcomeDestination> { inclusive = true }
    }
}
