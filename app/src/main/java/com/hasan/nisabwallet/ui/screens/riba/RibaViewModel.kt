package com.hasan.nisabwallet.ui.screens.riba

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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

data class RibaTransaction(
    val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val categoryId: String = "",
    val accountId: String = "",
    val isRiba: Boolean = false,
    val sadaqahDone: Boolean = false,
    val sadaqahAmount: Double = 0.0,
    val sadaqahDate: String = "",
    val createdAtMillis: Long = 0L
)

data class RibaAccount(val id: String, val name: String, val balance: Double)
data class RibaCategory(val id: String, val name: String)

data class SadaqahForm(
    val amount: String = "",
    val accountId: String = "",
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val note: String = ""
)

data class RibaUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    
    val ribaTransactions: List<RibaTransaction> = emptyList(),
    val accounts: List<RibaAccount> = emptyList(),
    val categories: List<RibaCategory> = emptyList(),
    
    val totalRiba: Double = 0.0,
    val totalPurified: Double = 0.0,
    val totalUnpurified: Double = 0.0,
    val unpurifiedCount: Int = 0,
    
    val showSadaqahModal: Boolean = false,
    val selectedTransaction: RibaTransaction? = null,
    val sadaqahForm: SadaqahForm = SadaqahForm()
)

sealed class RibaEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : RibaEvent()
}

@HiltViewModel
class RibaViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(RibaUiState())
    val uiState: StateFlow<RibaUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RibaEvent>()
    val events = _events.asSharedFlow()

    private var accListener: ListenerRegistration? = null
    private var catListener: ListenerRegistration? = null
    private var txListener: ListenerRegistration? = null

    private var rawAccounts = emptyList<RibaAccount>()
    private var rawCategories = emptyList<RibaCategory>()
    private var rawTransactions = emptyList<RibaTransaction>()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        // 1. Listen to Accounts
        accListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawAccounts = snap.documents.map { 
                        RibaAccount(it.id, it.getString("name") ?: "", it.getDouble("balance") ?: 0.0) 
                    }
                    combineAndEmit()
                }
            }

        // 2. Listen to Categories
        catListener = db.collection("users").document(uid).collection("categories")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawCategories = snap.documents.map { 
                        RibaCategory(it.id, it.getString("name") ?: "") 
                    }
                    combineAndEmit()
                }
            }

        // 3. Listen to Transactions
        txListener = db.collection("users").document(uid).collection("transactions")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawTransactions = snap.documents.map { d ->
                        RibaTransaction(
                            id = d.id,
                            description = d.getString("description") ?: "",
                            amount = d.getDouble("amount") ?: 0.0,
                            date = d.getString("date") ?: "",
                            categoryId = d.getString("categoryId") ?: "",
                            accountId = d.getString("accountId") ?: "",
                            isRiba = d.getBoolean("isRiba") ?: false,
                            sadaqahDone = d.getBoolean("sadaqahDone") ?: false,
                            sadaqahAmount = d.getDouble("sadaqahAmount") ?: 0.0,
                            sadaqahDate = d.getString("sadaqahDate") ?: "",
                            createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        )
                    }
                    combineAndEmit()
                }
            }
    }

    private fun combineAndEmit() {
        val ribaCatIds = rawCategories
            .filter { it.name.contains("riba", ignoreCase = true) || it.name.contains("interest", ignoreCase = true) }
            .map { it.id }
            .toSet()

        // Filter transactions that are explicitly flagged OR belong to a Riba category
        val filteredRibaTxs = rawTransactions.filter { tx ->
            tx.isRiba || tx.categoryId in ribaCatIds
        }.sortedByDescending { it.date }

        val tRiba = filteredRibaTxs.sumOf { it.amount }
        val tPurified = filteredRibaTxs.filter { it.sadaqahDone }.sumOf { if (it.sadaqahAmount > 0) it.sadaqahAmount else it.amount }
        val unpurifiedList = filteredRibaTxs.filter { !it.sadaqahDone }
        val tUnpurified = unpurifiedList.sumOf { it.amount }

        _uiState.update { 
            it.copy(
                isLoading = false,
                ribaTransactions = filteredRibaTxs,
                accounts = rawAccounts,
                categories = rawCategories,
                totalRiba = tRiba,
                totalPurified = tPurified,
                totalUnpurified = tUnpurified,
                unpurifiedCount = unpurifiedList.size
            ) 
        }
    }

    // ─── Modal Actions ───
    fun openSadaqahModal(tx: RibaTransaction) {
        val defaultAcc = _uiState.value.accounts.firstOrNull()?.id ?: ""
        _uiState.update { 
            it.copy(
                showSadaqahModal = true, 
                selectedTransaction = tx,
                sadaqahForm = SadaqahForm(amount = tx.amount.toString(), accountId = defaultAcc)
            ) 
        }
    }

    fun closeSadaqahModal() = _uiState.update { it.copy(showSadaqahModal = false, selectedTransaction = null) }
    fun updateSadaqahForm(update: (SadaqahForm) -> SadaqahForm) = _uiState.update { it.copy(sadaqahForm = update(it.sadaqahForm)) }

    // ─── Database Actions ───
    fun recordSadaqah() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val tx = state.selectedTransaction ?: return
        val form = state.sadaqahForm

        val amount = form.amount.toDoubleOrNull() ?: 0.0
        if (amount <= 0.0 || form.accountId.isBlank()) {
            emitEvent(RibaEvent.ShowToast("Please enter a valid amount and select an account", true))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val batch = db.batch()

                // 1. Deduct from Account
                val accRef = db.collection("users").document(uid).collection("accounts").document(form.accountId)
                val currentBalance = state.accounts.find { it.id == form.accountId }?.balance ?: 0.0
                batch.update(accRef, "balance", currentBalance - amount)

                // 2. Mark Riba Transaction as Purified
                val ribaRef = db.collection("users").document(uid).collection("transactions").document(tx.id)
                batch.update(ribaRef, mapOf(
                    "sadaqahDone" to true,
                    "sadaqahAmount" to amount,
                    "sadaqahDate" to form.date,
                    "sadaqahAccountId" to form.accountId,
                    "sadaqahNote" to form.note,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))

                // 3. Create Expense Transaction for the Sadaqah
                val newTxRef = db.collection("users").document(uid).collection("transactions").document()
                val newTxData = mapOf(
                    "type" to "Expense",
                    "amount" to amount,
                    "accountId" to form.accountId,
                    "categoryId" to "sadaqah_system_id", // Replace with your actual Sadaqah category ID if needed
                    "description" to (if (form.note.isNotBlank()) form.note else "Riba Purification (Sadaqah)"),
                    "date" to form.date,
                    "isSadaqah" to true,
                    "relatedRibaTxId" to tx.id,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                batch.set(newTxRef, newTxData)

                // Commit batch offline
                batch.commit()

                emitEvent(RibaEvent.ShowToast("Sadaqah recorded successfully"))
                closeSadaqahModal()
            } catch (e: Exception) {
                emitEvent(RibaEvent.ShowToast("Failed to record Sadaqah: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        accListener?.remove()
        catListener?.remove()
        txListener?.remove()
    }

    private fun emitEvent(event: RibaEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}