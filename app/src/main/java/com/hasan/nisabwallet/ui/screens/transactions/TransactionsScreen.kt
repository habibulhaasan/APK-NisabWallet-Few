package com.hasan.nisabwallet.ui.screens.transactions

// Converted from: src/app/dashboard/transactions/page.js (68 KB)
// Mirrors every UI section — filter bar, summary cards, transaction list,
// TransactionDetailPopup (bottom sheet), AddEditTransactionSheet, DeleteConfirmDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

// ─── Screen Entry Point ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fmt   = { n: Double -> CurrencyFormatter.formatBDT(n) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isLoading,
        onRefresh  = { viewModel.refresh() },
    )

    Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(Modifier.fillMaxSize()) {

            // ── Top action bar ────────────────────────────────────────────
            TopActionBar(
                onAddIncome  = { viewModel.showAddSheet("Income") },
                onAddExpense = { viewModel.showAddSheet("Expense") },
                onFilter     = { viewModel.showFilterSheet() },
                hasActiveFilter = state.filterType != "All"
                        || state.filterAccountId != "all"
                        || state.filterCategoryId != "all"
                        || state.filterStartDate.isNotBlank(),
            )

            // ── Search bar ────────────────────────────────────────────────
            SearchBar(
                query    = state.searchQuery,
                onChange = { viewModel.setSearchQuery(it) },
            )

            // ── Month summary row ─────────────────────────────────────────
            MonthlySummaryRow(
                income  = state.thisMonthIncome,
                expense = state.thisMonthExpense,
                fmt     = fmt,
            )

            // ── Transaction type quick-filter tabs ────────────────────────
            TypeFilterTabs(
                selected = state.filterType,
                onSelect = { viewModel.setFilterType(it) },
            )

            // ── Transaction list ──────────────────────────────────────────
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

    // ── Sheets & Dialogs (rendered as overlays) ───────────────────────────

    // Transaction detail popup — mirrors TransactionDetailPopup in page.js
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

    // Add / Edit sheet — mirrors the modal form in page.js
    if (state.showAddEditSheet) {
        AddEditTransactionSheet(
            editing    = state.editingTransaction,
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

    // Filter sheet
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

    // Delete confirm dialog — mirrors ConfirmDialog used in page.js
    if (state.showDeleteConfirm && state.deletingTransaction != null) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirm() },
            icon             = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title            = { Text("Delete Transaction") },
            text             = { Text("Are you sure you want to delete this transaction? This will reverse the effect on your account balance.") },
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
// Mirrors: [+ Income] [+ Expense] [Filter] action row in page.js

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopActionBar(
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    onFilter: () -> Unit,
    hasActiveFilter: Boolean,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text       = "Transactions",
            fontSize   = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onBackground,
            modifier   = Modifier.weight(1f),
        )
        // + Income
        FilledTonalButton(
            onClick = onAddIncome,
            colors  = ButtonDefaults.filledTonalButtonColors(
                containerColor = Color(0xFFF0FDF4),
                contentColor   = Color(0xFF16A34A),
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Income", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        // + Expense
        FilledTonalButton(
            onClick = onAddExpense,
            colors  = ButtonDefaults.filledTonalButtonColors(
                containerColor = Color(0xFFFFF1F2),
                contentColor   = Color(0xFFDC2626),
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Expense", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        // Filter
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
// Mirrors: thisMonthIncome / thisMonthExpense summary cards in page.js

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
// Mirrors: All / Income / Expense filter toggle in page.js

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
    // Group by date — mirrors the date-grouped rendering in page.js
    val grouped = transactions.groupBy { it.date }.entries.sortedByDescending { it.key }
    val sdf     = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val display = SimpleDateFormat("dd MMM yyyy", Locale.US)

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        grouped.forEach { (date, txs) ->
            // Date header
            item(key = "header-$date") {
                val dateLabel = runCatching { display.format(sdf.parse(date)!!) }.getOrDefault(date)
                Text(
                    text     = dateLabel,
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

// ── Single list item ──────────────────────────────────────────────────────────
// Mirrors the transaction row in page.js — icon, category color, note, account, amount

@Composable
private fun TransactionListItem(
    tx: Transaction,
    getAccountName: (String) -> String,
    getCategoryName: (String) -> String,
    getCategoryColor: (String) -> String,
    onTap: () -> Unit,
    fmt: (Double) -> String,
) {
    val catColor = runCatching {
        val hex = getCategoryColor(tx.categoryId).trimStart('#')
        Color("#$hex".toColorInt())
    }.getOrDefault(Color.Gray)

    val amountColor = if (tx.type == "Income") Color(0xFF16A34A) else Color(0xFFDC2626)
    val sign        = if (tx.type == "Income") "+" else "−"
    val catName     = getCategoryName(tx.categoryId).ifBlank { tx.type }
    val accName     = getAccountName(tx.accountId)

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
            // Category color dot
            Box(
                modifier         = Modifier.size(36.dp).background(catColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(10.dp).background(catColor, CircleShape))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = tx.description.ifBlank { catName },
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(catName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("·", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(accName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
// Mirrors: TransactionDetailPopup component in page.js — slides up from bottom

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
    val accentColor = if (transaction.type == "Income") Color(0xFF16A34A) else Color(0xFFDC2626)
    val sign        = if (transaction.type == "Income") "+" else "−"
    val sdf         = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val display     = SimpleDateFormat("d MMMM yyyy", Locale.US)
    val dateLabel   = runCatching { display.format(sdf.parse(transaction.date)!!) }.getOrDefault(transaction.date)

    ModalBottomSheet(
        onDismissRequest = onClose,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {

            // ── Colored header with amount — mirrors gradient header in TransactionDetailPopup ──
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
                        text     = transaction.description.ifBlank { getCategoryName(transaction.categoryId) },
                        fontSize = 15.sp,
                        color    = Color.White.copy(alpha = 0.85f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Detail rows — mirrors DetailRow component in page.js ──
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                DetailRow("Type",     transaction.type)
                DetailRow("Category", getCategoryName(transaction.categoryId))
                DetailRow("Account",  getAccountName(transaction.accountId))
                DetailRow("Date",     dateLabel)
                if (transaction.chargeAmount > 0) DetailRow("Charges", "${fmt(transaction.chargeAmount)}${if (transaction.chargeNote.isNotBlank()) " (${transaction.chargeNote})" else ""}", Color(0xFFF97316))
                if (transaction.isRiba)     DetailRow("⚠ Riba Flag", "This is interest income", Color(0xFFD97706))
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            // ── Action buttons ──────────────────────────────────────────
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
// Mirrors: the full transaction form modal in page.js
// Includes: type toggle, amount, account, category (with inline add), description, date, charges field

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditTransactionSheet(
    editing: Transaction?,
    accounts: List<AccountItem>,
    categories: List<CategoryItem>,
    isSaving: Boolean,
    onSave: (TransactionForm) -> Unit,
    onAddCategory: (name: String, type: String, color: String, onAdded: (CategoryItem) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by remember(editing) {
        mutableStateOf(
            if (editing != null) TransactionForm(
                type        = editing.type,
                amount      = editing.amount.toString(),
                accountId   = editing.accountId,
                categoryId  = editing.categoryId,
                description = editing.description,
                date        = editing.date,
                chargeAmount = if (editing.chargeAmount > 0) editing.chargeAmount.toString() else "",
                chargeNote  = editing.chargeNote,
            ) else TransactionForm()
        )
    }

    val isIncome    = form.type == "Income"
    val accentColor = if (isIncome) Color(0xFF16A34A) else Color(0xFFDC2626)
    val filteredCats = categories.filter { it.type == form.type }

    // Inline category form state — mirrors InlineCategoryForm in page.js
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
            // ── Sheet title ────────────────────────────────────────────
            Text(
                text       = if (editing != null) "Edit Transaction" else "Add Transaction",
                fontSize   = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
            )

            // ── Type toggle — mirrors Income/Expense tab in page.js ───
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf("Income", "Expense").forEach { type ->
                    val sel = form.type == type
                    val bg  = when { sel && type == "Income" -> Color(0xFF16A34A); sel -> Color(0xFFDC2626); else -> Color.Transparent }
                    Box(
                        modifier          = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg).clickable { form = form.copy(type = type, categoryId = "") }.padding(vertical = 12.dp),
                        contentAlignment  = Alignment.Center,
                    ) {
                        Text(type, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ── Amount field ───────────────────────────────────────────
            OutlinedTextField(
                value         = form.amount,
                onValueChange = { form = form.copy(amount = it) },
                label         = { Text("Amount (৳)") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp),
                singleLine    = true,
            )

            // ── Account dropdown ───────────────────────────────────────
            LabeledDropdown(
                label   = "Account",
                value   = accounts.find { it.id == form.accountId }?.let { "${it.name} — ${CurrencyFormatter.formatBDT(it.balance)}" } ?: "Select Account",
                options = accounts.map { it.id to "${it.name} — ${CurrencyFormatter.formatBDT(it.balance)}" },
                onSelect= { form = form.copy(accountId = it) },
            )

            // ── Category dropdown with inline add ─────────────────────
            Column {
                LabeledDropdown(
                    label    = "Category",
                    value    = filteredCats.find { it.id == form.categoryId }?.name ?: "Select Category",
                    options  = filteredCats.map { it.id to "${it.name}${if (it.isRiba) " ⚠" else ""}" } + listOf("__add_new__" to "➕ Add new category…"),
                    onSelect = {
                        if (it == "__add_new__") {
                            inlineCatName = "" // Cleared on open instead of close to fix IDE warning
                            showInlineCatForm = true
                        } else {
                            form = form.copy(categoryId = it)
                            showInlineCatForm = false
                        }
                    },
                )

                // Inline category form — mirrors InlineCategoryForm component in page.js
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
                            // Color picker row
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

            // ── Description ────────────────────────────────────────────
            OutlinedTextField(
                value         = form.description,
                onValueChange = { form = form.copy(description = it) },
                label         = { Text("Description (optional)") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp),
                maxLines      = 2,
            )

            // ── Date field ─────────────────────────────────────────────
            OutlinedTextField(
                value         = form.date,
                onValueChange = { form = form.copy(date = it) },
                label         = { Text("Date (yyyy-MM-dd)") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(10.dp),
                singleLine    = true,
                trailingIcon  = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
            )

            // ── Charges field — mirrors ChargesField component in page.js ──
            ChargesSection(
                chargeAmount = form.chargeAmount,
                chargeNote   = form.chargeNote,
                onAmountChange = { form = form.copy(chargeAmount = it) },
                onNoteChange   = { form = form.copy(chargeNote = it) },
            )

            // ── Save button ────────────────────────────────────────────
            Button(
                onClick   = { onSave(form) },
                enabled   = !isSaving && form.amount.isNotBlank() && form.accountId.isNotBlank() && form.categoryId.isNotBlank(),
                modifier  = Modifier.fillMaxWidth().height(52.dp),
                shape     = RoundedCornerShape(12.dp),
                colors    = ButtonDefaults.buttonColors(containerColor = accentColor),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…", fontSize = 15.sp)
                } else {
                    Text(if (editing != null) "Update Transaction" else "Add Transaction", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Charges section — mirrors ChargesField component in page.js ──
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
// Mirrors: filter panel / modal in page.js

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

            // Type
            FilterSection("Type") {
                listOf("All", "Income", "Expense").forEach { t ->
                    FilterChip(selected = state.filterType == t, onClick = { onApplyType(t) }, label = { Text(t, fontSize = 13.sp) }, modifier = Modifier.padding(end = 6.dp))
                }
            }

            // Account
            FilterSection("Account") {
                val opts = listOf(AccountItem(id = "all", name = "All Accounts")) + state.accounts
                LabeledDropdown(
                    label    = "Account",
                    value    = opts.find { it.id == state.filterAccountId }?.name ?: "All Accounts",
                    options  = opts.map { it.id to it.name },
                    onSelect = onApplyAccount,
                )
            }

            // Category
            FilterSection("Category") {
                val cats = listOf(CategoryItem(id = "all", name = "All Categories", type = "")) + state.categories
                LabeledDropdown(
                    label    = "Category",
                    value    = cats.find { it.id == state.filterCategoryId }?.name ?: "All Categories",
                    options  = cats.map { it.id to it.name },
                    onSelect = onApplyCategory,
                )
            }

            // Date range
            FilterSection("Date Range") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = startDate,
                        onValueChange = { startDate = it },
                        label         = { Text("From", fontSize = 12.sp) },
                        placeholder   = { Text("yyyy-MM-dd", fontSize = 12.sp) },
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(10.dp),
                        singleLine    = true,
                    )
                    OutlinedTextField(
                        value         = endDate,
                        onValueChange = { endDate = it },
                        label         = { Text("To", fontSize = 12.sp) },
                        placeholder   = { Text("yyyy-MM-dd", fontSize = 12.sp) },
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(10.dp),
                        singleLine    = true,
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

// ─── Reusable Labeled Dropdown ────────────────────────────────────────────────

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
            modifier      = Modifier.fillMaxWidth().clickable { expanded = true },
            shape         = RoundedCornerShape(10.dp),
            trailingIcon  = { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null) },
        )
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