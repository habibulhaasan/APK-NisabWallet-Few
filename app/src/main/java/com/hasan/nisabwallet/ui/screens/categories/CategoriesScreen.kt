package com.hasan.nisabwallet.ui.screens.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var categoryToDelete by remember { mutableStateOf<CategoryItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is CategoriesEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
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
                                Text("Categories", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                            Text("Organize your transactions", fontSize = 13.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(start = 24.dp))
                        }
                    }
                }

                // Action Buttons
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.loadDefaultCategories() },
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
                            Text("Add Category", fontSize = 13.sp)
                        }
                    }
                }

                // Legend
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(50), color = Color(0xFFD1FAE5), border = BorderStroke(1.dp, Color(0xFF6EE7B7))) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, null, tint = Color(0xFF047857), modifier = Modifier.size(10.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("System", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF047857))
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Required — cannot be deleted", fontSize = 11.sp, color = Color(0xFF6B7280))
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(50), color = Color(0xFFEFF6FF), border = BorderStroke(1.dp, Color(0xFFBFDBFE))) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF2563EB), modifier = Modifier.size(10.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Default", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2563EB))
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Pre-loaded, can be deleted", fontSize = 11.sp, color = Color(0xFF6B7280))
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
                } else if (state.categories.isEmpty()) {
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
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(48.dp), tint = Color(0xFF9CA3AF))
                                Spacer(Modifier.height(16.dp))
                                Text("No categories yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563))
                                Text("Load defaults to get started with all required categories", fontSize = 13.sp, color = Color(0xFF9CA3AF), textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    val incomes = state.categories.filter { it.type == "Income" }
                    val expenses = state.categories.filter { it.type == "Expense" }

                    // Income Section
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                                    Icon(Icons.Default.LocalOffer, null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Income Categories (${incomes.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                }
                                if (incomes.isEmpty()) {
                                    Text("No income categories yet", fontSize = 13.sp, color = Color(0xFF6B7280), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                                } else {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        incomes.forEach { cat ->
                                            CategoryCard(
                                                category = cat,
                                                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(0.48f),
                                                onEdit = { viewModel.openEditModal(it) },
                                                onDelete = { categoryToDelete = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Expense Section
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                                    Icon(Icons.Default.LocalOffer, null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Expense Categories (${expenses.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                }
                                if (expenses.isEmpty()) {
                                    Text("No expense categories yet", fontSize = 13.sp, color = Color(0xFF6B7280), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                                } else {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        expenses.forEach { cat ->
                                            CategoryCard(
                                                category = cat,
                                                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(0.48f),
                                                onEdit = { viewModel.openEditModal(it) },
                                                onDelete = { categoryToDelete = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    item { Spacer(Modifier.height(40.dp)) }
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
        AddEditCategoryModal(
            form = state.form,
            isSaving = state.isSaving,
            onUpdateForm = { viewModel.updateForm(it) },
            onDismiss = { viewModel.closeModal() },
            onSave = { viewModel.saveCategory() }
        )
    }

    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Delete Category") },
            text = { Text("Are you sure you want to delete \"${categoryToDelete?.name}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { categoryToDelete?.let { viewModel.deleteCategory(it) }; categoryToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { categoryToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CategoryCard(
    category: CategoryItem,
    modifier: Modifier = Modifier,
    onEdit: (CategoryItem) -> Unit,
    onDelete: (CategoryItem) -> Unit
) {
    val catColor = runCatching { Color(android.graphics.Color.parseColor(category.color)) }.getOrDefault(Color.Gray)
    
    val bgColor = if (category.isSystem) Color(0xFFECFDF5).copy(alpha = 0.4f) else Color.Transparent
    val borderColor = if (category.isSystem) Color(0xFFA7F3D0) else Color(0xFFE5E7EB)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Badges
            if (category.isSystem) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                    shape = RoundedCornerShape(50), color = Color(0xFFD1FAE5), border = BorderStroke(1.dp, Color(0xFF6EE7B7))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = Color(0xFF047857), modifier = Modifier.size(8.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("System", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                    }
                }
            } else if (category.isDefault) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                    shape = RoundedCornerShape(50), color = Color(0xFFEFF6FF), border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF2563EB), modifier = Modifier.size(8.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Default", fontSize = 8.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2563EB))
                    }
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(28.dp).background(catColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(10.dp).background(catColor, CircleShape))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(category.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                        if (category.isRiba) {
                            Surface(color = Color(0xFFFFFBEB), border = BorderStroke(1.dp, Color(0xFFFDE68A)), shape = RoundedCornerShape(50)) {
                                Text("⚠ RIBA", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { onEdit(category) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { onDelete(category) }, 
                        enabled = !category.isSystem,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, "Delete", tint = if (category.isSystem) Color(0xFFE5E7EB) else Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEditCategoryModal(
    form: CategoryForm,
    isSaving: Boolean,
    onUpdateForm: ((CategoryForm) -> CategoryForm) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val colorsList = listOf("#EF4444", "#F59E0B", "#10B981", "#3B82F6", "#6366F1", "#8B5CF6", "#EC4899", "#06B6D4", "#84CC16", "#F97316")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (form.id != null) "Edit Category" else "Add Category", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }

            if (form.isSystem) {
                Surface(
                    color = Color(0xFFECFDF5),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Lock, null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("This is a system category. You can change the colour but not the name or type.", fontSize = 12.sp, color = Color(0xFF065F46))
                    }
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { n -> onUpdateForm { it.copy(name = n) } },
                label = { Text("Name") },
                placeholder = { Text("e.g. Salary, Groceries") },
                enabled = !form.isSystem,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledContainerColor = Color(0xFFF9FAFB),
                    disabledTextColor = Color(0xFF9CA3AF)
                )
            )

            var typeExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedTextField(
                    value = form.type,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !form.isSystem,
                    label = { Text("Type") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledContainerColor = Color(0xFFF9FAFB),
                        disabledTextColor = Color(0xFF9CA3AF)
                    )
                )
                if (!form.isSystem) {
                    Box(modifier = Modifier.matchParentSize().clickable { typeExpanded = true })
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf("Income", "Expense").forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { onUpdateForm { it.copy(type = t) }; typeExpanded = false })
                        }
                    }
                }
            }

            Column {
                Text("Color", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151), modifier = Modifier.padding(bottom = 8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorsList.forEach { hex ->
                        val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false).fillMaxWidth(0.18f) // 5 per row roughly
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(c)
                                .then(if (form.color == hex) Modifier.border(3.dp, Color(0xFF111827), RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { onUpdateForm { it.copy(color = hex) } }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...", fontSize = 15.sp)
                } else {
                    Text(if (form.id != null) "Update Category" else "Add Category", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}