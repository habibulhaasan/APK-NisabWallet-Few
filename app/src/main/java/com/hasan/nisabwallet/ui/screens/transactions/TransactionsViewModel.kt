package com.hasan.nisabwallet.ui.screens.transactions

// Converted from: src/app/dashboard/transactions/page.js (68 KB)
// Source deps: firestoreCollections.js (getAccounts, updateAccount, generateId),
//              goalUtils.js (getAvailableBalance), jewelleryCollections.js (unmarkSold)

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

    // Filters — mirrors filter state in page.js
    val filterType: String = "All",          // "All" | "Income" | "Expense"
    val filterAccountId: String = "all",
    val filterCategoryId: String = "all",
    val filterStartDate: String = "",
    val filterEndDate: String = "",
    val searchQuery: String = "",

    // Monthly summary — mirrors thisMonthIncome / thisMonthExpense
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
    val type: String = "Expense",
    val amount: String = "",
    val accountId: String = "",
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

    // ── loadAll — mirrors initial useEffect + loadTransactions/loadAccounts/loadCategories ──
    private fun loadAll() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val transactions = loadTransactions(uid)
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

    // ── loadTransactions — queries Firestore ordered by date desc ──
    // ── loadTransactions — queries Firestore and sorts locally to avoid Index errors ──
    private suspend fun loadTransactions(uid: String): List<Transaction> {
        val snap = db.collection("users").document(uid)
            .collection("transactions")
            // Single orderBy doesn't require a special Firebase Composite Index
            .orderBy("date", Query.Direction.DESCENDING)
            .get().await()

        return snap.documents.map { d ->
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
        }.sortedByDescending { it.createdAtMillis } // Handle the tie-breaker sort in Kotlin!
    }

    // ── loadAccounts — mirrors getAccounts() from firestoreCollections.js ──
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

    // ── loadCategories — mirrors category load in page.js ──
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

    // ── Month summary — mirrors thisMonthIncome / thisMonthExpense in page.js ──
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
            if (tx.date in start..end) {
                if (tx.type == "Income")  income  += tx.amount
                if (tx.type == "Expense") expense += tx.amount
            }
        }
        return Pair(income, expense)
    }

    // ── Filter actions — mirrors filter state in page.js ──
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

    // ── applyFilters — mirrors client-side filtering logic in page.js ──
    private fun applyFilters(transactions: List<Transaction>, state: TransactionsUiState): List<Transaction> {
        return transactions.filter { tx ->
            (state.filterType == "All" || tx.type == state.filterType) &&
            (state.filterAccountId == "all" || tx.accountId == state.filterAccountId) &&
            (state.filterCategoryId == "all" || tx.categoryId == state.filterCategoryId) &&
            (state.filterStartDate.isBlank() || tx.date >= state.filterStartDate) &&
            (state.filterEndDate.isBlank() || tx.date <= state.filterEndDate) &&
            (state.searchQuery.isBlank() || tx.description.contains(state.searchQuery, ignoreCase = true))
        }
    }

    // ── Sheet/dialog actions ────────────────────────────────────────────────

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

    // ── addTransaction — mirrors handleAddTransaction() in page.js ──────────
    // 1. Validates form
    // 2. Writes to Firestore transactions collection
    // 3. Updates account balance (+ for income, - for expense)
    // 4. If chargeAmount > 0: creates a separate "Fees & Charges" expense transaction
    // 5. Updates local state

    fun addTransaction(form: TransactionForm) {
        val uid = auth.currentUser?.uid ?: return
        if (form.amount.isBlank() || form.accountId.isBlank() || form.categoryId.isBlank()) {
            viewModelScope.launch { emit(TransactionEvent.ShowToast("Fill in all required fields", true)) }
            return
        }
        val amount = form.amount.toDoubleOrNull() ?: run {
            viewModelScope.launch { emit(TransactionEvent.ShowToast("Invalid amount", true)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
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
                    "createdAt"    to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                )
                db.collection("users").document(uid).collection("transactions").add(txData).await()

                // ── Update account balance — mirrors updateAccount() in page.js ──
                val account = _uiState.value.accounts.find { it.id == form.accountId }
                if (account != null) {
                    val delta = if (form.type == "Income") amount else -amount
                    db.collection("users").document(uid).collection("accounts")
                        .document(form.accountId)
                        .update("balance", account.balance + delta).await()
                }

                // ── Record charge as separate Expense (mirrors page.js charge logic) ──
                val chargeAmt = form.chargeAmount.toDoubleOrNull() ?: 0.0
                if (chargeAmt > 0) {
                    val feesCategory = getOrCreateFeesCategory(uid)
                    val chargeData = hashMapOf(
                        "type"        to "Expense",
                        "amount"      to chargeAmt,
                        "accountId"   to form.accountId,
                        "categoryId"  to feesCategory,
                        "description" to (form.chargeNote.ifBlank { "Related charges / fees" }),
                        "date"        to form.date,
                        "isCharge"    to true,
                        "createdAt"   to FieldValue.serverTimestamp(),
                    )
                    db.collection("users").document(uid).collection("transactions").add(chargeData).await()
                    // Deduct charge from account balance too
                    val acc = _uiState.value.accounts.find { it.id == form.accountId }
                    if (acc != null) {
                        db.collection("users").document(uid).collection("accounts")
                            .document(form.accountId)
                            .update("balance", acc.balance - chargeAmt).await()
                    }
                }

                emit(TransactionEvent.ShowToast("Transaction added!"))
                hideAddEditSheet()
                loadAll()
            } catch (e: Exception) {
                emit(TransactionEvent.ShowToast("Failed to add: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // ── updateTransaction — mirrors handleUpdateTransaction() in page.js ────
    // Reverses the old balance effect, then applies the new one
    fun updateTransaction(transactionId: String, form: TransactionForm) {
        val uid = auth.currentUser?.uid ?: return
        val oldTx = _uiState.value.transactions.find { it.id == transactionId } ?: return
        val newAmount = form.amount.toDoubleOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Reverse old balance effect
                val oldAccount = _uiState.value.accounts.find { it.id == oldTx.accountId }
                if (oldAccount != null) {
                    val reversal = if (oldTx.type == "Income") -oldTx.amount else oldTx.amount
                    db.collection("users").document(uid).collection("accounts")
                        .document(oldTx.accountId)
                        .update("balance", oldAccount.balance + reversal).await()
                }

                // Write updated transaction
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

                // Apply new balance effect
                val newAccount = _uiState.value.accounts.find { it.id == form.accountId }
                if (newAccount != null) {
                    val delta = if (form.type == "Income") newAmount else -newAmount
                    // Re-fetch balance after reversal
                    val freshBalance = db.collection("users").document(uid).collection("accounts")
                        .document(form.accountId).get().await().getDouble("balance") ?: 0.0
                    db.collection("users").document(uid).collection("accounts")
                        .document(form.accountId)
                        .update("balance", freshBalance + delta).await()
                }

                emit(TransactionEvent.ShowToast("Transaction updated!"))
                hideAddEditSheet()
                loadAll()
            } catch (e: Exception) {
                emit(TransactionEvent.ShowToast("Failed to update: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // ── deleteTransaction — mirrors handleDeleteTransaction() in page.js ────
    // Deletes the transaction and reverses its effect on account balance
    fun deleteTransaction() {
        val uid = auth.currentUser?.uid ?: return
        val tx  = _uiState.value.deletingTransaction ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Reverse balance effect
                val account = _uiState.value.accounts.find { it.id == tx.accountId }
                if (account != null) {
                    val reversal = if (tx.type == "Income") -tx.amount else tx.amount
                    db.collection("users").document(uid).collection("accounts")
                        .document(tx.accountId)
                        .update("balance", account.balance + reversal).await()
                }

                // Delete from Firestore
                db.collection("users").document(uid).collection("transactions")
                    .document(tx.id).delete().await()

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

    // ── addCategory (inline) — mirrors InlineCategoryForm.handleSave() in page.js ──
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

    // ── Helper: getOrCreateFeesCategory — mirrors getOrCreateSystemCategory() in page.js ──
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

    // Helper: emit events
    private fun emit(event: TransactionEvent) {
        viewModelScope.launch { _events.emit(event) }
    }

    // ── Helper: get category name / color ──
    fun getCategoryName(id: String): String = _uiState.value.categories.find { it.id == id }?.name ?: ""
    fun getCategoryColor(id: String): String = _uiState.value.categories.find { it.id == id }?.color ?: "#6B7280"
    fun getAccountName(id: String): String = _uiState.value.accounts.find { it.id == id }?.name ?: "Unknown"
}
