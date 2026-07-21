package com.hasan.nisabwallet.ui.screens.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel = hiltViewModel(),
    triggerFabAdd: Long = 0L,
    onAddHandled: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var categoryToDelete by remember { mutableStateOf<CategoryItem?>(null) }

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
                is CategoriesEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
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
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF111827), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Categories", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Organize your transactions", fontSize = 12.sp, color = Color(0xFF6B7280))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddModal() },
                containerColor = Color(0xFF111827),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
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
                                    Icon(Icons.Default.TrendingUp, null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Income Categories (${incomes.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                }
                                if (incomes.isEmpty()) {
                                    Text("No income categories yet", fontSize = 13.sp, color = Color(0xFF6B7280), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        incomes.forEach { cat ->
                                            CategoryCard(
                                                category = cat,
                                                modifier = Modifier.fillMaxWidth(),
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
                                    Icon(Icons.Default.TrendingDown, null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Expense Categories (${expenses.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                }
                                if (expenses.isEmpty()) {
                                    Text("No expense categories yet", fontSize = 13.sp, color = Color(0xFF6B7280), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        expenses.forEach { cat ->
                                            CategoryCard(
                                                category = cat,
                                                modifier = Modifier.fillMaxWidth(),
                                                onEdit = { viewModel.openEditModal(it) },
                                                onDelete = { categoryToDelete = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(36.dp).background(catColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(12.dp).background(catColor, CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = category.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        if (category.isSystem) {
                            Surface(shape = RoundedCornerShape(50), color = Color(0xFFD1FAE5), border = BorderStroke(1.dp, Color(0xFF6EE7B7))) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, null, tint = Color(0xFF047857), modifier = Modifier.size(10.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("System", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                                }
                            }
                        } else if (category.isDefault) {
                            Surface(shape = RoundedCornerShape(50), color = Color(0xFFEFF6FF), border = BorderStroke(1.dp, Color(0xFFBFDBFE))) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF2563EB), modifier = Modifier.size(10.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Default", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2563EB))
                                }
                            }
                        }

                        if (category.isRiba) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = Color(0xFFFFFBEB), border = BorderStroke(1.dp, Color(0xFFFDE68A)), shape = RoundedCornerShape(50)) {
                                Text("⚠ RIBA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onEdit(category) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF6B7280), modifier = Modifier.size(18.dp))
                }
                if (!category.isSystem) {
                    IconButton(onClick = { onDelete(category) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = Color.White
    ) {
        Column(modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {

            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (form.id != null) "Edit Category" else "Add Category", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }
            HorizontalDivider()

            // Scrollable Content area with Keyboard padding handling
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (form.isSystem) {
                    Surface(
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Lock, null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp).padding(top = 2.dp))
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
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledContainerColor = Color(0xFFF9FAFB),
                        disabledTextColor = Color(0xFF9CA3AF)
                    )
                )

                // Selectable Button Row for Type (Replaces Dropdown)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Category Type", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF3F4F6)),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("Income", "Expense").forEach { type ->
                            val isSelected = form.type == type
                            val bgColor = when {
                                isSelected && type == "Income" -> Color(0xFF16A34A)
                                isSelected && type == "Expense" -> Color(0xFFDC2626)
                                else -> Color.Transparent
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgColor)
                                    .clickable(enabled = !form.isSystem) { onUpdateForm { it.copy(type = type) } }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFF4B5563)
                                )
                            }
                        }
                    }
                }

                Column {
                    Text("Select Color", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151), modifier = Modifier.padding(bottom = 12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        colorsList.forEach { hex ->
                            val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
                            val isSelected = form.color == hex

                            Box(
                                modifier = Modifier
                                    .size(48.dp) // Distinct large circles
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { onUpdateForm { it.copy(color = hex) } },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Footer
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving...", fontSize = 14.sp)
                    } else {
                        Text(if (form.id != null) "Update Category" else "Add Category", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}