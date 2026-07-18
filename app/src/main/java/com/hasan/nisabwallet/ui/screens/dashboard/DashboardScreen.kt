package com.hasan.nisabwallet.ui.screens.dashboard

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF059669))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)), // Light gray background
        contentPadding = PaddingValues(bottom = 100.dp) // Padding for floating nav
    ) {
        item {
            DashboardHeader(state.syncStatus)
        }

        item {
            HeroBalanceCard(state.totalBalance, fmt)
        }

        item {
            MonthlySummaryPills(state.thisMonthIncome, state.thisMonthExpense, fmt)
        }

        item {
            QuickActionsRow(
                onNavigateToTransactions, onNavigateToAccounts, onNavigateToTransfer,
                onNavigateToAnalytics, onNavigateToZakat
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

@Composable
private fun DashboardHeader(syncStatus: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Assalamu Alaikum,", fontSize = 13.sp, color = Color(0xFF6B7280))
            Text("Nisab Wallet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
        }

        // Sync Status Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (syncStatus == "Synced") Color(0xFFECFDF5) else Color(0xFFFFFBEB))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (syncStatus == "Synced") Color(0xFF10B981) else Color(0xFFF59E0B), CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = syncStatus,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (syncStatus == "Synced") Color(0xFF047857) else Color(0xFFB45309)
            )
        }
    }
}

@Composable
private fun HeroBalanceCard(balance: Double, fmt: (Double) -> String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF064E3B), Color(0xFF059669)) // Deep Emerald Gradient
                )
            )
            .padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("TOTAL BALANCE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = fmt(balance),
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontFamily = FontFamily.Monospace
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
                Column {
                    Text("Income", fontSize = 12.sp, color = Color(0xFF6B7280))
                    Text(fmt(income), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
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
                Column {
                    Text("Expense", fontSize = 12.sp, color = Color(0xFF6B7280))
                    Text(fmt(expense), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onTransactions: () -> Unit,
    onAccounts: () -> Unit,
    onTransfer: () -> Unit,
    onAnalytics: () -> Unit,
    onZakat: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
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
            item { QuickActionItem("Transfer", Icons.Default.SyncAlt, Color(0xFF8B5CF6), Color(0xFFF5F3FF), onTransfer) }
            item { QuickActionItem("Accounts", Icons.Default.AccountBalance, Color(0xFFF59E0B), Color(0xFFFFFBEB), onAccounts) }
            item { QuickActionItem("Analytics", Icons.Default.PieChart, Color(0xFFEC4899), Color(0xFFFDF2F8), onAnalytics) }
            item { QuickActionItem("Zakat", Icons.Default.CleanHands, Color(0xFF10B981), Color(0xFFECFDF5), onZakat) }
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

    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
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
                                fontFamily = FontFamily.Monospace
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