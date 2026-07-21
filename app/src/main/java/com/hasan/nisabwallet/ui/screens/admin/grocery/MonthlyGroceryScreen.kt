package com.hasan.nisabwallet.ui.screens.admin.grocery

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ─── Palette ───────────────────────────────────────────────────────────────
private val Emerald50  = Color(0xFFECFDF5)
private val Emerald100 = Color(0xFFD1FAE5)
private val Emerald400 = Color(0xFF34D399)
private val Emerald500 = Color(0xFF10B981)
private val Emerald600 = Color(0xFF059669)
private val Emerald700 = Color(0xFF047857)
private val Emerald800 = Color(0xFF065F46)
private val Violet50   = Color(0xFFF5F3FF)
private val Violet100  = Color(0xFFEDE9FE)
private val Violet400  = Color(0xFFA78BFA)
private val Violet600  = Color(0xFF7C3AED)
private val Violet700  = Color(0xFF6D28D9)
private val Amber50    = Color(0xFFFFFBEB)
private val Amber100   = Color(0xFFFEF3C7)
private val Amber700   = Color(0xFFB45309)
private val Red600     = Color(0xFFDC2626)
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

private val VALID_UNITS = listOf("pcs", "kg", "g", "litre", "ml", "pack", "dozen", "bag", "bottle", "box", "can", "bunch")

private fun fmt(amount: Double) = CurrencyFormatter.formatBDT(amount)

private fun categoryLabel(catId: String, categories: List<GroceryCategoryItem>): String =
    if (catId.isBlank()) "Uncategorized" else categories.find { it.id == catId }?.name ?: catId

private data class GroceryGroup(val catId: String, val catName: String, val rows: List<GroceryRow>)

private fun groupRows(rows: List<GroceryRow>, categories: List<GroceryCategoryItem>): List<GroceryGroup> {
    val groups = rows.groupBy { it.category }
    return groups.map { (catId, groupRows) -> GroceryGroup(catId, categoryLabel(catId, categories), groupRows) }
        .sortedWith(compareBy({ it.catId.isBlank() }, { it.catName }))
}

private fun GroceryMonthItem.effectivePrice(): Double = boughtPrice ?: (qty * unitPrice)

private fun monthLabelFor(ym: String): String {
    val parts = ym.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: return ym
    val m = parts.getOrNull(1)?.toIntOrNull()?.minus(1) ?: return ym
    val name = MONTHS.getOrNull(m) ?: return ym
    return "$name $y"
}

@Composable
fun MonthlyGroceryScreen(
    viewModel: MonthlyGroceryViewModel = hiltViewModel(),
    triggerFabAdd: Long = 0L,
    onAddHandled: () -> Unit = {},
    onNavigateToDashboard: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(triggerFabAdd) {
        if (triggerFabAdd > 0L) {
            viewModel.openAddItemModal()
            onAddHandled()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GroceryEvent.ShowToast -> scope.launch { snackbarHostState.showSnackbar(event.message) }
                GroceryEvent.NavigateToDashboard -> onNavigateToDashboard()
            }
        }
    }

    if (state.isAuthLoading || (state.isLoading && !state.isAdmin)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Emerald600)
                Spacer(Modifier.height(12.dp))
                Text("Loading grocery planner...", fontSize = 13.sp, color = Gray500)
            }
        }
        return
    }

    if (!state.isAdmin) return

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Gray50,
        topBar = {
            Surface(color = Gray50, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(48.dp)) // Clears the hamburger menu
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Monthly Grocery", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gray900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(50), color = Amber100) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = Amber700, modifier = Modifier.size(10.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Admin Only", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Amber700)
                                }
                            }
                        }
                        Text("Plan, track and record monthly grocery", fontSize = 12.sp, color = Gray500)
                    }
                }
            }
        },
        floatingActionButton = {
            if (state.activeTab == "planner") {
                val recordable = state.rows.count { it.curBought && !it.curRecorded && !it.archived }
                if (recordable > 0) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.openConfirmModal() },
                        containerColor = Gray900,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Record ($recordable)", fontWeight = FontWeight.SemiBold) },
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    GroceryTabs(activeTab = state.activeTab, onSelect = { viewModel.setActiveTab(it) })
                }

                if (state.activeTab == "planner") {
                    item {
                        MonthNavRow(
                            state = state,
                            onPrev = { viewModel.goPrevMonth() },
                            onNext = { viewModel.goNextMonth() },
                            onSave = { viewModel.saveMonth() },
                        )
                    }

                    item {
                        SummaryPills(state = state, viewModel = viewModel)
                    }

                    item {
                        FilterBar(state = state, viewModel = viewModel)
                    }

                    if (state.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Emerald600)
                            }
                        }
                    } else if (state.items.isEmpty()) {
                        item { EmptyState(onAddItem = { viewModel.openAddItemModal() }) }
                    } else {
                        val filtered = viewModel.filteredRows(state)
                        if (filtered.isEmpty()) {
                            item { EmptyHint("No items match your filters.") }
                        } else {
                            val groups = groupRows(filtered, state.expenseCategories)
                            groups.forEach { group ->
                                item(key = "cat_${group.catId}") {
                                    CategoryGroupCard(
                                        group = group,
                                        onOpenGroupModal = { viewModel.openGroupModal(group.catId) },
                                        onToggleBought = { viewModel.toggleBought(it) },
                                        onQtyChange = { id, v -> viewModel.updateRowQty(id, v) },
                                        onUnitPriceChange = { id, v -> viewModel.updateRowUnitPrice(id, v) },
                                        onBoughtPriceChange = { id, v -> viewModel.updateRowBoughtPrice(id, v) },
                                        onEdit = { row ->
                                            state.items.find { it.id == row.itemId }?.let { viewModel.openEditItemModal(it) }
                                        },
                                        onArchive = { viewModel.archiveItem(it) },
                                        onDelete = { viewModel.deleteItem(it) },
                                        onReverse = { viewModel.openReverseModal(it) },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item { HistorySection(state = state) }
                }
            }
        }

        if (state.showAddItemModal) {
            AddEditItemSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { viewModel.closeAddItemModal() },
            )
        }

        if (state.showConfirmModal) {
            ConfirmRecordSheet(state = state, viewModel = viewModel, onDismiss = { viewModel.closeConfirmModal() })
        }

        if (state.groupModal.show) {
            GroupPriceSheet(state = state, viewModel = viewModel)
        }

        if (state.showReverseModal) {
            ReverseConfirmDialog(
                onDismiss = { viewModel.closeReverseModal() },
                onConfirm = { viewModel.reverseRecord() },
                isReversing = state.isReversing,
            )
        }
    }
}

// ─── Tabs ───────────────────────────────────────────────────────────────────

@Composable
private fun GroceryTabs(activeTab: String, onSelect: (String) -> Unit) {
    val tabs = listOf("planner" to ("Planner" to Icons.Default.ShoppingCart), "history" to ("History" to Icons.Default.History))
    Row(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Gray100).padding(4.dp)) {
        tabs.forEach { (id, pair) ->
            val (label, icon) = pair
            val selected = activeTab == id
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onSelect(id) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) Gray900 else Gray500, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected) Gray900 else Gray500)
            }
        }
    }
}

// ─── Month nav ──────────────────────────────────────────────────────────────

@Composable
private fun MonthNavRow(state: GroceryUiState, onPrev: () -> Unit, onNext: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().border(1.dp, Gray200, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, modifier = Modifier.size(32.dp).border(1.dp, Gray200, RoundedCornerShape(8.dp))) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = Gray700, modifier = Modifier.size(16.dp)) }
            Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("${MONTHS[state.curMonth]} ${state.curYear}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gray900) }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp).border(1.dp, Gray200, RoundedCornerShape(8.dp))) { Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = Gray700, modifier = Modifier.size(16.dp)) }
        }

        val dirty = state.dirtyItemIds.size
        Row(
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (dirty > 0) Emerald600 else Gray50).border(1.dp, if (dirty > 0) Emerald600 else Gray200, RoundedCornerShape(8.dp)).clickable(enabled = dirty > 0 && !state.isSaving) { onSave() }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Color.White)
            else Icon(Icons.Default.CloudUpload, contentDescription = null, tint = if (dirty > 0) Color.White else Gray300, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text = if (state.isSaving) "Saving…" else if (dirty > 0) "Save ($dirty)" else "Saved", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (dirty > 0) Color.White else Gray400)
        }
    }
}

// ─── Summary pills ──────────────────────────────────────────────────────────

@Composable
private fun SummaryPills(state: GroceryUiState, viewModel: MonthlyGroceryViewModel) {
    val bought = viewModel.boughtItems(state)
    val total = viewModel.totalItems(state)
    val estimated = viewModel.totalEstimated(state)
    val spent = viewModel.totalSpent(state)
    val recorded = state.rows.count { it.curRecorded }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Pill(icon = Icons.Default.CheckBox, label = "$bought/$total bought", value = fmt(spent), bg = Emerald50, fg = Emerald700) }
        item { Pill(icon = Icons.Default.CheckBoxOutlineBlank, label = "Estimated", value = fmt(estimated), bg = Amber50, fg = Amber700) }
        if (recorded > 0) item { Pill(icon = Icons.Default.Check, label = "Recorded", value = "$recorded items", bg = Emerald100, fg = Emerald800) }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, value: String, bg: Color, fg: Color) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Column {
            Text(label, fontSize = 9.sp, color = fg, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 12.sp, color = fg, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Filter bar ─────────────────────────────────────────────────────────────

@Composable
private fun FilterBar(state: GroceryUiState, viewModel: MonthlyGroceryViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search items...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            textStyle = TextStyle(fontSize = 13.sp),
            shape = RoundedCornerShape(10.dp),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                SegmentedFilter(
                    options = listOf("all" to "All", "bought" to "✓ Bought", "not_bought" to "Pending"),
                    selected = state.filterBought,
                    onSelect = { viewModel.setFilterBought(it) },
                )
            }

            item {
                CategoryFilterDropdown(
                    categories = state.expenseCategories,
                    selectedCategory = state.filterCategory,
                    onSelectCategory = { viewModel.setFilterCategory(it) }
                )
            }

            val recordedCount = state.rows.count { it.curRecorded }
            if (recordedCount > 0) {
                item {
                    ToggleChip(
                        label = if (state.showRecorded) "Hide recorded" else "Show recorded ($recordedCount)",
                        active = state.showRecorded,
                        activeBg = Emerald100, activeFg = Emerald800,
                        onClick = { viewModel.toggleShowRecorded() },
                    )
                }
            }

            val archivedCount = state.rows.count { it.archived }
            if (archivedCount > 0) {
                item {
                    ToggleChip(
                        label = if (state.showArchived) "Hide archived" else "Archived ($archivedCount)",
                        active = state.showArchived,
                        activeBg = Amber100, activeFg = Amber700,
                        onClick = { viewModel.toggleShowArchived() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterDropdown(
    categories: List<GroceryCategoryItem>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedLabel = when (selectedCategory) {
        "all" -> "All Categories"
        "" -> "Uncategorized"
        else -> categories.find { it.id == selectedCategory }?.name ?: "All Categories"
    }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selectedCategory != "all") Violet50 else Color.White)
                .border(1.dp, if (selectedCategory != "all") Violet400 else Gray200, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.Category, contentDescription = null, tint = if (selectedCategory != "all") Violet700 else Gray500, modifier = Modifier.size(13.dp))
            Text(
                text = selectedLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (selectedCategory != "all") Violet700 else Gray500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = if (selectedCategory != "all") Violet700 else Gray500, modifier = Modifier.size(14.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            DropdownMenuItem(
                text = { Text("All Categories", fontSize = 12.sp, fontWeight = if (selectedCategory == "all") FontWeight.Bold else FontWeight.Normal) },
                onClick = {
                    onSelectCategory("all")
                    expanded = false
                }
            )
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name, fontSize = 12.sp, fontWeight = if (selectedCategory == cat.id) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        onSelectCategory(cat.id)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Uncategorized", fontSize = 12.sp, fontWeight = if (selectedCategory == "") FontWeight.Bold else FontWeight.Normal) },
                onClick = {
                    onSelectCategory("")
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun SegmentedFilter(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Gray100).padding(3.dp)) {
        options.forEach { (value, label) ->
            val isSel = selected == value
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSel) Gray900 else Gray500,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isSel) Color.White else Color.Transparent).clickable { onSelect(value) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ToggleChip(label: String, active: Boolean, activeBg: Color, activeFg: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (active) activeBg else Color.White).border(1.dp, if (active) activeFg.copy(alpha = 0.3f) else Gray200, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (active) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = if (active) activeFg else Gray400, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (active) activeFg else Gray500)
    }
}

// ─── Empty states ───────────────────────────────────────────────────────────

@Composable
private fun EmptyState(onAddItem: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().border(1.dp, Gray200, RoundedCornerShape(14.dp)).padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Inventory2, contentDescription = null, tint = Gray300, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text("No grocery items yet", fontWeight = FontWeight.SemiBold, color = Gray700, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text("Add items to your master list to start planning", fontSize = 12.sp, color = Gray400, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddItem, colors = ButtonDefaults.buttonColors(containerColor = Emerald600), shape = RoundedCornerShape(10.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add First Item")
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().border(1.dp, Gray200, RoundedCornerShape(12.dp)).padding(20.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, color = Gray500, textAlign = TextAlign.Center)
    }
}

// ─── Spreadsheet Table Layout ───────────────────────────────────────────────

@Composable
private fun CategoryGroupCard(
    group: GroceryGroup,
    onOpenGroupModal: () -> Unit,
    onToggleBought: (String) -> Unit,
    onQtyChange: (String, String) -> Unit,
    onUnitPriceChange: (String, String) -> Unit,
    onBoughtPriceChange: (String, String) -> Unit,
    onEdit: (GroceryRow) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReverse: (String) -> Unit,
) {
    val catTotalEst = group.rows.sumOf { it.curQty * it.curUnitPrice }
    val catTotalBought = group.rows.filter { it.curBought }.sumOf { it.effectivePrice }

    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Gray200, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Gray50, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(group.catName.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gray700)
            Row(
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Violet100).clickable { onOpenGroupModal() }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Layers, contentDescription = null, tint = Violet700, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text("Group ৳", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Violet700)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Gray50).border(BorderStroke(0.5.dp, Gray200)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(28.dp))
            Text("Item", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, modifier = Modifier.weight(1f))
            Text("Qty", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, modifier = Modifier.width(46.dp), textAlign = TextAlign.Center)
            Text("Price", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
            Text("Paid/Ref", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
            Spacer(Modifier.width(24.dp))
        }

        Column(Modifier.padding(vertical = 4.dp)) {
            group.rows.forEach { row ->
                GroceryRowItem(
                    row = row,
                    onToggleBought = { onToggleBought(row.itemId) },
                    onQtyChange = { onQtyChange(row.itemId, it) },
                    onUnitPriceChange = { onUnitPriceChange(row.itemId, it) },
                    onBoughtPriceChange = { onBoughtPriceChange(row.itemId, it) },
                    onEdit = { onEdit(row) },
                    onArchive = { onArchive(row.itemId) },
                    onDelete = { onDelete(row.itemId) },
                    onReverse = { onReverse(row.itemId) },
                )
            }
        }

        if (group.rows.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Gray900).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${fmt(catTotalBought)} / ${fmt(catTotalEst)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald400)
            }
        }
    }
}

@Composable
private fun GroceryRowItem(
    row: GroceryRow,
    onToggleBought: () -> Unit,
    onQtyChange: (String) -> Unit,
    onUnitPriceChange: (String) -> Unit,
    onBoughtPriceChange: (String) -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onReverse: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var qtyField by remember(row.curQty) { mutableStateOf(formatNum(row.curQty)) }
    var priceField by remember(row.curUnitPrice) { mutableStateOf(formatNum(row.curUnitPrice)) }
    var boughtPriceField by remember(row.curBoughtPrice) { mutableStateOf(row.curBoughtPrice?.let { formatNum(it) } ?: "") }

    val curTotal = row.curQty * row.curUnitPrice

    Row(
        modifier = Modifier.fillMaxWidth().background(if (row.curBought) Emerald50.copy(alpha = 0.4f) else Color.Transparent).padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(if (row.curBought) Emerald500 else Color.Transparent).border(1.5.dp, if (row.curBought) Emerald500 else Gray300, RoundedCornerShape(4.dp)).clickable(enabled = !row.curRecorded) { onToggleBought() },
                contentAlignment = Alignment.Center,
            ) {
                if (row.curBought) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = row.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (row.archived) Gray400 else if (row.curBought) Emerald800 else Gray800,
                textDecoration = if (row.archived || row.curRecorded) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.unit, fontSize = 9.sp, color = Gray400)
                if (row.isGroupItem) Text("grp", fontSize = 8.sp, color = Violet600, modifier = Modifier.background(Violet50, RoundedCornerShape(2.dp)).padding(horizontal = 2.dp))
                if (row.curRecorded) Text("rec", fontSize = 8.sp, color = Emerald700, modifier = Modifier.background(Emerald100, RoundedCornerShape(2.dp)).padding(horizontal = 2.dp))
                if (row.prevQty != null) Text("prev ${fmt(row.prevQty * (row.prevUnitPrice ?: 0.0))}", fontSize = 8.sp, color = Gray400)
            }
        }
        Spacer(Modifier.width(4.dp))

        DenseTextField(value = qtyField, onValueChange = { qtyField = it; onQtyChange(it) }, placeholder = "0", numeric = true, alignEnd = true, enabled = !row.curRecorded, modifier = Modifier.width(46.dp))
        Spacer(Modifier.width(4.dp))
        DenseTextField(value = priceField, onValueChange = { priceField = it; onUnitPriceChange(it) }, placeholder = "0", numeric = true, alignEnd = true, enabled = !row.curRecorded, modifier = Modifier.width(56.dp))
        Spacer(Modifier.width(4.dp))

        if (row.curBought) {
            DenseTextField(value = boughtPriceField, onValueChange = { boughtPriceField = it; onBoughtPriceChange(it) }, placeholder = if (row.isGroupItem) "ref" else "0", numeric = true, alignEnd = true, enabled = !row.curRecorded, containerColor = if (row.isGroupItem) Violet50 else Emerald50, focusColor = if (row.isGroupItem) Violet400 else Emerald400, modifier = Modifier.width(60.dp))
        } else {
            Box(Modifier.width(60.dp).height(42.dp), contentAlignment = Alignment.CenterEnd) {
                Text(if (curTotal > 0) fmt(curTotal) else "—", fontSize = 11.sp, color = Gray400, fontWeight = FontWeight.Medium)
            }
        }

        Box(Modifier.width(24.dp), contentAlignment = Alignment.CenterEnd) {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "More actions", tint = Gray400, modifier = Modifier.size(16.dp)) }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }, onClick = { menuExpanded = false; onEdit() })
                if (row.curRecorded) DropdownMenuItem(text = { Text("Reverse recording") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp)) }, onClick = { menuExpanded = false; onReverse() })
                DropdownMenuItem(text = { Text(if (row.archived) "Restore" else "Archive") }, leadingIcon = { Icon(if (row.archived) Icons.Default.Visibility else Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp)) }, onClick = { menuExpanded = false; onArchive() })
                DropdownMenuItem(text = { Text("Delete", color = Red600) }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Red600, modifier = Modifier.size(16.dp)) }, onClick = { menuExpanded = false; onDelete() })
            }
        }
    }
}

@Composable
private fun DenseTextField(
    value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    numeric: Boolean = false, alignEnd: Boolean = false, enabled: Boolean = true,
    containerColor: Color = Color.White, focusColor: Color = Emerald600,
) {
    var isFocused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onValueChange, enabled = enabled, modifier = modifier.height(38.dp).onFocusChanged { isFocused = it.isFocused },
        textStyle = TextStyle(fontSize = 13.sp, color = if (enabled) Gray900 else Gray500, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start, fontWeight = if (numeric) FontWeight.SemiBold else FontWeight.Normal),
        singleLine = true, keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize().background(if (enabled) containerColor else Color.Transparent, RoundedCornerShape(6.dp)).border(width = if (isFocused) 1.5.dp else 1.dp, color = if (isFocused) focusColor else if (enabled) Gray300 else Color.Transparent, shape = RoundedCornerShape(6.dp)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
                    if (value.isEmpty() && enabled) Text(text = placeholder, color = Gray400, fontSize = 13.sp, textAlign = if (alignEnd) TextAlign.End else TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    innerTextField()
                }
            }
        }
    )
}

private fun formatNum(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

// ─── History tab ────────────────────────────────────────────────────────────

@Composable
private fun HistorySection(state: GroceryUiState) {
    val monthsWithRecords = state.monthData.values.filter { it.recordedItemIds.isNotEmpty() }.sortedByDescending { it.id }
    if (monthsWithRecords.isEmpty()) { EmptyHint("No recorded months yet. Bought items you record show up here."); return }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        monthsWithRecords.forEach { month ->
            val recordedTotal = month.items.filter { month.recordedItemIds.contains(it.itemId) }.sumOf { it.effectivePrice() }
            Row(
                modifier = Modifier.fillMaxWidth().border(1.dp, Gray200, RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(monthLabelFor(month.id), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                    Text("${month.recordedItemIds.size} items recorded", fontSize = 11.sp, color = Gray500)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt(recordedTotal), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Gray300, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ─── Add / Edit item sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditItemSheet(
    state: GroceryUiState,
    viewModel: MonthlyGroceryViewModel,
    onDismiss: () -> Unit,
) {
    val isEditing = state.editingItemId != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf("form") }
    var bulkRowIndex by remember { mutableIntStateOf(-1) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), containerColor = Color.White) {
        Crossfade(targetState = currentView, label = "AddEditItem") { view ->
            when(view) {
                "form" -> {
                    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                        Text(if (isEditing) "Edit Item" else "Add Grocery Item", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Gray900)
                        Spacer(Modifier.height(14.dp))

                        if (!isEditing) {
                            SegmentedFilter(options = listOf("single" to "Single", "bulk" to "Bulk"), selected = state.addModalTab, onSelect = { viewModel.setAddModalTab(it) })
                            Spacer(Modifier.height(14.dp))
                        }

                        if (isEditing || state.addModalTab == "single") {
                            SingleItemForm(
                                form = state.addItemForm, categories = state.expenseCategories, isSaving = state.isSaving, isEditing = isEditing,
                                onFormChange = { viewModel.updateAddItemForm(it) }, onSubmit = { if (isEditing) viewModel.editItem() else viewModel.addItem() },
                                onCategoryClick = { currentView = "selectCategorySingle" }
                            )
                        } else {
                            BulkItemForm(
                                rows = state.bulkRows, categories = state.expenseCategories, isSaving = state.isSaving,
                                onUpdateRow = { i, r -> viewModel.updateBulkRow(i, r) }, onAddRow = { viewModel.addBulkRow() },
                                onSubmit = { viewModel.addItemsBulk() }, onCategoryClick = { idx -> bulkRowIndex = idx; currentView = "selectCategoryBulk" }
                            )
                        }
                    }
                }
                "selectCategorySingle" -> {
                    GenericSelectionList(
                        title = "Select Category", items = state.expenseCategories.map { it.id to it.name } + ("" to "No category"),
                        selectedValue = state.addItemForm.category,
                        onSelect = { viewModel.updateAddItemForm(state.addItemForm.copy(category = it)); currentView = "form" },
                        onBack = { currentView = "form" }, icon = Icons.Default.Category
                    )
                }
                "selectCategoryBulk" -> {
                    GenericSelectionList(
                        title = "Select Category", items = state.expenseCategories.map { it.id to it.name } + ("" to "No category"),
                        selectedValue = state.bulkRows.getOrNull(bulkRowIndex)?.category ?: "",
                        onSelect = {
                            if (bulkRowIndex in state.bulkRows.indices) {
                                val newRow = state.bulkRows[bulkRowIndex].copy(category = it)
                                viewModel.updateBulkRow(bulkRowIndex, newRow)
                            }
                            currentView = "form"
                        },
                        onBack = { currentView = "form" }, icon = Icons.Default.Category
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleItemForm(
    form: GroceryItemForm, categories: List<GroceryCategoryItem>, isSaving: Boolean, isEditing: Boolean,
    onFormChange: (GroceryItemForm) -> Unit, onSubmit: () -> Unit, onCategoryClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = form.name, onValueChange = { onFormChange(form.copy(name = it)) }, label = { Text("Item name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = form.defaultQty, onValueChange = { onFormChange(form.copy(defaultQty = it)) }, label = { Text("Default qty") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
            UnitDropdown(unit = form.unit, onSelect = { onFormChange(form.copy(unit = it)) }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = form.defaultUnitPrice, onValueChange = { onFormChange(form.copy(defaultUnitPrice = it)) }, label = { Text("Default unit price") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))

        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Gray200, RoundedCornerShape(10.dp))) {
            DrillDownRow(label = "Category", value = if (form.category.isBlank()) "No category" else categoryLabel(form.category, categories), icon = Icons.Default.Category, onClick = onCategoryClick)
        }

        Spacer(Modifier.height(4.dp))
        Button(onClick = onSubmit, enabled = !isSaving && form.name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Emerald600), shape = RoundedCornerShape(10.dp)) {
            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(if (isEditing) "Save Changes" else "Add Item", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BulkItemForm(
    rows: List<BulkRow>, categories: List<GroceryCategoryItem>, isSaving: Boolean,
    onUpdateRow: (Int, BulkRow) -> Unit, onAddRow: () -> Unit, onSubmit: () -> Unit, onCategoryClick: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEachIndexed { index, row ->
                Column(modifier = Modifier.fillMaxWidth().border(1.dp, Gray200, RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = row.name, onValueChange = { onUpdateRow(index, row.copy(name = it)) }, placeholder = { Text("Item name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 13.sp), shape = RoundedCornerShape(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = row.defaultQty, onValueChange = { onUpdateRow(index, row.copy(defaultQty = it)) }, placeholder = { Text("Qty") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = TextStyle(fontSize = 13.sp), modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp))
                        UnitDropdown(unit = row.unit, onSelect = { onUpdateRow(index, row.copy(unit = it)) }, modifier = Modifier.weight(1f), compact = true)
                        OutlinedTextField(value = row.defaultUnitPrice, onValueChange = { onUpdateRow(index, row.copy(defaultUnitPrice = it)) }, placeholder = { Text("Price") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = TextStyle(fontSize = 13.sp), modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(42.dp).background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Gray300, RoundedCornerShape(8.dp)).clickable { onCategoryClick(index) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = if (row.category.isBlank()) "No category" else categoryLabel(row.category, categories), fontSize = 13.sp, color = if (row.category.isBlank()) Gray400 else Gray900, maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Gray500, modifier = Modifier.size(16.dp))
                    }
                }
            }
            OutlinedButton(onClick = onAddRow, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Add Row")
            }
        }
        Button(onClick = onSubmit, enabled = !isSaving && rows.any { it.name.isNotBlank() }, modifier = Modifier.fillMaxWidth().height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Emerald600), shape = RoundedCornerShape(10.dp)) {
            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Add ${rows.count { it.name.isNotBlank() }} Items", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UnitDropdown(unit: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(if (compact) 42.dp else 48.dp).background(Color.White, RoundedCornerShape(if (compact) 8.dp else 10.dp)).border(1.dp, Gray300, RoundedCornerShape(if (compact) 8.dp else 10.dp)).clickable { expanded = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = unit.ifBlank { "Unit" }, fontSize = if (compact) 13.sp else 14.sp, color = if (unit.isBlank()) Gray400 else Gray900, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Gray500, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VALID_UNITS.forEach { u -> DropdownMenuItem(text = { Text(u, fontSize = 13.sp) }, onClick = { onSelect(u); expanded = false }) }
        }
    }
}

// ─── Confirm Record Sheet (Upgraded from Dialog) ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmRecordSheet(state: GroceryUiState, viewModel: MonthlyGroceryViewModel, onDismiss: () -> Unit) {
    val recordable = state.rows.filter { it.curBought && !it.curRecorded && !it.archived }
    val total = recordable.sumOf { it.effectivePrice }
    var confirm by remember(state.showConfirmModal) { mutableStateOf(state.confirmState) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf("form") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        containerColor = Color.White
    ) {
        Crossfade(targetState = currentView, label = "ConfirmRecord") { view ->
            when (view) {
                "form" -> {
                    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                        Text("Record as Expense", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Gray900)
                        Spacer(Modifier.height(14.dp))
                        Text("${recordable.size} bought item${if (recordable.size == 1) "" else "s"} · ${fmt(total)}", fontSize = 13.sp, color = Gray700, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(14.dp))

                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Gray200, RoundedCornerShape(12.dp))) {
                            DrillDownRow(
                                label = "From Account",
                                value = state.accounts.find { it.id == confirm.accountId }?.name ?: "Select account",
                                icon = Icons.Default.AccountBalanceWallet,
                                onClick = { currentView = "selectAccount" }
                            )
                            HorizontalDivider(color = Gray200, modifier = Modifier.padding(start = 44.dp))
                            DrillDownRow(
                                label = "Expense Category",
                                value = if (confirm.categoryId.isBlank()) "Select category" else categoryLabel(confirm.categoryId, state.expenseCategories),
                                icon = Icons.Default.Category,
                                onClick = { currentView = "selectCategory" }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = confirm.note, onValueChange = { confirm = confirm.copy(note = it); viewModel.updateConfirmState(confirm) }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                        Spacer(Modifier.height(12.dp))
                        DateSelectionField(label = "Date", dateString = confirm.date, onDateSelected = { confirm = confirm.copy(date = it); viewModel.updateConfirmState(confirm) })

                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                            Button(
                                onClick = { viewModel.confirmRecord() },
                                enabled = !state.isSaving && confirm.accountId.isNotBlank() && confirm.categoryId.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Gray900),
                                modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(10.dp)
                            ) {
                                if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text("Record")
                            }
                        }
                    }
                }
                "selectAccount" -> {
                    GenericSelectionList(
                        title = "Select Account",
                        items = state.accounts.map { it.id to "${it.name} (${fmt(it.balance)})" },
                        selectedValue = confirm.accountId,
                        onSelect = { confirm = confirm.copy(accountId = it); viewModel.updateConfirmState(confirm); currentView = "form" },
                        onBack = { currentView = "form" }, icon = Icons.Default.AccountBalanceWallet
                    )
                }
                "selectCategory" -> {
                    GenericSelectionList(
                        title = "Select Category",
                        items = state.expenseCategories.map { it.id to it.name } + ("" to "No category"),
                        selectedValue = confirm.categoryId,
                        onSelect = { confirm = confirm.copy(categoryId = it); viewModel.updateConfirmState(confirm); currentView = "form" },
                        onBack = { currentView = "form" }, icon = Icons.Default.Category
                    )
                }
            }
        }
    }
}

// ─── Shared Sub-Sheet Drill-Down Helpers ─────────────────────────────────────

@Composable
private fun SubSheetHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Gray900)
    }
    HorizontalDivider(color = Gray200)
}

@Composable
private fun DrillDownRow(label: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Gray500, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, fontSize = 11.sp, color = Gray500)
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Gray900)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Gray300)
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
            items(items.size) { index ->
                val item = items[index]
                val isSelected = selectedValue == item.first
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(item.first) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) Violet50 else Gray50),
                    border = BorderStroke(1.dp, if (isSelected) Violet600 else Gray200)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(if (isSelected) Violet100 else Gray200, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = if (isSelected) Violet700 else Gray500)
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(item.second, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Gray900, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, null, tint = Violet600)
                        }
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
        OutlinedTextField(
            value = dateString, onValueChange = {}, readOnly = true,
            label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
            singleLine = true, trailingIcon = { Icon(Icons.Default.CalendarToday, null) }
        )
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

// ─── Group price sheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPriceSheet(state: GroceryUiState, viewModel: MonthlyGroceryViewModel) {
    val modal = state.groupModal
    val members = state.rows.filter { modal.selectedItemIds.contains(it.itemId) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = { viewModel.closeGroupModal() }, sheetState = sheetState, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), containerColor = Color.White) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Layers, contentDescription = null, tint = Violet600, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Group Price — ${categoryLabel(modal.catId, state.expenseCategories)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gray900)
            }
            Spacer(Modifier.height(4.dp))
            Text("All ${members.size} items in this category are bought together for one total. It will be split evenly, and you can fine-tune each share below.", fontSize = 11.sp, color = Gray500)
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = modal.groupTotal,
                onValueChange = { viewModel.setGroupTotal(it) },
                label = { Text("Group total (৳)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            )

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                members.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().border(1.dp, Gray200, RoundedCornerShape(8.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.name, fontSize = 12.sp, color = Gray800, modifier = Modifier.weight(1f))
                        DenseTextField(
                            value = modal.perItemPrices[row.itemId] ?: "",
                            onValueChange = { viewModel.setPerItemPrice(row.itemId, it) },
                            placeholder = "ref price", numeric = true, alignEnd = true,
                            focusColor = Violet400, modifier = Modifier.width(90.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { viewModel.closeGroupModal() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(
                    onClick = { viewModel.confirmGroupPrice() },
                    enabled = modal.groupTotal.toDoubleOrNull()?.let { it > 0 } == true,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet600),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Apply", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ─── Reverse confirm dialog ─────────────────────────────────────────────────

@Composable
private fun ReverseConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, isReversing: Boolean) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = Red600) },
        title = { Text("Reverse recording?") },
        text = { Text("This item will be marked as not-yet-recorded so it can be included in a future expense record. It does not undo the transaction itself.", fontSize = 13.sp) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isReversing,
                colors = ButtonDefaults.buttonColors(containerColor = Red600),
            ) {
                if (isReversing) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Reverse")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}