package com.hasan.nisabwallet.ui.screens.admin.grocery

// Converted from: src/app/dashboard/admin/monthly-grocery-2/page.js
// Source dependencies: firestoreCollections.js (addTransaction, getAccounts),
//                      adminUtils.js (checkIsAdmin)
// Collections used:
//   users/{uid}/groceryItems         — master item catalogue
//   users/{uid}/groceryMonths/{ym}   — per-month data (items[], recordedItemIds[])
//   users/{uid}/accounts             — for recording to transaction
//   users/{uid}/categories           — for expense category selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─── Data models ──────────────────────────────────────────────────────────────

// Master grocery item (groceryItems collection) — mirrors item fields in page.js
data class GroceryItem(
    val id: String             = "",
    val name: String           = "",
    val unit: String           = "pcs",          // pcs | kg | g | liter | ml | pack | dozen | bag | bottle | box | can | bunch
    val defaultQty: Double     = 1.0,
    val defaultUnitPrice: Double = 0.0,
    val category: String       = "",
    val archived: Boolean      = false,
)

// Row built from GroceryItem + this month's data — mirrors `built` in page.js buildRows()
data class GroceryRow(
    val itemId: String          = "",
    val name: String            = "",
    val unit: String            = "pcs",
    val category: String        = "",
    val archived: Boolean       = false,

    // Current month values
    val curQty: Double          = 1.0,
    val curUnitPrice: Double    = 0.0,
    val curBought: Boolean      = false,
    val curBoughtPrice: Double? = null,          // actual price paid (may differ from unit × qty)
    val curRecorded: Boolean    = false,         // in recordedItemIds[]
    val groupPriceTotal: Double? = null,
    val groupPriceShare: Double? = null,
    val isGroupItem: Boolean    = false,

    // Previous month values (for reference)
    val prevQty: Double?        = null,
    val prevUnitPrice: Double?  = null,
    val prevBought: Boolean     = false,
) {
    val effectivePrice: Double get() = curBoughtPrice ?: (curQty * curUnitPrice)
}

// Month data document — mirrors monthData[curYM] in page.js
data class GroceryMonthData(
    val id: String                  = "",
    val items: List<GroceryMonthItem> = emptyList(),
    val recordedItemIds: List<String> = emptyList(),
)

data class GroceryMonthItem(
    val itemId: String        = "",
    val qty: Double           = 1.0,
    val unitPrice: Double     = 0.0,
    val bought: Boolean       = false,
    val boughtPrice: Double?  = null,
    val groupPriceTotal: Double? = null,
    val groupPriceShare: Double? = null,
    val isGroupItem: Boolean  = false,
)

// Form for adding / editing a single grocery item
data class GroceryItemForm(
    val name: String           = "",
    val unit: String           = "pcs",
    val defaultQty: String     = "1",
    val defaultUnitPrice: String = "0",
    val category: String       = "",
)

// Bulk row for the bulk-add form
data class BulkRow(
    val id: String             = UUID.randomUUID().toString(),
    val name: String           = "",
    val unit: String           = "pcs",
    val defaultQty: String     = "1",
    val defaultUnitPrice: String = "0",
    val category: String       = "",
)

// Confirm modal for recording to transactions
data class ConfirmRecordState(
    val categoryId: String  = "",
    val accountId: String   = "",
    val note: String        = "",
    val date: String        = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
)

// Group price modal
data class GroupPriceModalState(
    val show: Boolean               = false,
    val catId: String               = "",
    val selectedItemIds: List<String> = emptyList(),
    val groupTotal: String          = "",
    val perItemPrices: Map<String, String> = emptyMap(),
)

// ─── UI State ─────────────────────────────────────────────────────────────────

data class GroceryUiState(
    val isAuthLoading: Boolean    = true,
    val isAdmin: Boolean          = false,
    val isLoading: Boolean        = true,
    val isSaving: Boolean         = false,
    val isManualSaving: Boolean   = false,

    // Month navigation
    val curYear: Int  = Calendar.getInstance().get(Calendar.YEAR),
    val curMonth: Int = Calendar.getInstance().get(Calendar.MONTH),

    // Data
    val items: List<GroceryItem>       = emptyList(),       // master catalogue
    val monthData: Map<String, GroceryMonthData> = emptyMap(),
    val accounts: List<GroceryAccount> = emptyList(),
    val expenseCategories: List<GroceryCategoryItem> = emptyList(),
    val rows: List<GroceryRow>         = emptyList(),       // built rows for current month

    // Dirty tracking — set of itemIds that changed since last save
    val dirtyItemIds: Set<String>      = emptySet(),

    // Tabs — "planner" | "history"
    val activeTab: String = "planner",

    // Filters
    val searchQuery: String      = "",
    val filterBought: String     = "all",              // "all" | "bought" | "not_bought"
    val filterCategory: String   = "all",
    val showArchived: Boolean    = false,
    val showRecorded: Boolean    = false,

    // Modals
    val showConfirmModal: Boolean      = false,
    val confirmState: ConfirmRecordState = ConfirmRecordState(),
    val showAddItemModal: Boolean      = false,
    val editingItemId: String?         = null,
    val addItemForm: GroceryItemForm   = GroceryItemForm(),
    val addModalTab: String            = "single",     // "single" | "bulk" | "csv"
    val bulkRows: List<BulkRow>        = List(5) { BulkRow() },

    // Group price modal
    val groupModal: GroupPriceModalState = GroupPriceModalState(),

    // Bulk assign
    val bulkAssignItemIds: List<String> = emptyList(),

    // Reverse modal
    val showReverseModal: Boolean     = false,
    val reverseTargetId: String?      = null,
    val isReversing: Boolean          = false,
)

// Events
sealed class GroceryEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : GroceryEvent()
    object NavigateToDashboard : GroceryEvent()
}

data class GroceryAccount(val id: String, val name: String, val balance: Double)
data class GroceryCategoryItem(val id: String, val name: String)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class MonthlyGroceryViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroceryUiState())
    val uiState: StateFlow<GroceryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GroceryEvent>()
    val events = _events.asSharedFlow()

    // fmt_ym helper — mirrors fmt_ym() in page.js
    private fun fmtYm(year: Int, month: Int) = "%04d-%02d".format(year, month + 1)
    private val curYm get() = fmtYm(_uiState.value.curYear, _uiState.value.curMonth)
    private val prevYm get() = fmtYm(_uiState.value.curYear, _uiState.value.curMonth).let { ym ->
        val cal = Calendar.getInstance()
        val y   = ym.split("-")[0].toInt()
        val m   = ym.split("-")[1].toInt() - 1
        cal.set(y, m - 1, 1)
        "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    init { checkAdminAndLoad() }

    // ── Admin check (Removed for general access) ────────────────────────
    private fun checkAdminAndLoad() {
        if (auth.currentUser?.uid == null) return

        viewModelScope.launch {
            // Bypass the Firestore check entirely and instantly grant access
            _uiState.update { state -> state.copy(isAdmin = true, isAuthLoading = false) }
            loadAll()
        }
    }

    // ── loadAll — mirrors loadAll() in page.js ─────────────────────────────
    fun loadAll() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoading = true) }
            try {
                val items      = loadItems(uid)
                val monthData  = loadMonthData(uid)
                val accounts   = loadAccounts(uid)
                val categories = loadCategories(uid)
                val rows       = buildRows(items, monthData)

                _uiState.update { state ->
                    state.copy(
                        isLoading         = false,
                        items             = items,
                        monthData         = monthData,
                        accounts          = accounts,
                        expenseCategories = categories,
                        rows              = rows,
                        dirtyItemIds      = emptySet(),
                        confirmState      = ConfirmRecordState(
                            accountId = accounts.firstOrNull()?.id ?: "",
                            categoryId = categories.firstOrNull()?.id ?: "",
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state -> state.copy(isLoading = false) }
                emit(GroceryEvent.ShowToast("Failed to load: ${e.message}", true))
            }
        }
    }

    // ── loadItems — mirrors loadItems() in page.js ─────────────────────────
    // Collection: users/{uid}/groceryItems ordered by name
    private suspend fun loadItems(uid: String): List<GroceryItem> {
        val snap = db.collection("users").document(uid)
            .collection("groceryItems").orderBy("name").get().await()
        return snap.documents.map { d ->
            GroceryItem(
                id              = d.id,
                name            = d.getString("name") ?: "",
                unit            = d.getString("unit") ?: "pcs",
                defaultQty      = d.getDouble("defaultQty") ?: 1.0,
                defaultUnitPrice= d.getDouble("defaultUnitPrice") ?: 0.0,
                category        = d.getString("category") ?: "",
                archived        = d.getBoolean("archived") ?: false,
            )
        }
    }

    // ── loadMonthData — mirrors loadMonthData() in page.js ─────────────────
    // Collection: users/{uid}/groceryMonths — all months (keyed by ym)
    private suspend fun loadMonthData(uid: String): Map<String, GroceryMonthData> {
        val snap = db.collection("users").document(uid)
            .collection("groceryMonths").get().await()
        return snap.documents.associate { d ->
            @Suppress("UNCHECKED_CAST")
            val rawItems     = d.get("items") as? List<Map<String, Any>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val recordedIds  = d.get("recordedItemIds") as? List<String> ?: emptyList()
            val monthItems   = rawItems.map { m ->
                GroceryMonthItem(
                    itemId         = m["itemId"] as? String ?: "",
                    qty            = (m["qty"] as? Number)?.toDouble() ?: 1.0,
                    unitPrice      = (m["unitPrice"] as? Number)?.toDouble() ?: 0.0,
                    bought         = m["bought"] as? Boolean ?: false,
                    boughtPrice    = (m["boughtPrice"] as? Number)?.toDouble(),
                    groupPriceTotal= (m["groupPriceTotal"] as? Number)?.toDouble(),
                    groupPriceShare= (m["groupPriceShare"] as? Number)?.toDouble(),
                    isGroupItem    = m["isGroupItem"] as? Boolean ?: false,
                )
            }
            d.id to GroceryMonthData(id = d.id, items = monthItems, recordedItemIds = recordedIds)
        }
    }

    // ── loadAccounts — only accounts with balance > 0 (mirrors loadAccounts in page.js) ──
    private suspend fun loadAccounts(uid: String): List<GroceryAccount> {
        val snap = db.collection("users").document(uid).collection("accounts").get().await()
        return snap.documents.mapNotNull { d ->
            val bal = d.getDouble("balance") ?: 0.0
            if (bal <= 0) null
            else GroceryAccount(id = d.id, name = d.getString("name") ?: "", balance = bal)
        }
    }

    // ── loadCategories — Expense categories only ──────────────────────────
    private suspend fun loadCategories(uid: String): List<GroceryCategoryItem> {
        val snap = db.collection("users").document(uid).collection("categories")
            .whereEqualTo("type", "Expense").orderBy("name").get().await()
        return snap.documents.map { d -> GroceryCategoryItem(id = d.id, name = d.getString("name") ?: "") }
    }

    // ── buildRows — mirrors the `built` array construction in page.js ───────
    // Merges master items + current month data + previous month data into GroceryRow list
    private fun buildRows(items: List<GroceryItem>, monthData: Map<String, GroceryMonthData>): List<GroceryRow> {
        val curMonthItems  = monthData[curYm]?.items  ?: emptyList()
        val prevMonthItems = monthData[prevYm]?.items ?: emptyList()
        val recordedIds    = monthData[curYm]?.recordedItemIds ?: emptyList()

        return items.map { item ->
            val cur  = curMonthItems.find { it.itemId == item.id }
            val prev = prevMonthItems.find { it.itemId == item.id }
            GroceryRow(
                itemId          = item.id,
                name            = item.name,
                unit            = item.unit,
                category        = item.category,
                archived        = item.archived,
                curQty          = cur?.qty          ?: item.defaultQty,
                curUnitPrice    = cur?.unitPrice    ?: item.defaultUnitPrice,
                curBought       = cur?.bought       ?: false,
                curBoughtPrice  = cur?.boughtPrice,
                curRecorded     = recordedIds.contains(item.id),
                groupPriceTotal = cur?.groupPriceTotal,
                groupPriceShare = cur?.groupPriceShare,
                isGroupItem     = cur?.isGroupItem  ?: false,
                prevQty         = prev?.qty,
                prevUnitPrice   = prev?.unitPrice,
                prevBought      = prev?.bought      ?: false,
            )
        }
    }

    // ── Month navigation ───────────────────────────────────────────────────
    fun goPrevMonth() {
        val cal = Calendar.getInstance()
        cal.set(_uiState.value.curYear, _uiState.value.curMonth, 1)
        cal.add(Calendar.MONTH, -1)
        _uiState.update { state -> state.copy(curYear = cal.get(Calendar.YEAR), curMonth = cal.get(Calendar.MONTH)) }
        loadAll()
    }

    fun goNextMonth() {
        val cal = Calendar.getInstance()
        cal.set(_uiState.value.curYear, _uiState.value.curMonth, 1)
        cal.add(Calendar.MONTH, 1)
        _uiState.update { state -> state.copy(curYear = cal.get(Calendar.YEAR), curMonth = cal.get(Calendar.MONTH)) }
        loadAll()
    }

    // ── Row mutations — update qty, price, bought status in local state ─────

    fun updateRowQty(itemId: String, qty: String) = updateRowField(itemId) { row -> row.copy(curQty = qty.toDoubleOrNull() ?: row.curQty) }
    fun updateRowUnitPrice(itemId: String, price: String) = updateRowField(itemId) { row -> row.copy(curUnitPrice = price.toDoubleOrNull() ?: row.curUnitPrice) }
    fun updateRowBoughtPrice(itemId: String, price: String) = updateRowField(itemId) { row -> row.copy(curBoughtPrice = price.toDoubleOrNull()) }

    // Toggle bought status — mirrors the bought checkbox in page.js
    fun toggleBought(itemId: String) {
        updateRowField(itemId) { row -> row.copy(curBought = !row.curBought) }
    }

    private fun updateRowField(itemId: String, transform: (GroceryRow) -> GroceryRow) {
        _uiState.update { state ->
            val rows = state.rows.map { row -> if (row.itemId == itemId) transform(row) else row }
            state.copy(rows = rows, dirtyItemIds = state.dirtyItemIds + itemId)
        }
    }

    // ── saveMonth — mirrors the auto-save logic in page.js ─────────────────
    // Serializes all dirty rows back to groceryMonths/{ym}
    fun saveMonth() {
        val uid   = auth.currentUser?.uid ?: return
        val state = _uiState.value
        if (state.dirtyItemIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Serialize rows → GroceryMonthItem list
                val itemsData = state.rows.map { row ->
                    mapOf(
                        "itemId"          to row.itemId,
                        "qty"             to row.curQty,
                        "unitPrice"       to row.curUnitPrice,
                        "bought"          to row.curBought,
                        "boughtPrice"     to row.curBoughtPrice,
                        "groupPriceTotal" to row.groupPriceTotal,
                        "groupPriceShare" to row.groupPriceShare,
                        "isGroupItem"     to row.isGroupItem,
                    )
                }

                db.collection("users").document(uid)
                    .collection("groceryMonths").document(curYm)
                    .set(
                        mapOf(
                            "items"     to itemsData,
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        ),
                        SetOptions.merge(),
                    ).await()

                _uiState.update { it.copy(isSaving = false, dirtyItemIds = emptySet()) }
            } catch (e: Exception) {
                _uiState.update { state -> state.copy(isSaving = false) }
                emit(GroceryEvent.ShowToast("Save failed: ${e.message}", true))
            }
        }
    }

    // ── confirmRecord — mirrors handleConfirmRecord() in page.js ────────────
    // Creates an Expense transaction for all bought + unrecorded items
    // Marks them as recorded in recordedItemIds[]
    fun confirmRecord() {
        val uid     = auth.currentUser?.uid ?: return
        val state   = _uiState.value
        val confirm = state.confirmState

        if (confirm.accountId.isBlank() || confirm.categoryId.isBlank()) {
            viewModelScope.launch { emit(GroceryEvent.ShowToast("Select account and category", true)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, showConfirmModal = false) }
            try {
                val boughtUnrecorded = state.rows.filter { it.curBought && !it.curRecorded && !it.archived }
                if (boughtUnrecorded.isEmpty()) {
                    emit(GroceryEvent.ShowToast("No new bought items to record"))
                    _uiState.update { state -> state.copy(isSaving = false) }
                    return@launch
                }

                val total = boughtUnrecorded.sumOf { it.effectivePrice }

                // Create one consolidated Expense transaction — mirrors page.js behavior
                db.collection("users").document(uid).collection("transactions").add(
                    mapOf(
                        "type"        to "Expense",
                        "amount"      to total,
                        "accountId"   to confirm.accountId,
                        "categoryId"  to confirm.categoryId,
                        "description" to confirm.note.ifBlank { "Monthly Grocery — ${monthLabel()}" },
                        "date"        to confirm.date,
                        "source"      to "monthly_grocery",
                        "createdAt"   to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )
                ).await()

                // Deduct from account balance
                val acc = state.accounts.find { it.id == confirm.accountId }
                if (acc != null) {
                    db.collection("users").document(uid).collection("accounts")
                        .document(confirm.accountId)
                        .update("balance", acc.balance - total).await()
                }

                // Mark items as recorded in groceryMonths/{ym}
                val newRecordedIds = (state.monthData[curYm]?.recordedItemIds ?: emptyList()) + boughtUnrecorded.map { it.itemId }
                db.collection("users").document(uid).collection("groceryMonths").document(curYm)
                    .update("recordedItemIds", newRecordedIds).await()

                emit(GroceryEvent.ShowToast("Recorded ${boughtUnrecorded.size} items — ৳${"%.2f".format(total)}"))
                loadAll()
            } catch (e: Exception) {
                _uiState.update { state -> state.copy(isSaving = false) }
                emit(GroceryEvent.ShowToast("Record failed: ${e.message}", true))
            }
        }
    }

    // ── reverseRecord — mirrors handleReverse() in page.js ──────────────────
    // Removes an item from recordedItemIds[]
    fun reverseRecord() {
        val uid      = auth.currentUser?.uid ?: return
        val targetId = _uiState.value.reverseTargetId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isReversing = true, showReverseModal = false) }
            try {
                val curRecorded = _uiState.value.monthData[curYm]?.recordedItemIds ?: emptyList()
                db.collection("users").document(uid).collection("groceryMonths").document(curYm)
                    .update("recordedItemIds", curRecorded - targetId).await()
                emit(GroceryEvent.ShowToast("Recording reversed"))
                loadAll()
            } catch (e: Exception) {
                _uiState.update { state -> state.copy(isReversing = false) }
                emit(GroceryEvent.ShowToast("Reverse failed: ${e.message}", true))
            }
        }
    }

    // ── addItem (single) — mirrors handleAddItem() in page.js ───────────────
    fun addItem() {
        val uid  = auth.currentUser?.uid ?: return
        val form = _uiState.value.addItemForm
        if (form.name.isBlank()) {
            viewModelScope.launch { emit(GroceryEvent.ShowToast("Enter item name", true)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                db.collection("users").document(uid).collection("groceryItems").add(
                    mapOf(
                        "name"             to form.name.trim(),
                        "unit"             to form.unit,
                        "defaultQty"       to (form.defaultQty.toDoubleOrNull() ?: 1.0),
                        "defaultUnitPrice" to (form.defaultUnitPrice.toDoubleOrNull() ?: 0.0),
                        "category"         to form.category.trim(),
                        "archived"         to false,
                        "createdAt"        to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )
                ).await()
                emit(GroceryEvent.ShowToast("\"${form.name.trim()}\" added!"))
                closeAddItemModal()
                loadAll()
            } catch (e: Exception) {
                _uiState.update { state -> state.copy(isSaving = false) }
                emit(GroceryEvent.ShowToast("Failed to add: ${e.message}", true))
            }
        }
    }

    // ── addItemsBulk — mirrors handleBulkSave() in page.js ──────────────────
    fun addItemsBulk() {
        val uid   = auth.currentUser?.uid ?: return
        val valid = _uiState.value.bulkRows.filter { it.name.isNotBlank() }
        if (valid.isEmpty()) {
            viewModelScope.launch { emit(GroceryEvent.ShowToast("Enter at least one item name", true)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val batch = db.batch()
                valid.forEach { row ->
                    val ref = db.collection("users").document(uid).collection("groceryItems").document()
                    batch.set(ref, mapOf(
                        "name"             to row.name.trim(),
                        "unit"             to row.unit,
                        "defaultQty"       to (row.defaultQty.toDoubleOrNull() ?: 1.0),
                        "defaultUnitPrice" to (row.defaultUnitPrice.toDoubleOrNull() ?: 0.0),
                        "category"         to row.category.trim(),
                        "archived"         to false,
                        "createdAt"        to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    ))
                }
                batch.commit().await()
                emit(GroceryEvent.ShowToast("${valid.size} items added!"))
                closeAddItemModal()
                loadAll()
            } catch (e: Exception) {
                _uiState.update { state -> state.copy(isSaving = false) }
                emit(GroceryEvent.ShowToast("Bulk add failed: ${e.message}", true))
            }
        }
    }

    // ── editItem — mirrors handleEditItem() in page.js ───────────────────────
    fun editItem() {
        val uid     = auth.currentUser?.uid ?: return
        val editId  = _uiState.value.editingItemId ?: return
        val form    = _uiState.value.addItemForm
        if (form.name.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                db.collection("users").document(uid).collection("groceryItems").document(editId)
                    .update(mapOf(
                        "name"             to form.name.trim(),
                        "unit"             to form.unit,
                        "defaultQty"       to (form.defaultQty.toDoubleOrNull() ?: 1.0),
                        "defaultUnitPrice" to (form.defaultUnitPrice.toDoubleOrNull() ?: 0.0),
                        "category"         to form.category.trim(),
                        "updatedAt"        to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    )).await()
                emit(GroceryEvent.ShowToast("Item updated"))
                closeAddItemModal()
                loadAll()
            } catch (e: Exception) {
                _uiState.update { state -> state.copy(isSaving = false) }
                emit(GroceryEvent.ShowToast("Update failed: ${e.message}", true))
            }
        }
    }

    // ── archiveItem — mirrors handleArchiveItem() in page.js ─────────────────
    fun archiveItem(itemId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("groceryItems").document(itemId)
                    .update("archived", true).await()
                emit(GroceryEvent.ShowToast("Item archived"))
                loadAll()
            } catch (e: Exception) {
                emit(GroceryEvent.ShowToast("Archive failed: ${e.message}", true))
            }
        }
    }

    // ── deleteItem — mirrors handleDeleteItem() in page.js ───────────────────
    fun deleteItem(itemId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("groceryItems").document(itemId).delete().await()
                emit(GroceryEvent.ShowToast("Item deleted"))
                loadAll()
            } catch (e: Exception) {
                emit(GroceryEvent.ShowToast("Delete failed: ${e.message}", true))
            }
        }
    }

    // ── Group price modal actions — mirrors groupModal state in page.js ───────

    fun openGroupModal(catId: String) {
        val catItems = _uiState.value.rows.filter { it.category == catId && !it.archived }
        _uiState.update { state -> state.copy(
            groupModal = GroupPriceModalState(
                show            = true,
                catId           = catId,
                selectedItemIds = catItems.map { it.itemId },
                groupTotal      = "",
                perItemPrices   = catItems.associate { it.itemId to "" },
            )
        )}
    }

    fun closeGroupModal() = _uiState.update { state -> state.copy(groupModal = GroupPriceModalState()) }

    fun setGroupTotal(total: String) {
        _uiState.update { state ->
            val modal = state.groupModal
            // Auto-distribute equally among selected items
            val totalAmt  = total.toDoubleOrNull() ?: 0.0
            val count     = modal.selectedItemIds.size.coerceAtLeast(1)
            val perItem   = if (totalAmt > 0) (totalAmt / count) else 0.0
            val perItemMap = modal.selectedItemIds.associateWith { "%.2f".format(perItem) }
            state.copy(groupModal = modal.copy(groupTotal = total, perItemPrices = perItemMap))
        }
    }

    fun setPerItemPrice(itemId: String, price: String) {
        _uiState.update { state ->
            val modal = state.groupModal
            state.copy(groupModal = modal.copy(perItemPrices = modal.perItemPrices + (itemId to price)))
        }
    }

    fun confirmGroupPrice() {
        _uiState.update { state ->
            val modal = state.groupModal
            val rows  = state.rows.map { row ->
                if (modal.selectedItemIds.contains(row.itemId)) {
                    val share = modal.perItemPrices[row.itemId]?.toDoubleOrNull() ?: 0.0
                    row.copy(
                        groupPriceTotal = modal.groupTotal.toDoubleOrNull(),
                        groupPriceShare = share,
                        isGroupItem     = true,
                        curBoughtPrice  = share,
                        curBought       = true,
                    )
                } else row
            }
            val dirty = state.dirtyItemIds + modal.selectedItemIds
            state.copy(rows = rows, groupModal = GroupPriceModalState(), dirtyItemIds = dirty)
        }
        saveMonth()
    }

    // ── Filter actions ─────────────────────────────────────────────────────
    fun setSearchQuery(q: String)    = _uiState.update { state -> state.copy(searchQuery = q) }
    fun setFilterBought(f: String)   = _uiState.update { state -> state.copy(filterBought = f) }
    fun setFilterCategory(c: String) = _uiState.update { state -> state.copy(filterCategory = c) }
    fun toggleShowArchived()         = _uiState.update { state -> state.copy(showArchived = !state.showArchived) }
    fun toggleShowRecorded()         = _uiState.update { state -> state.copy(showRecorded = !state.showRecorded) }
    fun setActiveTab(tab: String)    = _uiState.update { state -> state.copy(activeTab = tab) }

    // ── Modal helpers ──────────────────────────────────────────────────────
    fun openAddItemModal() = _uiState.update { state -> state.copy(showAddItemModal = true, editingItemId = null, addItemForm = GroceryItemForm()) }
    fun openEditItemModal(item: GroceryItem) = _uiState.update { state -> state.copy(
        showAddItemModal = true,
        editingItemId    = item.id,
        addItemForm      = GroceryItemForm(name = item.name, unit = item.unit, defaultQty = item.defaultQty.toString(), defaultUnitPrice = item.defaultUnitPrice.toString(), category = item.category),
    )}
    fun closeAddItemModal() = _uiState.update { state -> state.copy(showAddItemModal = false, editingItemId = null, addItemForm = GroceryItemForm()) }

    fun updateAddItemForm(form: GroceryItemForm) = _uiState.update { state -> state.copy(addItemForm = form) }

    fun updateBulkRow(index: Int, row: BulkRow) {
        _uiState.update { state ->
            val rows = state.bulkRows.toMutableList().also { list -> list[index] = row }
            state.copy(bulkRows = rows)
        }
    }

    fun addBulkRow() = _uiState.update { state -> state.copy(bulkRows = state.bulkRows + BulkRow()) }

    fun setAddModalTab(tab: String) = _uiState.update { state -> state.copy(addModalTab = tab) }

    fun openConfirmModal() = _uiState.update { state -> state.copy(showConfirmModal = true) }
    fun closeConfirmModal() = _uiState.update { state -> state.copy(showConfirmModal = false) }
    fun updateConfirmState(s: ConfirmRecordState) = _uiState.update { state -> state.copy(confirmState = s) }

    fun openReverseModal(itemId: String) = _uiState.update { state -> state.copy(showReverseModal = true, reverseTargetId = itemId) }
    fun closeReverseModal() = _uiState.update { state -> state.copy(showReverseModal = false, reverseTargetId = null) }

    // ── Computed helpers ───────────────────────────────────────────────────

    // filteredRows — mirrors the filtered display list in page.js
    fun filteredRows(state: GroceryUiState = _uiState.value): List<GroceryRow> {
        return state.rows.filter { row ->
            (!row.archived || state.showArchived) &&
                    (state.showRecorded || !row.curRecorded) &&
                    (state.searchQuery.isBlank() || row.name.contains(state.searchQuery, ignoreCase = true)) &&
                    (state.filterBought == "all" || (state.filterBought == "bought" && row.curBought) || (state.filterBought == "not_bought" && !row.curBought)) &&
                    (state.filterCategory == "all" || row.category == state.filterCategory)
        }
    }

    // Summary totals — mirrors summary cards in page.js
    fun totalItems(state: GroceryUiState = _uiState.value) = state.rows.count { !it.archived }
    fun boughtItems(state: GroceryUiState = _uiState.value) = state.rows.count { it.curBought && !it.archived }
    fun totalEstimated(state: GroceryUiState = _uiState.value) = state.rows.filter { !it.archived }.sumOf { it.curQty * it.curUnitPrice }
    fun totalSpent(state: GroceryUiState = _uiState.value) = state.rows.filter { it.curBought && !it.archived }.sumOf { it.effectivePrice }

    private fun monthLabel(): String {
        val months = arrayOf("January","February","March","April","May","June","July","August","September","October","November","December")
        val state  = _uiState.value
        return "${months[state.curMonth]} ${state.curYear}"
    }

    private fun emit(event: GroceryEvent) { viewModelScope.launch { _events.emit(event) } }
}