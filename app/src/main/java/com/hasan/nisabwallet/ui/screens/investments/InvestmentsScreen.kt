package com.hasan.nisabwallet.ui.screens.investments

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
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
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InvestmentsScreen(
    viewModel: InvestmentsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is InvestmentsEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
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
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF111827))
                                }
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Color(0xFF111827), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Investment Portfolio", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                            Text("Track your investments and monitor returns", fontSize = 13.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(start = 32.dp))
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
                        Text("Add Investment", fontSize = 14.sp)
                    }
                }

                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF111827))
                        }
                    }
                } else if (state.investments.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            color = Color.White
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, modifier = Modifier.size(48.dp), tint = Color(0xFF9CA3AF))
                                Spacer(Modifier.height(16.dp))
                                Text("No investments yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563))
                                Text("Start tracking your investment portfolio by adding your first investment", fontSize = 13.sp, color = Color(0xFF9CA3AF), textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    // Summary Cards
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryCard(
                                title = "Total Invested", icon = Icons.Default.AttachMoney, value = fmt(state.summary.totalInvested),
                                modifier = Modifier.weight(1f)
                            )
                            SummaryCard(
                                title = "Current Value", icon = Icons.Default.BarChart, value = fmt(state.summary.totalCurrentValue),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val retColor = if (state.summary.absoluteReturn >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                            SummaryCard(
                                title = "Total Returns", 
                                icon = if (state.summary.absoluteReturn >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                value = fmt(state.summary.absoluteReturn),
                                subtitle = "${if (state.summary.percentageReturn >= 0) "+" else ""}${String.format("%.2f", state.summary.percentageReturn)}%",
                                valueColor = retColor,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryCard(
                                title = "Dividends/Interest", icon = Icons.AutoMirrored.Filled.TrendingUp, value = fmt(state.summary.totalDividends),
                                subtitle = "${state.summary.activeCount} active",
                                valueColor = Color(0xFF2563EB),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Asset Allocation
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PieChart, null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Asset Allocation", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF111827))
                                }
                                Spacer(Modifier.height(16.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.allocations.forEach { alloc ->
                                        val color = runCatching { Color(android.graphics.Color.parseColor(InvestmentConstants.getColor(alloc.type))) }.getOrDefault(Color.Gray)
                                        Row(
                                            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(0.48f).background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(InvestmentConstants.getLabel(alloc.type), fontSize = 11.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("${String.format("%.1f", alloc.percentage)}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                                Text(fmt(alloc.value), fontSize = 10.sp, color = Color(0xFF6B7280))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Filters
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = state.searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text("Search name or symbol...", fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    singleLine = true, shape = RoundedCornerShape(8.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterDropdown(label = "Type", value = state.filterType, options = listOf("all" to "All Types") + InvestmentConstants.TYPES.map { it to InvestmentConstants.getLabel(it) }, onSelect = { viewModel.setFilterType(it) }, modifier = Modifier.weight(1f))
                                    FilterDropdown(label = "Status", value = state.filterStatus, options = listOf("all" to "All Status", "active" to "Active", "matured" to "Matured", "sold" to "Sold", "closed" to "Closed"), onSelect = { viewModel.setFilterStatus(it) }, modifier = Modifier.weight(1f))
                                    FilterDropdown(label = "Sort", value = state.sortBy, options = listOf("date" to "Date", "return" to "Return %", "value" to "Current Value"), onSelect = { viewModel.setSortBy(it) }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // List
                    item {
                        Text("Investments (${state.filteredInvestments.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 8.dp))
                    }

                    if (state.filteredInvestments.isEmpty()) {
                        item {
                            Text("No investments match your filters", fontSize = 13.sp, color = Color(0xFF6B7280), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
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
            form = state.form,
            accounts = state.accounts,
            isSaving = state.isSaving,
            onUpdateForm = { viewModel.updateForm(it) },
            onDismiss = { viewModel.closeModal() },
            onSave = { viewModel.saveInvestment() }
        )
    }
}

@Composable
private fun SummaryCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, subtitle: String? = null, valueColor: Color = Color(0xFF111827), modifier: Modifier = Modifier) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 11.sp, color = Color(0xFF6B7280))
                Icon(icon, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = valueColor, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun FilterDropdown(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.find { it.first == value }?.second ?: label
    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFD1D5DB)), color = Color.White
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(label, fontSize = 9.sp, color = Color(0xFF6B7280))
                    Text(display, fontSize = 11.sp, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (k, v) ->
                DropdownMenuItem(text = { Text(v, fontSize = 13.sp) }, onClick = { onSelect(k); expanded = false })
            }
        }
    }
}

@Composable
private fun InvestmentCard(inv: Investment, fmt: (Double) -> String, onClick: () -> Unit) {
    val isProfit = inv.absoluteReturn >= 0
    val retColor = if (isProfit) Color(0xFF16A34A) else Color(0xFFDC2626)
    val typeColor = runCatching { Color(android.graphics.Color.parseColor(InvestmentConstants.getColor(inv.type))) }.getOrDefault(Color.Gray)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(typeColor))
            Column(Modifier.padding(16.dp).weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(inv.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(InvestmentConstants.getLabel(inv.type), fontSize = 11.sp, color = Color(0xFF6B7280))
                            if (inv.symbol.isNotBlank()) {
                                Text(" • ", fontSize = 11.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(horizontal = 4.dp))
                                Text(inv.symbol, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4B5563))
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmt(inv.totalCurrentValue), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text("${if (isProfit) "+" else ""}${String.format("%.2f", inv.percentageReturn)}%", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = retColor)
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Invested", fontSize = 10.sp, color = Color(0xFF6B7280))
                        Text(fmt(inv.totalInvested), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                    }
                    Column {
                        Text("Returns", fontSize = 10.sp, color = Color(0xFF6B7280))
                        Text(fmt(inv.absoluteReturn), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = retColor)
                    }
                    Column {
                        Text("Date", fontSize = 10.sp, color = Color(0xFF6B7280))
                        Text(inv.purchaseDate.ifBlank { "-" }, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                    }
                    if (inv.type == InvestmentConstants.STOCK || inv.type == InvestmentConstants.MUTUAL_FUND) {
                        Column {
                            Text("Qty", fontSize = 10.sp, color = Color(0xFF6B7280))
                            Text(inv.quantity.toString(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditInvestmentModal(
    form: InvestmentForm,
    accounts: List<InvestmentAccount>,
    isSaving: Boolean,
    onUpdateForm: ((InvestmentForm) -> InvestmentForm) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = Color.White
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (form.id != null) "Edit Investment" else "Add Investment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider()

            // Form Content
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Type Dropdown
                var typeExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = InvestmentConstants.getLabel(form.type), onValueChange = {}, readOnly = true,
                        label = { Text("Investment Type *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }, enabled = form.id == null
                    )
                    if (form.id == null) {
                        Box(modifier = Modifier.matchParentSize().clickable { typeExpanded = true })
                        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            InvestmentConstants.TYPES.forEach { t ->
                                DropdownMenuItem(text = { Text(InvestmentConstants.getLabel(t)) }, onClick = { onUpdateForm { it.copy(type = t) }; typeExpanded = false })
                            }
                        }
                    }
                }

                OutlinedTextField(value = form.name, onValueChange = { n -> onUpdateForm { it.copy(name = n) } }, label = { Text("Investment Name *") }, placeholder = { Text("Apple Inc., Grameen Bank FDR...") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)

                // Account Selection
                var accExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = accounts.find { it.id == form.accountId }?.name ?: "Select account", 
                        onValueChange = {}, readOnly = true, label = { Text("Fund from Account") }, 
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), 
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        enabled = form.id == null // Lock account selection if editing
                    )
                    if (form.id == null) {
                        Box(Modifier.matchParentSize().clickable { accExpanded = true })
                        DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                            accounts.forEach { a -> DropdownMenuItem(text = { Text("${a.name} (৳${a.balance})") }, onClick = { onUpdateForm { it.copy(accountId = a.id) }; accExpanded = false }) }
                        }
                    }
                }

                // Type Specific Fields
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

                // Core fields
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateSelectionField(label = "Purchase Date *", dateString = form.purchaseDate, onDateSelected = { d -> onUpdateForm { it.copy(purchaseDate = d) } }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = form.purchasePrice, onValueChange = { v -> onUpdateForm { it.copy(purchasePrice = v) } }, label = { Text("Price/Unit *") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = form.quantity, onValueChange = { v -> onUpdateForm { it.copy(quantity = v) } }, label = { Text("Quantity/Units *") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = form.currentValue, onValueChange = { v -> onUpdateForm { it.copy(currentValue = v) } }, label = { Text("Current Value/Unit") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }

                // Metadata Status Dropdowns
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

            // Footer
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(
                    onClick = onSave, disabled = isSaving, modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving...", fontSize = 14.sp)
                    } else {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (form.id != null) "Update" else "Save", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ── Custom Native Date Picker Field ──
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
        initialSelectedDateMillis = runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            sdf.parse(dateString)?.time
        }.getOrNull()
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
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
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