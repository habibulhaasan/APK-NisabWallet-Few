package com.hasan.nisabwallet.ui.screens.loans.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
    viewModel: LoanDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            // ─── Frozen Top Bar ───
            Surface(color = Color(0xFFF9FAFB), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(Modifier.clickable { onNavigateBack() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back", fontSize = 14.sp, color = Color(0xFF4B5563), fontWeight = FontWeight.Medium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.requestCsvExport() }, modifier = Modifier.size(36.dp).background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))) {
                            Icon(Icons.Default.Download, null, tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onNavigateToEdit(loan.id) }, modifier = Modifier.size(36.dp).background(Color(0xFFE5E7EB), RoundedCornerShape(8.dp))) {
                            Icon(Icons.Default.Edit, null, tint = Color(0xFF374151), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.openDeleteConfirm() }, modifier = Modifier.size(36.dp).background(Color(0xFFDC2626), RoundedCornerShape(8.dp))) {
                            Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Header
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.5.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.size(48.dp).background(if(isQard) Color(0xFFDCFCE7) else Color(0xFFFEF3C7), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Icon(if (isQard) Icons.Default.Money else Icons.Default.AccountBalance, null, tint = if(isQard) Color(0xFF16A34A) else Color(0xFFD97706), modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(loan.lenderName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text(if(isQard) "Qard Hasan" else "Interest-Based", fontSize = 13.sp, color = Color(0xFF6B7280))
                            }
                            Surface(shape = RoundedCornerShape(50), color = if(loan.status=="active") Color(0xFFEFF6FF) else Color(0xFFD1FAE5), modifier = Modifier.padding(top = 8.dp)) {
                                Text(loan.status.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if(loan.status=="active") Color(0xFF1D4ED8) else Color(0xFF065F46), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }

            // Repayment Progress
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Repayment Progress", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 12.dp))
                        
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("৳${CurrencyFormatter.formatBDT(loan.totalPaid)} / ৳${CurrencyFormatter.formatBDT(loan.principalAmount)}", fontSize = 13.sp, color = Color(0xFF4B5563), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${loan.progress}% Repaid", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                        }
                        LinearProgressIndicator(progress = { (loan.progress / 100f).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(10.dp).clip(RoundedCornerShape(5.dp)), color = Color(0xFF2563EB), trackColor = Color(0xFFE5E7EB))

                        Spacer(Modifier.height(16.dp))

                        val infoBoxes = mutableListOf<@Composable () -> Unit>()
                        infoBoxes.add { InfoBox(Icons.Default.AttachMoney, "Principal", fmt(loan.principalAmount), Color(0xFF111827), Color(0xFFF9FAFB), Modifier.fillMaxWidth()) }
                        infoBoxes.add { InfoBox(Icons.Default.AccountBalanceWallet, "Remaining", fmt(loan.remainingBalance), Color(0xFFDC2626), Color(0xFFFEF2F2), Modifier.fillMaxWidth()) }
                        infoBoxes.add { InfoBox(Icons.Default.Event, "Next Due", loan.nextPaymentDue ?: loan.endDate ?: "-", Color(0xFFD97706), Color(0xFFFFFBEB), Modifier.fillMaxWidth()) }
                        infoBoxes.add { InfoBox(Icons.Default.CheckCircle, "Total Paid", fmt(loan.totalPaid), Color(0xFF16A34A), Color(0xFFF0FDF4), Modifier.fillMaxWidth()) }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (i in infoBoxes.indices step 2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    Box(Modifier.weight(1f)) { infoBoxes[i]() }
                                    if (i + 1 < infoBoxes.size) Box(Modifier.weight(1f)) { infoBoxes[i + 1]() } else Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Details List
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Loan Details", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 8.dp))
                        DetailRow("Start Date", formatDisplayDate(loan.startDate))
                        DetailRow("End Date", formatDisplayDate(loan.endDate ?: ""))
                        if (!isQard) {
                            DetailRow("Interest Rate", "${loan.interestRate}%")
                            DetailRow("Monthly Payment", fmt(loan.monthlyPayment ?: 0.0))
                            DetailRow("Total Interest", fmt(loan.totalInterest))
                        }
                        
                        if (loan.notes.isNotBlank()) {
                            HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(vertical = 12.dp))
                            Text("Notes", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
                            Text(loan.notes, fontSize = 13.sp, color = Color(0xFF111827), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            // Payments Log
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Payment History", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            Text("${state.payments.size} payments", fontSize = 13.sp, color = Color(0xFF6B7280))
                        }
                        Spacer(Modifier.height(16.dp))
                        if (state.payments.isEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ReceiptLong, null, modifier = Modifier.size(32.dp), tint = Color(0xFFD1D5DB))
                                Spacer(Modifier.height(8.dp))
                                Text("No payments recorded yet", fontSize = 13.sp, color = Color(0xFF6B7280))
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.payments.forEach { p ->
                                    Row(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text(formatDisplayDate(p.paymentDate), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                            if (p.notes.isNotBlank()) Text(p.notes, fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(fmt(p.amount), fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A))
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

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteConfirm() },
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Loan Record?") },
            text = { Text("This will permanently delete this loan. Note: You cannot delete a loan that has payment history.") },
            confirmButton = { Button(onClick = { viewModel.deleteLoan() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete") } },
            dismissButton = { OutlinedButton(onClick = { viewModel.closeDeleteConfirm() }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun InfoBox(icon: ImageVector, label: String, value: String, tint: Color, bg: Color, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.background(bg, RoundedCornerShape(10.dp)).padding(12.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailRow(label: String, value: String, valColor: Color = Color(0xFF111827), isLast: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = Color(0xFF4B5563), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valColor, textAlign = TextAlign.End, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (!isLast) HorizontalDivider(color = Color(0xFFF3F4F6))
}

private fun formatDisplayDate(dateStr: String): String {
    if (dateStr.isBlank()) return "N/A"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val out = SimpleDateFormat("dd MMM, yyyy", Locale.US)
        out.format(sdf.parse(dateStr)!!)
    } catch (e: Exception) { dateStr }
}