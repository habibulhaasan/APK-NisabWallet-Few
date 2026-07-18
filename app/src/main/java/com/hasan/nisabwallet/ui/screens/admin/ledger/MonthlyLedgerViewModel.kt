package com.hasan.nisabwallet.ui.screens.admin.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject

data class LedgerRow(
    val id: String        = newId(),
    val desc: String      = "",
    val amount: String    = "",
    val catId: String     = "",
    val isSynced: Boolean = false,
    val isRecorded: Boolean = false,
    val txId: String?     = null,
) {
    companion object {
        fun newId() = "${System.currentTimeMillis()}-${(Math.random() * 1e9).toLong()}"
    }
    val isDone: Boolean get() = isSynced || isRecorded
    val amountDouble: Double get() = amount.toDoubleOrNull() ?: 0.0
}

data class LedgerCategory(
    val id: String   = "",
    val name: String = "",
    val type: String = "",
    val color: String = "#6B7280",
)

data class LedgerAccount(
    val id: String      = "",
    val name: String    = "",
    val type: String    = "",
    val balance: Double = 0.0,
)

data class Budget(
    val id: String?     = null,
    val categoryId: String = "",
    val amount: Double  = 0.0,
)

data class LedgerData(
    val expense: Map<String, Map<Int, List<LedgerRow>>> = emptyMap(),
    val income: Map<Int, List<LedgerRow>>               = emptyMap(),
)

data class RecordQueueItem(
    val catId: String,
    val catName: String,
    val day: Int,
    val rowId: String,
    val desc: String,
    val amount: Double,
    val type: String,
)

data class LedgerUiState(
    val isAuthLoading: Boolean = true,
    val isAdmin: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSyncing: Boolean = false,
    val isRecording: Boolean = false,
    val isDirty: Boolean = false,

    val syncStatus: String = "Connecting...",

    val curYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val curMonth: Int = Calendar.getInstance().get(Calendar.MONTH),

    val expenseCategories: List<LedgerCategory> = emptyList(),
    val incomeCategories: List<LedgerCategory>  = emptyList(),
    val orderedExpenseCats: List<LedgerCategory> = emptyList(),
    val ledgerData: LedgerData = LedgerData(),
    val budgets: Map<String, Budget> = emptyMap(),
    val accounts: List<LedgerAccount> = emptyList(),

    val hiddenCategoryIds: Set<String> = emptySet(),
    val categoryOrder: List<String>    = emptyList(),
    val incomeCollapsed: Boolean       = false,

    val showAllDates: Map<String, Boolean> = emptyMap(),

    val showBudgetModal: Boolean       = false,
    val editingBudgetCatId: String     = "",
    val budgetInput: String            = "",
    val showCatSettingsModal: Boolean  = false,
    val showRecordModal: Boolean       = false,
    val showRowRecordModal: Boolean    = false,
    val recordAccountId: String        = "",

    val recordQueue: Map<String, RecordQueueItem> = emptyMap(),
    val modalAccountsPerCat: Map<String, String> = emptyMap(),
)

sealed class LedgerEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : LedgerEvent()
    object NavigateToDashboard : LedgerEvent()
}

@HiltViewModel
class MonthlyLedgerViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LedgerEvent>()
    val events = _events.asSharedFlow()

    private fun fmtYm(year: Int, month: Int) = "%04d-%02d".format(year, month + 1)
    private val curYm get() = fmtYm(_uiState.value.curYear, _uiState.value.curMonth)

    private var catListener: ListenerRegistration? = null
    private var prefsListener: ListenerRegistration? = null
    private var accListener: ListenerRegistration? = null
    private var ledgerListener: ListenerRegistration? = null
    private var budgetListener: ListenerRegistration? = null

    init {
        checkAdminAndLoad()
    }

    private fun checkAdminAndLoad() {
        if (auth.currentUser?.uid == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAdmin = true, isAuthLoading = false) }
            startRealTimeSync()
        }
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true, isDirty = false, recordQueue = emptyMap()) }

        startStaticListeners(uid)
        startMonthSpecificListeners(uid)
    }

    private fun startStaticListeners(uid: String) {
        catListener?.remove()
        catListener = db.collection("users").document(uid).collection("categories")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val categories = snap.documents.map { d ->
                        LedgerCategory(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            type = d.getString("type") ?: "",
                            color = d.getString("color") ?: "#6B7280"
                        )
                    }
                    val expCats = categories.filter { it.type == "Expense" }
                    val incCats = categories.filter { it.type == "Income" }

                    _uiState.update { state ->
                        state.copy(
                            expenseCategories = expCats,
                            incomeCategories = incCats,
                            orderedExpenseCats = buildOrderedCats(expCats, state.categoryOrder)
                        )
                    }
                }
            }

        prefsListener?.remove()
        prefsListener = db.collection("users").document(uid).collection("ledgerPrefs").document("global")
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val hidden = (snap.get("hiddenFromLedger") as? List<String>)?.toSet() ?: emptySet()
                    @Suppress("UNCHECKED_CAST")
                    val order = (snap.get("catOrder") as? List<String>) ?: emptyList()

                    _uiState.update { state ->
                        state.copy(
                            hiddenCategoryIds = hidden,
                            categoryOrder = order,
                            orderedExpenseCats = buildOrderedCats(state.expenseCategories, order)
                        )
                    }
                }
            }

        accListener?.remove()
        accListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val accounts = snap.documents.map { d ->
                        LedgerAccount(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            type = d.getString("type") ?: "",
                            balance = d.getDouble("balance") ?: 0.0
                        )
                    }.sortedWith(compareBy({ it.type.lowercase() != "cash" }, { it.name }))

                    _uiState.update { state ->
                        state.copy(
                            accounts = accounts,
                            recordAccountId = state.recordAccountId.ifBlank { accounts.firstOrNull()?.id ?: "" }
                        )
                    }
                }
            }
    }

    private fun startMonthSpecificListeners(uid: String) {
        val state = _uiState.value
        val ym = fmtYm(state.curYear, state.curMonth)
        val year = state.curYear
        val month = state.curMonth + 1

        ledgerListener?.remove()
        ledgerListener = db.collection("users").document(uid).collection("monthlyLedger").document(ym)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    emit(LedgerEvent.ShowToast("Sync error: ${e.message}", true))
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val status = when {
                        snap.metadata.hasPendingWrites() -> "Syncing..."
                        snap.metadata.isFromCache() -> "Offline (Cached)"
                        else -> "Synced"
                    }

                    if (!_uiState.value.isDirty) {
                        @Suppress("UNCHECKED_CAST")
                        val raw = snap.get("data") as? Map<String, Any>
                        val ledger = if (raw != null) parseLedgerData(raw) else LedgerData()
                        _uiState.update { it.copy(ledgerData = ledger, isLoading = false, syncStatus = status) }
                    } else {
                        _uiState.update { it.copy(syncStatus = status, isLoading = false) }
                    }
                }
            }

        budgetListener?.remove()
        budgetListener = db.collection("users").document(uid).collection("budgets")
            .whereEqualTo("year", year).whereEqualTo("month", month)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val budgets = snap.documents.associate { d ->
                        val catId = d.getString("categoryId") ?: ""
                        catId to Budget(id = d.id, categoryId = catId, amount = d.getDouble("amount") ?: 0.0)
                    }
                    _uiState.update { it.copy(budgets = budgets) }
                }
            }
    }

    private fun parseLedgerData(raw: Map<String, Any>): LedgerData {
        val expense = (raw["expense"] as? Map<String, Any>)?.mapValues { (_, dayMap) ->
            (dayMap as? Map<String, Any>)?.mapNotNull { (dayStr, rows) ->
                val day  = dayStr.toIntOrNull() ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val list = (rows as? List<Map<String, Any>>)?.map { r -> rowFromMap(r) } ?: return@mapNotNull null
                day to list
            }?.toMap() ?: emptyMap()
        } ?: emptyMap()

        val income = (raw["income"] as? Map<String, Any>)?.mapNotNull { (dayStr, rows) ->
            val day  = dayStr.toIntOrNull() ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val list = (rows as? List<Map<String, Any>>)?.map { r -> rowFromMap(r) } ?: return@mapNotNull null
            day to list
        }?.toMap() ?: emptyMap()

        return LedgerData(expense = expense, income = income)
    }

    @Suppress("UNCHECKED_CAST")
    private fun rowFromMap(m: Map<String, Any>): LedgerRow {
        val id = (m["id"] as? String) ?: LedgerRow.newId()
        return LedgerRow(
            id         = id,
            desc       = m["desc"] as? String ?: "",
            amount     = (m["amount"] as? String) ?: (m["amount"] as? Number)?.toString() ?: "",
            catId      = m["catId"] as? String ?: "",
            isSynced   = id.startsWith("sync-"),
            isRecorded = m["_recorded"] as? Boolean ?: false,
            txId       = m["_txId"] as? String,
        )
    }

    private fun buildOrderedCats(expCats: List<LedgerCategory>, order: List<String>): List<LedgerCategory> {
        if (order.isEmpty()) return expCats
        val byId    = expCats.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }.toMutableList()
        expCats.filter { !order.contains(it.id) }.forEach { ordered.add(it) }
        return ordered
    }

    override fun onCleared() {
        super.onCleared()
        catListener?.remove()
        prefsListener?.remove()
        accListener?.remove()
        ledgerListener?.remove()
        budgetListener?.remove()
    }

    fun goPrevMonth() {
        val cal = Calendar.getInstance().apply { set(_uiState.value.curYear, _uiState.value.curMonth, 1); add(Calendar.MONTH, -1) }
        _uiState.update { it.copy(curYear = cal.get(Calendar.YEAR), curMonth = cal.get(Calendar.MONTH), isDirty = false, isLoading = true) }
        startMonthSpecificListeners(auth.currentUser?.uid ?: return)
    }

    fun goNextMonth() {
        val cal = Calendar.getInstance().apply { set(_uiState.value.curYear, _uiState.value.curMonth, 1); add(Calendar.MONTH, 1) }
        _uiState.update { it.copy(curYear = cal.get(Calendar.YEAR), curMonth = cal.get(Calendar.MONTH), isDirty = false, isLoading = true) }
        startMonthSpecificListeners(auth.currentUser?.uid ?: return)
    }

    fun toggleShowAllDates(key: String) {
        _uiState.update { state ->
            val prev = state.showAllDates[key] ?: false
            state.copy(showAllDates = state.showAllDates + (key to !prev))
        }
    }

    fun updateExpenseRow(catId: String, day: Int, rowId: String, field: String, value: String) {
        _uiState.update { state ->
            val data    = state.ledgerData
            val catMap  = data.expense.toMutableMap()
            val dayMap  = (catMap[catId] ?: emptyMap()).toMutableMap()
            val rows    = (dayMap[day] ?: emptyList()).toMutableList()
            val idx     = rows.indexOfFirst { it.id == rowId }
            if (idx >= 0) {
                var row = rows[idx]
                row = when (field) {
                    "desc"   -> row.copy(desc = value)
                    "amount" -> row.copy(amount = value, isRecorded = if (row.isRecorded) false else row.isRecorded)
                    else     -> row
                }
                rows[idx] = row
            } else {
                rows.add(LedgerRow(id = rowId, desc = if (field == "desc") value else "",
                    amount = if (field == "amount") value else ""))
            }
            dayMap[day] = rows
            catMap[catId] = dayMap
            state.copy(ledgerData = data.copy(expense = catMap), isDirty = true)
        }
    }

    fun addExpenseRow(catId: String, day: Int) {
        _uiState.update { state ->
            val data   = state.ledgerData
            val catMap = data.expense.toMutableMap()
            val dayMap = (catMap[catId] ?: emptyMap()).toMutableMap()
            val rows   = (dayMap[day] ?: emptyList()).toMutableList()
            rows.add(LedgerRow())
            dayMap[day] = rows
            catMap[catId] = dayMap
            state.copy(ledgerData = data.copy(expense = catMap), isDirty = true)
        }
    }

    fun removeExpenseRow(catId: String, day: Int, rowId: String) {
        _uiState.update { state ->
            val data   = state.ledgerData
            val catMap = data.expense.toMutableMap()
            val dayMap = (catMap[catId] ?: emptyMap()).toMutableMap()
            dayMap[day] = (dayMap[day] ?: emptyList()).filter { it.id != rowId }
            catMap[catId] = dayMap
            val qKey = "$catId||$day||$rowId"
            state.copy(
                ledgerData  = data.copy(expense = catMap),
                recordQueue = state.recordQueue - qKey,
                isDirty     = true,
            )
        }
    }

    fun updateIncomeRow(day: Int, rowId: String, field: String, value: String) {
        _uiState.update { state ->
            val data    = state.ledgerData
            val dayMap  = data.income.toMutableMap()
            val rows    = (dayMap[day] ?: emptyList()).toMutableList()
            val idx     = rows.indexOfFirst { it.id == rowId }
            if (idx >= 0) {
                var row = rows[idx]
                row = when (field) {
                    "desc"  -> row.copy(desc = value)
                    "amount"-> row.copy(amount = value, isRecorded = if (row.isRecorded) false else row.isRecorded)
                    "catId" -> row.copy(catId = value)
                    else    -> row
                }
                rows[idx] = row
            } else {
                rows.add(LedgerRow(id = rowId, catId = if (field == "catId") value else ""))
            }
            dayMap[day] = rows
            state.copy(ledgerData = data.copy(income = dayMap), isDirty = true)
        }
    }

    fun addIncomeRow(day: Int, firstCatId: String = "") {
        _uiState.update { state ->
            val data   = state.ledgerData
            val dayMap = data.income.toMutableMap()
            val rows   = (dayMap[day] ?: emptyList()).toMutableList()
            rows.add(LedgerRow(catId = firstCatId))
            dayMap[day] = rows
            state.copy(ledgerData = data.copy(income = dayMap), isDirty = true)
        }
    }

    fun removeIncomeRow(day: Int, rowId: String) {
        _uiState.update { state ->
            val data   = state.ledgerData
            val dayMap = data.income.toMutableMap()
            dayMap[day] = (dayMap[day] ?: emptyList()).filter { it.id != rowId }
            val qKey   = "__income__||$day||$rowId"
            state.copy(
                ledgerData  = data.copy(income = dayMap),
                recordQueue = state.recordQueue - qKey,
                isDirty     = true,
            )
        }
    }

    fun toggleRowQueue(catId: String, catName: String, day: Int, row: LedgerRow, type: String) {
        val key = "$catId||$day||${row.id}"
        _uiState.update { state ->
            val queue = state.recordQueue.toMutableMap()
            if (queue.containsKey(key)) queue.remove(key)
            else queue[key] = RecordQueueItem(catId, catName, day, row.id, row.desc, row.amountDouble, type)
            state.copy(recordQueue = queue)
        }
    }

    fun saveLedger() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val data = _uiState.value.ledgerData
                val expenseMap = data.expense.mapValues { (_, dayMap) ->
                    dayMap.mapKeys { it.key.toString() }.mapValues { (_, rows) ->
                        rows.map { rowToMap(it) }
                    }
                }
                val incomeMap = data.income.mapKeys { it.key.toString() }.mapValues { (_, rows) ->
                    rows.map { rowToMap(it) }
                }
                db.collection("users").document(uid)
                    .collection("monthlyLedger").document(curYm)
                    .set(mapOf(
                        "month"     to curYm,
                        "data"      to mapOf("expense" to expenseMap, "income" to incomeMap),
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    ), SetOptions.merge()).await()
                _uiState.update { it.copy(isSaving = false, isDirty = false) }
                emit(LedgerEvent.ShowToast("Saved successfully"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                emit(LedgerEvent.ShowToast("Save failed: ${e.message}", true))
            }
        }
    }

    private fun rowToMap(row: LedgerRow): Map<String, Any?> = mapOf(
        "id"        to row.id,
        "desc"      to row.desc,
        "amount"    to row.amount,
        "catId"     to row.catId,
        "_recorded" to row.isRecorded,
        "_txId"     to row.txId,
    )

    fun syncFromTransactions() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                val state     = _uiState.value
                val monthStr  = fmtYm(state.curYear, state.curMonth)
                val startDate = "$monthStr-01"
                val lastDay   = Calendar.getInstance().apply {
                    set(state.curYear, state.curMonth, 1)
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                }.get(Calendar.DAY_OF_MONTH)
                val endDate   = "$monthStr-%02d".format(lastDay)

                val snap = db.collection("users").document(uid)
                    .collection("transactions")
                    .whereGreaterThanOrEqualTo("date", startDate)
                    .whereLessThanOrEqualTo("date", endDate)
                    .orderBy("date", Query.Direction.ASCENDING)
                    .get().await()

                if (snap.isEmpty) {
                    emit(LedgerEvent.ShowToast("No transactions found for this month"))
                    _uiState.update { it.copy(isSyncing = false) }
                    return@launch
                }

                val expenseMap = mutableMapOf<String, MutableMap<Int, MutableList<LedgerRow>>>()
                val incomeMap  = mutableMapOf<Int, MutableList<LedgerRow>>()

                snap.documents.forEach { d ->
                    val tx     = d.data ?: return@forEach
                    if (tx["isTransfer"] == true) return@forEach
                    val day    = (tx["date"] as? String)?.split("-")?.getOrNull(2)?.toIntOrNull() ?: return@forEach
                    val catId  = tx["categoryId"] as? String ?: ""
                    val amount = ((tx["amount"] as? Number)?.toDouble() ?: 0.0).toString()
                    val desc   = tx["description"] as? String ?: ""
                    val type   = tx["type"] as? String ?: "Expense"
                    val syncRow = LedgerRow(id = "sync-${d.id}", desc = desc, amount = amount,
                        catId = catId, isSynced = true)

                    if (type == "Income") {
                        incomeMap.getOrPut(day) { mutableListOf() }.add(syncRow)
                    } else {
                        expenseMap.getOrPut(catId) { mutableMapOf() }
                            .getOrPut(day) { mutableListOf() }.add(syncRow)
                    }
                }

                _uiState.update { st ->
                    val curr    = st.ledgerData
                    val newExp  = curr.expense.toMutableMap()
                    expenseMap.forEach { (catId, dayMap) ->
                        val existing = (newExp[catId] ?: emptyMap()).toMutableMap()
                        dayMap.forEach { (day, syncRows) ->
                            val manual = (existing[day] ?: emptyList()).filter { !it.isSynced }
                            existing[day] = syncRows + manual
                        }
                        newExp[catId] = existing
                    }
                    val newInc = curr.income.toMutableMap()
                    incomeMap.forEach { (day, syncRows) ->
                        val manual = (newInc[day] ?: emptyList()).filter { !it.isSynced }
                        newInc[day] = syncRows + manual
                    }
                    val total = snap.documents.count { it.getBoolean("isTransfer") != true }
                    st.copy(
                        ledgerData = LedgerData(expense = newExp, income = newInc),
                        isSyncing  = false,
                        isDirty    = true,
                    ).also { emit(LedgerEvent.ShowToast("Synced $total transaction${if (total != 1) "s" else ""} — click Save to store")) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSyncing = false) }
                emit(LedgerEvent.ShowToast("Sync failed: ${e.message}", true))
            }
        }
    }

    fun confirmRowRecord() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRecording = true, showRowRecordModal = false) }
            try {
                val state   = _uiState.value
                val queue   = state.recordQueue
                val modalAcc = state.modalAccountsPerCat
                val balanceDeltas = mutableMapOf<String, Double>()

                var created = 0
                var updated = 0

                val byCat = queue.values.groupBy { it.catId }

                byCat.forEach { (catId, items) ->
                    val accountId = modalAcc[catId] ?: state.accounts.firstOrNull()?.id ?: return@forEach
                    items.forEach { item ->
                        val dateStr = "%s-%02d".format(fmtYm(state.curYear, state.curMonth), item.day)
                        val txType  = if (item.type == "income") "Income" else "Expense"

                        val existingTxId = findLedgerRowTxId(state.ledgerData, item)

                        if (existingTxId != null) {
                            val oldSnap = db.collection("users").document(uid)
                                .collection("transactions").document(existingTxId).get().await()
                            if (oldSnap.exists()) {
                                val oldAmt    = oldSnap.getDouble("amount") ?: 0.0
                                val oldAccId  = oldSnap.getString("accountId") ?: ""
                                val oldType   = oldSnap.getString("type") ?: ""
                                val reversal  = if (oldType == "Income") -oldAmt else oldAmt
                                balanceDeltas[oldAccId] = (balanceDeltas[oldAccId] ?: 0.0) + reversal
                            }
                            db.collection("users").document(uid)
                                .collection("transactions").document(existingTxId)
                                .update(mapOf(
                                    "amount"      to item.amount,
                                    "accountId"   to accountId,
                                    "description" to item.desc,
                                    "date"        to dateStr,
                                    "updatedAt"   to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                )).await()
                            updated++
                        } else {
                            val txRef = db.collection("users").document(uid)
                                .collection("transactions").add(mapOf(
                                    "type"         to txType,
                                    "amount"       to item.amount,
                                    "accountId"    to accountId,
                                    "categoryId"   to catId,
                                    "description"  to item.desc,
                                    "date"         to dateStr,
                                    "isCharge"     to false,
                                    "isRiba"       to false,
                                    "source"       to "monthly_ledger",
                                    "ledgerRowId"  to item.rowId,
                                    "createdAt"    to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                )).await()
                            storeLedgerRowTxId(item, txRef.id)
                            created++
                        }

                        val delta = if (txType == "Income") item.amount else -item.amount
                        balanceDeltas[accountId] = (balanceDeltas[accountId] ?: 0.0) + delta
                    }
                }

                balanceDeltas.forEach { (accId, delta) ->
                    val acc = state.accounts.find { it.id == accId } ?: return@forEach
                    db.collection("users").document(uid).collection("accounts")
                        .document(accId).update("balance", acc.balance + delta).await()
                }

                markRowsAsRecorded(queue)

                _uiState.update { it.copy(isRecording = false, recordQueue = emptyMap(), isDirty = true) }
                val msg = listOfNotNull(
                    if (created > 0) "$created created" else null,
                    if (updated > 0) "$updated updated" else null,
                ).joinToString(", ")
                emit(LedgerEvent.ShowToast("Recorded: $msg — save to persist"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecording = false) }
                emit(LedgerEvent.ShowToast("Recording failed: ${e.message}", true))
            }
        }
    }

    private fun findLedgerRowTxId(data: LedgerData, item: RecordQueueItem): String? {
        return if (item.type == "income") {
            data.income[item.day]?.find { it.id == item.rowId }?.txId
        } else {
            data.expense[item.catId]?.get(item.day)?.find { it.id == item.rowId }?.txId
        }
    }

    private fun storeLedgerRowTxId(item: RecordQueueItem, txId: String) {
        _uiState.update { state ->
            val data = state.ledgerData
            if (item.type == "income") {
                val dayMap = data.income.toMutableMap()
                dayMap[item.day] = (dayMap[item.day] ?: emptyList()).map { r ->
                    if (r.id == item.rowId) r.copy(txId = txId) else r
                }
                state.copy(ledgerData = data.copy(income = dayMap))
            } else {
                val expMap = data.expense.toMutableMap()
                val dayMap = (expMap[item.catId] ?: emptyMap()).toMutableMap()
                dayMap[item.day] = (dayMap[item.day] ?: emptyList()).map { r ->
                    if (r.id == item.rowId) r.copy(txId = txId) else r
                }
                expMap[item.catId] = dayMap
                state.copy(ledgerData = data.copy(expense = expMap))
            }
        }
    }

    private fun markRowsAsRecorded(queue: Map<String, RecordQueueItem>) {
        _uiState.update { state ->
            var data = state.ledgerData
            queue.values.forEach { item ->
                if (item.type == "income") {
                    val dayMap = data.income.toMutableMap()
                    dayMap[item.day] = (dayMap[item.day] ?: emptyList()).map { r ->
                        if (r.id == item.rowId) r.copy(isRecorded = true) else r
                    }
                    data = data.copy(income = dayMap)
                } else {
                    val expMap = data.expense.toMutableMap()
                    val dayMap = (expMap[item.catId] ?: emptyMap()).toMutableMap()
                    dayMap[item.day] = (dayMap[item.day] ?: emptyList()).map { r ->
                        if (r.id == item.rowId) r.copy(isRecorded = true) else r
                    }
                    expMap[item.catId] = dayMap
                    data = data.copy(expense = expMap)
                }
            }
            state.copy(ledgerData = data)
        }
    }

    fun openBudgetModal(catId: String) {
        val current = _uiState.value.budgets[catId]?.amount ?: 0.0
        _uiState.update { it.copy(showBudgetModal = true, editingBudgetCatId = catId, budgetInput = if (current > 0) current.toString() else "") }
    }

    fun closeBudgetModal() = _uiState.update { it.copy(showBudgetModal = false, editingBudgetCatId = "", budgetInput = "") }

    fun setBudgetInput(value: String) = _uiState.update { it.copy(budgetInput = value) }

    fun saveBudget() {
        val uid    = auth.currentUser?.uid ?: return
        val catId  = _uiState.value.editingBudgetCatId
        val amount = _uiState.value.budgetInput.toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            try {
                val state    = _uiState.value
                val catName  = state.expenseCategories.find { it.id == catId }?.name ?: ""
                val existing = state.budgets[catId]

                val budgetData = mapOf(
                    "categoryId"   to catId,
                    "categoryName" to catName,
                    "amount"       to amount,
                    "year"         to state.curYear,
                    "month"        to state.curMonth + 1,
                    "rollover"     to false,
                    "notes"        to "",
                )

                val savedId = if (existing?.id != null) {
                    db.collection("users").document(uid).collection("budgets")
                        .document(existing.id).set(budgetData, SetOptions.merge()).await()
                    existing.id
                } else {
                    db.collection("users").document(uid).collection("budgets")
                        .add(budgetData + ("createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())).await().id
                }

                val nextMonth = if (state.curMonth + 1 == 12) 1 else state.curMonth + 2
                val nextYear  = if (state.curMonth + 1 == 12) state.curYear + 1 else state.curYear
                val nextData  = budgetData.toMutableMap()
                nextData["month"] = nextMonth
                nextData["year"]  = nextYear

                val nextSnap  = db.collection("users").document(uid).collection("budgets")
                    .whereEqualTo("categoryId", catId).whereEqualTo("year", nextYear)
                    .whereEqualTo("month", nextMonth).get().await()

                if (!nextSnap.isEmpty) {
                    nextSnap.documents.first().reference.set(nextData, SetOptions.merge()).await()
                } else {
                    db.collection("users").document(uid).collection("budgets")
                        .add(nextData + ("createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())).await()
                }

                _uiState.update { st ->
                    st.copy(
                        budgets = st.budgets + (catId to Budget(id = savedId, categoryId = catId, amount = amount)),
                        showBudgetModal = false, editingBudgetCatId = "", budgetInput = "",
                    )
                }
                emit(LedgerEvent.ShowToast("Budget saved & carried forward"))
            } catch (e: Exception) {
                emit(LedgerEvent.ShowToast("Budget save failed: ${e.message}", true))
            }
        }
    }

    fun moveCategoryUp(index: Int)   = moveCategory(index, -1)
    fun moveCategoryDown(index: Int) = moveCategory(index, 1)

    private fun moveCategory(index: Int, dir: Int) {
        val uid = auth.currentUser?.uid ?: return
        _uiState.update { state ->
            val arr  = state.orderedExpenseCats.toMutableList()
            val swap = index + dir
            if (swap < 0 || swap >= arr.size) return@update state
            val temp = arr[index]; arr[index] = arr[swap]; arr[swap] = temp
            val newOrder = arr.map { it.id }
            viewModelScope.launch { savePrefs(uid, state.hiddenCategoryIds, newOrder) }
            state.copy(orderedExpenseCats = arr, categoryOrder = newOrder)
        }
    }

    fun toggleCategoryVisibility(catId: String) {
        val uid = auth.currentUser?.uid ?: return
        _uiState.update { state ->
            val hidden = state.hiddenCategoryIds.toMutableSet()
            if (hidden.contains(catId)) hidden.remove(catId) else hidden.add(catId)
            viewModelScope.launch { savePrefs(uid, hidden, state.categoryOrder) }
            state.copy(hiddenCategoryIds = hidden)
        }
    }

    private suspend fun savePrefs(uid: String, hidden: Set<String>, order: List<String>) {
        db.collection("users").document(uid).collection("ledgerPrefs").document("global")
            .set(mapOf(
                "hiddenFromLedger" to hidden.toList(),
                "catOrder"         to order,
                "updatedAt"        to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            ), SetOptions.merge()).await()
    }

    fun openRowRecordModal() {
        val queuedCatIds = _uiState.value.recordQueue.values.map { it.catId }.distinct()
        val defaultAccId = _uiState.value.accounts.firstOrNull()?.id ?: ""
        val initial      = queuedCatIds.associateWith { defaultAccId }
        _uiState.update { it.copy(showRowRecordModal = true, modalAccountsPerCat = initial) }
    }

    fun closeRowRecordModal() = _uiState.update { it.copy(showRowRecordModal = false) }

    fun setModalAccount(catId: String, accountId: String) {
        _uiState.update { it.copy(modalAccountsPerCat = it.modalAccountsPerCat + (catId to accountId)) }
    }

    fun openRecordModal() = _uiState.update { it.copy(showRecordModal = true) }
    fun closeRecordModal() = _uiState.update { it.copy(showRecordModal = false) }
    fun setRecordAccountId(id: String) = _uiState.update { it.copy(recordAccountId = id) }

    fun showCatSettings() = _uiState.update { it.copy(showCatSettingsModal = true) }
    fun hideCatSettings() = _uiState.update { it.copy(showCatSettingsModal = false) }

    fun getDaysInMonth(): Int {
        val state = _uiState.value
        val cal = Calendar.getInstance()
        cal.set(state.curYear, state.curMonth, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun recordableCount(): Int {
        val state     = _uiState.value
        val visExp    = state.orderedExpenseCats.filter { !state.hiddenCategoryIds.contains(it.id) }
        val visInc    = state.incomeCategories.filter { !state.hiddenCategoryIds.contains(it.id) }
        val visIncIds = visInc.map { it.id }.toSet()
        var n = 0
        visExp.forEach { cat ->
            state.ledgerData.expense[cat.id]?.values?.forEach { rows ->
                rows.forEach { r -> if (r.amountDouble > 0 && !r.isDone) n++ }
            }
        }
        state.ledgerData.income.values.forEach { rows ->
            rows.forEach { r -> if (r.amountDouble > 0 && !r.isDone && r.catId in visIncIds) n++ }
        }
        return n
    }

    private fun emit(event: LedgerEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}