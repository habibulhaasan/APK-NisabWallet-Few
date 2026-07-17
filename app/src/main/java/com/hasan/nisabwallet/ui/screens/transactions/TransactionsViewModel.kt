package com.hasan.nisabwallet.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
import java.util.*
import javax.inject.Inject

// ─── UI State ─────────────────────────────────────────────────────────────────

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val accounts: List<AccountItem> = emptyList(),
    val categories: List<CategoryItem> = emptyList(),

    // Filters
    val filterType: String = "All",          // "All" | "Income" | "Expense"
    val filterAccountId: String = "all",
    val filterCategoryId: String = "all",
    val filterStartDate: String = "",
    val filterEndDate: String = "",
    val searchQuery: String = "",

    // Monthly summary
    val thisMonthIncome: Double = 0.0,
    val thisMonthExpense: Double = 0.0,

    // Sheet state
    val showAddEditSheet: Boolean = false,
    val editingTransaction: Transaction? = null,
    val showFilterSheet: Boolean = false,
    val showDetailPopup: Boolean = false,
    val detailTransaction: Transaction? = null,
    val showDeleteConfirm: Boolean = false,
    val deletingTransaction: Transaction? = null,
)

// ─── Domain models for this screen ────────────────────────────────────────────

data class Transaction(
    val id: String = "",
    val type: String = "",                 // "Income" | "Expense"
    val amount: Double = 0.0,
    val accountId: String = "",
    val categoryId: String = "",
    val description: String = "",
    val date: String = "",                 // "yyyy-MM-dd"
    val isCharge: Boolean = false,
    val isRiba: Boolean = false,
    val chargeAmount: Double = 0.0,
    val chargeNote: String = "",
    val createdAtMillis: Long = 0L,

    // Transfer specific fields
    val isTransfer: Boolean = false,
    val originalId: String? = null,
    val transferDirection: String? = null, // "from" | "to"
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
    val type: String = "",                 // "Income" | "Expense"
    val color: String = "#6B7280",
    val isSystem: Boolean = false,
    val isRiba: Boolean = false,
)

// Form state for AddEditTransactionSheet
data class TransactionForm(
    val type: String = "Expense", // "Income", "Expense", "Transfer"
    val amount: String = "",
    val accountId: String = "",
    val toAccountId: String = "", // Used only for transfers
    val categoryId: String = "",
    val description: String = "",
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val chargeAmount: String = "",
    val chargeNote: String = "",
)

// Events
sealed class TransactionEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : TransactionEvent()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TransactionEvent>()
    val events = _events.asSharedFlow()

    init {
        loadAll()
    }

    fun refresh() = loadAll()

    private fun loadAll() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val transactions = loadTransactionsAndTransfers(uid)
                val accounts     = loadAccounts(uid)
                val categories   = loadCategories(uid)
                val monthSummary = computeMonthSummary(transactions)

                _uiState.update { state ->
                    state.copy(
                        isLoading          = false,
                        transactions       = transactions,
                        filteredTransactions = applyFilters(transactions, state),
                        accounts           = accounts,
                        categories         = categories,
                        thisMonthIncome    = monthSummary.first,
                        thisMonthExpense   = monthSummary.second,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                emit(TransactionEvent.ShowToast("Failed to load: ${e.message}", true))
            }
        }
    }

    private suspend fun loadTransactionsAndTransfers(uid: String): List<Transaction> {
        // Query transactions with fallback for caching/index errors
        val txSnap = try {
            db.collection("users").document(uid)
                .collection("transactions")
                .orderBy("date", Query.Direction.DESCENDING)
                .get().await()
        } catch (e: Exception) {
            db.collection("users").document(uid)
                .collection("transactions")
                .get().await()
        }

        val normalTxs = txSnap.documents.map { d ->
            Transaction(
                id            = d.id,
                type          = d.getString("type") ?: "",
                amount        = d.getDouble("amount") ?: 0.0,
                accountId     = d.getString("accountId") ?: "",
                categoryId    = d.getString("categoryId") ?: "",
                description   = d.getString("description") ?: "",
                date          = d.getString("date") ?: "",
                isCharge      = d.getBoolean("isCharge") ?: false,
                isRiba        = d.getBoolean("isRiba") ?: false,
                chargeAmount  = d.getDouble("chargeAmount") ?: 0.0,
                chargeNote    = d.getString("chargeNote") ?: "",
                createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            )
        }

        // Query transfers with fallback
        val trSnap = try {
            db.collection("users").document(uid)
                .collection("transfers")
                .orderBy("date", Query.Direction.DESCENDING)
                .get().await()
        } catch (e: Exception) {
            db.collection("users").document(uid)
                .collection("transfers")
                .get().await()
        }

        val expandedTransfers = mutableListOf<Transaction>()

        trSnap.documents.forEach { t ->
            val id = t.id
            val amt = t.getDouble("amount") ?: 0.0
            val fromId = t.getString("fromAccountId") ?: ""
            val fromName = t.getString("fromAccountName") ?: ""
            val toId = t.getString("toAccountId") ?: ""
            val toName = t.getString("toAccountName") ?: ""
            val desc = t.getString("description") ?: ""
            val date = t.getString("date") ?: ""
            val ts = t.getTimestamp("createdAt")?.toDate()?.time ?: 0L

            // Expand into Expense (From)
            expandedTransfers.add(
                Transaction(
                    id = "$id-expense", originalId = id, isTransfer = true,
                    type = "Expense", amount = amt, accountId = fromId,
                    description = desc.ifBlank { "Transfer to $toName" },
                    originalDescription = desc,
                    date = date, createdAtMillis = ts,
                    transferDirection = "from", relatedAccountId = toId, relatedAccountName = toName
                )
            )
            // Expand into Income (To)
            expandedTransfers.add(
                Transaction(
                    id = "$id-income", originalId = id, isTransfer = true,
                    type = "Income", amount = amt, accountId = toId,
                    description = desc.ifBlank { "Transfer from $fromName" },
                    originalDescription = desc,
                    date = date, createdAtMillis = ts,
                    transferDirection = "to", relatedAccountId = fromId, relatedAccountName = fromName
                )
            )
        }

        // Combine and resolve the secondary sorting locally
        return (normalTxs + expandedTransfers).sortedWith(
            compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAtMillis }
        )
    }

    private suspend fun loadAccounts(uid: String): List<AccountItem> {
        val snap = db.collection("users").document(uid).collection("accounts").get().await()
        return snap.documents.map { d ->
            AccountItem(
                id      = d.id,
                name    = d.getString("name") ?: "",
                type    = d.getString("type") ?: "",
                balance = d.getDouble("balance") ?: 0.0,
            )
        }
    }

    private suspend fun loadCategories(uid: String): List<CategoryItem> {
        val snap = db.collection("users").document(uid).collection("categories").get().await()
        return snap.documents.map { d ->
            CategoryItem(
                id       = d.id,
                name     = d.getString("name") ?: "",
                type     = d.getString("type") ?: "",
                color    = d.getString("color") ?: "#6B7280",
                isSystem = d.getBoolean("isSystem") ?: false,
                isRiba   = d.getBoolean("isRiba") ?: false,
            )
        }.sortedBy { it.name }
    }

    private fun computeMonthSummary(transactions: List<Transaction>): Pair<Double, Double> {
        val cal   = Calendar.getInstance()
        val year  = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val start = "%04d-%02d-01".format(year, month)
        val end   = "%04d-%02d-%02d".format(year, month,
            cal.apply { set(year, month - 1, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH))

        var income  = 0.0
        var expense = 0.0
        transactions.forEach { tx ->
            // Skip transfers in the summary
            if (!tx.isTransfer && tx.date in start..end) {
                if (tx.type == "Income")  income  += tx.amount
                if (tx.type == "Expense") expense += tx.amount
            }
        }
        return Pair(income, expense)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = applyFilters(state.transactions, state.copy(searchQuery = query))
            state.copy(searchQuery = query, filteredTransactions = filtered)
        }
    }

    fun setFilterType(type: String) {
        _uiState.update { state ->
            val filtered = applyFilters(state.transactions, state.copy(filterType = type))
            state.copy(filterType = type, filteredTransactions = filtered)
        }
    }

    fun setFilterAccount(accountId: String) {
        _uiState.update { state ->
            val filtered = applyFilters(state.transactions, state.copy(filterAccountId = accountId))
            state.copy(filterAccountId = accountId, filteredTransactions = filtered)
        }
    }

    fun setFilterCategory(categoryId: String) {
        _uiState.update { state ->
            val filtered = applyFilters(state.transactions, state.copy(filterCategoryId = categoryId))
            state.copy(filterCategoryId = categoryId, filteredTransactions = filtered)
        }
    }

    fun setFilterDateRange(start: String, end: String) {
        _uiState.update { state ->
            val filtered = applyFilters(state.transactions, state.copy(filterStartDate = start, filterEndDate = end))
            state.copy(filterStartDate = start, filterEndDate = end, filteredTransactions = filtered)
        }
    }

    fun clearFilters() {
        _uiState.update { state ->
            state.copy(
                filterType       = "All",
                filterAccountId  = "all",
                filterCategoryId = "all",
                filterStartDate  = "",
                filterEndDate    = "",
                searchQuery      = "",
                filteredTransactions = state.transactions,
            )
        }
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
        _uiState.update { it.copy(showAddEditSheet = true, editingTransaction = null) }
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

                    // Update balances
                    db.collection("users").document(uid).collection("accounts").document(fromAcc.id)
                        .update("balance", fromAcc.balance - amount).await()
                    db.collection("users").document(uid).collection("accounts").document(toAcc.id)
                        .update("balance", toAcc.balance + amount).await()

                    // Charge handling for transfers
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
                loadAll()
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

                    // Revert old balances
                    val oldFromId = if (oldTx.transferDirection == "from") oldTx.accountId else oldTx.relatedAccountId!!
                    val oldToId = if (oldTx.transferDirection == "to") oldTx.accountId else oldTx.relatedAccountId!!
                    val oldAmt = oldTx.amount

                    val oldFromAcc = _uiState.value.accounts.find { it.id == oldFromId }
                    val oldToAcc = _uiState.value.accounts.find { it.id == oldToId }

                    if (oldFromAcc != null) db.collection("users").document(uid).collection("accounts")
                        .document(oldFromId).update("balance", oldFromAcc.balance + oldAmt).await()
                    if (oldToAcc != null) db.collection("users").document(uid).collection("accounts")
                        .document(oldToId).update("balance", oldToAcc.balance - oldAmt).await()

                    // Apply new transfer doc
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

                    // Apply new balances
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
                loadAll()
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
                loadAll()
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
                _uiState.update { state ->
                    state.copy(categories = (state.categories + newCat).sortedBy { it.name })
                }
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