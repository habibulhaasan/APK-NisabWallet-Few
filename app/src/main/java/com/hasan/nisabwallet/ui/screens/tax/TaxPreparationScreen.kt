package com.hasan.nisabwallet.ui.screens.tax

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import com.hasan.nisabwallet.core.util.TaxCategoryUtils
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TaxPreparationScreen(
    viewModel: TaxPreparationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToTaxYear: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TaxPreparationEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                is TaxPreparationEvent.NavigateToSetup -> onNavigateToSetup()
                is TaxPreparationEvent.NavigateToTaxYear -> onNavigateToTaxYear(event.id)
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
                        Text("Tax Preparation", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text("Prepare your NBR tax filing documents", fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                    if (state.hasMappings) {
                        OutlinedButton(onClick = onNavigateToSetup, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp), tint = Color(0xFF374151))
                            Spacer(Modifier.width(6.dp))
                            Text("Mappings", fontSize = 12.sp, color = Color(0xFF374151))
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ─── Info Banner ───
            item {
                Row(Modifier.fillMaxWidth().background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Bangladesh Tax Year Information", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E3A8A))
                        Text("• Income Year: July 1 to June 30\n• Tax Year: Following year\n• Filing Deadline: November 30 of tax year", fontSize = 12.sp, color = Color(0xFF1D4ED8), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // ─── Setup Warning (if no mappings) ───
            if (!state.hasMappings) {
                item {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFFEFCE8), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFFEF08A), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFCA8A04), modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Setup Required", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF713F12))
                            Text("Before creating tax files, you need to map your expense/income categories to NBR tax categories. This is a one-time setup that takes about 2 minutes.", fontSize = 13.sp, color = Color(0xFFA16207), modifier = Modifier.padding(vertical = 8.dp))
                            Button(onClick = onNavigateToSetup, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCA8A04)), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                                Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("Start Setup Wizard", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ─── Current Year Quick Action ───
            item {
                val dates = TaxCategoryUtils.getFiscalYearDates(state.currentIncomeYear)
                val taxYear = TaxCategoryUtils.getTaxYear(state.currentIncomeYear)
                val isCurrentYearCreated = state.taxYears.any { it.incomeYear == state.currentIncomeYear }

                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF111827), Color(0xFF1F2937))), RoundedCornerShape(12.dp)).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Current Income Year: ${state.currentIncomeYear}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text("${formatDate(dates.first)} - ${formatDate(dates.second)}", fontSize = 13.sp, color = Color(0xFFD1D5DB), modifier = Modifier.padding(top = 6.dp))
                    Text("Tax Year: $taxYear • Deadline: November 30", fontSize = 11.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(top = 2.dp))

                    Button(
                        onClick = { viewModel.createCurrentYear() },
                        enabled = !state.isCreating && state.hasMappings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF111827), disabledContainerColor = Color.White.copy(alpha=0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(44.dp)
                    ) {
                        if (state.isCreating) {
                            CircularProgressIndicator(Modifier.size(16.dp), Color(0xFF111827), 2.dp)
                            Spacer(Modifier.width(8.dp)); Text("Creating...")
                        } else {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isCurrentYearCreated) "View Current Year" else "Create Tax File", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ─── Tax Years List ───
            if (state.taxYears.isNotEmpty()) {
                item { Text("Your Tax Years (${state.taxYears.size})", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4B5563)) }
                items(state.taxYears, key = { it.id }) { taxYear ->
                    TaxYearCard(
                        taxYear = taxYear,
                        fmt = fmt,
                        onClick = { onNavigateToTaxYear(taxYear.id) }
                    )
                }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).border(2.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp)).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No Tax Files Yet", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                        Text(if (!state.hasMappings) "Complete the setup wizard to start preparing your tax files." else "Create your first tax file to start tracking income and expenses for NBR filing.", fontSize = 13.sp, color = Color(0xFF6B7280), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxYearCard(taxYear: TaxYearRecord, fmt: (Double) -> String, onClick: () -> Unit) {
    val deadlineDays = TaxCategoryUtils.getDaysUntilDeadline(taxYear.filingDeadline)

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFFF3F4F6), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Description, null, tint = Color(0xFF4B5563), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Income Year ${taxYear.incomeYear}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            Spacer(Modifier.width(8.dp))
                            StatusBadge(taxYear.status)
                        }
                        Text("Tax Year ${taxYear.taxYear} • ${formatDate(taxYear.fiscalYearStart)} - ${formatDate(taxYear.fiscalYearEnd)}", fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF9CA3AF))
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricColumn("Income", "৳${fmt(taxYear.totalIncome)}", Color(0xFF16A34A), Modifier.weight(1f))
                MetricColumn("Expenses", "৳${fmt(taxYear.totalExpenses)}", Color(0xFFDC2626), Modifier.weight(1f))
                MetricColumn("Assets", "৳${fmt(taxYear.totalAssets)}", Color(0xFF111827), Modifier.weight(1f))
                MetricColumn("Net Worth", "৳${fmt(taxYear.netWorth)}", Color(0xFF2563EB), Modifier.weight(1f))
            }

            if (taxYear.status != "filed") {
                val (color, bg, text) = when {
                    deadlineDays < 0 -> Triple(Color(0xFFDC2626), Color(0xFFFEF2F2), "Overdue")
                    deadlineDays == 0 -> Triple(Color(0xFFDC2626), Color(0xFFFEF2F2), "Due Today!")
                    deadlineDays <= 30 -> Triple(Color(0xFFD97706), Color(0xFFFFFBEB), "$deadlineDays days left")
                    else -> Triple(Color(0xFF16A34A), Color(0xFFF0FDF4), "$deadlineDays days left")
                }
                Surface(color = bg, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = color, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp))
                        Text("Filing: $text", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val icon: ImageVector
    val bg: Color
    val fg: Color
    val text: String

    when(status) {
        "filed" -> { icon = Icons.Default.CheckCircle; bg = Color(0xFFDCFCE7); fg = Color(0xFF15803D); text = "Filed" }
        "in_review" -> { icon = Icons.Default.Schedule; bg = Color(0xFFDBEAFE); fg = Color(0xFF1D4ED8); text = "In Review" }
        else -> { icon = Icons.Default.Description; bg = Color(0xFFF3F4F6); fg = Color(0xFF374151); text = "Draft" }
    }

    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(10.dp)); Spacer(Modifier.width(4.dp))
            Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = fg)
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Color(0xFF6B7280))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val out = SimpleDateFormat("MMM d, yyyy", Locale.US)
        out.format(sdf.parse(dateStr)!!)
    } catch (_: Exception) { dateStr }
}