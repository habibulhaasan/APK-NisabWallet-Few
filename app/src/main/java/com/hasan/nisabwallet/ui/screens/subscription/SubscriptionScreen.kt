package com.hasan.nisabwallet.ui.screens.subscription

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
import com.hasan.nisabwallet.core.util.CurrencyFormatter
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewModel: SubscriptionViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPending: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SubscriptionEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                SubscriptionEvent.NavigateToPending -> onNavigateToPending()
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
                    Text("Subscription", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF2563EB)) }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ─── Header ───
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (state.currentSubscription != null) "Extend Your Subscription" else "Choose Your Plan", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    Text(if (state.currentSubscription != null) "Select a plan to continue using Nisab Wallet" else "Get started with Nisab Wallet today", fontSize = 14.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 4.dp))
                }
            }

            // ─── Current Subscription Info[cite: 6] ───
            if (state.currentSubscription != null) {
                item {
                    val sub = state.currentSubscription!!
                    Row(Modifier.fillMaxWidth().background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp)).padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Current Plan: ${sub.planName}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E3A8A))
                            val dateStr = try {
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val out = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                                out.format(sdf.parse(sub.endDate)!!)
                            } catch (e: Exception) { sub.endDate }
                            Text(if (sub.status == "trial") "Your trial ends on $dateStr" else "Your subscription expires on $dateStr", fontSize = 13.sp, color = Color(0xFF1D4ED8), modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }

            // ─── Subscription Plans[cite: 6] ───
            item {
                Text("Choose a Plan", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.plans.forEach { plan ->
                        val isSelected = state.selectedPlan?.id == plan.id
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.selectPlan(plan) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEFF6FF) else Color.White),
                            border = BorderStroke(2.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFFE5E7EB))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CalendarToday, null, tint = if (isSelected) Color(0xFF2563EB) else Color(0xFF9CA3AF), modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
                                Text(plan.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                                Text("৳${CurrencyFormatter.formatBDT(plan.price)}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827), modifier = Modifier.padding(vertical = 4.dp))
                                Text(plan.duration.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, color = Color(0xFF6B7280))
                                Text("${plan.durationDays} days access", fontSize = 11.sp, color = Color(0xFF4B5563), modifier = Modifier.padding(bottom = 12.dp))

                                if (plan.features.isNotEmpty()) {
                                    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                        plan.features.forEach { feature ->
                                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp).padding(top = 2.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(feature, fontSize = 11.sp, color = Color(0xFF4B5563))
                                            }
                                        }
                                    }
                                }
                                if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2563EB), modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            // ─── Payment Section[cite: 6] ───
            if (state.selectedPlan != null && state.paymentSettings.methods.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                                Icon(Icons.Default.CreditCard, null, tint = Color(0xFF111827), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Payment Information", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                            }

                            Text("Select Payment Method *", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF374151), modifier = Modifier.padding(bottom = 8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                                state.paymentSettings.methods.filter { it.isActive }.forEach { method ->
                                    val isSelected = state.selectedPaymentMethod == method.name
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable { viewModel.selectPaymentMethod(method.name) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFEFF6FF) else Color.White),
                                        border = BorderStroke(2.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFFE5E7EB))
                                    ) {
                                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column {
                                                Text(method.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                                                if (method.accountNumber.isNotBlank()) Text("Account: ${method.accountNumber}", fontSize = 12.sp, color = Color(0xFF4B5563), modifier = Modifier.padding(top = 2.dp))
                                                if (method.instructions.isNotBlank()) Text(method.instructions, fontSize = 12.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 2.dp))
                                            }
                                            if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = state.transactionId, onValueChange = { viewModel.updateTransactionId(it) },
                                label = { Text("Transaction / Reference ID *") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true
                            )
                            Text("Enter the transaction ID from your payment confirmation message", fontSize = 11.sp, color = Color(0xFF6B7280), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                // ─── Summary & Submit[cite: 6] ───
                item {
                    val plan = state.selectedPlan!!
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Order Summary", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827), modifier = Modifier.padding(bottom = 12.dp))
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Plan:", fontSize = 13.sp, color = Color(0xFF4B5563))
                                Text(plan.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                            }
                            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Duration:", fontSize = 13.sp, color = Color(0xFF4B5563))
                                Text("${plan.duration.replaceFirstChar { it.uppercase() }} (${plan.durationDays} days)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
                            }
                            HorizontalDivider(color = Color(0xFFD1D5DB))
                            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Total:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Text("৳${CurrencyFormatter.formatBDT(plan.price)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                            }

                            Row(Modifier.fillMaxWidth().background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.Top) {
                                Text("💡", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("Your subscription will be activated after admin verification (usually within 24-48 hours)", fontSize = 11.sp, color = Color(0xFF1E3A8A))
                            }

                            Button(
                                onClick = { viewModel.handleSubscribe() },
                                enabled = !state.isSaving && state.selectedPaymentMethod.isNotBlank() && state.transactionId.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                if (state.isSaving) CircularProgressIndicator(Modifier.size(16.dp), Color.White, 2.dp)
                                else Text("Submit Payment & Continue", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}