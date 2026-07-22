package com.hasan.nisabwallet.ui.screens.goals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ─── Theme Colors ───
private val Emerald500 = Color(0xFF10B981)
private val Green500 = Color(0xFF22C55E)
private val Pink500 = Color(0xFFEC4899)
private val Rose500 = Color(0xFFF43F5E)
private val Blue500 = Color(0xFF3B82F6)
private val Cyan500 = Color(0xFF06B6D4)
private val Red500 = Color(0xFFEF4444)
private val Orange500 = Color(0xFFF97316)
private val Purple500 = Color(0xFFA855F7)
private val Indigo500 = Color(0xFF6366F1)
private val Yellow500 = Color(0xFFEAB308)
private val Gray500 = Color(0xFF6B7280)
private val Gray600 = Color(0xFF4B5563)

private fun getCategoryGradient(cat: String): Brush {
    return when (cat) {
        "hajj" -> Brush.linearGradient(listOf(Emerald500, Green500))
        "marriage" -> Brush.linearGradient(listOf(Pink500, Rose500))
        "education" -> Brush.linearGradient(listOf(Blue500, Cyan500))
        "emergency" -> Brush.linearGradient(listOf(Red500, Orange500))
        "house" -> Brush.linearGradient(listOf(Purple500, Pink500))
        "car" -> Brush.linearGradient(listOf(Indigo500, Blue500))
        "business" -> Brush.linearGradient(listOf(Orange500, Yellow500))
        else -> Brush.linearGradient(listOf(Gray500, Gray600))
    }
}

private fun getCategoryIcon(cat: String): String {
    return when (cat) {
        "hajj" -> "🕋"
        "marriage" -> "💍"
        "education" -> "🎓"
        "emergency" -> "🆘"
        "house" -> "🏠"
        "car" -> "🚗"
        "business" -> "💼"
        else -> "🎯"
    }
}

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel = hiltViewModel(),
    triggerFabAdd: Long = 0L,
    onAddHandled: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {} // Default empty block to prevent crashes if missing in NavGraph
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    // ─── Global FAB Trigger Listener ───
    LaunchedEffect(triggerFabAdd) {
        if (triggerFabAdd > 0L) {
            viewModel.openGoalModal()
            onAddHandled()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GoalsEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            Surface(color = Color(0xFFF9FAFB), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Financial Goals", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            Text("Virtual allocations from your accounts", fontSize = 12.sp, color = Color(0xFF6B7280))
                        }
                        Button(
                            onClick = { viewModel.openGoalModal() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Goal", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF111827)) }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Summary Cards ───
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Allocated", fmt(state.totalAllocated), "Virtual savings", Icons.Default.Savings, Color(0xFF16A34A), Modifier.weight(1f))
                    SummaryCard("Target", fmt(state.totalTarget), if(state.totalTarget>0) "${String.format(Locale.US, "%.1f", (state.totalAllocated/state.totalTarget)*100)}% achieved" else "0% achieved", Icons.Default.TrackChanges, Color(0xFF2563EB), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Active", state.activeCount.toString(), "In progress", Icons.Default.Flag, Color(0xFFEA580C), Modifier.weight(1f))
                    SummaryCard("Done", state.completedCount.toString(), "Completed", Icons.Default.CheckCircle, Color(0xFF059669), Modifier.weight(1f))
                }
            }

            // ─── Account Allocations Warning Card ───
            if (state.accountsWithAllocations.any { it.allocated > 0 }) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFEFF6FF), Color(0xFFEEF2FF)))).border(2.dp, Color(0xFFBFDBFE), RoundedCornerShape(12.dp)).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(modifier = Modifier.size(44.dp).background(Color(0xFF2563EB), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Account Allocations", fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A), fontSize = 14.sp)
                                Text("Goals are virtual allocations. Money stays in your accounts.", fontSize = 12.sp, color = Color(0xFF1D4ED8), modifier = Modifier.padding(bottom = 12.dp))

                                state.accountsWithAllocations.filter { it.allocated > 0 }.forEach { acc ->
                                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Color.White.copy(alpha=0.8f), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(10.dp)).padding(12.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                            Column(Modifier.weight(1f)) {
                                                Text(acc.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("Total: ৳${CurrencyFormatter.formatBDT(acc.balance)}", fontSize = 11.sp, color = Color(0xFF4B5563))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Available", fontSize = 11.sp, color = Color(0xFF4B5563))
                                                Text("৳${CurrencyFormatter.formatBDT(acc.available)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if(acc.available < 0) Color(0xFFDC2626) else Color(0xFF16A34A))
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFFDBEAFE), modifier = Modifier.padding(vertical = 8.dp))
                                        Text("Allocated: ৳${CurrencyFormatter.formatBDT(acc.allocated)}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E40AF), modifier = Modifier.padding(bottom = 6.dp))
                                        acc.activeGoals.forEach { g ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).background(Color(0xFFEFF6FF), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${getCategoryIcon(g.category)} ${g.goalName}", fontSize = 11.sp, color = Color(0xFF1D4ED8), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                Text("৳${CurrencyFormatter.formatBDT(g.currentAmount)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E3A8A))
                                            }
                                        }
                                        if (acc.available < 0) {
                                            HorizontalDivider(color = Color(0xFFFECACA), modifier = Modifier.padding(vertical = 8.dp))
                                            Text("⚠️ Warning: You've spent ৳${CurrencyFormatter.formatBDT(kotlin.math.abs(acc.available))} of your goal money!", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFDC2626))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Filter Tabs ───
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFE5E7EB)), modifier = Modifier.fillMaxWidth()) {
                    LazyRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("all" to "All", "active" to "Active", "completed" to "Completed").forEach { (v, l) ->
                            item {
                                val sel = state.filterStatus == v
                                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if(sel) Color(0xFF111827) else Color(0xFFF3F4F6)).clickable { viewModel.setFilter(v) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(l, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if(sel) Color.White else Color(0xFF374151))
                                }
                            }
                        }
                    }
                }
            }

            // ─── Goal List ───
            val filtered = state.goals.filter { if(state.filterStatus == "all") true else it.status == state.filterStatus }

            if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp)).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.TrackChanges, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No goals found", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                        Text("Create your first goal to start saving", fontSize = 13.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 4.dp))
                        Button(onClick = { viewModel.openGoalModal() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)), shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(top = 16.dp)) {
                            Text("Create Goal")
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { goal ->
                    GoalItemCard(
                        goal = goal,
                        metrics = viewModel.calculateMetrics(goal),
                        accountName = state.accountsWithAllocations.find { it.id == goal.linkedAccountId }?.name ?: "Unknown",
                        onDeposit = { viewModel.openDepositModal(goal) },
                        onWithdraw = { viewModel.openWithdrawModal(goal) },
                        onDetail = { onNavigateToDetail(goal.id) },
                        onEdit = { viewModel.openGoalModal(goal) },
                        onDelete = { viewModel.openDeleteModal(goal) }
                    )
                }
            }
        }

        // ─── Modals ───
        if (state.showGoalModal) {
            GoalFormModal(
                form = state.goalForm, isSaving = state.isSaving, isEditing = state.editingGoal != null,
                accounts = state.accountsWithAllocations,
                onUpdate = { viewModel.updateGoalForm(it) }, onDismiss = { viewModel.closeGoalModal() }, onSave = { viewModel.saveGoal() }
            )
        }

        if (state.showDepositModal && state.selectedGoal != null) {
            TransactionModal("Allocate to Goal", state.selectedGoal!!, state.txForm, state.accountsWithAllocations, state.isSaving, true,
                onUpdate = { viewModel.updateTxForm(it) }, onDismiss = { viewModel.closeDepositModal() }, onSave = { viewModel.submitDeposit() })
        }

        if (state.showWithdrawModal && state.selectedGoal != null) {
            TransactionModal("Remove from Goal", state.selectedGoal!!, state.txForm, state.accountsWithAllocations, state.isSaving, false,
                onUpdate = { viewModel.updateTxForm(it) }, onDismiss = { viewModel.closeWithdrawModal() }, onSave = { viewModel.submitWithdraw() })
        }

        if (state.showDeleteModal && state.selectedGoal != null) {
            AlertDialog(
                onDismissRequest = { viewModel.closeDeleteModal() },
                icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444)) },
                title = { Text("Delete Goal?") },
                text = { Text("This will permanently delete \"${state.selectedGoal!!.goalName}\" and remove its transaction history. The allocated amount will become available in your account.") },
                confirmButton = { Button(onClick = { viewModel.deleteGoal() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete Goal") } },
                dismissButton = { OutlinedButton(onClick = { viewModel.closeDeleteModal() }) { Text("Cancel") } }
            )
        }
    }
}

// ─── Component Helpers ───

@Composable
private fun SummaryCard(title: String, value: String, subtitle: String, icon: ImageVector, tint: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
                Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            }
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.padding(top = 4.dp))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun GoalItemCard(goal: GoalItem, metrics: GoalMetrics, accountName: String, onDeposit: () -> Unit, onWithdraw: () -> Unit, onDetail: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB)), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(48.dp).background(getCategoryGradient(goal.category), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(getCategoryIcon(goal.category), fontSize = 24.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(goal.goalName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(8.dp))
                            val pColor = when(goal.priority) { "high" -> Color(0xFFDC2626) "medium" -> Color(0xFFD97706) else -> Color(0xFF059669) }
                            val pBg = when(goal.priority) { "high" -> Color(0xFFFEF2F2) "medium" -> Color(0xFFFFFBEB) else -> Color(0xFFECFDF5) }
                            Surface(shape = RoundedCornerShape(50), color = pBg) { Text(goal.priority.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = pColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                            if (goal.status == "completed") {
                                Spacer(Modifier.width(4.dp))
                                Surface(shape = RoundedCornerShape(50), color = Color(0xFFD1FAE5)) { Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF047857), modifier = Modifier.size(10.dp)); Spacer(Modifier.width(2.dp)); Text("DONE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857)) } }
                            }
                        }
                        Text(goal.description.ifBlank { "Target by ${goal.targetDate}" }, fontSize = 12.sp, color = Color(0xFF4B5563), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.Default.AccountBalance, null, tint = Color(0xFF2563EB), modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text(accountName, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2563EB)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (goal.status == "active") {
                        IconButton(onClick = onDeposit, modifier = Modifier.size(28.dp).background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))) { Icon(Icons.Default.ArrowUpward, null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = onWithdraw, modifier = Modifier.size(28.dp).background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))) { Icon(Icons.Default.ArrowDownward, null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp)) }
                    }
                    IconButton(onClick = onDetail, modifier = Modifier.size(28.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Visibility, null, tint = Color(0xFF4B5563), modifier = Modifier.size(16.dp)) }
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Edit, null, tint = Color(0xFF4B5563), modifier = Modifier.size(16.dp)) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp)) }
                }
            }

            Column(Modifier.padding(vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("৳${CurrencyFormatter.formatBDT(goal.currentAmount)} / ৳${CurrencyFormatter.formatBDT(goal.targetAmount)}", fontSize = 11.sp, color = Color(0xFF4B5563))
                    Text("${metrics.percentageComplete}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                }
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(Color(0xFFE5E7EB), CircleShape)) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(metrics.percentageComplete / 100f).background(getCategoryGradient(goal.category), CircleShape))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Days Left", metrics.daysRemaining.toString(), Icons.Default.Schedule, Color(0xFF111827), Modifier.weight(1f))
                StatPill("Monthly Need", "৳${CurrencyFormatter.formatBDT(metrics.monthlyRequired)}", null, Color(0xFF2563EB), Modifier.weight(1f))
                StatPill("Status", if(metrics.onTrack) "Track" else "Behind", if(metrics.onTrack) Icons.Default.CheckCircle else Icons.Default.Warning, if(metrics.onTrack) Color(0xFF16A34A) else Color(0xFFD97706), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, icon: ImageVector?, valueColor: Color, modifier: Modifier) {
    Column(modifier.background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).padding(8.dp)) {
        Text(label, fontSize = 10.sp, color = Color(0xFF6B7280))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            if (icon != null) { Icon(icon, null, tint = valueColor, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)) }
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalFormModal(form: GoalForm, isSaving: Boolean, isEditing: Boolean, accounts: List<GoalAccount>, onUpdate: (GoalForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if(isEditing) "Edit Goal" else "Create New Goal", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider()
            Column(Modifier.weight(1f, fill=false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = form.goalName, onValueChange = { onUpdate(form.copy(goalName = it)) }, label = { Text("Goal Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalDropdown("Category *", form.category, listOf("hajj" to "🕋 Hajj", "marriage" to "💍 Marriage", "education" to "🎓 Education", "emergency" to "🆘 Emergency", "house" to "🏠 House", "car" to "🚗 Car", "business" to "💼 Business", "other" to "🎯 Other"), { onUpdate(form.copy(category = it)) }, Modifier.weight(1f))
                    GoalDropdown("Priority *", form.priority, listOf("high" to "High", "medium" to "Medium", "low" to "Low"), { onUpdate(form.copy(priority = it)) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = form.targetAmount, onValueChange = { onUpdate(form.copy(targetAmount = it)) }, label = { Text("Target (৳) *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                    if (!isEditing) OutlinedTextField(value = form.currentAmount, onValueChange = { onUpdate(form.copy(currentAmount = it)) }, label = { Text("Initial (৳)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateSelectionField("Target Date *", form.targetDate, { onUpdate(form.copy(targetDate = it)) }, Modifier.weight(1f))
                    OutlinedTextField(value = form.monthlyContribution, onValueChange = { onUpdate(form.copy(monthlyContribution = it)) }, label = { Text("Monthly (৳)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                }
                Column {
                    GoalDropdown("Allocate From Account *", form.linkedAccountId, accounts.map { it.id to "${it.name} (Avail: ৳${CurrencyFormatter.formatBDT(it.available)})" }, { onUpdate(form.copy(linkedAccountId = it)) }, Modifier.fillMaxWidth())
                    Text("💡 Money will stay in this account but marked as allocated", fontSize = 11.sp, color = Color(0xFF2563EB), modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                }
                OutlinedTextField(value = form.description, onValueChange = { onUpdate(form.copy(description = it)) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = form.enableNotifications, onCheckedChange = { onUpdate(form.copy(enableNotifications = it)) })
                    Text("Enable milestone notifications", fontSize = 13.sp, color = Color(0xFF374151))
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))) {
                    if(isSaving) CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp) else Text(if(isEditing) "Update Goal" else "Create Goal")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionModal(title: String, goal: GoalItem, form: GoalTransactionForm, accounts: List<GoalAccount>, isSaving: Boolean, isDeposit: Boolean, onUpdate: (GoalTransactionForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text(goal.goalName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                    Text(if(isDeposit) "Current: ৳${CurrencyFormatter.formatBDT(goal.currentAmount)} / ৳${CurrencyFormatter.formatBDT(goal.targetAmount)}" else "Allocated: ৳${CurrencyFormatter.formatBDT(goal.currentAmount)}", fontSize = 12.sp, color = Color(0xFF4B5563), modifier = Modifier.padding(top = 2.dp))
                }
                Column {
                    OutlinedTextField(value = form.amount, onValueChange = { onUpdate(form.copy(amount = it)) }, label = { Text("Amount (৳) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = if(isDeposit) Color(0xFF16A34A) else Color(0xFFDC2626)))
                    val amt = form.amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        Text(if(isDeposit) "New allocation: ৳${CurrencyFormatter.formatBDT(goal.currentAmount + amt)}" else "Remaining allocation: ৳${CurrencyFormatter.formatBDT(goal.currentAmount - amt)}", fontSize = 11.sp, color = Color(0xFF4B5563), modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                    }
                }
                GoalDropdown(if(isDeposit) "From Account *" else "To Account *", form.accountId, accounts.map { it.id to "${it.name} (Avail: ৳${CurrencyFormatter.formatBDT(it.available)})" }, { onUpdate(form.copy(accountId = it)) }, Modifier.fillMaxWidth())
                DateSelectionField("Date *", form.date, { onUpdate(form.copy(date = it)) }, Modifier.fillMaxWidth())
                OutlinedTextField(value = form.description, onValueChange = { onUpdate(form.copy(description = it)) }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
                if (!isDeposit) {
                    Text("⚠️ Removing allocation will reduce your goal progress", fontSize = 11.sp, color = Color(0xFF92400E), modifier = Modifier.fillMaxWidth().background(Color(0xFFFFFBEB), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp)).padding(12.dp))
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = if(isDeposit) Color(0xFF16A34A) else Color(0xFFDC2626))) {
                    if(isSaving) CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp) else Text(if(isDeposit) "Allocate" else "Remove", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDropdown(label: String, selectedValue: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.find { it.first == selectedValue }?.second ?: "Select"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(value = display, onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(10.dp))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color.White)) {
            options.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(id); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionField(label: String, dateString: String, onDateSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(value = dateString, onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true, trailingIcon = { Icon(Icons.Default.CalendarToday, null) })
        Box(modifier = Modifier.matchParentSize().clickable { showPicker = true })
    }
    if (showPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(dateString)?.time } catch(_: Exception) { null })
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { millis -> onDateSelected(SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))) }; showPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}