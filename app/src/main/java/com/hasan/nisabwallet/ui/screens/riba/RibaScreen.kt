package com.hasan.nisabwallet.ui.screens.riba

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingDown
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RibaScreen(
    viewModel: RibaViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = remember { { n: Double -> CurrencyFormatter.formatBDT(n) } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RibaEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFF59E0B))
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Riba Tracker", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("Track interest income and purify it", fontSize = 12.sp, color = Color(0xFF6B7280))
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
            // Context Banner
            item {
                Surface(color = Color(0xFFFFFBEB), border = BorderStroke(1.dp, Color(0xFFFDE68A)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Understanding Riba in Modern Finance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78350F), modifier = Modifier.padding(bottom = 4.dp))
                            Text("Interest (Riba) is prohibited in Islam. When received from bank accounts or bonds, the Islamic ruling is to not use it for personal benefit. Instead, donate it as Sadaqah to remove it from your wealth — without expecting any reward (Sawab) from Allah, purely to rid yourself of what is impermissible.", fontSize = 12.sp, color = Color(0xFF92400E), lineHeight = 18.sp)
                        }
                    }
                }
            }

            // Summary Cards
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Total Riba Received", fmt(state.totalRiba), "${state.ribaTransactions.size} transactions", Icons.Default.Warning, Color(0xFFD97706), Color(0xFFFFFBEB), Color(0xFFFDE68A), Modifier.weight(1f))
                    SummaryCard("Unpurified (Pending)", fmt(state.totalUnpurified), "${state.unpurifiedCount} need Sadaqah", Icons.AutoMirrored.Filled.TrendingDown, Color(0xFFDC2626), Color(0xFFFEF2F2), Color(0xFFFECACA), Modifier.weight(1f))
                }
            }
            item {
                val pCount = state.ribaTransactions.count { it.sadaqahDone }
                SummaryCard("Donated as Sadaqah", fmt(state.totalPurified), "$pCount purified", Icons.Default.CheckCircle, Color(0xFF059669), Color(0xFFECFDF5), Color(0xFFA7F3D0), Modifier.fillMaxWidth())
            }

            // Unpurified Warning
            if (state.unpurifiedCount > 0) {
                item {
                    Surface(color = Color(0xFFFEF2F2), border = BorderStroke(1.dp, Color(0xFFFECACA)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("You have ${fmt(state.totalUnpurified)} in unpurified Riba. Please donate it as Sadaqah to cleanse your wealth.", fontSize = 13.sp, color = Color(0xFF991B1B))
                        }
                    }
                }
            }

            // Empty State
            if (state.ribaTransactions.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(64.dp).background(Color(0xFFD1FAE5), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(32.dp), tint = Color(0xFF10B981))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("No Riba income recorded", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text("When you record income under the 'Interest / Riba' category, it will appear here for tracking and purification.", fontSize = 13.sp, color = Color(0xFF6B7280), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            } else {
                val unpurified = state.ribaTransactions.filter { !it.sadaqahDone }
                val purified = state.ribaTransactions.filter { it.sadaqahDone }

                if (unpurified.isNotEmpty()) {
                    item { Text("Pending Purification (${unpurified.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), modifier = Modifier.padding(top = 8.dp)) }
                    items(unpurified, key = { it.id }) { tx ->
                        RibaItemCard(
                            tx = tx, fmt = fmt,
                            catName = state.categories.find { it.id == tx.categoryId }?.name ?: "Interest",
                            accName = state.accounts.find { it.id == tx.accountId }?.name ?: "Unknown",
                            onSadaqah = { viewModel.openSadaqahModal(tx) }
                        )
                    }
                }

                if (purified.isNotEmpty()) {
                    item { Text("Purified ✓ (${purified.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669), modifier = Modifier.padding(top = 16.dp)) }
                    items(purified, key = { it.id }) { tx ->
                        RibaItemCard(
                            tx = tx, fmt = fmt,
                            catName = state.categories.find { it.id == tx.categoryId }?.name ?: "Interest",
                            accName = state.accounts.find { it.id == tx.accountId }?.name ?: "Unknown",
                            onSadaqah = null
                        )
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    if (state.showSadaqahModal && state.selectedTransaction != null) {
        SadaqahModal(
            form = state.sadaqahForm,
            transaction = state.selectedTransaction!!,
            accounts = state.accounts,
            isSaving = state.isSaving,
            fmt = fmt,
            onUpdateForm = { viewModel.updateSadaqahForm { it } },
            onDismiss = { viewModel.closeSadaqahModal() },
            onSave = { viewModel.recordSadaqah() }
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, subtitle: String, icon: ImageVector, tint: Color, bg: Color, border: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = bg), border = BorderStroke(1.dp, border)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6B7280), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.padding(top = 8.dp))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun RibaItemCard(
    tx: RibaTransaction, fmt: (Double) -> String, catName: String, accName: String, onSadaqah: (() -> Unit)?
) {
    val isPurified = tx.sadaqahDone

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (isPurified) Color(0xFFA7F3D0) else Color(0xFFFDE68A))
    ) {
        Column {
            if (isPurified) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFECFDF5)).border(1.dp, Color(0xFFD1FAE5)).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Purified via Sadaqah", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                    }
                    Text("${formatDisplayDate(tx.sadaqahDate)} · ${fmt(tx.sadaqahAmount)}", fontSize = 11.sp, color = Color(0xFF059669))
                }
            }

            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f).padding(end = 12.dp)) {
                    Box(modifier = Modifier.size(40.dp).background(if (isPurified) Color(0xFFECFDF5) else Color(0xFFFFFBEB), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(if (isPurified) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (isPurified) Color(0xFF10B981) else Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tx.description.ifBlank { catName }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(50), color = Color(0xFFFEF3C7), border = BorderStroke(1.dp, Color(0xFFFDE68A))) {
                                Text("RIBA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text("$accName · ${formatDisplayDate(tx.date)}", fontSize = 12.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 4.dp))
                        if (tx.description.isNotBlank()) {
                            Text(catName, fontSize = 11.sp, color = Color(0xFF9CA3AF), modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("+${fmt(tx.amount)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                    if (!isPurified && onSadaqah != null) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onSadaqah, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(32.dp)) {
                            Icon(Icons.Default.Favorite, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Give Sadaqah", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SadaqahModal(
    form: SadaqahForm, transaction: RibaTransaction, accounts: List<RibaAccount>, isSaving: Boolean, fmt: (Double) -> String,
    onUpdateForm: (SadaqahForm) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf("form") }

    val parsedAmt = form.amount.toDoubleOrNull() ?: 0.0
    val selectedAcc = accounts.find { it.id == form.accountId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = Color.White) {
        Crossfade(targetState = currentView, label = "SadaqahModal") { view ->
            when(view) {
                "form" -> {
                    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
                        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF0F766E)))).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Record Sadaqah", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha=0.7f), modifier = Modifier.size(20.dp)) }
                        }

                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Source Box
                            Surface(color = Color(0xFFFFFBEB), border = BorderStroke(1.dp, Color(0xFFFDE68A)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("RIBA SOURCE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706), modifier = Modifier.padding(bottom = 2.dp))
                                    Text(transaction.description.ifBlank { "Interest Income" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                                    Text("${formatDisplayDate(transaction.date)} · ${fmt(transaction.amount)} received", fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp))
                                }
                            }

                            // Amount
                            Column {
                                OutlinedTextField(
                                    value = form.amount, onValueChange = { onUpdateForm(form.copy(amount = it)) },
                                    label = { Text("Sadaqah Amount (৳)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857), textAlign = TextAlign.Center),
                                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFFECFDF5), unfocusedContainerColor = Color(0xFFECFDF5), focusedBorderColor = Color(0xFF34D399), unfocusedBorderColor = Color(0xFFA7F3D0))
                                )
                                Text("Full amount: ${fmt(transaction.amount)} — you may donate any amount", fontSize = 10.sp, color = Color(0xFF9CA3AF), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }

                            // Account
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))) {
                                Row(modifier = Modifier.fillMaxWidth().clickable { currentView = "selectAccount" }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("From Account", fontSize = 11.sp, color = Color(0xFF6B7280))
                                        Text(selectedAcc?.let { "${it.name} (${fmt(it.balance)})" } ?: "Select Account", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), modifier = Modifier.padding(top = 2.dp))
                                    }
                                    Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF9CA3AF))
                                }
                            }
                            if (selectedAcc != null && parsedAmt > 0) {
                                val isOver = parsedAmt > selectedAcc.balance
                                Text("Balance after: ${fmt(selectedAcc.balance - parsedAmt)}", fontSize = 11.sp, fontWeight = if(isOver) FontWeight.Bold else FontWeight.Normal, color = if(isOver) Color(0xFFEF4444) else Color(0xFF6B7280), modifier = Modifier.padding(start = 4.dp).offset(y = (-8).dp))
                            }

                            DateSelectionField(label = "Date of Donation", dateString = form.date, onDateSelected = { onUpdateForm(form.copy(date = it)) })
                            OutlinedTextField(value = form.note, onValueChange = { onUpdateForm(form.copy(note = it)) }, label = { Text("Note (optional)") }, placeholder = { Text("e.g. Donated to masjid...") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)

                            // Islamic Note
                            Surface(color = Color(0xFFEFF6FF), border = BorderStroke(1.dp, Color(0xFFDBEAFE)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Give this in Sadaqah without expecting any reward (Sawab) from Allah. The intention is to rid yourself of what is impermissible, not to gain reward.", fontSize = 11.sp, color = Color(0xFF1D4ED8), lineHeight = 16.sp)
                                }
                            }
                        }

                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
                            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))) {
                                if (isSaving) { CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp); Spacer(Modifier.width(8.dp)); Text("Recording...") }
                                else { Icon(Icons.Default.Favorite, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Record Sadaqah", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                "selectAccount" -> {
                    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).imePadding()) {
                        Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { currentView = "form" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                            Text("Select Account", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        }
                        HorizontalDivider(color = Color(0xFFE5E7EB))
                        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(accounts) { acc ->
                                val isSelected = form.accountId == acc.id
                                Card(modifier = Modifier.fillMaxWidth().clickable { onUpdateForm(form.copy(accountId = acc.id)); currentView = "form" }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF9FAFB)), border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFFE5E7EB))) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(40.dp).background(Color(0xFFDBEAFE), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFF1D4ED8)) }
                                        Spacer(Modifier.width(16.dp))
                                        Text("${acc.name} (${fmt(acc.balance)})", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, trailingIcon = { Icon(Icons.Default.CalendarToday, null) }
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

private fun formatDisplayDate(dateStr: String): String {
    if (dateStr.isBlank()) return "N/A"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val out = SimpleDateFormat("dd MMM, yyyy", Locale.US)
        out.format(sdf.parse(dateStr)!!)
    } catch (_: Exception) { dateStr }
}