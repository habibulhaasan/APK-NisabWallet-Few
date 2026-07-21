package com.hasan.nisabwallet.ui.screens.cashflow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashflowScreen(
    viewModel: CashflowViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF111827))
        }
        return
    }

    Scaffold(
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            // ─── Frozen Top Bar ───
            Surface(color = Color(0xFFF9FAFB), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(Modifier.clickable { onNavigateBack() }.padding(top = 8.dp, bottom = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF4B5563), modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Cashflow", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Track money moving in and out", fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Filter & Date Nav Bar
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("weekly" to "Weekly", "monthly" to "Monthly", "quarterly" to "Quarterly", "yearly" to "Yearly", "custom" to "Custom").forEach { (v, l) ->
                                val sel = state.period == v
                                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if(sel) Color(0xFF111827) else Color(0xFFF3F4F6)).clickable{ viewModel.setPeriod(v) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(l, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if(sel) Color.White else Color(0xFF4B5563))
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            if (state.showNav) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.goPrev() }, modifier = Modifier.size(32.dp).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))) {
                                        Icon(Icons.Default.ChevronLeft, null, tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp))
                                    }
                                    Text(state.dateLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 120.dp).padding(horizontal = 8.dp))
                                    IconButton(onClick = { viewModel.goNext() }, modifier = Modifier.size(32.dp).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))) {
                                        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF4B5563), modifier = Modifier.size(18.dp))
                                    }
                                }
                            } else if (state.period == "weekly") {
                                Text(state.dateLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }

                            if (state.period == "custom") {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DateSelectionField(label = "Start", dateString = state.customStart, onDateSelected = { viewModel.setCustomDates(it, state.customEnd) }, modifier = Modifier.weight(1f))
                                    Text("→", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                                    DateSelectionField(label = "End", dateString = state.customEnd, onDateSelected = { viewModel.setCustomDates(state.customStart, it) }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Summary Cards
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryBox("Total Income", fmt(state.totalIncome), Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF16A34A), Color(0xFFECFDF5), Modifier.weight(1f))
                    SummaryBox("Total Expense", fmt(state.totalExpense), Icons.AutoMirrored.Filled.TrendingDown, Color(0xFFDC2626), Color(0xFFFEF2F2), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val netColor = if (state.netFlow >= 0) Color(0xFF2563EB) else Color(0xFFEA580C)
                    val netBg = if (state.netFlow >= 0) Color(0xFFEFF6FF) else Color(0xFFFFF7ED)
                    SummaryBox("Net Flow", "${if(state.netFlow>=0) "+" else ""}${fmt(state.netFlow)}", Icons.Default.SyncAlt, netColor, netBg, Modifier.weight(1f))

                    val saveColor = if (state.savingsRate >= 0) Color(0xFF7C3AED) else Color(0xFFDC2626)
                    val saveBg = if (state.savingsRate >= 0) Color(0xFFF5F3FF) else Color(0xFFFEF2F2)
                    SummaryBox("Savings Rate", "${String.format(Locale.US, "%.1f", state.savingsRate)}%", Icons.Default.Savings, saveColor, saveBg, Modifier.weight(1f))
                }
            }

            // Charts Section
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Monthly cashflow (last 6 months)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 16.dp))
                        TrendBarChart(state.last6Months)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Expense breakdown", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 16.dp))
                        if (state.expensesByCategory.isEmpty()) {
                            Text("No expenses in this period", fontSize = 12.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally))
                        } else {
                            val maxCat = state.expensesByCategory.maxOf { it.amount }.coerceAtLeast(1.0)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                state.expensesByCategory.forEach { cat ->
                                    val clr = try { Color(android.graphics.Color.parseColor(cat.color)) } catch (_: Exception) { Color.Gray }
                                    Column {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(10.dp).background(clr, CircleShape))
                                                Spacer(Modifier.width(8.dp))
                                                Text(cat.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151))
                                            }
                                            Text(fmt(cat.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        LinearProgressIndicator(progress = { (cat.amount / maxCat).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = clr, trackColor = Color(0xFFF3F4F6))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Monthly Table
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column {
                        Text("Cashflow by month", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(16.dp))
                        HorizontalDivider(color = Color(0xFFF3F4F6))

                        if (state.monthRows.isEmpty()) {
                            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(40.dp), tint = Color(0xFFD1D5DB))
                                Spacer(Modifier.height(12.dp))
                                Text("No transactions in this period", fontSize = 13.sp, color = Color(0xFF6B7280))
                            }
                        } else {
                            Row(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Month", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), modifier = Modifier.weight(1f))
                                Text("Net Flow", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                            }
                            HorizontalDivider(color = Color(0xFFE5E7EB))

                            Column {
                                state.monthRows.forEach { row ->
                                    val netColor = if (row.net >= 0) Color(0xFF2563EB) else Color(0xFFEA580C)
                                    val netPrefix = if (row.net >= 0) "+" else ""
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(row.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                            Row(Modifier.padding(top = 2.dp)) {
                                                Text("In: ${fmt(row.income)}", fontSize = 10.sp, color = Color(0xFF16A34A))
                                                Text("  •  Out: ${fmt(row.expense)}", fontSize = 10.sp, color = Color(0xFFDC2626))
                                            }
                                        }
                                        Text("$netPrefix${fmt(row.net)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = netColor, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                                    }
                                    HorizontalDivider(color = Color(0xFFF9FAFB))
                                }
                            }

                            // Total Footer
                            Row(Modifier.fillMaxWidth().background(Color(0xFF111827)).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("TOTAL", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                                val tNetColor = if (state.netFlow >= 0) Color(0xFF93C5FD) else Color(0xFFFDBA74)
                                val tNetPrefix = if (state.netFlow >= 0) "+" else ""
                                Text("$tNetPrefix${fmt(state.netFlow)}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = tNetColor, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ─── UI Components ────────────────────────────────────────────────────────────

@Composable
private fun SummaryBox(title: String, value: String, icon: ImageVector, tint: Color, bg: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(32.dp).background(bg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280))
            }
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.padding(top = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Custom Lightweight Bar Chart ──
@Composable
private fun TrendBarChart(data: List<MonthChartData>) {
    if (data.isEmpty()) {
        Text("No trend data", fontSize = 12.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(vertical = 24.dp))
        return
    }

    val maxVal = data.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1.0)

    // Legend
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(Color(0xFF10B981), RoundedCornerShape(2.dp))); Spacer(Modifier.width(6.dp)); Text("Income", fontSize = 11.sp, color = Color(0xFF6B7280)); Spacer(Modifier.width(16.dp))
        Box(modifier = Modifier.size(10.dp).background(Color(0xFFF87171), RoundedCornerShape(2.dp))); Spacer(Modifier.width(6.dp)); Text("Expense", fontSize = 11.sp, color = Color(0xFF6B7280))
    }

    // Chart Area
    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    val incHeight = (item.income / maxVal).toFloat().coerceIn(0.02f, 1f)
                    val expHeight = (item.expense / maxVal).toFloat().coerceIn(0.02f, 1f)

                    Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        drawRoundRect(
                            color = Color(0xFF10B981),
                            topLeft = Offset(0f, size.height * (1 - incHeight)),
                            size = Size(size.width, size.height * incHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                    Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        drawRoundRect(
                            color = Color(0xFFF87171),
                            topLeft = Offset(0f, size.height * (1 - expHeight)),
                            size = Size(size.width, size.height * expHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(item.label, fontSize = 10.sp, color = Color(0xFF9CA3AF), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item != data.last()) Spacer(Modifier.width(6.dp))
        }
    }
}

// ── Custom Native Date Picker Field ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionField(label: String, dateString: String, onDateSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = dateString, onValueChange = {}, readOnly = true,
            placeholder = { Text(label, fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
            singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
        )
        Box(modifier = Modifier.matchParentSize().clickable { showPicker = true })
    }

    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                sdf.parse(dateString)?.time
            } catch(_: Exception) { null }
        )

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { millis -> onDateSelected(SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))) }; showPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}