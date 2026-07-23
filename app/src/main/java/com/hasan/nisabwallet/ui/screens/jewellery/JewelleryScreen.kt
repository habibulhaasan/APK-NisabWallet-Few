package com.hasan.nisabwallet.ui.screens.jewellery

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
import androidx.compose.material.icons.automirrored.filled.ArrowRight
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
import androidx.compose.ui.text.font.FontStyle
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JewelleryScreen(
    viewModel: JewelleryViewModel = hiltViewModel(),
    triggerFabAdd: Long = 0L,
    onAddHandled: () -> Unit = {},
    onNavigateBack: () -> Unit
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
                is JewelleryEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            Surface(color = Color(0xFFF9FAFB), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(48.dp))
                    Column {
                        Text("Jewellery Tracker", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Track gold & silver — monitor value for Zakat", fontSize = 12.sp, color = Color(0xFF6B7280))
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
                if (state.items.isNotEmpty()) {
                    item {
                        val cards = mutableListOf<@Composable () -> Unit>()
                        cards.add { SummaryCard("Total Zakat Value", fmt(state.totalZakat), "${state.pricedCount}/${state.activeCount} priced", Icons.Default.Star, Color(0xFF059669), Color(0xFFECFDF5), Color(0xFFD1FAE5)) }
                        val soldStr = if(state.totalSoldRevenue > 0) "${state.items.size - state.activeCount} sold" else "${state.items.count { it.metal=="Gold" && it.status!="sold" }} gold · ${state.items.count { it.metal=="Silver" && it.status!="sold" }} silver"
                        cards.add { SummaryCard("Active Items", state.activeCount.toString(), soldStr, Icons.Default.Inventory, Color(0xFFD97706), Color(0xFFFFFBEB), Color(0xFFFEF3C7)) }
                        if (state.totalGoldGrams > 0) cards.add { SummaryCard("Total Gold", formatWeightString(state.totalGoldGrams), null, Icons.Default.Toll, Color(0xFFD97706), Color(0xFFFFFBEB), Color(0xFFFEF3C7)) }
                        if (state.totalSilverGrams > 0) cards.add { SummaryCard("Total Silver", formatWeightString(state.totalSilverGrams), null, Icons.Default.Toll, Color(0xFF64748B), Color(0xFFF8FAFC), Color(0xFFF1F5F9)) }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (i in cards.indices step 2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Box(Modifier.weight(1f)) { cards[i]() }
                                    if (i + 1 < cards.size) Box(Modifier.weight(1f)) { cards[i + 1]() } else Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                if (state.activeCount > 0 && state.pricedCount < state.activeCount) {
                    item {
                        Surface(color = Color(0xFFFFFBEB), border = BorderStroke(1.dp, Color(0xFFFDE68A)), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Warning, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("${state.activeCount - state.pricedCount} item(s) not priced yet — tap Check Price to fetch BAJUS rates for Zakat.", fontSize = 12.sp, color = Color(0xFF92400E))
                            }
                        }
                    }
                }

                if (state.items.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = state.searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text("Search jewellery...", fontSize = 14.sp) }, 
                                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CompactFilterDropdown("Status", state.filterStatus, listOf("active" to "Active", "sold" to "Sold", "all" to "All Items"), { viewModel.setFilterStatus(it) }, Modifier.weight(1f))
                                    CompactFilterDropdown("Metal", state.filterMetal, listOf("all" to "All Metals", "Gold" to "Gold", "Silver" to "Silver"), { viewModel.setFilterMetal(it) }, Modifier.weight(1f))
                                    CompactFilterDropdown("Sort", state.sortBy, listOf("newest" to "Newest", "value" to "By Value", "weight" to "By Weight", "name" to "By Name"), { viewModel.setSortBy(it) }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                item { Text("Jewellery (${state.filteredItems.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 8.dp)) }

                if (state.isLoading) {
                    item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFFF59E0B)) } }
                } else if (state.filteredItems.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Diamond, null, modifier = Modifier.size(48.dp), tint = Color(0xFFFDE68A))
                            Spacer(Modifier.height(16.dp))
                            Text("No jewellery found", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                            Text("Add your gold and silver to track Zakat", fontSize = 13.sp, color = Color(0xFF6B7280))
                        }
                    }
                } else {
                    items(state.filteredItems, key = { it.id }) { item ->
                        JewelleryCard(
                            item = item, fmt = fmt,
                            onEdit = { viewModel.openAddModal(item) },
                            onSell = { viewModel.openSellModal(item) },
                            onCheckPrice = { viewModel.openPriceModal(item) },
                            onDelete = { viewModel.openDeleteConfirm(item) }
                        )
                    }
                }
            }
        }
    }

    if (state.showAddModal) {
        AddEditJewelleryModal(
            form = state.form, accounts = state.accounts, isSaving = state.isSaving,
            calcGrams = { v, a, r, p -> viewModel.calculateGrams(v, a, r, p) },
            onUpdateForm = { viewModel.updateForm(it) }, onDismiss = { viewModel.closeAddModal() }, onSave = { viewModel.saveJewellery() }
        )
    }

    if (state.showSellModal && state.selectedItem != null) {
        SellJewelleryModal(
            form = state.sellForm, item = state.selectedItem!!, accounts = state.accounts, isSaving = state.isSaving,
            onUpdateForm = { viewModel.updateSellForm(it) }, onDismiss = { viewModel.closeSellModal() }, onSave = { viewModel.submitSale() }
        )
    }
    
    if (state.showPriceModal && state.selectedItem != null) {
        JewelleryPriceModal(
            item = state.selectedItem!!, state = state, fmt = fmt,
            onClose = { viewModel.closePriceModal() }, onFetch = { viewModel.fetchBajusRates() },
            onToggleDeduction = { viewModel.setApplyDeduction(it) }, onToggleManual = { viewModel.setUseManualPrice(it) },
            onManualValueChange = { viewModel.setManualPriceValue(it) },
            onSaveSnapshot = { mVal, dVal, pGram -> viewModel.savePriceSnapshot(mVal, dVal, pGram) }
        )
    }

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDeleteConfirm() },
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Jewellery?") },
            text = { Text("Are you sure you want to delete \"${state.selectedItem?.name}\"? This action and all price history will be permanently deleted.") },
            confirmButton = { Button(onClick = { viewModel.deleteJewellery() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete") } },
            dismissButton = { OutlinedButton(onClick = { viewModel.closeDeleteConfirm() }) { Text("Cancel") } }
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

// ─── List Cards ───────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(label: String, value: String, sub: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, bg: Color, border: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = bg), border = BorderStroke(1.dp, border)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 11.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(14.dp))
            }
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            if (sub != null) Text(sub, fontSize = 11.sp, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JewelleryCard(
    item: Jewellery, fmt: (Double) -> String,
    onEdit: () -> Unit, onSell: () -> Unit, onCheckPrice: () -> Unit, onDelete: () -> Unit
) {
    val isGold = item.metal == "Gold"
    val isSold = item.status == "sold"

    val badgeBg = if(isGold) Color(0xFFFEF3C7) else Color(0xFFF1F5F9)
    val badgeText = if(isGold) Color(0xFF92400E) else Color(0xFF1E293B)
    val iconBg = if(isSold) Color(0xFFF3F4F6) else if(isGold) Color(0xFFFFFBEB) else Color(0xFFF8FAFC)
    val iconTint = if(isSold) Color(0xFF9CA3AF) else if(isGold) Color(0xFFB45309) else Color(0xFF475569)

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, if(isSold) Color(0xFFDBEAFE) else Color(0xFFE5E7EB)),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            if (isSold) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFEFF6FF)).border(1.dp, Color(0xFFDBEAFE)).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("SOLD on ${formatDisplayDate(item.soldAt ?: "")}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(fmt(item.soldPrice ?: 0.0), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f).padding(end = 12.dp)) {
                    Box(modifier = Modifier.size(40.dp).background(iconBg, RoundedCornerShape(10.dp)).border(1.dp, if(isSold) Color.Transparent else badgeBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Diamond, null, tint = iconTint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if(isSold) Color(0xFF6B7280) else Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Surface(shape = RoundedCornerShape(50), color = badgeBg) { Text("${item.karat} ${item.metal}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = badgeText, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                            if (item.acquisitionType != "purchased") {
                                Surface(shape = RoundedCornerShape(50), color = Color(0xFFFAF5FF), border = BorderStroke(1.dp, Color(0xFFF3E8FF))) { Text(item.acquisitionType.replaceFirstChar { it.uppercase() }, fontSize = 9.sp, color = Color(0xFF7E22CE), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                            }
                        }

                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Scale, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(formatWeightLong(item), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(String.format(Locale.US, " • %.4fg", item.weightGrams), fontSize = 11.sp, color = Color(0xFF9CA3AF))
                        }

                        if (!isSold) {
                            if (item.currentZakatValue != null && item.currentZakatValue > 0) {
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(fmt(item.currentZakatValue), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(" Zakat value (−15%)", fontSize = 10.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(start = 4.dp))
                                }
                            } else {
                                Text("⚠ Price not checked yet", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF59E0B), modifier = Modifier.padding(top = 6.dp))
                            }
                        } else {
                            if (item.acquisitionType == "purchased" && item.purchaseTotal != null) {
                                val profit = (item.soldPrice ?: 0.0) - item.purchaseTotal
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if(profit >= 0) "📈 Profit: " else "📉 Loss: ", fontSize = 10.sp, color = Color(0xFF6B7280))
                                    Text(fmt(abs(profit)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(profit >= 0) Color(0xFF059669) else Color(0xFFDC2626), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                
                // Card Actions Column
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.wrapContentWidth()) {
                    if (isSold) {
                        Button(onClick = onCheckPrice, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6), contentColor = Color(0xFF4B5563)), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Icon(Icons.Default.History, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("History", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Edit, null, tint = Color(0xFF4B5563), modifier = Modifier.size(14.dp)) }
                            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp)) }
                        }
                    } else {
                        Button(onClick = onCheckPrice, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Icon(Icons.Default.TrendingUp, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(if((item.currentZakatValue ?: 0.0) > 0) "Update Price" else "Check Price", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = onSell, modifier = Modifier.height(32.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6), contentColor = Color.White), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowRight, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Sell", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Edit, null, tint = Color(0xFF4B5563), modifier = Modifier.size(14.dp)) }
                            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }

            if (item.notes.isNotBlank() || item.soldNotes?.isNotBlank() == true) {
                HorizontalDivider(color = Color(0xFFF3F4F6))
                Text("📝 ${item.soldNotes?.takeIf { it.isNotBlank() } ?: item.notes}", fontSize = 10.sp, color = Color(0xFF9CA3AF), fontStyle = FontStyle.Italic, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

// ─── Price Checking Modal ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JewelleryPriceModal(
    item: Jewellery, state: JewelleryUiState, fmt: (Double) -> String,
    onClose: () -> Unit, onFetch: () -> Unit,
    onToggleDeduction: (Boolean) -> Unit, onToggleManual: (Boolean) -> Unit,
    onManualValueChange: (String) -> Unit,
    onSaveSnapshot: (Double, Double, Double) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val effectivePct = if(state.useManualPrice) 0.0 else if(state.applyDeduction) 15.0 else 0.0
    var pGram = 0.0; var marketValue = 0.0; var deductedValue = 0.0; var deductionAmt = 0.0
    val prevValue = state.priceHistory.firstOrNull()?.deductedValue
    var valueChange: Double? = null

    if (state.fetchedRates != null && !state.useManualPrice) {
        pGram = if(item.metal == "Gold") {
            when(item.karat) { "22K"->state.fetchedRates.gold.k22; "21K"->state.fetchedRates.gold.k21; "18K"->state.fetchedRates.gold.k18; else->state.fetchedRates.gold.traditional }
        } else {
            when(item.karat) { "22K"->state.fetchedRates.silver.k22; "21K"->state.fetchedRates.silver.k21; "18K"->state.fetchedRates.silver.k18; else->state.fetchedRates.silver.traditional }
        }
        marketValue = pGram * item.weightGrams
        deductedValue = marketValue * (1 - (effectivePct/100))
        deductionAmt = marketValue - deductedValue
        valueChange = if(prevValue!=null) deductedValue - prevValue else null
    } else if (state.useManualPrice) {
        deductedValue = state.manualPriceValue.toDoubleOrNull() ?: 0.0
        marketValue = deductedValue
        valueChange = if(prevValue!=null) deductedValue - prevValue else null
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).background(Color(0xFFD1FAE5), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.TrendingUp, null, tint = Color(0xFF047857), modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column { Text("Check Current Price", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827)); Text("${item.name} · ${item.karat} ${item.metal}", fontSize = 11.sp, color = Color(0xFF6B7280)) }
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color(0xFF6B7280)) }
            }
            HorizontalDivider()

            Column(Modifier.weight(1f, fill=false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFFFFBEB), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Scale, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                        Text(formatWeightLong(item), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                        Text(" (${String.format(Locale.US, "%.4f", item.weightGrams)}g)", fontSize = 11.sp, color = Color(0xFFD97706))
                    }
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFFFDE68A)) { Text("${item.karat} ${item.metal}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }
                }

                if (state.bajusFetchState == "loading") {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF059669))
                        Spacer(Modifier.height(12.dp)); Text("Fetching BAJUS live prices…", fontSize = 13.sp, color = Color(0xFF4B5563))
                    }
                }

                if (state.bajusFetchState == "error") {
                    Surface(color = Color(0xFFFEF2F2), border = BorderStroke(1.dp, Color(0xFFFECACA)), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.WifiOff, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Could not fetch prices", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onFetch, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(36.dp)) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Retry", fontSize = 11.sp) }
                                    OutlinedButton(onClick = { onToggleManual(true) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(36.dp)) { Text("Enter Manually", fontSize = 11.sp, color = Color(0xFF374151)) }
                                }
                            }
                        }
                    }
                }

                if (state.bajusFetchState == "success" || state.useManualPrice) {
                    Surface(color = Color(0xFFF3F4F6), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(4.dp)) {
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (!state.useManualPrice) Color.White else Color.Transparent).clickable { onToggleManual(false) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text("Live Price", fontSize = 13.sp, fontWeight = if (!state.useManualPrice) FontWeight.Bold else FontWeight.Medium, color = if (!state.useManualPrice) Color(0xFF111827) else Color(0xFF4B5563)) }
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (state.useManualPrice) Color.White else Color.Transparent).clickable { onToggleManual(true) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text("Edit Manually", fontSize = 13.sp, fontWeight = if (state.useManualPrice) FontWeight.Bold else FontWeight.Medium, color = if (state.useManualPrice) Color(0xFF111827) else Color(0xFF4B5563)) }
                        }
                    }

                    if (state.useManualPrice) {
                        Column {
                            Text("Enter current value of this jewellery (৳)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4B5563))
                            OutlinedTextField(value = state.manualPriceValue, onValueChange = onManualValueChange, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, leadingIcon = { Text("৳", color = Color(0xFF9CA3AF)) })
                            Text("Enter the resale/sell value after any deductions.", fontSize = 11.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(top = 4.dp))
                        }
                    } else {
                        if (pGram > 0) {
                            Row(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFF3F4F6), RoundedCornerShape(12.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${item.karat} ${item.metal} price/gram", fontSize = 12.sp, color = Color(0xFF6B7280))
                                Text(fmt(pGram), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                        }

                        Surface(modifier = Modifier.fillMaxWidth().clickable { onToggleDeduction(!state.applyDeduction) }, shape = RoundedCornerShape(12.dp), color = if(state.applyDeduction) Color(0xFFFFFBEB) else Color.White, border = BorderStroke(2.dp, if(state.applyDeduction) Color(0xFFFDE68A) else Color(0xFFE5E7EB))) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Switch(checked = state.applyDeduction, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFD97706)), modifier = Modifier.padding(top = 2.dp).size(36.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Apply 15% BAJUS Deduction", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if(state.applyDeduction) Color(0xFF78350F) else Color(0xFF374151))
                                        if (state.applyDeduction) Surface(shape = RoundedCornerShape(50), color = Color(0xFFFDE68A), modifier = Modifier.padding(start = 8.dp)) { Text("ON", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                    }
                                    Text("BAJUS officially deducts 17% for old gold resale; Bangladeshi Ulama recommend 15%. 15% is applied here by default.", fontSize = 11.sp, color = Color(0xFF6B7280), lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }

                    if (deductedValue > 0) {
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF0D9488)))).padding(16.dp)) {
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(if(state.useManualPrice) "MANUAL VALUE" else if(state.applyDeduction) "RESALE VALUE (−15%)" else "FULL MARKET VALUE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA7F3D0))
                                    if (valueChange != null) {
                                        Surface(color = if(valueChange >= 0) Color(0x6610B981) else Color(0x66EF4444), shape = RoundedCornerShape(50)) {
                                            Text("${if(valueChange >= 0) "+" else ""}${fmt(valueChange)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Text(fmt(deductedValue), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 4.dp))
                                if (!state.useManualPrice && state.applyDeduction) {
                                    Text("Full market: ${fmt(marketValue)} · Deducted: ${fmt(deductionAmt)}", fontSize = 11.sp, color = Color(0xFFA7F3D0), modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }

                        Button(onClick = { onSaveSnapshot(marketValue, deductedValue, pGram) }, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))) {
                            if (state.isSaving) CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp) else Text("Save as Asset Snapshot", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (state.priceHistory.isNotEmpty()) {
                    Column(Modifier.padding(top = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Price History", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151)) }
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.priceHistory.forEachIndexed { i, snap ->
                                val prev = state.priceHistory.getOrNull(i + 1)
                                val chg = if(prev != null) snap.deductedValue - prev.deductedValue else null
                                Row(Modifier.fillMaxWidth().background(if(i==0) Color(0xFFECFDF5) else Color.White, RoundedCornerShape(12.dp)).border(1.dp, if(i==0) Color(0xFFA7F3D0) else Color(0xFFE5E7EB), RoundedCornerShape(12.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(snap.recordedAt)), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
                                        Text(if(snap.isManualValue) "Manual entry" else "−${snap.deductionPct?.toInt() ?: 15}% deduction", fontSize = 11.sp, color = Color(0xFF6B7280))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(fmt(snap.deductedValue), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if(i==0) Color(0xFF047857) else Color(0xFF111827))
                                        if (chg != null) Text("${if(chg>=0) "+" else ""}${fmt(chg)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if(chg>=0) Color(0xFF059669) else Color(0xFFDC2626))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Add/Edit & Sell Modals ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditJewelleryModal(
    form: JewelleryForm, accounts: List<JewelleryAccount>, isSaving: Boolean,
    calcGrams: (Int, Int, Int, Int) -> Double,
    onUpdateForm: ((JewelleryForm) -> JewelleryForm) -> Unit,
    onDismiss: () -> Unit, onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf("form") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Crossfade(targetState = currentView, label = "JewelleryModal") { view ->
            when (view) {
                "form" -> {
                    Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding().navigationBarsPadding()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if(form.id != null) "Edit Jewellery" else "Add Jewellery", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                        }
                        HorizontalDivider()

                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Basic Info", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                OutlinedTextField(value = form.name, onValueChange = { n -> onUpdateForm { it.copy(name = n) } }, label = { Text("Item Name *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Metal *", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("Gold" to "Gold", "Silver" to "Silver").forEach { (v, l) ->
                                            val sel = form.metal == v
                                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if(sel) Color(0xFF111827) else Color(0xFFF3F4F6)).clickable{ onUpdateForm { it.copy(metal = v, karat = "22K") } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                Text(l, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if(sel) Color.White else Color(0xFF4B5563))
                                            }
                                        }
                                    }
                                }

                                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))) {
                                    DrillDownRow(label = "Category", value = form.category, icon = Icons.Default.Category, onClick = { currentView = "selectCategory" })
                                }

                                Text("Karat / Purity", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151))
                                val karats = if(form.metal == "Gold") listOf("22K", "21K", "18K", "Traditional", "24K") else listOf("22K", "21K", "18K", "Traditional")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    karats.forEach { k ->
                                        val sel = form.karat == k
                                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(2.dp, if(sel) Color(0xFFF59E0B) else Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).background(if(sel) Color(0xFFF59E0B) else Color.White).clickable { onUpdateForm { it.copy(karat = k) } }.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                            Text(k, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if(sel) Color.White else Color(0xFF4B5563))
                                        }
                                    }
                                }
                                OutlinedTextField(value = form.notes, onValueChange = { n -> onUpdateForm { it.copy(notes = n) } }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 2)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("How Was It Acquired?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("purchased" to "Purchased", "gift" to "Gift", "inherited" to "Inherited", "other" to "Other").forEach { (v, l) ->
                                        val sel = form.acquisitionType == v
                                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(2.dp, if(sel) Color(0xFFA855F7) else Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).background(if(sel) Color(0xFFFAF5FF) else Color.White).clickable { onUpdateForm { it.copy(acquisitionType = v, recordTransaction = false) } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                            Text(l, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(sel) Color(0xFF7E22CE) else Color(0xFF6B7280))
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Weight", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Surface(color = Color(0xFFEFF6FF), border = BorderStroke(1.dp, Color(0xFFBFDBFE)), shape = RoundedCornerShape(8.dp)) { Text("1 Vori = 16 Ana = 96 Roti = 960 Point = 11.664g. Enter what you know — leave others at 0.", fontSize = 10.sp, color = Color(0xFF1D4ED8), modifier = Modifier.padding(8.dp)) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    WeightInput("Vori", form.weightVori, { v -> onUpdateForm { it.copy(weightVori = v) } }, Modifier.weight(1f))
                                    WeightInput("Ana", form.weightAna, { v -> onUpdateForm { it.copy(weightAna = v) } }, Modifier.weight(1f))
                                    WeightInput("Roti", form.weightRoti, { v -> onUpdateForm { it.copy(weightRoti = v) } }, Modifier.weight(1f))
                                    WeightInput("Point", form.weightPoint, { v -> onUpdateForm { it.copy(weightPoint = v) } }, Modifier.weight(1f))
                                }
                                val g = calcGrams(form.weightVori.toIntOrNull()?:0, form.weightAna.toIntOrNull()?:0, form.weightRoti.toIntOrNull()?:0, form.weightPoint.toIntOrNull()?:0)
                                if (g > 0) {
                                    Row(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Total weight", fontSize = 12.sp, color = Color(0xFF4B5563))
                                        Text(String.format(Locale.US, "%.4f grams", g), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                    }
                                }
                            }

                            if (form.acquisitionType == "purchased") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Purchase Price (optional)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                    DateSelectionField("Purchase Date", form.purchaseDate, { d -> onUpdateForm { it.copy(purchaseDate = d) } })

                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(2.dp, if(!form.usePurchaseManual) Color(0xFF111827) else Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).background(if(!form.usePurchaseManual) Color(0xFF111827) else Color.White).clickable { onUpdateForm { it.copy(usePurchaseManual = false) } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text("Auto Calculate", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(!form.usePurchaseManual) Color.White else Color(0xFF6B7280)) }
                                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(2.dp, if(form.usePurchaseManual) Color(0xFF111827) else Color(0xFFE5E7EB), RoundedCornerShape(8.dp)).background(if(form.usePurchaseManual) Color(0xFF111827) else Color.White).clickable { onUpdateForm { it.copy(usePurchaseManual = true) } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text("Enter Total Directly", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(form.usePurchaseManual) Color.White else Color(0xFF6B7280)) }
                                    }

                                    if (form.usePurchaseManual) {
                                        OutlinedTextField(value = form.purchaseTotal, onValueChange = { v -> onUpdateForm { it.copy(purchaseTotal = v) } }, label = { Text("Total Amount Paid (৳)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                    } else {
                                        OutlinedTextField(value = form.purchaseGoldPrice, onValueChange = { v -> onUpdateForm { it.copy(purchaseGoldPrice = v) } }, label = { Text("Price Per Gram at Purchase (৳)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(value = form.purchaseVat, onValueChange = { v -> onUpdateForm { it.copy(purchaseVat = v) } }, label = { Text("VAT (%)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                            OutlinedTextField(value = form.purchaseMaking, onValueChange = { v -> onUpdateForm { it.copy(purchaseMaking = v) } }, label = { Text("Making (%)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                            OutlinedTextField(value = form.purchaseOther, onValueChange = { v -> onUpdateForm { it.copy(purchaseOther = v) } }, label = { Text("Other (৳)") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                        }

                                        val g = calcGrams(form.weightVori.toIntOrNull()?:0, form.weightAna.toIntOrNull()?:0, form.weightRoti.toIntOrNull()?:0, form.weightPoint.toIntOrNull()?:0)
                                        val bgP = form.purchaseGoldPrice.toDoubleOrNull() ?: 0.0
                                        if (g > 0 && bgP > 0) {
                                            val base = g * bgP
                                            val tot = base + (base * ((form.purchaseVat.toDoubleOrNull()?:0.0)/100)) + (base * ((form.purchaseMaking.toDoubleOrNull()?:0.0)/100)) + (form.purchaseOther.toDoubleOrNull()?:0.0)
                                            Surface(color = Color(0xFFECFDF5), border = BorderStroke(1.dp, Color(0xD1FAE5)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text("Estimated Purchase Cost", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                                                    Text(String.format(Locale.US, "Total paid: ৳%.0f", tot), fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF047857), modifier = Modifier.padding(top = 4.dp))
                                                }
                                            }
                                        }
                                    }

                                    if (form.id == null) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().clickable { onUpdateForm { it.copy(recordTransaction = !it.recordTransaction) } },
                                            shape = RoundedCornerShape(12.dp), border = BorderStroke(2.dp, if(form.recordTransaction) Color(0xFF34D399) else Color(0xFFE5E7EB)),
                                            color = if(form.recordTransaction) Color(0xFFECFDF5) else Color.White
                                        ) {
                                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CreditCard, null, tint = if(form.recordTransaction) Color(0xFF059669) else Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text("Record as expense", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if(form.recordTransaction) Color(0xFF065F46) else Color(0xFF374151))
                                                    Text("Deducts from account balance", fontSize = 10.sp, color = Color(0xFF6B7280))
                                                }
                                                Checkbox(checked = form.recordTransaction, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF059669)))
                                            }
                                        }

                                        if (form.recordTransaction) {
                                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))) {
                                                DrillDownRow(label = "Deduct from account", value = accounts.find { it.id == form.txAccountId }?.name ?: "Select account", icon = Icons.Default.AccountBalanceWallet, onClick = { currentView = "selectTxAccount" })
                                            }
                                        }
                                    }
                                }
                            } else {
                                DateSelectionField("Date Acquired (optional)", form.purchaseDate, { d -> onUpdateForm { it.copy(purchaseDate = d) } })
                                Text("No purchase cost recorded. Current market price will be used for Zakat calculation.", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                            }
                        }

                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))) {
                                if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Saving...") }
                                else Text(if(form.id != null) "Save Changes" else "Add Jewellery", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "selectCategory" -> GenericSelectionList(title = "Select Category", items = listOf("Necklace", "Ring", "Earring", "Bracelet", "Bangle", "Anklet", "Nose Ring", "Pendant", "Chain", "Other").map { Pair(it, it) }, selectedValue = form.category, onSelect = { onUpdateForm { f -> f.copy(category = it) }; currentView = "form" }, onBack = { currentView = "form" }, icon = Icons.Default.Category)
                "selectTxAccount" -> GenericSelectionList(title = "Select Account", items = accounts.map { Pair(it.id, "${it.name} (৳${it.balance})") }, selectedValue = form.txAccountId, onSelect = { onUpdateForm { f -> f.copy(txAccountId = it) }; currentView = "form" }, onBack = { currentView = "form" }, icon = Icons.Default.AccountBalanceWallet)
            }
        }
    }
}

@Composable
private fun WeightInput(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
        OutlinedTextField(value = value, onValueChange = { if(it.length <= 3) onValueChange(it) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SellJewelleryModal(
    form: SellJewelleryForm, item: Jewellery, accounts: List<JewelleryAccount>, isSaving: Boolean,
    onUpdateForm: ((SellJewelleryForm) -> SellJewelleryForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf("form") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Crossfade(targetState = currentView, label = "SellModal") { view ->
            when (view) {
                "form" -> {
                    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Sell Jewellery", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827)); IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                        }
                        HorizontalDivider()

                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(color = Color(0xFFFFFBEB), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFFDE68A)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78350F), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${item.karat} ${item.metal}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                    }
                                    Text("${formatWeightLong(item)} (${String.format(Locale.US, "%.4f", item.weightGrams)}g)", fontSize = 12.sp, color = Color(0xFF92400E), modifier = Modifier.padding(top = 4.dp))
                                }
                            }

                            OutlinedTextField(value = form.saleAmount, onValueChange = { v -> onUpdateForm { it.copy(saleAmount = v) } }, label = { Text("Sale Amount (৳) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            DateSelectionField("Sale Date", form.saleDate, { d -> onUpdateForm { it.copy(saleDate = d) } })

                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))) {
                                DrillDownRow(label = "Add money to account *", value = accounts.find { it.id == form.accountId }?.name ?: "Select account", icon = Icons.Default.AccountBalanceWallet, onClick = { currentView = "selectAccount" })
                            }

                            OutlinedTextField(value = form.notes, onValueChange = { n -> onUpdateForm { it.copy(notes = n) } }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 2)

                            Surface(color = Color(0xFFFFF7ED), border = BorderStroke(1.dp, Color(0xFFFED7AA)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.Warning, null, tint = Color(0xFFF97316), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text("This will mark the jewellery as Sold, create an income transaction, and add the amount to the selected account.", fontSize = 12.sp, color = Color(0xFF9A3412))
                                }
                            }
                        }

                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                                if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Processing...") } else Text("Confirm Sale", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "selectAccount" -> GenericSelectionList(title = "Select Account", items = accounts.map { Pair(it.id, "${it.name} (৳${it.balance})") }, selectedValue = form.accountId, onSelect = { onUpdateForm { f -> f.copy(accountId = it) }; currentView = "form" }, onBack = { currentView = "form" }, icon = Icons.Default.AccountBalanceWallet)
            }
        }
    }
}

@Composable
private fun DrillDownRow(label: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
            Column { Text(label, fontSize = 11.sp, color = Color(0xFF6B7280)); Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFD1D5DB))
    }
}

@Composable
private fun GenericSelectionList(title: String, items: List<Pair<String, String>>, selectedValue: String, onSelect: (String) -> Unit, onBack: () -> Unit, icon: ImageVector) {
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding().navigationBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }; Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
        }
        HorizontalDivider(color = Color(0xFFE5E7EB))
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                val isSelected = selectedValue == item.first
                Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(item.first) }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF9FAFB)), border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFFE5E7EB))) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(Color(0xFFDBEAFE), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color(0xFF1D4ED8)) }; Spacer(Modifier.width(16.dp))
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
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(dateString)?.time } catch(e: Exception) { null })

    Box(modifier = modifier) {
        OutlinedTextField(value = dateString, onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, trailingIcon = { Icon(Icons.Default.CalendarToday, null) })
        Box(modifier = Modifier.matchParentSize().clickable { showPicker = true })
    }

    if (showPicker) {
        DatePickerDialog(onDismissRequest = { showPicker = false }, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { millis -> onDateSelected(SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))) }; showPicker = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }) { DatePicker(state = datePickerState) }
    }
}

private fun formatWeightString(grams: Double): String {
    if (grams <= 0) return "0g"
    val vori = floor(grams / 11.664).toInt()
    val rem = grams - (vori * 11.664)
    val ana = floor(rem / (11.664 / 16)).toInt()
    val parts = mutableListOf<String>()
    if (vori > 0) parts.add("${vori}V")
    if (ana > 0) parts.add("${ana}A")
    if (parts.isEmpty()) return "${String.format(Locale.US, "%.2f", grams)}g"
    return parts.joinToString(" ") + " (${String.format(Locale.US, "%.2f", grams)}g)"
}

private fun formatWeightLong(item: Jewellery): String {
    val parts = mutableListOf<String>()
    if (item.weightVori > 0) parts.add("${item.weightVori} Vori")
    if (item.weightAna > 0) parts.add("${item.weightAna} Ana")
    if (item.weightRoti > 0) parts.add("${item.weightRoti} Roti")
    if (item.weightPoint > 0) parts.add("${item.weightPoint} Point")
    return if (parts.isNotEmpty()) parts.joinToString(" ") else "0"
}

private fun formatDisplayDate(dateStr: String): String {
    if (dateStr.isBlank()) return "—"
    return try { SimpleDateFormat("dd MMM, yyyy", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)!!) } catch (e: Exception) { dateStr }
}