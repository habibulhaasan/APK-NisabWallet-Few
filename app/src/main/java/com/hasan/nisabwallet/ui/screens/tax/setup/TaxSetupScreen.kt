package com.hasan.nisabwallet.ui.screens.tax.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.TaxCategoryUtils
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxSetupScreen(
    viewModel: TaxSetupViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSetupComplete: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TaxSetupEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                is TaxSetupEvent.SetupComplete -> onSetupComplete()
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
                    Column(Modifier.weight(1f)) {
                        Text("Category Mappings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text("Map your categories to NBR tax codes", fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF111827)) }
            return@Scaffold
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Smart Auto-Mapping", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E3A8A))
                            Text("We've automatically suggested NBR tax categories based on your category names. Please review them and make adjustments if necessary before saving.", fontSize = 12.sp, color = Color(0xFF1D4ED8), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                // Expenses Section
                val expenses = state.mappings.filter { it.userCategoryType == "Expense" }
                if (expenses.isNotEmpty()) {
                    item { Text("Expense Categories", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(top = 8.dp)) }
                    items(expenses, key = { it.userCategoryId }) { mapping ->
                        MappingCard(mapping, TaxCategoryUtils.ALL_EXPENSE_TAX_CATEGORIES, onUpdate = { newId -> viewModel.updateMapping(mapping.userCategoryId, newId) })
                    }
                }

                // Income Section
                val incomes = state.mappings.filter { it.userCategoryType == "Income" }
                if (incomes.isNotEmpty()) {
                    item { Text("Income Categories", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(top = 16.dp)) }
                    items(incomes, key = { it.userCategoryId }) { mapping ->
                        MappingCard(mapping, TaxCategoryUtils.INCOME_TAX_CATEGORIES, onUpdate = { newId -> viewModel.updateMapping(mapping.userCategoryId, newId) })
                    }
                }
            }

            // Fixed Bottom Bar
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color.White, shadowElevation = 16.dp
            ) {
                Button(
                    onClick = { viewModel.saveMappings() },
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp)
                ) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp)
                    else Text("Save Tax Mappings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingCard(
    mapping: CategoryMappingState,
    taxOptions: List<com.hasan.nisabwallet.core.util.TaxCategory>,
    onUpdate: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTaxCat = taxOptions.find { it.id == mapping.selectedTaxCategoryId }
    
    val confColor = when (mapping.confidence) {
        "high" -> Color(0xFF16A34A)
        "medium" -> Color(0xFFD97706)
        else -> Color(0xFFDC2626)
    }
    val confBg = when (mapping.confidence) {
        "high" -> Color(0xFFDCFCE7)
        "medium" -> Color(0xFFFEF3C7)
        else -> Color(0xFFFEE2E2)
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(mapping.userCategoryName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                Surface(color = confBg, shape = RoundedCornerShape(50)) {
                    Text(mapping.confidence.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = confColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            
            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedTaxCat?.name ?: "Select Tax Category",
                    onValueChange = {}, readOnly = true,
                    label = { Text("NBR Tax Classification") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White)) {
                    taxOptions.forEach { taxCat ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("${taxCat.name} (${taxCat.nbrCode})", fontWeight = FontWeight.Medium)
                                    Text(taxCat.description, fontSize = 11.sp, color = Color.Gray)
                                }
                            }, 
                            onClick = { onUpdate(taxCat.id); expanded = false }
                        )
                    }
                }
            }

            if (mapping.confidence == "low") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Low confidence. Please review this assignment manually.", fontSize = 11.sp, color = Color(0xFFDC2626))
                }
            }
        }
    }
}