package com.hasan.nisabwallet.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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
import kotlin.math.ceil

// ─── Data Models ───
data class GoalAccount(
    val id: String,
    val name: String,
    val balance: Double,
    val allocated: Double = 0.0,
    val available: Double = 0.0,
    val activeGoals: List<GoalItem> = emptyList()
)

data class GoalItem(
    val id: String = "",
    val goalName: String = "",
    val category: String = "other",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val startDate: String = "",
    val targetDate: String = "",
    val monthlyContribution: Double = 0.0,
    val linkedAccountId: String = "",
    val priority: String = "medium",
    val description: String = "",
    val status: String = "active",
    val enableNotifications: Boolean = true,
    val totalDeposited: Double = 0.0,
    val totalWithdrawn: Double = 0.0,
    val createdAt: Long = 0L
)

data class GoalForm(
    val goalName: String = "",
    val category: String = "other",
    val targetAmount: String = "",
    val currentAmount: String = "0",
    val targetDate: String = "",
    val monthlyContribution: String = "",
    val linkedAccountId: String = "",
    val priority: String = "medium",
    val description: String = "",
    val enableNotifications: Boolean = true
)

data class GoalTransactionForm(
    val amount: String = "",
    val accountId: String = "",
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val description: String = ""
)

data class GoalMetrics(
    val percentageComplete: Float,
    val daysRemaining: Int,
    val monthsRemaining: Float,
    val onTrack: Boolean,
    val monthlyRequired: Double
)

data class GoalsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val filterStatus: String = "all",
    
    val goals: List<GoalItem> = emptyList(),
    val accountsWithAllocations: List<GoalAccount> = emptyList(),
    
    val totalAllocated: Double = 0.0,
    val totalTarget: Double = 0.0,
    val activeCount: Int = 0,
    val completedCount: Int = 0,

    val showGoalModal: Boolean = false,
    val showDepositModal: Boolean = false,
    val showWithdrawModal: Boolean = false,
    val showDeleteModal: Boolean = false,

    val editingGoal: GoalItem? = null,
    val selectedGoal: GoalItem? = null,
    
    val goalForm: GoalForm = GoalForm(),
    val txForm: GoalTransactionForm = GoalTransactionForm()
)

sealed class GoalsEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : GoalsEvent()
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GoalsEvent>()
    val events = _events.asSharedFlow()

    private var rawAccounts = listOf<GoalAccount>()
    private var rawGoals = listOf<GoalItem>()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        // Fetch Accounts
        db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawAccounts = snap.documents.map {
                        GoalAccount(it.id, it.getString("name") ?: "", it.getDouble("balance") ?: 0.0)
                    }
                    recalculateState()
                }
            }

        // Fetch Goals
        db.collection("users").document(uid).collection("financialGoals")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawGoals = snap.documents.map { d ->
                        GoalItem(
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
                    }
                    recalculateState()
                }
            }
    }

    private fun recalculateState() {
        // Compute allocations[cite: 3, 4]
        val accountsWithAllocations = rawAccounts.map { acc ->
            val accGoals = rawGoals.filter { it.linkedAccountId == acc.id && it.status == "active" }
            val allocated = accGoals.sumOf { it.currentAmount }
            val available = maxOf(0.0, acc.balance - allocated)
            acc.copy(allocated = allocated, available = available, activeGoals = accGoals)
        }

        val activeGoals = rawGoals.filter { it.status == "active" }
        val totalAllocated = activeGoals.sumOf { it.currentAmount }
        val totalTarget = rawGoals.sumOf { it.targetAmount }

        _uiState.update { 
            it.copy(
                isLoading = false,
                goals = rawGoals,
                accountsWithAllocations = accountsWithAllocations,
                totalAllocated = totalAllocated,
                totalTarget = totalTarget,
                activeCount = activeGoals.size,
                completedCount = rawGoals.count { g -> g.status == "completed" }
            )
        }
    }

    // ─── Metrics Calculation[cite: 3] ───
    fun calculateMetrics(goal: GoalItem): GoalMetrics {
        val current = goal.currentAmount
        val target = maxOf(1.0, goal.targetAmount)
        val percentage = minOf((current / target) * 100, 100.0).toFloat()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = System.currentTimeMillis()
        val targetDate = runCatching { sdf.parse(goal.targetDate)?.time }.getOrNull() ?: today
        val startDate = runCatching { sdf.parse(goal.startDate)?.time }.getOrNull() ?: goal.createdAt

        val daysRemaining = maxOf(0, ceil((targetDate - today) / (1000.0 * 60 * 60 * 24)).toInt())
        val monthsRemaining = maxOf(1f, daysRemaining / 30f)

        val totalDays = ceil((targetDate - startDate) / (1000.0 * 60 * 60 * 24)).toInt()
        val daysElapsed = ceil((today - startDate) / (1000.0 * 60 * 60 * 24)).toInt()

        val expectedProgress = if (totalDays > 0) (daysElapsed.toDouble() / totalDays) * 100 else 0.0
        val onTrack = percentage >= expectedProgress

        val remainingAmount = target - current
        val monthlyRequired = if (daysRemaining > 0) remainingAmount / monthsRemaining else 0.0

        return GoalMetrics(percentage, daysRemaining, monthsRemaining, onTrack, monthlyRequired)
    }

    // ─── Actions & Modals ───
    fun setFilter(status: String) = _uiState.update { it.copy(filterStatus = status) }
    
    fun openGoalModal(goal: GoalItem? = null) {
        val form = if (goal != null) {
            GoalForm(
                goal.goalName, goal.category, goal.targetAmount.toString(), goal.currentAmount.toString(),
                goal.targetDate, goal.monthlyContribution.toString(), goal.linkedAccountId, goal.priority,
                goal.description, goal.enableNotifications
            )
        } else {
            GoalForm(linkedAccountId = _uiState.value.accountsWithAllocations.firstOrNull()?.id ?: "")
        }
        _uiState.update { it.copy(showGoalModal = true, editingGoal = goal, goalForm = form) }
    }
    fun closeGoalModal() = _uiState.update { it.copy(showGoalModal = false, editingGoal = null) }
    fun updateGoalForm(form: GoalForm) = _uiState.update { it.copy(goalForm = form) }

    fun openDepositModal(goal: GoalItem) {
        val txForm = GoalTransactionForm(
            amount = if (goal.monthlyContribution > 0) goal.monthlyContribution.toString() else "",
            accountId = goal.linkedAccountId.ifBlank { _uiState.value.accountsWithAllocations.firstOrNull()?.id ?: "" }
        )
        _uiState.update { it.copy(showDepositModal = true, selectedGoal = goal, txForm = txForm) }
    }
    fun closeDepositModal() = _uiState.update { it.copy(showDepositModal = false, selectedGoal = null) }

    fun openWithdrawModal(goal: GoalItem) {
        val txForm = GoalTransactionForm(accountId = goal.linkedAccountId.ifBlank { _uiState.value.accountsWithAllocations.firstOrNull()?.id ?: "" })
        _uiState.update { it.copy(showWithdrawModal = true, selectedGoal = goal, txForm = txForm) }
    }
    fun closeWithdrawModal() = _uiState.update { it.copy(showWithdrawModal = false, selectedGoal = null) }

    fun openDeleteModal(goal: GoalItem) = _uiState.update { it.copy(showDeleteModal = true, selectedGoal = goal) }
    fun closeDeleteModal() = _uiState.update { it.copy(showDeleteModal = false, selectedGoal = null) }
    fun updateTxForm(form: GoalTransactionForm) = _uiState.update { it.copy(txForm = form) }

    // ─── Offline-First Data Persistence[cite: 3, 4] ───

    fun saveGoal() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val form = state.goalForm
        val editing = state.editingGoal

        val targetAmount = form.targetAmount.toDoubleOrNull() ?: 0.0
        val currentAmount = form.currentAmount.toDoubleOrNull() ?: 0.0

        if (form.goalName.isBlank() || targetAmount <= 0 || form.targetDate.isBlank()) {
            emitEvent(GoalsEvent.ShowToast("Please fill in required fields", true))
            return
        }

        // Validate available balance for new goals[cite: 3, 4]
        if (editing == null && currentAmount > 0) {
            val account = state.accountsWithAllocations.find { it.id == form.linkedAccountId }
            if (account != null && currentAmount > account.available) {
                emitEvent(GoalsEvent.ShowToast("Insufficient available balance. Account has ৳${account.balance} total but only ৳${account.available} is available.", true))
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val batch = db.batch()
                val goalRef = if (editing != null) {
                    db.collection("users").document(uid).collection("financialGoals").document(editing.id)
                } else {
                    db.collection("users").document(uid).collection("financialGoals").document()
                }

                val goalData = mutableMapOf<String, Any>(
                    "goalName" to form.goalName,
                    "category" to form.category,
                    "targetAmount" to targetAmount,
                    "targetDate" to form.targetDate,
                    "monthlyContribution" to (form.monthlyContribution.toDoubleOrNull() ?: 0.0),
                    "linkedAccountId" to form.linkedAccountId,
                    "priority" to form.priority,
                    "description" to form.description,
                    "enableNotifications" to form.enableNotifications,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                if (editing == null) {
                    goalData["currentAmount"] = currentAmount
                    goalData["startDate"] = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    goalData["status"] = "active"
                    goalData["totalDeposited"] = currentAmount
                    goalData["totalWithdrawn"] = 0.0
                    goalData["createdAt"] = FieldValue.serverTimestamp()
                    
                    batch.set(goalRef, goalData)

                    // Add initial transaction if needed[cite: 3]
                    if (currentAmount > 0) {
                        val txRef = db.collection("users").document(uid).collection("goalTransactions").document()
                        batch.set(txRef, mapOf(
                            "goalId" to goalRef.id,
                            "type" to "deposit",
                            "amount" to currentAmount,
                            "date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            "accountId" to form.linkedAccountId,
                            "description" to "Initial allocation",
                            "createdAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    emitEvent(GoalsEvent.ShowToast("Goal created successfully! 🎯"))
                } else {
                    batch.update(goalRef, goalData)
                    emitEvent(GoalsEvent.ShowToast("Goal updated successfully"))
                }

                batch.commit() // Instant offline commit
                closeGoalModal()
            } catch (e: Exception) {
                emitEvent(GoalsEvent.ShowToast("Error saving goal", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun submitDeposit() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val goal = state.selectedGoal ?: return
        val form = state.txForm

        val amount = form.amount.toDoubleOrNull() ?: 0.0
        if (amount <= 0) {
            emitEvent(GoalsEvent.ShowToast("Please enter a valid amount", true))
            return
        }

        // Validate available balance[cite: 3, 4]
        val account = state.accountsWithAllocations.find { it.id == form.accountId }
        if (account != null && amount > account.available) {
            emitEvent(GoalsEvent.ShowToast("Insufficient available balance. Total: ৳${account.balance}, Available: ৳${account.available}.", true))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val batch = db.batch()
                val goalRef = db.collection("users").document(uid).collection("financialGoals").document(goal.id)
                
                val newCurrentAmount = goal.currentAmount + amount
                val newStatus = if (newCurrentAmount >= goal.targetAmount) "completed" else "active"
                
                batch.update(goalRef, mapOf(
                    "currentAmount" to newCurrentAmount,
                    "totalDeposited" to goal.totalDeposited + amount,
                    "status" to newStatus,
                    "lastContributionDate" to form.date,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))

                val txRef = db.collection("users").document(uid).collection("goalTransactions").document()
                batch.set(txRef, mapOf(
                    "goalId" to goal.id,
                    "type" to "deposit",
                    "amount" to amount,
                    "date" to form.date,
                    "accountId" to form.accountId,
                    "description" to form.description.ifBlank { "Deposit" },
                    "createdAt" to FieldValue.serverTimestamp()
                ))

                batch.commit() // Instant offline commit
                
                if (newStatus == "completed") emitEvent(GoalsEvent.ShowToast("🎉 Congratulations! Goal completed!"))
                else emitEvent(GoalsEvent.ShowToast("Allocation increased successfully"))
                
                closeDepositModal()
            } catch (e: Exception) {
                emitEvent(GoalsEvent.ShowToast("Error processing deposit", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun submitWithdraw() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val goal = state.selectedGoal ?: return
        val form = state.txForm

        val amount = form.amount.toDoubleOrNull() ?: 0.0
        if (amount <= 0 || amount > goal.currentAmount) {
            emitEvent(GoalsEvent.ShowToast("Amount exceeds allocated balance or is invalid", true))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val batch = db.batch()
                val goalRef = db.collection("users").document(uid).collection("financialGoals").document(goal.id)
                
                batch.update(goalRef, mapOf(
                    "currentAmount" to goal.currentAmount - amount,
                    "totalWithdrawn" to goal.totalWithdrawn + amount,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))

                val txRef = db.collection("users").document(uid).collection("goalTransactions").document()
                batch.set(txRef, mapOf(
                    "goalId" to goal.id,
                    "type" to "withdrawal",
                    "amount" to amount,
                    "date" to form.date,
                    "accountId" to form.accountId,
                    "description" to form.description.ifBlank { "Withdrawal" },
                    "createdAt" to FieldValue.serverTimestamp()
                ))

                batch.commit() // Instant offline commit
                emitEvent(GoalsEvent.ShowToast("Allocation reduced successfully"))
                closeWithdrawModal()
            } catch (e: Exception) {
                emitEvent(GoalsEvent.ShowToast("Error processing withdrawal", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteGoal() {
        val uid = auth.currentUser?.uid ?: return
        val goal = _uiState.value.selectedGoal ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // To delete safely offline, we query from cache, then batch delete.
                db.collection("users").document(uid).collection("goalTransactions")
                    .whereEqualTo("goalId", goal.id).get().addOnSuccessListener { snap ->
                        val batch = db.batch()
                        snap.documents.forEach { batch.delete(it.reference) }
                        
                        val goalRef = db.collection("users").document(uid).collection("financialGoals").document(goal.id)
                        batch.delete(goalRef)
                        
                        batch.commit() // Instant offline commit
                        
                        emitEvent(GoalsEvent.ShowToast("Goal deleted. ৳${goal.currentAmount} is now available in your account"))
                        closeDeleteModal()
                        _uiState.update { it.copy(isSaving = false) }
                    }.addOnFailureListener {
                        emitEvent(GoalsEvent.ShowToast("Error deleting goal", true))
                        _uiState.update { it.copy(isSaving = false) }
                    }
            } catch (e: Exception) {
                emitEvent(GoalsEvent.ShowToast("Error deleting goal", true))
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun emitEvent(event: GoalsEvent) = viewModelScope.launch { _events.emit(event) }
}