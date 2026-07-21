package com.hasan.nisabwallet.ui.screens.investments

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InvestmentsScreen(
    viewModel: InvestmentsViewModel = hiltViewModel(),
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
                is InvestmentsEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            // ─── Frozen Top Bar ───
            Surface(color = Color(0xFFF9FAFB), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(48.dp)) // Clears the floating hamburger menu
                    Column {
                        Text("Investment Portfolio", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Track your investments and returns", fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Button(
                        onClick = { viewModel.openAddModal() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Investment", fontSize = 14.sp)
                    }
                }

                if (state.isLoading) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF111827)) } }
                } else if (state.investments.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, modifier = Modifier.size(48.dp), tint = Color(0xFF9CA3AF))
                            Spacer(Modifier.height(16.dp))
                            Text("No investments yet", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                        }
                    }
                } else {
                    // Summary Cards
                    item {
                        val retColor = if (state.summary.absoluteReturn >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                        val retIcon = if (state.summary.absoluteReturn >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown
                        val retSub = "${if (state.summary.percentageReturn >= 0) "+" else ""}${String.format(Locale.US, "%.2f", state.summary.percentageReturn)}%"
                        
                        val cards = mutableListOf<@Composable () -> Unit>()
                        cards.add { SummaryCard("Total Invested", fmt(state.summary.totalInvested), null, Icons.Default.AttachMoney) }
                        cards.add { SummaryCard("Current Value", fmt(state.summary.totalCurrentValue), null, Icons.Default.BarChart) }
                        cards.add { SummaryCard("Total Returns", fmt(state.summary.absoluteReturn), retSub, retIcon, retColor) }
                        cards.add { SummaryCard("Dividends/Interest", fmt(state.summary.totalDividends), "${state.summary.activeCount} active", Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF2563EB)) }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (i in cards.indices step 2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Box(Modifier.weight(1f)) { cards[i]() }
                                    if (i + 1 < cards.size) Box(Modifier.weight(1f)) { cards[i + 1]() } else Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Asset Allocation
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PieChart, null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Asset Allocation", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF111827))
                                }
                                Spacer(Modifier.height(16.dp))
                                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.allocations.forEach { alloc ->
                                        val color = try { Color(InvestmentConstants.getColor(alloc.type).toColorInt()) } catch (_: Exception) { Color.Gray }
                                        Row(
                                            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(0.48f).background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(InvestmentConstants.getLabel(alloc.type), fontSize = 11.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("${String.format(Locale.US, "%.1f", alloc.percentage)}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                                Text(fmt(alloc.value), fontSize = 10.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Filter Panel
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = state.searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text("Search investments...", fontSize = 14.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val typeOpts = listOf(Pair("all", "All Types")) + InvestmentConstants.TYPES.map { Pair(it, InvestmentConstants.getLabel(it)) }
                                    CompactFilterDropdown("Type", state.filterType, typeOpts, { t: String -> viewModel.setFilterType(t) }, Modifier.weight(1f))
                                    CompactFilterDropdown("Status", state.filterStatus, listOf(Pair("all", "All Status"), Pair("active", "Active"), Pair("matured", "Matured"), Pair("sold", "Sold"), Pair("closed", "Closed")), { s: String -> viewModel.setFilterStatus(s) }, Modifier.weight(1f))
                                    CompactFilterDropdown("Sort By", state.sortBy, listOf(Pair("date", "Purchase Date"), Pair("return", "Return %"), Pair("value", "Current Value")), { s: String -> viewModel.setSortBy(s) }, Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // List Header
                    item { Text("Investments (${state.filteredInvestments.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 8.dp)) }

                    if (state.filteredInvestments.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FilterList, null, modifier = Modifier.size(32.dp), tint = Color(0xFFD1D5DB))
                                Spacer(Modifier.height(8.dp))
                                Text("No investments match your filters", fontSize = 13.sp, color = Color(0xFF6B7280))
                            }
                        }
                    } else {
                        items(state.filteredInvestments, key = { it.id }) { inv ->
                            InvestmentCard(inv = inv, fmt = fmt, onClick = { onNavigateToDetail(inv.id) })
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    if (state.showModal) {
        AddEditInvestmentModal(
            form = state.form, accounts = state.accounts, isSaving = state.isSaving,
            onUpdateForm = { viewModel.updateForm(it) }, onDismiss = { viewModel.closeModal() }, onSave = { viewModel.saveInvestment() }
        )
    }
}

// ─── Compact Filters ──────────────────────────────────────────────────────────

@Composable
private fun CompactFilterDropdown(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.find { it.first == value }?.second ?: label
    Box(modifier) {
        Surface(modifier = Modifier.fillMaxWidth().clickable { expanded = true }, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFD1D5DB)), color = Color.White) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, fontSize = 9.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(display, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.wrapContentWidth().background(Color.White)) {
            options.forEach { (k, v) -> DropdownMenuItem(text = { Text(v, fontSize = 12.sp, maxLines = 1) }, onClick = { onSelect(k); expanded = false }) }
        }
    }
}

// ─── Modal Implementation ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
        Crossfade(targetState = currentView, label = "InvestmentModal") { view ->
            when (view) {
                "form" -> {
                    Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding()) {
                        // Header
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (form.id != null) "Edit Investment" else "Add Investment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                        }
                        HorizontalDivider()

                        // Scrollable Form
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                            // Type & Account (Drill-downs remain since they have many options)
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))) {
                                DrillDownRow(
                                    label = "Investment Type *",
                                    value = InvestmentConstants.getLabel(form.type),
                                    icon = Icons.Default.Category,
                                    onClick = { if (form.id == null) currentView = "selectType" }
                                )
                                HorizontalDivider(color = Color(0xFFE5E7EB), modifier = Modifier.padding(start = 44.dp))
                                DrillDownRow(
                                    label = "Fund from Account",
                                    value = accounts.find { it.id == form.accountId }?.name ?: "Select account",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    onClick = { if (form.id == null) currentView = "selectAccount" }
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

                            // ─── Selectable Segments instead of DrillDown for small arrays ───
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

                        // Footer
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))) {
                                if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Saving...") }
                                else Text(if (form.id != null) "Update" else "Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "selectAccount" -> {
                    GenericSelectionList(
                        title = "Select Account",
                        items = accounts.map { Pair(it.id, "${it.name} (৳${it.balance})") },
                        selectedValue = form.accountId,
                        onSelect = { onUpdateForm { f -> f.copy(accountId = it) }; currentView = "form" },
                        onBack = { currentView = "form" },
                        icon = Icons.Default.AccountBalanceWallet
                    )
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

// ─── Sub-Sheet Components ─────────────────────────────────────────────────────

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

// ─── List Cards ───────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(title: String, value: String, subtitle: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, valueColor: Color = Color(0xFF111827)) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 11.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(icon, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
            }
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = valueColor, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun InvestmentCard(inv: Investment, fmt: (Double) -> String, onClick: () -> Unit) {
    val isProfit = inv.absoluteReturn >= 0
    val retColor = if (isProfit) Color(0xFF16A34A) else Color(0xFFDC2626)
    val typeColor = try { Color(InvestmentConstants.getColor(inv.type).toColorInt()) } catch (_: Exception) { Color.Gray }

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(typeColor))
            Column(Modifier.padding(16.dp).weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(inv.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(InvestmentConstants.getLabel(inv.type), fontSize = 11.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (inv.symbol.isNotBlank()) {
                                Text(" • ", fontSize = 11.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(horizontal = 4.dp))
                                Text(inv.symbol, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4B5563), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmt(inv.totalCurrentValue), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${if (isProfit) "+" else ""}${String.format(Locale.US, "%.2f", inv.percentageReturn)}%", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = retColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("Invested", fontSize = 10.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(fmt(inv.totalInvested), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Column(Modifier.weight(1f)) { Text("Returns", fontSize = 10.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(fmt(inv.absoluteReturn), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = retColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Column(Modifier.weight(1f)) { Text("Date", fontSize = 10.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(inv.purchaseDate.ifBlank { "-" }, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis) }
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