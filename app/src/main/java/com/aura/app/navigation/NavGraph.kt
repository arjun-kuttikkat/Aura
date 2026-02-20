package com.aura.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aura.app.data.AuraRepository
import com.aura.app.ui.components.MainBottomBar
import com.aura.app.ui.screen.CreateListingScreen
import com.aura.app.ui.screen.EscrowPayScreen
import com.aura.app.ui.screen.HomeScreen
import com.aura.app.ui.screen.ListingDetailScreen
import com.aura.app.ui.screen.MeetSessionScreen
import com.aura.app.ui.screen.OnboardingScreen
import com.aura.app.ui.screen.ProfileScreen
import com.aura.app.ui.screen.RewardsScreen
import com.aura.app.ui.screen.SettingsScreen
import com.aura.app.ui.screen.FaceVerificationScreen
import com.aura.app.ui.screen.TradeCompleteScreen
import com.aura.app.ui.screen.VerifyItemScreen
import com.aura.app.wallet.WalletConnectionState

private val MAIN_TAB_ROUTES = setOf(
    Routes.HOME,
    Routes.REWARDS,
    Routes.PROFILE,
    Routes.SETTINGS,
)

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = if (WalletConnectionState.walletAddress.value != null) Routes.HOME else Routes.ONBOARDING,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.let { route ->
        if (route.startsWith("listing_detail/")) Routes.HOME else route
    }
    val showBottomBar = currentRoute in MAIN_TAB_ROUTES

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = if (showBottomBar) 100.dp else 0.dp),
            ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onWalletConnected = { navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onListingClick = { id -> navController.navigate(Routes.listingDetail(id)) },
                )
            }
            composable(Routes.REWARDS) {
                RewardsScreen()
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onVerifyIdentity = { navController.navigate(Routes.FACE_VERIFICATION) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(Routes.CREATE_LISTING) {
                CreateListingScreen(
                    onListingCreated = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LISTING_DETAIL) { backStackEntry ->
            val listingId = backStackEntry.arguments?.getString("listingId") ?: return@composable
            val session by AuraRepository.currentTradeSession.collectAsState(initial = null)
            ListingDetailScreen(
                listingId = listingId,
                tradeSession = session,
                onStartMeetup = { navController.navigate(Routes.MEET_SESSION) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.MEET_SESSION) {
            MeetSessionScreen(
                onHandshakeComplete = { navController.navigate(Routes.VERIFY_ITEM) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.VERIFY_ITEM) {
            VerifyItemScreen(
                onVerified = { navController.navigate(Routes.ESCROW_PAY) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ESCROW_PAY) {
            EscrowPayScreen(
                onComplete = { navController.navigate(Routes.TRADE_COMPLETE) { popUpTo(Routes.HOME) { inclusive = false } } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TRADE_COMPLETE) {
            TradeCompleteScreen(
                onDone = { navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
            )
        }
        composable(Routes.FACE_VERIFICATION) {
            FaceVerificationScreen(
                onVerificationSuccess = {
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
        }
        }
        if (showBottomBar) {
            MainBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                currentRoute = currentRoute ?: Routes.HOME,
                onNavigate = { route ->
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
    }
}
