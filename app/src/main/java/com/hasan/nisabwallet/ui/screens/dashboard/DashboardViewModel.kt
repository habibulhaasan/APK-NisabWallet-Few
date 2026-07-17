package com.hasan.nisabwallet.ui.screens.dashboard

// Converted from: src/app/dashboard/page.js
// Source deps: zakatUtils.js (calculateZakatableWealth, determineZakatStatus, addOneHijriYear,
//              daysUntilHijriAnniversary, hasOneHijriYearPassed, calculateZakat, ZAKAT_STATUS),
//              firestoreCollections.js (getAccounts), jewelleryCollections.js (getJewellery)

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hasan.nisabwallet.core.util.HijriConverter
import com.hasan.nisabwallet.core.util.ZakatCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject

// ─── UI State ─────────────────────────────────────────────────────────────────
// Maps 1-to-1 with the state variables in dashboard/page.js

data class DashboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,

    // ── original state from page.js ──
    val accounts: List<AccountSummary> = emptyList(),
    val loans: List<LoanSummary> = emptyList(),
    val nisabThreshold: Double = 0.0,
    val activeCycle: ZakatCycleSummary? = null,
    val thisMonthIncome: Double = 0.0,
    val thisMonthExpense: Double = 0.0,
    val recentTransactions: List<TransactionSummary> = emptyList(),

    // ── new state added in page.js ──
    val jewellery: List<JewellerySummary> = emptyList(),
    val investments: List<InvestmentSummary> = emptyList(),
    val lendings: List<LendingSummary> = emptyList(),
    val goals: List<GoalSummary> = emptyList(),

    // ── derived zakat values (computed in ViewModel, mirrors page.js derivations) ──
    val zakatStatus: String = ZakatStatus.NOT_MANDATORY,
    val zakatProgress: Double = 0.0,          // 0-100 %
    val daysRemaining: Int = 0,
    val yearEndDate: Date? = null,
    val zakatAmount: Double = 0.0,
    val netZakatableWealth: Double = 0.0,
    val wealthBreakdown: WealthBreakdown = WealthBreakdown(),
)

// Mirror of ZAKAT_STATUS enum in zakatUtils.js
object ZakatStatus {
    const val NOT_MANDATORY  = "Not Mandatory"
    const val MONITORING     = "Monitoring"
    const val DUE            = "Zakat Due"
    const val PAID           = "Paid"
    const val EXEMPT         = "Exempt"
    const val READY_TO_START = "Ready to Monitor"
}

// ─── Light model classes (mirrors Firestore document fields exactly) ──────────

data class AccountSummary(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val balance: Double = 0.0,
)

data class LoanSummary(
    val id: String = "",
    val status: String = "",
    val remainingBalance: Double = 0.0,
    val monthlyPayment: Double = 0.0,
    val nextPaymentDue: String? = null,
)

data class ZakatCycleSummary(
    val id: String = "",
    val status: String = "",
    val startDate: String = "",
    val startDateHijri: Map<String, Any> = emptyMap(),   // { year, month, day, formatted }
)

data class TransactionSummary(
    val id: String = "",
    val type: String = "",                    // "Income" | "Expense"
    val amount: Double = 0.0,
    val description: String = "",
    val date: String = "",
    val accountId: String = "",
    val categoryId: String = "",
    val isTransfer: Boolean = false,
    val fromAccountName: String = "",
    val toAccountName: String = "",
    val createdAtMillis: Long = 0L,
)

data class JewellerySummary(
    val id: String = "",
    val name: String = "",
    val currentZakatValue: Double = 0.0,     // 0 means not priced yet
)

data class InvestmentSummary(
    val id: String = "",
    val status: String = "",
    val currentValue: Double = 0.0,
    val purchasePrice: Double = 0.0,
    val quantity: Double = 1.0,
)

data class LendingSummary(
    val id: String = "",
    val status: String = "",
    val remainingBalance: Double = 0.0,
    val principalAmount: Double = 0.0,
    val nextPaymentDue: String? = null,
    val dueDate: String? = null,
)

data class GoalSummary(
    val id: String = "",
    val status: String = "",
    val currentAmount: Double = 0.0,
    val targetAmount: Double = 0.0,
)

data class WealthBreakdown(
    val accountsTotal: Double = 0.0,
    val jewelleryTotal: Double = 0.0,
    val lendingsTotal: Double = 0.0,
    val investmentsTotal: Double = 0.0,
    val loansTotal: Double = 0.0,
    val netZakatableWealth: Double = 0.0,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    // ── Pull-to-refresh ────────────────────────────────────────────────────
    fun refresh() = loadDashboardData()

    // ── Main loader — mirrors loadDashboardData() in page.js ───────────────
    // Runs all sub-loaders in parallel (Promise.all equivalent = async/await + coroutine)
    private fun loadDashboardData() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val accountsDeferred     = async { loadAccounts(uid) }
                val loansDeferred        = async { loadLoans(uid) }
                val nisabDeferred        = async { loadNisabSettings(uid) }
                val cycleDeferred        = async { loadActiveCycle(uid) }
                val transDeferred        = async { loadTransactions(uid) }
                val jewelleryDeferred    = async { loadJewellery(uid) }
                val investmentsDeferred  = async { loadInvestments(uid) }
                val lendingsDeferred     = async { loadLendings(uid) }
                val goalsDeferred        = async { loadGoals(uid) }

                val accounts     = accountsDeferred.await()
                val loans        = loansDeferred.await()
                val nisab        = nisabDeferred.await()
                val cycle        = cycleDeferred.await()
                val txResult     = transDeferred.await()
                val jewellery    = jewelleryDeferred.await()
                val investments  = investmentsDeferred.await()
                val lendings     = lendingsDeferred.await()
                val goals        = goalsDeferred.await()

                // Compute all derived values — mirrors page.js derived values block
                val derived = computeDerived(
                    accounts, loans, nisab, cycle, jewellery, investments, lendings, goals
                )

                _uiState.update { state ->
                    state.copy(
                        isLoading            = false,
                        accounts             = accounts,
                        loans                = loans,
                        nisabThreshold       = nisab,
                        activeCycle          = cycle,
                        thisMonthIncome      = txResult.income,
                        thisMonthExpense     = txResult.expense,
                        recentTransactions   = txResult.recent,
                        jewellery            = jewellery,
                        investments          = investments,
                        lendings             = lendings,
                        goals                = goals,
                        zakatStatus          = derived.zakatStatus,
                        zakatProgress        = derived.zakatProgress,
                        daysRemaining        = derived.daysRemaining,
                        yearEndDate          = derived.yearEndDate,
                        zakatAmount          = derived.zakatAmount,
                        netZakatableWealth   = derived.netZakatableWealth,
                        wealthBreakdown      = derived.wealthBreakdown,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ── loadAccounts — mirrors loadAccounts() in page.js ──────────────────
    private suspend fun loadAccounts(uid: String): List<AccountSummary> {
        val snap = db.collection("users").document(uid)
            .collection("accounts").get().await()
        return snap.documents.map { d ->
            AccountSummary(
                id      = d.id,
                name    = d.getString("name") ?: "",
                type    = d.getString("type") ?: "",
                balance = d.getDouble("balance") ?: 0.0,
            )
        }
    }

    // ── loadLoans — mirrors loadLoans() in page.js ────────────────────────
    private suspend fun loadLoans(uid: String): List<LoanSummary> {
        val snap = db.collection("users").document(uid)
            .collection("loans").get().await()
        return snap.documents.map { d ->
            LoanSummary(
                id               = d.id,
                status           = d.getString("status") ?: "",
                remainingBalance = d.getDouble("remainingBalance") ?: 0.0,
                monthlyPayment   = d.getDouble("monthlyPayment") ?: 0.0,
                nextPaymentDue   = d.getString("nextPaymentDue"),
            )
        }
    }

    // ── loadNisabSettings — mirrors loadNisabSettings() in page.js ────────
    private suspend fun loadNisabSettings(uid: String): Double {
        val snap = db.collection("users").document(uid)
            .collection("settings").get().await()
        return snap.documents.firstOrNull()?.getDouble("nisabThreshold") ?: 0.0
    }

    // ── loadActiveCycle — mirrors loadActiveCycle() in page.js ────────────
    // Queries zakatCycles where status == 'active'
    private suspend fun loadActiveCycle(uid: String): ZakatCycleSummary? {
        val snap = db.collection("users").document(uid)
            .collection("zakatCycles")
            .whereEqualTo("status", "active")
            .get().await()
        val doc = snap.documents.firstOrNull() ?: return null
        @Suppress("UNCHECKED_CAST")
        return ZakatCycleSummary(
            id             = doc.id,
            status         = doc.getString("status") ?: "",
            startDate      = doc.getString("startDate") ?: "",
            startDateHijri = (doc.get("startDateHijri") as? Map<String, Any>) ?: emptyMap(),
        )
    }

    // ── loadTransactions — mirrors loadTransactions() in page.js ──────────
    // Loads all transactions, computes this-month income/expense,
    // merges transfers, sorts and takes top 5 as recentTransactions
    private suspend fun loadTransactions(uid: String): TransactionResult {
        val now         = java.util.Calendar.getInstance()
        val year        = now.get(java.util.Calendar.YEAR)
        val month       = now.get(java.util.Calendar.MONTH) + 1  // 1-based
        val startDate   = "%04d-%02d-01".format(year, month)
        val lastDay     = java.util.Calendar.getInstance().apply {
            set(year, month - 1, 1)
            set(java.util.Calendar.DAY_OF_MONTH, getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        }.get(java.util.Calendar.DAY_OF_MONTH)
        val endDate     = "%04d-%02d-%02d".format(year, month, lastDay)

        val txSnap      = db.collection("users").document(uid)
            .collection("transactions").get().await()
        val transferSnap = db.collection("users").document(uid)
            .collection("transfers").get().await()

        var income  = 0.0
        var expense = 0.0
        val allTransactions = mutableListOf<TransactionSummary>()

        txSnap.documents.forEach { d ->
            val date   = d.getString("date") ?: ""
            val amount = d.getDouble("amount") ?: 0.0
            val type   = d.getString("type") ?: ""
            val tx = TransactionSummary(
                id            = d.id,
                type          = type,
                amount        = amount,
                description   = d.getString("description") ?: "",
                date          = date,
                accountId     = d.getString("accountId") ?: "",
                categoryId    = d.getString("categoryId") ?: "",
                createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            )
            allTransactions.add(tx)
            // Only count current-month transactions toward income/expense summary
            if (date in startDate..endDate) {
                if (type == "Income")  income  += amount
                if (type == "Expense") expense += amount
            }
        }

        // Merge transfers (within this month) — mirrors the transfer merge in page.js
        transferSnap.documents.forEach { d ->
            val date = d.getString("date") ?: ""
            if (date in startDate..endDate) {
                allTransactions.add(
                    TransactionSummary(
                        id              = d.id,
                        isTransfer      = true,
                        amount          = d.getDouble("amount") ?: 0.0,
                        date            = date,
                        fromAccountName = d.getString("fromAccountName") ?: "",
                        toAccountName   = d.getString("toAccountName") ?: "",
                        createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                    )
                )
            }
        }

        // Sort: by date desc, then by createdAt desc — mirrors page.js sort comparator
        val recent = allTransactions
            .sortedWith(compareByDescending<TransactionSummary> { it.date }
                .thenByDescending { it.createdAtMillis })
            .take(5)

        return TransactionResult(income, expense, recent)
    }

    // ── loadJewellery — mirrors loadJewellery() in page.js ────────────────
    private suspend fun loadJewellery(uid: String): List<JewellerySummary> {
        val snap = db.collection("users").document(uid)
            .collection("jewellery")
            .whereEqualTo("status", "active")
            .get().await()
        return snap.documents.map { d ->
            JewellerySummary(
                id                = d.id,
                name              = d.getString("name") ?: "",
                currentZakatValue = d.getDouble("currentZakatValue") ?: 0.0,
            )
        }
    }

    // ── loadInvestments — mirrors loadInvestments() in page.js ────────────
    private suspend fun loadInvestments(uid: String): List<InvestmentSummary> {
        val snap = db.collection("users").document(uid)
            .collection("investments").get().await()
        return snap.documents.map { d ->
            InvestmentSummary(
                id            = d.id,
                status        = d.getString("status") ?: "",
                currentValue  = d.getDouble("currentValue") ?: 0.0,
                purchasePrice = d.getDouble("purchasePrice") ?: 0.0,
                quantity      = d.getDouble("quantity") ?: 1.0,
            )
        }
    }

    // ── loadLendings — mirrors loadLendings() in page.js ──────────────────
    private suspend fun loadLendings(uid: String): List<LendingSummary> {
        val snap = db.collection("users").document(uid)
            .collection("lendings").get().await()
        return snap.documents.map { d ->
            LendingSummary(
                id               = d.id,
                status           = d.getString("status") ?: "",
                remainingBalance = d.getDouble("remainingBalance") ?: 0.0,
                principalAmount  = d.getDouble("principalAmount") ?: 0.0,
                nextPaymentDue   = d.getString("nextPaymentDue"),
                dueDate          = d.getString("dueDate"),
            )
        }
    }

    // ── loadGoals — mirrors loadGoals() in page.js ────────────────────────
    // NOTE: Firestore collection is 'financialGoals', NOT 'goals'
    private suspend fun loadGoals(uid: String): List<GoalSummary> {
        val snap = db.collection("users").document(uid)
            .collection("financialGoals").get().await()
        return snap.documents.map { d ->
            GoalSummary(
                id            = d.id,
                status        = d.getString("status") ?: "",
                currentAmount = d.getDouble("currentAmount") ?: 0.0,
                targetAmount  = d.getDouble("targetAmount") ?: 0.0,
            )
        }
    }

    // ── computeDerived — mirrors all derived-value blocks in page.js ───────
    private fun computeDerived(
        accounts: List<AccountSummary>,
        loans: List<LoanSummary>,
        nisabThreshold: Double,
        activeCycle: ZakatCycleSummary?,
        jewellery: List<JewellerySummary>,
        investments: List<InvestmentSummary>,
        lendings: List<LendingSummary>,
        goals: List<GoalSummary>,
    ): DerivedValues {

        // ── Jewellery (mirrors pricedJewellery / totalJewelleryZakat) ──
        val totalJewelleryZakat = jewellery.filter { it.currentZakatValue > 0 }
            .sumOf { it.currentZakatValue }

        // ── Investments — active only (mirrors activeInvestments / totalInvestmentValue) ──
        val totalInvestmentValue = investments.filter { it.status == "active" }
            .sumOf { inv ->
                val qty = if (inv.quantity > 0) inv.quantity else 1.0
                val price = if (inv.currentValue > 0) inv.currentValue else inv.purchasePrice
                price * qty
            }

        // ── Lendings (mirrors activeLendings / totalLendingValue) ──
        val activeLendings = lendings.filter { it.status == "active" }
        val totalLendingValue = activeLendings.sumOf { l ->
            if (l.remainingBalance > 0) l.remainingBalance else l.principalAmount
        }

        // ── Loans deduction (mirrors totalDebt) ──
        val activeLoans = loans.filter { it.status == "active" }
        val totalDebt   = activeLoans.sumOf { it.remainingBalance }

        // ── calculateZakatableWealth (mirrors calculateZakatableWealth() in zakatUtils.js) ──
        val accountsTotal = accounts.sumOf { it.balance }
        val netZakatableWealth = accountsTotal + totalJewelleryZakat + totalLendingValue + totalInvestmentValue - totalDebt

        val wealthBreakdown = WealthBreakdown(
            accountsTotal     = accountsTotal,
            jewelleryTotal    = totalJewelleryZakat,
            lendingsTotal     = totalLendingValue,
            investmentsTotal  = totalInvestmentValue,
            loansTotal        = totalDebt,
            netZakatableWealth = netZakatableWealth,
        )

        // ── Zakat status / progress (mirrors zakatStatus / zakatProgress block in page.js) ──
        var zakatStatus   = ZakatStatus.NOT_MANDATORY
        var zakatProgress = 0.0
        var daysRemaining = 0
        var yearEndDate: Date? = null
        var zakatAmount   = 0.0

        if (nisabThreshold > 0) {
            zakatProgress = minOf((netZakatableWealth / nisabThreshold) * 100.0, 100.0)
            zakatAmount   = ZakatCalculator.calculateZakat(netZakatableWealth)

            if (activeCycle != null && activeCycle.status == "active") {
                if (HijriConverter.hasOneHijriYearPassed(activeCycle.startDate)) {
                    zakatStatus = if (netZakatableWealth >= nisabThreshold)
                        ZakatStatus.DUE else ZakatStatus.EXEMPT
                } else {
                    zakatStatus   = ZakatStatus.MONITORING
                    daysRemaining = HijriConverter.daysUntilHijriAnniversary(activeCycle.startDate)
                    yearEndDate   = HijriConverter.addOneHijriYear(activeCycle.startDate)
                }
            } else if (netZakatableWealth >= nisabThreshold) {
                zakatStatus = ZakatStatus.READY_TO_START
            }
        }

        return DerivedValues(
            zakatStatus        = zakatStatus,
            zakatProgress      = zakatProgress,
            daysRemaining      = daysRemaining,
            yearEndDate        = yearEndDate,
            zakatAmount        = zakatAmount,
            netZakatableWealth = netZakatableWealth,
            wealthBreakdown    = wealthBreakdown,
        )
    }

    // ── Helper data classes ────────────────────────────────────────────────
    private data class TransactionResult(
        val income: Double,
        val expense: Double,
        val recent: List<TransactionSummary>,
    )

    private data class DerivedValues(
        val zakatStatus: String,
        val zakatProgress: Double,
        val daysRemaining: Int,
        val yearEndDate: Date?,
        val zakatAmount: Double,
        val netZakatableWealth: Double,
        val wealthBreakdown: WealthBreakdown,
    )

    // ── Helper: get account name by id (mirrors getAccountName() in page.js) ──
    fun getAccountName(id: String): String =
        _uiState.value.accounts.find { it.id == id }?.name ?: "Unknown"
}
