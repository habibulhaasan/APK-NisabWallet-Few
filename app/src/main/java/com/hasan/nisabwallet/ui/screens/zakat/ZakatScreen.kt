package com.hasan.nisabwallet.ui.screens.zakat

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.font.FontFamily
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

@Composable
fun ZakatScreen(
    viewModel: ZakatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ZakatEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Offset Spacer for Left Drawer Toggle
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(56.dp))
                                Icon(Icons.Default.Favorite, null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Zakat Tracking", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("Monitor your Zakat obligations", fontSize = 13.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(start = 88.dp))
                        }
                    }
                }

                // Top Actions
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (state.zakatStatus == "Zakat Due") {
                            Button(
                                onClick = { viewModel.openPaymentModal() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.CreditCard, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pay Zakat", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (state.activeCycle == null) {
                            Button(
                                onClick = { viewModel.openStartCycleModal() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Start Cycle", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = { viewModel.openSettingsModal() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nisab Settings", fontSize = 13.sp)
                        }
                    }
                }

                // ─── Navigation Toggle (Tabs) ───
                item {
                    Surface(color = Color(0xFFE5E7EB), shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(Modifier.padding(4.dp)) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (state.activeTab == "overview") Color.White else Color.Transparent).clickable { viewModel.setActiveTab("overview") }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("Overview", fontSize = 13.sp, fontWeight = if (state.activeTab == "overview") FontWeight.Bold else FontWeight.Medium, color = if (state.activeTab == "overview") Color(0xFF111827) else Color(0xFF4B5563))
                            }
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (state.activeTab == "history") Color.White else Color.Transparent).clickable { viewModel.setActiveTab("history") }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("Zakat History", fontSize = 13.sp, fontWeight = if (state.activeTab == "history") FontWeight.Bold else FontWeight.Medium, color = if (state.activeTab == "history") Color(0xFF111827) else Color(0xFF4B5563))
                            }
                        }
                    }
                }

                if (state.activeTab == "overview") {
                    // Main Status Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFD1FAE5))
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Current Status", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                    }
                                    Surface(shape = RoundedCornerShape(50), color = if(state.zakatStatus == "Zakat Due") Color(0xFFDC2626) else Color(0xFF2563EB)) {
                                        Text(state.zakatStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                
                                val cards = mutableListOf<@Composable () -> Unit>()
                                cards.add { StatBox("Zakatable Wealth", fmt(state.breakdown.netZakatableWealth), Icons.Default.AccountBalanceWallet, Color(0xFF059669)) }
                                cards.add { StatBox("Nisab Threshold", if(state.settings.nisabThreshold>0) fmt(state.settings.nisabThreshold) else "Not Set", Icons.Default.Gavel, Color(0xFF059669)) }
                                
                                if (state.zakatStatus == "Zakat Due" || state.zakatStatus == "Monitoring") {
                                    cards.add { StatBox("Zakat (2.5%)", fmt(state.zakatAmountDue), Icons.Default.Favorite, Color(0xFFDC2626)) }
                                }
                                if (state.activeCycle != null) {
                                    cards.add { StatBox("Days Remaining", "${state.daysRemaining} days", Icons.Default.Schedule, Color(0xFF2563EB)) }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    for (i in cards.indices step 2) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Box(Modifier.weight(1f)) { cards[i]() }
                                            if (i + 1 < cards.size) Box(Modifier.weight(1f)) { cards[i + 1]() } else Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                                
                                if (state.settings.nisabThreshold > 0 && state.breakdown.netZakatableWealth < state.settings.nisabThreshold) {
                                    Surface(color = Color(0xFFF3F4F6), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                            Icon(Icons.Default.Info, null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Your wealth is below the Nisab threshold. Zakat monitoring will begin automatically when your wealth reaches Nisab.", fontSize = 12.sp, color = Color(0xFF4B5563))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Wealth Breakdown Accordion
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                        ) {
                            Column {
                                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Wealth Breakdown", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                    Text(fmt(state.breakdown.netZakatableWealth), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF059669), fontFamily = FontFamily.Monospace)
                                }
                                HorizontalDivider(color = Color(0xFFF3F4F6))
                                
                                BreakdownRow("Accounts", fmt(state.breakdown.accountsTotal), Icons.Default.AccountBalance)
                                BreakdownRow("Investments", fmt(state.breakdown.investmentsTotal), Icons.AutoMirrored.Filled.TrendingUp)
                                BreakdownRow("Jewellery", fmt(state.breakdown.jewelleryTotal), Icons.Default.Diamond)
                                BreakdownRow("Lendings (Counted)", fmt(state.breakdown.lendingsIncludedTotal), Icons.Default.Money)
                                BreakdownRow("Loans (Liability)", "− ${fmt(state.breakdown.loansTotal)}", Icons.AutoMirrored.Filled.TrendingDown, Color(0xFFDC2626))
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                } else {
                    // History Tab Content
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
        }

        // Modals
        if (state.showSettingsModal) {
            NisabSettingsModal(
                form = state.settingsForm, isSaving = state.isSaving, fetchState = state.bajusFetchState,
                onUpdate = { viewModel.updateSettingsForm(it) }, onFetch = { viewModel.fetchBajusRates() },
                onDismiss = { viewModel.closeSettingsModal() }, onSave = { viewModel.saveNisabSettings() }
            )
        }

        if (state.showStartCycleModal) {
            StartCycleModal(
                date = state.cycleStartDate, isSaving = state.isSaving,
                onDateChange = { viewModel.setCycleStartDate(it) },
                onDismiss = { viewModel.closeStartCycleModal() },
                onStart = { viewModel.startZakatCycle() }
            )
        }

        if (state.showPaymentModal) {
            PayZakatModal(
                amount = state.paymentAmount, accountId = state.paymentAccountId,
                zakatDue = state.zakatAmountDue, totalPaid = state.activeCycle?.totalPaid ?: 0.0,
                accounts = state.accounts, isSaving = state.isSaving,
                onAmountChange = { viewModel.updatePaymentAmount(it) },
                onAccountChange = { viewModel.updatePaymentAccount(it) },
                onDismiss = { viewModel.closePaymentModal() },
                onSave = { viewModel.recordZakatPayment() }
            )
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, icon: ImageVector, iconColor: Color) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(14.dp))
        }
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, icon: ImageVector, valueColor: Color = Color(0xFF111827)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 13.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
        }
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor, fontFamily = FontFamily.Monospace)
    }
    HorizontalDivider(color = Color(0xFFF3F4F6))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NisabSettingsModal(
    form: NisabSettings, isSaving: Boolean, fetchState: String,
    onUpdate: ((NisabSettings) -> NisabSettings) -> Unit, onFetch: () -> Unit,
    onDismiss: () -> Unit, onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputTab by remember { mutableStateOf(if (form.priceSource == "auto") "auto" else "manual") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Nisab Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            
            // Auto vs Manual Tabs
            Surface(color = Color(0xFFF3F4F6), shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Row(Modifier.padding(4.dp)) {
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (inputTab == "auto") Color.White else Color.Transparent).clickable { inputTab = "auto"; onUpdate { it.copy(priceSource = "auto") } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Auto Fetch", fontSize = 13.sp, fontWeight = if (inputTab == "auto") FontWeight.Bold else FontWeight.Medium, color = if (inputTab == "auto") Color(0xFF111827) else Color(0xFF4B5563))
                    }
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (inputTab == "manual") Color.White else Color.Transparent).clickable { inputTab = "manual"; onUpdate { it.copy(priceSource = "manual") } }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Manual Input", fontSize = 13.sp, fontWeight = if (inputTab == "manual") FontWeight.Bold else FontWeight.Medium, color = if (inputTab == "manual") Color(0xFF111827) else Color(0xFF4B5563))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 16.dp))

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                if (inputTab == "auto") {
                    Surface(color = Color(0xFFECFDF5), border = BorderStroke(1.dp, Color(0xFF6EE7B7)), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Live BAJUS Rates", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                                Text("Syncs official Gold & Silver prices automatically.", fontSize = 11.sp, color = Color(0xFF047857))
                            }
                        }
                    }

                    if (fetchState == "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF059669))
                        }
                    } else {
                        Button(
                            onClick = onFetch,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Fetch Latest Rates", fontWeight = FontWeight.Bold)
                        }

                        if (form.silverPricePerGram > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f).background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)).padding(12.dp)) {
                                    Text("Silver (per g)", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                                    Text("৳${form.silverPricePerGram}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A), modifier = Modifier.padding(top = 4.dp))
                                }
                                Column(Modifier.weight(1f).background(Color(0xFFFFFBEB), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(10.dp)).padding(12.dp)) {
                                    Text("Gold (per g)", fontSize = 11.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                                    Text("৳${form.goldPricePerGram}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF78350F), modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                } else {
                    Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Calculate Nisab based on Silver (Traditional)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                            Text("Enter the current per-gram price of Traditional Silver to accurately track your Zakat obligation (52.5 Tola = 612.36g).", fontSize = 11.sp, color = Color(0xFF1D4ED8), modifier = Modifier.padding(top = 4.dp))
                        }
                    }

                    OutlinedTextField(
                        value = if(form.silverPricePerGram > 0) form.silverPricePerGram.toString() else "",
                        onValueChange = { v -> onUpdate { it.copy(silverPricePerGram = v.toDoubleOrNull() ?: 0.0) } },
                        label = { Text("Silver Price Per Gram (৳) *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                    )

                    OutlinedTextField(
                        value = if(form.goldPricePerGram > 0) form.goldPricePerGram.toString() else "",
                        onValueChange = { v -> onUpdate { it.copy(goldPricePerGram = v.toDoubleOrNull() ?: 0.0) } },
                        label = { Text("Gold Price Per Gram (৳) (Optional)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onUpdate { it.copy(applyDeduction = !it.applyDeduction) } }.padding(vertical = 8.dp)) {
                    Checkbox(checked = form.applyDeduction, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Apply 15% Deduction on Gold", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text("Ulama recommend a 15% deduction for used gold.", fontSize = 11.sp, color = Color(0xFF6B7280))
                    }
                }

                if (form.nisabThreshold > 0) {
                    Surface(color = Color(0xFF111827), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Calculated Nisab Threshold", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF))
                            Text("৳${CurrencyFormatter.formatBDT(form.nisabThreshold)}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
                Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp) else Text("Save Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartCycleModal(
    date: String, isSaving: Boolean,
    onDateChange: (String) -> Unit, onDismiss: () -> Unit, onStart: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().imePadding().padding(bottom = 16.dp)) {
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
    var isAccountExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().imePadding().padding(bottom = 16.dp)) {
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

                // Account Selection
                Box {
                    OutlinedTextField(
                        value = accounts.find { it.id == accountId }?.let { "${it.name} (৳${it.balance})" } ?: "Select account",
                        onValueChange = {}, readOnly = true, label = { Text("Pay From Account") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { isAccountExpanded = true })
                    DropdownMenu(expanded = isAccountExpanded, onDismissRequest = { isAccountExpanded = false }) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text("${acc.name} (৳${acc.balance})") }, onClick = { onAccountChange(acc.id); isAccountExpanded = false })
                        }
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
}

// ── Custom Native Date Picker Field ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionField(label: String, dateString: String, onDateSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        OutlinedTextField(value = dateString, onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true, trailingIcon = { Icon(Icons.Default.CalendarToday, null) })
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