package com.hasan.nisabwallet.ui.screens.zakat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil

// ─── Constants ───
const val NISAB_SILVER_GRAMS = 612.36
const val GRAMS_PER_VORI = 11.664

// ─── Data Models ───
data class ZakatAccount(val id: String, val name: String, val balance: Double, val type: String)
data class LendingItem(val id: String, val name: String, val amount: Double, val countForZakat: Boolean)
data class CategoryItem(val id: String, val name: String, val isRiba: Boolean)
data class TransactionItem(val id: String, val amount: Double, val categoryId: String, val isRiba: Boolean, val type: String)

data class ZakatPayment(
    val paymentId: String = "",
    val amount: Double = 0.0,
    val accountName: String = "",
    val date: String = "",
    val recipient: String = "",
    val note: String = ""
)

data class ZakatCycle(
    val id: String = "",
    val status: String = "active",
    val startDate: String = "",
    val endDate: String = "",
    val startWealth: Double = 0.0,
    val endWealth: Double = 0.0,
    val nisabAtStart: Double = 0.0,
    val nisabAtEnd: Double = 0.0,
    val totalPaid: Double = 0.0,
    val zakatDue: Double = 0.0,
    val payments: List<ZakatPayment> = emptyList()
)

data class KaratRates(val k22: Double = 0.0, val k21: Double = 0.0, val k18: Double = 0.0, val traditional: Double = 0.0)
data class BajusRates(val gold: KaratRates = KaratRates(), val silver: KaratRates = KaratRates(), val lastFetched: String = "")

data class NisabSettings(
    val nisabThreshold: Double = 0.0,
    val silverPricePerGram: Double = 0.0,
    val silverPricePerVori: Double = 0.0,
    val goldPricePerGram: Double = 0.0,
    val goldPricePerVori: Double = 0.0,
    val applyDeduction: Boolean = false,
    val priceSource: String = "manual",
    val priceUnit: String = "gram",
    val goldRates: KaratRates = KaratRates(),
    val silverRates: KaratRates = KaratRates(),
    val lastFetched: String = ""
)

data class WealthBreakdown(
    val cashTotal: Double = 0.0, val bankTotal: Double = 0.0, val mobileTotal: Double = 0.0,
    val goldTotal: Double = 0.0, val silverTotal: Double = 0.0, val otherTotal: Double = 0.0,
    val accountsTotal: Double = 0.0, val investmentsTotal: Double = 0.0, val goalsTotal: Double = 0.0,
    val jewelleryTotal: Double = 0.0, val jewelleryCount: Int = 0,
    val lendingsIncludedTotal: Double = 0.0, val lendingsExcludedTotal: Double = 0.0,
    val ribaTotal: Double = 0.0, val loansTotal: Double = 0.0,
    val totalAssets: Double = 0.0, val netZakatableWealth: Double = 0.0
)

data class ZakatUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val activeTab: String = "overview",
    
    val activeCycle: ZakatCycle? = null,
    val dueCycle: ZakatCycle? = null,
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

    private var rawCategories = listOf<CategoryItem>()
    private var rawTransactions = listOf<TransactionItem>()
    private var isAutoCompleting = false

    init {
        startRealTimeSync()
    }

    @Suppress("UNCHECKED_CAST")
    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        // 1. Categories & Transactions for Strict Riba Calc
        db.collection("users").document(uid).collection("categories").addSnapshotListener { snap, _ ->
            if (snap != null) {
                rawCategories = snap.documents.map { CategoryItem(it.id, it.getString("name") ?: "", it.getBoolean("isRiba") ?: false) }
                recalculateWealth(uid)
            }
        }

        db.collection("users").document(uid).collection("transactions").addSnapshotListener { snap, _ ->
            if (snap != null) {
                rawTransactions = snap.documents.map { 
                    TransactionItem(it.id, it.getDouble("amount") ?: 0.0, it.getString("categoryId") ?: "", it.getBoolean("isRiba") ?: false, it.getString("type") ?: "") 
                }
                recalculateWealth(uid)
            }
        }

        // 2. Core Wealth Collections
        db.collection("users").document(uid).collection("accounts").addSnapshotListener { snap, _ ->
            if (snap != null) {
                _uiState.update { it.copy(accounts = snap.documents.map { d -> ZakatAccount(d.id, d.getString("name") ?: "", d.getDouble("balance") ?: 0.0, d.getString("type") ?: "Cash") }) }
                recalculateWealth(uid)
            }
        }

        db.collection("users").document(uid).collection("lendings").whereEqualTo("status", "active").addSnapshotListener { snap, _ ->
            if (snap != null) {
                _uiState.update { it.copy(lendings = snap.documents.map { d -> LendingItem(d.id, d.getString("borrowerName") ?: d.getString("name") ?: "", d.getDouble("remainingBalance") ?: d.getDouble("principalAmount") ?: 0.0, d.getBoolean("countForZakat") ?: false) }) }
                recalculateWealth(uid)
            }
        }

        // 3. Nisab Settings
        db.collection("users").document(uid).collection("settings").limit(1).addSnapshotListener { snap, _ ->
            if (snap != null && !snap.isEmpty) {
                val data = snap.documents.first()
                val goldMap = data.get("goldRates") as? Map<String, Double> ?: emptyMap()
                val silverMap = data.get("silverRates") as? Map<String, Double> ?: emptyMap()

                val settings = NisabSettings(
                    nisabThreshold = data.getDouble("nisabThreshold") ?: 0.0,
                    silverPricePerGram = data.getDouble("silverPricePerGram") ?: 0.0,
                    silverPricePerVori = data.getDouble("silverPricePerVori") ?: 0.0,
                    goldPricePerGram = data.getDouble("goldPricePerGram") ?: 0.0,
                    goldPricePerVori = data.getDouble("goldPricePerVori") ?: 0.0,
                    applyDeduction = data.getBoolean("applyDeduction") ?: false,
                    priceSource = data.getString("priceSource") ?: "manual",
                    priceUnit = data.getString("priceUnit") ?: "gram",
                    goldRates = KaratRates(goldMap["k22"] ?: 0.0, goldMap["k21"] ?: 0.0, goldMap["k18"] ?: 0.0, goldMap["traditional"] ?: 0.0),
                    silverRates = KaratRates(silverMap["k22"] ?: 0.0, silverMap["k21"] ?: 0.0, silverMap["k18"] ?: 0.0, silverMap["traditional"] ?: 0.0),
                    lastFetched = data.getString("lastFetched") ?: ""
                )
                _uiState.update { it.copy(settings = settings, settingsForm = settings) }
                recalculateWealth(uid)
            }
        }

        // 4. Zakat Cycles (Separating Due and Active)
        db.collection("users").document(uid).collection("zakatCycles").orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener { snap, _ ->
            if (snap != null) {
                val cycles = snap.documents.map { d ->
                    val rawPayments = d.get("payments") as? List<Map<String, Any>> ?: emptyList()
                    val payments = rawPayments.map { p -> ZakatPayment(p["paymentId"] as? String ?: "", (p["amount"] as? Number)?.toDouble() ?: 0.0, p["accountName"] as? String ?: "", p["date"] as? String ?: "", p["recipient"] as? String ?: "", p["note"] as? String ?: "") }
                    ZakatCycle(
                        id = d.id, status = d.getString("status") ?: "active", startDate = d.getString("startDate") ?: "",
                        endDate = d.getString("endDate") ?: "", startWealth = d.getDouble("startWealth") ?: 0.0,
                        endWealth = d.getDouble("endWealth") ?: 0.0, nisabAtStart = d.getDouble("nisabAtStart") ?: 0.0,
                        nisabAtEnd = d.getDouble("nisabAtEnd") ?: 0.0, totalPaid = d.getDouble("totalPaid") ?: 0.0,
                        zakatDue = d.getDouble("zakatDue") ?: 0.0, payments = payments
                    )
                }
                
                val active = cycles.find { it.status == "active" }
                val due = cycles.find { it.status == "due" }
                val history = cycles.filter { it.status == "paid" || it.status == "exempt" }
                
                _uiState.update { it.copy(activeCycle = active, dueCycle = due, cycleHistory = history) }
                recalculateWealth(uid)
            }
        }
    }

    private fun recalculateWealth(uid: String) {
        viewModelScope.launch {
            try {
                var cCash = 0.0; var cBank = 0.0; var cMobile = 0.0; var cGold = 0.0; var cSilver = 0.0; var cOther = 0.0
                var invTotal = 0.0; var goalTotal = 0.0; var jewTotal = 0.0; var loanTotal = 0.0; var jewCount = 0
                var lendInc = 0.0; var lendExc = 0.0

                _uiState.value.accounts.forEach { acc ->
                    val t = acc.type.lowercase()
                    when {
                        t.contains("cash") -> cCash += acc.balance
                        t.contains("bank") && !t.contains("mobile") -> cBank += acc.balance
                        t.contains("mobile") || t.contains("bkash") -> cMobile += acc.balance
                        t.contains("gold") -> cGold += acc.balance
                        t.contains("silver") -> cSilver += acc.balance
                        else -> cOther += acc.balance
                    }
                }
                val accTotal = cCash + cBank + cMobile + cGold + cSilver + cOther

                val invSnap = db.collection("users").document(uid).collection("investments").whereEqualTo("status", "active").get().await()
                invSnap.documents.forEach { invTotal += ((it.getDouble("currentValue") ?: it.getDouble("purchasePrice") ?: 0.0) * (it.getDouble("quantity") ?: 1.0)) }

                val goalSnap = db.collection("users").document(uid).collection("financialGoals").whereEqualTo("status", "active").get().await()
                goalSnap.documents.forEach { goalTotal += (it.getDouble("currentAmount") ?: 0.0) }

                val jewSnap = db.collection("users").document(uid).collection("jewellery").whereNotEqualTo("status", "sold").get().await()
                jewSnap.documents.forEach { 
                    val v = it.getDouble("currentZakatValue") ?: 0.0
                    if (v > 0) { jewTotal += v; jewCount++ }
                }

                _uiState.value.lendings.forEach { if (it.countForZakat) lendInc += it.amount else lendExc += it.amount }

                val loanSnap = db.collection("users").document(uid).collection("loans").whereEqualTo("status", "active").get().await()
                loanSnap.documents.forEach { loanTotal += (it.getDouble("remainingBalance") ?: it.getDouble("principalAmount") ?: 0.0) }

                // Strict Riba Check
                val ribaCatIds = rawCategories.filter { it.isRiba || it.name.contains("interest", true) || it.name.contains("riba", true) }.map { it.id }
                val ribaTotal = rawTransactions.filter { it.type == "Income" && (it.isRiba || ribaCatIds.contains(it.categoryId)) }.sumOf { it.amount }

                val totalAssets = accTotal + invTotal + goalTotal + jewTotal + lendInc
                val netWealth = maxOf(0.0, totalAssets - loanTotal - ribaTotal)

                val breakdown = WealthBreakdown(cCash, cBank, cMobile, cGold, cSilver, cOther, accTotal, invTotal, goalTotal, jewTotal, jewCount, lendInc, lendExc, ribaTotal, loanTotal, totalAssets, netWealth)

                val active = _uiState.value.activeCycle
                val due = _uiState.value.dueCycle
                val nisab = _uiState.value.settings.nisabThreshold

                var status = "Not Mandatory"
                var amountDue = 0.0
                var daysRem = 0
                val progress = if (nisab > 0) ((netWealth / nisab) * 100).toFloat().coerceIn(0f, 100f) else 0f

                if (active != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val start = runCatching { sdf.parse(active.startDate)?.time }.getOrNull() ?: System.currentTimeMillis()
                    val today = System.currentTimeMillis()
                    val end = start + (354L * 24 * 60 * 60 * 1000)

                    if (today >= end) {
                        checkAndAutoCompleteCycle(netWealth, active)
                    } else {
                        status = "Monitoring"
                        daysRem = maxOf(0, ceil((end - today) / (1000.0 * 60 * 60 * 24)).toInt())
                    }
                } else if (due != null) {
                    status = "Due"
                    amountDue = due.zakatDue
                } else if (nisab > 0 && netWealth >= nisab) {
                    status = "Not Mandatory"
                    amountDue = netWealth * 0.025
                }

                _uiState.update { it.copy(isLoading = false, breakdown = breakdown, zakatStatus = status, zakatAmountDue = amountDue, daysRemaining = daysRem, progressPercentage = progress) }
            } catch (e: Exception) {
                emitEvent(ZakatEvent.ShowToast("Calculation error", true))
            }
        }
    }

    // Auto-Rollover Logic Ported from Web App
    private fun checkAndAutoCompleteCycle(netWealth: Double, activeCycle: ZakatCycle) {
        if (isAutoCompleting) return
        val uid = auth.currentUser?.uid ?: return
        isAutoCompleting = true
        
        viewModelScope.launch {
            try {
                // Attempt fresh fetch, fallback to saved Nisab
                val (rates, _) = try { fetchLiveMetalPrices() } catch (e: Exception) { Pair(null, null) }
                val finalNisab = rates?.silver?.traditional?.times(NISAB_SILVER_GRAMS) ?: _uiState.value.settings.nisabThreshold
                
                val isDue = netWealth >= finalNisab
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val batch = db.batch()
                
                // Close current
                val cycleRef = db.collection("users").document(uid).collection("zakatCycles").document(activeCycle.id)
                batch.update(cycleRef, mapOf(
                    "status" to if (isDue) "due" else "exempt",
                    "endDate" to todayStr,
                    "endWealth" to netWealth,
                    "zakatDue" to if (isDue) netWealth * 0.025 else 0.0,
                    "nisabAtEnd" to finalNisab,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                
                // Start new if due
                if (isDue && finalNisab > 0) {
                    val newRef = db.collection("users").document(uid).collection("zakatCycles").document()
                    batch.set(newRef, mapOf(
                        "cycleId" to UUID.randomUUID().toString(),
                        "status" to "active",
                        "startDate" to todayStr,
                        "startWealth" to netWealth,
                        "nisabAtStart" to finalNisab,
                        "createdAt" to FieldValue.serverTimestamp()
                    ))
                }
                batch.commit().await()
                emitEvent(ZakatEvent.ShowToast(if (isDue) "1 Year Passed: Zakat is Due" else "1 Year Passed: Exempt"))
            } finally {
                isAutoCompleting = false
            }
        }
    }

    fun markAsExempt() {
        val uid = auth.currentUser?.uid ?: return
        val cycle = _uiState.value.dueCycle ?: _uiState.value.activeCycle ?: return
        
        viewModelScope.launch {
            try {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                db.collection("users").document(uid).collection("zakatCycles").document(cycle.id).update(mapOf(
                    "status" to "exempt",
                    "endDate" to todayStr,
                    "endWealth" to _uiState.value.breakdown.netZakatableWealth,
                    "updatedAt" to FieldValue.serverTimestamp()
                )).await()
                emitEvent(ZakatEvent.ShowToast("Cycle marked as exempt"))
            } catch (e: Exception) {
                emitEvent(ZakatEvent.ShowToast("Failed to exempt", true))
            }
        }
    }

    fun toggleLendingZakat(lendingId: String, count: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("lendings").document(lendingId).update("countForZakat", count)
                emitEvent(ZakatEvent.ShowToast(if(count) "Lending included in Zakat" else "Lending excluded from Zakat"))
            } catch (e: Exception) {}
        }
    }

    fun fetchBajusRates() {
        viewModelScope.launch {
            _uiState.update { it.copy(bajusFetchState = "loading") }
            try {
                val (fetchedRates, source) = fetchLiveMetalPrices()
                _uiState.update { 
                    it.copy(
                        bajusFetchState = "success", fetchedRates = fetchedRates,
                        settingsForm = it.settingsForm.copy(
                            goldRates = fetchedRates.gold, silverRates = fetchedRates.silver,
                            silverPricePerGram = fetchedRates.silver.traditional, silverPricePerVori = fetchedRates.silver.traditional * GRAMS_PER_VORI,
                            goldPricePerGram = fetchedRates.gold.k22, goldPricePerVori = fetchedRates.gold.k22 * GRAMS_PER_VORI,
                            nisabThreshold = fetchedRates.silver.traditional * NISAB_SILVER_GRAMS, priceSource = "auto", priceUnit = "gram", lastFetched = fetchedRates.lastFetched
                        )
                    ) 
                }
                emitEvent(ZakatEvent.ShowToast("Rates fetched via $source"))
            } catch (e: Exception) {
                _uiState.update { it.copy(bajusFetchState = "error") }
                emitEvent(ZakatEvent.ShowToast("Failed to fetch rates", true))
            }
        }
    }

    private suspend fun fetchLiveMetalPrices(): Pair<BajusRates, String> = withContext(Dispatchers.IO) {
        val timeStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())
        fun deriveGram(list: List<Double>, t: Double): Double { val m = list.maxOrNull() ?: 0.0; return if (m > t) m / 11.664 else m }

        try {
            val doc = Jsoup.connect("https://www.goldr.org/").userAgent("Mozilla/5.0").timeout(15000).get()
            val html = doc.html()
            fun p(s: String?) = s?.map { c -> when(c) { '০'->'0'; '১'->'1'; '২'->'2'; '৩'->'3'; '৪'->'4'; '৫'->'5'; '৬'->'6'; '৭'->'7'; '৮'->'8'; '৯'->'9'; else->c } }?.joinToString("")?.replace(Regex("[^0-9]"), "")?.toDoubleOrNull()
            fun ext(label: String): List<Double> {
                val rx = Regex(Regex.escape(label) + """[\s\S]*?<strong>৳([\d,০-৯]+)</strong>""")
                return rx.findAll(html).mapNotNull { p(it.groupValues.getOrNull(1)) }.toList()
            }
            
            val g22 = ext("22 Karat Gold"); val s22 = ext("22 Karat Silver")
            if (g22.isNotEmpty() && s22.isNotEmpty()) {
                val trad = ext("Traditional")
                val goldRates = KaratRates(k22 = deriveGram(g22, 50000.0), k21 = deriveGram(ext("21 Karat Gold"), 50000.0), k18 = deriveGram(ext("18 Karat Gold"), 50000.0), traditional = deriveGram(trad.take(2), 50000.0))
                val silverRates = KaratRates(k22 = deriveGram(s22, 500.0), k21 = deriveGram(ext("21 Karat Silver"), 500.0), k18 = deriveGram(ext("18 Karat Silver"), 500.0), traditional = if (trad.size > 2) deriveGram(trad.drop(2), 500.0) else deriveGram(ext("18 Karat Silver"), 500.0) * 0.85)
                return@withContext Pair(BajusRates(goldRates, silverRates, timeStr), "goldr.org")
            }
        } catch (e: Exception) {}

        try {
            val doc = Jsoup.connect("https://www.bajus.org/gold-price").userAgent("Mozilla/5.0").timeout(15000).get()
            var g22=0.0; var g21=0.0; var g18=0.0; var gTrad=0.0
            var s22=0.0; var s21=0.0; var s18=0.0; var sTrad=0.0
            for (row in doc.select("table tbody tr")) {
                val txt = row.text().lowercase(Locale.US)
                val nums = row.select("td").mapNotNull { it.text().replace(Regex("[^0-9.]"), "").toDoubleOrNull() }
                if (nums.isNotEmpty()) {
                    val m = nums.maxOrNull() ?: 0.0
                    val isG = txt.contains("gold"); val pG = if (m > (if (isG) 50000.0 else 500.0)) m / 11.664 else m
                    if (isG) { when { txt.contains("22")->g22=pG; txt.contains("21")->g21=pG; txt.contains("18")->g18=pG; else->gTrad=pG } }
                    else { when { txt.contains("22")->s22=pG; txt.contains("21")->s21=pG; txt.contains("18")->s18=pG; else->sTrad=pG } }
                }
            }
            if (g22 > 0 && (s22 > 0 || sTrad > 0)) {
                val goldRates = KaratRates(g22, if(g21>0) g21 else g22*21/22, if(g18>0) g18 else g22*18/22, if(gTrad>0) gTrad else g22*14/22)
                val silverRates = KaratRates(s22, if(s21>0) s21 else s22*21/22, if(s18>0) s18 else s22*18/22, if(sTrad>0) sTrad else if(s22>0) s22*0.85 else 130.0)
                return@withContext Pair(BajusRates(goldRates, silverRates, timeStr), "bajus.org")
            }
        } catch (e: Exception) {}

        throw Exception("Network unavailable")
    }

    fun setActiveTab(tab: String) = _uiState.update { it.copy(activeTab = tab) }
    
    fun openSettingsModal() {
        _uiState.update { it.copy(showSettingsModal = true, settingsForm = it.settings) }
        if (_uiState.value.settings.priceSource == "auto" && _uiState.value.bajusFetchState == "idle") fetchBajusRates()
    }
    fun closeSettingsModal() = _uiState.update { it.copy(showSettingsModal = false) }
    fun updateSettingsForm(f: NisabSettings) = _uiState.update { it.copy(settingsForm = f) }

    fun openStartCycleModal() = _uiState.update { it.copy(showStartCycleModal = true) }
    fun closeStartCycleModal() = _uiState.update { it.copy(showStartCycleModal = false) }
    fun setCycleStartDate(d: String) = _uiState.update { it.copy(cycleStartDate = d) }

    fun openPaymentModal(prefillAmt: Double? = null) {
        val cycle = _uiState.value.dueCycle ?: _uiState.value.activeCycle
        val due = cycle?.zakatDue ?: _uiState.value.zakatAmountDue
        val rem = maxOf(0.0, due - (cycle?.totalPaid ?: 0.0))
        _uiState.update { it.copy(showPaymentModal = true, paymentAmount = String.format(Locale.US, "%.2f", prefillAmt ?: rem), paymentAccountId = it.accounts.firstOrNull()?.id ?: "") }
    }
    fun closePaymentModal() = _uiState.update { it.copy(showPaymentModal = false) }
    fun updatePaymentAmount(a: String) = _uiState.update { it.copy(paymentAmount = a) }
    fun updatePaymentAccount(id: String) = _uiState.update { it.copy(paymentAccountId = id) }

    fun saveNisabSettings() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.settingsForm
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val data = mapOf(
                    "nisabThreshold" to form.nisabThreshold, "silverPricePerGram" to form.silverPricePerGram, "silverPricePerVori" to form.silverPricePerVori,
                    "goldPricePerGram" to form.goldPricePerGram, "goldPricePerVori" to form.goldPricePerVori, "applyDeduction" to form.applyDeduction,
                    "priceSource" to form.priceSource, "priceUnit" to form.priceUnit, "lastFetched" to form.lastFetched,
                    "goldRates" to mapOf("k22" to form.goldRates.k22, "k21" to form.goldRates.k21, "k18" to form.goldRates.k18, "traditional" to form.goldRates.traditional),
                    "silverRates" to mapOf("k22" to form.silverRates.k22, "k21" to form.silverRates.k21, "k18" to form.silverRates.k18, "traditional" to form.silverRates.traditional),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                
                // Fixed ID mismatch by querying limit(1) like the web app
                val ref = db.collection("users").document(uid).collection("settings")
                val snap = ref.limit(1).get().await()
                if (snap.isEmpty) ref.add(data).await() else ref.document(snap.documents.first().id).set(data, SetOptions.merge()).await()
                
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
                db.collection("users").document(uid).collection("zakatCycles").document().set(mapOf(
                    "cycleId" to UUID.randomUUID().toString(), "status" to "active", "startDate" to state.cycleStartDate,
                    "startWealth" to state.breakdown.netZakatableWealth, "nisabAtStart" to state.settings.nisabThreshold, "createdAt" to FieldValue.serverTimestamp()
                ))
                emitEvent(ZakatEvent.ShowToast("Zakat cycle started"))
                closeStartCycleModal()
            } catch (e: Exception) { emitEvent(ZakatEvent.ShowToast("Failed to start", true)) } 
            finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    fun recordZakatPayment() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val cycle = state.dueCycle ?: state.activeCycle ?: return
        val amt = state.paymentAmount.toDoubleOrNull() ?: 0.0
        val acc = state.accounts.find { it.id == state.paymentAccountId }
        
        if (amt <= 0 || acc == null || amt > acc.balance) { emitEvent(ZakatEvent.ShowToast("Invalid amount or insufficient balance", true)); return }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val batch = db.batch()
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                
                batch.update(db.collection("users").document(uid).collection("accounts").document(acc.id), "balance", acc.balance - amt)
                batch.set(db.collection("users").document(uid).collection("transactions").document(), mapOf("type" to "Expense", "amount" to amt, "accountId" to acc.id, "description" to "Zakat Payment", "date" to dateStr, "isZakatPayment" to true, "createdAt" to FieldValue.serverTimestamp()))

                val newPaid = cycle.totalPaid + amt
                val isFullyPaid = newPaid >= (if(cycle.zakatDue > 0) cycle.zakatDue else state.zakatAmountDue)
                
                val newPmt = mapOf("paymentId" to UUID.randomUUID().toString(), "amount" to amt, "accountName" to acc.name, "date" to dateStr)
                val updates = mutableMapOf<String, Any>("totalPaid" to newPaid, "payments" to FieldValue.arrayUnion(newPmt))
                if (isFullyPaid) { updates["status"] = "paid"; updates["endDate"] = dateStr }
                
                batch.update(db.collection("users").document(uid).collection("zakatCycles").document(cycle.id), updates)
                batch.commit().await()
                
                emitEvent(ZakatEvent.ShowToast(if (isFullyPaid) "JazakAllah! Zakat fully paid." else "Payment recorded"))
                closePaymentModal()
            } catch (e: Exception) { emitEvent(ZakatEvent.ShowToast("Payment failed", true)) } 
            finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    private fun emitEvent(event: ZakatEvent) = viewModelScope.launch { _events.emit(event) }
}