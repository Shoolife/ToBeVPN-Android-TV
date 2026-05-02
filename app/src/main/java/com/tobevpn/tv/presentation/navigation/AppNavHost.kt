package com.tobevpn.tv.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tobevpn.tv.domain.model.AuthState
import com.tobevpn.tv.presentation.main.MainScreen
import com.tobevpn.tv.presentation.onboarding.OnboardingScreen
import com.tobevpn.tv.presentation.pairing.PairingScreen
import com.tobevpn.tv.presentation.servers.ServerListScreen
import com.tobevpn.tv.presentation.settings.SettingsScreen
import com.tobevpn.tv.presentation.speedtest.SpeedTestScreen
import com.tobevpn.tv.presentation.stats.StatsScreen
import com.tobevpn.tv.presentation.subscription.SubscriptionScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startFromOnboarding: Boolean = false,
    startAuthenticated: Boolean = false,
) {
    val sessionViewModel: AppSessionViewModel = hiltViewModel()
    val authState by sessionViewModel.authState.collectAsStateWithLifecycle()
    var wasAuthenticated by remember(startAuthenticated) { mutableStateOf(startAuthenticated) }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                if (!wasAuthenticated) {
                    navController.navigate(MainRoute) {
                        popUpTo(PairingRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                wasAuthenticated = true
            }
            AuthState.Unauthenticated -> {
                if (wasAuthenticated) {
                    navController.navigate(PairingRoute) {
                        popUpTo(MainRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            null -> Unit
        }
    }

    val startDestination: Any = when {
        startFromOnboarding -> OnboardingRoute
        startAuthenticated -> MainRoute
        else -> PairingRoute
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(PairingRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<PairingRoute> {
            PairingScreen(
                onAuthenticated = {
                    navController.navigate(MainRoute) {
                        popUpTo(PairingRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<MainRoute> {
            MainScreen(
                onNavigateToServers = { navController.navigate(ServerListRoute) },
                onNavigateToPairing = {
                    navController.navigate(PairingRoute) {
                        popUpTo(MainRoute) { inclusive = true }
                    }
                },
                onNavigateToSubscription = { navController.navigate(SubscriptionRoute) },
                onNavigateToSettings = { navController.navigate(SettingsRoute) },
                onNavigateToStats = { navController.navigate(StatsRoute) },
                onNavigateToSpeedTest = { navController.navigate(SpeedTestRoute) },
            )
        }
        composable<ServerListRoute> {
            ServerListScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<StatsRoute> {
            StatsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<SpeedTestRoute> {
            SpeedTestScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<SubscriptionRoute> {
            SubscriptionScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
