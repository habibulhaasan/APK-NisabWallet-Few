package com.hasan.nisabwallet.ui.screens.zakat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ZakatScreen(
    viewModel: ZakatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ZakatEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(48.dp))
                        Column {
                            Text("Zakat Tracking", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            Text("Monitor your Zakat obligations", fontSize = 12.sp, color = Color(0xFF6B7280))
                        }
                    }
                    IconButton(onClick = { viewModel.openSettingsModal() }, modifier = Modifier.background(Color(0xFF111827), RoundedCornerShape(8.dp)).size(36.dp)) {
                        Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF059669)) }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    when {
                        state.zakatStatus == "Zakat Due" || state.activeCycle?.status == "due" -> {
                            Button(onClick = { viewModel.openPaymentModal() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).height(48.dp)) {
                                Icon(Icons.Default.CreditCard, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Pay Zakat", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        state.activeCycle == null -> {
                            Button(onClick = { viewModel.openStartCycleModal() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).height(48.dp)) {
                                Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Start Cycle", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F4F6)).padding(4.dp)) {
                    listOf("overview" to "Overview", "history" to "Zakat History").forEach { (id, label) ->
                        val selected = state.activeTab == id
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Color.White else Color.Transparent).clickable { viewModel.setActiveTab(id) }.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Color(0xFF111827) else Color(0xFF6B7280))
                        }
                    }
                }
            }

            if (state.activeTab == "overview") {
                item {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFFECFDF5), Color(0xFFE0F2FE)))).border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(16.dp)).padding(24.dp)) {
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(40.dp).background(Color.White, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Star, null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp)) }
                                    Spacer(Modifier.width(12.dp))
                                    Column { Text("Current Status", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827)); Text("Real-time monitoring", fontSize = 11.sp, color = Color(0xFF4B5563)) }
                                }
                                val bg = when(state.zakatStatus) { "Zakat Due" -> Color(0xFFDC2626); "Monitoring" -> Color(0xFF2563EB); else -> Color(0xFF111827) }
                                Surface(shape = RoundedCornerShape(50), color = bg) { Text(state.zakatStatus, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
                            }

                            Spacer(Modifier.height(24.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                WealthStatCard("Net Zakatable Wealth", fmt(state.breakdown.netZakatableWealth), Icons.Default.AccountBalanceWallet, Color(0xFF059669), Modifier.weight(1f))
                                WealthStatCard("Nisab Threshold", if (state.settings.nisabThreshold > 0) fmt(state.settings.nisabThreshold) else "Not set", Icons.Default.CheckCircle, Color(0xFF059669), Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (state.zakatStatus == "Zakat Due" || state.zakatStatus == "Monitoring") {
                                    WealthStatCard("Zakat Due (2.5%)", fmt(state.zakatAmountDue), Icons.Default.Favorite, Color(0xFFDC2626), Modifier.weight(1f), true)
                                }
                                if (state.activeCycle != null) {
                                    WealthStatCard("Days Remaining", "${state.daysRemaining} days", Icons.Default.Schedule, Color(0xFF2563EB), Modifier.weight(1f))
                                }
                            }

                            if (state.settings.nisabThreshold > 0) {
                                Spacer(Modifier.height(20.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Progress to Nisab", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151))
                                    Text("${String.format(Locale.US, "%.1f", state.progressPercentage)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                }
                                Spacer(Modifier.height(8.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(Color.White, CircleShape).border(1.dp, Color(0xFFD1FAE5), CircleShape)) {
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(state.progressPercentage / 100f).background(Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF14B8A6))), CircleShape))
                                }
                            }
                        }
                    }
                }

                if (state.settings.nisabThreshold == 0.0) {
                    item {
                        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFD1FAE5))) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFF6B7280), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("No Active Cycle", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                                    Text("Nisab threshold is not set yet. Please update your settings to begin tracking.", fontSize = 12.sp, color = Color(0xFF4B5563), modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                                    Button(onClick = { viewModel.openSettingsModal() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(42.dp)) {
                                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Set Nisab Threshold", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column { Text("Wealth Breakdown", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827)); Text("Click sections to expand", fontSize = 11.sp, color = Color(0xFF6B7280)) }
                                Column(horizontalAlignment = Alignment.End) { Text("Net Zakatable", fontSize = 10.sp, color = Color(0xFF6B7280)); Text(fmt(state.breakdown.netZakatableWealth), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF059669)) }
                            }
                            HorizontalDivider(color = Color(0xFFF3F4F6))

                            var openAcc by remember { mutableStateOf(false) }
                            Column {
                                Row(Modifier.fillMaxWidth().clickable { openAcc = !openAcc }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(32.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp)) }
                                        Spacer(Modifier.width(12.dp))
                                        Text("Accounts", fontSize = 14.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("+ ${fmt(state.breakdown.accountsTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827)); Spacer(Modifier.width(8.dp)); Icon(if(openAcc) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color(0xFF9CA3AF)) }
                                }
                                AnimatedVisibility(visible = openAcc, enter = expandVertically(), exit = shrinkVertically()) {
                                    Column(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if(state.breakdown.cashTotal > 0) SubBreakdownRow("Cash", fmt(state.breakdown.cashTotal))
                                        if(state.breakdown.bankTotal > 0) SubBreakdownRow("Bank", fmt(state.breakdown.bankTotal))
                                        if(state.breakdown.mobileTotal > 0) SubBreakdownRow("Mobile Banking", fmt(state.breakdown.mobileTotal))
                                        if(state.breakdown.goldTotal > 0) SubBreakdownRow("Gold", fmt(state.breakdown.goldTotal))
                                        if(state.breakdown.silverTotal > 0) SubBreakdownRow("Silver", fmt(state.breakdown.silverTotal))
                                        if(state.breakdown.otherTotal > 0) SubBreakdownRow("Other", fmt(state.breakdown.otherTotal))
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF3F4F6))

                            var openLend by remember { mutableStateOf(false) }
                            Column {
                                Row(Modifier.fillMaxWidth().clickable { openLend = !openLend }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(32.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Handshake, null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp)) }
                                        Spacer(Modifier.width(12.dp))
                                        Text("Lendings (Receivable)", fontSize = 14.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text("+ ${fmt(state.breakdown.lendingsIncludedTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827)); Spacer(Modifier.width(8.dp)); Icon(if(openLend) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color(0xFF9CA3AF)) }
                                }
                                AnimatedVisibility(visible = openLend, enter = expandVertically(), exit = shrinkVertically()) {
                                    Column(Modifier.fillMaxWidth().background(Color(0xFFF0F9FF)).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Include only money you are confident you will recover.", fontSize = 11.sp, color = Color(0xFF0369A1))
                                        state.lendings.forEach { lend ->
                                            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(lend.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(fmt(lend.amount), fontSize = 12.sp, color = if(lend.countForZakat) Color(0xFF059669) else Color(0xFF9CA3AF), textDecoration = if(lend.countForZakat) TextDecoration.None else TextDecoration.LineThrough)
                                                }
                                                Switch(checked = lend.countForZakat, onCheckedChange = { viewModel.toggleLendingZakat(lend.id, it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF059669)))
                                            }
                                        }
                                        if (state.breakdown.lendingsExcludedTotal > 0) {
                                            Text("Excluded: ${fmt(state.breakdown.lendingsExcludedTotal)}", fontSize = 11.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(top = 4.dp))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF3F4F6))

                            SimpleBreakdownRow("Investments", fmt(state.breakdown.investmentsTotal), Icons.AutoMirrored.Filled.TrendingUp, Color(0xFFDB2777))
                            SimpleBreakdownRow("Savings Goals", fmt(state.breakdown.goalsTotal), Icons.Default.TrackChanges, Color(0xFFEA580C))
                            SimpleBreakdownRow("Jewellery (−15%)", fmt(state.breakdown.jewelleryTotal), Icons.Default.Diamond, Color(0xFFD97706))

                            if (state.breakdown.ribaTotal > 0) {
                                Row(Modifier.fillMaxWidth().background(Color(0xFFFFFBEB)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(12.dp)); Text("Riba (Excluded)", fontSize = 13.sp, color = Color(0xFF92400E)) }
                                    Text("− ${fmt(state.breakdown.ribaTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                }
                                HorizontalDivider(color = Color(0xFFF3F4F6))
                            }

                            if (state.breakdown.loansTotal > 0) {
                                Row(Modifier.fillMaxWidth().background(Color(0xFFFEF2F2)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MoneyOff, null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(12.dp)); Text("Loans (Liabilities)", fontSize = 13.sp, color = Color(0xFF991B1B)) }
                                    Text("− ${fmt(state.breakdown.loansTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                }
                            }

                            Row(Modifier.fillMaxWidth().background(Color(0xFFECFDF5)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Net Zakatable Wealth", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                                Text(fmt(state.breakdown.netZakatableWealth), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF047857))
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(40.dp)) }
            } else {
                if (state.cycleHistory.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, null, modifier = Modifier.size(48.dp), tint = Color(0xFFD1D5DB))
                            Spacer(Modifier.height(16.dp))
                            Text("No Zakat History Yet", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                            Text("Past completed cycles will appear here.", fontSize = 13.sp, color = Color(0xFF6B7280))
                        }
                    }
                } else {
                    items(state.cycleHistory, key = { it.id }) { cycle ->
                        Card(
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Cycle: ${cycle.startDate}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                    Surface(shape = RoundedCornerShape(50), color = if(cycle.status=="paid") Color(0xFFD1FAE5) else Color(0xFFF3F4F6)) {
                                        Text(cycle.status.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if(cycle.status=="paid") Color(0xFF065F46) else Color(0xFF4B5563), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column { Text("Starting Wealth", fontSize = 11.sp, color = Color(0xFF6B7280)); Text(fmt(cycle.startWealth), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827)) }
                                    Column(horizontalAlignment = Alignment.End) { Text("Zakat Paid", fontSize = 11.sp, color = Color(0xFF6B7280)); Text(fmt(cycle.totalPaid), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669)) }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }

        if (state.showSettingsModal) {
            NisabSettingsModal(
                form = state.settingsForm, isSaving = state.isSaving, fetchState = state.bajusFetchState,
                onUpdate = { newForm -> viewModel.updateSettingsForm(newForm) },
                onFetch = { viewModel.fetchBajusRates() },
                onDismiss = { viewModel.closeSettingsModal() },
                onSave = { viewModel.saveNisabSettings() }
            )
        }

        if (state.showStartCycleModal) {
            StartCycleModal(
                date = state.cycleStartDate, isSaving = state.isSaving,
                onDateChange = { newDate -> viewModel.setCycleStartDate(newDate) },
                onDismiss = { viewModel.closeStartCycleModal() },
                onStart = { viewModel.startZakatCycle() }
            )
        }

        if (state.showPaymentModal) {
            PayZakatModal(
                amount = state.paymentAmount, accountId = state.paymentAccountId,
                zakatDue = state.zakatAmountDue, totalPaid = state.activeCycle?.totalPaid ?: 0.0,
                accounts = state.accounts, isSaving = state.isSaving,
                onAmountChange = { newAmt -> viewModel.updatePaymentAmount(newAmt) },
                onAccountChange = { newAcc -> viewModel.updatePaymentAccount(newAcc) },
                onDismiss = { viewModel.closePaymentModal() },
                onSave = { viewModel.recordZakatPayment() }
            )
        }
    }
}

@Composable
private fun WealthStatCard(label: String, value: String, icon: ImageVector, iconColor: Color, modifier: Modifier, highlight: Boolean = false) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, if(highlight) Color(0xFFFECACA) else Color.Transparent), elevation = CardDefaults.cardElevation(0.5.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if(highlight) Color(0xFFDC2626) else Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(14.dp))
            }
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if(highlight) Color(0xFFB91C1C) else Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun SubBreakdownRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color(0xFF4B5563))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
    }
}

@Composable
private fun SimpleBreakdownRow(label: String, value: String, icon: ImageVector, iconColor: Color) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
        }
        Text("+ $value", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
    }
    HorizontalDivider(color = Color(0xFFF3F4F6))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NisabSettingsModal(
    form: NisabSettings, isSaving: Boolean, fetchState: String,
    onUpdate: (NisabSettings) -> Unit, onFetch: () -> Unit,
    onDismiss: () -> Unit, onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputTab by remember { mutableStateOf(if (form.priceSource == "auto") "auto" else "manual") }
    var showAllKarats by remember { mutableStateOf(false) }

    var manualSilverGram by remember(form.silverPricePerGram) { mutableStateOf(if (form.silverPricePerGram > 0) form.silverPricePerGram.toString() else "") }
    var manualSilverVori by remember(form.silverPricePerVori) { mutableStateOf(if (form.silverPricePerVori > 0) form.silverPricePerVori.toString() else "") }
    var manualGoldGram by remember(form.goldPricePerGram) { mutableStateOf(if (form.goldPricePerGram > 0) form.goldPricePerGram.toString() else "") }
    var manualGoldVori by remember(form.goldPricePerVori) { mutableStateOf(if (form.goldPricePerVori > 0) form.goldPricePerVori.toString() else "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding().navigationBarsPadding()) {

            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).background(Color(0xFFD1FAE5), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Calculate, null, tint = Color(0xFF047857), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Nisab Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text("Gold & Silver — BAJUS Rates", fontSize = 11.sp, color = Color(0xFF6B7280))
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color(0xFF6B7280)) }
            }

            Surface(color = Color(0xFFF3F4F6), shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Row(Modifier.padding(4.dp)) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (inputTab == "auto") Color.White else Color.Transparent).clickable { inputTab = "auto"; onUpdate(form.copy(priceSource = "auto")) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Auto Fetch", fontSize = 13.sp, fontWeight = if (inputTab == "auto") FontWeight.Bold else FontWeight.Medium, color = if (inputTab == "auto") Color(0xFF111827) else Color(0xFF4B5563))
                    }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (inputTab == "manual") Color.White else Color.Transparent).clickable { inputTab = "manual"; onUpdate(form.copy(priceSource = "manual")) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Manual Input", fontSize = 13.sp, fontWeight = if (inputTab == "manual") FontWeight.Bold else FontWeight.Medium, color = if (inputTab == "manual") Color(0xFF111827) else Color(0xFF4B5563))
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = Color(0xFFF3F4F6))

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                if (inputTab == "auto") {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFECFDF5), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text("Live BAJUS Official Rates", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                        }
                    }

                    if (fetchState == "loading") {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF059669))
                            Spacer(Modifier.height(12.dp))
                            Text("Fetching today's BAJUS rates…", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4B5563))
                        }
                    } else if (fetchState == "success") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Last fetched: ${form.lastFetched}", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onFetch() }) {
                                Icon(Icons.Default.Refresh, null, tint = Color(0xFF059669), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Refresh", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF059669))
                            }
                        }

                        // Price Cards (Swapped Per Vori to be above Per Gram inside the card)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f).background(Color(0xFFFFFBEB), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFFEF3C7), RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFBBF24), CircleShape)); Spacer(Modifier.width(6.dp))
                                    Text("Gold (22K)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                }
                                val deductedGold = if(form.applyDeduction) form.goldRates.k22 * 0.85 else form.goldRates.k22
                                
                                Text("Per vori", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                                Text("৳${CurrencyFormatter.formatBDT(deductedGold * 11.664)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                
                                Spacer(Modifier.height(6.dp))
                                
                                Text("Per gram", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                                Text("৳${CurrencyFormatter.formatBDT(deductedGold)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                if (form.applyDeduction) {
                                    Text("৳${CurrencyFormatter.formatBDT(form.goldRates.k22)}", fontSize = 11.sp, color = Color(0xFF9CA3AF), textDecoration = TextDecoration.LineThrough)
                                }
                            }

                            Column(Modifier.weight(1f).background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF94A3B8), CircleShape)); Spacer(Modifier.width(6.dp))
                                    Text("Silver (Trad.)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                }
                                
                                Text("Per vori", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                                Text("৳${CurrencyFormatter.formatBDT(form.silverRates.traditional * 11.664)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                
                                Spacer(Modifier.height(6.dp))
                                
                                Text("Per gram", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                                Text("৳${CurrencyFormatter.formatBDT(form.silverRates.traditional)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                
                                Text("★ Nisab basis", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF059669), modifier = Modifier.padding(top = 4.dp))
                            }
                        }

                        GoldDeductionToggle(
                            enabled = form.applyDeduction,
                            rawGoldGram = form.goldRates.k22,
                            onToggle = { onUpdate(form.copy(applyDeduction = it)) }
                        )

                        Surface(modifier = Modifier.fillMaxWidth().clickable { showAllKarats = !showAllKarats }, color = Color(0xFFF9FAFB), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Show all karat prices", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4B5563))
                                Icon(if(showAllKarats) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color(0xFF6B7280))
                            }
                        }

                        if (showAllKarats) {
                            KaratTable("Gold", "amber", form.goldRates, form.applyDeduction)
                            KaratTable("Silver", "slate", form.silverRates, false, "Traditional ★")
                        }

                        NisabResultCard(form.nisabThreshold)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))) {
                                Text("Save — Nisab ৳${CurrencyFormatter.formatBDT(form.nisabThreshold)}", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = {
                                manualSilverGram = form.silverRates.traditional.toString()
                                manualSilverVori = (form.silverRates.traditional * 11.664).toString()
                                manualGoldGram = (if(form.applyDeduction) form.goldRates.k22 * 0.85 else form.goldRates.k22).toString()
                                manualGoldVori = ((if(form.applyDeduction) form.goldRates.k22 * 0.85 else form.goldRates.k22) * 11.664).toString()
                                onUpdate(form.copy(priceSource = "manual"))
                                inputTab = "manual"
                            }, modifier = Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp))
                                Text("Edit prices before saving", color = Color(0xFF4B5563))
                            }
                        }

                    } else {
                        Button(onClick = onFetch, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Fetch Latest Rates", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Enter today's prices from the official BAJUS website. Type in either gram or vori — the other fills automatically.", fontSize = 11.sp, color = Color(0xFF1E3A8A))
                    }

                    Column {
                        Text("DISPLAY UNIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), modifier = Modifier.padding(bottom = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onUpdate(form.copy(priceUnit = "vori")) }, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = if(form.priceUnit == "vori") Color(0xFF111827) else Color.White, contentColor = if(form.priceUnit == "vori") Color.White else Color(0xFF4B5563)), border = BorderStroke(1.dp, if(form.priceUnit == "vori") Color.Transparent else Color(0xFFE5E7EB))) { Text("Per Vori") }
                            Button(onClick = { onUpdate(form.copy(priceUnit = "gram")) }, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = if(form.priceUnit == "gram") Color(0xFF111827) else Color.White, contentColor = if(form.priceUnit == "gram") Color.White else Color(0xFF4B5563)), border = BorderStroke(1.dp, if(form.priceUnit == "gram") Color.Transparent else Color(0xFFE5E7EB))) { Text("Per Gram (g)") }
                        }
                    }

                    // Vori first visually for manual entry
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) { Box(modifier = Modifier.size(10.dp).background(Color(0xFF94A3B8), CircleShape)); Spacer(Modifier.width(8.dp)); Text("Silver Price (৳)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827)) }
                        Text("Enter Traditional (Sanaton) silver price for accurate Nisab", fontSize = 11.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(start = 18.dp, bottom = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = manualSilverVori, onValueChange = {
                                manualSilverVori = it; manualSilverGram = (it.toDoubleOrNull()?.div(11.664))?.toString() ?: ""
                                onUpdate(form.copy(silverPricePerVori = it.toDoubleOrNull() ?: 0.0, silverPricePerGram = manualSilverGram.toDoubleOrNull() ?: 0.0))
                            }, label = { Text("Per Vori") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            OutlinedTextField(value = manualSilverGram, onValueChange = {
                                manualSilverGram = it; manualSilverVori = (it.toDoubleOrNull()?.times(11.664))?.toString() ?: ""
                                onUpdate(form.copy(silverPricePerGram = it.toDoubleOrNull() ?: 0.0, silverPricePerVori = manualSilverVori.toDoubleOrNull() ?: 0.0))
                            }, label = { Text("Per Gram") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        }
                        if ((manualSilverGram.toDoubleOrNull() ?: 0.0) > 0) {
                            Text("Nisab = ৳${CurrencyFormatter.formatBDT((manualSilverGram.toDoubleOrNull() ?: 0.0) * 612.36)} (612.36 g × price/gram)", fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) { Box(modifier = Modifier.size(10.dp).background(Color(0xFFFBBF24), CircleShape)); Spacer(Modifier.width(8.dp)); Text("Gold Price (৳)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = manualGoldVori, onValueChange = {
                                manualGoldVori = it; manualGoldGram = (it.toDoubleOrNull()?.div(11.664))?.toString() ?: ""
                                onUpdate(form.copy(goldPricePerVori = it.toDoubleOrNull() ?: 0.0, goldPricePerGram = manualGoldGram.toDoubleOrNull() ?: 0.0))
                            }, label = { Text("Per Vori") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            OutlinedTextField(value = manualGoldGram, onValueChange = {
                                manualGoldGram = it; manualGoldVori = (it.toDoubleOrNull()?.times(11.664))?.toString() ?: ""
                                onUpdate(form.copy(goldPricePerGram = it.toDoubleOrNull() ?: 0.0, goldPricePerVori = manualGoldVori.toDoubleOrNull() ?: 0.0))
                            }, label = { Text("Per Gram") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        }
                    }

                    if ((manualGoldGram.toDoubleOrNull() ?: 0.0) > 0) {
                        GoldDeductionToggle(
                            enabled = form.applyDeduction,
                            rawGoldGram = manualGoldGram.toDoubleOrNull() ?: 0.0,
                            onToggle = { onUpdate(form.copy(applyDeduction = it)) }
                        )
                    }

                    val rawManualNisab = if (form.priceUnit == "gram") (manualSilverGram.toDoubleOrNull()?:0.0) * 612.36 else (manualSilverVori.toDoubleOrNull()?:0.0) * 52.5
                    if (rawManualNisab > 0) {
                        NisabResultCard(rawManualNisab)

                        Column(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text("Calculation breakdown", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4B5563))
                            if (form.priceUnit == "gram") {
                                Text("৳${manualSilverGram}/g × 612.36 g = ৳${CurrencyFormatter.formatBDT(rawManualNisab)}", fontSize = 12.sp, color = Color(0xFF111827), modifier = Modifier.padding(top = 4.dp))
                            } else {
                                Text("৳${manualSilverVori}/vori × 52.5 = ৳${CurrencyFormatter.formatBDT(rawManualNisab)}", fontSize = 12.sp, color = Color(0xFF111827), modifier = Modifier.padding(top = 4.dp))
                            }
                            Text("No deduction applied — silver Nisab uses full market price.", fontSize = 11.sp, color = Color(0xFF9CA3AF), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(top = 4.dp))
                        }

                        Button(onClick = { onSave() }, enabled = !isSaving, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))) {
                            if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp) else Text("Save — Nisab ৳${CurrencyFormatter.formatBDT(rawManualNisab)}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoldDeductionToggle(enabled: Boolean, rawGoldGram: Double, onToggle: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!enabled) },
        shape = RoundedCornerShape(12.dp), color = if(enabled) Color(0xFFFFFBEB) else Color.White,
        border = BorderStroke(2.dp, if(enabled) Color(0xFFFDE68A) else Color(0xFFE5E7EB))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Switch(checked = enabled, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFD97706)), modifier = Modifier.padding(top = 2.dp).size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Apply 15% Deduction on Gold", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if(enabled) Color(0xFF78350F) else Color(0xFF374151))
                    if (enabled) {
                        Surface(shape = RoundedCornerShape(50), color = Color(0xFFFDE68A), modifier = Modifier.padding(start = 8.dp)) {
                            Text("% ON", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text("For gold standard Nisab only. BAJUS officially deducts 17% for old gold, but Bangladeshi Ulama recommend 15%. Not applicable for silver-based Nisab.", fontSize = 11.sp, color = Color(0xFF6B7280), lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))

                if (rawGoldGram > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("৳${CurrencyFormatter.formatBDT(rawGoldGram)}/g", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(enabled) Color(0xFF9CA3AF) else Color(0xFFB45309), textDecoration = if(enabled) TextDecoration.LineThrough else TextDecoration.None)
                        if (enabled) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFF9CA3AF), modifier = Modifier.padding(horizontal = 6.dp).size(12.dp))
                            Text("৳${CurrencyFormatter.formatBDT(rawGoldGram * 0.85)}/g", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            Text(" (after −15%)", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NisabResultCard(nisab: Double) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF0D9488)))).padding(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFA7F3D0), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("NISAB THRESHOLD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD1FAE5))
            }
            Text("৳${CurrencyFormatter.formatBDT(nisab)}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 4.dp))
            Text("52.5 Tola (612.36 g) × Traditional silver price", fontSize = 11.sp, color = Color(0xFFA7F3D0), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// Visual Swapped layout (Per Vori first, Per Gram second)
@Composable
private fun KaratTable(label: String, theme: String, rates: KaratRates, applyDeduction: Boolean, nisabRow: String = "") {
    val isAmber = theme == "amber"
    val bg = if(isAmber) Color(0xFFFFFBEB) else Color(0xFFF8FAFC)
    val border = if(isAmber) Color(0xFFFEF3C7) else Color(0xFFF1F5F9)
    val head = if(isAmber) Color(0xFFB45309) else Color(0xFF334155)
    val dot = if(isAmber) Color(0xFFFBBF24) else Color(0xFF94A3B8)

    val adj = { n: Double -> if(applyDeduction) n * 0.85 else n }

    Column(modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).border(1.dp, border, RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().border(BorderStroke(1.dp, border)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(dot, CircleShape)); Spacer(Modifier.width(8.dp))
                Text("$label (BAJUS)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = head)
            }
            if (applyDeduction) Text("−15% applied", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = head.copy(alpha = 0.7f))
        }

        // Header Row Swapped
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Karat", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = head, modifier = Modifier.weight(1.5f))
            Text("Per Vori", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = head, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("Per Gram", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = head, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }

        val rows = listOf("22K" to rates.k22, "21K" to rates.k21, "18K" to rates.k18, nisabRow.ifBlank { "Traditional" } to rates.traditional)

        rows.forEach { (k, p) -> // p is the per gram price
            val isNisab = k == nisabRow
            Row(Modifier.fillMaxWidth().background(if(isNisab) Color(0xFFECFDF5) else Color.Transparent).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(k, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if(isNisab) Color(0xFF047857) else Color(0xFF374151), modifier = Modifier.weight(1.5f))
                Text(CurrencyFormatter.formatBDT(adj(p * 11.664)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(isNisab) Color(0xFF065F46) else Color(0xFF111827), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text(CurrencyFormatter.formatBDT(adj(p)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(isNisab) Color(0xFF065F46) else Color(0xFF111827), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartCycleModal(
    date: String, isSaving: Boolean, onDateChange: (String) -> Unit, onDismiss: () -> Unit, onStart: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Start Zakat Cycle", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            HorizontalDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("When did your wealth cross Nisab?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                        Text("Set the actual date your wealth first crossed the threshold to ensure accurate 1-year Hijri calculation.", fontSize = 11.sp, color = Color(0xFF1D4ED8), modifier = Modifier.padding(top = 4.dp))
                    }
                }
                DateSelectionField(label = "Cycle Start Date *", dateString = date, onDateSelected = onDateChange)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(onClick = onStart, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp) else Text("Start Monitoring", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayZakatModal(
    amount: String, accountId: String, zakatDue: Double, totalPaid: Double, accounts: List<ZakatAccount>, isSaving: Boolean,
    onAmountChange: (String) -> Unit, onAccountChange: (String) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val remaining = maxOf(0.0, zakatDue - totalPaid)
    var currentView by remember { mutableStateOf("form") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Crossfade(targetState = currentView, label = "PayModal") { view ->
            when (view) {
                "form" -> {
                    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Pay Zakat", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                        }
                        HorizontalDivider()

                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Summary Row
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f).background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("TOTAL DUE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                    Text(CurrencyFormatter.formatBDT(zakatDue), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B), modifier = Modifier.padding(top = 2.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f).background(Color(0xFFECFDF5), RoundedCornerShape(8.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("PAID", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                    Text(CurrencyFormatter.formatBDT(totalPaid), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46), modifier = Modifier.padding(top = 2.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f).background(Color(0xFFFFFBEB), RoundedCornerShape(8.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("REMAINING", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                    Text(CurrencyFormatter.formatBDT(remaining), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E), modifier = Modifier.padding(top = 2.dp))
                                }
                            }

                            // Amount Field
                            Column {
                                OutlinedTextField(
                                    value = amount, onValueChange = onAmountChange,
                                    label = { Text("Payment Amount (৳)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                                )
                                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onAmountChange(remaining.toString()) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(32.dp), contentPadding = PaddingValues(0.dp)) { Text("Pay All", fontSize = 11.sp) }
                                    if (remaining > 0) {
                                        Button(onClick = { onAmountChange((remaining / 2).toString()) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB), contentColor = Color(0xFF374151)), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(32.dp), contentPadding = PaddingValues(0.dp)) { Text("Half", fontSize = 11.sp) }
                                    }
                                }
                            }

                            // Drill-down for Account Selection
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))) {
                                Row(modifier = Modifier.fillMaxWidth().clickable { currentView = "selectAccount" }.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF6B7280), modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("Pay From Account", fontSize = 11.sp, color = Color(0xFF6B7280))
                                            Text(accounts.find { it.id == accountId }?.name ?: "Select account", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFFD1D5DB))
                                }
                            }
                        }

                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))) {
                                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp) else Text("Record Payment", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "selectAccount" -> {
                    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding().navigationBarsPadding()) {
                        Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { currentView = "form" }) { Icon(Icons.Default.Close, contentDescription = "Back") }
                            Text("Select Account", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        }
                        HorizontalDivider(color = Color(0xFFE5E7EB))
                        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(accounts) { acc ->
                                val isSelected = accountId == acc.id
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { onAccountChange(acc.id); currentView = "form" },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF9FAFB)),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFFE5E7EB))
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(40.dp).background(Color(0xFFDBEAFE), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF1D4ED8))
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Text("${acc.name} (৳${CurrencyFormatter.formatBDT(acc.balance)})", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF3B82F6))
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

// ── Custom Native Date Picker Field ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionField(label: String, dateString: String, onDateSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = dateString, onValueChange = {}, readOnly = true, label = { Text(label) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true, trailingIcon = { Icon(Icons.Default.CalendarToday, null) }
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