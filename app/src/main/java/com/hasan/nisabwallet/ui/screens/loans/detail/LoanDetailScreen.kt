package com.hasan.nisabwallet.ui.screens.loans.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import androidx.compose.foundation.border
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
    viewModel: LoanDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    var showAmortization by remember { mutableStateOf(false) }

    val createCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.executeCsvExport(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoanDetailEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                is LoanDetailEvent.NavigateBack -> onNavigateBack()
                is LoanDetailEvent.TriggerCsvExport -> {
                    val name = state.loan?.lenderName?.replace(" ", "_") ?: "loan"
                    createCsvLauncher.launch("${name}_payments.csv")
                }
            }
        }
    }

    if (state.isLoading || state.loan == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF111827))
        }
        return
    }

    val loan = state.loan!!
    val isQard = loan.loanType == "qard-hasan"
    val accent = if (isQard) Color(0xFF22C55E) else Color(0xFFF59E0B)
    val accentBg = if (isQard) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF9FAFB)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.clickable { onNavigateBack() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back to Loans", fontSize = 14.sp, color = Color(0xFF4B5563), fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = { onNavigateToEdit(loan.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF374151)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Loan Hero
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.5.dp)
                ) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                            Box(modifier = Modifier.size(48.dp).background(accentBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(if (isQard) Icons.Default.Money else Icons.Default.AccountBalance, null, tint = accent, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(loan.lenderName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (isQard) "Interest-free loan (Qard Hasan)" else "${loan.interestRate}% Annual Interest", fontSize = 13.sp, color = Color(0xFF4B5563), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (loan.status == "paid-off") {
                                    Surface(shape = RoundedCornerShape(50), color = Color(0xFFDBEAFE), modifier = Modifier.padding(top = 8.dp)) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Paid Off", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D4ED8))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Progress Bar
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.5.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Loan Progress", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Payment Progress", fontSize = 13.sp, color = Color(0xFF4B5563))
                            Text("${loan.progress}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                        }
                        LinearProgressIndicator(progress = { loan.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(12.dp).clip(RoundedCornerShape(6.dp)), color = accent, trackColor = Color(0xFFE5E7EB))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Paid: ৳${CurrencyFormatter.formatBDT(loan.totalPaid)}", fontSize = 11.sp, color = Color(0xFF4B5563), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("Remaining: ৳${CurrencyFormatter.formatBDT(loan.remainingBalance)}", fontSize = 11.sp, color = Color(0xFF4B5563), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // 4-Grid Stats (Fixed Grid Simulator)
            item {
                val stats = mutableListOf<@Composable () -> Unit>()
                stats.add { StatBox("Principal Amount", Icons.Default.AttachMoney, fmt(loan.principalAmount), Modifier.fillMaxWidth()) }
                stats.add { StatBox("Interest Rate", Icons.Default.Percent, if (isQard) "0%" else "${loan.interestRate}%", Modifier.fillMaxWidth(), sub = if (isQard) "Qard Hasan" else "Annual") }
                stats.add { StatBox("Total Paid", Icons.AutoMirrored.Filled.TrendingDown, fmt(loan.totalPaid), Modifier.fillMaxWidth(), valColor = Color(0xFF16A34A)) }
                stats.add { StatBox("Monthly Payment", Icons.Default.CalendarToday, loan.monthlyPayment?.let { fmt(it) } ?: "Flexible", Modifier.fillMaxWidth(), valColor = Color(0xFF2563EB)) }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (i in stats.indices step 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.weight(1f)) { stats[i]() }
                            if (i + 1 < stats.size) Box(Modifier.weight(1f)) { stats[i + 1]() } else Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Loan Details List
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.5.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Loan Details", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 8.dp))

                        DetailRow("Start Date", formatDisplayDate(loan.startDate))
                        if (!loan.endDate.isNullOrBlank()) DetailRow("End Date", formatDisplayDate(loan.endDate))
                        if (loan.totalMonths > 0) DetailRow("Duration", "${loan.totalMonths} months")
                        DetailRow("Remaining Balance", "৳${CurrencyFormatter.formatBDT(loan.remainingBalance)}", valColor = Color(0xFFDC2626))

                        if (!isQard) {
                            DetailRow("Total Interest", "৳${CurrencyFormatter.formatBDT(loan.totalInterest)}", valColor = Color(0xFFD97706))
                            DetailRow("Total Repayment", "৳${CurrencyFormatter.formatBDT(loan.totalRepayment)}")
                        }

                        if (!loan.nextPaymentDue.isNullOrBlank() && loan.status == "active") DetailRow("Next Payment Due", formatDisplayDate(loan.nextPaymentDue), valColor = Color(0xFF2563EB))
                        if (!loan.lastPaymentDate.isNullOrBlank()) DetailRow("Last Payment", formatDisplayDate(loan.lastPaymentDate))

                        val accName = state.accounts.find { it.id == loan.accountId }?.name ?: "Unknown"
                        DetailRow("Payment Account", accName)
                        DetailRow("Status", if (loan.status == "active") "Active" else "Paid Off", valColor = if (loan.status == "active") Color(0xFF16A34A) else Color(0xFF2563EB))

                        if (loan.notes.isNotBlank()) {
                            HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(vertical = 12.dp))
                            Text("Notes", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
                            Text(loan.notes, fontSize = 13.sp, color = Color(0xFF111827), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            // Early Payoff Calculator
            if (!isQard && loan.status == "active" && state.earlyPayoff500 != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.5.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Early Payoff Calculator", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                            Text("See how extra payments can save you money and time:", fontSize = 13.sp, color = Color(0xFF4B5563), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val p500 = state.earlyPayoff500!!
                                Column(modifier = Modifier.weight(1f).background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp)).padding(12.dp)) {
                                    Text("Extra ৳500/month", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E3A8A))
                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Remaining:", fontSize = 11.sp, color = Color(0xFF1D4ED8)); Text("${p500.monthsRemaining} mo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E3A8A)) }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Saved:", fontSize = 11.sp, color = Color(0xFF1D4ED8)); Text("৳${String.format(Locale.US, "%.2f", p500.savedInterest)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E3A8A)) }
                                }

                                state.earlyPayoff1000?.let { p1000 ->
                                    Column(modifier = Modifier.weight(1f).background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp)).padding(12.dp)) {
                                        Text("Extra ৳1,000/month", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF14532D))
                                        Spacer(Modifier.height(8.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Remaining:", fontSize = 11.sp, color = Color(0xFF15803D)); Text("${p1000.monthsRemaining} mo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF14532D)) }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Saved:", fontSize = 11.sp, color = Color(0xFF15803D)); Text("৳${String.format(Locale.US, "%.2f", p1000.savedInterest)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF14532D)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Amortization Schedule
            if (!isQard && state.amortizationSchedule.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.5.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Amortization Schedule", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                                TextButton(onClick = { showAmortization = !showAmortization }, contentPadding = PaddingValues(0.dp)) {
                                    Text(if (showAmortization) "Hide Schedule" else "Show Schedule", fontSize = 13.sp, color = Color(0xFF2563EB))
                                }
                            }
                            AnimatedVisibility(visible = showAmortization) {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                                    Column(Modifier.width(420.dp)) {
                                        Row(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                                            Text("Mo.", modifier = Modifier.weight(0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563))
                                            Text("Payment", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563), textAlign = TextAlign.End)
                                            Text("Principal", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563), textAlign = TextAlign.End)
                                            Text("Interest", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563), textAlign = TextAlign.End)
                                            Text("Balance", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563), textAlign = TextAlign.End)
                                        }
                                        state.amortizationSchedule.forEach { row ->
                                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                                                Text(row.month.toString(), modifier = Modifier.weight(0.5f), fontSize = 11.sp, color = Color(0xFF111827))
                                                Text(fmt(row.payment), modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color(0xFF111827), textAlign = TextAlign.End)
                                                Text(fmt(row.principal), modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color(0xFF16A34A), textAlign = TextAlign.End)
                                                Text(fmt(row.interest), modifier = Modifier.weight(1f), fontSize = 11.sp, color = Color(0xFFD97706), textAlign = TextAlign.End)
                                                Text(fmt(row.balance), modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), textAlign = TextAlign.End)
                                            }
                                            HorizontalDivider(color = Color(0xFFF3F4F6))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Payment History
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.5.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Payment History (${state.payments.size} payments)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                            Button(
                                onClick = { viewModel.requestCsvExport() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF374151)),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export CSV", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (state.payments.isEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(32.dp), tint = Color(0xFFD1D5DB))
                                Spacer(Modifier.height(8.dp))
                                Text("No payments recorded yet", fontSize = 13.sp, color = Color(0xFF6B7280))
                            }
                        } else {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                Column(Modifier.width(600.dp)) {
                                    Row(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text("Date", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563))
                                        Text("Amount", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563), textAlign = TextAlign.End)
                                        Text("Principal", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563), textAlign = TextAlign.End)
                                        Text("Interest", modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563), textAlign = TextAlign.End)
                                        Text("Account", modifier = Modifier.weight(1.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563))
                                        Text("Notes", modifier = Modifier.weight(1.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563))
                                    }
                                    state.payments.forEach { p ->
                                        val accName = state.accounts.find { it.id == p.accountId }?.name ?: "Unknown"
                                        Row(Modifier.fillMaxWidth().border(1.dp, Color(0xFFE5E7EB)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                                            Text(formatDisplayDate(p.paymentDate), modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color(0xFF111827))
                                            Text(fmt(p.amount), modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), textAlign = TextAlign.End)
                                            Text(fmt(p.principalPaid), modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color(0xFF16A34A), textAlign = TextAlign.End)
                                            Text(fmt(p.interestPaid), modifier = Modifier.weight(1f), fontSize = 12.sp, color = Color(0xFFD97706), textAlign = TextAlign.End)
                                            Text(accName, modifier = Modifier.weight(1.5f), fontSize = 12.sp, color = Color(0xFF374151), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(p.notes.ifBlank { "-" }, modifier = Modifier.weight(1.5f), fontSize = 12.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun StatBox(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, modifier: Modifier, sub: String? = null, valColor: Color = Color(0xFF111827)) {
    Column(modifier = modifier.background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF6B7280), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valColor, modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailRow(label: String, value: String, valColor: Color = Color(0xFF111827)) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = Color(0xFF4B5563), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valColor, textAlign = TextAlign.End, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(color = Color(0xFFF3F4F6))
}

private fun formatDisplayDate(dateStr: String): String {
    if (dateStr.isBlank()) return "N/A"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val out = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        out.format(sdf.parse(dateStr)!!)
    } catch (e: Exception) { dateStr }
}