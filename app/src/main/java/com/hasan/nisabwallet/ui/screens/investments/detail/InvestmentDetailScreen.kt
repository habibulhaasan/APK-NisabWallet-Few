package com.hasan.nisabwallet.ui.screens.investments.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentDetailScreen(
    viewModel: InvestmentDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit // Kept for signature compatibility, though edit is handled via Modal now
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
    val retColor = if (isProfit) Color(0xFF16A34A) else Color(0xFFDC2626)
    val typeColor = try { Color(InvestmentConstants.getColor(inv.type).toColorInt()) } catch (_: Exception) { Color.Gray }

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
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(4.dp).fillMaxHeight().background(typeColor))
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                            Box(modifier = Modifier.size(48.dp).background(typeColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = typeColor, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(inv.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                    Text(InvestmentConstants.getLabel(inv.type), fontSize = 13.sp, color = Color(0xFF6B7280))
                                    if (inv.symbol.isNotBlank()) {
                                        Text(" • ", fontSize = 13.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(horizontal = 4.dp))
                                        Text(inv.symbol, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4B5563), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Surface(shape = RoundedCornerShape(50), color = if(inv.status=="active") Color(0xFFD1FAE5) else Color(0xFFF3F4F6), modifier = Modifier.padding(top = 8.dp)) {
                                    Text(inv.status.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if(inv.status=="active") Color(0xFF065F46) else Color(0xFF4B5563), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                            IconButton(onClick = { viewModel.openEditModal() }, modifier = Modifier.size(36.dp).background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))) {
                                Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Summary Grid
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Performance", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 12.dp))

                        val infoBoxes = mutableListOf<@Composable () -> Unit>()
                        infoBoxes.add { InfoBox(Icons.Default.AttachMoney, "Invested", fmt(inv.totalInvested), Color(0xFF2563EB), Color(0xFFEFF6FF), Modifier.fillMaxWidth()) }
                        infoBoxes.add { InfoBox(Icons.Default.BarChart, "Current Value", fmt(inv.totalCurrentValue), Color(0xFF111827), Color(0xFFF9FAFB), Modifier.fillMaxWidth()) }
                        infoBoxes.add { InfoBox(if(isProfit) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, "Total Return", "${if(isProfit) "+" else ""}${fmt(inv.absoluteReturn)}", retColor, if(isProfit) Color(0xFFF0FDF4) else Color(0xFFFEF2F2), Modifier.fillMaxWidth()) }
                        infoBoxes.add { InfoBox(Icons.Default.Percent, "Return %", "${if(isProfit) "+" else ""}${String.format(Locale.US, "%.2f", inv.percentageReturn)}%", retColor, if(isProfit) Color(0xFFF0FDF4) else Color(0xFFFEF2F2), Modifier.fillMaxWidth()) }

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
                        Text("Investment Details", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 8.dp))
                        DetailRow("Purchase Date", formatDisplayDate(inv.purchaseDate))
                        DetailRow("Quantity / Units", String.format(Locale.US, "%.4f", inv.quantity))
                        DetailRow("Avg Price Paid", fmt(inv.purchasePrice))
                        DetailRow("Current Price/Unit", fmt(inv.currentValue))

                        if (inv.type == InvestmentConstants.STOCK && inv.exchange.isNotBlank()) DetailRow("Exchange", inv.exchange)
                        if (inv.type == InvestmentConstants.FDR || inv.type == InvestmentConstants.DPS || inv.type == InvestmentConstants.SAVINGS_CERTIFICATE) {
                            if (inv.institution.isNotBlank()) DetailRow("Institution", inv.institution)
                            if (inv.interestRate != null) DetailRow("Interest Rate", "${inv.interestRate}%")
                            if (inv.maturityDate.isNotBlank()) DetailRow("Maturity Date", formatDisplayDate(inv.maturityDate))
                            if (inv.maturityAmount != null) DetailRow("Maturity Amount", fmt(inv.maturityAmount))
                        }
                        if (inv.type == InvestmentConstants.REAL_ESTATE) {
                            if (inv.propertyType.isNotBlank()) DetailRow("Property Type", inv.propertyType.replaceFirstChar { it.uppercase() })
                            if (inv.address.isNotBlank()) DetailRow("Address", inv.address)
                        }

                        DetailRow("Risk Level", inv.riskLevel.replaceFirstChar { it.uppercase() }, valColor = if(inv.riskLevel=="high") Color(0xFFDC2626) else if(inv.riskLevel=="medium") Color(0xFFD97706) else Color(0xFF16A34A))
                        DetailRow("Category", inv.category.replaceFirstChar { it.uppercase() })
                        DetailRow("Dividends Received", fmt(inv.totalDividends), valColor = Color(0xFF2563EB), isLast = true) // Removed Funded From Row

                        if (inv.notes.isNotBlank()) {
                            HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(vertical = 12.dp))
                            Text("Notes", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
                            Text(inv.notes, fontSize = 13.sp, color = Color(0xFF111827), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            // Dividends
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Dividends & Payouts", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            Button(onClick = { viewModel.openDividendModal() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(32.dp)) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add", fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (inv.dividends.isEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Paid, null, modifier = Modifier.size(32.dp), tint = Color(0xFFD1D5DB))
                                Spacer(Modifier.height(8.dp))
                                Text("No dividends recorded", fontSize = 13.sp, color = Color(0xFF6B7280))
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                inv.dividends.forEach { div ->
                                    Row(Modifier.fillMaxWidth().background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(formatDisplayDate(div.date), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                            if (div.notes.isNotBlank()) Text(div.notes, fontSize = 11.sp, color = Color(0xFF065F46), modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text("+${fmt(div.amount)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A))
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

    // ─── Modals ───

    if (state.showEditModal) {
        AddEditInvestmentModal(
            form = state.editForm,
            accounts = state.accounts,
            isSaving = state.isSaving,
            onUpdateForm = { viewModel.updateEditForm(it) },
            onDismiss = { viewModel.closeEditModal() },
            onSave = { viewModel.updateInvestment() }
        )
    }

    if (state.showDividendModal) {
        AddDividendModal(
            form = state.dividendForm, accounts = state.accounts, isSaving = state.isSaving,
            onUpdateForm = { viewModel.updateDividendForm { it } }, onDismiss = { viewModel.closeDividendModal() }, onSave = { viewModel.addDividend() }
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteConfirm() },
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Investment?") },
            text = { Text("This will permanently delete \"${inv.name}\" and all associated dividend history.") },
            confirmButton = { Button(onClick = { viewModel.deleteInvestment() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete") } },
            dismissButton = { OutlinedButton(onClick = { viewModel.closeDeleteConfirm() }) { Text("Cancel") } }
        )
    }
}

// ─── Detail Components ────────────────────────────────────────────────────────

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

// ─── Modals ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditInvestmentModal(
    form: InvestmentForm, accounts: List<InvestmentAccount>, isSaving: Boolean,
    onUpdateForm: ((InvestmentForm) -> InvestmentForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf("form") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = Color.White
    ) {
        Crossfade(targetState = currentView, label = "EditInvestmentModal") { view ->
            when (view) {
                "form" -> {
                    Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Edit Investment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                        }
                        HorizontalDivider()

                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))) {
                                DrillDownRow(
                                    label = "Investment Type",
                                    value = InvestmentConstants.getLabel(form.type),
                                    icon = Icons.Default.Category,
                                    onClick = { currentView = "selectType" }
                                )
                            }

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
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DateSelectionField(label = "Purchase Date *", dateString = form.purchaseDate, onDateSelected = { d -> onUpdateForm { it.copy(purchaseDate = d) } }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = form.purchasePrice, onValueChange = { v -> onUpdateForm { it.copy(purchasePrice = v) } }, label = { Text("Price/Unit *") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(value = form.quantity, onValueChange = { v -> onUpdateForm { it.copy(quantity = v) } }, label = { Text("Quantity/Units *") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                OutlinedTextField(value = form.currentValue, onValueChange = { v -> onUpdateForm { it.copy(currentValue = v) } }, label = { Text("Current Value/Unit") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Status", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("active" to "Active", "matured" to "Matured", "sold" to "Sold", "closed" to "Closed").forEach { (v, l) ->
                                        val sel = form.status == v
                                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if(sel) Color(0xFF111827) else Color(0xFFF3F4F6)).clickable{ onUpdateForm { it.copy(status = v) } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                            Text(l, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if(sel) Color.White else Color(0xFF4B5563), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Risk Level", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("low" to "Low", "medium" to "Medium", "high" to "High").forEach { (v, l) ->
                                        val sel = form.riskLevel == v
                                        val bg = if(sel) {
                                            if(v=="high") Color(0xFFDC2626) else if(v=="medium") Color(0xFFD97706) else Color(0xFF16A34A)
                                        } else Color(0xFFF3F4F6)
                                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(bg).clickable{ onUpdateForm { it.copy(riskLevel = v) } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                            Text(l, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if(sel) Color.White else Color(0xFF4B5563))
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(value = form.notes, onValueChange = { n -> onUpdateForm { it.copy(notes = n) } }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), maxLines = 3)
                        }

                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))) {
                                if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Saving...") }
                                else Text("Update", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "selectType" -> {
                    GenericSelectionList(
                        title = "Investment Type",
                        items = InvestmentConstants.TYPES.map { Pair(it, InvestmentConstants.getLabel(it)) },
                        selectedValue = form.type,
                        onSelect = { onUpdateForm { f -> f.copy(type = it) }; currentView = "form" },
                        onBack = { currentView = "form" },
                        icon = Icons.Default.Category
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDividendModal(
    form: DividendForm, accounts: List<InvestmentAccount>, isSaving: Boolean,
    onUpdateForm: (DividendForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf("form") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Crossfade(targetState = currentView, label = "DividendModal") { view ->
            when(view) {
                "form" -> {
                    Column(Modifier.fillMaxWidth().imePadding()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Record Dividend", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                        }
                        HorizontalDivider()

                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(value = form.amount, onValueChange = { onUpdateForm(form.copy(amount = it)) }, label = { Text("Amount Received (৳) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            DateSelectionField(label = "Date *", dateString = form.date, onDateSelected = { onUpdateForm(form.copy(date = it)) })

                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))) {
                                DrillDownRow(
                                    label = "Deposit to Account *",
                                    value = accounts.find { it.id == form.accountId }?.name ?: "Select account",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    onClick = { currentView = "selectAccount" }
                                )
                            }

                            OutlinedTextField(value = form.notes, onValueChange = { onUpdateForm(form.copy(notes = it)) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
                        }

                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))) {
                                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp) else Text("Record", fontWeight = FontWeight.Bold)
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

// ─── Shared Sub-Sheet Helpers ─────────────────────────────────────────────────

@Composable
private fun SubSheetHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
    }
    HorizontalDivider(color = Color(0xFFE5E7EB))
}

@Composable
private fun DrillDownRow(label: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF6B7280), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, fontSize = 11.sp, color = Color(0xFF6B7280))
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFFD1D5DB))
    }
}

@Composable
private fun GenericSelectionList(title: String, items: List<Pair<String, String>>, selectedValue: String, onSelect: (String) -> Unit, onBack: () -> Unit, icon: ImageVector) {
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding()) {
        SubSheetHeader(title, onBack)
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                val isSelected = selectedValue == item.first
                Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(item.first) }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF9FAFB)), border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFFE5E7EB))) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(Color(0xFFDBEAFE), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color(0xFF1D4ED8)) }
                        Spacer(Modifier.width(16.dp))
                        Text(item.second, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF3B82F6))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionField(label: String, dateString: String, onDateSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(value = dateString, onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true, trailingIcon = { Icon(Icons.Default.CalendarToday, null) })
        Box(modifier = Modifier.matchParentSize().clickable { showPicker = true })
    }
    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try { val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }; sdf.parse(dateString)?.time } catch (_: Exception) { null }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { millis -> onDateSelected(SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))) }; showPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}