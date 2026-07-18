package com.hasan.nisabwallet.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
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
import com.hasan.nisabwallet.ui.screens.transactions.TransactionsScreen

// Routes mirror the web app's URL paths one-to-one so it's obvious which
// page.js each destination corresponds to.
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "dashboard/transactions"
    const val MONTHLY_LEDGER = "dashboard/admin/monthly-ledger"
    const val MONTHLY_GROCERY = "dashboard/admin/monthly-grocery-2"

    // Not converted yet — DashboardScreen still links to these, so they route
    // to ComingSoonScreen until each one gets its own real screen.
    const val ACCOUNTS = "dashboard/accounts"
    const val TRANSFER = "dashboard/transfer"
    const val LOANS = "dashboard/loans"
    const val LENDINGS = "dashboard/lendings"
    const val GOALS = "dashboard/goals"
    const val JEWELLERY = "dashboard/jewellery"
    const val INVESTMENTS = "dashboard/investments"
    const val ANALYTICS = "dashboard/analytics"
    const val ZAKAT = "dashboard/zakat"

    /** Routes that get a bottom tab. Only the pages converted so far — the other
     *  Dashboard quick-links still fall through to ComingSoonScreen with no tab. */
    val BOTTOM_NAV_ROUTES = listOf(DASHBOARD, TRANSACTIONS, MONTHLY_LEDGER, MONTHLY_GROCERY)
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomTabs = listOf(
    BottomTab(Routes.DASHBOARD, "Home", Icons.Default.Home),
    BottomTab(Routes.TRANSACTIONS, "Transactions", Icons.Default.Receipt),
    BottomTab(Routes.MONTHLY_LEDGER, "Ledger", Icons.Default.AccountBalance),
    BottomTab(Routes.MONTHLY_GROCERY, "Grocery", Icons.Default.ShoppingCart),
)

/**
 * Top-level composable: decides Login/Register vs. the main app, and — only on
 * the four converted pages — shows a bottom nav bar so Monthly Ledger and Monthly
 * Grocery are actually reachable.
 */
@Composable
fun NisabWalletRootNav(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.BOTTOM_NAV_ROUTES

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NisabWalletNavGraph(
            navController = navController,
            modifier = androidx.compose.ui.Modifier.padding(padding),
        )
    }
}

@Composable
fun NisabWalletNavGraph(
    navController: NavHostController = rememberNavController(),
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    startDestination: String = if (FirebaseAuth.getInstance().currentUser != null) {
        Routes.DASHBOARD
    } else {
        Routes.LOGIN
    },
) {
    // We explicitly disable entering and exiting animations to make tab switching instant
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
        )
        placeholders.forEach { (route, title) ->
            composable(route) {
                ComingSoonScreen(title = title) { navController.popBackStack() }
            }
        }
    }
}