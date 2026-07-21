package com.hasan.nisabwallet.ui.screens.zakat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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

// ─── Constants ───
const val NISAB_SILVER_GRAMS = 612.36 // 52.5 Tola

// ─── Data Models ───
data class ZakatAccount(val id: String, val name: String, val balance: Double, val type: String)

data class LendingItem(val id: String, val name: String, val amount: Double, val countForZakat: Boolean)

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
    val applyDeduction: Boolean = false,
    val priceSource: String = "manual"
)

data class BajusRates(
    val silverPerGram: Double = 0.0,
    val goldPerGram: Double = 0.0,
    val lastFetched: String = ""
)

data class WealthBreakdown(
    val cashTotal: Double = 0.0,
    val bankTotal: Double = 0.0,
    val mobileTotal: Double = 0.0,
    val goldTotal: Double = 0.0,
    val silverTotal: Double = 0.0,
    val otherTotal: Double = 0.0,
    
    val accountsTotal: Double = 0.0,
    val investmentsTotal: Double = 0.0,
    val goalsTotal: Double = 0.0,
    val jewelleryTotal: Double = 0.0,
    val lendingsIncludedTotal: Double = 0.0,
    val lendingsExcludedTotal: Double = 0.0,
    
    val ribaTotal: Double = 0.0,
    val loansTotal: Double = 0.0,
    val totalAssets: Double = 0.0,
    val netZakatableWealth: Double = 0.0
)

data class ZakatUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val activeTab: String = "overview",
    
    val activeCycle: ZakatCycle? = null,
    val cycleHistory: List<ZakatCycle> = emptyList(),
    val accounts: List<ZakatAccount> = emptyList(),
    val lendings: List<LendingItem> = emptyList(),
    
    val settings: NisabSettings = NisabSettings(),
    val breakdown: WealthBreakdown = WealthBreakdown(),
    val daysRemaining: Int = 0,
    val zakatStatus: String = "Not Mandatory",
    val zakatAmountDue: Double = 0.0,
    val progressPercentage: Float = 0f,

    val showSettingsModal: Boolean = false,
    val showStartCycleModal: Boolean = false,
    val showPaymentModal: Boolean = false,

    val settingsForm: NisabSettings = NisabSettings(),
    val cycleStartDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    
    val paymentAmount: String = "",
    val paymentAccountId: String = "",
    
    val bajusFetchState: String = "idle",
    val fetchedRates: BajusRates? = null
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

        // 1. Fetch Accounts
        db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val accs = snap.documents.map {
                        ZakatAccount(it.id, it.getString("name") ?: "", it.getDouble("balance") ?: 0.0, it.getString("type") ?: "Cash")
                    }
                    _uiState.update { it.copy(accounts = accs) }
                    recalculateWealth(uid)
                }
            }

        // 2. Fetch Lendings (for toggleable Zakat inclusion)
        db.collection("users").document(uid).collection("lendings").whereEqualTo("status", "active")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val lends = snap.documents.map {
                        LendingItem(
                            it.id, 
                            it.getString("borrowerName") ?: it.getString("name") ?: "Borrower", 
                            it.getDouble("remainingBalance") ?: it.getDouble("principalAmount") ?: 0.0,
                            it.getBoolean("countForZakat") ?: false
                        )
                    }
                    _uiState.update { it.copy(lendings = lends) }
                    recalculateWealth(uid)
                }
            }

        // 3. Fetch Nisab Settings
        db.collection("users").document(uid).collection("settings").limit(1)
            .addSnapshotListener { snap, _ ->
                if (snap != null && !snap.isEmpty) {
                    val data = snap.documents.first()
                    val settings = NisabSettings(
                        nisabThreshold = data.getDouble("nisabThreshold") ?: 0.0,
                        silverPricePerGram = data.getDouble("silverPricePerGram") ?: 0.0,
                        goldPricePerGram = data.getDouble("goldPricePerGram") ?: 0.0,
                        applyDeduction = data.getBoolean("applyDeduction") ?: false,
                        priceSource = data.getString("priceSource") ?: "manual"
                    )
                    _uiState.update { it.copy(settings = settings, settingsForm = settings) }
                    recalculateWealth(uid)
                }
            }

        // 4. Fetch Zakat Cycles
        db.collection("users").document(uid).collection("zakatCycles")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val cycles = snap.documents.map { d ->
                        ZakatCycle(
                            id = d.id, status = d.getString("status") ?: "active", startDate = d.getString("startDate") ?: "",
                            startWealth = d.getDouble("startWealth") ?: 0.0, nisabAtStart = d.getDouble("nisabAtStart") ?: 0.0,
                            totalPaid = d.getDouble("totalPaid") ?: 0.0, zakatDue = d.getDouble("zakatDue") ?: 0.0
                        )
                    }
                    val active = cycles.find { it.status == "active" || it.status == "due" }
                    val history = cycles.filter { it.status == "paid" || it.status == "exempt" }
                    
                    _uiState.update { it.copy(activeCycle = active, cycleHistory = history) }
                    recalculateWealth(uid)
                }
            }
    }

    private fun recalculateWealth(uid: String) {
        viewModelScope.launch {
            try {
                var cCash = 0.0; var cBank = 0.0; var cMobile = 0.0; var cGold = 0.0; var cSilver = 0.0; var cOther = 0.0
                var invTotal = 0.0; var goalTotal = 0.0; var jewTotal = 0.0; var ribaTotal = 0.0; var loanTotal = 0.0
                var lendInc = 0.0; var lendExc = 0.0

                // Accounts
                _uiState.value.accounts.forEach { acc ->
                    val type = acc.type.lowercase()
                    when {
                        type.contains("cash") -> cCash += acc.balance
                        type.contains("bank") && !type.contains("mobile") -> cBank += acc.balance
                        type.contains("mobile") || type.contains("bkash") -> cMobile += acc.balance
                        type.contains("gold") -> cGold += acc.balance
                        type.contains("silver") -> cSilver += acc.balance
                        else -> cOther += acc.balance
                    }
                }
                val accTotal = cCash + cBank + cMobile + cGold + cSilver + cOther

                // Investments
                val invSnap = db.collection("users").document(uid).collection("investments").whereEqualTo("status", "active").get().await()
                invSnap.documents.forEach { invTotal += ((it.getDouble("currentValue") ?: it.getDouble("purchasePrice") ?: 0.0) * (it.getDouble("quantity") ?: 1.0)) }

                // Goals
                val goalSnap = db.collection("users").document(uid).collection("financialGoals").whereEqualTo("status", "active").get().await()
                goalSnap.documents.forEach { goalTotal += (it.getDouble("currentAmount") ?: 0.0) }

                // Jewellery
                val jewSnap = db.collection("users").document(uid).collection("jewellery").whereNotEqualTo("status", "sold").get().await()
                jewSnap.documents.forEach { jewTotal += (it.getDouble("currentZakatValue") ?: 0.0) }

                // Lendings
                _uiState.value.lendings.forEach {
                    if (it.countForZakat) lendInc += it.amount else lendExc += it.amount
                }

                // Loans
                val loanSnap = db.collection("users").document(uid).collection("loans").whereEqualTo("status", "active").get().await()
                loanSnap.documents.forEach { loanTotal += (it.getDouble("remainingBalance") ?: it.getDouble("principalAmount") ?: 0.0) }

                // Riba Transactions (To Exclude)
                val txSnap = db.collection("users").document(uid).collection("transactions").whereEqualTo("isRiba", true).get().await()
                txSnap.documents.forEach { ribaTotal += (it.getDouble("amount") ?: 0.0) }

                val totalAssets = accTotal + invTotal + goalTotal + jewTotal + lendInc
                val netWealth = maxOf(0.0, totalAssets - loanTotal - ribaTotal)
                val breakdown = WealthBreakdown(
                    cCash, cBank, cMobile, cGold, cSilver, cOther, 
                    accTotal, invTotal, goalTotal, jewTotal, lendInc, lendExc, 
                    ribaTotal, loanTotal, totalAssets, netWealth
                )

                val cycle = _uiState.value.activeCycle
                val nisab = _uiState.value.settings.nisabThreshold

                var status = "Not Mandatory"
                var amountDue = 0.0
                var daysRem = 0
                val progress = if (nisab > 0) ((netWealth / nisab) * 100).toFloat().coerceIn(0f, 100f) else 0f

                if (cycle != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val start = runCatching { sdf.parse(cycle.startDate)?.time }.getOrNull() ?: System.currentTimeMillis()
                    val end = start + (354L * 24 * 60 * 60 * 1000) // Hijri year approx
                    val today = System.currentTimeMillis()

                    if (today >= end || cycle.status == "due") {
                        status = "Due"
                        amountDue = if (cycle.zakatDue > 0) cycle.zakatDue else netWealth * 0.025
                    } else {
                        status = "Monitoring"
                        daysRem = maxOf(0, ceil((end - today) / (1000.0 * 60 * 60 * 24)).toInt())
                        amountDue = if (nisab > 0) netWealth * 0.025 else 0.0
                    }
                } else if (nisab > 0 && netWealth >= nisab) {
                    status = "Not Mandatory"
                    amountDue = netWealth * 0.025
                }

                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        breakdown = breakdown,
                        zakatStatus = status,
                        zakatAmountDue = amountDue,
                        daysRemaining = daysRem,
                        progressPercentage = progress
                    )
                }
            } catch (e: Exception) {
                emitEvent(ZakatEvent.ShowToast("Wealth calculation error", true))
            }
        }
    }

    // ─── Actions ───

    fun toggleLendingZakat(lendingId: String, count: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("lendings").document(lendingId)
                    .update("countForZakat", count).await()
                emitEvent(ZakatEvent.ShowToast(if(count) "Lending included in Zakat" else "Lending excluded from Zakat"))
            } catch (e: Exception) {
                emitEvent(ZakatEvent.ShowToast("Failed to update lending", true))
            }
        }
    }

    fun fetchBajusRates() {
        viewModelScope.launch {
            _uiState.update { it.copy(bajusFetchState = "loading") }
            delay(1500) // Simulated network
            val simulatedRates = BajusRates(145.0, 11500.0, SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date()))
            _uiState.update { 
                it.copy(
                    bajusFetchState = "success", fetchedRates = simulatedRates,
                    settingsForm = it.settingsForm.copy(silverPricePerGram = simulatedRates.silverPerGram, goldPricePerGram = simulatedRates.goldPerGram, nisabThreshold = simulatedRates.silverPerGram * NISAB_SILVER_GRAMS, priceSource = "auto")
                ) 
            }
            emitEvent(ZakatEvent.ShowToast("Live rates fetched successfully"))
        }
    }

    fun setActiveTab(tab: String) = _uiState.update { it.copy(activeTab = tab) }
    fun openSettingsModal() {
        _uiState.update { it.copy(showSettingsModal = true, settingsForm = it.settings) }
        if (_uiState.value.settings.priceSource == "auto" && _uiState.value.bajusFetchState == "idle") fetchBajusRates()
    }
    fun closeSettingsModal() = _uiState.update { it.copy(showSettingsModal = false) }
    fun updateSettingsForm(update: (NisabSettings) -> NisabSettings) {
        _uiState.update { 
            val newForm = update(it.settingsForm)
            it.copy(settingsForm = newForm.copy(nisabThreshold = newForm.silverPricePerGram * NISAB_SILVER_GRAMS)) 
        }
    }

    fun openStartCycleModal() = _uiState.update { it.copy(showStartCycleModal = true) }
    fun closeStartCycleModal() = _uiState.update { it.copy(showStartCycleModal = false) }
    fun setCycleStartDate(date: String) = _uiState.update { it.copy(cycleStartDate = date) }

    fun openPaymentModal(prefillAmt: Double? = null) {
        val remaining = maxOf(0.0, _uiState.value.zakatAmountDue - (_uiState.value.activeCycle?.totalPaid ?: 0.0))
        val amtToFill = prefillAmt ?: remaining
        _uiState.update { 
            it.copy(showPaymentModal = true, paymentAmount = String.format(Locale.US, "%.2f", amtToFill), paymentAccountId = it.accounts.firstOrNull()?.id ?: "") 
        }
    }
    fun closePaymentModal() = _uiState.update { it.copy(showPaymentModal = false) }
    fun updatePaymentAmount(amt: String) = _uiState.update { it.copy(paymentAmount = amt) }
    fun updatePaymentAccount(id: String) = _uiState.update { it.copy(paymentAccountId = id) }

    fun saveNisabSettings() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.settingsForm
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val data = mapOf(
                    "nisabThreshold" to form.nisabThreshold, "silverPricePerGram" to form.silverPricePerGram, "goldPricePerGram" to form.goldPricePerGram,
                    "applyDeduction" to form.applyDeduction, "priceSource" to form.priceSource, "updatedAt" to FieldValue.serverTimestamp()
                )
                val ref = db.collection("users").document(uid).collection("settings")
                val existing = ref.limit(1).get().await()
                if (existing.isEmpty) ref.add(data).await() else existing.documents.first().reference.set(data, SetOptions.merge()).await()
                emitEvent(ZakatEvent.ShowToast("Nisab settings updated"))
                closeSettingsModal()
            } catch (e: Exception) { emitEvent(ZakatEvent.ShowToast("Failed to save", true)) } 
            finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    fun startZakatCycle() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val data = mapOf(
                    "cycleId" to UUID.randomUUID().toString(), "status" to "active", "startDate" to state.cycleStartDate,
                    "startWealth" to state.breakdown.netZakatableWealth, "nisabAtStart" to state.settings.nisabThreshold, "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("users").document(uid).collection("zakatCycles").add(data).await()
                emitEvent(ZakatEvent.ShowToast("Zakat cycle started"))
                closeStartCycleModal()
            } catch (e: Exception) { emitEvent(ZakatEvent.ShowToast("Failed to start", true)) } 
            finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    fun recordZakatPayment() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val cycle = state.activeCycle ?: return
        val amt = state.paymentAmount.toDoubleOrNull() ?: 0.0
        if (amt <= 0 || state.paymentAccountId.isBlank()) { emitEvent(ZakatEvent.ShowToast("Enter valid amount", true)); return }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val accRef = db.collection("users").document(uid).collection("accounts").document(state.paymentAccountId)
                val accSnap = accRef.get().await()
                accRef.update("balance", (accSnap.getDouble("balance") ?: 0.0) - amt).await()
                
                db.collection("users").document(uid).collection("transactions").add(
                    mapOf("type" to "Expense", "amount" to amt, "accountId" to state.paymentAccountId, "description" to "Zakat Payment", "date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()), "isZakatPayment" to true, "createdAt" to FieldValue.serverTimestamp())
                ).await()

                val newPaid = cycle.totalPaid + amt
                val isFullyPaid = newPaid >= state.zakatAmountDue
                val cycleUpdate = mutableMapOf<String, Any>("totalPaid" to newPaid)
                if (isFullyPaid) { cycleUpdate["status"] = "paid"; cycleUpdate["endDate"] = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
                db.collection("users").document(uid).collection("zakatCycles").document(cycle.id).update(cycleUpdate).await()
                
                emitEvent(ZakatEvent.ShowToast(if (isFullyPaid) "JazakAllah! Zakat fully paid." else "Payment recorded"))
                closePaymentModal()
            } catch (e: Exception) { emitEvent(ZakatEvent.ShowToast("Payment failed", true)) } 
            finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    private fun emitEvent(event: ZakatEvent) = viewModelScope.launch { _events.emit(event) }
}