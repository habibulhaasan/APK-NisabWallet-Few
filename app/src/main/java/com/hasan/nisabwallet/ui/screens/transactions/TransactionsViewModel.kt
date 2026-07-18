package com.hasan.nisabwallet.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
import java.util.*
import javax.inject.Inject

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val syncStatus: String = "Connecting...", 
    
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val accounts: List<AccountItem> = emptyList(),
    val categories: List<CategoryItem> = emptyList(),

    val filterType: String = "All",
    val filterAccountId: String = "all",
    val filterCategoryId: String = "all",
    val filterStartDate: String = "",
    val filterEndDate: String = "",
    val searchQuery: String = "",

    val summaryIncome: Double = 0.0,
    val summaryExpense: Double = 0.0,

    val showAddEditSheet: Boolean = false,
    val defaultAddType: String = "Expense",
    val editingTransaction: Transaction? = null,
    val showFilterSheet: Boolean = false,
    val showDetailPopup: Boolean = false,
    val detailTransaction: Transaction? = null,
    val showDeleteConfirm: Boolean = false,
    val deletingTransaction: Transaction? = null,
)

data class Transaction(
    val id: String = "",
    val type: String = "",
    val amount: Double = 0.0,
    val accountId: String = "",
    val categoryId: String = "",
    val description: String = "",
    val date: String = "",
    val isCharge: Boolean = false,
    val isRiba: Boolean = false,
    val chargeAmount: Double = 0.0,
    val chargeNote: String = "",
    val createdAtMillis: Long = 0L,
    
    val isTransfer: Boolean = false,
    val originalId: String? = null,
    val transferDirection: String? = null,
    val relatedAccountId: String? = null,
    val relatedAccountName: String? = null,
    val originalDescription: String? = null,
)

data class AccountItem(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val balance: Double = 0.0,
)

data class CategoryItem(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val color: String = "#6B7280",
    val isSystem: Boolean = false,
    val isRiba: Boolean = false,
)

data class TransactionForm(
    val type: String = "Expense",
    val amount: String = "",
    val accountId: String = "",
    val toAccountId: String = "",
    val categoryId: String = "",
    val description: String = "",
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val chargeAmount: String = "",
    val chargeNote: String = "",
)

sealed class TransactionEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : TransactionEvent()
}

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TransactionEvent>()
    val events = _events.asSharedFlow()

    private var txListener: ListenerRegistration? = null
    private var trListener: ListenerRegistration? = null
    private var accListener: ListenerRegistration? = null
    private var catListener: ListenerRegistration? = null

    private var rawTransactions = emptyList<Transaction>()
    private var rawTransfers = emptyList<Transaction>()

    init {
        startRealTimeSync()
    }

    fun refresh() {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true) }

        txListener?.remove()
        trListener?.remove()
        accListener?.remove()
        catListener?.remove()

        accListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val accounts = snap.documents.map { d ->
                        AccountItem(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            type = d.getString("type") ?: "",
                            balance = d.getDouble("balance") ?: 0.0,
                        )
                    }
                    _uiState.update { it.copy(accounts = accounts) }
                }
            }

        catListener = db.collection("users").document(uid).collection("categories")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val categories = snap.documents.map { d ->
                        CategoryItem(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            type = d.getString("type") ?: "",
                            color = d.getString("color") ?: "#6B7280",
                            isSystem = d.getBoolean("isSystem") ?: false,
                            isRiba = d.getBoolean("isRiba") ?: false,
                        )
                    }.sortedBy { it.name }
                    _uiState.update { it.copy(categories = categories) }
                }
            }

        txListener = db.collection("users").document(uid).collection("transactions")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    emit(TransactionEvent.ShowToast("Sync error: ${e.message}", true))
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val status = when {
                        snap.metadata.hasPendingWrites() -> "Syncing..."
                        snap.metadata.isFromCache() -> "Offline (Cached)"
                        else -> "Synced"
                    }
                    
                    rawTransactions = snap.documents.map { d ->
                        Transaction(
                            id = d.id,
                            type = d.getString("type") ?: "",
                            amount = d.getDouble("amount") ?: 0.0,
                            accountId = d.getString("accountId") ?: "",
                            categoryId = d.getString("categoryId") ?: "",
                            description = d.getString("description") ?: "",
                            date = d.getString("date") ?: "",
                            isCharge = d.getBoolean("isCharge") ?: false,
                            isRiba = d.getBoolean("isRiba") ?: false,
                            chargeAmount = d.getDouble("chargeAmount") ?: 0.0,
                            chargeNote = d.getString("chargeNote") ?: "",
                            createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                        )
                    }
                    combineAndEmit(status)
                }
            }

        trListener = db.collection("users").document(uid).collection("transfers")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val expandedTransfers = mutableListOf<Transaction>()
                    snap.documents.forEach { t ->
                        val id = t.id
                        val amt = t.getDouble("amount") ?: 0.0
                        val fromId = t.getString("fromAccountId") ?: ""
                        val fromName = t.getString("fromAccountName") ?: ""
                        val toId = t.getString("toAccountId") ?: ""
                        val toName = t.getString("toAccountName") ?: ""
                        val desc = t.getString("description") ?: ""
                        val date = t.getString("date") ?: ""
                        val ts = t.getTimestamp("createdAt")?.toDate()?.time ?: 0L

                        expandedTransfers.add(
                            Transaction(
                                id = "$id-expense", originalId = id, isTransfer = true,
                                type = "Expense", amount = amt, accountId = fromId,
                                description = desc.ifBlank { "Transfer to $toName" },
                                originalDescription = desc, date = date, createdAtMillis = ts,
                                transferDirection = "from", relatedAccountId = toId, relatedAccountName = toName
                            )
                        )
                        expandedTransfers.add(
                            Transaction(
                                id = "$id-income", originalId = id, isTransfer = true,
                                type = "Income", amount = amt, accountId = toId,
                                description = desc.ifBlank { "Transfer from $fromName" },
                                originalDescription = desc, date = date, createdAtMillis = ts,
                                transferDirection = "to", relatedAccountId = fromId, relatedAccountName = fromName
                            )
                        )
                    }
                    rawTransfers = expandedTransfers
                    combineAndEmit(null)
                }
            }
    }

    private fun combineAndEmit(newSyncStatus: String?) {
        val combined = (rawTransactions + rawTransfers).sortedWith(
            compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAtMillis }
        )

        _uiState.update { state ->
            val filtered = applyFilters(combined, state)
            
            var inc = 0.0
            var exp = 0.0
            filtered.forEach { tx ->
                if (!tx.isTransfer) {
                    if (tx.type == "Income") inc += tx.amount
                    if (tx.type == "Expense") exp += tx.amount
                }
            }

            state.copy(
                isLoading = false,
                transactions = combined,
                filteredTransactions = filtered,
                summaryIncome = inc,
                summaryExpense = exp,
                syncStatus = newSyncStatus ?: state.syncStatus
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        txListener?.remove()
        trListener?.remove()
        accListener?.remove()
        catListener?.remove()
    }

    private fun updateFilters(updateBlock: (TransactionsUiState) -> TransactionsUiState) {
        _uiState.update { currentState ->
            val nextState = updateBlock(currentState)
            val filtered = applyFilters(nextState.transactions, nextState)
            
            var inc = 0.0
            var exp = 0.0
            filtered.forEach { tx ->
                if (!tx.isTransfer) {
                    if (tx.type == "Income") inc += tx.amount
                    if (tx.type == "Expense") exp += tx.amount
                }
            }

            nextState.copy(
                filteredTransactions = filtered,
                summaryIncome = inc,
                summaryExpense = exp
            )
        }
    }

    fun setSearchQuery(query: String) = updateFilters { it.copy(searchQuery = query) }
    fun setFilterType(type: String) = updateFilters { it.copy(filterType = type) }
    fun setFilterAccount(accountId: String) = updateFilters { it.copy(filterAccountId = accountId) }
    fun setFilterCategory(categoryId: String) = updateFilters { it.copy(filterCategoryId = categoryId) }
    fun setFilterDateRange(start: String, end: String) = updateFilters { it.copy(filterStartDate = start, filterEndDate = end) }
    fun clearFilters() = updateFilters { 
        it.copy(
            filterType = "All",
            filterAccountId = "all",
            filterCategoryId = "all",
            filterStartDate = "",
            filterEndDate = "",
            searchQuery = ""
        ) 
    }

    private fun applyFilters(transactions: List<Transaction>, state: TransactionsUiState): List<Transaction> {
        return transactions.filter { tx ->
            (state.filterType == "All" || tx.type == state.filterType) &&
            (state.filterAccountId == "all" || tx.accountId == state.filterAccountId) &&
            (state.filterCategoryId == "all" || tx.categoryId == state.filterCategoryId) &&
            (state.filterStartDate.isBlank() || tx.date >= state.filterStartDate) &&
            (state.filterEndDate.isBlank() || tx.date <= state.filterEndDate) &&
            (state.searchQuery.isBlank() || tx.description.contains(state.searchQuery, ignoreCase = true) || getCategoryName(tx.categoryId).contains(state.searchQuery, ignoreCase = true))
        }
    }

    fun showAddSheet(defaultType: String = "Expense") {
        _uiState.update { it.copy(showAddEditSheet = true, editingTransaction = null, defaultAddType = defaultType) }
    }

    fun showEditSheet(transaction: Transaction) {
        _uiState.update { it.copy(showAddEditSheet = true, editingTransaction = transaction) }
    }

    fun hideAddEditSheet() {
        _uiState.update { it.copy(showAddEditSheet = false, editingTransaction = null) }
    }

    fun showFilterSheet() = _uiState.update { it.copy(showFilterSheet = true) }
    fun hideFilterSheet() = _uiState.update { it.copy(showFilterSheet = false) }

    fun showDetail(transaction: Transaction) {
        _uiState.update { it.copy(showDetailPopup = true, detailTransaction = transaction) }
    }
    fun hideDetail() = _uiState.update { it.copy(showDetailPopup = false, detailTransaction = null) }

    fun showDeleteConfirm(transaction: Transaction) {
        _uiState.update { it.copy(showDeleteConfirm = true, deletingTransaction = transaction) }
    }
    fun hideDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = false, deletingTransaction = null) }

    fun addTransaction(form: TransactionForm) {
        val uid = auth.currentUser?.uid ?: return
        
        if (form.type == "Transfer") {
            if (form.amount.isBlank() || form.accountId.isBlank() || form.toAccountId.isBlank()) {
                emit(TransactionEvent.ShowToast("Fill in all required fields", true))
                return
            }
            if (form.accountId == form.toAccountId) {
                emit(TransactionEvent.ShowToast("Cannot transfer to the same account", true))
                return
            }
        } else {
            if (form.amount.isBlank() || form.accountId.isBlank() || form.categoryId.isBlank()) {
                emit(TransactionEvent.ShowToast("Fill in all required fields", true))
                return
            }
        }
        
        val amount = form.amount.toDoubleOrNull() ?: run {
            emit(TransactionEvent.ShowToast("Invalid amount", true))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                if (form.type == "Transfer") {
                    val fromAcc = _uiState.value.accounts.find { it.id == form.accountId }
                    val toAcc = _uiState.value.accounts.find { it.id == form.toAccountId }
                    if (fromAcc == null || toAcc == null) throw Exception("Invalid accounts")

                    val transferData = hashMapOf(
                        "fromAccountId" to fromAcc.id,
                        "fromAccountName" to fromAcc.name,
                        "toAccountId" to toAcc.id,
                        "toAccountName" to toAcc.name,
                        "amount" to amount,
                        "description" to form.description.trim(),
                        "date" to form.date,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("users").document(uid).collection("transfers").add(transferData).await()

                    db.collection("users").document(uid).collection("accounts").document(fromAcc.id)
                        .update("balance", fromAcc.balance - amount).await()
                    db.collection("users").document(uid).collection("accounts").document(toAcc.id)
                        .update("balance", toAcc.balance + amount).await()

                    val chargeAmt = form.chargeAmount.toDoubleOrNull() ?: 0.0
                    if (chargeAmt > 0) {
                        val feesCategory = getOrCreateFeesCategory(uid)
                        db.collection("users").document(uid).collection("transactions").add(hashMapOf(
                            "type" to "Expense", "amount" to chargeAmt, "accountId" to form.accountId,
                            "categoryId" to feesCategory, "description" to (form.chargeNote.ifBlank { "Transfer charge" }),
                            "date" to form.date, "isCharge" to true, "createdAt" to FieldValue.serverTimestamp()
                        )).await()
                        db.collection("users").document(uid).collection("accounts").document(form.accountId)
                            .update("balance", (fromAcc.balance - amount) - chargeAmt).await()
                    }
                    emit(TransactionEvent.ShowToast("Transfer completed!"))
                } else {
                    val txData = hashMapOf(
                        "type"         to form.type,
                        "amount"       to amount,
                        "accountId"    to form.accountId,
                        "categoryId"   to form.categoryId,
                        "description"  to form.description.trim(),
                        "date"         to form.date,
                        "isCharge"     to false,
                        "isRiba"       to false,
                        "chargeAmount" to (form.chargeAmount.toDoubleOrNull() ?: 0.0),
                        "chargeNote"   to form.chargeNote.trim(),
                        "createdAt"    to FieldValue.serverTimestamp(),
                    )
                    db.collection("users").document(uid).collection("transactions").add(txData).await()

                    val account = _uiState.value.accounts.find { it.id == form.accountId }
                    if (account != null) {
                        val delta = if (form.type == "Income") amount else -amount
                        db.collection("users").document(uid).collection("accounts")
                            .document(form.accountId)
                            .update("balance", account.balance + delta).await()
                    }

                    val chargeAmt = form.chargeAmount.toDoubleOrNull() ?: 0.0
                    if (chargeAmt > 0) {
                        val feesCategory = getOrCreateFeesCategory(uid)
                        db.collection("users").document(uid).collection("transactions").add(hashMapOf(
                            "type" to "Expense", "amount" to chargeAmt, "accountId" to form.accountId,
                            "categoryId" to feesCategory, "description" to (form.chargeNote.ifBlank { "Related charges / fees" }),
                            "date" to form.date, "isCharge" to true, "createdAt" to FieldValue.serverTimestamp()
                        )).await()
                        
                        val acc = _uiState.value.accounts.find { it.id == form.accountId }
                        if (acc != null) {
                            db.collection("users").document(uid).collection("accounts").document(form.accountId)
                                .update("balance", acc.balance - chargeAmt).await()
                        }
                    }
                    emit(TransactionEvent.ShowToast("Transaction added!"))
                }

                hideAddEditSheet()
            } catch (e: Exception) {
                emit(TransactionEvent.ShowToast("Failed to add: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun updateTransaction(transactionId: String, form: TransactionForm) {
        val uid = auth.currentUser?.uid ?: return
        val oldTx = _uiState.value.transactions.find { it.id == transactionId } ?: return
        val newAmount = form.amount.toDoubleOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                if (oldTx.isTransfer) {
                    val origId = oldTx.originalId ?: throw Exception("Missing transfer ID")
                    
                    val oldFromId = if (oldTx.transferDirection == "from") oldTx.accountId else oldTx.relatedAccountId!!
                    val oldToId = if (oldTx.transferDirection == "to") oldTx.accountId else oldTx.relatedAccountId!!
                    val oldAmt = oldTx.amount

                    val oldFromAcc = _uiState.value.accounts.find { it.id == oldFromId }
                    val oldToAcc = _uiState.value.accounts.find { it.id == oldToId }

                    if (oldFromAcc != null) db.collection("users").document(uid).collection("accounts")
                        .document(oldFromId).update("balance", oldFromAcc.balance + oldAmt).await()
                    if (oldToAcc != null) db.collection("users").document(uid).collection("accounts")
                        .document(oldToId).update("balance", oldToAcc.balance - oldAmt).await()

                    val fromAcc = _uiState.value.accounts.find { it.id == form.accountId }
                    val toAcc = _uiState.value.accounts.find { it.id == form.toAccountId }
                    
                    db.collection("users").document(uid).collection("transfers").document(origId).update(
                        mapOf(
                            "amount" to newAmount,
                            "fromAccountId" to form.accountId,
                            "fromAccountName" to (fromAcc?.name ?: ""),
                            "toAccountId" to form.toAccountId,
                            "toAccountName" to (toAcc?.name ?: ""),
                            "description" to form.description.trim(),
                            "date" to form.date,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()

                    val freshFromBal = db.collection("users").document(uid).collection("accounts").document(form.accountId).get().await().getDouble("balance") ?: 0.0
                    db.collection("users").document(uid).collection("accounts").document(form.accountId).update("balance", freshFromBal - newAmount).await()
                    
                    val freshToBal = db.collection("users").document(uid).collection("accounts").document(form.toAccountId).get().await().getDouble("balance") ?: 0.0
                    db.collection("users").document(uid).collection("accounts").document(form.toAccountId).update("balance", freshToBal + newAmount).await()

                    emit(TransactionEvent.ShowToast("Transfer updated!"))
                } else {
                    val oldAccount = _uiState.value.accounts.find { it.id == oldTx.accountId }
                    if (oldAccount != null) {
                        val reversal = if (oldTx.type == "Income") -oldTx.amount else oldTx.amount
                        db.collection("users").document(uid).collection("accounts")
                            .document(oldTx.accountId)
                            .update("balance", oldAccount.balance + reversal).await()
                    }

                    val txData = mapOf(
                        "type"        to form.type,
                        "amount"      to newAmount,
                        "accountId"   to form.accountId,
                        "categoryId"  to form.categoryId,
                        "description" to form.description.trim(),
                        "date"        to form.date,
                        "updatedAt"   to FieldValue.serverTimestamp(),
                    )
                    db.collection("users").document(uid).collection("transactions")
                        .document(transactionId).update(txData).await()

                    val newAccount = _uiState.value.accounts.find { it.id == form.accountId }
                    if (newAccount != null) {
                        val delta = if (form.type == "Income") newAmount else -newAmount
                        val freshBalance = db.collection("users").document(uid).collection("accounts")
                            .document(form.accountId).get().await().getDouble("balance") ?: 0.0
                        db.collection("users").document(uid).collection("accounts")
                            .document(form.accountId)
                            .update("balance", freshBalance + delta).await()
                    }
                    emit(TransactionEvent.ShowToast("Transaction updated!"))
                }
                
                hideAddEditSheet()
            } catch (e: Exception) {
                emit(TransactionEvent.ShowToast("Failed to update: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteTransaction() {
        val uid = auth.currentUser?.uid ?: return
        val tx  = _uiState.value.deletingTransaction ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                if (tx.isTransfer) {
                    val origId = tx.originalId ?: throw Exception("Missing transfer ID")
                    val fromId = if (tx.transferDirection == "from") tx.accountId else tx.relatedAccountId!!
                    val toId = if (tx.transferDirection == "to") tx.accountId else tx.relatedAccountId!!

                    val oldFromAcc = _uiState.value.accounts.find { it.id == fromId }
                    val oldToAcc = _uiState.value.accounts.find { it.id == toId }

                    if (oldFromAcc != null) db.collection("users").document(uid).collection("accounts")
                        .document(fromId).update("balance", oldFromAcc.balance + tx.amount).await()
                    if (oldToAcc != null) db.collection("users").document(uid).collection("accounts")
                        .document(toId).update("balance", oldToAcc.balance - tx.amount).await()

                    db.collection("users").document(uid).collection("transfers").document(origId).delete().await()
                } else {
                    val account = _uiState.value.accounts.find { it.id == tx.accountId }
                    if (account != null) {
                        val reversal = if (tx.type == "Income") -tx.amount else tx.amount
                        db.collection("users").document(uid).collection("accounts")
                            .document(tx.accountId)
                            .update("balance", account.balance + reversal).await()
                    }
                    db.collection("users").document(uid).collection("transactions").document(tx.id).delete().await()
                }

                emit(TransactionEvent.ShowToast("Transaction deleted"))
                hideDeleteConfirm()
            } catch (e: Exception) {
                emit(TransactionEvent.ShowToast("Failed to delete: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun addCategoryInline(name: String, type: String, color: String, onAdded: (CategoryItem) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val isRiba = name.trim().lowercase() in listOf("interest", "riba")
                val data = hashMapOf(
                    "name"       to name.trim(),
                    "type"       to type,
                    "color"      to color,
                    "categoryId" to java.util.UUID.randomUUID().toString(),
                    "isRiba"     to isRiba,
                    "createdAt"  to FieldValue.serverTimestamp(),
                )
                val ref = db.collection("users").document(uid).collection("categories").add(data).await()
                val newCat = CategoryItem(id = ref.id, name = name.trim(), type = type, color = color, isRiba = isRiba)
                emit(TransactionEvent.ShowToast("\"${name.trim()}\" added!"))
                onAdded(newCat)
            } catch (e: Exception) {
                emit(TransactionEvent.ShowToast("Failed to add category: ${e.message}", true))
            }
        }
    }

    private suspend fun getOrCreateFeesCategory(uid: String): String {
        val snap = db.collection("users").document(uid).collection("categories")
            .whereEqualTo("name", "Fees & Charges").get().await()
        if (!snap.isEmpty) return snap.documents.first().id
        val data = hashMapOf(
            "name"       to "Fees & Charges",
            "type"       to "Expense",
            "color"      to "#F97316",
            "categoryId" to java.util.UUID.randomUUID().toString(),
            "isSystem"   to true,
            "createdAt"  to FieldValue.serverTimestamp(),
        )
        return db.collection("users").document(uid).collection("categories").add(data).await().id
    }

    private fun emit(event: TransactionEvent) {
        viewModelScope.launch { _events.emit(event) }
    }

    fun getCategoryName(id: String): String = _uiState.value.categories.find { it.id == id }?.name ?: ""
    fun getCategoryColor(id: String): String = _uiState.value.categories.find { it.id == id }?.color ?: "#6B7280"
    fun getAccountName(id: String): String = _uiState.value.accounts.find { it.id == id }?.name ?: "Unknown"
}