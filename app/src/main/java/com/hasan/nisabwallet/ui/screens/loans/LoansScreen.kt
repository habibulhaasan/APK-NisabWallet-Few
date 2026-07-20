package com.hasan.nisabwallet.ui.screens.loans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoansScreen(
    viewModel: LoansViewModel = hiltViewModel(),
    triggerFabAdd: Long = 0L,
    onAddHandled: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    LaunchedEffect(triggerFabAdd) {
        if (triggerFabAdd > 0L) {
            viewModel.openAddModal()
            onAddHandled()
        }
    }

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
                // Header with Offset Spacer for Left Drawer Toggle
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(56.dp))
                                Icon(Icons.Default.AccountBalance, null, tint = Color(0xFF111827), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Loans Management", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("Track money you borrowed", fontSize = 13.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(start = 88.dp))
                        }
                    }
                }

                // Add Button
                item {
                    Button(
                        onClick = { viewModel.openAddModal() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Loan Record", fontSize = 14.sp)
                    }
                }

                // Summary Cards (Fixed Grid Simulator)
                item {
                    val cards = mutableListOf<@Composable () -> Unit>()
                    cards.add { SummaryCard("Total Debt", fmt(state.totalDebt), Icons.AutoMirrored.Filled.TrendingDown, Color(0xFFDC2626)) }
                    cards.add { SummaryCard("Monthly Payment", fmt(state.totalMonthlyPayment), Icons.Default.CalendarToday, Color(0xFF2563EB)) }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in cards.indices step 2) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Box(Modifier.weight(1f)) { cards[i]() }
                                if (i + 1 < cards.size) Box(Modifier.weight(1f)) { cards[i + 1]() } else Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Filters
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("all" to "All", "active" to "Active", "paid-off" to "Paid Off").forEach { (v, l) ->
                                val sel = state.filterStatus == v
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if(sel) Color(0xFF111827) else Color(0xFFF3F4F6)).clickable { viewModel.setFilterStatus(v) }.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(l, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if(sel) Color.White else Color(0xFF374151), maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
                            Icon(Icons.Default.AccountBalance, null, modifier = Modifier.size(48.dp), tint = Color(0xFFD1D5DB))
                            Spacer(Modifier.height(16.dp))
                            Text("No loan records", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                            Text("Create a record when you borrow money", fontSize = 13.sp, color = Color(0xFF6B7280))
                        }
                    }
                } else {
                    items(state.filteredLoans, key = { it.id }) { loan ->
                        LoanCard(
                            loan = loan, fmt = fmt,
                            onPayment = { viewModel.openPaymentModal(loan) },
                            onView = { onNavigateToDetail(loan.id) },
                            onEdit = { viewModel.openEditModal(loan) },
                            onDelete = { viewModel.openDeleteConfirm(loan) }
                        )
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }

        if (state.showLoanModal) {
            AddEditLoanModal(
                form = state.loanForm, accounts = state.accounts, isSaving = state.isSaving,
                onUpdateForm = { newForm -> viewModel.updateLoanForm { newForm } },
                onDismiss = { viewModel.closeLoanModal() },
                onSave = { viewModel.saveLoan() },
                calculate = { viewModel.calculateLoanDetails(it) }
            )
        }

        if (state.showPaymentModal && state.selectedLoan != null) {
            PaymentModal(
                form = state.paymentForm, loan = state.selectedLoan!!, accounts = state.accounts, isSaving = state.isSaving,
                onUpdateForm = { newForm -> viewModel.updatePaymentForm { newForm } },
                onDismiss = { viewModel.closePaymentModal() },
                onSave = { viewModel.submitPayment() }
            )
        }

        if (state.showDeleteConfirm && state.selectedLoan != null) {
            AlertDialog(
                onDismissRequest = { viewModel.closeDeleteConfirm() },
                icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626)) },
                title = { Text("Delete Loan Record?") },
                text = { Text("This will permanently delete this loan record and all payment history.") },
                confirmButton = { Button(onClick = { viewModel.deleteLoan() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete") } },
                dismissButton = { OutlinedButton(onClick = { viewModel.closeDeleteConfirm() }) { Text("Cancel") } }
            )
        }
    }
}

// ─── Private Professional Dropdown Helpers ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDropdownPair(
    label: String, selectedValue: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.find { it.first == selectedValue }?.second ?: "Select"
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = display, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(10.dp), enabled = enabled
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White)) {
            options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(icon, null, tint = valueColor, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LoanCard(
    loan: Loan, fmt: (Double) -> String,
    onPayment: () -> Unit, onView: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val isQard = loan.loanType == "qard-hasan"
    val accent = if (loan.status == "paid-off") Color(0xFF22C55E) else Color(0xFFF59E0B)

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(16.dp)) {
                // Top Row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Row(Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(44.dp).background(if(isQard) Color(0xFFDCFCE7) else Color(0xFFFEF3C7), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(if (isQard) Icons.Default.Money else Icons.Default.AccountBalance, null, tint = if(isQard) Color(0xFF16A34A) else Color(0xFFD97706), modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                                Text(loan.lenderName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (loan.status == "paid-off") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF15803D), modifier = Modifier.size(10.dp))
                                    Text(" Paid Off", fontSize = 10.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Medium)
                                }
                            } else {
                                Text(if(isQard) "Qard Hasan" else "${loan.interestRate}% Interest", fontSize = 11.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (loan.status == "active") {
                            IconButton(onClick = onPayment, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Payment, "Payment", tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp)) }
                        }
                        IconButton(onClick = onView, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Visibility, "View", tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp)) }
                    }
                }

                // Progress
                Column(Modifier.padding(vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("৳${CurrencyFormatter.formatBDT(loan.totalPaid)} / ৳${CurrencyFormatter.formatBDT(loan.principalAmount)}", fontSize = 11.sp, color = Color(0xFF4B5563), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${loan.progress}% Repaid", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                    }
                    LinearProgressIndicator(progress = { (loan.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)), color = accent, trackColor = Color(0xFFE5E7EB))
                }

                // Details Grid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("Principal", fontSize = 10.sp, color = Color(0xFF6B7280)); Text(fmt(loan.principalAmount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Column(Modifier.weight(1f)) { Text("Remaining", fontSize = 10.sp, color = Color(0xFF6B7280)); Text(fmt(loan.remainingBalance), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Column(Modifier.weight(1f)) { Text("Next Due", fontSize = 10.sp, color = Color(0xFF6B7280)); Text(loan.nextPaymentDue ?: loan.endDate ?: "-", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditLoanModal(
    form: LoanForm, accounts: List<LoanAccount>, isSaving: Boolean,
    onUpdateForm: (LoanForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit,
    calculate: (LoanForm) -> LoanCalculations
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().imePadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (form.id != null) "Edit Loan" else "New Loan Record", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
            }
            HorizontalDivider()

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                OutlinedTextField(value = form.lenderName, onValueChange = { onUpdateForm(form.copy(lenderName = it)) }, label = { Text("Lender Name *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppDropdownPair(
                        label = "Loan Type *", selectedValue = form.loanType,
                        options = listOf(Pair("qard-hasan", "Qard Hasan"), Pair("interest", "Interest-Based")),
                        onSelect = { onUpdateForm(form.copy(loanType = it)) }, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(value = form.principalAmount, onValueChange = { onUpdateForm(form.copy(principalAmount = it)) }, label = { Text("Principal Amount *") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }

                if (form.loanType == "interest") {
                    OutlinedTextField(value = form.interestRate, onValueChange = { onUpdateForm(form.copy(interestRate = it)) }, label = { Text("Annual Interest Rate (%) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = form.monthlyPayment, onValueChange = { onUpdateForm(form.copy(monthlyPayment = it)) }, label = { Text("Monthly Payment") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = form.totalMonths, onValueChange = { onUpdateForm(form.copy(totalMonths = it)) }, label = { Text("Duration (Months)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }

                AppDropdownPair(
                    label = "Deposit to Account", selectedValue = form.accountId,
                    options = accounts.map { Pair(it.id, "${it.name} (৳${it.balance})") },
                    onSelect = { onUpdateForm(form.copy(accountId = it)) }, enabled = form.id == null
                )

                DateSelectionField(label = "Start Date *", dateString = form.startDate, onDateSelected = { onUpdateForm(form.copy(startDate = it)) })

                val calc = calculate(form)
                if (calc.totalMonths > 0) {
                    Surface(color = Color(0xFFF9FAFB), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Loan Summary", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Monthly Payment", fontSize = 12.sp, color = Color(0xFF6B7280)); Text("৳${String.format(Locale.US, "%.2f", calc.monthlyPayment)}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827)) }
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total Duration", fontSize = 12.sp, color = Color(0xFF6B7280)); Text("${calc.totalMonths} months", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827)) }
                            if (form.loanType == "interest") {
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total Interest", fontSize = 12.sp, color = Color(0xFF6B7280)); Text("৳${String.format(Locale.US, "%.2f", calc.totalInterest)}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFD97706)) }
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total Repayment", fontSize = 12.sp, color = Color(0xFF6B7280)); Text("৳${String.format(Locale.US, "%.2f", calc.totalRepayment)}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827)) }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = form.enableReminders, onCheckedChange = { onUpdateForm(form.copy(enableReminders = it)) })
                    Text("Enable Reminders", fontSize = 13.sp, color = Color(0xFF374151))
                }

                OutlinedTextField(value = form.notes, onValueChange = { onUpdateForm(form.copy(notes = it)) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), maxLines = 2)
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))) {
                    if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Saving...") }
                    else Text(if(form.id != null) "Update Loan" else "Add Loan")
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
        Column(Modifier.fillMaxWidth().imePadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Record Payment", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
            }
            HorizontalDivider()

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = Color(0xFFF9FAFB), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(loan.lenderName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text("Remaining: ৳${CurrencyFormatter.formatBDT(loan.remainingBalance)}", fontSize = 11.sp, color = Color(0xFF6B7280))
                    }
                }

                OutlinedTextField(
                    value = form.amount, onValueChange = { onUpdateForm(form.copy(amount = it)) },
                    label = { Text("Payment Amount (৳) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                )

                DateSelectionField(label = "Payment Date *", dateString = form.paymentDate, onDateSelected = { onUpdateForm(form.copy(paymentDate = it)) })

                AppDropdownPair(
                    label = "Pay from Account", selectedValue = form.accountId,
                    options = accounts.map { Pair(it.id, "${it.name} (৳${it.balance})") },
                    onSelect = { onUpdateForm(form.copy(accountId = it)) }
                )

                OutlinedTextField(value = form.notes, onValueChange = { onUpdateForm(form.copy(notes = it)) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
            }

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))) {
                    if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Processing...") }
                    else Text("Record Payment")
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
        initialSelectedDateMillis = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            sdf.parse(dateString)?.time
        } catch(e: Exception) { null }
    )

    Box(modifier = modifier) {
        OutlinedTextField(value = dateString, onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true, trailingIcon = { Icon(Icons.Default.CalendarToday, null) })
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