package com.hasan.nisabwallet.ui.screens.zakat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil

// ─── Data Models ───
data class ZakatCycle(
    val id: String = "",
    val status: String = "active",
    val startDate: String = "",
    val startWealth: Double = 0.0,
    val nisabAtStart: Double = 0.0,
    val totalPaid: Double = 0.0,
    val zakatDue: Double = 0.0
)

data class NisabSettings(
    val nisabThreshold: Double = 0.0,
    val silverPricePerGram: Double = 0.0,
    val goldPricePerGram: Double = 0.0,
    val applyDeduction: Boolean = false
)

data class WealthBreakdown(
    val accountsTotal: Double = 0.0,
    val investmentsTotal: Double = 0.0,
    val jewelleryTotal: Double = 0.0,
    val lendingsIncludedTotal: Double = 0.0,
    val loansTotal: Double = 0.0,
    val netZakatableWealth: Double = 0.0
)

data class ZakatUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val activeCycle: ZakatCycle? = null,
    val settings: NisabSettings = NisabSettings(),
    val breakdown: WealthBreakdown = WealthBreakdown(),
    val daysRemaining: Int = 0,
    val zakatStatus: String = "Not Mandatory",
    val zakatAmountDue: Double = 0.0,

    val showSettingsModal: Boolean = false,
    val showStartCycleModal: Boolean = false,
    val showPaymentModal: Boolean = false,

    val settingsForm: NisabSettings = NisabSettings(),
    val cycleStartDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val paymentAmount: String = "",
    val paymentAccountId: String = ""
)

sealed class ZakatEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : ZakatEvent()
}

@HiltViewModel
class ZakatViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZakatUiState())
    val uiState: StateFlow<ZakatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ZakatEvent>()
    val events = _events.asSharedFlow()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        // Fetch Nisab Settings
        db.collection("users").document(uid).collection("settings").limit(1)
            .addSnapshotListener { snap, _ ->
                if (snap != null && !snap.isEmpty) {
                    val data = snap.documents.first()
                    val settings = NisabSettings(
                        nisabThreshold = data.getDouble("nisabThreshold") ?: 0.0,
                        silverPricePerGram = data.getDouble("silverPricePerGram") ?: 0.0,
                        goldPricePerGram = data.getDouble("goldPricePerGram") ?: 0.0,
                        applyDeduction = data.getBoolean("applyDeduction") ?: false
                    )
                    _uiState.update { it.copy(settings = settings, settingsForm = settings) }
                    recalculateWealth(uid)
                }
            }

        // Fetch Active Cycle
        db.collection("users").document(uid).collection("zakatCycles")
            .whereEqualTo("status", "active").limit(1)
            .addSnapshotListener { snap, _ ->
                if (snap != null && !snap.isEmpty) {
                    val d = snap.documents.first()
                    val cycle = ZakatCycle(
                        id = d.id, status = "active", startDate = d.getString("startDate") ?: "",
                        startWealth = d.getDouble("startWealth") ?: 0.0,
                        nisabAtStart = d.getDouble("nisabAtStart") ?: 0.0,
                        totalPaid = d.getDouble("totalPaid") ?: 0.0,
                        zakatDue = d.getDouble("zakatDue") ?: 0.0
                    )
                    _uiState.update { it.copy(activeCycle = cycle) }
                    recalculateWealth(uid)
                } else {
                    _uiState.update { it.copy(activeCycle = null) }
                    recalculateWealth(uid)
                }
            }
    }

    private fun recalculateWealth(uid: String) {
        viewModelScope.launch {
            try {
                var accTotal = 0.0
                var invTotal = 0.0
                var jewTotal = 0.0
                var lendTotal = 0.0
                var loanTotal = 0.0

                val accSnap = db.collection("users").document(uid).collection("accounts").get().await()
                accSnap.documents.forEach { accTotal += (it.getDouble("balance") ?: 0.0) }

                val invSnap = db.collection("users").document(uid).collection("investments").whereEqualTo("status", "active").get().await()
                invSnap.documents.forEach { invTotal += ((it.getDouble("currentValue") ?: it.getDouble("purchasePrice") ?: 0.0) * (it.getDouble("quantity") ?: 1.0)) }

                val jewSnap = db.collection("users").document(uid).collection("jewellery").whereNotEqualTo("status", "sold").get().await()
                jewSnap.documents.forEach { jewTotal += (it.getDouble("currentZakatValue") ?: 0.0) }

                val lendSnap = db.collection("users").document(uid).collection("lendings").whereEqualTo("status", "active").get().await()
                lendSnap.documents.forEach { 
                    if (it.getBoolean("countForZakat") == true) {
                        lendTotal += (it.getDouble("remainingBalance") ?: it.getDouble("principalAmount") ?: 0.0)
                    }
                }

                val loanSnap = db.collection("users").document(uid).collection("loans").whereEqualTo("status", "active").get().await()
                loanSnap.documents.forEach { loanTotal += (it.getDouble("remainingBalance") ?: it.getDouble("principalAmount") ?: 0.0) }

                val totalAssets = accTotal + invTotal + jewTotal + lendTotal
                val netWealth = maxOf(0.0, totalAssets - loanTotal)
                val breakdown = WealthBreakdown(accTotal, invTotal, jewTotal, lendTotal, loanTotal, netWealth)

                val cycle = _uiState.value.activeCycle
                val nisab = _uiState.value.settings.nisabThreshold

                var status = "Not Mandatory"
                var amountDue = 0.0
                var daysRem = 0

                if (cycle != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val start = runCatching { sdf.parse(cycle.startDate)?.time }.getOrNull() ?: System.currentTimeMillis()
                    // Hijri year approximation: 354 days
                    val end = start + (354L * 24 * 60 * 60 * 1000)
                    val today = System.currentTimeMillis()

                    if (today >= end) {
                        status = if (netWealth >= nisab) "Zakat Due" else "Exempt"
                        if (status == "Zakat Due") amountDue = netWealth * 0.025
                    } else {
                        status = "Monitoring"
                        daysRem = maxOf(0, ceil((end - today) / (1000.0 * 60 * 60 * 24)).toInt())
                        amountDue = if (nisab > 0) netWealth * 0.025 else 0.0
                    }
                } else if (nisab > 0) {
                    amountDue = netWealth * 0.025
                }

                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        breakdown = breakdown,
                        zakatStatus = status,
                        zakatAmountDue = amountDue,
                        daysRemaining = daysRem
                    )
                }
            } catch (_: Exception) {
                emitEvent(ZakatEvent.ShowToast("Wealth calculation error", true))
            }
        }
    }

    // ─── Modals ───
    fun openSettingsModal() = _uiState.update { it.copy(showSettingsModal = true) }
    fun closeSettingsModal() = _uiState.update { it.copy(showSettingsModal = false) }
    fun updateSettingsForm(update: (NisabSettings) -> NisabSettings) = _uiState.update { it.copy(settingsForm = update(it.settingsForm)) }

    fun openStartCycleModal() = _uiState.update { it.copy(showStartCycleModal = true) }
    fun closeStartCycleModal() = _uiState.update { it.copy(showStartCycleModal = false) }
    fun setCycleStartDate(date: String) = _uiState.update { it.copy(cycleStartDate = date) }

    fun openPaymentModal() = _uiState.update { it.copy(showPaymentModal = true, paymentAmount = it.zakatAmountDue.toString()) }
    fun closePaymentModal() = _uiState.update { it.copy(showPaymentModal = false) }

    // ─── Actions ───
    fun saveNisabSettings() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.settingsForm
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val data = mapOf(
                    "nisabThreshold" to form.nisabThreshold,
                    "silverPricePerGram" to form.silverPricePerGram,
                    "goldPricePerGram" to form.goldPricePerGram,
                    "applyDeduction" to form.applyDeduction,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val ref = db.collection("users").document(uid).collection("settings")
                val existing = ref.limit(1).get().await()
                if (existing.isEmpty) ref.add(data).await()
                else existing.documents.first().reference.set(data, SetOptions.merge()).await()
                
                emitEvent(ZakatEvent.ShowToast("Nisab settings updated"))
                closeSettingsModal()
            } catch (e: Exception) {
                emitEvent(ZakatEvent.ShowToast("Failed to save: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun startZakatCycle() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val data = mapOf(
                    "cycleId" to UUID.randomUUID().toString(),
                    "status" to "active",
                    "startDate" to state.cycleStartDate,
                    "startWealth" to state.breakdown.netZakatableWealth,
                    "nisabAtStart" to state.settings.nisabThreshold,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("users").document(uid).collection("zakatCycles").add(data).await()
                emitEvent(ZakatEvent.ShowToast("Zakat monitoring cycle started"))
                closeStartCycleModal()
            } catch (e: Exception) {
                emitEvent(ZakatEvent.ShowToast("Failed to start cycle: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun emitEvent(event: ZakatEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}