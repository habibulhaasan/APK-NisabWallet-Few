package com.hasan.nisabwallet.ui.screens.accounts

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = hiltViewModel(),
    triggerFabAdd: Long = 0L,
    onAddHandled: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    var accountToDelete by remember { mutableStateOf<AccountItem?>(null) }

    // ─── Dynamic FAB Trigger ───
    LaunchedEffect(triggerFabAdd) {
        if (triggerFabAdd > 0L) {
            viewModel.openAddModal()
            onAddHandled()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AccountsEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            // ─── Frozen Top Bar ───
            Surface(color = Color(0xFFF9FAFB), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(48.dp)) // Clears the floating hamburger menu
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF111827), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Accounts", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Manage your financial accounts", fontSize = 12.sp, color = Color(0xFF6B7280))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Action Buttons
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.loadDefaultAccounts() },
                            enabled = !state.isLoadingDefaults,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            if (state.isLoadingDefaults) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Loading...", fontSize = 13.sp)
                            } else {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Load Defaults", fontSize = 13.sp)
                            }
                        }

                        Button(
                            onClick = { viewModel.openAddModal() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Account", fontSize = 13.sp)
                        }
                    }
                }

                // Total Balance Card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF1F2937), Color(0xFF374151))))
                            .padding(24.dp)
                    ) {
                        Column {
                            Text("Total Balance", fontSize = 13.sp, color = Color(0xFFD1D5DB))
                            Spacer(Modifier.height(4.dp))
                            Text(fmt(state.totalBalance), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Text("Across ${state.accounts.size} account${if (state.accounts.size != 1) "s" else ""}", fontSize = 13.sp, color = Color(0xFFD1D5DB))
                        }
                    }
                }

                // State handling
                if (state.isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF2563EB))
                        }
                    }
                } else if (state.accounts.isEmpty()) {
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
                                Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(48.dp), tint = Color(0xFF9CA3AF))
                                Spacer(Modifier.height(16.dp))
                                Text("No accounts yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563))
                                Text("Create your first account or load defaults to start", fontSize = 13.sp, color = Color(0xFF9CA3AF), textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    // Accounts List
                    items(state.accounts, key = { it.account.id }) { accWithAlloc ->
                        AccountCard(
                            item = accWithAlloc,
                            fmt = fmt,
                            onEdit = { viewModel.openEditModal(it) },
                            onDelete = { accountToDelete = it }
                        )
                    }
                }
            }

            // Sync Status Indicator
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                shape = RoundedCornerShape(50),
                color = if (state.syncStatus == "Synced") Color(0xFFD1FAE5) else Color(0xFFFEF3C7),
                border = BorderStroke(1.dp, if (state.syncStatus == "Synced") Color(0xFF10B981) else Color(0xFFF59E0B))
            ) {
                Text(
                    text = "State: ${state.syncStatus}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = if (state.syncStatus == "Synced") Color(0xFF065F46) else Color(0xFF92400E),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }

    if (state.showAddEditModal) {
        AddEditAccountModal(
            form = state.form,
            isSaving = state.isSaving,
            onUpdateForm = { viewModel.updateForm(it) },
            onDismiss = { viewModel.closeModal() },
            onSave = { viewModel.saveAccount() }
        )
    }

    if (accountToDelete != null) {
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete \"${accountToDelete?.name}\"?") },
            confirmButton = {
                Button(
                    onClick = { accountToDelete?.let { viewModel.deleteAccount(it) }; accountToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { accountToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AccountCard(
    item: AccountWithAllocations,
    fmt: (Double) -> String,
    onEdit: (AccountItem) -> Unit,
    onDelete: (AccountItem) -> Unit
) {
    val acc = item.account

    val icon = when (acc.type) {
        "Cash" -> Icons.Default.AccountBalanceWallet
        "Bank" -> Icons.Default.AccountBalance
        "Mobile Banking" -> Icons.Default.Smartphone
        "Gold", "Silver" -> Icons.Default.MonetizationOn
        else -> Icons.Default.AccountBalanceWallet
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Box {
            if (acc.isDefault) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFEFF6FF)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF2563EB), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Default", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2563EB))
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.size(44.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = Color(0xFF374151), modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                            Text(acc.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(acc.type, fontSize = 12.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(modifier = Modifier.padding(top = 28.dp)) {
                        IconButton(onClick = { onEdit(acc) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDelete(acc) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(fmt(acc.balance), fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFF111827), fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (item.allocated > 0) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFE5E7EB))
                    Spacer(Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Available to Spend", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF16A34A))
                        Text(fmt(item.available), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Allocated to Goals", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFEA580C))
                        Text(fmt(item.allocated), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    }

                    Spacer(Modifier.height(8.dp))
                    item.allocatedGoals.forEach { goal ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• ${goal.goalName}", fontSize = 12.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(fmt(goal.currentAmount), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151))
                        }
                    }

                    if (item.available < 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF2F2)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("You've spent ${fmt(kotlin.math.abs(item.available))} of your goal money!", fontSize = 12.sp, color = Color(0xFFDC2626))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Modern Drill-Down Modal ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditAccountModal(
    form: AccountForm,
    isSaving: Boolean,
    onUpdateForm: ((AccountForm) -> AccountForm) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf("form") }
    val types = listOf("Cash", "Bank", "Mobile Banking", "Gold", "Silver")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = Color.White
    ) {
        Crossfade(targetState = currentView, label = "AccountModal") { view ->
            when (view) {
                "form" -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding().navigationBarsPadding()
                    ) {
                        // Header
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (form.id != null) "Edit Account" else "Add New Account", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                        HorizontalDivider()

                        Column(
                            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = form.name,
                                onValueChange = { n -> onUpdateForm { it.copy(name = n) } },
                                label = { Text("Account Name") },
                                placeholder = { Text("e.g. DBBL Savings") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            // Drill-Down Selection Field for Account Type
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))) {
                                DrillDownRow(
                                    label = "Account Type",
                                    value = form.type,
                                    icon = Icons.Default.Category,
                                    onClick = { currentView = "selectType" }
                                )
                            }

                            Column {
                                OutlinedTextField(
                                    value = form.balance,
                                    onValueChange = { b -> onUpdateForm { it.copy(balance = b) } },
                                    label = { Text(if (form.type == "Gold" || form.type == "Silver") "Current Value (৳)" else "Balance (৳)") },
                                    placeholder = { Text("0.00") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                if (form.type == "Gold" || form.type == "Silver") {
                                    Text("Enter the current market value in Taka", fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                                }
                            }
                        }

                        // Footer
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                            Button(
                                onClick = onSave,
                                enabled = !isSaving,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Saving...", fontSize = 14.sp)
                                } else {
                                    Text(if (form.id != null) "Update" else "Add Account", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                "selectType" -> {
                    GenericSelectionList(
                        title = "Select Account Type",
                        items = types.map { Pair(it, it) },
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