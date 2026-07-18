package com.hasan.nisabwallet.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.hasan.nisabwallet.ui.screens.admin.grocery.MonthlyGroceryScreen
import com.hasan.nisabwallet.ui.screens.admin.ledger.MonthlyLedgerScreen
import com.hasan.nisabwallet.ui.screens.auth.LoginScreen
import com.hasan.nisabwallet.ui.screens.auth.RegisterScreen
import com.hasan.nisabwallet.ui.screens.common.ComingSoonScreen
import com.hasan.nisabwallet.ui.screens.dashboard.DashboardScreen
import com.hasan.nisabwallet.ui.screens.settings.SettingsScreen
import com.hasan.nisabwallet.ui.screens.transactions.TransactionsScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "dashboard/transactions"
    const val MONTHLY_LEDGER = "dashboard/admin/monthly-ledger"
    const val MONTHLY_GROCERY = "dashboard/admin/monthly-grocery-2"
    const val SETTINGS = "dashboard/settings"

    const val ACCOUNTS = "dashboard/accounts"
    const val TRANSFER = "dashboard/transfer"
    const val LOANS = "dashboard/loans"
    const val LENDINGS = "dashboard/lendings"
    const val GOALS = "dashboard/goals"
    const val JEWELLERY = "dashboard/jewellery"
    const val INVESTMENTS = "dashboard/investments"
    const val ANALYTICS = "dashboard/analytics"
    const val ZAKAT = "dashboard/zakat"
    const val SUBSCRIPTION = "dashboard/subscription" // Added subscription route

    val BOTTOM_NAV_ROUTES = listOf(DASHBOARD, TRANSACTIONS, MONTHLY_LEDGER, MONTHLY_GROCERY, SETTINGS)
}

private data class NavTabItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val navigationTabs = listOf(
    NavTabItem(Routes.DASHBOARD, "Home", Icons.Default.Home),
    NavTabItem(Routes.TRANSACTIONS, "Transactions", Icons.Default.Receipt),
    NavTabItem(Routes.MONTHLY_LEDGER, "Ledger", Icons.Default.AccountBalance),
    NavTabItem(Routes.MONTHLY_GROCERY, "Grocery", Icons.Default.ShoppingCart),
    NavTabItem(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun NisabWalletRootNav(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showFloatingNav = currentRoute in Routes.BOTTOM_NAV_ROUTES

    var isMenuExpanded by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize()) {

            NisabWalletNavGraph(
                navController = navController,
                modifier = Modifier.padding(padding),
            )

            // Dim screen mask when the floating menu is active
            if (showFloatingNav && isMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isMenuExpanded = false }
                )
            }

            // Context Floating Navigation System Layout
            if (showFloatingNav) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    // Floating Expanded Navigation Menu
                    AnimatedVisibility(
                        visible = isMenuExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF111827), // Deep premium gray-black theme
                            tonalElevation = 8.dp,
                            modifier = Modifier.width(280.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                navigationTabs.forEach { tab ->
                                    val isSelected = currentRoute == tab.route
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) Color(0xFF1F2937) else Color.Transparent)
                                            .clickable {
                                                isMenuExpanded = false
                                                if (currentRoute != tab.route) {
                                                    navController.navigate(tab.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label,
                                            tint = if (isSelected) Color(0xFF34D399) else Color(0xFF9CA3AF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Text(
                                            text = tab.label,
                                            color = if (isSelected) Color.White else Color(0xFF9CA3AF),
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Centered Floating Trigger Button
                    val rotationAnimation by animateFloatAsState(targetValue = if (isMenuExpanded) 90f else 0f, label = "")

                    FloatingActionButton(
                        onClick = { isMenuExpanded = !isMenuExpanded },
                        shape = CircleShape,
                        containerColor = Color(0xFF111827),
                        contentColor = Color.White,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isMenuExpanded) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription = "Toggle Menu",
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotationAnimation)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NisabWalletNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = if (FirebaseAuth.getInstance().currentUser != null) {
        Routes.DASHBOARD
    } else {
        Routes.LOGIN
    },
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() },
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                onNavigateToAccounts     = { navController.navigate(Routes.ACCOUNTS) },
                onNavigateToTransfer     = { navController.navigate(Routes.TRANSFER) },
                onNavigateToLoans        = { navController.navigate(Routes.LOANS) },
                onNavigateToLendings     = { navController.navigate(Routes.LENDINGS) },
                onNavigateToGoals        = { navController.navigate(Routes.GOALS) },
                onNavigateToJewellery    = { navController.navigate(Routes.JEWELLERY) },
                onNavigateToInvestments  = { navController.navigate(Routes.INVESTMENTS) },
                onNavigateToAnalytics    = { navController.navigate(Routes.ANALYTICS) },
                onNavigateToZakat        = { navController.navigate(Routes.ZAKAT) },
            )
        }

        composable(Routes.TRANSACTIONS) {
            TransactionsScreen()
        }

        composable(Routes.MONTHLY_LEDGER) {
            MonthlyLedgerScreen(
                onNavigateToDashboard = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.DASHBOARD) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.MONTHLY_GROCERY) {
            MonthlyGroceryScreen(
                onNavigateToDashboard = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.DASHBOARD) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToSubscription = {
                    navController.navigate(Routes.SUBSCRIPTION)
                }
            )
        }

        val placeholders = listOf(
            Routes.ACCOUNTS to "Accounts",
            Routes.TRANSFER to "Transfer",
            Routes.LOANS to "Loans",
            Routes.LENDINGS to "Lendings",
            Routes.GOALS to "Goals",
            Routes.JEWELLERY to "Jewellery",
            Routes.INVESTMENTS to "Investments",
            Routes.ANALYTICS to "Analytics",
            Routes.ZAKAT to "Zakat",
            Routes.SUBSCRIPTION to "Subscription",
        )
        placeholders.forEach { (route, title) ->
            composable(route) {
                ComingSoonScreen(title = title) { navController.popBackStack() }
            }
        }
    }
}