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
import java.util.Calendar
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val syncStatus: String = "Connecting...",
    val totalBalance: Double = 0.0,
    val thisMonthIncome: Double = 0.0,
    val thisMonthExpense: Double = 0.0,
    val recentTransactions: List<DashboardTransaction> = emptyList(),
    val categories: Map<String, DashboardCategory> = emptyMap(),
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

    private var accListener: ListenerRegistration? = null
    private var txListener: ListenerRegistration? = null
    private var trListener: ListenerRegistration? = null
    private var catListener: ListenerRegistration? = null

    private var rawTransactions = emptyList<DashboardTransaction>()
    private var rawTransfers = emptyList<DashboardTransaction>()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        accListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val total = snap.documents.sumOf { it.getDouble("balance") ?: 0.0 }
                    _uiState.update { it.copy(totalBalance = total) }
                }
            }

        catListener = db.collection("users").document(uid).collection("categories")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val catMap = snap.documents.associate { d ->
                        d.id to DashboardCategory(
                            name = d.getString("name") ?: "Unknown",
                            color = d.getString("color") ?: "#6B7280"
                        )
                    }
                    _uiState.update { it.copy(categories = catMap) }
                }
            }

        txListener = db.collection("users").document(uid).collection("transactions")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val status = when {
                        snap.metadata.hasPendingWrites() -> "Syncing..."
                        snap.metadata.isFromCache() -> "Offline (Cached)"
                        else -> "Synced"
                    }
                    rawTransactions = snap.documents.map { d ->
                        DashboardTransaction(
                            id = d.id,
                            type = d.getString("type") ?: "",
                            amount = d.getDouble("amount") ?: 0.0,
                            categoryId = d.getString("categoryId") ?: "",
                            accountId = d.getString("accountId") ?: "",
                            description = d.getString("description") ?: "",
                            date = d.getString("date") ?: "",
                            createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                        )
                    }
                    combineAndEmit(status)
                }
            }

        trListener = db.collection("users").document(uid).collection("transfers")
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val expanded = mutableListOf<DashboardTransaction>()
                    snap.documents.forEach { t ->
                        val amt = t.getDouble("amount") ?: 0.0
                        val date = t.getString("date") ?: ""
                        val ts = t.getTimestamp("createdAt")?.toDate()?.time ?: 0L

                        expanded.add(DashboardTransaction(
                            id = "${t.id}-exp", isTransfer = true, type = "Expense", amount = amt,
                            date = date, createdAtMillis = ts, relatedAccountName = t.getString("toAccountName")
                        ))
                        expanded.add(DashboardTransaction(
                            id = "${t.id}-inc", isTransfer = true, type = "Income", amount = amt,
                            date = date, createdAtMillis = ts, relatedAccountName = t.getString("fromAccountName")
                        ))
                    }
                    rawTransfers = expanded
                    combineAndEmit(null)
                }
            }
    }

    private fun combineAndEmit(newSyncStatus: String?) {
        val all = (rawTransactions + rawTransfers).sortedWith(
            compareByDescending<DashboardTransaction> { it.date }.thenByDescending { it.createdAtMillis }
        )

        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val start = "%04d-%02d-01".format(year, month)
        val end = "%04d-%02d-%02d".format(year, month, cal.apply { set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH))

        var inc = 0.0
        var exp = 0.0
        all.forEach { tx ->
            if (!tx.isTransfer && tx.date in start..end) {
                if (tx.type == "Income") inc += tx.amount
                if (tx.type == "Expense") exp += tx.amount
            }
        }

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                recentTransactions = all.take(8),
                thisMonthIncome = inc,
                thisMonthExpense = exp,
                syncStatus = newSyncStatus ?: state.syncStatus
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        accListener?.remove()
        txListener?.remove()
        trListener?.remove()
        catListener?.remove()
    }
}