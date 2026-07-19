package com.hasan.nisabwallet.ui.screens.loans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    viewModel: LoansViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoansEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF9FAFB)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onNavigateBack, modifier = Modifier.size(24.dp).padding(end = 8.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF111827))
                                }
                                Text("Loans", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                            Text("Track your debts and repayments", fontSize = 13.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(start = 32.dp))
                        }
                        Button(
                            onClick = { viewModel.openAddModal() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Loan", fontSize = 13.sp)
                        }
                    }
                }

                // Upcoming Alerts
                if (state.upcomingPayments.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A))
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Warning, null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp).padding(top = 2.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Upcoming Payments", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78350F), modifier = Modifier.padding(bottom = 8.dp))
                                    state.upcomingPayments.forEach { upcoming ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${upcoming.loan.lenderName} - ৳${upcoming.loan.monthlyPayment?.let { fmt(it) } ?: "N/A"}", fontSize = 13.sp, color = Color(0xFF92400E))
                                            Text(
                                                when(upcoming.daysUntilDue) {
                                                    0 -> "Due today!"
                                                    1 -> "Due tomorrow"
                                                    else -> "Due in ${upcoming.daysUntilDue} days"
                                                },
                                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                                color = if (upcoming.daysUntilDue <= 3) Color(0xFFDC2626) else Color(0xFF92400E)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Summaries
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryCard(title = "Active Loans", value = state.activeCount.toString(), icon = Icons.Default.AttachMoney, modifier = Modifier.weight(1f))
                        SummaryCard(title = "Total Debt", value = fmt(state.totalDebt), icon = Icons.Default.TrendingDown, valueColor = Color(0xFFDC2626), modifier = Modifier.weight(1.2f))
                        SummaryCard(title = "Monthly Payment", value = fmt(state.totalMonthlyPayment), icon = Icons.Default.CalendarToday, valueColor = Color(0xFF2563EB), modifier = Modifier.weight(1.3f))
                    }
                }

                // Filters
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Status", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280), modifier = Modifier.padding(bottom = 4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("all" to "All", "active" to "Active", "paid-off" to "Paid Off").forEach { (v, l) ->
                                        val sel = state.filterStatus == v
                                        Box(
                                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if(sel) Color(0xFF111827) else Color(0xFFF3F4F6)).clickable { viewModel.setFilterStatus(v) }.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) { Text(l, fontSize = 11.sp, color = if(sel) Color.White else Color(0xFF374151)) }
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Type", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280), modifier = Modifier.padding(bottom = 4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("all" to "All", "qard-hasan" to "Qard", "interest" to "Interest").forEach { (v, l) ->
                                        val sel = state.filterType == v
                                        Box(
                                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(if(sel) Color(0xFF111827) else Color(0xFFF3F4F6)).clickable { viewModel.setFilterType(v) }.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) { Text(l, fontSize = 11.sp, color = if(sel) Color.White else Color(0xFF374151)) }
                                    }
                                }
                            }
                        }
                    }
                }

                // List
                if (state.isLoading) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF111827)) } }
                } else if (state.filteredLoans.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AttachMoney, null, modifier = Modifier.size(48.dp), tint = Color(0xFFD1D5DB))
                            Spacer(Modifier.height(16.dp))
                            Text("No loans found", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
                            Text("Add your first loan to start tracking", fontSize = 13.sp, color = Color(0xFF9CA3AF))
                        }
                    }
                } else {
                    items(state.filteredLoans, key = { it.id }) { loan ->
                        LoanCard(
                            loan = loan, fmt = fmt,
                            onToggleReminders = { viewModel.toggleReminders(loan) },
                            onMakePayment = { viewModel.openPaymentModal(loan) },
                            onViewDetails = { onNavigateToDetail(loan.id) },
                            onEdit = { viewModel.openEditModal(loan) },
                            onDelete = { viewModel.openDeleteConfirm(loan) }
                        )
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    if (state.showLoanModal) {
        val calc = viewModel.calculateLoanDetails(state.loanForm)
        AddEditLoanModal(
            form = state.loanForm, accounts = state.accounts, isSaving = state.isSaving, calc = calc,
            onUpdateForm = { viewModel.updateLoanForm(it) }, onDismiss = { viewModel.closeLoanModal() }, onSave = { viewModel.saveLoan() }
        )
    }

    if (state.showPaymentModal && state.selectedLoan != null) {
        PaymentModal(
            form = state.paymentForm, loan = state.selectedLoan!!, accounts = state.accounts, isSaving = state.isSaving,
            onUpdateForm = { viewModel.updatePaymentForm(it) }, onDismiss = { viewModel.closePaymentModal() }, onSave = { viewModel.submitPayment() }
        )
    }

    if (state.showDeleteConfirm && state.selectedLoan != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteConfirm() },
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete this loan?") },
            text = { Text("This action cannot be undone. Loans with payment history cannot be deleted.") },
            confirmButton = { Button(onClick = { viewModel.deleteLoan() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete") } },
            dismissButton = { OutlinedButton(onClick = { viewModel.closeDeleteConfirm() }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, valueColor: Color = Color(0xFF111827), modifier: Modifier = Modifier) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B7280))
                Icon(icon, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LoanCard(
    loan: Loan, fmt: (Double) -> String,
    onToggleReminders: () -> Unit, onMakePayment: () -> Unit, onViewDetails: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val isQard = loan.loanType == "qard-hasan"
    val accent = if (isQard) Color(0xFF22C55E) else Color(0xFFF59E0B)

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isQard) Icons.Default.Money else Icons.Default.AccountBalance, null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(loan.lenderName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            if (loan.status == "paid-off") {
                                Spacer(Modifier.width(8.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFDBEAFE)) {
                                    Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(10.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Paid Off", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1D4ED8))
                                    }
                                }
                            }
                        }
                        Text(if (isQard) "Qard Hasan (Interest-free)" else "Interest: ${loan.interestRate}% annually", fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (loan.status == "active") {
                            IconButton(onClick = onToggleReminders, modifier = Modifier.size(32.dp)) {
                                Icon(if (loan.enableReminders) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, null, tint = if (loan.enableReminders) Color(0xFF2563EB) else Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onMakePayment, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Payment, null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(onClick = onViewDetails, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Visibility, null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp)) }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progress", fontSize = 11.sp, color = Color(0xFF6B7280))
                    Text("${loan.progress}%", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                }
                LinearProgressIndicator(progress = { loan.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp).height(8.dp).clip(RoundedCornerShape(4.dp)), color = accent, trackColor = Color(0xFFE5E7EB))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Principal", fontSize = 11.sp, color = Color(0xFF6B7280)); Text(fmt(loan.principalAmount), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827)) }
                    Column { Text("Paid", fontSize = 11.sp, color = Color(0xFF6B7280)); Text(fmt(loan.totalPaid), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A)) }
                    Column { Text("Remaining", fontSize = 11.sp, color = Color(0xFF6B7280)); Text(fmt(loan.remainingBalance), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626)) }
                    Column { Text("Monthly", fontSize = 11.sp, color = Color(0xFF6B7280)); Text(loan.monthlyPayment?.let { fmt(it) } ?: "Flexible", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB)) }
                }

                if (loan.nextPaymentDue != null && loan.status == "active") {
                    HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp), color = Color(0xFFF3F4F6))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, null, tint = Color(0xFF6B7280), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Next payment: ", fontSize = 11.sp, color = Color(0xFF6B7280))
                            Text(runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(loan.nextPaymentDue)!!) }.getOrDefault(loan.nextPaymentDue), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditLoanModal(
    form: LoanForm, accounts: List<LoanAccount>, isSaving: Boolean, calc: LoanCalculations,
    onUpdateForm: (LoanForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (form.id != null) "Edit Loan" else "New Loan", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
            }
            HorizontalDivider()

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Type Switch
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isQard = form.loanType == "qard-hasan"
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(2.dp, if(isQard) Color(0xFF22C55E) else Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).background(if(isQard) Color(0xFFF0FDF4) else Color.White).clickable { onUpdateForm(form.copy(loanType = "qard-hasan", interestRate = "0")) }.padding(12.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Money, null, tint = if(isQard) Color(0xFF16A34A) else Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("Qard Hasan", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if(isQard) Color(0xFF111827) else Color(0xFF6B7280))
                        }
                    }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(2.dp, if(!isQard) Color(0xFFF59E0B) else Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).background(if(!isQard) Color(0xFFFFFBEB) else Color.White).clickable { onUpdateForm(form.copy(loanType = "interest", interestRate = "")) }.padding(12.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AccountBalance, null, tint = if(!isQard) Color(0xFFD97706) else Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text("Interest-based", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if(!isQard) Color(0xFF111827) else Color(0xFF6B7280))
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = form.lenderName, onValueChange = { onUpdateForm(form.copy(lenderName = it)) }, label = { Text("Lender Name *") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true)
                    OutlinedTextField(value = form.principalAmount, onValueChange = { onUpdateForm(form.copy(principalAmount = it)) }, label = { Text("Principal (৳) *") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = form.interestRate, onValueChange = { onUpdateForm(form.copy(interestRate = it)) }, label = { Text("Interest Rate %" + if(!isQard) " *" else "") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !isQard)
                    DateSelectionField(label = "Start Date *", dateString = form.startDate, onDateSelected = { onUpdateForm(form.copy(startDate = it)) }, modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(value = form.monthlyPayment, onValueChange = { onUpdateForm(form.copy(monthlyPayment = it, totalMonths = "")) }, label = { Text("Monthly Payment (৳)") }, placeholder = { Text("Auto-calculated") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = form.totalMonths.isBlank())
                        if (calc.monthlyPayment > 0 && form.monthlyPayment.isBlank()) Text("Auto: ৳${String.format("%.2f", calc.monthlyPayment)}", fontSize = 10.sp, color = Color(0xFF16A34A), modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(value = form.totalMonths, onValueChange = { onUpdateForm(form.copy(totalMonths = it, monthlyPayment = "")) }, label = { Text("Duration (months)") }, placeholder = { Text("e.g. 24") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = form.monthlyPayment.isBlank())
                        if (calc.totalMonths > 0 && form.totalMonths.isBlank()) Text("Auto: ${calc.totalMonths} months", fontSize = 10.sp, color = Color(0xFF16A34A), modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                }

                var accExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(value = accounts.find { it.id == form.accountId }?.name ?: "Select account", onValueChange = {}, readOnly = true, label = { Text("Pay from Account") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                    Box(Modifier.matchParentSize().clickable { accExpanded = true })
                    DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                        accounts.forEach { a -> DropdownMenuItem(text = { Text("${a.name} (৳${a.balance})") }, onClick = { onUpdateForm(form.copy(accountId = a.id)); accExpanded = false }) }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = form.enableReminders, onCheckedChange = { onUpdateForm(form.copy(enableReminders = it)) })
                    Text("Enable payment reminders", fontSize = 12.sp, color = Color(0xFF374151))
                }

                OutlinedTextField(value = form.notes, onValueChange = { onUpdateForm(form.copy(notes = it)) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), maxLines = 2)

                // Calculation Summary Map
                Surface(color = Color(0xFFF9FAFB), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Summary", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("Monthly", fontSize = 11.sp, color = Color(0xFF6B7280)); Text("৳${String.format("%.2f", calc.monthlyPayment)}", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            Column { Text("Interest", fontSize = 11.sp, color = Color(0xFF6B7280)); Text("৳${String.format("%.2f", calc.totalInterest)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706)) }
                            Column { Text("Total", fontSize = 11.sp, color = Color(0xFF6B7280)); Text("৳${String.format("%.2f", calc.totalRepayment)}", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp)) { Text("Cancel") }
                Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))) {
                    if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Saving...") } 
                    else Text(if(form.id != null) "Update Loan" else "Create Loan")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentModal(
    form: LoanPaymentForm, loan: Loan, accounts: List<LoanAccount>, isSaving: Boolean,
    onUpdateForm: (LoanPaymentForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Make Loan Payment", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
            }
            HorizontalDivider()

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = Color(0xFFF9FAFB), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(loan.lenderName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text("Remaining: ৳${CurrencyFormatter.formatBDT(loan.remainingBalance)}", fontSize = 11.sp, color = Color(0xFF6B7280))
                    }
                }

                OutlinedTextField(
                    value = form.amount, onValueChange = { onUpdateForm(form.copy(amount = it)) },
                    label = { Text("Payment Amount (৳) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                )
                if (form.amount.toDoubleOrNull() != null) {
                    Text("After payment: ৳${CurrencyFormatter.formatBDT((loan.remainingBalance - form.amount.toDouble()).coerceAtLeast(0.0))}", fontSize = 10.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(start = 4.dp))
                }

                var accExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(value = accounts.find { it.id == form.accountId }?.name ?: "Select account", onValueChange = {}, readOnly = true, label = { Text("Pay from Account *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                    Box(Modifier.matchParentSize().clickable { accExpanded = true })
                    DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                        accounts.forEach { a -> DropdownMenuItem(text = { Text("${a.name} (৳${a.balance})") }, onClick = { onUpdateForm(form.copy(accountId = a.id)); accExpanded = false }) }
                    }
                }

                DateSelectionField(label = "Payment Date *", dateString = form.paymentDate, onDateSelected = { onUpdateForm(form.copy(paymentDate = it)) })
                OutlinedTextField(value = form.notes, onValueChange = { onUpdateForm(form.copy(notes = it)) }, label = { Text("Notes") }, placeholder = { Text("Monthly installment...") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)

                Surface(color = Color(0xFFFFFBEB), border = BorderStroke(1.dp, Color(0xFFFDE68A)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("This will create an expense transaction and reduce your account balance", fontSize = 11.sp, color = Color(0xFF92400E))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp)) { Text("Cancel") }
                    Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))) {
                        if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Processing...") } 
                        else Text("Pay Now")
                    }
                }
            }
        }
    }
}

// ── Custom Native Date Picker Field ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionField(label: String, dateString: String, onDateSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = runCatching { val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }; sdf.parse(dateString)?.time }.getOrNull()
    )

    Box(modifier = modifier) {
        OutlinedTextField(value = dateString, onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true, trailingIcon = { Icon(Icons.Default.CalendarToday, null) })
        Box(modifier = Modifier.matchParentSize().clickable { showPicker = true })
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { millis -> onDateSelected(SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))) }; showPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}