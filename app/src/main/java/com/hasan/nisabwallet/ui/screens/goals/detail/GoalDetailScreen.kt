package com.hasan.nisabwallet.ui.screens.goals.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Theme Colors ───
private val Emerald500 = Color(0xFF10B981)
private val Green500 = Color(0xFF22C55E)
private val Pink500 = Color(0xFFEC4899)
private val Rose500 = Color(0xFFF43F5E)
private val Blue500 = Color(0xFF3B82F6)
private val Cyan500 = Color(0xFF06B6D4)
private val Red500 = Color(0xFFEF4444)
private val Orange500 = Color(0xFFF97316)
private val Purple500 = Color(0xFFA855F7)
private val Indigo500 = Color(0xFF6366F1)
private val Yellow500 = Color(0xFFEAB308)
private val Gray500 = Color(0xFF6B7280)
private val Gray600 = Color(0xFF4B5563)

private fun getCategoryGradient(cat: String): Brush {
    return when (cat) {
        "hajj" -> Brush.linearGradient(listOf(Emerald500, Green500))
        "marriage" -> Brush.linearGradient(listOf(Pink500, Rose500))
        "education" -> Brush.linearGradient(listOf(Blue500, Cyan500))
        "emergency" -> Brush.linearGradient(listOf(Red500, Orange500))
        "house" -> Brush.linearGradient(listOf(Purple500, Pink500))
        "car" -> Brush.linearGradient(listOf(Indigo500, Blue500))
        "business" -> Brush.linearGradient(listOf(Orange500, Yellow500))
        else -> Brush.linearGradient(listOf(Gray500, Gray600))
    }
}

private fun getCategoryIcon(cat: String): String {
    return when (cat) {
        "hajj" -> "🕋"
        "marriage" -> "💍"
        "education" -> "🎓"
        "emergency" -> "🆘"
        "house" -> "🏠"
        "car" -> "🚗"
        "business" -> "💼"
        else -> "🎯"
    }
}

@Composable
fun GoalDetailScreen(
    viewModel: GoalDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is GoalDetailEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                is GoalDetailEvent.NavigateBack -> {
                    event.message?.let { snackbarHostState.showSnackbar(it) }
                    onNavigateBack()
                }
                is GoalDetailEvent.TriggerExport -> {
                    snackbarHostState.showSnackbar("Export functionality coming soon to native app")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            Surface(color = Color(0xFFF9FAFB), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF4B5563))
                    }
                }
            }
        }
    ) { padding ->
        if (state.isLoading || state.goal == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF111827)) }
            return@Scaffold
        }

        val goal = state.goal!!
        val metrics = state.metrics

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Header[cite: 5] ───
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(64.dp).background(getCategoryGradient(goal.category), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Text(getCategoryIcon(goal.category), fontSize = 32.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(goal.goalName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(goal.description.ifBlank { "Financial Goal" }, fontSize = 14.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                
                // ─── Actions Row[cite: 5] ───
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.triggerExport() }, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Export", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { onNavigateToEdit(goal.id) }, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Edit", fontSize = 12.sp)
                    }
                    Button(onClick = { viewModel.openDeleteModal() }, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Delete", fontSize = 12.sp)
                    }
                }
            }

            // ─── Progress Card[cite: 5] ───
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Progress Overview", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            if (goal.status == "completed") {
                                Surface(shape = RoundedCornerShape(50), color = Color(0xFFD1FAE5)) { Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF15803D), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Completed", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF15803D)) } }
                            } else {
                                val bg = if (metrics.onTrack) Color(0xFFDCFCE7) else Color(0xFFFFEDD5)
                                val fg = if (metrics.onTrack) Color(0xFF15803D) else Color(0xFFC2410C)
                                Surface(shape = RoundedCornerShape(50), color = bg) { Text(if (metrics.onTrack) "On Track" else "Behind Schedule", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
                            }
                        }

                        Column(Modifier.padding(vertical = 16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("৳${fmt(goal.currentAmount)} / ৳${fmt(goal.targetAmount)}", fontSize = 13.sp, color = Color(0xFF4B5563))
                                Text("${metrics.percentageComplete}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(Color(0xFFE5E7EB), CircleShape)) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(metrics.percentageComplete / 100f).background(getCategoryGradient(goal.category), CircleShape))
                            }
                        }

                        // 4-Grid Layout
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricPill(Icons.Default.AttachMoney, Color(0xFF16A34A), "Saved", "৳${fmt(goal.currentAmount)}", Modifier.weight(1f))
                            MetricPill(Icons.Default.TrackChanges, Color(0xFF2563EB), "Target", "৳${fmt(goal.targetAmount)}", Modifier.weight(1f))
                            MetricPill(Icons.Default.Schedule, Color(0xFFEA580C), "Days Left", metrics.daysRemaining.toString(), Modifier.weight(1f))
                            MetricPill(Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF9333EA), "Monthly Need", "৳${fmt(metrics.monthlyRequired)}", Modifier.weight(1f))
                        }
                    }
                }
            }

            // ─── Goal Information & Statistics[cite: 5] ───
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Goal Information", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 12.dp))
                        DetailRow("Category", goal.category.replaceFirstChar { it.uppercase() })
                        val pColor = when(goal.priority) { "high" -> Color(0xFFDC2626) "medium" -> Color(0xFFD97706) else -> Color(0xFF16A34A) }
                        DetailRow("Priority", goal.priority.replaceFirstChar { it.uppercase() }, pColor)
                        DetailRow("Start Date", formatDateStr(goal.startDate.ifBlank { goal.createdAt.toString() }))
                        DetailRow("Target Date", formatDateStr(goal.targetDate))
                        DetailRow("Linked Account", viewModel.getAccountName(goal.linkedAccountId))
                        DetailRow("Monthly Contribution", if(goal.monthlyContribution > 0) "৳${fmt(goal.monthlyContribution)}" else "Flexible", hideBorder = true)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Statistics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 12.dp))
                        DetailRow("Total Deposits", "৳${fmt(metrics.totalDeposits)}", Color(0xFF16A34A))
                        DetailRow("Total Withdrawals", "৳${fmt(metrics.totalWithdrawals)}", Color(0xFFDC2626))
                        DetailRow("Net Savings", "৳${fmt(metrics.netSavings)}", Color(0xFF2563EB))
                        DetailRow("Days Elapsed", "${metrics.daysElapsed} days")
                        DetailRow("Total Duration", "${metrics.totalDays} days", hideBorder = true)
                    }
                }
            }

            // ─── Transaction History[cite: 5] ───
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Transaction History", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 12.dp))
                        
                        if (state.transactions.isEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CalendarToday, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No transactions yet", fontSize = 14.sp, color = Color(0xFF6B7280))
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                state.transactions.forEach { tx ->
                                    val isDep = tx.type == "deposit"
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(if(isDep) Color(0xFFF0FDF4) else Color(0xFFFEF2F2), RoundedCornerShape(8.dp)).border(1.dp, if(isDep) Color(0xFFBBF7D0) else Color(0xFFFECACA), RoundedCornerShape(8.dp)).padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top
                                    ) {
                                        Row(Modifier.weight(1f)) {
                                            Icon(if(isDep) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null, tint = if(isDep) Color(0xFF16A34A) else Color(0xFFDC2626), modifier = Modifier.size(18.dp).padding(top = 2.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(if(isDep) "Deposit" else "Withdrawal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                                                Text(viewModel.getAccountName(tx.accountId), fontSize = 11.sp, color = Color(0xFF4B5563))
                                                if (tx.description.isNotBlank()) Text(tx.description, fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp))
                                                Text(formatDateStr(tx.date), fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp))
                                            }
                                        }
                                        Text(if(isDep) "+৳${fmt(tx.amount)}" else "-৳${fmt(tx.amount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if(isDep) Color(0xFF16A34A) else Color(0xFFDC2626))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Milestones[cite: 5] ───
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                            Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Milestones", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(25, 50, 75, 100).forEach { ms ->
                                val achieved = metrics.percentageComplete >= ms
                                Column(
                                    modifier = Modifier.weight(1f).background(if(achieved) Color(0xFFF0FDF4) else Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).border(if(achieved) 2.dp else 1.dp, if(achieved) Color(0xFF22C55E) else Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(when(ms) { 25->"✨" 50->"🌟" 75->"🎊" else->"🎉" }, fontSize = 20.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    Text("$ms%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if(achieved) Color(0xFF16A34A) else Color(0xFF9CA3AF))
                                    Text(if(achieved) "Achieved!" else "Not yet", fontSize = 10.sp, color = Color(0xFF4B5563), modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── Delete Modal[cite: 5] ───
        if (state.showDeleteModal) {
            AlertDialog(
                onDismissRequest = { viewModel.closeDeleteModal() },
                icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444), modifier = Modifier.size(36.dp)) },
                title = { Text("Delete Goal?") },
                text = { Text("This will permanently delete \"${goal.goalName}\" and all transaction history. The allocated amount will become available in your account.", textAlign = TextAlign.Center) },
                confirmButton = { Button(onClick = { viewModel.deleteGoalOfflineFriendly() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete") } },
                dismissButton = { OutlinedButton(onClick = { viewModel.closeDeleteModal() }) { Text("Cancel") } }
            )
        }
    }
}

// ─── Helper Components ───

@Composable
private fun MetricPill(icon: ImageVector, tint: Color, label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier.background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp).padding(bottom = 4.dp))
        Text(label, fontSize = 10.sp, color = Color(0xFF6B7280))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = Color(0xFF111827), hideBorder: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = Color(0xFF4B5563))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor)
        }
        if (!hideBorder) HorizontalDivider(color = Color(0xFFF3F4F6))
    }
}

private fun formatDateStr(dateStr: String): String {
    if (dateStr.isBlank()) return "N/A"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val out = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        out.format(sdf.parse(dateStr)!!)
    } catch (_: Exception) { 
        try {
            val ts = dateStr.toLong()
            SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ts))
        } catch (_: Exception) { dateStr }
    }
}