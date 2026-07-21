package com.hasan.nisabwallet.ui.screens.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

// ─── Data Models ───
data class SubscriptionPlan(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val duration: String = "",
    val durationDays: Int = 0,
    val features: List<String> = emptyList(),
    val isActive: Boolean = false
)

data class PaymentMethod(
    val id: String = "",
    val name: String = "",
    val accountNumber: String = "",
    val instructions: String = "",
    val isActive: Boolean = false
)

data class PaymentSettings(
    val methods: List<PaymentMethod> = emptyList(),
    val instructions: String = ""
)

data class CurrentSubscription(
    val id: String = "",
    val planName: String = "",
    val status: String = "",
    val endDate: String = ""
)

data class SubscriptionUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val plans: List<SubscriptionPlan> = emptyList(),
    val paymentSettings: PaymentSettings = PaymentSettings(),
    val currentSubscription: CurrentSubscription? = null,
    val selectedPlan: SubscriptionPlan? = null,
    val selectedPaymentMethod: String = "",
    val transactionId: String = ""
)

sealed class SubscriptionEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : SubscriptionEvent()
    object NavigateToPending : SubscriptionEvent()
}

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SubscriptionEvent>()
    val events = _events.asSharedFlow()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        // Fetch Active Plans[cite: 6, 8]
        db.collection("subscriptionPlans")
            .whereEqualTo("isActive", true)
            .orderBy("price", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val plansList = snap.documents.map { d ->
                        SubscriptionPlan(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            price = d.getDouble("price") ?: 0.0,
                            duration = d.getString("duration") ?: "",
                            durationDays = d.getLong("durationDays")?.toInt() ?: 0,
                            features = d.get("features") as? List<String> ?: emptyList(),
                            isActive = d.getBoolean("isActive") ?: false
                        )
                    }
                    _uiState.update { it.copy(plans = plansList) }
                }
            }

        // Fetch Payment Settings[cite: 6, 7]
        db.collection("appSettings")
            .whereEqualTo("type", "paymentGateway")
            .limit(1)
            .addSnapshotListener { snap, _ ->
                if (snap != null && !snap.isEmpty) {
                    val doc = snap.documents.first()
                    val methodsRaw = doc.get("methods") as? List<Map<String, Any>> ?: emptyList()
                    val methods = methodsRaw.map { m ->
                        PaymentMethod(
                            id = m["id"] as? String ?: "",
                            name = m["name"] as? String ?: "",
                            accountNumber = m["accountNumber"] as? String ?: "",
                            instructions = m["instructions"] as? String ?: "",
                            isActive = m["isActive"] as? Boolean ?: false
                        )
                    }
                    val settings = PaymentSettings(
                        methods = methods,
                        instructions = doc.getString("instructions") ?: ""
                    )
                    _uiState.update { it.copy(paymentSettings = settings) }
                }
            }

        // Fetch Current Subscription[cite: 6, 8]
        db.collection("users").document(uid).collection("subscriptions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val activeOrTrial = snap.documents.firstOrNull { d ->
                        val status = d.getString("status")
                        status == "active" || status == "trial"
                    } ?: snap.documents.firstOrNull()

                    val currentSub = activeOrTrial?.let {
                        CurrentSubscription(
                            id = it.id,
                            planName = it.getString("planName") ?: "",
                            status = it.getString("status") ?: "",
                            endDate = it.getString("endDate") ?: ""
                        )
                    }

                    _uiState.update { it.copy(currentSubscription = currentSub, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
    }

    fun selectPlan(plan: SubscriptionPlan) = _uiState.update { it.copy(selectedPlan = plan) }
    fun selectPaymentMethod(methodName: String) = _uiState.update { it.copy(selectedPaymentMethod = methodName) }
    fun updateTransactionId(id: String) = _uiState.update { it.copy(transactionId = id) }

    fun handleSubscribe() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val plan = state.selectedPlan

        if (plan == null) {
            emitEvent(SubscriptionEvent.ShowToast("Please select a subscription plan", true))
            return
        }
        if (state.selectedPaymentMethod.isBlank()) {
            emitEvent(SubscriptionEvent.ShowToast("Please select a payment method", true))
            return
        }
        if (state.transactionId.isBlank()) {
            emitEvent(SubscriptionEvent.ShowToast("Please enter transaction ID", true))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val todayTime = System.currentTimeMillis()
                val endDateMs = todayTime + (plan.durationDays * 24L * 60L * 60L * 1000L)
                val endDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(endDateMs))

                val isExtension = state.currentSubscription?.status in listOf("active", "trial")

                val subscriptionData = mapOf(
                    "subscriptionId" to UUID.randomUUID().toString(),
                    "planId" to plan.id,
                    "planName" to plan.name,
                    "status" to "pending_approval",
                    "startDate" to todayStr,
                    "endDate" to endDateStr,
                    "paymentMethod" to state.selectedPaymentMethod,
                    "transactionId" to state.transactionId,
                    "amount" to plan.price,
                    "isFirstSubscription" to false,
                    "isExtension" to isExtension,
                    "durationDays" to plan.durationDays,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                // Write offline-friendly (no .await())[cite: 6, 8]
                db.collection("users").document(uid).collection("subscriptions")
                    .document().set(subscriptionData)

                emitEvent(SubscriptionEvent.NavigateToPending)

            } catch (e: Exception) {
                emitEvent(SubscriptionEvent.ShowToast("Failed to submit subscription", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun emitEvent(event: SubscriptionEvent) = viewModelScope.launch { _events.emit(event) }
}