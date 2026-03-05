package com.aura.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aura.app.data.AuraRepository
import com.aura.app.ui.components.MainBottomBar
import com.aura.app.ui.screen.AppearanceSettingsScreen
import com.aura.app.ui.screen.CreateListingScreen
import com.aura.app.ui.screen.EscrowPayScreen
import com.aura.app.ui.screen.FaceVerificationScreen
import com.aura.app.ui.screen.HomeScreen
import com.aura.app.ui.screen.ListingDetailScreen
import com.aura.app.ui.screen.MeetSessionScreen
import com.aura.app.ui.screen.NotificationsSettingsScreen
import com.aura.app.ui.screen.OnboardingScreen
import com.aura.app.ui.screen.PrivacySettingsScreen
import com.aura.app.ui.screen.ProfileScreen
import com.aura.app.ui.screen.RewardsScreen
import com.aura.app.ui.screen.SecuritySettingsScreen
import com.aura.app.ui.screen.SettingsScreen
import com.aura.app.ui.screen.TradeCompleteScreen
import com.aura.app.ui.screen.VerifyItemScreen
import com.aura.app.wallet.WalletConnectionState
import com.aura.app.ui.screen.EmiratePickerScreen

private val MAIN_TAB_ROUTES = setOf(
    Routes.HOME,
    Routes.FAVORITES,
    Routes.CHATS,
    Routes.DIRECTIVES,
    Routes.PROFILE,
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
                    .padding(padding),
            ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onWalletConnected = {
                        navController.navigate(Routes.EMIRATE_PICKER) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
            composable(Routes.EMIRATE_PICKER) {
                EmiratePickerScreen(
                    onEmirateSelected = { emirate ->
                        navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                    }
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onListingClick = { id -> navController.navigate(Routes.listingDetail(id)) },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(Routes.FAVORITES) {
                com.aura.app.ui.screen.FavoritesScreen(
                    onNavigateToHome = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
                )
            }
            composable(Routes.CHATS) {
                com.aura.app.ui.screen.ChatsScreen(
                    onNavigateToChat = { listingId -> navController.navigate(Routes.chatDetail(listingId)) },
                    onNavigateToHome = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
                )
            }
            composable(Routes.REWARDS) {
                RewardsScreen()
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onVerifyIdentity = { navController.navigate(Routes.FACE_VERIFICATION) },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNotificationsClick = { navController.navigate(Routes.SETTINGS_NOTIFICATIONS) },
                    onAppearanceClick = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                    onSecurityClick = { navController.navigate(Routes.SETTINGS_SECURITY) },
                    onPrivacyClick = { navController.navigate(Routes.SETTINGS_PRIVACY) },
                    onLogout = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.SETTINGS_NOTIFICATIONS) {
                NotificationsSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS_APPEARANCE) {
                AppearanceSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS_SECURITY) {
                SecuritySettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS_PRIVACY) {
                PrivacySettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CREATE_LISTING) {
                CreateListingScreen(
                    onListingCreated = {
                        navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PLACE_AD_TYPE) { backStackEntry ->
                val category = backStackEntry.arguments?.getString("category") ?: ""
                com.aura.app.ui.screen.PlaceAdTypeScreen(
                    category = category,
                    onSellWithAI = { navController.navigate(Routes.PLACE_AD_UPLOAD) },
                    onClassicPost = { navController.navigate(Routes.PLACE_AD_UPLOAD) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PLACE_AD_UPLOAD) {
                com.aura.app.ui.screen.PlaceAdAiUploadScreen(
                    onNext = { navController.navigate(Routes.PLACE_AD_LOCATION) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PLACE_AD_LOCATION) {
                com.aura.app.ui.screen.PlaceAdLocationScreen(
                    onLocationConfirmed = { 
                        navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } 
                    },
                    onBack = { navController.popBackStack() }
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
                onChatClicked = { navController.navigate(Routes.chatDetail(listingId)) }
            )
        }
        composable(Routes.CHAT_DETAIL) { backStackEntry ->
            val listingId = backStackEntry.arguments?.getString("listingId") ?: return@composable
            com.aura.app.ui.screen.ChatDetailScreen(
                listingId = listingId,
                onBack = { navController.popBackStack() }
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
        composable(Routes.AURA_CHECK) {
            com.aura.app.ui.screen.AuraCheckScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.P2P_EXCHANGE) {
            com.aura.app.ui.screen.P2PExchangeScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ZONE_REFINEMENT) {
            com.aura.app.ui.screen.ZoneRefinementScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.DIRECTIVES) {
            com.aura.app.ui.screen.DirectivesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.AVATAR_CREATOR) {
            com.aura.app.ui.screen.AvatarCreatorScreen(
                onDone = {
                    navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable(Routes.AVATAR_STORE) {
            com.aura.app.ui.screen.AvatarStoreScreen(
                onBack = { navController.popBackStack() }
            )
        }
        }
        }
        if (showBottomBar) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                // Fade behind navbar — content extends to bottom, fades under bar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        )
                        .blur(32.dp),
                )
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
}
