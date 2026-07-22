package com.hasan.nisabwallet.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    var showBalanceModal by remember { mutableStateOf(false) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF059669))
        }
        return
    }

    Scaffold(
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            Surface(color = Color(0xFFF9FAFB), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(48.dp))
                        Column {
                            Text("Assalamu Alaikum, ${state.userName}!", fontSize = 13.sp, color = Color(0xFF6B7280))
                            Text("Nisab Wallet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                HeroBalanceCard(state.totalBalance, fmt, onClick = { showBalanceModal = true })
            }

            item {
                MonthlySummaryPills(state.thisMonthIncome, state.thisMonthExpense, fmt)
            }

            if (state.totalLoans > 0) {
                item {
                    LoanStatusCard(state.totalLoans, fmt, onNavigateToLoans)
                }
            }

            item {
                ZakatStatusCard(state, fmt, onNavigateToZakat)
            }

            item {
                QuickActionsRow(
                    onTransactions = onNavigateToTransactions,
                    onAccounts = onNavigateToAccounts,
                    onJewellery = onNavigateToJewellery,
                    onAnalytics = onNavigateToAnalytics
                )
            }

            item {
                RecentTransactionsSection(
                    transactions = state.recentTransactions,
                    categories = state.categories,
                    onViewAll = onNavigateToTransactions,
                    fmt = fmt
                )
            }
        }
    }

    if (showBalanceModal) {
        BalanceBreakdownModal(
            accounts = state.accounts,
            totalBalance = state.totalBalance,
            fmt = fmt,
            onDismiss = { showBalanceModal = false },
            onManageAccounts = onNavigateToAccounts
        )
    }
}

@Composable
private fun LoanStatusCard(totalLoans: Double, fmt: (Double) -> String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFFECACA))
    ) {
        Row(
            modifier = Modifier.background(Brush.linearGradient(listOf(Color(0xFFFEF2F2), Color(0xFFFFF1F2)))).padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MoneyOff, null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Active Loans", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                    Text(fmt(totalLoans), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFDC2626), modifier = Modifier.padding(top = 2.dp))
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun HeroBalanceCard(balance: Double, fmt: (Double) -> String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF064E3B), Color(0xFF059669))
                )
            )
            .clickable { onClick() }
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("TOTAL BALANCE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.OpenInFull, contentDescription = "Breakdown", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = fmt(balance),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MonthlySummaryPills(income: Double, expense: Double, fmt: (Double) -> String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(Color(0xFFECFDF5), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Color(0xFF10B981))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Income", fontSize = 12.sp, color = Color(0xFF6B7280))
                    Text(fmt(income), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).background(Color(0xFFFEF2F2), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = Color(0xFFEF4444))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Expense", fontSize = 12.sp, color = Color(0xFF6B7280))
                    Text(fmt(expense), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ZakatStatusCard(state: DashboardUiState, fmt: (Double) -> String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFD1FAE5))
    ) {
        Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFECFDF5), Color(0xFFF0FDF4)))).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Zakat Status", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                }
                Surface(shape = RoundedCornerShape(50), color = if(state.zakatStatus == "Due") Color(0xFFDC2626) else Color(0xFF2563EB)) {
                    Text(state.zakatStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Net Zakatable Wealth", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmt(state.netZakatableWealth), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF111827), modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Nisab Threshold", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if(state.nisabThreshold>0) fmt(state.nisabThreshold) else "Not Set", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF111827), modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (state.nisabThreshold > 0) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progress to Nisab", fontSize = 12.sp, color = Color(0xFF374151))
                    Text("${String.format(Locale.US, "%.1f", state.zakatProgress)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                }
                LinearProgressIndicator(progress = { (state.zakatProgress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)), color = Color(0xFF059669), trackColor = Color(0xFFD1FAE5))
            }

            if (state.zakatStatus == "Due") {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Zakat Amount Due (2.5%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    Text(fmt(state.zakatAmount), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF991B1B))
                }
            } else if (state.activeCycleDate != null && state.zakatStatus == "Monitoring") {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Days Remaining in Year", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    Text("${state.daysRemaining} Days", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8))
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onTransactions: () -> Unit,
    onAccounts: () -> Unit,
    onJewellery: () -> Unit,
    onAnalytics: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = "Quick Actions",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827),
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { QuickActionItem("Records", Icons.Default.ReceiptLong, Color(0xFF3B82F6), Color(0xFFEFF6FF), onTransactions) }
            item { QuickActionItem("Accounts", Icons.Default.AccountBalance, Color(0xFFF59E0B), Color(0xFFFFFBEB), onAccounts) }
            item { QuickActionItem("Jewellery", Icons.Default.Diamond, Color(0xFFD97706), Color(0xFFFFFBEB), onJewellery) }
            item { QuickActionItem("Analytics", Icons.Default.PieChart, Color(0xFFEC4899), Color(0xFFFDF2F8), onAnalytics) }
        }
    }
}

@Composable
private fun QuickActionItem(label: String, icon: ImageVector, iconColor: Color, bgColor: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(bgColor, RoundedCornerShape(16.dp))
                .border(1.dp, iconColor.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4B5563))
    }
}

@Composable
private fun RecentTransactionsSection(
    transactions: List<DashboardTransaction>,
    categories: Map<String, DashboardCategory>,
    onViewAll: () -> Unit,
    fmt: (Double) -> String
) {
    val display = remember { SimpleDateFormat("dd MMM", Locale.US) }
    val parser = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent Transactions", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
            TextButton(onClick = onViewAll, contentPadding = PaddingValues(0.dp)) {
                Text("View All", fontSize = 13.sp, color = Color(0xFF059669))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp).padding(start = 2.dp))
            }
        }

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No recent transactions", fontSize = 13.sp, color = Color(0xFF9CA3AF))
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Column {
                    transactions.forEachIndexed { index, tx ->
                        val cat = categories[tx.categoryId]
                        val catName = if (tx.isTransfer) "Transfer" else cat?.name ?: "Uncategorized"
                        val colorHex = if (tx.isTransfer) "#3B82F6" else cat?.color ?: "#6B7280"
                        val catColor = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Color.Gray)

                        val dateLabel = runCatching { display.format(parser.parse(tx.date)!!) }.getOrDefault(tx.date)

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).background(catColor.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tx.isTransfer) {
                                    Icon(Icons.Default.SyncAlt, contentDescription = null, tint = catColor, modifier = Modifier.size(18.dp))
                                } else {
                                    Box(modifier = Modifier.size(12.dp).background(catColor, CircleShape))
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = catName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                val subtitle = if (tx.isTransfer) {
                                    if (tx.type == "Income") "From ${tx.relatedAccountName} · $dateLabel"
                                    else "To ${tx.relatedAccountName} · $dateLabel"
                                } else {
                                    val desc = if (tx.description.isNotBlank()) "${tx.description} · " else ""
                                    "$desc$dateLabel"
                                }
                                Text(subtitle, fontSize = 12.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            val amountColor = if (tx.isTransfer) Color(0xFF3B82F6) else if (tx.type == "Income") Color(0xFF10B981) else Color(0xFFEF4444)
                            val sign = if (tx.isTransfer) "" else if (tx.type == "Income") "+" else "-"

                            Text(
                                text = "$sign${fmt(tx.amount)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = amountColor,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }

                        if (index < transactions.lastIndex) {
                            HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalanceBreakdownModal(
    accounts: List<DashboardAccount>,
    totalBalance: Double,
    fmt: (Double) -> String,
    onDismiss: () -> Unit,
    onManageAccounts: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = Color.White
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Balance Breakdown", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
            }
            HorizontalDivider()

            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total Available", fontSize = 13.sp, color = Color(0xFF6B7280))
                    Text(fmt(totalBalance), fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF059669), fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(16.dp))

                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF9FAFB), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column {
                        accounts.forEachIndexed { index, acc ->
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(acc.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(acc.type, fontSize = 11.sp, color = Color(0xFF6B7280))
                                }
                                Text(fmt(acc.balance), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), fontFamily = FontFamily.Monospace)
                            }
                            if (index < accounts.lastIndex) HorizontalDivider(color = Color(0xFFE5E7EB))
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { onDismiss(); onManageAccounts() }) {
                    Text("Manage Accounts", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp).padding(start = 4.dp))
                }
            }
        }
    }
}