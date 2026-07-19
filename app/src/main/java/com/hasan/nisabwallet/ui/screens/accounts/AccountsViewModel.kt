package com.hasan.nisabwallet.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
import java.util.UUID
import javax.inject.Inject

// ─── Data Models ───
data class AccountItem(
    val id: String = "",
    val name: String = "",
    val type: String = "Cash",
    val balance: Double = 0.0,
    val isDefault: Boolean = false
)

data class FinancialGoal(
    val id: String = "",
    val goalName: String = "",
    val currentAmount: Double = 0.0,
    val linkedAccountId: String = "",
    val status: String = "active"
)

data class AccountWithAllocations(
    val account: AccountItem,
    val allocated: Double,
    val available: Double,
    val allocatedGoals: List<FinancialGoal>
)

data class AccountForm(
    val id: String? = null,
    val name: String = "",
    val type: String = "Cash",
    val balance: String = ""
)

data class AccountsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isLoadingDefaults: Boolean = false,
    val syncStatus: String = "Connecting...",
    val accounts: List<AccountWithAllocations> = emptyList(),
    val totalBalance: Double = 0.0,
    
    val showAddEditModal: Boolean = false,
    val form: AccountForm = AccountForm()
)

sealed class AccountsEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : AccountsEvent()
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AccountsEvent>()
    val events = _events.asSharedFlow()

    private var accListener: ListenerRegistration? = null
    private var goalsListener: ListenerRegistration? = null

    private var rawAccounts = emptyList<AccountItem>()
    private var rawGoals = emptyList<FinancialGoal>()

    private val defaultAccounts = listOf(
        AccountItem(name = "Cash", type = "Cash", balance = 0.0, isDefault = true),
        AccountItem(name = "Bank 1", type = "Bank", balance = 0.0, isDefault = true),
        AccountItem(name = "bKash", type = "Mobile Banking", balance = 0.0, isDefault = true),
        AccountItem(name = "Bank 2", type = "Bank", balance = 0.0, isDefault = true)
    )

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        accListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    emitEvent(AccountsEvent.ShowToast("Sync error: ${e.message}", true))
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val status = when {
                        snap.metadata.hasPendingWrites() -> "Syncing..."
                        snap.metadata.isFromCache() -> "Offline (Cached)"
                        else -> "Synced"
                    }
                    rawAccounts = snap.documents.map { d ->
                        AccountItem(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            type = d.getString("type") ?: "Cash",
                            balance = d.getDouble("balance") ?: 0.0,
                            isDefault = d.getBoolean("isDefault") ?: false
                        )
                    }.sortedBy { it.name }
                    combineAndEmit(status)
                }
            }

        goalsListener = db.collection("users").document(uid).collection("financialGoals")
            .whereEqualTo("status", "active")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawGoals = snap.documents.map { d ->
                        FinancialGoal(
                            id = d.id,
                            goalName = d.getString("goalName") ?: "",
                            currentAmount = d.getDouble("currentAmount") ?: 0.0,
                            linkedAccountId = d.getString("linkedAccountId") ?: "",
                            status = d.getString("status") ?: "active"
                        )
                    }
                    combineAndEmit(null)
                }
            }
    }

    private fun combineAndEmit(newSyncStatus: String?) {
        var totalBal = 0.0
        val processedAccounts = rawAccounts.map { acc ->
            totalBal += acc.balance
            val linkedGoals = rawGoals.filter { it.linkedAccountId == acc.id }
            val allocatedSum = linkedGoals.sumOf { it.currentAmount }
            val availableBal = acc.balance - allocatedSum
            
            AccountWithAllocations(
                account = acc,
                allocated = allocatedSum,
                available = availableBal,
                allocatedGoals = linkedGoals
            )
        }

        _uiState.update { 
            it.copy(
                isLoading = false,
                accounts = processedAccounts,
                totalBalance = totalBal,
                syncStatus = newSyncStatus ?: it.syncStatus
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        accListener?.remove()
        goalsListener?.remove()
    }

    // ─── Intent Actions ───

    fun loadDefaultAccounts() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDefaults = true) }
            try {
                val existingNames = rawAccounts.map { it.name.trim().lowercase() }
                val toAdd = defaultAccounts.filter { !existingNames.contains(it.name.trim().lowercase()) }

                if (toAdd.isEmpty()) {
                    emitEvent(AccountsEvent.ShowToast("All default accounts already exist"))
                    return@launch
                }

                val batch = db.batch()
                val accountsRef = db.collection("users").document(uid).collection("accounts")

                toAdd.forEach { acc ->
                    val docRef = accountsRef.document()
                    val data = hashMapOf(
                        "accountId" to UUID.randomUUID().toString(),
                        "name" to acc.name,
                        "type" to acc.type,
                        "balance" to acc.balance,
                        "isDefault" to acc.isDefault,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(docRef, data)
                }

                batch.commit().await()
                emitEvent(AccountsEvent.ShowToast("Added ${toAdd.size} default account(s)"))
            } catch (e: Exception) {
                emitEvent(AccountsEvent.ShowToast("Failed to load defaults: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isLoadingDefaults = false) }
            }
        }
    }

    fun openAddModal() {
        _uiState.update { it.copy(showAddEditModal = true, form = AccountForm()) }
    }

    fun openEditModal(account: AccountItem) {
        _uiState.update { 
            it.copy(
                showAddEditModal = true, 
                form = AccountForm(
                    id = account.id,
                    name = account.name,
                    type = account.type,
                    balance = account.balance.toString()
                )
            )
        }
    }

    fun closeModal() {
        _uiState.update { it.copy(showAddEditModal = false, form = AccountForm()) }
    }

    fun updateForm(update: (AccountForm) -> AccountForm) {
        _uiState.update { it.copy(form = update(it.form)) }
    }

    fun saveAccount() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.form
        
        val trimmedName = form.name.trim()
        if (trimmedName.isBlank() || form.balance.isBlank()) {
            emitEvent(AccountsEvent.ShowToast("Please fill in all fields", true))
            return
        }

        val balanceVal = form.balance.toDoubleOrNull()
        if (balanceVal == null) {
            emitEvent(AccountsEvent.ShowToast("Invalid balance amount", true))
            return
        }

        // Duplicate Check
        val isDuplicate = rawAccounts.any { 
            it.id != form.id && it.name.trim().lowercase() == trimmedName.lowercase() 
        }
        if (isDuplicate) {
            emitEvent(AccountsEvent.ShowToast("An account with this name already exists", true))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val accountsRef = db.collection("users").document(uid).collection("accounts")
                val data = hashMapOf(
                    "name" to trimmedName,
                    "type" to form.type,
                    "balance" to balanceVal,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                if (form.id != null) {
                    accountsRef.document(form.id).set(data, SetOptions.merge()).await()
                    emitEvent(AccountsEvent.ShowToast("Account updated successfully"))
                } else {
                    data["accountId"] = UUID.randomUUID().toString()
                    data["isDefault"] = false
                    data["createdAt"] = FieldValue.serverTimestamp()
                    accountsRef.add(data).await()
                    emitEvent(AccountsEvent.ShowToast("Account added successfully"))
                }
                closeModal()
            } catch (e: Exception) {
                emitEvent(AccountsEvent.ShowToast("Save failed: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteAccount(account: AccountItem) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("accounts").document(account.id).delete().await()
                emitEvent(AccountsEvent.ShowToast("Account deleted successfully"))
            } catch (e: Exception) {
                emitEvent(AccountsEvent.ShowToast("Error deleting account: ${e.message}", true))
            }
        }
    }

    private fun emitEvent(event: AccountsEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}