package com.hasan.nisabwallet.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ceil

data class DashboardAccount(val id: String, val name: String, val balance: Double, val type: String)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val userName: String = "User",
    val syncStatus: String = "Connecting...",

    // Balances & Transactions
    val totalBalance: Double = 0.0,
    val accounts: List<DashboardAccount> = emptyList(),
    val thisMonthIncome: Double = 0.0,
    val thisMonthExpense: Double = 0.0,
    val recentTransactions: List<DashboardTransaction> = emptyList(),
    val categories: Map<String, DashboardCategory> = emptyMap(),

    // Zakat Status
    val nisabThreshold: Double = 0.0,
    val netZakatableWealth: Double = 0.0,
    val zakatStatus: String = "Not Mandatory",
    val zakatAmount: Double = 0.0,
    val zakatProgress: Float = 0f,
    val daysRemaining: Int = 0,
    val activeCycleDate: String? = null,

    // Loan Status
    val totalLoans: Double = 0.0
)

data class DashboardTransaction(
    val id: String = "",
    val type: String = "",
    val amount: Double = 0.0,
    val categoryId: String = "",
    val accountId: String = "",
    val description: String = "",
    val date: String = "",
    val isTransfer: Boolean = false,
    val relatedAccountName: String? = null,
    val createdAtMillis: Long = 0L,
)

data class DashboardCategory(val name: String, val color: String)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val listeners = mutableListOf<ListenerRegistration>()

    // Wealth aggregation variables
    private var accTotal = 0.0
    private var invTotal = 0.0
    private var jewTotal = 0.0
    private var lendTotal = 0.0
    private var loanTotal = 0.0
    private var activeCycleDate: String? = null
    private var activeCycleStatus: String? = null
    private var activeCycleDue: Double = 0.0

    init {
        val name = auth.currentUser?.displayName?.split(" ")?.firstOrNull() ?: "User"
        _uiState.update { it.copy(userName = name) }
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        // 1. Accounts
        listeners.add(db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val accs = snap.documents.map {
                        DashboardAccount(it.id, it.getString("name") ?: "", it.getDouble("balance") ?: 0.0, it.getString("type") ?: "")
                    }
                    accTotal = accs.sumOf { it.balance }
                    _uiState.update { it.copy(accounts = accs, totalBalance = accTotal) }
                    recalculateZakat()
                }
            })

        // 2. Categories
        listeners.add(db.collection("users").document(uid).collection("categories")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val catMap = snap.documents.associate { d ->
                        d.id to DashboardCategory(d.getString("name") ?: "Unknown", d.getString("color") ?: "#6B7280")
                    }
                    _uiState.update { it.copy(categories = catMap) }
                }
            })

        // 3. Transactions & Transfers (Recent & Monthly)
        setupTransactionListeners(uid)

        // 4. Zakat & Wealth Listeners
        setupWealthListeners(uid)
    }

    private fun setupTransactionListeners(uid: String) {
        var rawTx = emptyList<DashboardTransaction>()
        var rawTr = emptyList<DashboardTransaction>()

        val combineTx = { status: String? ->
            val all = (rawTx + rawTr).sortedWith(compareByDescending<DashboardTransaction> { it.date }.thenByDescending { it.createdAtMillis })
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val start = "%04d-%02d-01".format(year, month)
            val end = "%04d-%02d-%02d".format(year, month, cal.apply { set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH))

            var inc = 0.0; var exp = 0.0
            all.forEach { tx ->
                if (!tx.isTransfer && tx.date in start..end) {
                    if (tx.type == "Income") inc += tx.amount
                    if (tx.type == "Expense") exp += tx.amount
                }
            }

            _uiState.update { it.copy(isLoading = false, recentTransactions = all.take(8), thisMonthIncome = inc, thisMonthExpense = exp, syncStatus = status ?: it.syncStatus) }
        }

        listeners.add(db.collection("users").document(uid).collection("transactions").orderBy("date", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val status = if (snap.metadata.hasPendingWrites()) "Syncing..." else if (snap.metadata.isFromCache()) "Offline" else "Synced"
                    rawTx = snap.documents.map { d ->
                        DashboardTransaction(d.id, d.getString("type") ?: "", d.getDouble("amount") ?: 0.0, d.getString("categoryId") ?: "", d.getString("accountId") ?: "", d.getString("description") ?: "", d.getString("date") ?: "", false, null, d.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
                    }
                    combineTx(status)
                }
            })

        listeners.add(db.collection("users").document(uid).collection("transfers").orderBy("date", Query.Direction.DESCENDING).limit(20)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val exp = mutableListOf<DashboardTransaction>()
                    snap.documents.forEach { t ->
                        val amt = t.getDouble("amount") ?: 0.0
                        val date = t.getString("date") ?: ""
                        val ts = t.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        exp.add(DashboardTransaction("${t.id}-exp", "Expense", amt, "", "", "", date, true, t.getString("toAccountName"), ts))
                        exp.add(DashboardTransaction("${t.id}-inc", "Income", amt, "", "", "", date, true, t.getString("fromAccountName"), ts))
                    }
                    rawTr = exp
                    combineTx(null)
                }
            })
    }

    private fun setupWealthListeners(uid: String) {
        listeners.add(db.collection("users").document(uid).collection("settings").limit(1).addSnapshotListener { snap, _ ->
            if (snap != null && !snap.isEmpty) {
                _uiState.update { it.copy(nisabThreshold = snap.documents.first().getDouble("nisabThreshold") ?: 0.0) }
                recalculateZakat()
            }
        })

        listeners.add(db.collection("users").document(uid).collection("zakatCycles").whereEqualTo("status", "active").limit(1).addSnapshotListener { snap, _ ->
            if (snap != null && !snap.isEmpty) {
                val d = snap.documents.first()
                activeCycleStatus = d.getString("status")
                activeCycleDate = d.getString("startDate")
                activeCycleDue = d.getDouble("zakatDue") ?: 0.0
            } else {
                activeCycleStatus = null
                activeCycleDate = null
            }
            recalculateZakat()
        })

        listeners.add(db.collection("users").document(uid).collection("investments").whereEqualTo("status", "active").addSnapshotListener { snap, _ ->
            invTotal = snap?.documents?.sumOf { ((it.getDouble("currentValue") ?: it.getDouble("purchasePrice") ?: 0.0) * (it.getDouble("quantity") ?: 1.0)) } ?: 0.0
            recalculateZakat()
        })

        listeners.add(db.collection("users").document(uid).collection("jewellery").whereNotEqualTo("status", "sold").addSnapshotListener { snap, _ ->
            jewTotal = snap?.documents?.sumOf { it.getDouble("currentZakatValue") ?: 0.0 } ?: 0.0
            recalculateZakat()
        })

        listeners.add(db.collection("users").document(uid).collection("lendings").whereEqualTo("status", "active").addSnapshotListener { snap, _ ->
            lendTotal = snap?.documents?.filter { it.getBoolean("countForZakat") == true }?.sumOf { it.getDouble("remainingBalance") ?: it.getDouble("principalAmount") ?: 0.0 } ?: 0.0
            recalculateZakat()
        })

        listeners.add(db.collection("users").document(uid).collection("loans").whereEqualTo("status", "active").addSnapshotListener { snap, _ ->
            loanTotal = snap?.documents?.sumOf { it.getDouble("remainingBalance") ?: it.getDouble("principalAmount") ?: 0.0 } ?: 0.0
            _uiState.update { it.copy(totalLoans = loanTotal) }
            recalculateZakat()
        })
    }

    private fun recalculateZakat() {
        val netWealth = maxOf(0.0, accTotal + invTotal + jewTotal + lendTotal - loanTotal)
        val nisab = _uiState.value.nisabThreshold

        var status = "Not Mandatory"
        var progress = 0f
        var daysRem = 0
        var amt = 0.0

        if (nisab > 0) {
            progress = ((netWealth / nisab) * 100).toFloat().coerceIn(0f, 100f)
            amt = netWealth * 0.025

            if (activeCycleDate != null) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val start = runCatching { sdf.parse(activeCycleDate!!)?.time }.getOrNull() ?: System.currentTimeMillis()
                val end = start + (354L * 24 * 60 * 60 * 1000)
                val today = System.currentTimeMillis()

                if (today >= end || activeCycleStatus == "due") {
                    status = "Due"
                    amt = if (activeCycleDue > 0) activeCycleDue else netWealth * 0.025
                } else {
                    status = "Monitoring"
                    daysRem = maxOf(0, ceil((end - today) / (1000.0 * 60 * 60 * 24)).toInt())
                }
            } else if (netWealth >= nisab) {
                status = "Not Mandatory" // Matches Zakat Screen where it asks user to manually start it
            }
        }

        _uiState.update {
            it.copy(
                netZakatableWealth = netWealth, zakatStatus = status, zakatAmount = amt,
                zakatProgress = progress, daysRemaining = daysRem, activeCycleDate = activeCycleDate
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        listeners.forEach { it.remove() }
    }
}