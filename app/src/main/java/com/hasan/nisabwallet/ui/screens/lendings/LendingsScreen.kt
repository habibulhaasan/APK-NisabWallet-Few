package com.hasan.nisabwallet.ui.screens.lendings

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
fun LendingsScreen(
    viewModel: LendingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LendingsEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
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
                                Text("Lending Management", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                            Text("Track money lent to others (Qard Hasan & Conventional)", fontSize = 12.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(start = 32.dp))
                        }
                        Button(
                            onClick = { viewModel.openLendingModal() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("New", fontSize = 13.sp)
                        }
                    }
                }

                // Summary Cards
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryCard("Total Lent", fmt(state.totalLent), Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF2563EB), Modifier.weight(1f, false).fillMaxWidth(0.3f))
                        SummaryCard("Repaid", fmt(state.totalRepaid), Icons.Default.CheckCircle, Color(0xFF16A34A), Modifier.weight(1f, false).fillMaxWidth(0.3f))
                        SummaryCard("Outstanding", fmt(state.totalOutstanding), Icons.Default.AttachMoney, Color(0xFFDC2626), Modifier.weight(1f, false).fillMaxWidth(0.3f))
                        SummaryCard("Active", state.activeCount.toString(), Icons.Default.AccessTime, Color(0xFFEA580C), Modifier.weight(1f, false).fillMaxWidth(0.3f))
                        SummaryCard("Completed", state.completedCount.toString(), Icons.Default.CheckCircle, Color(0xFF059669), Modifier.weight(1f, false).fillMaxWidth(0.3f))
                        SummaryCard("Overdue", state.overdueCount.toString(), Icons.Default.Warning, Color(0xFFDC2626), Modifier.weight(1f, false).fillMaxWidth(0.3f))
                    }
                }

                // Filters
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("all" to "All", "active" to "Active", "completed" to "Completed", "overdue" to "Overdue").forEach { (v, l) ->
                                val sel = state.filterStatus == v
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if(sel) Color(0xFF111827) else Color(0xFFF3F4F6)).clickable { viewModel.setFilterStatus(v) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(l, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if(sel) Color.White else Color(0xFF374151)) }
                            }
                        }
                    }
                }

                // List
                if (state.isLoading) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF111827)) } }
                } else if (state.filteredLendings.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(48.dp), tint = Color(0xFFD1D5DB))
                            Spacer(Modifier.height(16.dp))
                            Text("No lending records", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                            Text("Create a record when you lend money", fontSize = 13.sp, color = Color(0xFF6B7280))
                            Button(onClick = { viewModel.openLendingModal() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)), modifier = Modifier.padding(top = 16.dp), shape = RoundedCornerShape(10.dp)) { Text("Create Lending Record") }
                        }
                    }
                } else {
                    items(state.filteredLendings, key = { it.id }) { lending ->
                        val statusInfo = viewModel.calculateLendingStatus(lending)
                        LendingCard(
                            lending = lending, statusInfo = statusInfo, fmt = fmt,
                            onPayment = { viewModel.openPaymentModal(lending) },
                            onReminder = { viewModel.openReminderModal(lending) },
                            onView = { onNavigateToDetail(lending.id) },
                            onEdit = { viewModel.openLendingModal(lending) },
                            onDelete = { viewModel.openDeleteModal(lending) }
                        )
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    if (state.showLendingModal) {
        AddEditLendingModal(
            form = state.lendingForm, accounts = state.accounts, isSaving = state.isSaving,
            onUpdateForm = { newForm -> viewModel.updateLendingForm { newForm } },
            onDismiss = { viewModel.closeLendingModal() },
            onSave = { viewModel.saveLending() }
        )
    }

    if (state.showPaymentModal && state.selectedLending != null) {
        PaymentModal(
            form = state.paymentForm, lending = state.selectedLending!!, isSaving = state.isSaving,
            onUpdateForm = { newForm -> viewModel.updatePaymentForm { newForm } },
            onDismiss = { viewModel.closePaymentModal() },
            onSave = { viewModel.submitPayment() }
        )
    }

    if (state.showReminderModal && state.selectedLending != null) {
        ReminderModal(
            message = state.reminderMessage, lending = state.selectedLending!!,
            onMessageChange = { viewModel.updateReminderMessage(it) },
            onDismiss = { viewModel.closeReminderModal() },
            onSend = { viewModel.sendReminder() }
        )
    }

    if (state.showDeleteModal && state.selectedLending != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteModal() },
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Lending Record?") },
            text = { Text("This will permanently delete this lending record and all payment history.") },
            confirmButton = { Button(onClick = { viewModel.deleteLending() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete") } },
            dismissButton = { OutlinedButton(onClick = { viewModel.closeDeleteModal() }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
                Icon(icon, null, tint = valueColor, modifier = Modifier.size(12.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LendingCard(
    lending: Lending, statusInfo: LendingStatusInfo, fmt: (Double) -> String,
    onPayment: () -> Unit, onReminder: () -> Unit, onView: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val isQard = lending.lendingType == "qard-hasan"
    val accent = if (statusInfo.isOverdue) Color(0xFFEF4444) else if (lending.status == "completed") Color(0xFF22C55E) else Color(0xFF3B82F6)

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (statusInfo.isOverdue) Color(0xFFFEF2F2) else Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(16.dp)) {
                // Top Row: Icon + Name + Actions
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Row(Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(44.dp).background(if(isQard) Color(0xFF059669) else Color(0xFF2563EB), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(if (isQard) Icons.Default.Money else Icons.Default.AccountBalance, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                                Text(lending.borrowerName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(50), color = if(isQard) Color(0xFFD1FAE5) else Color(0xFFDBEAFE)) {
                                    Text(if(isQard) "Qard Hasan" else "Conventional", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = if(isQard) Color(0xFF047857) else Color(0xFF1D4ED8), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            if (lending.status == "completed") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF15803D), modifier = Modifier.size(10.dp))
                                    Text(" Paid Off", fontSize = 10.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Medium)
                                }
                            } else if (statusInfo.isOverdue) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, tint = Color(0xFFB91C1C), modifier = Modifier.size(10.dp))
                                    Text(" ${statusInfo.daysOverdue}d Overdue", fontSize = 10.sp, color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                            if (lending.borrowerContact.phone.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                    Icon(Icons.Default.Phone, null, tint = Color(0xFF4B5563), modifier = Modifier.size(10.dp))
                                    Text(" ${lending.borrowerContact.phone}", fontSize = 11.sp, color = Color(0xFF4B5563))
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (lending.status == "active") {
                            IconButton(onClick = onPayment, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Payment, "Payment", tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = onReminder, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.Send, "Reminder", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp)) }
                        }
                        IconButton(onClick = onView, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Visibility, "View", tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp)) }
                    }
                }

                // Progress
                Column(Modifier.padding(vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("৳${CurrencyFormatter.formatBDT(lending.totalRepaid)} / ৳${CurrencyFormatter.formatBDT(lending.principalAmount)}", fontSize = 11.sp, color = Color(0xFF4B5563))
                        Text("${String.format(Locale.US, "%.1f", statusInfo.percentagePaid)}%", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                    }
                    LinearProgressIndicator(progress = { (statusInfo.percentagePaid / 100f).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)), color = if(lending.status=="completed") Color(0xFF16A34A) else Color(0xFF2563EB), trackColor = Color(0xFFE5E7EB))
                }

                // Details Grid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Principal", fontSize = 10.sp, color = Color(0xFF6B7280)); Text(fmt(lending.principalAmount), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827)) }
                    Column { Text("Remaining", fontSize = 10.sp, color = Color(0xFF6B7280)); Text(fmt(statusInfo.remainingBalance), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626)) }
                    Column { Text("Payments", fontSize = 10.sp, color = Color(0xFF6B7280)); Text("${lending.paymentsReceived}${if(lending.totalInstallments!=null) " / ${lending.totalInstallments}" else ""}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827)) }
                    Column { Text("Next Due", fontSize = 10.sp, color = Color(0xFF6B7280)); Text(runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(lending.nextPaymentDue ?: lending.dueDate)!!) }.getOrDefault(lending.dueDate), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827)) }
                }

                // Witnesses
                if (lending.witnesses.isNotEmpty()) {
                    HorizontalDivider(color = Color(0xFFE5E7EB), modifier = Modifier.padding(vertical = 12.dp))
                    Text("Witnesses:", fontSize = 10.sp, color = Color(0xFF6B7280))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        lending.witnesses.forEach { w ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.People, null, tint = Color(0xFF6B7280), modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(w.name + if(w.contact.isNotBlank()) " (${w.contact})" else "", fontSize = 11.sp, color = Color(0xFF374151))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditLendingModal(
    form: LendingForm, accounts: List<LendingAccount>, isSaving: Boolean,
    onUpdateForm: (LendingForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (form.id != null) "Edit Lending Record" else "New Lending Record", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
            }
            HorizontalDivider()

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

                // Borrower Info
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = Color(0xFF111827), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Borrower Information", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    }
                    OutlinedTextField(value = form.borrowerName, onValueChange = { onUpdateForm(form.copy(borrowerName = it)) }, label = { Text("Full Name *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = form.borrowerPhone, onValueChange = { onUpdateForm(form.copy(borrowerPhone = it)) }, label = { Text("Phone") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                        OutlinedTextField(value = form.borrowerEmail, onValueChange = { onUpdateForm(form.copy(borrowerEmail = it)) }, label = { Text("Email") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    }
                    OutlinedTextField(value = form.borrowerAddress, onValueChange = { onUpdateForm(form.copy(borrowerAddress = it)) }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                }

                // Lending Details
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachMoney, null, tint = Color(0xFF111827), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lending Details", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        var typeExpanded by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1f)) {
                            OutlinedTextField(value = if(form.lendingType=="qard-hasan") "Qard Hasan" else "Conventional", onValueChange = {}, readOnly = true, label = { Text("Lending Type *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                            Box(Modifier.matchParentSize().clickable { typeExpanded = true })
                            DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                                DropdownMenuItem(text = { Text("Qard Hasan") }, onClick = { onUpdateForm(form.copy(lendingType = "qard-hasan")); typeExpanded = false })
                                DropdownMenuItem(text = { Text("Conventional") }, onClick = { onUpdateForm(form.copy(lendingType = "conventional")); typeExpanded = false })
                            }
                        }
                        var catExpanded by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1f)) {
                            OutlinedTextField(value = form.category.replaceFirstChar { it.uppercase() }, onValueChange = {}, readOnly = true, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                            Box(Modifier.matchParentSize().clickable { catExpanded = true })
                            DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                                listOf("personal", "business", "emergency", "other").forEach { t -> DropdownMenuItem(text = { Text(t.replaceFirstChar { it.uppercase() }) }, onClick = { onUpdateForm(form.copy(category = t)); catExpanded = false }) }
                            }
                        }
                    }

                    OutlinedTextField(value = form.principalAmount, onValueChange = { onUpdateForm(form.copy(principalAmount = it)) }, label = { Text("Principal Amount (৳) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)

                    var accExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedTextField(value = accounts.find { it.id == form.accountId }?.name ?: "Select account", onValueChange = {}, readOnly = true, label = { Text("From Account") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                        Box(Modifier.matchParentSize().clickable { accExpanded = true })
                        DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                            accounts.forEach { a -> DropdownMenuItem(text = { Text("${a.name} (৳${a.balance})") }, onClick = { onUpdateForm(form.copy(accountId = a.id)); accExpanded = false }) }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DateSelectionField(label = "Lending Date *", dateString = form.lendingDate, onDateSelected = { onUpdateForm(form.copy(lendingDate = it)) }, modifier = Modifier.weight(1f))
                        DateSelectionField(label = "Due Date *", dateString = form.dueDate, onDateSelected = { onUpdateForm(form.copy(dueDate = it)) }, modifier = Modifier.weight(1f))
                    }
                }

                // Repayment Settings
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, tint = Color(0xFF111827), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Repayment Settings", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isFull = form.repaymentType == "full-payment"
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(2.dp, if(isFull) Color(0xFF2563EB) else Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).background(if(isFull) Color(0xFFEFF6FF) else Color.White).clickable { onUpdateForm(form.copy(repaymentType = "full-payment")) }.padding(12.dp), contentAlignment = Alignment.Center) {
                            Text("Full Payment", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if(isFull) Color(0xFF1D4ED8) else Color(0xFF6B7280))
                        }
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(2.dp, if(!isFull) Color(0xFF2563EB) else Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).background(if(!isFull) Color(0xFFEFF6FF) else Color.White).clickable { onUpdateForm(form.copy(repaymentType = "installments")) }.padding(12.dp), contentAlignment = Alignment.Center) {
                            Text("Installments", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if(!isFull) Color(0xFF1D4ED8) else Color(0xFF6B7280))
                        }
                    }

                    if (form.repaymentType == "installments") {
                        OutlinedTextField(value = form.installmentAmount, onValueChange = { onUpdateForm(form.copy(installmentAmount = it)) }, label = { Text("Installment Amount (৳)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            var freqExpanded by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f)) {
                                OutlinedTextField(value = form.installmentFrequency.replaceFirstChar { it.uppercase() }, onValueChange = {}, readOnly = true, label = { Text("Frequency") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                                Box(Modifier.matchParentSize().clickable { freqExpanded = true })
                                DropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                                    listOf("weekly", "monthly").forEach { t -> DropdownMenuItem(text = { Text(t.replaceFirstChar { it.uppercase() }) }, onClick = { onUpdateForm(form.copy(installmentFrequency = t)); freqExpanded = false }) }
                                }
                            }
                            OutlinedTextField(value = form.totalInstallments, onValueChange = { onUpdateForm(form.copy(totalInstallments = it)) }, label = { Text("Total Installments") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        }
                    }
                }

                // Witnesses
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.People, null, tint = Color(0xFF111827), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Witnesses (Islamic Requirement)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = form.witness1Name, onValueChange = { onUpdateForm(form.copy(witness1Name = it)) }, label = { Text("W1 Name") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true)
                        OutlinedTextField(value = form.witness1Contact, onValueChange = { onUpdateForm(form.copy(witness1Contact = it)) }, label = { Text("W1 Contact") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = form.witness2Name, onValueChange = { onUpdateForm(form.copy(witness2Name = it)) }, label = { Text("W2 Name") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true)
                        OutlinedTextField(value = form.witness2Contact, onValueChange = { onUpdateForm(form.copy(witness2Contact = it)) }, label = { Text("W2 Contact") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true)
                    }
                }

                // Reminders
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null, tint = Color(0xFF111827), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reminders", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = form.enableReminders, onCheckedChange = { onUpdateForm(form.copy(enableReminders = it)) })
                        Text("Enable payment reminders", fontSize = 13.sp, color = Color(0xFF374151))
                    }
                    if (form.enableReminders) {
                        OutlinedTextField(value = form.reminderDaysBefore, onValueChange = { onUpdateForm(form.copy(reminderDaysBefore = it)) }, label = { Text("Remind before (days)") }, modifier = Modifier.fillMaxWidth(0.5f), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                }

                OutlinedTextField(value = form.notes, onValueChange = { onUpdateForm(form.copy(notes = it)) }, label = { Text("Additional Notes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), maxLines = 2)
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp)) { Text("Cancel") }
                Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))) {
                    if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Saving...") }
                    else Text(if(form.id != null) "Update Record" else "Create Record")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentModal(
    form: LendingPaymentForm, lending: Lending, isSaving: Boolean,
    onUpdateForm: (LendingPaymentForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Record Payment", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
            }
            HorizontalDivider()

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = Color(0xFFF9FAFB), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(lending.borrowerName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text("Remaining: ৳${CurrencyFormatter.formatBDT(lending.remainingBalance)}", fontSize = 11.sp, color = Color(0xFF6B7280))
                    }
                }

                OutlinedTextField(
                    value = form.amount, onValueChange = { onUpdateForm(form.copy(amount = it)) },
                    label = { Text("Payment Amount (৳) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                )

                DateSelectionField(label = "Payment Date *", dateString = form.paymentDate, onDateSelected = { onUpdateForm(form.copy(paymentDate = it)) })

                var methodExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(value = form.paymentMethod.replaceFirstChar { it.uppercase() }.replace("-", " "), onValueChange = {}, readOnly = true, label = { Text("Payment Method *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                    Box(Modifier.matchParentSize().clickable { methodExpanded = true })
                    DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                        listOf("cash", "bank-transfer", "mobile-wallet", "cheque").forEach { t -> DropdownMenuItem(text = { Text(t.replaceFirstChar { it.uppercase() }.replace("-", " ")) }, onClick = { onUpdateForm(form.copy(paymentMethod = t)); methodExpanded = false }) }
                    }
                }

                OutlinedTextField(value = form.notes, onValueChange = { onUpdateForm(form.copy(notes = it)) }, label = { Text("Notes") }, placeholder = { Text("Monthly installment...") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp)) { Text("Cancel") }
                    Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))) {
                        if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Processing...") }
                        else Text("Record Payment")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderModal(
    message: String, lending: Lending,
    onMessageChange: (String) -> Unit, onDismiss: () -> Unit, onSend: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Send Payment Reminder", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
            }
            HorizontalDivider()

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("To: ${lending.borrowerName}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                        Text(lending.borrowerContact.phone, fontSize = 11.sp, color = Color(0xFF1D4ED8))
                    }
                }

                OutlinedTextField(
                    value = message, onValueChange = onMessageChange,
                    label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), maxLines = 4
                )

                Surface(color = Color(0xFFFFFBEB), border = BorderStroke(1.dp, Color(0xFFFDE68A)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Text("Note: This will log a reminder record in the system. External API integration is required for actual SMS/WhatsApp dispatch.", fontSize = 11.sp, color = Color(0xFF92400E))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp)) { Text("Cancel") }
                    Button(onClick = onSend, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Log Reminder")
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