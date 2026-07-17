package com.hasan.nisabwallet.ui.screens.admin.ledger

// Converted from: src/app/dashboard/admin/monthly-ledger/page.js
// Pairs with: MonthlyLedgerViewModel.kt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── Palette (mirrors Tailwind classes used in page.js) ───────────────────────
private val Emerald50  = Color(0xFFECFDF5)
private val Emerald100 = Color(0xFFD1FAE5)
private val Emerald200 = Color(0xFFA7F3D0)
private val Emerald400 = Color(0xFF34D399)
private val Emerald500 = Color(0xFF10B981)
private val Emerald600 = Color(0xFF059669)
private val Emerald700 = Color(0xFF047857)
private val Emerald900 = Color(0xFF064E3B)
private val Red50      = Color(0xFFFEF2F2)
private val Red200     = Color(0xFFFECACA)
private val Red300     = Color(0xFFFCA5A5)
private val Red400     = Color(0xFFF87171)
private val Red500     = Color(0xFFEF4444)
private val Red700     = Color(0xFFB91C1C)
private val Red800     = Color(0xFF991B1B)
private val Blue50     = Color(0xFFEFF6FF)
private val Blue100    = Color(0xFFDBEAFE)
private val Blue600    = Color(0xFF2563EB)
private val Blue700    = Color(0xFF1D4ED8)
private val Amber100   = Color(0xFFFEF3C7)
private val Amber700   = Color(0xFFB45309)
private val Gray50     = Color(0xFFF9FAFB)
private val Gray100    = Color(0xFFF3F4F6)
private val Gray200    = Color(0xFFE5E7EB)
private val Gray300    = Color(0xFFD1D5DB)
private val Gray400    = Color(0xFF9CA3AF)
private val Gray500    = Color(0xFF6B7280)
private val Gray700    = Color(0xFF374151)
private val Gray800    = Color(0xFF1F2937)
private val Gray900    = Color(0xFF111827)

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

// ─── Pure helpers ─────────────────────────────────────────────────────────────

private fun visibleExpenseCats(state: LedgerUiState): List<LedgerCategory> =
    state.orderedExpenseCats.filter { it.id !in state.hiddenCategoryIds }

private fun visibleIncomeCats(state: LedgerUiState): List<LedgerCategory> =
    state.incomeCategories.filter { it.id !in state.hiddenCategoryIds }

private fun expenseCatTotal(state: LedgerUiState, catId: String): Double {
    val catData = state.ledgerData.expense[catId] ?: return 0.0
    return catData.values.sumOf { rows -> rows.sumOf { it.amountDouble } }
}

private fun incomeCatTotal(state: LedgerUiState, catId: String): Double {
    return state.ledgerData.income.values.flatten()
        .filter { it.catId == catId }.sumOf { it.amountDouble }
}

private fun totalExpenses(state: LedgerUiState): Double =
    visibleExpenseCats(state).sumOf { expenseCatTotal(state, it.id) }

private fun totalIncome(state: LedgerUiState): Double =
    visibleIncomeCats(state).sumOf { incomeCatTotal(state, it.id) }

private fun daysInMonth(state: LedgerUiState): Int {
    val cal = Calendar.getInstance()
    cal.set(state.curYear, state.curMonth, 1)
    return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun isCurrentMonth(state: LedgerUiState): Boolean {
    val now = Calendar.getInstance()
    return state.curYear == now.get(Calendar.YEAR) && state.curMonth == now.get(Calendar.MONTH)
}

private fun todayDay(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

private fun getExpenseDaysToShow(state: LedgerUiState, catId: String): List<Int> {
    val all = (1..daysInMonth(state)).toList()
    if (state.showAllDates[catId] == true) return all
    val filled = mutableSetOf<Int>()
    if (isCurrentMonth(state)) filled.add(todayDay())
    state.ledgerData.expense[catId]?.forEach { (day, rows) ->
        if (rows.any { it.desc.isNotBlank() || it.amount.isNotBlank() }) filled.add(day)
    }
    return all.filter { it in filled }
}

private fun getIncomeDaysToShow(state: LedgerUiState): List<Int> {
    val all = (1..daysInMonth(state)).toList()
    if (state.showAllDates["__income__"] == true) return all
    val filled = mutableSetOf<Int>()
    if (isCurrentMonth(state)) filled.add(todayDay())
    state.ledgerData.income.forEach { (day, rows) ->
        if (rows.any { it.desc.isNotBlank() || it.amount.isNotBlank() }) filled.add(day)
    }
    return all.filter { it in filled }
}

private fun expenseRowsForDay(state: LedgerUiState, catId: String, day: Int): List<LedgerRow> {
    val stored = state.ledgerData.expense[catId]?.get(day)
    return if (!stored.isNullOrEmpty()) stored else listOf(LedgerRow(id = "phantom-exp-$catId-$day"))
}

private fun incomeRowsForDay(state: LedgerUiState, day: Int, firstIncomeCatId: String): List<LedgerRow> {
    val stored = state.ledgerData.income[day]
    return if (!stored.isNullOrEmpty()) stored
    else listOf(LedgerRow(id = "phantom-inc-$day", catId = firstIncomeCatId))
}

private fun fmtMoney(n: Double): String = CurrencyFormatter.formatBDT(n)

// ─── Screen Entry Point ────────────────────────────────────────────────────────

@Composable
fun MonthlyLedgerScreen(
    viewModel: MonthlyLedgerViewModel = hiltViewModel(),
    onNavigateToDashboard: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var incomeCollapsed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LedgerEvent.ShowToast -> scope.launch {
                    snackbarHostState.showSnackbar(event.message)
                }
                LedgerEvent.NavigateToDashboard -> onNavigateToDashboard()
            }
        }
    }

    if (state.isAuthLoading || (state.isLoading && !state.isAdmin)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Emerald600)
                Spacer(Modifier.height(12.dp))
                Text("Loading ledger...", fontSize = 13.sp, color = Gray500)
            }
        }
        return
    }

    if (!state.isAdmin) return

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LedgerHeader(
                    isSyncing = state.isSyncing,
                    isRecording = state.isRecording,
                    recordableCount = viewModel.recordableCount(),
                    onSync = { viewModel.syncFromTransactions() },
                    onRecordAll = {
                        queueAllRecordable(state, viewModel)
                        viewModel.openRowRecordModal()
                    },
                    onCategorySettings = { viewModel.showCatSettings() },
                )
            }

            item {
                MonthNavCard(
                    monthLabel = MONTHS[state.curMonth],
                    year = state.curYear,
                    daysInMonth = daysInMonth(state),
                    onPrev = { viewModel.goPrevMonth() },
                    onNext = { viewModel.goNextMonth() },
                )
            }

            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Emerald600)
                    }
                }
            } else {
                item {
                    SummaryCard(
                        totalIncome = totalIncome(state),
                        totalExpenses = totalExpenses(state),
                        visibleExpenseCats = visibleExpenseCats(state),
                        catTotal = { expenseCatTotal(state, it) },
                        budgets = state.budgets,
                    )
                }

                val visibleInc = visibleIncomeCats(state)
                if (visibleInc.isNotEmpty()) {
                    item {
                        IncomeBlockCard(
                            state = state,
                            incomeCategories = visibleInc,
                            collapsed = incomeCollapsed,
                            onToggleCollapse = { incomeCollapsed = !incomeCollapsed },
                            onToggleShowAllDates = { viewModel.toggleShowAllDates("__income__") },
                            onUpdateRow = { day, rowId, field, value -> viewModel.updateIncomeRow(day, rowId, field, value) },
                            onAddRow = { day, firstCat -> viewModel.addIncomeRow(day, firstCat) },
                            onRemoveRow = { day, rowId -> viewModel.removeIncomeRow(day, rowId) },
                            onToggleQueue = { catName, day, row -> viewModel.toggleRowQueue("__income__", catName, day, row, "income") },
                        )
                    }
                }

                val visibleExp = visibleExpenseCats(state)
                items(visibleExp, key = { it.id }) { cat ->
                    ExpenseCategoryCard(
                        state = state,
                        cat = cat,
                        total = expenseCatTotal(state, cat.id),
                        budget = state.budgets[cat.id]?.amount ?: 0.0,
                        onToggleShowAllDates = { viewModel.toggleShowAllDates(cat.id) },
                        onSetBudget = { viewModel.openBudgetModal(cat.id) },
                        onUpdateRow = { day, rowId, field, value -> viewModel.updateExpenseRow(cat.id, day, rowId, field, value) },
                        onAddRow = { day -> viewModel.addExpenseRow(cat.id, day) },
                        onRemoveRow = { day, rowId -> viewModel.removeExpenseRow(cat.id, day, rowId) },
                        onToggleQueue = { day, row -> viewModel.toggleRowQueue(cat.id, cat.name, day, row, "expense") },
                    )
                }

                if (visibleExp.isEmpty() && state.expenseCategories.isNotEmpty()) {
                    item { EmptyHint("All expense categories are hidden. Open Category Settings to show them.") }
                }
                if (state.expenseCategories.isEmpty()) {
                    item { EmptyHint("No expense categories found. Add categories in your settings first.") }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        if (state.isDirty) {
            ExtendedFloatingActionButton(
                onClick = { viewModel.saveLedger() },
                containerColor = Gray900,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (state.isSaving) "Saving…" else "Save Changes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        if (state.recordQueue.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openRowRecordModal() },
                containerColor = Emerald600,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp)
                    .padding(bottom = if (state.isDirty) 84.dp else 16.dp),
            ) {
                Icon(Icons.Default.LibraryAddCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Record ${state.recordQueue.size}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }

    if (state.showBudgetModal) {
        BudgetDialog(
            catName = state.expenseCategories.find { it.id == state.editingBudgetCatId }?.name ?: "",
            value = state.budgetInput,
            onValueChange = { viewModel.setBudgetInput(it) },
            onDismiss = { viewModel.closeBudgetModal() },
            onSave = { viewModel.saveBudget() },
        )
    }

    if (state.showCatSettingsModal) {
        CategorySettingsSheet(
            incomeCategories = state.incomeCategories,
            orderedExpenseCats = state.orderedExpenseCats,
            hiddenCategoryIds = state.hiddenCategoryIds,
            onToggleVisibility = { viewModel.toggleCategoryVisibility(it) },
            onMoveUp = { viewModel.moveCategoryUp(it) },
            onMoveDown = { viewModel.moveCategoryDown(it) },
            onDismiss = { viewModel.hideCatSettings() },
        )
    }

    if (state.showRowRecordModal) {
        RowRecordSheet(
            state = state,
            onSetAccount = { catId, accId -> viewModel.setModalAccount(catId, accId) },
            onDismiss = { viewModel.closeRowRecordModal() },
            onConfirm = { viewModel.confirmRowRecord() },
            isRecording = state.isRecording,
        )
    }
}

private fun queueAllRecordable(state: LedgerUiState, viewModel: MonthlyLedgerViewModel) {
    val visExp = visibleExpenseCats(state)
    val visInc = visibleIncomeCats(state)
    val visIncIds = visInc.map { it.id }.toSet()

    visExp.forEach { cat ->
        state.ledgerData.expense[cat.id]?.forEach { (day, rows) ->
            rows.forEach { row ->
                val key = "${cat.id}||$day||${row.id}"
                if (row.amountDouble > 0 && !row.isDone && key !in state.recordQueue) {
                    viewModel.toggleRowQueue(cat.id, cat.name, day, row, "expense")
                }
            }
        }
    }
    state.ledgerData.income.forEach { (day, rows) ->
        rows.forEach { row ->
            if (row.catId in visIncIds) {
                val key = "__income__||$day||${row.id}"
                if (row.amountDouble > 0 && !row.isDone && key !in state.recordQueue) {
                    val catName = visInc.find { it.id == row.catId }?.name ?: "Income"
                    viewModel.toggleRowQueue("__income__", catName, day, row, "income")
                }
            }
        }
    }
}

// ─── Header ─────────────────────────────────────────────────────────────────

@Composable
private fun LedgerHeader(
    isSyncing: Boolean,
    isRecording: Boolean,
    recordableCount: Int,
    onSync: () -> Unit,
    onRecordAll: () -> Unit,
    onCategorySettings: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Blue600),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.GridView, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Monthly Ledger", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Gray900)
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = Amber100) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Amber700, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Admin Only", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Amber700)
                        }
                    }
                }
                Text("Day-by-day expense & income ledger", fontSize = 11.sp, color = Gray500)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderActionChip(
                label = if (isSyncing) "Syncing…" else "Sync",
                icon = Icons.Default.Sync,
                loading = isSyncing,
                enabled = !isSyncing && !isRecording,
                containerColor = Blue50,
                contentColor = Blue700,
                onClick = onSync,
                modifier = Modifier.weight(1f),
            )
            HeaderActionChip(
                label = if (isRecording) "Recording…" else "Record All${if (recordableCount > 0) " ($recordableCount)" else ""}",
                icon = Icons.Default.LibraryAddCheck,
                loading = isRecording,
                enabled = !isRecording && !isSyncing && recordableCount > 0,
                containerColor = Emerald50,
                contentColor = Emerald700,
                onClick = onRecordAll,
                modifier = Modifier.weight(1.4f),
            )
            IconButton(
                onClick = onCategorySettings,
                modifier = Modifier.size(40.dp).border(1.dp, Gray200, RoundedCornerShape(10.dp)),
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Category Settings", tint = Gray700, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HeaderActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    loading: Boolean,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled) { onClick() },
        color = if (enabled) containerColor else Gray100,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = if (enabled) contentColor else Gray400)
            } else {
                Icon(icon, contentDescription = null, tint = if (enabled) contentColor else Gray400, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) contentColor else Gray400, maxLines = 1)
        }
    }
}

// ─── Month Nav ──────────────────────────────────────────────────────────────

@Composable
private fun MonthNavCard(monthLabel: String, year: Int, daysInMonth: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(32.dp).border(1.dp, Gray200, RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = Gray700, modifier = Modifier.size(16.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$monthLabel $year", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Gray900)
                Text("$daysInMonth days", fontSize = 11.sp, color = Gray400)
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp).border(1.dp, Gray200, RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = Gray700, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Summary Card ───────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(
    totalIncome: Double,
    totalExpenses: Double,
    visibleExpenseCats: List<LedgerCategory>,
    catTotal: (String) -> Double,
    budgets: Map<String, Budget>,
) {
    val remaining = totalIncome - totalExpenses
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "MONTHLY SUMMARY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gray900,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider(color = Gray100)
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryPill(Modifier.weight(1f), "Income", fmtMoney(totalIncome), Icons.AutoMirrored.Filled.TrendingUp, Emerald600, Emerald50)
                SummaryPill(Modifier.weight(1f), "Expenses", fmtMoney(totalExpenses), Icons.AutoMirrored.Filled.TrendingDown, Red500, Red50)
                SummaryPill(
                    Modifier.weight(1f), "Remaining", fmtMoney(remaining), Icons.Default.AccountBalanceWallet,
                    if (remaining >= 0) Blue600 else Amber700, if (remaining >= 0) Blue50 else Amber100,
                )
            }
            if (visibleExpenseCats.isNotEmpty()) {
                Text(
                    "EXPENSES BY CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gray500,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
                )
                FlowRowSimple(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()) {
                    visibleExpenseCats.forEach { cat ->
                        val total = catTotal(cat.id)
                        val budget = budgets[cat.id]?.amount ?: 0.0
                        val over = budget > 0 && total > budget
                        val (bg, fg) = when {
                            over -> Red50 to Red700
                            total > 0 -> Gray100 to Gray700
                            else -> Gray50 to Gray400
                        }
                        Surface(shape = RoundedCornerShape(50), color = bg, modifier = Modifier.padding(bottom = 8.dp, end = 8.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(cat.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg)
                                Spacer(Modifier.width(4.dp))
                                Text(fmtMoney(total), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
                                if (budget > 0) {
                                    Text(" / ${fmtMoney(budget)}", fontSize = 10.sp, color = if (over) Red400 else Gray400)
                                }
                                if (over) Text(" ↑", fontSize = 10.sp, color = fg)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SummaryPill(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, bg: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = bg, border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.15f))) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = accent)
            }
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSimple(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier = modifier) { content() }
}

@Composable
private fun EmptyHint(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Gray200),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, fontSize = 12.sp, color = Gray400, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(28.dp))
    }
}

// ─── Income Block ───────────────────────────────────────────────────────────

@Composable
private fun IncomeBlockCard(
    state: LedgerUiState,
    incomeCategories: List<LedgerCategory>,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onToggleShowAllDates: () -> Unit,
    onUpdateRow: (Int, String, String, String) -> Unit,
    onAddRow: (Int, String) -> Unit,
    onRemoveRow: (Int, String) -> Unit,
    onToggleQueue: (String, Int, LedgerRow) -> Unit,
) {
    val total = totalIncome(state)
    val showAll = state.showAllDates["__income__"] == true
    val days = getIncomeDaysToShow(state)
    val firstIncomeCatId = incomeCategories.firstOrNull()?.id ?: ""
    val isCurMonth = isCurrentMonth(state)
    val today = todayDay()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald200),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(Emerald50).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onToggleCollapse() },
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Emerald600, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Income", fontWeight = FontWeight.Bold, color = Emerald900, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = Emerald100) {
                        Text(fmtMoney(total), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = null, tint = Emerald700, modifier = Modifier.size(16.dp),
                    )
                }
                ShowAllDatesToggle(showAll = showAll, onClick = onToggleShowAllDates, activeColor = Emerald600, inactiveColor = Emerald100, inactiveText = Emerald700)
            }

            if (!collapsed) {
                if (days.isEmpty()) {
                    Text(
                        "No income entries yet — tap \"All dates\" to start filling.",
                        fontSize = 11.sp, color = Gray300, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                    )
                } else {
                    // Table Header
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Gray50).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Date", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, modifier = Modifier.width(40.dp))
                        Text("Category", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(4.dp))
                        Text("Description", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, modifier = Modifier.weight(1f))
                        Text("Amount", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, modifier = Modifier.width(76.dp), textAlign = TextAlign.End)
                        Spacer(Modifier.width(60.dp))
                    }

                    Column(Modifier.padding(vertical = 4.dp)) {
                        days.forEach { day ->
                            val rows = incomeRowsForDay(state, day, firstIncomeCatId)
                            val isToday = isCurMonth && day == today
                            rows.forEachIndexed { ri, row ->
                                IncomeRowEditor(
                                    day = day,
                                    isToday = isToday,
                                    isFirst = ri == 0,
                                    row = row,
                                    categories = incomeCategories,
                                    isLast = ri == rows.lastIndex,
                                    hasMultiple = rows.size > 1,
                                    inQueue = "__income__||$day||${row.id}" in state.recordQueue,
                                    onDesc = { onUpdateRow(day, row.id, "desc", it) },
                                    onAmount = { onUpdateRow(day, row.id, "amount", it) },
                                    onCategory = { onUpdateRow(day, row.id, "catId", it) },
                                    onAdd = { onAddRow(day, firstIncomeCatId) },
                                    onRemove = { onRemoveRow(day, row.id) },
                                    onToggleQueue = {
                                        val catName = incomeCategories.find { it.id == row.catId }?.name ?: "Income"
                                        onToggleQueue(catName, day, row)
                                    },
                                )
                            }
                        }
                    }
                }
                if (total > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Emerald900).padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Total Income", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(fmtMoney(total), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Emerald200)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowAllDatesToggle(showAll: Boolean, onClick: () -> Unit, activeColor: Color, inactiveColor: Color, inactiveText: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (showAll) activeColor else inactiveColor,
        modifier = Modifier.clickable { onClick() },
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (showAll) "Filled only" else "All dates", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (showAll) Color.White else inactiveText)
            Spacer(Modifier.width(4.dp))
            Icon(
                if (showAll) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null, tint = if (showAll) Color.White else inactiveText, modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun IncomeRowEditor(
    day: Int,
    isToday: Boolean,
    isFirst: Boolean,
    row: LedgerRow,
    categories: List<LedgerCategory>,
    isLast: Boolean,
    hasMultiple: Boolean,
    inQueue: Boolean,
    onDesc: (String) -> Unit,
    onAmount: (String) -> Unit,
    onCategory: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onToggleQueue: () -> Unit,
) {
    val hasAmount = row.amountDouble > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isToday) Emerald50.copy(alpha = 0.4f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(40.dp)) {
            if (isFirst) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isToday) Emerald500 else Gray100
                ) {
                    Text(
                        text = day.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) Color.White else Gray500,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        CompactCategoryDropdown(
            categories = categories,
            selectedId = row.catId,
            onSelect = onCategory,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))

        DenseTextField(value = row.desc, onValueChange = onDesc, placeholder = "Desc...", modifier = Modifier.weight(1f))
        Spacer(Modifier.width(4.dp))

        DenseTextField(value = row.amount, onValueChange = onAmount, placeholder = "0", modifier = Modifier.width(76.dp), numeric = true, alignEnd = true)
        Spacer(Modifier.width(4.dp))

        RowActions(
            modifier = Modifier.width(60.dp),
            hasAmount = hasAmount, done = row.isDone, inQueue = inQueue,
            isLast = isLast, hasMultiple = hasMultiple,
            onToggleQueue = onToggleQueue, onAdd = onAdd, onRemove = onRemove
        )
    }
}

@Composable
private fun CompactCategoryDropdown(
    categories: List<LedgerCategory>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val catName = categories.find { it.id == selectedId }?.name ?: "Select…"
    
    Box(modifier = modifier) {
        OutlinedTextField(
            value = catName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
            textStyle = TextStyle(fontSize = 12.sp, color = if (selectedId.isBlank()) Gray400 else Gray800),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(42.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Emerald600,
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.White,
            ),
            shape = RoundedCornerShape(6.dp),
        )
        Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.name, fontSize = 13.sp) }, onClick = { onSelect(c.id); expanded = false })
            }
        }
    }
}

// ─── Expense Category Block ─────────────────────────────────────────────────

@Composable
private fun ExpenseCategoryCard(
    state: LedgerUiState,
    cat: LedgerCategory,
    total: Double,
    budget: Double,
    onToggleShowAllDates: () -> Unit,
    onSetBudget: () -> Unit,
    onUpdateRow: (Int, String, String, String) -> Unit,
    onAddRow: (Int) -> Unit,
    onRemoveRow: (Int, String) -> Unit,
    onToggleQueue: (Int, LedgerRow) -> Unit,
) {
    val over = budget > 0 && total > budget
    val pct = if (budget > 0) (total / budget * 100.0).coerceAtMost(100.0).toInt() else 0
    val showAll = state.showAllDates[cat.id] == true
    val days = getExpenseDaysToShow(state, cat.id)
    val isCurMonth = isCurrentMonth(state)
    val today = todayDay()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (over) Red300 else Gray200),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(if (over) Red50 else Gray50).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(cat.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (over) Red800 else Gray800, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = if (over) Red200 else if (total > 0) Blue100 else Gray100) {
                        Text(fmtMoney(total), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (over) Red700 else if (total > 0) Blue700 else Gray400, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                    if (budget > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text("/ ${fmtMoney(budget)}", fontSize = 10.sp, color = if (over) Red500 else Gray400, maxLines = 1)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSetBudget, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.TrackChanges, contentDescription = "Set budget", tint = if (budget > 0) Blue600 else Gray400, modifier = Modifier.size(15.dp))
                    }
                    ShowAllDatesToggle(
                        showAll = showAll, onClick = onToggleShowAllDates,
                        activeColor = Blue600,
                        inactiveColor = if (over) Red200 else Gray200,
                        inactiveText = if (over) Red700 else Gray700,
                    )
                }
            }

            if (budget > 0) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                    LinearProgressIndicator(
                        progress = { (pct / 100f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (over) Red400 else Blue600,
                        trackColor = Gray100,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$pct% used", fontSize = 9.sp, color = Gray400)
                        if (over) Text("Over by ${fmtMoney(total - budget)}", fontSize = 9.sp, color = Red500, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (days.isEmpty()) {
                Text(
                    "No entries yet — tap All dates to show all days.",
                    fontSize = 11.sp, color = Gray300, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            } else {
                // Table Header
                Row(
                    modifier = Modifier.fillMaxWidth().background(Gray50).border(androidx.compose.foundation.BorderStroke(0.5.dp, Gray200)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, modifier = Modifier.width(40.dp))
                    Text("Description", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, modifier = Modifier.weight(1f))
                    Text("Amount", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, modifier = Modifier.width(76.dp), textAlign = TextAlign.End)
                    Spacer(Modifier.width(60.dp))
                }

                Column(Modifier.padding(vertical = 4.dp)) {
                    days.forEach { day ->
                        val rows = expenseRowsForDay(state, cat.id, day)
                        val isToday = isCurMonth && day == today
                        val daySum = rows.sumOf { it.amountDouble }
                        
                        rows.forEachIndexed { ri, row ->
                            ExpenseRowEditor(
                                day = day,
                                isToday = isToday,
                                isFirst = ri == 0,
                                showDaySum = ri == rows.lastIndex && daySum > 0 && rows.size > 1,
                                daySum = daySum,
                                row = row,
                                isLast = ri == rows.lastIndex,
                                hasMultiple = rows.size > 1,
                                inQueue = "${cat.id}||$day||${row.id}" in state.recordQueue,
                                onDesc = { onUpdateRow(day, row.id, "desc", it) },
                                onAmount = { onUpdateRow(day, row.id, "amount", it) },
                                onAdd = { onAddRow(day) },
                                onRemove = { onRemoveRow(day, row.id) },
                                onToggleQueue = { onToggleQueue(day, row) },
                            )
                        }
                    }
                }
            }

            if (total > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Gray900).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Total", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(fmtMoney(total), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald400)
                }
            }
        }
    }
}

@Composable
private fun ExpenseRowEditor(
    day: Int,
    isToday: Boolean,
    isFirst: Boolean,
    showDaySum: Boolean,
    daySum: Double,
    row: LedgerRow,
    isLast: Boolean,
    hasMultiple: Boolean,
    inQueue: Boolean,
    onDesc: (String) -> Unit,
    onAmount: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onToggleQueue: () -> Unit,
) {
    val hasAmount = row.amountDouble > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isToday) Blue50.copy(alpha = 0.3f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(40.dp)) {
            if (isFirst) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isToday) Blue600 else Gray100
                ) {
                    Text(
                        text = day.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) Color.White else Gray500,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (showDaySum) {
                Text(fmtMoney(daySum), fontSize = 9.sp, color = Gray400, modifier = Modifier.padding(top = 2.dp))
            }
        }

        DenseTextField(value = row.desc, onValueChange = onDesc, placeholder = "Description...", modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        DenseTextField(value = row.amount, onValueChange = onAmount, placeholder = "0", modifier = Modifier.width(76.dp), numeric = true, alignEnd = true)
        Spacer(Modifier.width(2.dp))

        RowActions(
            modifier = Modifier.width(60.dp),
            hasAmount = hasAmount, done = row.isDone, inQueue = inQueue,
            isLast = isLast, hasMultiple = hasMultiple,
            onToggleQueue = onToggleQueue, onAdd = onAdd, onRemove = onRemove
        )
    }
}

@Composable
private fun RowActions(
    modifier: Modifier = Modifier,
    hasAmount: Boolean,
    done: Boolean,
    inQueue: Boolean,
    isLast: Boolean,
    hasMultiple: Boolean,
    onToggleQueue: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        if (hasAmount) {
            if (done) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Recorded", tint = Emerald600, modifier = Modifier.size(16.dp))
            } else {
                IconButton(onClick = onToggleQueue, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Default.LibraryAddCheck, contentDescription = "Queue for recording",
                        tint = if (inQueue) Emerald600 else Gray300, modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        if (isLast) {
            IconButton(onClick = onAdd, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add row", tint = Gray300, modifier = Modifier.size(14.dp))
            }
        }
        if (hasMultiple) {
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove row", tint = Gray300, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// Compact underline-ish text field
@Composable
private fun DenseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    alignEnd: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(42.dp),
        placeholder = { Text(placeholder, fontSize = 12.sp, color = Gray300) },
        textStyle = TextStyle(fontSize = 12.sp, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start, fontWeight = if (numeric) FontWeight.Medium else FontWeight.Normal),
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Blue600,
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.White,
        ),
        shape = RoundedCornerShape(6.dp),
    )
}

// ─── Budget Dialog ──────────────────────────────────────────────────────────

@Composable
private fun BudgetDialog(catName: String, value: String, onValueChange: (String) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Budget", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(buildString { append("Monthly budget for "); append(catName) }, fontSize = 13.sp, color = Gray500)
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = Blue50, border = androidx.compose.foundation.BorderStroke(1.dp, Blue100)) {
                    Text(
                        "This updates the shared budgets page and carries forward to next month automatically.",
                        fontSize = 11.sp, color = Blue700, modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text("Enter budget amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Blue700),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = Blue600)) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Budget")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─── Category Settings Sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySettingsSheet(
    incomeCategories: List<LedgerCategory>,
    orderedExpenseCats: List<LedgerCategory>,
    hiddenCategoryIds: Set<String>,
    onToggleVisibility: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Blue600, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Category Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Text(
                "Toggle visibility and reorder expense categories with the arrows. Income categories can be hidden but not reordered.",
                fontSize = 11.sp, color = Gray500,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 4.dp),
            )

            if (incomeCategories.isNotEmpty()) {
                Text("INCOME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald600, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp))
                incomeCategories.forEach { cat ->
                    val hidden = cat.id in hiddenCategoryIds
                    CategoryVisibilityRow(
                        name = cat.name, hidden = hidden,
                        dotColor = if (hidden) Gray200 else Emerald400,
                        bg = if (hidden) Gray50 else Emerald50,
                        textColor = if (hidden) Gray400 else Emerald900,
                        onClick = { onToggleVisibility(cat.id) },
                    )
                }
            }

            if (orderedExpenseCats.isNotEmpty()) {
                Text("EXPENSES  (use arrows to reorder)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Red500, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp))
                orderedExpenseCats.forEachIndexed { idx, cat ->
                    val hidden = cat.id in hiddenCategoryIds
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 3.dp, bottom = 3.dp)
                            .clip(RoundedCornerShape(10.dp)).background(if (hidden) Gray50 else Red50)
                            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            IconButton(onClick = { onMoveUp(idx) }, enabled = idx != 0, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", tint = if (idx != 0) Red500 else Gray200, modifier = Modifier.size(12.dp))
                            }
                            IconButton(onClick = { onMoveDown(idx) }, enabled = idx != orderedExpenseCats.lastIndex, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", tint = if (idx != orderedExpenseCats.lastIndex) Red500 else Gray200, modifier = Modifier.size(12.dp))
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (hidden) Gray200 else Red400))
                        Spacer(Modifier.width(8.dp))
                        Text(cat.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (hidden) Gray400 else Red800, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onToggleVisibility(cat.id) }, modifier = Modifier.size(26.dp)) {
                            Icon(
                                if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility",
                                tint = if (hidden) Gray300 else Red400, modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Gray900),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            ) { Text("Done", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun CategoryVisibilityRow(name: String, hidden: Boolean, dotColor: Color, bg: Color, textColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(10.dp)).background(bg).clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(8.dp))
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
        }
        Icon(
            if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = null, tint = if (hidden) Gray300 else Emerald600, modifier = Modifier.size(15.dp),
        )
    }
}

// ─── Row Record Sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowRecordSheet(
    state: LedgerUiState,
    onSetAccount: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isRecording: Boolean,
) {
    val queuedCatIds = state.recordQueue.values.map { it.catId }.distinct()
    val allSelected = queuedCatIds.all { state.modalAccountsPerCat[it]?.isNotBlank() == true }

    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LibraryAddCheck, contentDescription = null, tint = Emerald600, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Record Selected Entries", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = Emerald100) {
                        Text("${state.recordQueue.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald700, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
            Text(
                "Choose which account each category should be charged to. All entries within a category share the same account.",
                fontSize = 11.sp, color = Gray500, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 4.dp),
            )

            LazyColumn(modifier = Modifier.heightIn(max = 380.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(queuedCatIds) { catId ->
                    val items = state.recordQueue.values.filter { it.catId == catId }
                    val catName = items.firstOrNull()?.catName ?: catId
                    val catTotal = items.sumOf { it.amount }
                    val isIncome = items.firstOrNull()?.type == "income"

                    Surface(shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (isIncome) Emerald200 else Gray200)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(if (isIncome) Emerald50 else Gray50).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (isIncome) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null, tint = if (isIncome) Emerald600 else Red500, modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(catName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Gray800, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(50), color = if (isIncome) Emerald100 else Red50) {
                                    Text(fmtMoney(catTotal), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (isIncome) Emerald700 else Red700, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                                }
                            }
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                items.forEach { item ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            "Day ${item.day}" + if (item.desc.isNotBlank()) " · ${item.desc}" else "",
                                            fontSize = 11.sp, color = Gray500, maxLines = 1,
                                        )
                                        Text(fmtMoney(item.amount), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gray800)
                                    }
                                }
                            }
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                                Text(
                                    if (isIncome) "DEPOSIT TO ACCOUNT" else "CHARGE TO ACCOUNT",
                                    fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Gray400,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                                AccountDropdown(
                                    accounts = state.accounts,
                                    selectedId = state.modalAccountsPerCat[catId] ?: "",
                                    onSelect = { onSetAccount(catId, it) },
                                    accentBorder = if (isIncome) Emerald200 else Gray200,
                                )
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = onConfirm,
                    enabled = !isRecording && allSelected,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isRecording) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.LibraryAddCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Confirm & Record", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDropdown(accounts: List<LedgerAccount>, selectedId: String, onSelect: (String) -> Unit, accentBorder: Color) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.find { it.id == selectedId }
    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, accentBorder),
            color = Color.White,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected?.let { "${it.name} — ${fmtMoney(it.balance)}" } ?: "Select account…",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (selected != null) Gray800 else Gray400,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Gray400, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = { Text("${acc.name} — ${fmtMoney(acc.balance)}", fontSize = 13.sp) },
                    onClick = { onSelect(acc.id); expanded = false },
                )
            }
        }
    }
}