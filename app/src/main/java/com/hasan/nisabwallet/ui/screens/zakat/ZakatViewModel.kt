package com.hasan.nisabwallet.ui.screens.zakat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Query
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
const val NISAB_SILVER_GRAMS = 612.36 // 52.5 Tola
const val NISAB_SILVER_VORI = 52.5
const val GRAMS_PER_VORI = 11.664

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

data class KaratRates(
    val k22: Double = 0.0,
    val k21: Double = 0.0,
    val k18: Double = 0.0,
    val traditional: Double = 0.0
)

data class BajusRates(
    val gold: KaratRates = KaratRates(),
    val silver: KaratRates = KaratRates(),
    val lastFetched: String = ""
)

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

    @Suppress("UNCHECKED_CAST")
    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

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

        db.collection("users").document(uid).collection("settings").limit(1)
            .addSnapshotListener { snap, _ ->
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
                        goldRates = KaratRates(
                            k22 = goldMap["k22"] ?: 0.0,
                            k21 = goldMap["k21"] ?: 0.0,
                            k18 = goldMap["k18"] ?: 0.0,
                            traditional = goldMap["traditional"] ?: 0.0
                        ),
                        silverRates = KaratRates(
                            k22 = silverMap["k22"] ?: 0.0,
                            k21 = silverMap["k21"] ?: 0.0,
                            k18 = silverMap["k18"] ?: 0.0,
                            traditional = silverMap["traditional"] ?: 0.0
                        ),
                        lastFetched = data.getString("lastFetched") ?: ""
                    )
                    _uiState.update { it.copy(settings = settings, settingsForm = settings) }
                    recalculateWealth(uid)
                }
            }

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

                val invSnap = db.collection("users").document(uid).collection("investments").whereEqualTo("status", "active").get().await()
                invSnap.documents.forEach { invTotal += ((it.getDouble("currentValue") ?: it.getDouble("purchasePrice") ?: 0.0) * (it.getDouble("quantity") ?: 1.0)) }

                val goalSnap = db.collection("users").document(uid).collection("financialGoals").whereEqualTo("status", "active").get().await()
                goalSnap.documents.forEach { goalTotal += (it.getDouble("currentAmount") ?: 0.0) }

                val jewSnap = db.collection("users").document(uid).collection("jewellery").whereNotEqualTo("status", "sold").get().await()
                jewSnap.documents.forEach { jewTotal += (it.getDouble("currentZakatValue") ?: 0.0) }

                _uiState.value.lendings.forEach {
                    if (it.countForZakat) lendInc += it.amount else lendExc += it.amount
                }

                val loanSnap = db.collection("users").document(uid).collection("loans").whereEqualTo("status", "active").get().await()
                loanSnap.documents.forEach { loanTotal += (it.getDouble("remainingBalance") ?: it.getDouble("principalAmount") ?: 0.0) }

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
                    val end = start + (354L * 24 * 60 * 60 * 1000) 
                    val today = System.currentTimeMillis()

                    if (today >= end || cycle.status == "due") {
                        status = "Zakat Due" // Ensures uniform string across app
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
                    it.copy(isLoading = false, breakdown = breakdown, zakatStatus = status, zakatAmountDue = amountDue, daysRemaining = daysRem, progressPercentage = progress)
                }
            } catch (e: Exception) {
                emitEvent(ZakatEvent.ShowToast("Wealth calculation error", true))
            }
        }
    }

    fun toggleLendingZakat(lendingId: String, count: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("lendings").document(lendingId)
                    .update("countForZakat", count)
                emitEvent(ZakatEvent.ShowToast(if(count) "Lending included in Zakat" else "Lending excluded from Zakat"))
            } catch (e: Exception) {
                emitEvent(ZakatEvent.ShowToast("Failed to update lending", true))
            }
        }
    }

    // ─── Live BAJUS Scraping & API Logic ───
    fun fetchBajusRates() {
        viewModelScope.launch {
            _uiState.update { it.copy(bajusFetchState = "loading") }
            try {
                val (fetchedRates, source) = fetchLiveMetalPrices()
                
                _uiState.update { 
                    it.copy(
                        bajusFetchState = "success",
                        fetchedRates = fetchedRates,
                        settingsForm = it.settingsForm.copy(
                            goldRates = fetchedRates.gold,
                            silverRates = fetchedRates.silver,
                            silverPricePerGram = fetchedRates.silver.traditional, 
                            silverPricePerVori = fetchedRates.silver.traditional * GRAMS_PER_VORI,
                            goldPricePerGram = fetchedRates.gold.k22,
                            goldPricePerVori = fetchedRates.gold.k22 * GRAMS_PER_VORI,
                            nisabThreshold = fetchedRates.silver.traditional * NISAB_SILVER_GRAMS, 
                            priceSource = "auto",
                            priceUnit = "gram",
                            lastFetched = fetchedRates.lastFetched
                        )
                    ) 
                }
                emitEvent(ZakatEvent.ShowToast("Rates fetched via $source"))
            } catch (e: Exception) {
                _uiState.update { it.copy(bajusFetchState = "error") }
                emitEvent(ZakatEvent.ShowToast("Failed to fetch rates: ${e.message}", true))
            }
        }
    }

    private suspend fun fetchLiveMetalPrices(): Pair<BajusRates, String> = withContext(Dispatchers.IO) {
        val timeStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())

        // Adjusted Thresholds: 50,000 isolates Gold Vori vs Gram. 500 isolates Silver Vori vs Gram.
        fun deriveGram(list: List<Double>, threshold: Double): Double {
            val max = list.maxOrNull() ?: 0.0
            return if (max > threshold) max / 11.664 else max
        }

        // 1. Scraping goldr.org (Official BAJUS aggregator)
        try {
            val doc = Jsoup.connect("https://www.goldr.org/")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(15000)
                .get()

            val html = doc.html()

            fun parsePrice(s: String?): Double? {
                if (s == null) return null
                val ascii = s.map { c ->
                    when (c) {
                        '০' -> '0'; '১' -> '1'; '২' -> '2'; '৩' -> '3'; '৪' -> '4'
                        '৫' -> '5'; '৬' -> '6'; '৭' -> '7'; '৮' -> '8'; '৯' -> '9'
                        else -> c
                    }
                }.joinToString("").replace(Regex("[^0-9]"), "")
                return ascii.toDoubleOrNull()
            }

            fun allPricesForLabel(label: String): List<Double> {
                val esc = Regex.escape(label)
                val rx = Regex(esc + """[\s\S]*?<strong>৳([\d,০-৯]+)</strong>""")
                val results = mutableListOf<Double>()
                rx.findAll(html).forEach { m ->
                    val p = parsePrice(m.groupValues.getOrNull(1))
                    if (p != null) results.add(p)
                }
                return results
            }

            val gold22 = allPricesForLabel("22 Karat Gold")
            val gold21 = allPricesForLabel("21 Karat Gold")
            val gold18 = allPricesForLabel("18 Karat Gold")
            val tradAll = allPricesForLabel("Traditional")

            val silver22 = allPricesForLabel("22 Karat Silver")
            val silver21 = allPricesForLabel("21 Karat Silver")
            val silver18 = allPricesForLabel("18 Karat Silver")

            if (gold22.isNotEmpty() && silver22.isNotEmpty()) {
                val goldRates = KaratRates(
                    k22 = deriveGram(gold22, 50000.0),
                    k21 = deriveGram(gold21, 50000.0),
                    k18 = deriveGram(gold18, 50000.0),
                    traditional = deriveGram(tradAll.take(2), 50000.0)
                )

                val silverRates = KaratRates(
                    k22 = deriveGram(silver22, 500.0),
                    k21 = deriveGram(silver21, 500.0),
                    k18 = deriveGram(silver18, 500.0),
                    traditional = if (tradAll.size > 2) deriveGram(tradAll.drop(2), 500.0) else deriveGram(silver18, 500.0) * 0.85
                )

                val bajusRates = BajusRates(gold = goldRates, silver = silverRates, lastFetched = timeStr)
                return@withContext Pair(bajusRates, "goldr.org (BAJUS)")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Direct BAJUS official site scrape
        try {
            val doc = Jsoup.connect("https://www.bajus.org/gold-price")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(15000)
                .get()

            var g22 = 0.0; var g21 = 0.0; var g18 = 0.0; var gTrad = 0.0
            var s22 = 0.0; var s21 = 0.0; var s18 = 0.0; var sTrad = 0.0

            val rows = doc.select("table tbody tr")
            for (row in rows) {
                val text = row.text().lowercase(Locale.US)
                val parsedNumbers = row.select("td").map { it.text().replace(Regex("[^0-9.]"), "") }
                    .filter { it.isNotBlank() }
                    .mapNotNull { it.toDoubleOrNull() }

                if (parsedNumbers.isNotEmpty()) {
                    val maxVal = parsedNumbers.maxOrNull() ?: 0.0
                    val isGold = text.contains("gold")
                    val threshold = if (isGold) 50000.0 else 500.0
                    val perGram = if (maxVal > threshold) maxVal / 11.664 else maxVal

                    if (isGold) {
                        when {
                            text.contains("22") -> g22 = perGram
                            text.contains("21") -> g21 = perGram
                            text.contains("18") -> g18 = perGram
                            text.contains("traditional") || text.contains("sanaton") -> gTrad = perGram
                        }
                    } else if (text.contains("silver")) {
                        when {
                            text.contains("22") -> s22 = perGram
                            text.contains("21") -> s21 = perGram
                            text.contains("18") -> s18 = perGram
                            text.contains("traditional") || text.contains("sanaton") -> sTrad = perGram
                        }
                    }
                }
            }

            if (g22 > 0.0 && (s22 > 0.0 || sTrad > 0.0)) {
                val goldRates = KaratRates(k22 = g22, k21 = if (g21 > 0) g21 else g22 * 21 / 22, k18 = if (g18 > 0) g18 else g22 * 18 / 22, traditional = if (gTrad > 0) gTrad else g22 * 14 / 22)
                val silverRates = KaratRates(k22 = s22, k21 = if (s21 > 0) s21 else s22 * 21 / 22, k18 = if (s18 > 0) s18 else s22 * 18 / 22, traditional = if (sTrad > 0) sTrad else if (s22 > 0) s22 * 0.85 else 130.0)

                val bajusRates = BajusRates(gold = goldRates, silver = silverRates, lastFetched = timeStr)
                return@withContext Pair(bajusRates, "bajus.org")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback: Spot Metal APIs + FX Conversion
        try {
            val jsonGoldStr = Jsoup.connect("https://api.gold-api.com/price/XAU").ignoreContentType(true).timeout(10000).execute().body()
            val jsonSilverStr = Jsoup.connect("https://api.gold-api.com/price/XAG").ignoreContentType(true).timeout(10000).execute().body()
            val jsonFxStr = Jsoup.connect("https://open.er-api.com/v6/latest/USD").ignoreContentType(true).timeout(10000).execute().body()

            val goldUSD = JSONObject(jsonGoldStr).getDouble("price")
            val silverUSD = JSONObject(jsonSilverStr).getDouble("price")
            val usdToBdt = JSONObject(jsonFxStr).optJSONObject("rates")?.optDouble("BDT", 122.0) ?: 122.0

            val troyOunceToGrams = 31.1034768
            val rawGoldGram = (goldUSD / troyOunceToGrams) * usdToBdt
            val rawSilverGram = (silverUSD / troyOunceToGrams) * usdToBdt

            fun kG(gram: Double, karat: Int) = Math.round(gram * karat / 24.0).toDouble()

            val goldRates = KaratRates(k22 = kG(rawGoldGram, 22), k21 = kG(rawGoldGram, 21), k18 = kG(rawGoldGram, 18), traditional = kG(rawGoldGram, 14))
            val silverRates = KaratRates(k22 = kG(rawSilverGram, 22), k21 = kG(rawSilverGram, 21), k18 = kG(rawSilverGram, 18), traditional = kG(rawSilverGram, 10))

            val bajusRates = BajusRates(gold = goldRates, silver = silverRates, lastFetched = timeStr)
            return@withContext Pair(bajusRates, "gold-api.com (Spot Fallback)")
        } catch (e: Exception) {
            throw Exception("Network connection unavailable.")
        }
    }

    fun setActiveTab(tab: String) = _uiState.update { it.copy(activeTab = tab) }
    
    fun openSettingsModal() {
        _uiState.update { it.copy(showSettingsModal = true, settingsForm = it.settings) }
        if (_uiState.value.settings.priceSource == "auto" && _uiState.value.bajusFetchState == "idle") fetchBajusRates()
    }
    
    fun closeSettingsModal() = _uiState.update { it.copy(showSettingsModal = false) }
    
    fun updateSettingsForm(form: NisabSettings) {
        _uiState.update { it.copy(settingsForm = form) }
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
                    "nisabThreshold" to form.nisabThreshold,
                    "silverPricePerGram" to form.silverPricePerGram,
                    "silverPricePerVori" to form.silverPricePerVori,
                    "goldPricePerGram" to form.goldPricePerGram,
                    "goldPricePerVori" to form.goldPricePerVori,
                    "applyDeduction" to form.applyDeduction,
                    "priceSource" to form.priceSource,
                    "priceUnit" to form.priceUnit,
                    "lastFetched" to form.lastFetched,
                    "goldRates" to mapOf("k22" to form.goldRates.k22, "k21" to form.goldRates.k21, "k18" to form.goldRates.k18, "traditional" to form.goldRates.traditional),
                    "silverRates" to mapOf("k22" to form.silverRates.k22, "k21" to form.silverRates.k21, "k18" to form.silverRates.k18, "traditional" to form.silverRates.traditional),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                
                db.collection("users").document(uid).collection("settings").document("nisab_settings")
                    .set(data, SetOptions.merge())
                
                emitEvent(ZakatEvent.ShowToast("Nisab settings updated"))
                closeSettingsModal()
            } catch (e: Exception) { 
                emitEvent(ZakatEvent.ShowToast("Failed to save", true)) 
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
                db.collection("users").document(uid).collection("zakatCycles").document().set(data)
                emitEvent(ZakatEvent.ShowToast("Zakat cycle started"))
                closeStartCycleModal()
            } catch (e: Exception) { 
                emitEvent(ZakatEvent.ShowToast("Failed to start", true)) 
            } finally { 
                _uiState.update { it.copy(isSaving = false) } 
            }
        }
    }

    fun recordZakatPayment() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val cycle = state.activeCycle ?: return
        val amt = state.paymentAmount.toDoubleOrNull() ?: 0.0
        if (amt <= 0 || state.paymentAccountId.isBlank()) { 
            emitEvent(ZakatEvent.ShowToast("Enter valid amount", true))
            return 
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val batch = db.batch()
                
                val accRef = db.collection("users").document(uid).collection("accounts").document(state.paymentAccountId)
                val currentBalance = state.accounts.find { it.id == state.paymentAccountId }?.balance ?: 0.0
                batch.update(accRef, "balance", currentBalance - amt)
                
                val txRef = db.collection("users").document(uid).collection("transactions").document()
                val txData = mapOf(
                    "type" to "Expense", 
                    "amount" to amt, 
                    "accountId" to state.paymentAccountId, 
                    "description" to "Zakat Payment", 
                    "date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()), 
                    "isZakatPayment" to true, 
                    "createdAt" to FieldValue.serverTimestamp()
                )
                batch.set(txRef, txData)

                val newPaid = cycle.totalPaid + amt
                val isFullyPaid = newPaid >= state.zakatAmountDue
                val cycleRef = db.collection("users").document(uid).collection("zakatCycles").document(cycle.id)
                val cycleUpdate = mutableMapOf<String, Any>("totalPaid" to newPaid)
                if (isFullyPaid) { 
                    cycleUpdate["status"] = "paid"
                    cycleUpdate["endDate"] = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) 
                }
                batch.update(cycleRef, cycleUpdate)
                
                batch.commit()
                
                emitEvent(ZakatEvent.ShowToast(if (isFullyPaid) "JazakAllah! Zakat fully paid." else "Payment recorded"))
                closePaymentModal()
            } catch (e: Exception) { 
                emitEvent(ZakatEvent.ShowToast("Payment failed", true)) 
            } finally { 
                _uiState.update { it.copy(isSaving = false) } 
            }
        }
    }

    private fun emitEvent(event: ZakatEvent) = viewModelScope.launch { _events.emit(event) }
}