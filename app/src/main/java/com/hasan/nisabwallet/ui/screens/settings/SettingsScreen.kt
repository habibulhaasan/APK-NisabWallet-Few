package com.hasan.nisabwallet.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit,
    onNavigateToSubscription: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmationInput by remember { mutableStateOf("") }
    var showHistoryExpanded by remember { mutableStateOf(false) }

    val createJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.executeJsonExport(it) }
    }
    val selectFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.executeCsvExport(it) }
    }
    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.executeImport(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SettingsEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                SettingsEvent.TriggerJsonFileCreate -> createJsonLauncher.launch("nisab-backup-${System.currentTimeMillis()}.json")
                SettingsEvent.TriggerCsvFolderSelection -> selectFolderLauncher.launch(null)
                SettingsEvent.LogoutComplete -> onNavigateToLogin()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("Settings", fontSize = 24.dp.value.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    Text("Manage account instance states and active filters", fontSize = 13.sp, color = Color(0xFF6B7280))
                }
            }

            // ─── SECTION 1: Subscription ───
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CreditCard, null, tint = Color(0xFF374151), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Subscription", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827))
                            }
                            Button(
                                onClick = onNavigateToSubscription,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Extend", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        state.currentSubscription?.let { sub ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFFEFF6FF), Color(0xFFF5F3FF))))
                                    .border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Current Plan", fontSize = 11.sp, color = Color(0xFF6B7280))
                                            Text(sub.planName, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF111827))
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = if (sub.status == "active" || sub.status == "trial") Color(0xFFD1FAE5) else Color(0xFFF3F4F6)
                                        ) {
                                            Text(
                                                sub.status.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                color = if (sub.status == "active" || sub.status == "trial") Color(0xFF065F46) else Color(0xFF374151),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text("Duration Days: ${sub.durationDays}d · Rate: ৳${sub.amount}", fontSize = 13.sp, color = Color(0xFF4B5563))
                                    Text("Valid instance horizon: ${sub.endDate}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                }
                            }
                        }

                        if (state.subscriptionHistory.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showHistoryExpanded = !showHistoryExpanded }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subscription History Logs", fontSize = 13.sp, color = Color(0xFF4B5563), fontWeight = FontWeight.Medium)
                                Icon(if (showHistoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                            AnimatedVisibility(visible = showHistoryExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    state.subscriptionHistory.forEach { h ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp)).padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(h.planName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                Text("ID: ${h.transactionId.ifBlank { "System instance" }}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                                            }
                                            Text("৳${h.amount}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── SECTION 2: Profile ───
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = Color(0xFF374151), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Profile Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827))
                        }
                        OutlinedTextField(
                            value = state.displayName,
                            onValueChange = { viewModel.updateDisplayName(it) },
                            label = { Text("Display Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = state.userEmail,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Email Address") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFFF3F4F6), unfocusedContainerColor = Color(0xFFF3F4F6))
                        )
                        Button(
                            onClick = { viewModel.handleUpdateProfile() },
                            enabled = !state.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Update Profile Parameters", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ─── SECTION 3: App Preferences ───
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, null, tint = Color(0xFF374151), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("App Preferences", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827))
                        }

                        // Theme
                        Column {
                            Text("Theme Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4B5563))
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("light" to "Light", "dark" to "Dark", "auto" to "Auto").forEach { (v, l) ->
                                    val sel = state.preferences.theme == v
                                    Box(
                                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                            .background(if (sel) Color(0xFF111827) else Color(0xFFF3F4F6))
                                            .clickable { viewModel.updateTheme(v) }.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(l, color = if (sel) Color.White else Color(0xFF374151), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        // Currency Dropdown Link
                        var curExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedTextField(
                                value = state.preferences.currency, onValueChange = {}, readOnly = true,
                                label = { Text("Base Currency Paradigm") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { curExpanded = true })
                            DropdownMenu(expanded = curExpanded, onDismissRequest = { curExpanded = false }) {
                                listOf("BDT", "USD", "EUR", "SAR", "INR").forEach { c ->
                                    DropdownMenuItem(text = { Text(c) }, onClick = { viewModel.updateCurrency(c); curExpanded = false })
                                }
                            }
                        }

                        // Date Format
                        var dateExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedTextField(
                                value = state.preferences.dateFormat, onValueChange = {}, readOnly = true,
                                label = { Text("Display Date Format String") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { dateExpanded = true })
                            DropdownMenu(expanded = dateExpanded, onDismissRequest = { dateExpanded = false }) {
                                listOf("DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD").forEach { f ->
                                    DropdownMenuItem(text = { Text(f) }, onClick = { viewModel.updateDateFormat(f); dateExpanded = false })
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.savePreferences() },
                            enabled = !state.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Configuration State", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ─── SECTION 4: Data Interop (Import/Export) ───
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Backup, null, tint = Color(0xFF374151), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import / Export Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827))
                        }

                        // Import Block
                        Surface(
                            shape = RoundedCornerShape(12.dp), color = Color(0xFFF9FAFB),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            modifier = Modifier.fillMaxWidth().clickable { importFileLauncher.launch("application/json") }
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.UploadFile, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                Text("Import Master JSON Backup File", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF374151))
                                Text("Adds instances into current live collection maps", fontSize = 11.sp, color = Color.Gray)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF3F4F6))

                        // Export Parameters
                        Text("Export Configuration Matrix", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("all" to "All Doc", "selective" to "Choose Map", "daterange" to "Range Filter").forEach { (m, label) ->
                                val sel = state.exportMode == m
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) Color(0xFF111827) else Color(0xFFF3F4F6))
                                        .clickable { viewModel.setExportMode(if (sel) null else m) }.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (sel) Color.White else Color(0xFF374151), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        state.exportMode?.let { mode ->
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (mode == "selective" || mode == "daterange") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Select Collection Targets", fontSize = 12.sp, color = Color.Gray)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("All", fontSize = 11.sp, color = Color.Blue, modifier = Modifier.clickable { viewModel.selectAllCollections() })
                                            Text("Clear", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.clickable { viewModel.selectNoCollections() })
                                        }
                                    }
                                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        viewModel.allCollections.forEach { (k, label) ->
                                            val act = state.selectedCollections.contains(k)
                                            FilterChip(
                                                selected = act, onClick = { viewModel.toggleCollection(k) },
                                                label = { Text(label, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }

                                if (mode == "daterange") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = state.dateFrom, onValueChange = { viewModel.setDateFrom(it) }, label = { Text("From (yyyy-MM-dd)", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                                        OutlinedTextField(value = state.dateTo, onValueChange = { viewModel.setDateTo(it) }, label = { Text("To (yyyy-MM-dd)", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                                    }
                                }

                                Text("Target File Format Paradigm", fontSize = 12.sp, color = Color.Gray)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("json" to "Unified JSON payload", "csv" to "Multi-File CSV Directory").forEach { (f, l) ->
                                        val sel = state.exportFormat == f
                                        Box(
                                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                                .background(if (sel) Color(0xFF047857) else Color(0xFFF3F4F6))
                                                .clickable { viewModel.setExportFormat(f) }.padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(l, color = if (sel) Color.White else Color(0xFF374151), fontSize = 11.sp)
                                        }
                                    }
                                }

                                Button(
                                    onClick = { viewModel.requestExportAccess() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Execute Document Generation Pipeline")
                                }
                            }
                        }
                    }
                }
            }

            // ─── SECTION 5: Diagnostics Info ───
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFF374151), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("System Metadata Diagnostics", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111827))
                        }
                        HorizontalDivider(color = Color(0xFFF3F4F6))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Client version core", color = Color.Gray, fontSize = 13.sp)
                            Text("1.0.0-android-native", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Target session hash token", color = Color.Gray, fontSize = 13.sp)
                            Text(state.uid.take(12) + "...", fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cloud account record timestamp", color = Color.Gray, fontSize = 13.sp)
                            Text(state.creationTime, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ─── SECTION 6: Danger Purge Zone ───
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Danger Management Zone", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF991B1B))
                        }
                        Text("Executing options inside this scope drop live reference keys permanently. Login token parameters are preserved.", fontSize = 12.sp, color = Color(0xFF7F1D1D))
                        Button(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Wipe Storage Cache Trees")
                        }
                    }
                }
            }

            // ─── SECTION 7: Session Detach ───
            item {
                Button(
                    onClick = { viewModel.logout(onNavigateToLogin) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB), contentColor = Color(0xFF374151)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Terminate Session Instance", fontWeight = FontWeight.Bold)
                }

                // Extra padding at the bottom so the floating pill doesn't block the button
                Spacer(Modifier.height(80.dp))
            }
        }

        // Floating Connection Tracker Notification
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            shape = RoundedCornerShape(50),
            color = if (state.syncStatus == "Synced") Color(0xFFD1FAE5) else Color(0xFFFEF3C7),
            border = BorderStroke(1.dp, if (state.syncStatus == "Synced") Color(0xFF10B981) else Color(0xFFF59E0B))
        ) {
            Text(
                text = "Engine State: ${state.syncStatus}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (state.syncStatus == "Synced") Color(0xFF065F46) else Color(0xFF92400E),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // ─── Wipe Confirmation Dialogue Overlay ───
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirm Total Cache Wipe", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This actions drops references across all mapped financial parameters permanently.", fontSize = 13.sp)
                    Text("Type \"DELETE\" below to clear active collection structures:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = deleteConfirmationInput, onValueChange = { deleteConfirmationInput = it },
                        placeholder = { Text("DELETE") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.executePurgeWallet(deleteConfirmationInput)
                        showDeleteDialog = false
                        deleteConfirmationInput = ""
                    },
                    enabled = deleteConfirmationInput == "DELETE",
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Wipe Collections") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false; deleteConfirmationInput = "" }) { Text("Cancel") }
            }
        )
    }
}