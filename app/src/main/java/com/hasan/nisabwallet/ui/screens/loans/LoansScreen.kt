package com.hasan.nisabwallet.ui.screens.loans

import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
                // Header
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

                // Summary Cards
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

                // Filter Panel with Search
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = state.searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search loans...", fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CompactFilterDropdown("Status", state.filterStatus, listOf("all" to "All Status", "active" to "Active", "paid-off" to "Paid Off"), { viewModel.setFilterStatus(it) }, Modifier.weight(1f))
                                CompactFilterDropdown("Type", state.filterType, listOf("all" to "All Types", "qard-hasan" to "Qard Hasan", "interest" to "Interest-Based"), { viewModel.setFilterType(it) }, Modifier.weight(1f))
                            }
                        }
                    }
                }

                // List Header
                item {
                    Text("Loan Records (${state.filteredLoans.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 8.dp))
                }

                if (state.isLoading) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF111827)) } }
                } else if (state.filteredLoans.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AccountBalance, null, modifier = Modifier.size(48.dp), tint = Color(0xFFD1D5DB))
                            Spacer(Modifier.height(16.dp))
                            Text("No matching loans found", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
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

// ─── Compact Filters ──────────────────────────────────────────────────────────

@Composable
private fun CompactFilterDropdown(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.find { it.first == value }?.second ?: label
    Box(modifier) {
        Surface(modifier = Modifier.fillMaxWidth().clickable { expanded = true }, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFD1D5DB)), color = Color.White) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 9.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(display, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White)) {
            options.forEach { (k, v) -> DropdownMenuItem(text = { Text(v, fontSize = 13.sp) }, onClick = { onSelect(k); expanded = false }) }
        }
    }
}

// ─── List Cards ───────────────────────────────────────────────────────────────

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

// ─── Modern Drill-Down Modals ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditLoanModal(
    form: LoanForm, accounts: List<LoanAccount>, isSaving: Boolean,
    onUpdateForm: (LoanForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit,
    calculate: (LoanForm) -> LoanCalculations
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf("form") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = Color.White
    ) {
        Crossfade(targetState = currentView, label = "LoanModal") { view ->
            when (view) {
                "form" -> {
                    Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (form.id != null) "Edit Loan" else "New Loan Record", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                        }
                        HorizontalDivider()

                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                            OutlinedTextField(value = form.lenderName, onValueChange = { onUpdateForm(form.copy(lenderName = it)) }, label = { Text("Lender Name *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)

                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))) {
                                DrillDownRow(
                                    label = "Loan Type *",
                                    value = if (form.loanType == "qard-hasan") "Qard Hasan" else "Interest-Based",
                                    icon = Icons.Default.Category,
                                    onClick = { currentView = "selectType" }
                                )
                                HorizontalDivider(color = Color(0xFFE5E7EB), modifier = Modifier.padding(start = 44.dp))
                                DrillDownRow(
                                    label = "Deposit to Account",
                                    value = accounts.find { it.id == form.accountId }?.name ?: "Select account",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    onClick = { if (form.id == null) currentView = "selectAccount" }
                                )
                            }

                            OutlinedTextField(value = form.principalAmount, onValueChange = { onUpdateForm(form.copy(principalAmount = it)) }, label = { Text("Principal Amount (৳) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)

                            if (form.loanType == "interest") {
                                OutlinedTextField(value = form.interestRate, onValueChange = { onUpdateForm(form.copy(interestRate = it)) }, label = { Text("Annual Interest Rate (%) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(value = form.monthlyPayment, onValueChange = { onUpdateForm(form.copy(monthlyPayment = it)) }, label = { Text("Monthly Payment") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                OutlinedTextField(value = form.totalMonths, onValueChange = { onUpdateForm(form.copy(totalMonths = it)) }, label = { Text("Duration (Months)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                            }

                            DateSelectionField(label = "Start Date *", dateString = form.startDate, onDateSelected = { onUpdateForm(form.copy(startDate = it)) })

                            val calc = calculate(form)
                            if (calc.totalMonths > 0) {
                                Surface(color = Color(0xFFF9FAFB), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                                    Column(Modifier.padding(16.dp)) {
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

                            OutlinedTextField(value = form.notes, onValueChange = { onUpdateForm(form.copy(notes = it)) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 2)
                        }

                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))) {
                                if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Saving...") }
                                else Text(if(form.id != null) "Update Loan" else "Add Loan", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "selectType" -> {
                    GenericSelectionList(
                        title = "Loan Type",
                        items = listOf(Pair("qard-hasan", "Qard Hasan"), Pair("interest", "Interest-Based")),
                        selectedValue = form.loanType,
                        onSelect = { onUpdateForm(form.copy(loanType = it)); currentView = "form" },
                        onBack = { currentView = "form" },
                        icon = Icons.Default.Category
                    )
                }
                "selectAccount" -> {
                    GenericSelectionList(
                        title = "Deposit to Account",
                        items = accounts.map { Pair(it.id, "${it.name} (৳${it.balance})") },
                        selectedValue = form.accountId,
                        onSelect = { onUpdateForm(form.copy(accountId = it)); currentView = "form" },
                        onBack = { currentView = "form" },
                        icon = Icons.Default.AccountBalanceWallet
                    )
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf("form") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Crossfade(targetState = currentView, label = "PaymentModal") { view ->
            when (view) {
                "form" -> {
                    Column(Modifier.fillMaxWidth().imePadding().padding(bottom = 16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Record Payment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                        }
                        HorizontalDivider()

                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(color = Color(0xFFF9FAFB), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(loan.lenderName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                    Text("Remaining: ৳${CurrencyFormatter.formatBDT(loan.remainingBalance)}", fontSize = 12.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 4.dp))
                                }
                            }

                            OutlinedTextField(
                                value = form.amount, onValueChange = { onUpdateForm(form.copy(amount = it)) },
                                label = { Text("Payment Amount (৳) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                            )

                            DateSelectionField(label = "Payment Date *", dateString = form.paymentDate, onDateSelected = { onUpdateForm(form.copy(paymentDate = it)) })

                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))) {
                                DrillDownRow(
                                    label = "Pay from Account",
                                    value = accounts.find { it.id == form.accountId }?.name ?: "Select account",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    onClick = { currentView = "selectAccount" }
                                )
                            }

                            OutlinedTextField(value = form.notes, onValueChange = { onUpdateForm(form.copy(notes = it)) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                        }

                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))) {
                                if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Processing...") }
                                else Text("Record Payment", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "selectAccount" -> {
                    GenericSelectionList(
                        title = "Select Account",
                        items = accounts.map { Pair(it.id, "${it.name} (৳${it.balance})") },
                        selectedValue = form.accountId,
                        onSelect = { onUpdateForm(form.copy(accountId = it)); currentView = "form" },
                        onBack = { currentView = "form" },
                        icon = Icons.Default.AccountBalanceWallet
                    )
                }
            }
        }
    }
}

// ─── Sub-Sheet Drill-Down Helpers ─────────────────────────────────────────────

@Composable
private fun SubSheetHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
    }
    HorizontalDivider(color = Color(0xFFE5E7EB))
}

@Composable
private fun DrillDownRow(label: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, fontSize = 11.sp, color = Color(0xFF6B7280))
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFD1D5DB))
    }
}

@Composable
private fun GenericSelectionList(
    title: String, items: List<Pair<String, String>>, selectedValue: String,
    onSelect: (String) -> Unit, onBack: () -> Unit, icon: ImageVector
) {
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding()) {
        SubSheetHeader(title = title, onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                val isSelected = selectedValue == item.first
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(item.first) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF9FAFB)),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFFE5E7EB))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(Color(0xFFDBEAFE), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = Color(0xFF1D4ED8))
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(item.second, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF3B82F6))
                        }
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
        initialSelectedDateMillis = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            sdf.parse(dateString)?.time
        } catch(e: Exception) { null }
    )

    Box(modifier = modifier) {
        OutlinedTextField(value = dateString, onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, trailingIcon = { Icon(Icons.Default.CalendarToday, null) })
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