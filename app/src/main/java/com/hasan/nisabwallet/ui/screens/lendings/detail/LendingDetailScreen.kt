package com.hasan.nisabwallet.ui.screens.lendings.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LendingDetailScreen(
    viewModel: LendingDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
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
                is LendingDetailEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                is LendingDetailEvent.NavigateBack -> onNavigateBack()
                is LendingDetailEvent.TriggerCsvExport -> {
                    val name = state.lending?.borrowerName?.replace(" ", "_") ?: "lending"
                    createCsvLauncher.launch("${name}_payments.csv")
                }
            }
        }
    }

    if (state.isLoading || state.lending == null || state.statusInfo == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF111827))
        }
        return
    }

    val lending = state.lending!!
    val status = state.statusInfo!!
    val isQard = lending.lendingType == "qard-hasan"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF9FAFB)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Actions
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clickable { onNavigateBack() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back", fontSize = 14.sp, color = Color(0xFF4B5563), fontWeight = FontWeight.Medium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.requestCsvExport() }, modifier = Modifier.size(36.dp).background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))) {
                            Icon(Icons.Default.Download, null, tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.openDeleteConfirm() }, modifier = Modifier.size(36.dp).background(Color(0xFFDC2626), RoundedCornerShape(8.dp))) {
                            Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Hero Profile
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.dp).background(if(isQard) Color(0xFF059669) else Color(0xFF2563EB), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Security, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(lending.borrowerName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if(isQard) "Qard Hasan (Interest-Free)" else "Conventional Lending", fontSize = 13.sp, color = Color(0xFF6B7280))
                    }
                }
            }

            // Status Alerts
            if (status.isOverdue) {
                item {
                    Surface(color = Color(0xFFFEF2F2), border = BorderStroke(2.dp, Color(0xFFEF4444)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Payment Overdue!", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7F1D1D))
                                Text("This payment is ${status.daysOverdue} days overdue. Consider sending a reminder.", fontSize = 13.sp, color = Color(0xFFB91C1C), modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            } else if (lending.status == "completed") {
                item {
                    Surface(color = Color(0xFFF0FDF4), border = BorderStroke(2.dp, Color(0xFF22C55E)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Fully Repaid!", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14532D))
                                Text("This lending has been completely paid off. Great job!", fontSize = 13.sp, color = Color(0xFF15803D), modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }

            // Progress Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Repayment Progress", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            Surface(shape = RoundedCornerShape(50), color = if(lending.status=="completed") Color(0xFFD1FAE5) else if(status.isOverdue) Color(0xFFFEE2E2) else Color(0xFFDBEAFE)) {
                                Text(
                                    if(lending.status=="completed") "Completed" else if(status.isOverdue) "Overdue" else "Active",
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                    color = if(lending.status=="completed") Color(0xFF15803D) else if(status.isOverdue) Color(0xFFB91C1C) else Color(0xFF1D4ED8),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("৳${CurrencyFormatter.formatBDT(lending.totalRepaid)} / ৳${CurrencyFormatter.formatBDT(lending.principalAmount)}", fontSize = 12.sp, color = Color(0xFF4B5563))
                            Text("${status.percentagePaid}% Repaid", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        }
                        LinearProgressIndicator(progress = { (status.percentagePaid / 100f).toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(12.dp).clip(RoundedCornerShape(6.dp)), color = if(lending.status=="completed") Color(0xFF16A34A) else Color(0xFF2563EB), trackColor = Color(0xFFE5E7EB))

                        Spacer(Modifier.height(16.dp))
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoBox(Icons.Default.AttachMoney, "Principal", fmt(lending.principalAmount), Color(0xFF2563EB), Color(0xFFEFF6FF), Modifier.weight(1f, false).fillMaxWidth(0.45f))
                            InfoBox(Icons.Default.CheckCircle, "Repaid", fmt(lending.totalRepaid), Color(0xFF16A34A), Color(0xFFF0FDF4), Modifier.weight(1f, false).fillMaxWidth(0.45f))
                            InfoBox(Icons.AutoMirrored.Filled.TrendingUp, "Remaining", fmt(status.remainingBalance), Color(0xFFDC2626), Color(0xFFFEF2F2), Modifier.weight(1f, false).fillMaxWidth(0.45f))
                            InfoBox(Icons.Default.Schedule, "Payments", "${lending.paymentsReceived}${if(lending.totalInstallments!=null) " / ${lending.totalInstallments}" else ""}", Color(0xFF9333EA), Color(0xFFFAF5FF), Modifier.weight(1f, false).fillMaxWidth(0.45f))
                        }
                    }
                }
            }

            // Borrower & Details Layout
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Borrower Information
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                                Icon(Icons.Default.Person, null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Borrower Information", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                            DetailRow("Full Name", lending.borrowerName, Icons.Default.PersonOutline)
                            if (lending.borrowerContact.phone.isNotBlank()) DetailRow("Phone Number", lending.borrowerContact.phone, Icons.Default.Phone)
                            if (lending.borrowerContact.email.isNotBlank()) DetailRow("Email", lending.borrowerContact.email, Icons.Default.Mail)
                            if (lending.borrowerContact.address.isNotBlank()) DetailRow("Address", lending.borrowerContact.address, Icons.Default.Place, isLast = true)
                        }
                    }

                    // Lending Details
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Article, null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Lending Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                            TextRow("Type", if(isQard) "Qard Hasan" else "Conventional")
                            TextRow("Category", lending.category.replaceFirstChar { it.uppercase() })
                            TextRow("Lending Date", formatDisplayDate(lending.lendingDate))
                            TextRow("Due Date", formatDisplayDate(lending.dueDate))
                            TextRow("Repayment Type", if(lending.repaymentType == "installments") "Installments" else "Full Payment")
                            if (lending.repaymentType == "installments") {
                                TextRow("Installment Amount", "৳${lending.installmentAmount?.let { fmt(it) }}")
                                TextRow("Frequency", lending.installmentFrequency.replaceFirstChar { it.uppercase() })
                            }
                            TextRow("Account", viewModel.getAccountName(lending.accountId))
                            TextRow("Next Payment Due", formatDisplayDate(lending.nextPaymentDue ?: lending.dueDate), valColor = if (status.isOverdue) Color(0xFFDC2626) else Color(0xFF111827), isLast = true)
                        }
                    }

                    // Witnesses
                    if (lending.witnesses.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                                    Icon(Icons.Default.People, null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Witnesses (Islamic Requirement)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    lending.witnesses.forEachIndexed { idx, w ->
                                        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(32.dp).background(Color(0xFFD1FAE5), CircleShape), contentAlignment = Alignment.Center) {
                                                Text("${idx + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(w.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                                if (w.contact.isNotBlank()) Text(w.contact, fontSize = 11.sp, color = Color(0xFF4B5563))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Statistics
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Statistics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 12.dp))
                            TextRow("Total Payments Received", status.totalPayments.toString())
                            TextRow("Expected Payments", status.expectedPayments.toString())
                            TextRow("Payments On Time", status.paymentsOnTime.toString(), valColor = Color(0xFF16A34A))
                            TextRow("Payments Missed", "0", valColor = Color(0xFFDC2626)) // Set to static 0 as it's not tracked
                            TextRow("On-Time Rate", String.format(Locale.US, "%.0f%%", status.paymentRate))
                            TextRow("Reminders Sent", state.reminders.size.toString(), isLast = true)
                        }
                    }
                }
            }

            // Payment History
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Payment History", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                            if (state.payments.isNotEmpty()) {
                                Row(Modifier.clickable { viewModel.requestCsvExport() }, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Download, null, tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Export CSV", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (state.payments.isEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CalendarToday, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No payments received yet", fontSize = 13.sp, color = Color(0xFF6B7280))
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                state.payments.forEach { p ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF0FDF4), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(12.dp)).padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Row(Modifier.weight(1f)) {
                                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text("Payment Received", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                                Text("${formatDisplayDate(p.paymentDate)} • ${p.paymentMethod.replaceFirstChar { it.uppercase() }.replace("-", " ")}", fontSize = 11.sp, color = Color(0xFF4B5563), modifier = Modifier.padding(top = 2.dp))
                                                if (p.installmentNumber != null) Text("Installment #${p.installmentNumber}${if(lending.totalInstallments!=null) " of ${lending.totalInstallments}" else ""}", fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp))
                                                if (p.notes.isNotBlank()) Text(p.notes, fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp))
                                            }
                                        }
                                        Text("+৳${CurrencyFormatter.formatBDT(p.amount)}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Reminder History
            if (state.reminders.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Reminder History", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                state.reminders.forEach { r ->
                                    Row(Modifier.fillMaxWidth().background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.Top) {
                                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("Reminder sent to ${r.borrowerContact}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                            Text(runCatching { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(r.sentAtMillis)) }.getOrDefault("Unknown"), fontSize = 11.sp, color = Color(0xFF4B5563), modifier = Modifier.padding(top = 2.dp))
                                            Surface(color = Color.White, border = BorderStroke(1.dp, Color(0xFFDBEAFE)), shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                                                Text(r.message, fontSize = 11.sp, color = Color(0xFF374151), modifier = Modifier.padding(8.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteConfirm() },
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Lending Record?") },
            text = { Text("This will permanently delete the lending record for \"${lending.borrowerName}\" and all payment history.") },
            confirmButton = { Button(onClick = { viewModel.deleteLending() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete") } },
            dismissButton = { OutlinedButton(onClick = { viewModel.closeDeleteConfirm() }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun InfoBox(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, tint: Color, bg: Color, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.background(bg, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF6B7280))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isLast: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 11.sp, color = Color(0xFF6B7280))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), modifier = Modifier.padding(top = 2.dp))
        }
    }
    if (!isLast) HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(start = 28.dp))
}

@Composable
private fun TextRow(label: String, value: String, valColor: Color = Color(0xFF111827), isLast: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 12.sp, color = Color(0xFF4B5563), modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valColor, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
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