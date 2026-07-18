package com.hasan.nisabwallet.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isLoading,
        onRefresh  = { viewModel.refresh() },
    )

    Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(Modifier.fillMaxSize()) {

            TopActionBar(
                onAddIncome  = { viewModel.showAddSheet("Income") },
                onAddExpense = { viewModel.showAddSheet("Expense") },
                onAddTransfer= { viewModel.showAddSheet("Transfer") },
                onFilter     = { viewModel.showFilterSheet() },
                hasActiveFilter = state.filterType != "All"
                        || state.filterAccountId != "all"
                        || state.filterCategoryId != "all"
                        || state.filterStartDate.isNotBlank(),
            )

            MonthlySummaryRow(
                income  = state.summaryIncome,
                expense = state.summaryExpense,
                fmt     = fmt,
            )

            SearchBar(
                query    = state.searchQuery,
                onChange = { viewModel.setSearchQuery(it) },
            )

            TypeFilterTabs(
                selected = state.filterType,
                onSelect = { viewModel.setFilterType(it) },
            )

            if (state.isLoading) {
                LoadingShimmerList()
            } else if (state.filteredTransactions.isEmpty()) {
                EmptyTransactionsState(
                    hasFilters = state.filterType != "All" || state.searchQuery.isNotBlank(),
                    onClearFilters = { viewModel.clearFilters() },
                    onAdd = { viewModel.showAddSheet() },
                )
            } else {
                TransactionList(
                    transactions = state.filteredTransactions,
                    getAccountName  = { viewModel.getAccountName(it) },
                    getCategoryName = { viewModel.getCategoryName(it) },
                    getCategoryColor= { viewModel.getCategoryColor(it) },
                    onTap    = { viewModel.showDetail(it) },
                    fmt      = fmt,
                )
            }
        }

        PullRefreshIndicator(
            refreshing   = state.isLoading,
            state        = pullRefreshState,
            modifier     = Modifier.align(Alignment.TopCenter),
            contentColor = MaterialTheme.colorScheme.primary,
        )
    }

    if (state.showDetailPopup && state.detailTransaction != null) {
        TransactionDetailSheet(
            transaction     = state.detailTransaction!!,
            getAccountName  = { viewModel.getAccountName(it) },
            getCategoryName = { viewModel.getCategoryName(it) },
            onClose  = { viewModel.hideDetail() },
            onEdit   = { tx -> viewModel.hideDetail(); viewModel.showEditSheet(tx) },
            onDelete = { tx -> viewModel.hideDetail(); viewModel.showDeleteConfirm(tx) },
            fmt      = fmt,
        )
    }

    if (state.showAddEditSheet) {
        AddEditTransactionSheet(
            editing    = state.editingTransaction,
            defaultType = state.defaultAddType,
            accounts   = state.accounts,
            categories = state.categories,
            isSaving   = state.isSaving,
            onSave     = { form ->
                if (state.editingTransaction != null)
                    viewModel.updateTransaction(state.editingTransaction!!.id, form)
                else
                    viewModel.addTransaction(form)
            },
            onAddCategory = { name, type, color, onAdded ->
                viewModel.addCategoryInline(name, type, color, onAdded)
            },
            onDismiss  = { viewModel.hideAddEditSheet() },
        )
    }

    if (state.showFilterSheet) {
        FilterSheet(
            state    = state,
            onApplyType     = { viewModel.setFilterType(it) },
            onApplyAccount  = { viewModel.setFilterAccount(it) },
            onApplyCategory = { viewModel.setFilterCategory(it) },
            onApplyDateRange= { s, e -> viewModel.setFilterDateRange(s, e) },
            onClearAll      = { viewModel.clearFilters() },
            onDismiss       = { viewModel.hideFilterSheet() },
        )
    }

    if (state.showDeleteConfirm && state.deletingTransaction != null) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            icon             = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title            = { Text("Delete Record") },
            text             = { Text("Are you sure you want to delete this record? This will reverse the effect on your account balance(s).") },
            confirmButton    = {
                Button(
                    onClick = { viewModel.deleteTransaction() },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton    = {
                OutlinedButton(onClick = { viewModel.hideDeleteConfirm() }) { Text("Cancel") }
            },
        )
    }
}

// ─── Top Action Bar ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopActionBar(
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    onAddTransfer: () -> Unit,
    onFilter: () -> Unit,
    hasActiveFilter: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transactions",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            BadgedBox(
                badge = {
                    if (hasActiveFilter) Badge(containerColor = MaterialTheme.colorScheme.primary)
                }
            ) {
                IconButton(onClick = onFilter) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filter")
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onAddIncome,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFFF0FDF4), contentColor = Color(0xFF16A34A)
                ),
                contentPadding = PaddingValues(vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Income", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            FilledTonalButton(
                onClick = onAddExpense,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFFFFF1F2), contentColor = Color(0xFFDC2626)
                ),
                contentPadding = PaddingValues(vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Expense", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            FilledTonalButton(
                onClick = onAddTransfer,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFFEFF6FF), contentColor = Color(0xFF2563EB)
                ),
                contentPadding = PaddingValues(vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Transfer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Search Bar ───────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value         = query,
        onValueChange = onChange,
        modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder   = { Text("Search transactions…", fontSize = 14.sp) },
        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon  = {
            if (query.isNotBlank()) IconButton(onClick = { onChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear")
            }
        },
        singleLine    = true,
        shape         = RoundedCornerShape(12.dp),
    )
}

// ─── Monthly Summary Row ──────────────────────────────────────────────────────

@Composable
private fun MonthlySummaryRow(income: Double, expense: Double, fmt: (Double) -> String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryMiniCard(
            modifier   = Modifier.weight(1f),
            label      = "Income",
            value      = fmt(income),
            valueColor = Color(0xFF16A34A),
            icon       = Icons.AutoMirrored.Filled.TrendingUp,
        )
        SummaryMiniCard(
            modifier   = Modifier.weight(1f),
            label      = "Expenses",
            value      = fmt(expense),
            valueColor = Color(0xFFDC2626),
            icon       = Icons.AutoMirrored.Filled.TrendingDown,
        )
        SummaryMiniCard(
            modifier   = Modifier.weight(1f),
            label      = "Net",
            value      = fmt(income - expense),
            valueColor = if (income - expense >= 0) Color(0xFF16A34A) else Color(0xFFDC2626),
            icon       = Icons.Default.AccountBalance,
        )
    }
}

@Composable
private fun SummaryMiniCard(
    modifier: Modifier,
    label: String,
    value: String,
    valueColor: Color,
    icon: ImageVector,
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, tint = valueColor, modifier = Modifier.size(13.dp))
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor,
                fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── Type Filter Tabs ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeFilterTabs(selected: String, onSelect: (String) -> Unit) {
    val tabs = listOf("All", "Income", "Expense")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { tab ->
            val isSelected = selected == tab
            val bg = when {
                isSelected && tab == "Income"  -> Color(0xFFF0FDF4)
                isSelected && tab == "Expense" -> Color(0xFFFFF1F2)
                isSelected                     -> MaterialTheme.colorScheme.primaryContainer
                else                           -> MaterialTheme.colorScheme.surfaceVariant
            }
            val textColor = when {
                isSelected && tab == "Income"  -> Color(0xFF16A34A)
                isSelected && tab == "Expense" -> Color(0xFFDC2626)
                isSelected                     -> MaterialTheme.colorScheme.onPrimaryContainer
                else                           -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            FilterChip(
                selected = isSelected,
                onClick  = { onSelect(tab) },
                label    = { Text(tab, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = textColor) },
                colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = bg),
            )
        }
    }
}

// ─── Transaction List ─────────────────────────────────────────────────────────

@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    getAccountName: (String) -> String,
    getCategoryName: (String) -> String,
    getCategoryColor: (String) -> String,
    onTap: (Transaction) -> Unit,
    fmt: (Double) -> String,
) {
    val grouped = remember(transactions) { 
        transactions.groupBy { it.date }.entries.sortedByDescending { it.key } 
    }
    
    val displayDates = remember(grouped) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val display = SimpleDateFormat("dd MMM yyyy", Locale.US)
        grouped.associate { 
            it.key to runCatching { display.format(sdf.parse(it.key)!!) }.getOrDefault(it.key) 
        }
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        grouped.forEach { (date, txs) ->
            item(key = "header-$date") {
                Text(
                    text     = displayDates[date] ?: date,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(txs, key = { it.id }) { tx ->
                TransactionListItem(
                    tx              = tx,
                    getAccountName  = getAccountName,
                    getCategoryName = getCategoryName,
                    getCategoryColor= getCategoryColor,
                    onTap           = { onTap(tx) },
                    fmt             = fmt,
                )
            }
        }
    }
}

@Composable
private fun TransactionListItem(
    tx: Transaction,
    getAccountName: (String) -> String,
    getCategoryName: (String) -> String,
    getCategoryColor: (String) -> String,
    onTap: () -> Unit,
    fmt: (Double) -> String,
) {
    val colorHex = getCategoryColor(tx.categoryId)
    val catColor = remember(tx.isTransfer, colorHex) {
        if (tx.isTransfer) Color(0xFF3B82F6) else runCatching {
            Color("#${colorHex.trimStart('#')}".toColorInt())
        }.getOrDefault(Color.Gray)
    }

    val amountColor = if (tx.isTransfer) Color(0xFF3B82F6) else if (tx.type == "Income") Color(0xFF16A34A) else Color(0xFFDC2626)
    val sign        = if (tx.isTransfer) "" else if (tx.type == "Income") "+" else "−"
    val accName     = getAccountName(tx.accountId)

    val displayTitle = if (tx.isTransfer) {
        if (tx.type == "Income") "From ${tx.relatedAccountName}" else "To ${tx.relatedAccountName}"
    } else {
        tx.description.ifBlank { getCategoryName(tx.categoryId).ifBlank { tx.type } }
    }

    val displaySubtitle = if (tx.isTransfer) {
        accName
    } else {
        "${getCategoryName(tx.categoryId)} · $accName"
    }

    Card(
        modifier  = Modifier.fillMaxWidth().clickable { onTap() },
        shape     = RoundedCornerShape(10.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier         = Modifier.size(36.dp).background(catColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (tx.isTransfer) {
                    Icon(Icons.Default.SyncAlt, contentDescription = null, tint = catColor, modifier = Modifier.size(16.dp))
                } else {
                    Box(modifier = Modifier.size(10.dp).background(catColor, CircleShape))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = displayTitle,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(displaySubtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "$sign${fmt(tx.amount)}",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = amountColor,
                    fontFamily = FontFamily.Monospace,
                )
                if (tx.chargeAmount > 0) {
                    Text(
                        text     = "+${fmt(tx.chargeAmount)} fee",
                        fontSize = 10.sp,
                        color    = Color(0xFFF97316),
                    )
                }
            }
        }
    }
}

// ─── Transaction Detail Sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailSheet(
    transaction: Transaction,
    getAccountName: (String) -> String,
    getCategoryName: (String) -> String,
    onClose: () -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    fmt: (Double) -> String,
) {
    val accentColor = if (transaction.isTransfer) Color(0xFF2563EB) else if (transaction.type == "Income") Color(0xFF16A34A) else Color(0xFFDC2626)
    val sign        = if (transaction.isTransfer) "" else if (transaction.type == "Income") "+" else "−"
    
    val dateLabel = remember(transaction.date) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val display = SimpleDateFormat("d MMMM yyyy", Locale.US)
        runCatching { display.format(sdf.parse(transaction.date)!!) }.getOrDefault(transaction.date)
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {

            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(accentColor)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Column {
                    Text(
                        text       = "$sign${fmt(transaction.amount)}",
                        fontSize   = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = if (transaction.isTransfer) "Account Transfer" else transaction.description.ifBlank { getCategoryName(transaction.categoryId) },
                        fontSize = 15.sp,
                        color    = Color.White.copy(alpha = 0.85f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                DetailRow("Type",     if (transaction.isTransfer) "Transfer" else transaction.type)
                
                if (transaction.isTransfer) {
                    if (transaction.type == "Income") {
                        DetailRow("To Account", getAccountName(transaction.accountId))
                        DetailRow("From Account", transaction.relatedAccountName ?: "")
                    } else {
                        DetailRow("From Account", getAccountName(transaction.accountId))
                        DetailRow("To Account", transaction.relatedAccountName ?: "")
                    }
                    if (!transaction.originalDescription.isNullOrBlank()) {
                        DetailRow("Note", transaction.originalDescription)
                    }
                } else {
                    DetailRow("Category", getCategoryName(transaction.categoryId))
                    DetailRow("Account",  getAccountName(transaction.accountId))
                }
                
                DetailRow("Date",     dateLabel)
                if (transaction.chargeAmount > 0) DetailRow("Charges", "${fmt(transaction.chargeAmount)}${if (transaction.chargeNote.isNotBlank()) " (${transaction.chargeNote})" else ""}", Color(0xFFF97316))
                if (transaction.isRiba)     DetailRow("⚠ Riba Flag", "This is interest income", Color(0xFFD97706))
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick  = { onDelete(transaction) },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
                Button(
                    onClick  = { onEdit(transaction) },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

// ─── Add / Edit Transaction Sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditTransactionSheet(
    editing: Transaction?,
    defaultType: String,
    accounts: List<AccountItem>,
    categories: List<CategoryItem>,
    isSaving: Boolean,
    onSave: (TransactionForm) -> Unit,
    onAddCategory: (name: String, type: String, color: String, onAdded: (CategoryItem) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember(editing, defaultType) {
        mutableStateOf(
            if (editing != null) {
                if (editing.isTransfer) {
                    val isFrom = editing.transferDirection == "from"
                    TransactionForm(
                        type = "Transfer",
                        amount = editing.amount.toString(),
                        accountId = if (isFrom) editing.accountId else editing.relatedAccountId ?: "",
                        toAccountId = if (isFrom) editing.relatedAccountId ?: "" else editing.accountId,
                        description = editing.originalDescription ?: "",
                        date = editing.date,
                        chargeAmount = "", 
                        chargeNote = "",
                    )
                } else {
                    TransactionForm(
                        type        = editing.type,
                        amount      = editing.amount.toString(),
                        accountId   = editing.accountId,
                        categoryId  = editing.categoryId,
                        description = editing.description,
                        date        = editing.date,
                        chargeAmount = if (editing.chargeAmount > 0) editing.chargeAmount.toString() else "",
                        chargeNote  = editing.chargeNote,
                    )
                }
            } else TransactionForm(type = defaultType)
        )
    }

    val isIncome    = form.type == "Income"
    val isTransfer  = form.type == "Transfer"
    val accentColor = if (isTransfer) Color(0xFF2563EB) else if (isIncome) Color(0xFF16A34A) else Color(0xFFDC2626)
    val filteredCats = categories.filter { it.type == form.type }

    var showInlineCatForm by remember { mutableStateOf(false) }
    var inlineCatName     by remember { mutableStateOf("") }
    var inlineCatColor    by remember { mutableStateOf(if (isIncome) "#10B981" else "#EF4444") }
    val colorOptions = listOf("#EF4444","#F59E0B","#10B981","#3B82F6","#6366F1","#8B5CF6","#EC4899","#06B6D4","#84CC16","#F97316")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text       = if (editing != null) "Edit Record" else "Add Record",
                fontSize   = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
            )

            // Tabs
            if (editing == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    listOf("Income", "Expense", "Transfer").forEach { type ->
                        val sel = form.type == type
                        val bg  = when { 
                            sel && type == "Income" -> Color(0xFF16A34A)
                            sel && type == "Transfer" -> Color(0xFF2563EB)
                            sel -> Color(0xFFDC2626)
                            else -> Color.Transparent 
                        }
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg).clickable { 
                                form = form.copy(type = type, categoryId = "") 
                            }.padding(vertical = 12.dp),
                            contentAlignment  = Alignment.Center,
                        ) {
                            Text(type, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            OutlinedTextField(
                value         = form.amount,
                onValueChange = { form = form.copy(amount = it) },
                label         = { Text("Amount (৳)") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            if (isTransfer) {
                LabeledDropdown(
                    label   = "From Account",
                    value   = accounts.find { it.id == form.accountId }?.let { "${it.name} — ${CurrencyFormatter.formatBDT(it.balance)}" } ?: "Select Source Account",
                    options = accounts.map { it.id to "${it.name} — ${CurrencyFormatter.formatBDT(it.balance)}" },
                    onSelect= { form = form.copy(accountId = it) },
                )
                
                LabeledDropdown(
                    label   = "To Account",
                    value   = accounts.find { it.id == form.toAccountId }?.name ?: "Select Destination Account",
                    options = accounts.filter { it.id != form.accountId }.map { it.id to it.name },
                    onSelect= { form = form.copy(toAccountId = it) },
                )
            } else {
                LabeledDropdown(
                    label   = "Account",
                    value   = accounts.find { it.id == form.accountId }?.let { "${it.name} — ${CurrencyFormatter.formatBDT(it.balance)}" } ?: "Select Account",
                    options = accounts.map { it.id to "${it.name} — ${CurrencyFormatter.formatBDT(it.balance)}" },
                    onSelect= { form = form.copy(accountId = it) },
                )

                Column {
                    LabeledDropdown(
                        label    = "Category",
                        value    = filteredCats.find { it.id == form.categoryId }?.name ?: "Select Category",
                        options  = filteredCats.map { it.id to "${it.name}${if (it.isRiba) " ⚠" else ""}" } + listOf("__add_new__" to "➕ Add new category…"),
                        onSelect = {
                            if (it == "__add_new__") {
                                inlineCatName = ""
                                showInlineCatForm = true
                            } else {
                                form = form.copy(categoryId = it)
                                showInlineCatForm = false
                            }
                        },
                    )

                    AnimatedVisibility(visible = showInlineCatForm) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = CardDefaults.cardColors(containerColor = if (isIncome) Color(0xFFF0FDF4) else Color(0xFFFFF1F2)),
                            border   = BorderStroke(1.dp, if (isIncome) Color(0xFF86EFAC) else Color(0xFFFCA5A5)),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("New ${form.type} Category", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
                                OutlinedTextField(
                                    value         = inlineCatName,
                                    onValueChange = { inlineCatName = it },
                                    placeholder   = { Text("Category name…", fontSize = 13.sp) },
                                    singleLine    = true,
                                    modifier      = Modifier.fillMaxWidth(),
                                    shape         = RoundedCornerShape(8.dp),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    colorOptions.forEach { hex ->
                                        val c = runCatching { Color(hex.toColorInt()) }.getOrDefault(Color.Gray)
                                        Box(
                                            modifier = Modifier.size(24.dp)
                                                .background(c, CircleShape)
                                                .then(if (inlineCatColor == hex) Modifier.border(2.dp, Color.Black, CircleShape) else Modifier)
                                                .clickable { inlineCatColor = hex }
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { showInlineCatForm = false }) { Text("Cancel", fontSize = 13.sp) }
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        onClick  = {
                                            if (inlineCatName.isNotBlank()) {
                                                onAddCategory(inlineCatName, form.type, inlineCatColor) { newCat ->
                                                    form = form.copy(categoryId = newCat.id)
                                                    showInlineCatForm = false
                                                }
                                            }
                                        },
                                        colors   = ButtonDefaults.buttonColors(containerColor = accentColor),
                                    ) { Text("Save", fontSize = 13.sp) }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value         = form.description,
                onValueChange = { form = form.copy(description = it) },
                label         = { Text("Note (optional)") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp),
                maxLines      = 2,
            )

            // Date Picker Field
            DateSelectionField(
                label = "Date (yyyy-MM-dd)",
                dateString = form.date,
                onDateSelected = { form = form.copy(date = it) }
            )

            ChargesSection(
                chargeAmount = form.chargeAmount,
                chargeNote   = form.chargeNote,
                onAmountChange = { form = form.copy(chargeAmount = it) },
                onNoteChange   = { form = form.copy(chargeNote = it) },
            )

            Button(
                onClick   = { onSave(form) },
                enabled   = !isSaving && form.amount.isNotBlank() && form.accountId.isNotBlank() && (if (isTransfer) form.toAccountId.isNotBlank() else form.categoryId.isNotBlank()),
                modifier  = Modifier.fillMaxWidth().height(52.dp),
                shape     = RoundedCornerShape(12.dp),
                colors    = ButtonDefaults.buttonColors(containerColor = accentColor),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…", fontSize = 15.sp)
                } else {
                    Text(if (editing != null) "Update Record" else "Add Record", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { 
                timeZone = TimeZone.getTimeZone("UTC") 
            }
            sdf.parse(dateString)?.time
        }.getOrNull()
    )

    Box(modifier = modifier) {
        OutlinedTextField(
            value = dateString,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) }
        )
        // Invisible overlay to intercept clicks perfectly
        Box(modifier = Modifier.matchParentSize().clickable { showPicker = true })
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { 
                            timeZone = TimeZone.getTimeZone("UTC") 
                        }
                        onDateSelected(sdf.format(Date(millis)))
                    }
                    showPicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}


// ── Charges section ──
@Composable
private fun ChargesSection(
    chargeAmount: String,
    chargeNote: String,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(chargeAmount.isNotBlank() || chargeNote.isNotBlank()) }

    if (!open) {
        OutlinedButton(
            onClick  = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(10.dp),
            border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("Add related charges / fees (optional)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(10.dp),
            colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
            border   = BorderStroke(1.dp, Color(0xFFFED7AA)),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(14.dp))
                        Text("Related Charges / Fees", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C), letterSpacing = 0.5.sp)
                    }
                    IconButton(onClick = { open = false; onAmountChange(""); onNoteChange("") }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = chargeAmount,
                        onValueChange = onAmountChange,
                        label         = { Text("Amount (৳)", fontSize = 12.sp) },
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(8.dp),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value         = chargeNote,
                        onValueChange = onNoteChange,
                        label         = { Text("Label", fontSize = 12.sp) },
                        placeholder   = { Text("e.g. TDS, Bank fee…", fontSize = 12.sp) },
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(8.dp),
                        singleLine    = true,
                    )
                }
                Text(
                    text     = "ℹ Will be recorded separately as an expense under \"Fees & Charges\"",
                    fontSize = 11.sp,
                    color    = Color(0xFFF97316),
                )
            }
        }
    }
}

// ─── Filter Sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: TransactionsUiState,
    onApplyType: (String) -> Unit,
    onApplyAccount: (String) -> Unit,
    onApplyCategory: (String) -> Unit,
    onApplyDateRange: (String, String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var startDate by remember { mutableStateOf(state.filterStartDate) }
    var endDate   by remember { mutableStateOf(state.filterEndDate) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("Filters", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { onClearAll(); onDismiss() }) { Text("Clear all") }
            }

            FilterSection("Type") {
                listOf("All", "Income", "Expense").forEach { t ->
                    FilterChip(selected = state.filterType == t, onClick = { onApplyType(t) }, label = { Text(t, fontSize = 13.sp) }, modifier = Modifier.padding(end = 6.dp))
                }
            }

            FilterSection("Account") {
                val opts = listOf(AccountItem(id = "all", name = "All Accounts")) + state.accounts
                LabeledDropdown(
                    label    = "Account",
                    value    = opts.find { it.id == state.filterAccountId }?.name ?: "All Accounts",
                    options  = opts.map { it.id to it.name },
                    onSelect = onApplyAccount,
                )
            }

            FilterSection("Category") {
                val cats = listOf(CategoryItem(id = "all", name = "All Categories", type = "")) + state.categories
                LabeledDropdown(
                    label    = "Category",
                    value    = cats.find { it.id == state.filterCategoryId }?.name ?: "All Categories",
                    options  = cats.map { it.id to it.name },
                    onSelect = onApplyCategory,
                )
            }

            FilterSection("Date Range") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateSelectionField(
                        label = "From",
                        dateString = startDate,
                        onDateSelected = { startDate = it },
                        modifier = Modifier.weight(1f)
                    )
                    DateSelectionField(
                        label = "To",
                        dateString = endDate,
                        onDateSelected = { endDate = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick   = { onApplyDateRange(startDate, endDate); onDismiss() },
                modifier  = Modifier.fillMaxWidth().height(50.dp),
                shape     = RoundedCornerShape(12.dp),
            ) { Text("Apply Filters", fontSize = 15.sp) }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
        Row(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun LabeledDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value         = value,
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label) },
            modifier      = Modifier.fillMaxWidth(), 
            shape         = RoundedCornerShape(10.dp),
            trailingIcon  = { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null) },
        )
        Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
        
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name, fontSize = 14.sp) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyTransactionsState(
    hasFilters: Boolean,
    onClearFilters: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier         = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(if (hasFilters) "No matching transactions" else "No transactions yet", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(if (hasFilters) "Try adjusting or clearing your filters" else "Add income or expenses to start tracking", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (hasFilters) {
            OutlinedButton(onClick = onClearFilters, shape = RoundedCornerShape(10.dp)) { Text("Clear Filters") }
        } else {
            Button(onClick = onAdd, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Transaction")
            }
        }
    }
}

// ─── Loading Shimmer ──────────────────────────────────────────────────────────

@Composable
private fun LoadingShimmerList() {
    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(8) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            )
        }
    }
}