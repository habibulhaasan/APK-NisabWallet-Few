package com.hasan.nisabwallet.ui.screens.investments.detail

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import com.hasan.nisabwallet.ui.screens.investments.InvestmentConstants
import com.hasan.nisabwallet.ui.screens.investments.InvestmentForm
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InvestmentDetailScreen(
    viewModel: InvestmentDetailViewModel = hiltViewModel(),
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
                is InvestmentDetailEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                is InvestmentDetailEvent.NavigateBack -> onNavigateBack()
                is InvestmentDetailEvent.TriggerCsvExport -> {
                    val name = state.investment?.name?.replace(" ", "_") ?: "investment"
                    createCsvLauncher.launch("${name}_dividends.csv")
                }
            }
        }
    }

    if (state.isLoading || state.investment == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF111827))
        }
        return
    }

    val inv = state.investment!!
    val isProfit = inv.absoluteReturn >= 0
    val typeColor = try { Color(InvestmentConstants.getColor(inv.type).toColorInt()) } catch (e: Exception) { Color.Gray }

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
                        IconButton(onClick = { viewModel.openDeleteConfirm() }, modifier = Modifier.size(36.dp).background(Color(0xFFDC2626), RoundedCornerShape(8.dp))) {
                            Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Hero Profile
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.dp).background(typeColor, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(inv.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(InvestmentConstants.getLabel(inv.type), fontSize = 13.sp, color = Color(0xFF6B7280))
                    }
                    IconButton(onClick = { viewModel.openEditModal() }, modifier = Modifier.size(40.dp).background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(8.dp))) {
                        Icon(Icons.Default.Edit, null, tint = Color(0xFF374151), modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Summary 4-Grid
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailStatBox(label = "Invested", value = fmt(inv.totalInvested), modifier = Modifier.weight(1f, false).fillMaxWidth(0.48f))
                    DetailStatBox(label = "Current Value", value = fmt(inv.totalCurrentValue), modifier = Modifier.weight(1f, false).fillMaxWidth(0.48f))
                    DetailStatBox(
                        label = "Returns",
                        value = fmt(inv.absoluteReturn),
                        subValue = "${if(isProfit) "+" else ""}${String.format(Locale.US, "%.2f", inv.percentageReturn)}%",
                        isPositive = isProfit,
                        modifier = Modifier.weight(1f, false).fillMaxWidth(0.48f)
                    )
                    DetailStatBox(label = "Dividends/Interest", value = fmt(inv.totalDividends), valueColor = Color(0xFF2563EB), modifier = Modifier.weight(1f, false).fillMaxWidth(0.48f))
                }
            }

            // Purchase Information
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, null, tint = Color(0xFF111827), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Purchase Information", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827))
                        }
                        Spacer(Modifier.height(16.dp))

                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            InfoField("Purchase Date", formatDisplayDate(inv.purchaseDate), Modifier.weight(1f, false).fillMaxWidth(0.45f))
                            InfoField("Purchase Price", fmt(inv.purchasePrice), Modifier.weight(1f, false).fillMaxWidth(0.45f))
                            InfoField("Quantity", "${inv.quantity}", Modifier.weight(1f, false).fillMaxWidth(0.45f))
                            InfoField("Current Price", fmt(inv.currentValue), Modifier.weight(1f, false).fillMaxWidth(0.45f))

                            if (inv.institution.isNotBlank()) InfoField("Institution", inv.institution, Modifier.weight(1f, false).fillMaxWidth(0.45f))
                            if (inv.interestRate != null) InfoField("Interest Rate", "${inv.interestRate}%/yr", Modifier.weight(1f, false).fillMaxWidth(0.45f))
                            if (inv.maturityDate.isNotBlank()) InfoField("Maturity Date", formatDisplayDate(inv.maturityDate), Modifier.weight(1f, false).fillMaxWidth(0.45f))
                            if (inv.maturityAmount != null) InfoField("Maturity Amount", fmt(inv.maturityAmount), Modifier.weight(1f, false).fillMaxWidth(0.45f))

                            InfoField("Category", inv.category.replaceFirstChar { it.uppercase() }, Modifier.weight(1f, false).fillMaxWidth(0.45f))
                            InfoField("Risk Level", inv.riskLevel.replaceFirstChar { it.uppercase() }, Modifier.weight(1f, false).fillMaxWidth(0.45f))
                        }

                        if (inv.notes.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color(0xFFE5E7EB))
                            Text("Notes", fontSize = 11.sp, color = Color(0xFF6B7280))
                            Spacer(Modifier.height(4.dp))
                            Text(inv.notes, fontSize = 13.sp, color = Color(0xFF374151))
                        }
                    }
                }
            }

            // Dividends/Interest
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachMoney, null, tint = Color(0xFF111827), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Dividends & Interest", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827))
                            }
                            Button(
                                onClick = { viewModel.openDividendModal() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add", fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (inv.dividends.isEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AttachMoney, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No dividend or interest payments recorded", fontSize = 13.sp, color = Color(0xFF6B7280))
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                inv.dividends.forEach { div ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(div.type.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                            Text(formatDisplayDate(div.date), fontSize = 11.sp, color = Color(0xFF6B7280))
                                            if (div.notes.isNotBlank()) Text(div.notes, fontSize = 11.sp, color = Color(0xFF4B5563), modifier = Modifier.padding(top = 2.dp))
                                        }
                                        Text(fmt(div.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Performance Summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BarChart, null, tint = Color(0xFF111827), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Performance Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827))
                        }
                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp)).padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (isProfit) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null, tint = if (isProfit) Color(0xFF16A34A) else Color(0xFFDC2626), modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Capital Gains/Loss", fontSize = 12.sp, color = Color(0xFF6B7280))
                                    Text(fmt(inv.absoluteReturn), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isProfit) Color(0xFF16A34A) else Color(0xFFDC2626), fontFamily = FontFamily.Monospace)
                                }
                            }
                            Text("${if(isProfit) "+" else ""}${String.format(Locale.US, "%.2f", inv.percentageReturn)}%", fontSize = 20.sp, fontWeight = FontWeight.Black, color = if (isProfit) Color(0xFF16A34A) else Color(0xFFDC2626))
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val netTotal = inv.absoluteReturn + inv.totalDividends
                            val netPct = if (inv.totalInvested > 0) (netTotal / inv.totalInvested) * 100 else 0.0
                            Column(modifier = Modifier.weight(1f).background(Color(0xFFFAF5FF), RoundedCornerShape(12.dp)).padding(16.dp)) {
                                Text("Net Total Returns", fontSize = 12.sp, color = Color(0xFF7E22CE))
                                Spacer(Modifier.height(4.dp))
                                Text(fmt(netTotal), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9333EA), fontFamily = FontFamily.Monospace)
                                Text("${String.format(Locale.US, "%.2f", netPct)}% return", fontSize = 10.sp, color = Color(0xFF9333EA), modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (state.showDividendModal) {
        AddDividendModal(
            form = state.dividendForm, accounts = state.accounts, isSaving = state.isSaving,
            onUpdate = { viewModel.updateDividendForm(it) }, onDismiss = { viewModel.closeDividendModal() }, onSave = { viewModel.addDividend() }
        )
    }

    if (state.showEditModal) {
        AddEditInvestmentModal(
            form = state.editForm, isSaving = state.isSaving,
            onUpdateForm = { viewModel.updateEditForm(it) }, onDismiss = { viewModel.closeEditModal() }, onSave = { viewModel.updateInvestment() }
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteConfirm() },
            icon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Investment?") },
            text = { Text("Are you sure you want to delete \"${inv.name}\"? This action cannot be undone.") },
            confirmButton = { Button(onClick = { viewModel.deleteInvestment() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete") } },
            dismissButton = { OutlinedButton(onClick = { viewModel.closeDeleteConfirm() }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DetailStatBox(label: String, value: String, subValue: String? = null, isPositive: Boolean = true, valueColor: Color = Color(0xFF111827), modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(Color(0xFFF9FAFB), RoundedCornerShape(10.dp)).padding(12.dp)) {
        Text(label, fontSize = 11.sp, color = Color(0xFF6B7280))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor, fontFamily = FontFamily.Monospace)
        if (subValue != null) {
            Text(subValue, fontSize = 11.sp, color = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun InfoField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Color(0xFF6B7280))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), modifier = Modifier.padding(top = 2.dp))
    }
}

private fun formatDisplayDate(dateStr: String): String {
    if (dateStr.isBlank()) return "-"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val out = SimpleDateFormat("MMM d, yyyy", Locale.US)
        out.format(sdf.parse(dateStr)!!)
    } catch (e: Exception) {
        dateStr
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDividendModal(
    form: DividendForm, accounts: List<InvestmentAccount>, isSaving: Boolean,
    onUpdate: ((DividendForm) -> DividendForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Add Dividend/Interest", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }

            DateSelectionField(label = "Payment Date *", dateString = form.date, onDateSelected = { onUpdate { f -> f.copy(date = it) } })

            OutlinedTextField(
                value = form.amount, onValueChange = { onUpdate { f -> f.copy(amount = it) } },
                label = { Text("Amount *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
            )

            var typeExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedTextField(
                    value = form.type.replaceFirstChar { it.uppercase() }, onValueChange = {}, readOnly = true,
                    label = { Text("Type") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                )
                Box(modifier = Modifier.matchParentSize().clickable { typeExpanded = true })
                DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    listOf("dividend", "interest", "bonus").forEach { t ->
                        DropdownMenuItem(text = { Text(t.replaceFirstChar { it.uppercase() }) }, onClick = { onUpdate { f -> f.copy(type = t) }; typeExpanded = false })
                    }
                }
            }

            var accExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedTextField(
                    value = accounts.find { it.id == form.accountId }?.name ?: "Select account",
                    onValueChange = {}, readOnly = true, label = { Text("Deposit to Account *") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                )
                Box(Modifier.matchParentSize().clickable { accExpanded = true })
                DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                    accounts.forEach { a -> DropdownMenuItem(text = { Text("${a.name} (৳${a.balance})") }, onClick = { onUpdate { f -> f.copy(accountId = a.id) }; accExpanded = false }) }
                }
            }

            OutlinedTextField(
                value = form.notes, onValueChange = { onUpdate { f -> f.copy(notes = it) } },
                label = { Text("Notes") }, placeholder = { Text("Optional notes...") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 2
            )

            Button(
                onClick = onSave, enabled = !isSaving, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...", fontSize = 15.sp)
                } else {
                    Text("Add Payment", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── Reused Edit Modal Logic & Date Picker ───
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditInvestmentModal(
    form: InvestmentForm, isSaving: Boolean,
    onUpdateForm: ((InvestmentForm) -> InvestmentForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Edit Investment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider()

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = InvestmentConstants.getLabel(form.type), onValueChange = {}, readOnly = true,
                    label = { Text("Investment Type") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    enabled = false
                )

                OutlinedTextField(value = form.name, onValueChange = { n -> onUpdateForm { it.copy(name = n) } }, label = { Text("Investment Name *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)

                when (form.type) {
                    InvestmentConstants.STOCK -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(value = form.symbol, onValueChange = { v -> onUpdateForm { it.copy(symbol = v) } }, label = { Text("Symbol") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true)
                            OutlinedTextField(value = form.exchange, onValueChange = { v -> onUpdateForm { it.copy(exchange = v) } }, label = { Text("Exchange") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true)
                        }
                    }
                    InvestmentConstants.FDR -> {
                        OutlinedTextField(value = form.institution, onValueChange = { v -> onUpdateForm { it.copy(institution = v) } }, label = { Text("Bank/Institution") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(value = form.interestRate, onValueChange = { v -> onUpdateForm { it.copy(interestRate = v) } }, label = { Text("Interest Rate %") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            OutlinedTextField(value = form.maturityAmount, onValueChange = { v -> onUpdateForm { it.copy(maturityAmount = v) } }, label = { Text("Maturity Amount") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        }
                        DateSelectionField(label = "Maturity Date", dateString = form.maturityDate, onDateSelected = { d -> onUpdateForm { it.copy(maturityDate = d) } })
                    }
                    InvestmentConstants.DPS -> {
                        OutlinedTextField(value = form.institution, onValueChange = { v -> onUpdateForm { it.copy(institution = v) } }, label = { Text("Bank/Institution") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(value = form.monthlyAmount, onValueChange = { v -> onUpdateForm { it.copy(monthlyAmount = v) } }, label = { Text("Monthly Amt") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            OutlinedTextField(value = form.period, onValueChange = { v -> onUpdateForm { it.copy(period = v) } }, label = { Text("Period (months)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DateSelectionField(label = "Maturity Date", dateString = form.maturityDate, onDateSelected = { d -> onUpdateForm { it.copy(maturityDate = d) } }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = form.maturityAmount, onValueChange = { v -> onUpdateForm { it.copy(maturityAmount = v) } }, label = { Text("Maturity Amount") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        }
                    }
                    InvestmentConstants.SAVINGS_CERTIFICATE -> {
                        OutlinedTextField(value = form.certificateNumber, onValueChange = { v -> onUpdateForm { it.copy(certificateNumber = v) } }, label = { Text("Certificate Num") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DateSelectionField(label = "Issue Date", dateString = form.issueDate, onDateSelected = { d -> onUpdateForm { it.copy(issueDate = d) } }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = form.interestRate, onValueChange = { v -> onUpdateForm { it.copy(interestRate = v) } }, label = { Text("Interest Rate %") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DateSelectionField(label = "Maturity Date", dateString = form.maturityDate, onDateSelected = { d -> onUpdateForm { it.copy(maturityDate = d) } }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = form.maturityAmount, onValueChange = { v -> onUpdateForm { it.copy(maturityAmount = v) } }, label = { Text("Maturity Amount") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        }
                    }
                    InvestmentConstants.REAL_ESTATE -> {
                        OutlinedTextField(value = form.address, onValueChange = { v -> onUpdateForm { it.copy(address = v) } }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
                        var propExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedTextField(
                                value = form.propertyType.replaceFirstChar { it.uppercase() }, onValueChange = {}, readOnly = true,
                                label = { Text("Property Type") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { propExpanded = true })
                            DropdownMenu(expanded = propExpanded, onDismissRequest = { propExpanded = false }) {
                                listOf("residential", "commercial", "land", "apartment").forEach { t ->
                                    DropdownMenuItem(text = { Text(t.replaceFirstChar { it.uppercase() }) }, onClick = { onUpdateForm { it.copy(propertyType = t) }; propExpanded = false })
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateSelectionField(label = "Purchase Date *", dateString = form.purchaseDate, onDateSelected = { d -> onUpdateForm { it.copy(purchaseDate = d) } }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = form.purchasePrice, onValueChange = { v -> onUpdateForm { it.copy(purchasePrice = v) } }, label = { Text("Price/Unit *") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = form.quantity, onValueChange = { v -> onUpdateForm { it.copy(quantity = v) } }, label = { Text("Quantity/Units *") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = form.currentValue, onValueChange = { v -> onUpdateForm { it.copy(currentValue = v) } }, label = { Text("Current Value/Unit") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    var statusExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = form.status.replaceFirstChar { it.uppercase() }, onValueChange = {}, readOnly = true, label = { Text("Status") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                        Box(modifier = Modifier.matchParentSize().clickable { statusExpanded = true })
                        DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                            listOf("active", "matured", "sold", "closed").forEach { t -> DropdownMenuItem(text = { Text(t.replaceFirstChar { it.uppercase() }) }, onClick = { onUpdateForm { it.copy(status = t) }; statusExpanded = false }) }
                        }
                    }

                    var catExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(value = form.category.replaceFirstChar { it.uppercase() }, onValueChange = {}, readOnly = true, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                        Box(modifier = Modifier.matchParentSize().clickable { catExpanded = true })
                        DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                            listOf("growth", "income", "safe", "speculative").forEach { t -> DropdownMenuItem(text = { Text(t.replaceFirstChar { it.uppercase() }) }, onClick = { onUpdateForm { it.copy(category = t) }; catExpanded = false }) }
                        }
                    }
                }

                var riskExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(value = form.riskLevel.replaceFirstChar { it.uppercase() }, onValueChange = {}, readOnly = true, label = { Text("Risk Level") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                    Box(modifier = Modifier.matchParentSize().clickable { riskExpanded = true })
                    DropdownMenu(expanded = riskExpanded, onDismissRequest = { riskExpanded = false }) {
                        listOf("low", "medium", "high").forEach { t -> DropdownMenuItem(text = { Text(t.replaceFirstChar { it.uppercase() }) }, onClick = { onUpdateForm { it.copy(riskLevel = t) }; riskExpanded = false }) }
                    }
                }

                OutlinedTextField(value = form.notes, onValueChange = { n -> onUpdateForm { it.copy(notes = n) } }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), maxLines = 3)
            }

            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(
                    onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving...", fontSize = 14.sp)
                    } else {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Update", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionField(
    label: String,
    dateString: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            sdf.parse(dateString)?.time
        } catch (e: Exception) { null }
    )

    Box(modifier = modifier) {
        OutlinedTextField(
            value = dateString, onValueChange = {}, readOnly = true, label = { Text(label) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true,
            trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) }
        )
        Box(modifier = Modifier.matchParentSize().clickable { showPicker = true })
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        onDateSelected(sdf.format(Date(millis)))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}