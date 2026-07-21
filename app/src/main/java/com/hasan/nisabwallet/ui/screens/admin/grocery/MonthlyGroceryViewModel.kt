package com.hasan.nisabwallet.ui.screens.admin.grocery

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
import java.util.Calendar
import javax.inject.Inject

data class GroceryItem(
    val id: String = "",
    val name: String = "",
    val unit: String = "pcs",
    val defaultQty: Double = 1.0,
    val defaultUnitPrice: Double = 0.0,
    val category: String = "",
    val archived: Boolean = false
)

data class GroceryMonthItem(
    val itemId: String = "",
    val qty: Double = 0.0,
    val unitPrice: Double = 0.0,
    val bought: Boolean = false,
    val boughtPrice: Double? = null,
    val groupPriceTotal: Double? = null,
    val groupPriceShare: Double? = null,
    val isGroupItem: Boolean = false
)

data class GroceryMonthData(
    val id: String = "",
    val month: String = "",
    val items: List<GroceryMonthItem> = emptyList(),
    val confirmedAt: Any? = null,
    val transactionIds: List<String> = emptyList(),
    val transactionId: String = "",
    val totalAmount: Double = 0.0,
    val accountId: String = "",
    val categoryId: String = "",
    val note: String = "",
    val recordedItemIds: List<String> = emptyList()
)

data class GroceryRow(
    val itemId: String,
    val name: String,
    val unit: String,
    val category: String,
    val archived: Boolean,
    val curQty: Double,
    val curUnitPrice: Double,
    val curBought: Boolean,
    val curBoughtPrice: Double?,
    val curRecorded: Boolean,
    val groupPriceTotal: Double?,
    val groupPriceShare: Double?,
    val isGroupItem: Boolean,
    val prevQty: Double?,
    val prevUnitPrice: Double?,
    val prevBought: Boolean
) {
    val effectivePrice: Double
        get() = if (isGroupItem) groupPriceTotal ?: 0.0 else curBoughtPrice ?: (curQty * curUnitPrice)
}

data class GroceryAccount(val id: String = "", val name: String = "", val balance: Double = 0.0)
data class GroceryCategoryItem(val id: String = "", val name: String = "")
data class BulkRow(val id: String, val name: String = "", val unit: String = "pcs", val defaultQty: String = "1", val defaultUnitPrice: String = "", val category: String = "")
data class GroceryItemForm(val name: String = "", val unit: String = "pcs", val defaultQty: String = "1", val defaultUnitPrice: String = "", val category: String = "")
data class ConfirmRecordState(val accountId: String = "", val categoryId: String = "", val note: String = "", val date: String = "")
data class GroupPriceModalState(val show: Boolean = false, val catId: String = "", val selectedItemIds: List<String> = emptyList(), val groupTotal: String = "", val perItemPrices: Map<String, String> = emptyMap())

data class GroceryUiState(
    val isAuthLoading: Boolean = true,
    val isAdmin: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isReversing: Boolean = false,
    
    val syncStatus: String = "Connecting...",

    val activeTab: String = "planner",
    val curYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val curMonth: Int = Calendar.getInstance().get(Calendar.MONTH),

    val items: List<GroceryItem> = emptyList(),
    val monthData: Map<String, GroceryMonthData> = emptyMap(),
    val accounts: List<GroceryAccount> = emptyList(),
    val expenseCategories: List<GroceryCategoryItem> = emptyList(),

    val rows: List<GroceryRow> = emptyList(),
    val dirtyItemIds: Set<String> = emptySet(),

    val searchQuery: String = "",
    val filterBought: String = "all",
    val filterCategory: String = "all",
    val showArchived: Boolean = false,
    val showRecorded: Boolean = false,

    val showAddItemModal: Boolean = false,
    val addModalTab: String = "single",
    val addItemForm: GroceryItemForm = GroceryItemForm(),
    val bulkRows: List<BulkRow> = emptyList(),
    val editingItemId: String? = null,

    val showConfirmModal: Boolean = false,
    val confirmState: ConfirmRecordState = ConfirmRecordState(),

    val groupModal: GroupPriceModalState = GroupPriceModalState(),

    val showReverseModal: Boolean = false,
    val reverseTargetYm: String = ""
)

sealed class GroceryEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : GroceryEvent()
    object NavigateToDashboard : GroceryEvent()
}

@HiltViewModel
class MonthlyGroceryViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroceryUiState())
    val uiState: StateFlow<GroceryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GroceryEvent>()
    val events = _events.asSharedFlow()

    private var itemsListener: ListenerRegistration? = null
    private var monthsListener: ListenerRegistration? = null
    private var accListener: ListenerRegistration? = null
    private var catListener: ListenerRegistration? = null

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
        _uiState.update { it.copy(isLoading = true, dirtyItemIds = emptySet()) }

        accListener?.remove()
        accListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val accounts = snap.documents.mapNotNull { d ->
                        val bal = d.getDouble("balance") ?: 0.0
                        if (bal > 0) GroceryAccount(d.id, d.getString("name") ?: "", bal) else null
                    }.sortedBy { it.name }
                    _uiState.update { state -> 
                        state.copy(
                            accounts = accounts, 
                            confirmState = state.confirmState.copy(accountId = state.confirmState.accountId.ifBlank { accounts.firstOrNull()?.id ?: "" })
                        ) 
                    }
                }
            }

        catListener?.remove()
        catListener = db.collection("users").document(uid).collection("categories")
            .whereEqualTo("type", "Expense")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val cats = snap.documents.map { d ->
                        GroceryCategoryItem(d.id, d.getString("name") ?: "")
                    }.sortedBy { it.name }
                    _uiState.update { it.copy(expenseCategories = cats) }
                }
            }

        itemsListener?.remove()
        itemsListener = db.collection("users").document(uid).collection("groceryItems")
            .addSnapshotListener { snap, e ->
                if (e != null) return@addSnapshotListener
                if (snap != null) {
                    val items = snap.documents.map { d ->
                        GroceryItem(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            unit = d.getString("unit") ?: "pcs",
                            defaultQty = d.getDouble("defaultQty") ?: 1.0,
                            defaultUnitPrice = d.getDouble("defaultUnitPrice") ?: 0.0,
                            category = d.getString("category") ?: "",
                            archived = d.getBoolean("archived") ?: false
                        )
                    }.sortedBy { it.name }
                    _uiState.update { it.copy(items = items) }
                    rebuildRowsIfClean()
                }
            }

        monthsListener?.remove()
        monthsListener = db.collection("users").document(uid).collection("groceryMonths")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    emit(GroceryEvent.ShowToast("Sync error: ${e.message}", true))
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val status = when {
                        snap.metadata.hasPendingWrites() -> "Syncing..."
                        snap.metadata.isFromCache() -> "Offline (Cached)"
                        else -> "Synced"
                    }

                    val mData = snap.documents.associate { d ->
                        val rawItems = d.get("items") as? List<Map<String, Any>> ?: emptyList()
                        val parsedItems = rawItems.map { r ->
                            GroceryMonthItem(
                                itemId = r["itemId"] as? String ?: "",
                                qty = (r["qty"] as? Number)?.toDouble() ?: 0.0,
                                unitPrice = (r["unitPrice"] as? Number)?.toDouble() ?: 0.0,
                                bought = r["bought"] as? Boolean ?: false,
                                boughtPrice = (r["boughtPrice"] as? Number)?.toDouble(),
                                groupPriceTotal = (r["groupPriceTotal"] as? Number)?.toDouble(),
                                groupPriceShare = (r["groupPriceShare"] as? Number)?.toDouble(),
                                isGroupItem = r["isGroupItem"] as? Boolean ?: false
                            )
                        }
                        @Suppress("UNCHECKED_CAST")
                        d.id to GroceryMonthData(
                            id = d.id,
                            month = d.getString("month") ?: d.id,
                            items = parsedItems,
                            confirmedAt = d.get("confirmedAt"),
                            transactionIds = d.get("transactionIds") as? List<String> ?: emptyList(),
                            transactionId = d.getString("transactionId") ?: "",
                            totalAmount = d.getDouble("totalAmount") ?: 0.0,
                            accountId = d.getString("accountId") ?: "",
                            categoryId = d.getString("categoryId") ?: "",
                            note = d.getString("note") ?: "",
                            recordedItemIds = d.get("recordedItemIds") as? List<String> ?: emptyList()
                        )
                    }

                    _uiState.update { it.copy(monthData = mData, syncStatus = status) }
                    rebuildRowsIfClean()
                }
            }
    }

    private fun rebuildRowsIfClean() {
        val state = _uiState.value
        if (state.dirtyItemIds.isNotEmpty()) return 

        val curYm = fmtYm(state.curYear, state.curMonth)
        val prevYm = getPrevYm(state.curYear, state.curMonth)

        val curMonthItems = state.monthData[curYm]?.items ?: emptyList()
        val prevMonthItems = state.monthData[prevYm]?.items ?: emptyList()
        val recordedIds = state.monthData[curYm]?.recordedItemIds ?: emptyList()

        val built = state.items.map { item ->
            val cur = curMonthItems.find { it.itemId == item.id }
            val prev = prevMonthItems.find { it.itemId == item.id }
            GroceryRow(
                itemId = item.id,
                name = item.name,
                unit = item.unit,
                category = item.category,
                archived = item.archived,
                curQty = cur?.qty ?: item.defaultQty,
                curUnitPrice = cur?.unitPrice ?: item.defaultUnitPrice,
                curBought = cur?.bought ?: false,
                curBoughtPrice = cur?.boughtPrice,
                curRecorded = recordedIds.contains(item.id),
                groupPriceTotal = cur?.groupPriceTotal,
                groupPriceShare = cur?.groupPriceShare,
                isGroupItem = cur?.isGroupItem ?: false,
                prevQty = prev?.qty,
                prevUnitPrice = prev?.unitPrice,
                prevBought = prev?.bought ?: false
            )
        }

        _uiState.update { it.copy(rows = built, isLoading = false) }
    }

    override fun onCleared() {
        super.onCleared()
        itemsListener?.remove()
        monthsListener?.remove()
        accListener?.remove()
        catListener?.remove()
    }

    private fun fmtYm(year: Int, month: Int) = "%04d-%02d".format(year, month + 1)
    private fun getPrevYm(year: Int, month: Int): String {
        val cal = Calendar.getInstance().apply { set(year, month, 1); add(Calendar.MONTH, -1) }
        return fmtYm(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    fun goPrevMonth() {
        val cal = Calendar.getInstance().apply { set(_uiState.value.curYear, _uiState.value.curMonth, 1); add(Calendar.MONTH, -1) }
        _uiState.update { it.copy(curYear = cal.get(Calendar.YEAR), curMonth = cal.get(Calendar.MONTH), dirtyItemIds = emptySet(), isLoading = true) }
        rebuildRowsIfClean()
    }

    fun goNextMonth() {
        val cal = Calendar.getInstance().apply { set(_uiState.value.curYear, _uiState.value.curMonth, 1); add(Calendar.MONTH, 1) }
        _uiState.update { it.copy(curYear = cal.get(Calendar.YEAR), curMonth = cal.get(Calendar.MONTH), dirtyItemIds = emptySet(), isLoading = true) }
        rebuildRowsIfClean()
    }

    private fun updateRow(itemId: String, transform: (GroceryRow) -> GroceryRow) {
        _uiState.update { state ->
            val nextRows = state.rows.map { if (it.itemId == itemId) transform(it) else it }
            state.copy(rows = nextRows, dirtyItemIds = state.dirtyItemIds + itemId)
        }
    }

    fun updateRowQty(itemId: String, value: String) {
        val v = value.toDoubleOrNull() ?: 0.0
        updateRow(itemId) { it.copy(curQty = v) }
    }

    fun updateRowUnitPrice(itemId: String, value: String) {
        val v = value.toDoubleOrNull() ?: 0.0
        updateRow(itemId) { it.copy(curUnitPrice = v) }
    }

    fun updateRowBoughtPrice(itemId: String, value: String) {
        val v = value.toDoubleOrNull()
        updateRow(itemId) { it.copy(curBoughtPrice = v) }
    }

    fun toggleBought(itemId: String) {
        _uiState.update { state ->
            val nextRows = state.rows.map { r ->
                if (r.itemId == itemId) {
                    val willBuy = !r.curBought
                    val defaultPrice = r.groupPriceShare ?: (r.curQty * r.curUnitPrice)
                    r.copy(
                        curBought = willBuy,
                        curBoughtPrice = if (willBuy) (r.curBoughtPrice ?: (if (defaultPrice > 0) defaultPrice else null)) else null
                    )
                } else r
            }
            state.copy(rows = nextRows, dirtyItemIds = state.dirtyItemIds + itemId)
        }
    }

    fun saveMonth() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        if (state.dirtyItemIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val curYm = fmtYm(state.curYear, state.curMonth)
                val existingItems = state.monthData[curYm]?.items?.associateBy { it.itemId }?.toMutableMap() ?: mutableMapOf()
                
                state.dirtyItemIds.forEach { id ->
                    val row = state.rows.find { it.itemId == id } ?: return@forEach
                    existingItems[id] = GroceryMonthItem(
                        itemId = row.itemId,
                        qty = row.curQty,
                        unitPrice = row.curUnitPrice,
                        bought = row.curBought,
                        boughtPrice = row.curBoughtPrice,
                        groupPriceTotal = row.groupPriceTotal,
                        groupPriceShare = row.groupPriceShare,
                        isGroupItem = row.isGroupItem
                    )
                }

                db.collection("users").document(uid).collection("groceryMonths").document(curYm)
                    .set(mapOf(
                        "month" to curYm,
                        "items" to existingItems.values.toList(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ), SetOptions.merge()).await()

                _uiState.update { it.copy(isSaving = false, dirtyItemIds = emptySet()) }
                emit(GroceryEvent.ShowToast("Saved successfully"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                emit(GroceryEvent.ShowToast("Save failed: ${e.message}", true))
            }
        }
    }

    fun openConfirmModal() {
        _uiState.update { it.copy(showConfirmModal = true, confirmState = ConfirmRecordState(
            date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
            accountId = it.accounts.firstOrNull()?.id ?: ""
        )) }
    }
    fun closeConfirmModal() = _uiState.update { it.copy(showConfirmModal = false) }
    fun updateConfirmState(state: ConfirmRecordState) = _uiState.update { it.copy(confirmState = state) }

    fun confirmRecord() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val confirm = state.confirmState

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val batch = db.batch()
                val recordable = state.rows.filter { it.curBought && !it.curRecorded && !it.archived }
                val totalAmount = recordable.sumOf { it.curBoughtPrice ?: (it.curQty * it.curUnitPrice) }

                // 1. Deduct from account
                val accRef = db.collection("users").document(uid).collection("accounts").document(confirm.accountId)
                val accBal = state.accounts.find { it.id == confirm.accountId }?.balance ?: 0.0
                batch.update(accRef, "balance", accBal - totalAmount)

                // 2. Create Transaction
                val txRef = db.collection("users").document(uid).collection("transactions").document()
                val txData = mapOf(
                    "type" to "Expense",
                    "amount" to totalAmount,
                    "accountId" to confirm.accountId,
                    "categoryId" to confirm.categoryId,
                    "description" to confirm.note.ifBlank { "Monthly Grocery" },
                    "date" to confirm.date,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                batch.set(txRef, txData)

                // 3. Mark grocery items as recorded
                val monthRef = db.collection("users").document(uid)
                    .collection("groceryMonths").document("${state.curYear}-${state.curMonth + 1}")
                val currentRecorded = state.monthData["${state.curYear}-${state.curMonth + 1}"]?.recordedItemIds ?: emptyList()
                val newRecordedIds = currentRecorded + recordable.map { it.itemId }
                
                batch.set(monthRef, mapOf("recordedItemIds" to newRecordedIds), SetOptions.merge())
                
                batch.commit() // Instant offline commit

                emit(GroceryEvent.ShowToast("Expense recorded successfully"))
                closeConfirmModal()
            } catch (e: Exception) {
                emit(GroceryEvent.ShowToast("Failed to record: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun openReverseModal(ym: String) = _uiState.update { it.copy(showReverseModal = true, reverseTargetYm = ym) }
    fun closeReverseModal() = _uiState.update { it.copy(showReverseModal = false) }

    fun reverseRecord() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val ym = state.reverseTargetYm
        val data = state.monthData[ym] ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isReversing = true) }
            try {
                val txIds = data.transactionIds.ifEmpty { if (data.transactionId.isNotBlank()) listOf(data.transactionId) else emptyList() }
                
                for (txId in txIds) {
                    try { db.collection("users").document(uid).collection("transactions").document(txId).delete().await() } catch (_: Exception) {}
                }

                if (data.accountId.isNotBlank() && data.totalAmount > 0) {
                    val accSnap = db.collection("users").document(uid).collection("accounts").document(data.accountId).get().await()
                    if (accSnap.exists()) {
                        val oldBal = accSnap.getDouble("balance") ?: 0.0
                        db.collection("users").document(uid).collection("accounts").document(data.accountId)
                            .update("balance", oldBal + data.totalAmount).await()
                    }
                }

                db.collection("users").document(uid).collection("groceryMonths").document(ym)
                    .set(mapOf(
                        "confirmedAt" to null,
                        "transactionIds" to emptyList<String>(),
                        "transactionId" to "",
                        "totalAmount" to 0.0,
                        "recordedItemIds" to emptyList<String>()
                    ), SetOptions.merge()).await()

                _uiState.update { it.copy(isReversing = false, showReverseModal = false) }
                emit(GroceryEvent.ShowToast("Recording reversed successfully"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isReversing = false) }
                emit(GroceryEvent.ShowToast("Reverse failed: ${e.message}", true))
            }
        }
    }

    fun openGroupModal(catId: String) {
        val items = _uiState.value.rows.filter { (it.category.ifBlank { "__none__" } == catId) && !it.curRecorded }
        _uiState.update { it.copy(
            groupModal = GroupPriceModalState(
                show = true, catId = catId,
                selectedItemIds = items.map { r -> r.itemId },
                groupTotal = "",
                perItemPrices = items.associate { r -> r.itemId to (r.curBoughtPrice?.toString() ?: "") }
            )
        )}
    }

    fun closeGroupModal() = _uiState.update { it.copy(groupModal = GroupPriceModalState()) }

    fun setGroupTotal(amount: String) = _uiState.update { it.copy(groupModal = it.groupModal.copy(groupTotal = amount)) }
    
    fun setPerItemPrice(id: String, price: String) = _uiState.update { 
        it.copy(groupModal = it.groupModal.copy(perItemPrices = it.groupModal.perItemPrices + (id to price))) 
    }

    fun confirmGroupPrice() {
        val state = _uiState.value
        val total = state.groupModal.groupTotal.toDoubleOrNull() ?: return
        val selected = state.groupModal.selectedItemIds

        _uiState.update { st ->
            val nextRows = st.rows.map { r ->
                if (r.itemId in selected) {
                    val refPrice = st.groupModal.perItemPrices[r.itemId]?.toDoubleOrNull()
                    r.copy(
                        curBought = true,
                        curBoughtPrice = refPrice,
                        groupPriceTotal = total,
                        groupPriceShare = refPrice,
                        isGroupItem = true
                    )
                } else r
            }
            st.copy(
                rows = nextRows,
                dirtyItemIds = st.dirtyItemIds + selected,
                groupModal = GroupPriceModalState()
            )
        }
    }

    fun openAddItemModal() = _uiState.update { it.copy(showAddItemModal = true, addModalTab = "single", editingItemId = null, addItemForm = GroceryItemForm(), bulkRows = List(5) { BulkRow(id = java.util.UUID.randomUUID().toString()) }) }
    fun closeAddItemModal() = _uiState.update { it.copy(showAddItemModal = false, editingItemId = null) }
    fun setAddModalTab(tab: String) = _uiState.update { it.copy(addModalTab = tab) }

    fun openEditItemModal(item: GroceryItem) = _uiState.update { 
        it.copy(
            showAddItemModal = true, editingItemId = item.id,
            addItemForm = GroceryItemForm(item.name, item.unit, item.defaultQty.toString(), if (item.defaultUnitPrice > 0) item.defaultUnitPrice.toString() else "", item.category)
        ) 
    }

    fun updateAddItemForm(form: GroceryItemForm) = _uiState.update { it.copy(addItemForm = form) }

    fun addItem() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val f = _uiState.value.addItemForm
                db.collection("users").document(uid).collection("groceryItems").add(mapOf(
                    "name" to f.name.trim(), "unit" to f.unit,
                    "defaultQty" to (f.defaultQty.toDoubleOrNull() ?: 1.0),
                    "defaultUnitPrice" to (f.defaultUnitPrice.toDoubleOrNull() ?: 0.0),
                    "category" to f.category, "createdAt" to FieldValue.serverTimestamp()
                )).await()
                _uiState.update { it.copy(isSaving = false, showAddItemModal = false) }
                emit(GroceryEvent.ShowToast("Item added successfully"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                emit(GroceryEvent.ShowToast("Failed to add: ${e.message}", true))
            }
        }
    }

    fun editItem() {
        val uid = auth.currentUser?.uid ?: return
        val itemId = _uiState.value.editingItemId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val f = _uiState.value.addItemForm
                db.collection("users").document(uid).collection("groceryItems").document(itemId).update(mapOf(
                    "name" to f.name.trim(), "unit" to f.unit,
                    "defaultQty" to (f.defaultQty.toDoubleOrNull() ?: 1.0),
                    "defaultUnitPrice" to (f.defaultUnitPrice.toDoubleOrNull() ?: 0.0),
                    "category" to f.category
                )).await()
                _uiState.update { it.copy(isSaving = false, showAddItemModal = false, editingItemId = null) }
                emit(GroceryEvent.ShowToast("Item updated"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                emit(GroceryEvent.ShowToast("Failed to update: ${e.message}", true))
            }
        }
    }

    fun archiveItem(itemId: String) {
        val uid = auth.currentUser?.uid ?: return
        val current = _uiState.value.items.find { it.id == itemId }?.archived ?: false
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("groceryItems").document(itemId).update("archived", !current).await()
                emit(GroceryEvent.ShowToast(if (current) "Item restored" else "Item archived"))
            } catch (e: Exception) { emit(GroceryEvent.ShowToast("Action failed: ${e.message}", true)) }
        }
    }

    fun deleteItem(itemId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("groceryItems").document(itemId).delete().await()
                emit(GroceryEvent.ShowToast("Item deleted"))
            } catch (e: Exception) { emit(GroceryEvent.ShowToast("Delete failed: ${e.message}", true)) }
        }
    }

    fun updateBulkRow(index: Int, row: BulkRow) = _uiState.update { 
        val list = it.bulkRows.toMutableList()
        list[index] = row
        it.copy(bulkRows = list) 
    }
    
    fun addBulkRow() = _uiState.update { it.copy(bulkRows = it.bulkRows + BulkRow(id = java.util.UUID.randomUUID().toString())) }

    fun addItemsBulk() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val validRows = _uiState.value.bulkRows.filter { it.name.isNotBlank() }
                validRows.forEach { r ->
                    db.collection("users").document(uid).collection("groceryItems").add(mapOf(
                        "name" to r.name.trim(), "unit" to r.unit,
                        "defaultQty" to (r.defaultQty.toDoubleOrNull() ?: 1.0),
                        "defaultUnitPrice" to (r.defaultUnitPrice.toDoubleOrNull() ?: 0.0),
                        "category" to r.category, "createdAt" to FieldValue.serverTimestamp()
                    )).await()
                }
                _uiState.update { it.copy(isSaving = false, showAddItemModal = false, bulkRows = List(5) { BulkRow(id = java.util.UUID.randomUUID().toString()) }) }
                emit(GroceryEvent.ShowToast("${validRows.size} items added"))
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                emit(GroceryEvent.ShowToast("Bulk add failed: ${e.message}", true))
            }
        }
    }

    fun setActiveTab(tab: String) = _uiState.update { it.copy(activeTab = tab) }
    fun setSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun setFilterBought(f: String) = _uiState.update { it.copy(filterBought = f) }
    fun setFilterCategory(c: String) = _uiState.update { it.copy(filterCategory = c) }
    fun toggleShowRecorded() = _uiState.update { it.copy(showRecorded = !it.showRecorded) }
    fun toggleShowArchived() = _uiState.update { it.copy(showArchived = !it.showArchived) }

    fun boughtItems(state: GroceryUiState) = state.rows.count { it.curBought }
    fun totalItems(state: GroceryUiState) = state.rows.size
    fun totalEstimated(state: GroceryUiState) = state.rows.filter { !it.curBought && it.curQty > 0 && it.curUnitPrice > 0 }.sumOf { it.curQty * it.curUnitPrice }
    
    fun totalSpent(state: GroceryUiState): Double {
        var sum = 0.0
        val seen = mutableSetOf<String>()
        state.rows.filter { it.curBought }.forEach { r ->
            if (r.isGroupItem) {
                val key = "${r.category}::${r.groupPriceTotal}"
                if (!seen.contains(key)) { seen.add(key); sum += (r.groupPriceTotal ?: 0.0) }
            } else { sum += r.curBoughtPrice ?: (r.curQty * r.curUnitPrice) }
        }
        return sum
    }

    fun filteredRows(state: GroceryUiState): List<GroceryRow> {
        return state.rows.filter { r ->
            if (!state.showArchived && r.archived) return@filter false
            if (!state.showRecorded && r.curRecorded) return@filter false
            
            val matchSearch = r.name.contains(state.searchQuery, ignoreCase = true)
            val matchBought = when (state.filterBought) {
                "bought" -> r.curBought
                "not_bought" -> !r.curBought
                else -> true
            }
            val matchCat = if (state.filterCategory == "all") true else r.category == state.filterCategory
            matchSearch && matchBought && matchCat
        }
    }

    private fun emit(event: GroceryEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}