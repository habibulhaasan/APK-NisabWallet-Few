package com.hasan.nisabwallet.ui.screens.tax.detail

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import com.hasan.nisabwallet.core.util.TaxCategoryUtils
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxDetailScreen(
    viewModel: TaxDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSetup: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TaxDetailEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                TaxDetailEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            Surface(color = Color(0xFFF9FAFB), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF4B5563))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text("Tax Year Details", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                }
            }
        }
    ) { padding ->
        if (state.isLoading || state.taxYear == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF111827)) }
            return@Scaffold
        }

        val taxYear = state.taxYear!!
        val analysis = state.analysis

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ─── Header Card[cite: 15] ───
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Income Year ${taxYear.incomeYear}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                    Spacer(Modifier.width(8.dp))
                                    StatusBadge(taxYear.status)
                                }
                                Text("Tax Year ${taxYear.taxYear} • ${formatDate(taxYear.fiscalYearStart)} - ${formatDate(taxYear.fiscalYearEnd)}", fontSize = 12.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 4.dp))
                                Text("Filing Deadline: ${formatDate(taxYear.filingDeadline)}", fontSize = 11.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(top = 2.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { viewModel.openProfileModal() }, modifier = Modifier.size(36.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))) {
                                    Icon(Icons.Default.Settings, "Profile", tint = Color(0xFF374151), modifier = Modifier.size(18.dp))
                                }
                                Button(
                                    onClick = { viewModel.handleAnalyze() },
                                    enabled = !state.isAnalyzing,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    if (state.isAnalyzing) CircularProgressIndicator(Modifier.size(14.dp), Color.White, 2.dp)
                                    else { Icon(Icons.Default.Description, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Analyze", fontSize = 12.sp) }
                                }
                                IconButton(onClick = { viewModel.requestDelete("taxYear", taxYear) }, modifier = Modifier.size(36.dp).background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))) {
                                    Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF3F4F6), modifier = Modifier.padding(vertical = 16.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricBox("Total Income", "৳${fmt(analysis.totalIncome)}", Color(0xFF16A34A), Modifier.weight(1f))
                            MetricBox("Total Expenses", "৳${fmt(analysis.totalExpenses)}", Color(0xFFDC2626), Modifier.weight(1f))
                            MetricBox("Savings", "৳${fmt(analysis.totalIncome - analysis.totalExpenses)}", Color(0xFF2563EB), Modifier.weight(1f))
                            val savingsRate = if (analysis.totalIncome > 0) ((analysis.totalIncome - analysis.totalExpenses) / analysis.totalIncome * 100).toInt() else 0
                            MetricBox("Savings Rate", "$savingsRate%", Color(0xFF111827), Modifier.weight(1f))
                        }
                    }
                }
            }

            // ─── Warning if no mappings[cite: 15] ───
            if (state.mappings.isEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFFEFCE8), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFFEF08A), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFCA8A04), modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("No Category Mappings Found", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF713F12))
                            Text("Please complete the tax category mapping setup to analyze your transactions.", fontSize = 12.sp, color = Color(0xFFA16207), modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                            Text("Go to Setup Wizard", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCA8A04), modifier = Modifier.clickable { onNavigateToSetup() })
                        }
                    }
                }
            }

            // ─── Income & Expense Analysis Grid[cite: 15] ───
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Income Analysis
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Income Analysis", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                            if (analysis.income.isEmpty()) {
                                Text("No income transactions found", fontSize = 12.sp, color = Color(0xFF9CA3AF), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    analysis.income.forEach { (catId, amt) ->
                                        val cat = TaxCategoryUtils.INCOME_TAX_CATEGORIES.find { it.id == catId }
                                        val pct = if (analysis.totalIncome > 0) String.format(Locale.US, "%.1f", (amt / analysis.totalIncome) * 100) else "0"
                                        Row(Modifier.fillMaxWidth().background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp)).padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(Modifier.weight(1f)) {
                                                Text(cat?.name ?: catId, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                                                Text(cat?.nbrCode ?: "", fontSize = 10.sp, color = Color(0xFF6B7280))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("৳${fmt(amt)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                                Text("$pct%", fontSize = 10.sp, color = Color(0xFF6B7280))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Expense Analysis
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                                Icon(Icons.AutoMirrored.Filled.TrendingDown, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Expense Analysis", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                            if (analysis.expenses.isEmpty()) {
                                Text("No expense transactions found", fontSize = 12.sp, color = Color(0xFF9CA3AF), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    analysis.expenses.entries.sortedByDescending { it.value }.take(10).forEach { (catId, amt) ->
                                        val cat = TaxCategoryUtils.ALL_EXPENSE_TAX_CATEGORIES.find { it.id == catId }
                                        val pct = if (analysis.totalExpenses > 0) String.format(Locale.US, "%.1f", (amt / analysis.totalExpenses) * 100) else "0"
                                        Row(Modifier.fillMaxWidth().background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp)).padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(Modifier.weight(1f)) {
                                                Text(cat?.name ?: catId, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                                                Text(cat?.nbrCode ?: "", fontSize = 10.sp, color = Color(0xFF6B7280))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("৳${fmt(amt)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                                Text("$pct%", fontSize = 10.sp, color = Color(0xFF6B7280))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Assets & Liabilities[cite: 15] ───
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Assets Column
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Assets", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Button(onClick = { viewModel.openAssetModal() }, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(28.dp)) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("Add", fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (state.assets.isEmpty()) {
                                Text("No assets added", fontSize = 11.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(vertical = 12.dp))
                            } else {
                                state.assets.forEach { asset ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0xFFF9FAFB), RoundedCornerShape(6.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(asset.description, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                                            Text(TaxCategoryUtils.ALL_EXPENSE_TAX_CATEGORIES.find { it.id == asset.assetType }?.name ?: asset.assetType, fontSize = 10.sp, color = Color(0xFF6B7280))
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("৳${fmt(asset.currentValue)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                            IconButton(onClick = { viewModel.openAssetModal(asset) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Edit, null, tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp)) }
                                            IconButton(onClick = { viewModel.requestDelete("asset", asset) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp)) }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Assets", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Text("৳${fmt(taxYear.totalAssets)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            }
                        }
                    }

                    // Liabilities Column
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Liabilities", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Button(onClick = { viewModel.openLiabilityModal() }, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(28.dp)) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)); Text("Add", fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (state.liabilities.isEmpty()) {
                                Text("No liabilities added", fontSize = 11.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(vertical = 12.dp))
                            } else {
                                state.liabilities.forEach { l ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0xFFF9FAFB), RoundedCornerShape(6.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(l.description, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                                            Text(l.liabilityType, fontSize = 10.sp, color = Color(0xFF6B7280))
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("৳${fmt(l.principal)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                            IconButton(onClick = { viewModel.openLiabilityModal(l) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Edit, null, tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp)) }
                                            IconButton(onClick = { viewModel.requestDelete("liability", l) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp)) }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Liabilities", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Text("৳${fmt(state.liabilities.sumOf { it.principal })}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEA580C))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }

        // ─── Modals[cite: 15] ───
        if (state.showAssetModal) {
            AssetModal(
                editing = state.editingAsset,
                isSaving = state.isSaving,
                onDismiss = { viewModel.closeAssetModal() },
                onSave = { type, desc, valAmt -> viewModel.saveAsset(type, desc, valAmt) }
            )
        }

        if (state.showLiabilityModal) {
            LiabilityModal(
                editing = state.editingLiability,
                isSaving = state.isSaving,
                onDismiss = { viewModel.closeLiabilityModal() },
                onSave = { type, desc, principal, lender -> viewModel.saveLiability(type, desc, principal, lender) }
            )
        }

        if (state.showProfileModal) {
            ProfileModal(
                profile = state.profile,
                isSaving = state.isSaving,
                onDismiss = { viewModel.closeProfileModal() },
                onSave = { name, tin -> viewModel.saveProfile(name, tin) }
            )
        }

        if (state.itemToDelete != null) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelDelete() },
                icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626)) },
                title = { Text("Confirm Deletion") },
                text = { Text("Are you sure you want to delete this record? This action cannot be undone.") },
                confirmButton = { Button(onClick = { viewModel.confirmDelete() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete") } },
                dismissButton = { OutlinedButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") } }
            )
        }
    }
}

// ─── Helpers ───

@Composable
private fun StatusBadge(status: String) {
    val (bg, fg, text) = when (status) {
        "filed" -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Filed")
        "in_review" -> Triple(Color(0xFFDBEAFE), Color(0xFF1D4ED8), "In Review")
        else -> Triple(Color(0xFFF3F4F6), Color(0xFF374151), "Draft")
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun MetricBox(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).padding(10.dp)) {
        Text(label, fontSize = 10.sp, color = Color(0xFF6B7280))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 2.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetModal(editing: TaxAsset?, isSaving: Boolean, onDismiss: () -> Unit, onSave: (String, String, Double) -> Unit) {
    var type by remember { mutableStateOf(editing?.assetType ?: "cash_bank") }
    var desc by remember { mutableStateOf(editing?.description ?: "") }
    var valAmt by remember { mutableStateOf(editing?.currentValue?.toString() ?: "") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(Modifier.padding(16.dp).fillMaxWidth().imePadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (editing != null) "Edit Asset" else "Add Asset", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = valAmt, onValueChange = { valAmt = it }, label = { Text("Current Value (৳) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { onSave(type, desc, valAmt.toDoubleOrNull() ?: 0.0) }, enabled = !isSaving, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiabilityModal(editing: TaxLiability?, isSaving: Boolean, onDismiss: () -> Unit, onSave: (String, String, Double, String) -> Unit) {
    var type by remember { mutableStateOf(editing?.liabilityType ?: "bank_loan") }
    var desc by remember { mutableStateOf(editing?.description ?: "") }
    var principal by remember { mutableStateOf(editing?.principal?.toString() ?: "") }
    var lender by remember { mutableStateOf(editing?.lender ?: "") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(Modifier.padding(16.dp).fillMaxWidth().imePadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (editing != null) "Edit Liability" else "Add Liability", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = principal, onValueChange = { principal = it }, label = { Text("Principal Amount (৳) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lender, onValueChange = { lender = it }, label = { Text("Lender Name (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { onSave(type, desc, principal.toDoubleOrNull() ?: 0.0, lender) }, enabled = !isSaving, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileModal(profile: TaxProfile, isSaving: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(profile.taxpayerName) }
    var tin by remember { mutableStateOf(profile.tin) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(Modifier.padding(16.dp).fillMaxWidth().imePadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Tax Profile Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Taxpayer Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = tin, onValueChange = { tin = it }, label = { Text("TIN Number") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { onSave(name, tin) }, enabled = !isSaving, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val out = SimpleDateFormat("MMM d, yyyy", Locale.US)
        out.format(sdf.parse(dateStr)!!)
    } catch (_: Exception) { dateStr }
}