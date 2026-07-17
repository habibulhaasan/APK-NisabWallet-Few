package com.hasan.nisabwallet.ui.screens.dashboard

// Converted from: src/app/dashboard/page.js
// Every UI section below maps to the same JSX block in the source file.
// Comments mark which section of page.js each composable came from.

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import com.hasan.nisabwallet.core.util.HijriConverter
import java.text.SimpleDateFormat
import java.util.*

// ─── Screen Entry Point ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DashboardScreen(
    onNavigateToTransactions: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToTransfer: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onNavigateToLendings: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToJewellery: () -> Unit,
    onNavigateToInvestments: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToZakat: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Pull-to-refresh
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isLoading,
        onRefresh  = { viewModel.refresh() },
    )

    Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Welcome Header (mirrors "Welcome back" section in page.js) ──
            item {
                WelcomeHeader(userName = "User") // TODO: pass user.displayName from AuthViewModel
            }

            // ── Stats Grid (mirrors 4-column stats grid in page.js) ──
            item {
                StatsGrid(
                    state                = state,
                    onNavigateToTransactions = onNavigateToTransactions,
                    fmt                  = { CurrencyFormatter.formatBDT(it) },
                )
            }

            // ── Asset Overview (mirrors "Asset Overview" conditional block in page.js) ──
            // Only shown when user has investments, lendings, goals, or jewellery
            val hasAssets = state.investments.any { it.status == "active" }
                || state.lendings.any { it.status == "active" }
                || state.goals.any { it.status == "active" }
                || state.jewellery.isNotEmpty()

            if (hasAssets) {
                item {
                    AssetOverviewSection(
                        state                = state,
                        onNavigateToLendings = onNavigateToLendings,
                        onNavigateToGoals    = onNavigateToGoals,
                        onNavigateToJewellery = onNavigateToJewellery,
                        onNavigateToInvestments = onNavigateToInvestments,
                        fmt                  = { CurrencyFormatter.formatBDT(it) },
                    )
                }
            }

            // ── Loan Summary Cards (mirrors "activeLoans.length > 0" block in page.js) ──
            val activeLoans = state.loans.filter { it.status == "active" }
            if (activeLoans.isNotEmpty()) {
                item {
                    LoanSummarySection(
                        loans              = activeLoans,
                        onNavigateToLoans  = onNavigateToLoans,
                        fmt                = { CurrencyFormatter.formatBDT(it) },
                    )
                }
            }

            // ── Zakat Status Card (mirrors the gradient Zakat card in page.js) ──
            item {
                ZakatStatusCard(
                    state              = state,
                    onNavigateToZakat  = onNavigateToZakat,
                    onNavigateToJewellery = onNavigateToJewellery,
                    fmt                = { CurrencyFormatter.formatBDT(it) },
                )
            }

            // ── Quick Actions (mirrors "Quick Actions" section in page.js) ──
            item {
                QuickActionsSection(
                    onNavigateToAccounts     = onNavigateToAccounts,
                    onNavigateToTransactions = onNavigateToTransactions,
                    onNavigateToTransfer     = onNavigateToTransfer,
                    onNavigateToJewellery    = onNavigateToJewellery,
                    onNavigateToAnalytics    = onNavigateToAnalytics,
                )
            }

            // ── Recent Transactions (mirrors "Recent Transactions" section in page.js) ──
            item {
                RecentTransactionsSection(
                    transactions             = state.recentTransactions,
                    onViewAll                = onNavigateToTransactions,
                    getAccountName           = { viewModel.getAccountName(it) },
                    fmt                      = { CurrencyFormatter.formatBDT(it) },
                )
            }
        }

        PullRefreshIndicator(
            refreshing = state.isLoading,
            state      = pullRefreshState,
            modifier   = Modifier.align(Alignment.TopCenter),
            contentColor = MaterialTheme.colorScheme.primary,
        )
    }
}

// ─── Welcome Header ───────────────────────────────────────────────────────────
// Mirrors: <h1>Welcome back, {user?.displayName || 'User'}!</h1> in page.js

@Composable
private fun WelcomeHeader(userName: String) {
    Column {
        Text(
            text       = "Welcome back, $userName!",
            fontSize   = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text     = "Overview of your finances",
            fontSize = 14.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Stats Grid ───────────────────────────────────────────────────────────────
// Mirrors: 4-column stats grid (Total Balance, Accounts, Income, Expenses) in page.js

@Composable
private fun StatsGrid(
    state: DashboardUiState,
    onNavigateToTransactions: () -> Unit,
    fmt: (Double) -> String,
) {
    val totalBalance  = state.accounts.sumOf { it.balance }
    val accountsCount = state.accounts.size

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Total Balance card
            StatCard(
                modifier  = Modifier.weight(1f),
                label     = "Total Balance",
                value     = fmt(totalBalance),
                subLabel  = "All accounts",
                valueColor = MaterialTheme.colorScheme.onSurface,
            )
            // Accounts count card
            StatCard(
                modifier  = Modifier.weight(1f),
                label     = "Accounts",
                value     = accountsCount.toString(),
                subLabel  = "Active accounts",
                valueColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Income card — tappable → transactions
            StatCard(
                modifier      = Modifier.weight(1f).clickable { onNavigateToTransactions() },
                label         = "Income",
                value         = fmt(state.thisMonthIncome),
                subLabel      = "This month",
                valueColor    = Color(0xFF2E7D32),
            )
            // Expenses card — tappable → transactions
            StatCard(
                modifier      = Modifier.weight(1f).clickable { onNavigateToTransactions() },
                label         = "Expenses",
                value         = fmt(state.thisMonthExpense),
                subLabel      = "This month",
                valueColor    = Color(0xFFB71C1C),
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subLabel: String,
    valueColor: Color,
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label.uppercase(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(8.dp))
            Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
            Spacer(Modifier.height(4.dp))
            Text(text = subLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Asset Overview Section ───────────────────────────────────────────────────
// Mirrors: "Asset Overview" conditional grid in page.js

@Composable
private fun AssetOverviewSection(
    state: DashboardUiState,
    onNavigateToLendings: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToJewellery: () -> Unit,
    onNavigateToInvestments: () -> Unit,
    fmt: (Double) -> String,
) {
    val activeInvestments  = state.investments.filter { it.status == "active" }
    val activeLendings     = state.lendings.filter { it.status == "active" }
    val activeGoals        = state.goals.filter { it.status == "active" }
    val unpricedJewellery  = state.jewellery.filter { it.currentZakatValue <= 0 }

    val totalInvestmentValue = activeInvestments.sumOf { inv ->
        val qty   = if (inv.quantity > 0) inv.quantity else 1.0
        val price = if (inv.currentValue > 0) inv.currentValue else inv.purchasePrice
        price * qty
    }
    val totalLendingValue = activeLendings.sumOf { l ->
        if (l.remainingBalance > 0) l.remainingBalance else l.principalAmount
    }
    val totalGoalSaved    = activeGoals.sumOf { it.currentAmount }
    val totalJewelleryVal = state.jewellery.filter { it.currentZakatValue > 0 }.sumOf { it.currentZakatValue }
    val overdueLendings   = activeLendings.filter { l ->
        val dueStr = l.nextPaymentDue ?: l.dueDate ?: return@filter false
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dueStr)!! < Date() }.getOrDefault(false)
    }
    val nearingGoals      = activeGoals.filter { g ->
        val pct = if (g.targetAmount > 0) (g.currentAmount / g.targetAmount) * 100 else 0.0
        pct in 80.0..99.9
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Asset Overview", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)

        Row(
            modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Investments card
            if (activeInvestments.isNotEmpty()) {
                AssetCard(
                    label      = "Investments",
                    value      = fmt(totalInvestmentValue),
                    subLabel   = "${activeInvestments.size} active position${if (activeInvestments.size > 1) "s" else ""}",
                    accentColor = Color(0xFF6200EA),
                    onClick    = onNavigateToInvestments,
                )
            }
            // Lendings card — orange border when overdue
            if (activeLendings.isNotEmpty()) {
                AssetCard(
                    label       = "Money Lent",
                    value       = fmt(totalLendingValue),
                    subLabel    = if (overdueLendings.isNotEmpty())
                        "${overdueLendings.size} payment${if (overdueLendings.size > 1) "s" else ""} overdue"
                        else "${activeLendings.size} borrower${if (activeLendings.size > 1) "s" else ""}",
                    accentColor = if (overdueLendings.isNotEmpty()) Color(0xFFE65100) else Color(0xFF00838F),
                    onClick     = onNavigateToLendings,
                )
            }
            // Goals card
            if (activeGoals.isNotEmpty()) {
                AssetCard(
                    label       = "Savings Goals",
                    value       = fmt(totalGoalSaved),
                    subLabel    = if (nearingGoals.isNotEmpty())
                        "${nearingGoals.size} goal${if (nearingGoals.size > 1) "s" else ""} almost reached!"
                        else "${activeGoals.size} active goal${if (activeGoals.size > 1) "s" else ""}",
                    accentColor = Color(0xFF2E7D32),
                    onClick     = onNavigateToGoals,
                )
            }
            // Jewellery card
            if (state.jewellery.isNotEmpty()) {
                AssetCard(
                    label       = "Jewellery",
                    value       = fmt(totalJewelleryVal),
                    subLabel    = if (unpricedJewellery.isNotEmpty())
                        "${unpricedJewellery.size} item${if (unpricedJewellery.size > 1) "s" else ""} need pricing"
                        else "${state.jewellery.size} item${if (state.jewellery.size > 1) "s" else ""} · all priced ✓",
                    accentColor = Color(0xFFE65100),
                    onClick     = onNavigateToJewellery,
                )
            }
        }
    }
}

@Composable
private fun AssetCard(
    label: String,
    value: String,
    subLabel: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier  = Modifier.width(160.dp).clickable { onClick() },
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = label.uppercase(), fontSize = 10.sp, color = accentColor, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(text = subLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Loan Summary Section ─────────────────────────────────────────────────────
// Mirrors: "activeLoans.length > 0" 3-card loan section in page.js

@Composable
private fun LoanSummarySection(
    loans: List<LoanSummary>,
    onNavigateToLoans: () -> Unit,
    fmt: (Double) -> String,
) {
    val totalDebt           = loans.sumOf { it.remainingBalance }
    val totalMonthlyPayment = loans.sumOf { it.monthlyPayment }
    val today               = Date()
    val sdf                 = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Find next upcoming payment — mirrors upcomingPayments sort in page.js
    data class LoanWithDays(val loan: LoanSummary, val daysUntilDue: Int)
    val upcomingPayments = loans
        .filter { it.nextPaymentDue != null }
        .map { l ->
            val due  = runCatching { sdf.parse(l.nextPaymentDue!!)!! }.getOrDefault(today)
            val days = ((due.time - today.time) / 86_400_000).toInt()
            LoanWithDays(l, days)
        }
        .sortedBy { it.daysUntilDue }
    val nextPaymentDue = upcomingPayments.firstOrNull()

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Active loans count card
        LoanCard(
            modifier    = Modifier.weight(1f).clickable { onNavigateToLoans() },
            label       = "Active Loans",
            value       = loans.size.toString(),
            subLabel    = "Click to manage",
            valueColor  = Color(0xFFB45309),
            cardColor   = Color(0xFFFFFBEB),
        )
        // Total debt card
        LoanCard(
            modifier    = Modifier.weight(1f).clickable { onNavigateToLoans() },
            label       = "Total Debt",
            value       = fmt(totalDebt),
            subLabel    = "Remaining balance",
            valueColor  = Color(0xFFB91C1C),
            cardColor   = Color(0xFFFFF1F2),
        )
        // Next payment / monthly payment card
        LoanCard(
            modifier    = Modifier.weight(1f).clickable { onNavigateToLoans() },
            label       = if (nextPaymentDue != null) "Next Payment Due" else "Monthly Payment",
            value       = fmt(nextPaymentDue?.loan?.monthlyPayment ?: totalMonthlyPayment),
            subLabel    = when {
                nextPaymentDue == null                  -> "Total per month"
                nextPaymentDue.daysUntilDue == 0        -> "Due today!"
                nextPaymentDue.daysUntilDue == 1        -> "Due tomorrow"
                nextPaymentDue.daysUntilDue < 0         -> "Overdue!"
                else                                    -> "Due in ${nextPaymentDue.daysUntilDue} days"
            },
            valueColor  = Color(0xFF1D4ED8),
            cardColor   = Color(0xFFEFF6FF),
        )
    }
}

@Composable
private fun LoanCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subLabel: String,
    valueColor: Color,
    cardColor: Color,
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label.uppercase(), fontSize = 10.sp, color = valueColor, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
            Spacer(Modifier.height(4.dp))
            Text(text = subLabel, fontSize = 11.sp, color = valueColor.copy(alpha = 0.7f))
        }
    }
}

// ─── Zakat Status Card ────────────────────────────────────────────────────────
// Mirrors: the large gradient Zakat card in page.js — the most complex UI section

@Composable
private fun ZakatStatusCard(
    state: DashboardUiState,
    onNavigateToZakat: () -> Unit,
    onNavigateToJewellery: () -> Unit,
    fmt: (Double) -> String,
) {
    val statusColors = mapOf(
        ZakatStatus.NOT_MANDATORY  to Color(0xFFD32F2F),
        ZakatStatus.MONITORING     to Color(0xFF1565C0),
        ZakatStatus.DUE            to Color(0xFFB71C1C),
        ZakatStatus.PAID           to Color(0xFF37474F),
        ZakatStatus.EXEMPT         to Color(0xFF4527A0),
        ZakatStatus.READY_TO_START to Color(0xFF1565C0),
    )
    val chipColor = statusColors[state.zakatStatus] ?: Color(0xFF37474F)

    val unpricedJewellery = state.jewellery.filter { it.currentZakatValue <= 0 }
    val totalJewelleryZakat = state.jewellery.filter { it.currentZakatValue > 0 }.sumOf { it.currentZakatValue }

    Card(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // ── Header row (icon + title + status chip) ──────────────────
            Row(
                modifier       = Modifier.fillMaxWidth(),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier          = Modifier.size(44.dp).background(Color.White, RoundedCornerShape(10.dp)),
                        contentAlignment  = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF1B5E20), modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text("Zakat Status", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Real-time monitoring", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Status chip
                Surface(shape = RoundedCornerShape(20.dp), color = chipColor) {
                    Text(
                        text     = state.zakatStatus,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color    = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Net Zakatable Wealth + Nisab 2-column cards ──────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ZakatInfoCard(
                    modifier = Modifier.weight(1f),
                    label    = "Net Zakatable Wealth",
                    value    = fmt(state.netZakatableWealth),
                    accent   = Color(0xFF1B5E20),
                )
                ZakatInfoCard(
                    modifier = Modifier.weight(1f),
                    label    = "Nisab Threshold",
                    value    = fmt(state.nisabThreshold),
                    accent   = Color(0xFF1B5E20),
                    subLabel = "52.5 Tola Silver",
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Wealth source pills (mirrors wealth breakdown pills in page.js) ──
            val breakdown = state.wealthBreakdown
            if (breakdown.jewelleryTotal > 0 || breakdown.lendingsTotal > 0 || breakdown.investmentsTotal > 0 || breakdown.loansTotal > 0) {
                Row(
                    modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (breakdown.accountsTotal > 0)
                        WealthPill("Accounts ${fmt(breakdown.accountsTotal)}", Color(0xFF1B5E20))
                    if (breakdown.jewelleryTotal > 0)
                        WealthPill("Jewellery ${fmt(breakdown.jewelleryTotal)}", Color(0xFFB45309))
                    if (breakdown.lendingsTotal > 0)
                        WealthPill("Lendings ${fmt(breakdown.lendingsTotal)}", Color(0xFF006064))
                    if (breakdown.investmentsTotal > 0)
                        WealthPill("Investments ${fmt(breakdown.investmentsTotal)}", Color(0xFF4527A0))
                    if (breakdown.loansTotal > 0)
                        WealthPill("Loans −${fmt(breakdown.loansTotal)}", Color(0xFFB71C1C))
                }
                Spacer(Modifier.height(10.dp))
            }

            // ── Unpriced jewellery nudge (mirrors amber alert in page.js) ──
            if (unpricedJewellery.isNotEmpty()) {
                Surface(
                    modifier  = Modifier.fillMaxWidth().clickable { onNavigateToJewellery() },
                    shape     = RoundedCornerShape(12.dp),
                    color     = Color(0xFFFFFBEB),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                        Text(
                            text     = "${unpricedJewellery.size} jewellery item${if (unpricedJewellery.size > 1) "s" else ""} not priced yet — tap to fetch prices",
                            fontSize = 12.sp,
                            color    = Color(0xFF92400E),
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFD97706))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // ── Progress bar (mirrors progress bar in page.js) ──
            val progressAnim by animateFloatAsState(
                targetValue  = (state.zakatProgress / 100f).toFloat().coerceIn(0f, 1f),
                animationSpec = tween(600),
                label        = "ZakatProgress",
            )
            Column {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Progress to Nisab", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text("${"%.1f".format(state.zakatProgress)}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B5E20))
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress          = progressAnim,
                    modifier          = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color             = Color(0xFF1B5E20),
                    trackColor        = Color.White,
                    strokeCap         = StrokeCap.Round,
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Status-specific content block (mirrors the 4 status message blocks in page.js) ──
            when (state.zakatStatus) {
                ZakatStatus.NOT_MANDATORY  -> NotMandatoryBlock(nisabThreshold = state.nisabThreshold)
                ZakatStatus.MONITORING     -> MonitoringBlock(state = state, fmt = fmt)
                ZakatStatus.READY_TO_START -> ReadyToStartBlock(onNavigateToZakat = onNavigateToZakat)
                ZakatStatus.DUE            -> ZakatDueBlock(zakatAmount = state.zakatAmount, onNavigateToZakat = onNavigateToZakat, fmt = fmt)
                else                       -> {}
            }
        }
    }
}

@Composable
private fun ZakatInfoCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color,
    subLabel: String? = null,
) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = label.uppercase(), fontSize = 10.sp, color = accent, letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (subLabel != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = subLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WealthPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
        Text(
            text     = text,
            fontSize = 11.sp,
            color    = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

// ── Zakat status detail blocks — each mirrors one conditional block in page.js ──

@Composable
private fun NotMandatoryBlock(nisabThreshold: Double) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFF1B5E20), modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Column {
                Text("No Active Cycle", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = if (nisabThreshold == 0.0)
                        "Set the Nisab threshold in the Zakat page to begin tracking."
                    else
                        "Your wealth has not yet reached the Nisab threshold. When it does, a monitoring cycle will begin automatically.",
                    fontSize = 13.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun MonitoringBlock(state: DashboardUiState, fmt: (Double) -> String) {
    val cycle = state.activeCycle ?: return
    val sdf   = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    val startDateGreg = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(cycle.startDate)?.let { sdf.format(it) }
    }.getOrDefault("—")

    // Format Hijri date from startDateHijri map — mirrors formatHijriDate() in zakatUtils.js
    val startHijriFormatted = cycle.startDateHijri["formatted"] as? String ?: "—"

    val yearEndFormatted = state.yearEndDate?.let {
        val hijri = HijriConverter.gregorianToHijri(it)
        "${hijri.day} ${HijriConverter.getHijriMonthName(hijri.month)} ${hijri.year} AH"
    } ?: "—"
    val yearEndGreg = state.yearEndDate?.let { sdf.format(it) } ?: "—"

    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Monitoring Active", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Cycle started when your wealth reached Nisab on $startDateGreg",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonitoringInfoBox(Modifier.weight(1f), "Cycle Started", startHijriFormatted, startDateGreg ?: "")
                MonitoringInfoBox(Modifier.weight(1f), "Days Remaining", "${state.daysRemaining} days", "Until Hijri year ends")
                MonitoringInfoBox(Modifier.weight(1f), "Year End Date", yearEndFormatted, yearEndGreg)
            }
        }
    }
}

@Composable
private fun MonitoringInfoBox(modifier: Modifier, label: String, value: String, sub: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = Color(0xFFEFF6FF)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, fontSize = 10.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = sub, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReadyToStartBlock(onNavigateToZakat: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Ready to Start Monitoring", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your wealth has reached the Nisab threshold. Visit the Zakat page to start the monitoring cycle.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick  = onNavigateToZakat,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
            ) {
                Text("Start Zakat Monitoring", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ZakatDueBlock(zakatAmount: Double, onNavigateToZakat: () -> Unit, fmt: (Double) -> String) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Zakat Payment Required", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFB91C1C))
            Spacer(Modifier.height(4.dp))
            Text(
                "One Hijri year has passed and your wealth is at or above Nisab. Zakat is now obligatory.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFF1F2)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Zakat Amount (2.5%)", fontSize = 11.sp, color = Color(0xFFB91C1C), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(fmt(zakatAmount), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick  = onNavigateToZakat,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
            ) {
                Text("Go to Zakat Page to Pay", fontSize = 14.sp)
            }
        }
    }
}

// ─── Quick Actions Section ────────────────────────────────────────────────────
// Mirrors: "Quick Actions" 5-button grid in page.js

@Composable
private fun QuickActionsSection(
    onNavigateToAccounts: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToTransfer: () -> Unit,
    onNavigateToJewellery: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Quick Actions", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickActionButton("Add Account",     Icons.Default.Add,          onClick = onNavigateToAccounts)
                QuickActionButton("Transaction",     Icons.Default.Receipt,       onClick = onNavigateToTransactions)
                QuickActionButton("Transfer",        Icons.Default.SwapHoriz,    onClick = onNavigateToTransfer)
                QuickActionButton("Jewellery",       Icons.Default.Diamond,      onClick = onNavigateToJewellery, isAmber = true)
                QuickActionButton("Reports",         Icons.Default.BarChart,     onClick = onNavigateToAnalytics)
            }
        }
    }
}

@Composable
private fun QuickActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, isAmber: Boolean = false) {
    val bg    = if (isAmber) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceVariant
    val tint  = if (isAmber) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier          = Modifier.width(72.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable { onClick() }.padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = label, fontSize = 10.sp, color = tint, maxLines = 1)
    }
}

// ─── Recent Transactions Section ──────────────────────────────────────────────
// Mirrors: "Recent Transactions" list in page.js

@Composable
private fun RecentTransactionsSection(
    transactions: List<TransactionSummary>,
    onViewAll: () -> Unit,
    getAccountName: (String) -> String,
    fmt: (Double) -> String,
) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Recent Transactions", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = onViewAll) { Text("View all", fontSize = 13.sp) }
            }

            if (transactions.isEmpty()) {
                // Empty state — mirrors empty state in page.js
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier         = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    Text("No transactions yet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Add income or expenses to start tracking", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick  = onViewAll,
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Transaction")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    transactions.forEach { tx -> TransactionRow(tx, getAccountName, fmt) }
                }
            }
        }
    }
}

// ── Single transaction row — mirrors the transaction list item in page.js ──
@Composable
private fun TransactionRow(
    tx: TransactionSummary,
    getAccountName: (String) -> String,
    fmt: (Double) -> String,
) {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    val dateStr = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(tx.date)?.let { sdf.format(it) } }.getOrDefault("—")

    val iconBg    = when {
        tx.isTransfer           -> Color(0xFFEFF6FF)
        tx.type == "Income"     -> Color(0xFFF0FDF4)
        else                    -> Color(0xFFFFF1F2)
    }
    val iconTint  = when {
        tx.isTransfer           -> Color(0xFF1D4ED8)
        tx.type == "Income"     -> Color(0xFF16A34A)
        else                    -> Color(0xFFDC2626)
    }
    val icon = when {
        tx.isTransfer       -> Icons.Default.SwapHoriz
        tx.type == "Income" -> Icons.Default.TrendingUp
        else                -> Icons.Default.TrendingDown
    }

    Row(
        modifier          = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier         = Modifier.size(36.dp).background(iconBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = if (tx.isTransfer) "Transfer: ${tx.fromAccountName} → ${tx.toAccountName}"
                             else tx.description.ifBlank { "No description" },
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface,
                maxLines   = 1,
            )
            Text(
                text     = "$dateStr • ${getAccountName(tx.accountId.ifBlank { tx.accountId })}",
                fontSize = 11.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text       = "${if (tx.type == "Income") "+" else "−"}${fmt(tx.amount)}",
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (tx.type == "Income") Color(0xFF16A34A) else Color(0xFFDC2626),
        )
    }
}
