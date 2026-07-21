package com.hasan.nisabwallet.ui.screens.goals.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hasan.nisabwallet.ui.screens.goals.GoalAccount
import com.hasan.nisabwallet.ui.screens.goals.GoalItem
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
import javax.inject.Inject
import kotlin.math.ceil

data class GoalTransaction(
    val id: String,
    val type: String,
    val amount: Double,
    val date: String,
    val accountId: String,
    val description: String,
    val createdAt: Long
)

data class GoalDetailMetrics(
    val percentageComplete: Float = 0f,
    val daysRemaining: Int = 0,
    val daysElapsed: Int = 0,
    val totalDays: Int = 0,
    val onTrack: Boolean = true,
    val monthlyRequired: Double = 0.0,
    val totalDeposits: Double = 0.0,
    val totalWithdrawals: Double = 0.0,
    val netSavings: Double = 0.0
)

data class GoalDetailUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val goal: GoalItem? = null,
    val transactions: List<GoalTransaction> = emptyList(),
    val accounts: List<GoalAccount> = emptyList(),
    val showDeleteModal: Boolean = false,
    val metrics: GoalDetailMetrics = GoalDetailMetrics()
)

sealed class GoalDetailEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : GoalDetailEvent()
    data class NavigateBack(val message: String? = null) : GoalDetailEvent()
    object TriggerExport : GoalDetailEvent()
}

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val goalId: String = checkNotNull(savedStateHandle["goalId"])
    private val _uiState = MutableStateFlow(GoalDetailUiState())
    val uiState: StateFlow<GoalDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GoalDetailEvent>()
    val events = _events.asSharedFlow()

    private var rawTransactions = listOf<GoalTransaction>()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        // Fetch Accounts[cite: 5]
        db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val accs = snap.documents.map {
                        GoalAccount(it.id, it.getString("name") ?: "", it.getDouble("balance") ?: 0.0)
                    }
                    _uiState.update { it.copy(accounts = accs) }
                }
            }

        // Fetch Specific Goal[cite: 5]
        db.collection("users").document(uid).collection("financialGoals").document(goalId)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val d = snap
                    val goal = GoalItem(
                        id = d.id,
                        goalName = d.getString("goalName") ?: "",
                        category = d.getString("category") ?: "other",
                        targetAmount = d.getDouble("targetAmount") ?: 0.0,
                        currentAmount = d.getDouble("currentAmount") ?: 0.0,
                        startDate = d.getString("startDate") ?: "",
                        targetDate = d.getString("targetDate") ?: "",
                        monthlyContribution = d.getDouble("monthlyContribution") ?: 0.0,
                        linkedAccountId = d.getString("linkedAccountId") ?: "",
                        priority = d.getString("priority") ?: "medium",
                        description = d.getString("description") ?: "",
                        status = d.getString("status") ?: "active",
                        enableNotifications = d.getBoolean("enableNotifications") ?: true,
                        totalDeposited = d.getDouble("totalDeposited") ?: 0.0,
                        totalWithdrawn = d.getDouble("totalWithdrawn") ?: 0.0,
                        createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                    _uiState.update { it.copy(goal = goal) }
                    recalculateMetrics()
                } else {
                    emitEvent(GoalDetailEvent.ShowToast("Goal not found", true))
                    emitEvent(GoalDetailEvent.NavigateBack())
                }
            }

        // Fetch Goal Transactions[cite: 5]
        db.collection("users").document(uid).collection("goalTransactions")
            .whereEqualTo("goalId", goalId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawTransactions = snap.documents.map { d ->
                        GoalTransaction(
                            id = d.id,
                            type = d.getString("type") ?: "deposit",
                            amount = d.getDouble("amount") ?: 0.0,
                            date = d.getString("date") ?: "",
                            accountId = d.getString("accountId") ?: "",
                            description = d.getString("description") ?: "",
                            createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        )
                    }
                    _uiState.update { it.copy(transactions = rawTransactions) }
                    recalculateMetrics()
                }
            }
    }

    private fun recalculateMetrics() {
        val goal = _uiState.value.goal ?: return
        val currentAmount = goal.currentAmount
        val targetAmount = maxOf(1.0, goal.targetAmount)
        val percentageComplete = minOf((currentAmount / targetAmount) * 100, 100.0).toFloat()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = System.currentTimeMillis()
        val targetDate = runCatching { sdf.parse(goal.targetDate)?.time }.getOrNull() ?: today
        val startDate = runCatching { sdf.parse(goal.startDate)?.time }.getOrNull() ?: goal.createdAt

        val totalDays = ceil((targetDate - startDate) / (1000.0 * 60 * 60 * 24)).toInt()
        val daysElapsed = ceil((today - startDate) / (1000.0 * 60 * 60 * 24)).toInt()
        val daysRemaining = maxOf(0, ceil((targetDate - today) / (1000.0 * 60 * 60 * 24)).toInt())

        val expectedProgress = if (totalDays > 0) (daysElapsed.toDouble() / totalDays) * 100 else 0.0
        val onTrack = percentageComplete >= expectedProgress

        val monthlyRequired = if (daysRemaining > 0) (targetAmount - currentAmount) / (daysRemaining / 30f) else 0.0

        val totalDeposits = rawTransactions.filter { it.type == "deposit" }.sumOf { it.amount }
        val totalWithdrawals = rawTransactions.filter { it.type == "withdrawal" }.sumOf { it.amount }
        val netSavings = totalDeposits - totalWithdrawals

        val metrics = GoalDetailMetrics(
            percentageComplete = percentageComplete,
            daysRemaining = daysRemaining,
            daysElapsed = daysElapsed,
            totalDays = totalDays,
            onTrack = onTrack,
            monthlyRequired = monthlyRequired,
            totalDeposits = totalDeposits,
            totalWithdrawals = totalWithdrawals,
            netSavings = netSavings
        )

        _uiState.update { it.copy(isLoading = false, metrics = metrics) }
    }

    fun getAccountName(id: String): String {
        return _uiState.value.accounts.find { it.id == id }?.name ?: "Unknown"
    }

    fun openDeleteModal() = _uiState.update { it.copy(showDeleteModal = true) }
    fun closeDeleteModal() = _uiState.update { it.copy(showDeleteModal = false) }

    fun triggerExport() = emitEvent(GoalDetailEvent.TriggerExport)

    fun deleteGoalOfflineFriendly() {
        val uid = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // To safely delete offline, query from cache, then batch delete[cite: 5]
                db.collection("users").document(uid).collection("goalTransactions")
                    .whereEqualTo("goalId", goalId).get().addOnSuccessListener { snap ->
                        val batch = db.batch()
                        snap.documents.forEach { batch.delete(it.reference) }
                        
                        val goalRef = db.collection("users").document(uid).collection("financialGoals").document(goalId)
                        batch.delete(goalRef)
                        
                        batch.commit() // Instant offline commit
                        
                        emitEvent(GoalDetailEvent.NavigateBack("Goal deleted successfully"))
                    }.addOnFailureListener {
                        emitEvent(GoalDetailEvent.ShowToast("Error deleting goal", true))
                        _uiState.update { it.copy(isSaving = false) }
                    }
            } catch (e: Exception) {
                emitEvent(GoalDetailEvent.ShowToast("Error deleting goal: ${e.message}", true))
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun emitEvent(event: GoalDetailEvent) = viewModelScope.launch { _events.emit(event) }
}