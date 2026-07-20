package com.hasan.nisabwallet.navigation

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

import com.hasan.nisabwallet.ui.screens.admin.grocery.MonthlyGroceryScreen
import com.hasan.nisabwallet.ui.screens.admin.ledger.MonthlyLedgerScreen
import com.hasan.nisabwallet.ui.screens.auth.LoginScreen
import com.hasan.nisabwallet.ui.screens.auth.RegisterScreen
import com.hasan.nisabwallet.ui.screens.common.ComingSoonScreen
import com.hasan.nisabwallet.ui.screens.dashboard.DashboardScreen
import com.hasan.nisabwallet.ui.screens.settings.SettingsScreen
import com.hasan.nisabwallet.ui.screens.transactions.TransactionsScreen
import com.hasan.nisabwallet.ui.screens.accounts.AccountsScreen
import com.hasan.nisabwallet.ui.screens.categories.CategoriesScreen
import com.hasan.nisabwallet.ui.screens.investments.InvestmentsScreen
import com.hasan.nisabwallet.ui.screens.investments.detail.InvestmentDetailScreen
import com.hasan.nisabwallet.ui.screens.loans.LoansScreen
import com.hasan.nisabwallet.ui.screens.loans.detail.LoanDetailScreen
import com.hasan.nisabwallet.ui.screens.lendings.LendingsScreen
import com.hasan.nisabwallet.ui.screens.lendings.detail.LendingDetailScreen
import com.hasan.nisabwallet.ui.screens.jewellery.JewelleryScreen

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "dashboard/transactions"
    const val MONTHLY_LEDGER = "dashboard/admin/monthly-ledger"
    const val MONTHLY_GROCERY = "dashboard/admin/monthly-grocery-2"
    const val SETTINGS = "dashboard/settings"

    const val ACCOUNTS = "dashboard/accounts"
    const val CATEGORIES = "dashboard/categories"
    const val TRANSFER = "dashboard/transfer"
    const val GOALS = "dashboard/goals"
    const val JEWELLERY = "dashboard/jewellery"
    const val ANALYTICS = "dashboard/analytics"
    const val ZAKAT = "dashboard/zakat"
    const val SUBSCRIPTION = "dashboard/subscription"

    const val INVESTMENTS = "dashboard/investments"
    const val INVESTMENT_DETAIL = "dashboard/investments/{investmentId}"
    fun createInvestmentDetailRoute(id: String) = "dashboard/investments/$id"

    const val LOANS = "dashboard/loans"
    const val LOAN_DETAIL = "dashboard/loans/{loanId}"
    fun createLoanDetailRoute(id: String) = "dashboard/loans/$id"

    const val LENDINGS = "dashboard/lendings"
    const val LENDING_DETAIL = "dashboard/lendings/{lendingId}"
    fun createLendingDetailRoute(id: String) = "dashboard/lendings/$id"

    val TOP_LEVEL_ROUTES = listOf(
        DASHBOARD, TRANSACTIONS, ACCOUNTS, CATEGORIES, INVESTMENTS,
        LOANS, LENDINGS, JEWELLERY, SETTINGS, MONTHLY_LEDGER, MONTHLY_GROCERY
    )
}

private data class NavTabItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

// ─── Left Navigation Drawer Items ───
private val drawerTabs = listOf(
    NavTabItem(Routes.DASHBOARD, "Dashboard", Icons.Default.Home),
    NavTabItem(Routes.ACCOUNTS, "Accounts", Icons.Default.AccountBalanceWallet),
    NavTabItem(Routes.TRANSACTIONS, "Transactions", Icons.Default.Receipt),
    NavTabItem(Routes.CATEGORIES, "Categories", Icons.Default.Category),
    NavTabItem(Routes.MONTHLY_LEDGER, "Monthly Ledger", Icons.Default.AccountBalance),
    NavTabItem(Routes.MONTHLY_GROCERY, "Monthly Grocery", Icons.Default.ShoppingCart),
    NavTabItem(Routes.INVESTMENTS, "Investments", Icons.AutoMirrored.Filled.TrendingUp),
    NavTabItem(Routes.JEWELLERY, "Jewellery", Icons.Default.Diamond),
    NavTabItem(Routes.LOANS, "Loans Borrowed", Icons.Default.AccountBalance),
    NavTabItem(Routes.LENDINGS, "Money Lent", Icons.Default.Money),
    NavTabItem(Routes.SETTINGS, "Settings", Icons.Default.Settings)
)

@Composable
fun NisabWalletRootNav(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = currentRoute in Routes.TOP_LEVEL_ROUTES

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isFabExpanded by remember { mutableStateOf(false) }

    // ─── Bind to Settings for Default FAB Option ───
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("nisab_prefs", Context.MODE_PRIVATE) }
    var defaultFabRoute by remember { mutableStateOf(sharedPrefs.getString("default_fab", Routes.TRANSACTIONS) ?: Routes.TRANSACTIONS) }

    DisposableEffect(sharedPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "default_fab") defaultFabRoute = prefs.getString("default_fab", Routes.TRANSACTIONS) ?: Routes.TRANSACTIONS
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // ─── Dynamic Contextual FAB Menu ───
    val dynamicQuickActions = remember(currentRoute, defaultFabRoute) {
        val actions = mutableListOf<NavTabItem>()

        val contextualAction = when (currentRoute) {
            Routes.JEWELLERY -> NavTabItem("action_add", "Add Jewellery", Icons.Default.Diamond)
            Routes.LOANS -> NavTabItem("action_add", "Add Loan Record", Icons.Default.AccountBalance)
            Routes.LENDINGS -> NavTabItem("action_add", "Add Lending Record", Icons.Default.Money)
            Routes.INVESTMENTS -> NavTabItem("action_add", "Add Investment", Icons.AutoMirrored.Filled.TrendingUp)
            Routes.ACCOUNTS -> NavTabItem("action_add", "Add Account", Icons.Default.AccountBalanceWallet)
            Routes.CATEGORIES -> NavTabItem("action_add", "Add Category", Icons.Default.Category)
            else -> null
        }

        if (contextualAction != null) {
            actions.add(contextualAction)
        } else {
            val defaultItem = when(defaultFabRoute) {
                Routes.JEWELLERY -> NavTabItem(Routes.JEWELLERY, "Add Jewellery", Icons.Default.Diamond)
                Routes.LOANS -> NavTabItem(Routes.LOANS, "Add Loan Record", Icons.Default.AccountBalance)
                Routes.INVESTMENTS -> NavTabItem(Routes.INVESTMENTS, "Add Investment", Icons.AutoMirrored.Filled.TrendingUp)
                else -> NavTabItem(Routes.TRANSACTIONS, "New Transaction", Icons.Default.AddCard)
            }
            actions.add(defaultItem)
        }

        if (actions.none { it.route == Routes.TRANSACTIONS }) {
            actions.add(NavTabItem(Routes.TRANSACTIONS, "New Transaction", Icons.Default.AddCard))
        }
        actions.add(NavTabItem(Routes.TRANSFER, "Transfer Funds", Icons.Default.SwapHoriz))

        actions
    }

    LaunchedEffect(currentRoute) { isFabExpanded = false }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isTopLevel,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF111827),
                drawerContentColor = Color.White,
                modifier = Modifier.width(300.dp)
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF34D399), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Capital Sync", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Financial Operating System", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    }
                    HorizontalDivider(color = Color(0xFF374151), modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(16.dp))

                    drawerTabs.forEach { tab ->
                        val isSelected = currentRoute == tab.route
                        NavigationDrawerItem(
                            label = {
                                Text(tab.label, color = if (isSelected) Color(0xFF34D399) else Color(0xFFD1D5DB), fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                            },
                            selected = isSelected,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(id = navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, null, tint = if (isSelected) Color(0xFF34D399) else Color(0xFF9CA3AF), modifier = Modifier.size(22.dp)) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFF1F2937),
                                unselectedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    ) {
        Scaffold { padding ->
            Box(modifier = Modifier.fillMaxSize()) {

                NisabWalletNavGraph(
                    navController = navController,
                    modifier = Modifier.padding(padding),
                )

                // ─── Top Left Hamburger Menu Toggle ───
                if (isTopLevel) {
                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .padding(top = 16.dp, start = 16.dp)
                    ) {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.9f), CircleShape)
                                .border(1.dp, Color(0xFFE5E7EB), CircleShape)
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Navigation", tint = Color(0xFF111827))
                        }
                    }
                }

                // ─── Floating Quick Actions FAB ───
                if (isTopLevel) {
                    if (isFabExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { isFabExpanded = false }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AnimatedVisibility(
                            visible = isFabExpanded,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                shadowElevation = 8.dp,
                                modifier = Modifier.width(220.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    dynamicQuickActions.forEach { action ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    isFabExpanded = false
                                                    if (action.route == "action_add") {
                                                        navController.currentBackStackEntry?.savedStateHandle?.set("triggerAdd", System.currentTimeMillis())
                                                    } else if (currentRoute != action.route) {
                                                        navController.navigate(action.route) { launchSingleTop = true }
                                                    } else {
                                                        navController.currentBackStackEntry?.savedStateHandle?.set("triggerAdd", System.currentTimeMillis())
                                                    }
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(action.icon, null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text(action.label, color = Color(0xFF111827), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        val rotationAnimation by animateFloatAsState(targetValue = if (isFabExpanded) 135f else 0f, label = "")
                        FloatingActionButton(
                            onClick = { isFabExpanded = !isFabExpanded },
                            shape = CircleShape,
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Quick Actions",
                                modifier = Modifier
                                    .size(28.dp)
                                    .rotate(rotationAnimation)
                            )
                        }
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

        composable(Routes.ACCOUNTS) { backStackEntry ->
            // Replaced the nullable getter with the guaranteed entry StateHandle
            val savedStateHandle = backStackEntry.savedStateHandle
            val trigger by savedStateHandle.getStateFlow("triggerAdd", 0L).collectAsState()

            // Once you update AccountsScreen to accept triggerFabAdd, pass it here!
            AccountsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.CATEGORIES) { backStackEntry ->
            val savedStateHandle = backStackEntry.savedStateHandle
            val trigger by savedStateHandle.getStateFlow("triggerAdd", 0L).collectAsState()

            CategoriesScreen(
                triggerFabAdd = trigger,
                onAddHandled = { savedStateHandle.set("triggerAdd", 0L) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.JEWELLERY) { backStackEntry ->
            val savedStateHandle = backStackEntry.savedStateHandle
            val trigger by savedStateHandle.getStateFlow("triggerAdd", 0L).collectAsState()

            JewelleryScreen(
                triggerFabAdd = trigger,
                onAddHandled = { savedStateHandle.set("triggerAdd", 0L) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.INVESTMENTS) { backStackEntry ->
            val savedStateHandle = backStackEntry.savedStateHandle
            val trigger by savedStateHandle.getStateFlow("triggerAdd", 0L).collectAsState()

            InvestmentsScreen(
                triggerFabAdd = trigger,
                onAddHandled = { savedStateHandle.set("triggerAdd", 0L) },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { id: String -> navController.navigate(Routes.createInvestmentDetailRoute(id)) }
            )
        }

        composable(
            route = Routes.INVESTMENT_DETAIL,
            arguments = listOf(navArgument("investmentId") { type = NavType.StringType })
        ) {
            InvestmentDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id: String -> /* BottomSheet handled internally */ }
            )
        }

        composable(Routes.LOANS) { backStackEntry ->
            val savedStateHandle = backStackEntry.savedStateHandle
            val trigger by savedStateHandle.getStateFlow("triggerAdd", 0L).collectAsState()

            LoansScreen(
                triggerFabAdd = trigger,
                onAddHandled = { savedStateHandle.set("triggerAdd", 0L) },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { id: String -> navController.navigate(Routes.createLoanDetailRoute(id)) }
            )
        }

        composable(
            route = Routes.LOAN_DETAIL,
            arguments = listOf(navArgument("loanId") { type = NavType.StringType })
        ) {
            LoanDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id: String -> navController.popBackStack(Routes.LOANS, false) }
            )
        }

        composable(Routes.LENDINGS) { backStackEntry ->
            val savedStateHandle = backStackEntry.savedStateHandle
            val trigger by savedStateHandle.getStateFlow("triggerAdd", 0L).collectAsState()

            LendingsScreen(
                triggerFabAdd = trigger,
                onAddHandled = { savedStateHandle.set("triggerAdd", 0L) },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { id: String -> navController.navigate(Routes.createLendingDetailRoute(id)) }
            )
        }

        composable(
            route = Routes.LENDING_DETAIL,
            arguments = listOf(navArgument("lendingId") { type = NavType.StringType })
        ) {
            LendingDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        val placeholders = listOf(
            Routes.TRANSFER to "Transfer",
            Routes.GOALS to "Goals",
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