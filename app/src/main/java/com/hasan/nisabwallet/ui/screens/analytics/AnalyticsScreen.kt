package com.hasan.nisabwallet.ui.screens.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
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
                        Text("Analytics", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Insights into your finances", fontSize = 12.sp, color = Color(0xFF6B7280))
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
            // Filter Bar
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, null, tint = Color(0xFF6B7280), modifier = Modifier.size(20.dp).padding(end = 4.dp))
                            listOf("week" to "Last 7 Days", "month" to "This Month", "year" to "This Year", "custom" to "Custom Range").forEach { (v, l) ->
                                val sel = state.timeRange == v
                                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if(sel) Color(0xFF111827) else Color(0xFFF3F4F6)).clickable{ viewModel.setTimeRange(v) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(l, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if(sel) Color.White else Color(0xFF4B5563))
                                }
                            }
                        }

                        if (state.timeRange == "custom") {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DateSelectionField(label = "Start", dateString = state.customStartDate, onDateSelected = { viewModel.setCustomDates(it, state.customEndDate) }, modifier = Modifier.weight(1f))
                                Text("to", fontSize = 12.sp, color = Color(0xFF6B7280))
                                DateSelectionField(label = "End", dateString = state.customEndDate, onDateSelected = { viewModel.setCustomDates(state.customStartDate, it) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (state.filteredTransactions.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BarChart, null, modifier = Modifier.size(48.dp), tint = Color(0xFFD1D5DB))
                        Spacer(Modifier.height(16.dp))
                        Text("No data for this period", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                        Text("Add transactions to see analytics", fontSize = 13.sp, color = Color(0xFF6B7280))
                    }
                }
            } else {
                // Summary Cards
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryBox("Total Income", fmt(state.totalIncome), Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF16A34A), Modifier.weight(1f))
                        SummaryBox("Total Expenses", fmt(state.totalExpense), Icons.AutoMirrored.Filled.TrendingDown, Color(0xFFDC2626), Modifier.weight(1f))
                    }
                }
                item {
                    val netColor = if (state.netBalance >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                    val netPrefix = if (state.netBalance >= 0) "+" else ""
                    SummaryBox("Net Balance", "$netPrefix${fmt(state.netBalance)}", Icons.Default.AttachMoney, netColor, Modifier.fillMaxWidth())
                }

                // Trend Chart
                item {
                    val label = if (state.timeRange == "week") "Weekly Trend" else if (state.timeRange == "year") "Monthly Trend" else "Daily Trend"
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                                Icon(Icons.Default.BarChart, null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            }
                            TrendBarChart(state.trendData)
                        }
                    }
                }

                // Category Breakdowns
                item {
                    CategoryPieCard("Expense by Category", state.expenseCategoryData, fmt)
                }
                item {
                    CategoryPieCard("Income by Category", state.incomeCategoryData, fmt)
                }

                // Bottom Summary Stats
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Summary", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatPill("Total Tx", state.filteredTransactions.size.toString(), Color(0xFF111827), Modifier.weight(1f))
                                StatPill("Income", state.filteredTransactions.count { it.type == "Income" }.toString(), Color(0xFF16A34A), Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val expCount = state.filteredTransactions.count { it.type == "Expense" }
                                val avgExp = if (expCount > 0) state.totalExpense / expCount else 0.0
                                StatPill("Expenses", expCount.toString(), Color(0xFFDC2626), Modifier.weight(1f))
                                StatPill("Avg. Exp", "৳${String.format(Locale.US, "%.0f", avgExp)}", Color(0xFF2563EB), Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

// ─── UI Components ────────────────────────────────────────────────────────────

@Composable
private fun SummaryBox(title: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4B5563))
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).padding(12.dp)) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tint)
        Text(label, fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun CategoryPieCard(title: String, data: List<CategoryData>, fmt: (Double) -> String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(Icons.Default.PieChart, null, tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
            }
            if (data.isEmpty()) {
                Text("No data available", fontSize = 12.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally))
            } else {
                // Custom Canvas Pie Chart
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(140.dp)) {
                        val total = data.sumOf { it.value }
                        var startAngle = -90f
                        data.forEach { slice ->
                            val sweep = ((slice.value / total) * 360f).toFloat()
                            val clr = try { Color(android.graphics.Color.parseColor(slice.colorHex)) } catch (_: Exception) { Color.Gray }
                            drawArc(
                                color = clr,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = true,
                                size = Size(size.width, size.height)
                            )
                            startAngle += sweep
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Legend
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.forEach { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val clr = try { Color(android.graphics.Color.parseColor(item.colorHex)) } catch (_: Exception) { Color.Gray }
                                Box(modifier = Modifier.size(10.dp).background(clr, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(item.name, fontSize = 13.sp, color = Color(0xFF374151))
                            }
                            Text(fmt(item.value), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                        }
                    }
                }
            }
        }
    }
}

// ── Custom Lightweight Bar Chart ──
@Composable
private fun TrendBarChart(data: List<TrendData>) {
    if (data.isEmpty()) {
        Text("No trend data", fontSize = 12.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(vertical = 24.dp))
        return
    }

    val maxVal = data.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1.0)

    // Legend
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), RoundedCornerShape(2.dp))); Spacer(Modifier.width(4.dp)); Text("Income", fontSize = 10.sp, color = Color(0xFF6B7280)); Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.size(8.dp).background(Color(0xFFEF4444), RoundedCornerShape(2.dp))); Spacer(Modifier.width(4.dp)); Text("Expense", fontSize = 10.sp, color = Color(0xFF6B7280))
    }

    // Chart Area
    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        // Take max 10 points to avoid crowding
        val displayData = data.takeLast(10)
        displayData.forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    val incHeight = (item.income / maxVal).toFloat().coerceIn(0.01f, 1f)
                    val expHeight = (item.expense / maxVal).toFloat().coerceIn(0.01f, 1f)

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
                            color = Color(0xFFEF4444),
                            topLeft = Offset(0f, size.height * (1 - expHeight)),
                            size = Size(size.width, size.height * expHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(item.label, fontSize = 9.sp, color = Color(0xFF9CA3AF), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item != displayData.last()) Spacer(Modifier.width(4.dp))
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