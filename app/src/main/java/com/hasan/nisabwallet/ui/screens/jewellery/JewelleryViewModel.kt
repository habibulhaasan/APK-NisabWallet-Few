package com.hasan.nisabwallet.ui.screens.jewellery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

// ─── Data Models ───
data class JewelleryAccount(val id: String, val name: String, val balance: Double, val type: String)

data class Jewellery(
    val id: String = "",
    val name: String = "",
    val category: String = "Ring",
    val metal: String = "Gold",
    val karat: String = "22K",
    val notes: String = "",
    val acquisitionType: String = "purchased",
    val weightVori: Int = 0,
    val weightAna: Int = 0,
    val weightRoti: Int = 0,
    val weightPoint: Int = 0,
    val weightGrams: Double = 0.0,
    val purchaseDate: String = "",
    val purchaseTotal: Double? = null,
    val currentMarketValue: Double? = null,
    val currentZakatValue: Double? = null,
    val currentPriceCheckedAt: String? = null,
    val status: String = "active",
    val soldAt: String? = null,
    val soldPrice: Double? = null,
    val soldNotes: String? = null,
    val purchaseTransactionId: String? = null,
    val saleTransactionId: String? = null,
    val createdAtMillis: Long = 0L
)

data class JewelleryForm(
    val id: String? = null,
    val name: String = "",
    val category: String = "Ring",
    val metal: String = "Gold",
    val karat: String = "22K",
    val notes: String = "",
    val acquisitionType: String = "purchased",
    val weightVori: String = "",
    val weightAna: String = "",
    val weightRoti: String = "",
    val weightPoint: String = "",
    val purchaseDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val usePurchaseManual: Boolean = true,
    val purchaseTotal: String = "",
    val purchaseGoldPrice: String = "",
    val purchaseVat: String = "5",
    val purchaseMaking: String = "6",
    val purchaseOther: String = "",
    val recordTransaction: Boolean = false,
    val txAccountId: String = ""
)

data class SellJewelleryForm(
    val saleAmount: String = "",
    val saleDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val accountId: String = "",
    val notes: String = ""
)

data class JewelleryUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val items: List<Jewellery> = emptyList(),
    val filteredItems: List<Jewellery> = emptyList(),
    val accounts: List<JewelleryAccount> = emptyList(),
    
    val totalZakat: Double = 0.0,
    val totalGoldGrams: Double = 0.0,
    val totalSilverGrams: Double = 0.0,
    val totalSoldRevenue: Double = 0.0,
    val activeCount: Int = 0,
    val pricedCount: Int = 0,

    val filterStatus: String = "active",
    val filterMetal: String = "all",
    val sortBy: String = "newest",
    val searchQuery: String = "",

    val showAddModal: Boolean = false,
    val showSellModal: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    
    val form: JewelleryForm = JewelleryForm(),
    val sellForm: SellJewelleryForm = SellJewelleryForm(),
    val selectedItem: Jewellery? = null
)

sealed class JewelleryEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : JewelleryEvent()
}

@HiltViewModel
class JewelleryViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(JewelleryUiState())
    val uiState: StateFlow<JewelleryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<JewelleryEvent>()
    val events = _events.asSharedFlow()

    private var jewListener: ListenerRegistration? = null
    private var accListener: ListenerRegistration? = null

    private var rawItems = emptyList<Jewellery>()
    private var rawAccounts = emptyList<JewelleryAccount>()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        accListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawAccounts = snap.documents.map { d ->
                        JewelleryAccount(d.id, d.getString("name") ?: "", d.getDouble("balance") ?: 0.0, d.getString("type") ?: "")
                    }
                    if (_uiState.value.form.txAccountId.isBlank() && rawAccounts.isNotEmpty()) {
                        _uiState.update { 
                            it.copy(
                                form = it.form.copy(txAccountId = rawAccounts.first().id),
                                sellForm = it.sellForm.copy(accountId = rawAccounts.first().id)
                            )
                        }
                    }
                    combineAndEmit()
                }
            }

        jewListener = db.collection("users").document(uid).collection("jewellery")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    emitEvent(JewelleryEvent.ShowToast("Sync error: ${e.message}", true))
                    return@addSnapshotListener
                }
                if (snap != null) {
                    rawItems = snap.documents.map { d ->
                        Jewellery(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            category = d.getString("category") ?: "Ring",
                            metal = d.getString("metal") ?: "Gold",
                            karat = d.getString("karat") ?: "22K",
                            notes = d.getString("notes") ?: "",
                            acquisitionType = d.getString("acquisitionType") ?: "purchased",
                            weightVori = d.getLong("weightVori")?.toInt() ?: 0,
                            weightAna = d.getLong("weightAna")?.toInt() ?: 0,
                            weightRoti = d.getLong("weightRoti")?.toInt() ?: 0,
                            weightPoint = d.getLong("weightPoint")?.toInt() ?: 0,
                            weightGrams = d.getDouble("weightGrams") ?: 0.0,
                            purchaseDate = d.getString("purchaseDate") ?: "",
                            purchaseTotal = d.getDouble("purchaseTotal"),
                            currentMarketValue = d.getDouble("currentMarketValue"),
                            currentZakatValue = d.getDouble("currentZakatValue"),
                            currentPriceCheckedAt = d.getString("currentPriceCheckedAt"),
                            status = d.getString("status") ?: "active",
                            soldAt = d.getString("soldAt"),
                            soldPrice = d.getDouble("soldPrice"),
                            soldNotes = d.getString("soldNotes"),
                            purchaseTransactionId = d.getString("purchaseTransactionId"),
                            saleTransactionId = d.getString("saleTransactionId"),
                            createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        )
                    }
                    combineAndEmit()
                }
            }
    }

    private fun combineAndEmit() {
        val state = _uiState.value
        
        var totalZakat = 0.0
        var totalGold = 0.0
        var totalSilver = 0.0
        var totalSold = 0.0
        var activeCount = 0
        var pricedCount = 0

        rawItems.forEach { i ->
            if (i.status != "sold") {
                activeCount++
                if (i.currentZakatValue != null && i.currentZakatValue > 0) {
                    pricedCount++
                    totalZakat += i.currentZakatValue
                }
                if (i.metal == "Gold") totalGold += i.weightGrams
                if (i.metal == "Silver") totalSilver += i.weightGrams
            } else {
                totalSold += (i.soldPrice ?: 0.0)
            }
        }

        var filtered = rawItems
        if (state.filterStatus == "active") filtered = filtered.filter { it.status != "sold" }
        if (state.filterStatus == "sold") filtered = filtered.filter { it.status == "sold" }
        if (state.filterMetal != "all") filtered = filtered.filter { it.metal == state.filterMetal }
        if (state.searchQuery.isNotBlank()) filtered = filtered.filter { it.name.contains(state.searchQuery, true) || it.notes.contains(state.searchQuery, true) }

        filtered = when (state.sortBy) {
            "value" -> filtered.sortedByDescending { it.currentZakatValue ?: 0.0 }
            "weight" -> filtered.sortedByDescending { it.weightGrams }
            "name" -> filtered.sortedBy { it.name }
            else -> filtered.sortedByDescending { it.createdAtMillis }
        }

        _uiState.update { 
            it.copy(
                isLoading = false,
                items = rawItems,
                filteredItems = filtered,
                accounts = rawAccounts,
                totalZakat = totalZakat,
                totalGoldGrams = totalGold,
                totalSilverGrams = totalSilver,
                totalSoldRevenue = totalSold,
                activeCount = activeCount,
                pricedCount = pricedCount
            )
        }
    }

    // ─── Weight Calculations ───
    fun calculateGrams(vori: Int, ana: Int, roti: Int, point: Int): Double {
        val totalPoints = (vori * 960) + (ana * 60) + (roti * 10) + point
        return (totalPoints / 960.0) * 11.664
    }

    // ─── Intent Actions ───
    fun setFilterStatus(s: String) { _uiState.update { it.copy(filterStatus = s) }; combineAndEmit() }
    fun setFilterMetal(m: String) { _uiState.update { it.copy(filterMetal = m) }; combineAndEmit() }
    fun setSortBy(s: String) { _uiState.update { it.copy(sortBy = s) }; combineAndEmit() }
    fun setSearchQuery(q: String) { _uiState.update { it.copy(searchQuery = q) }; combineAndEmit() }

    fun openAddModal(item: Jewellery? = null) {
        if (item != null) {
            _uiState.update { 
                it.copy(
                    showAddModal = true, 
                    form = JewelleryForm(
                        id = item.id, name = item.name, category = item.category, metal = item.metal, karat = item.karat, notes = item.notes,
                        acquisitionType = item.acquisitionType, weightVori = if (item.weightVori > 0) item.weightVori.toString() else "",
                        weightAna = if (item.weightAna > 0) item.weightAna.toString() else "", weightRoti = if (item.weightRoti > 0) item.weightRoti.toString() else "",
                        weightPoint = if (item.weightPoint > 0) item.weightPoint.toString() else "", purchaseDate = item.purchaseDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
                        purchaseTotal = item.purchaseTotal?.toString() ?: "", recordTransaction = false
                    )
                )
            }
        } else {
            _uiState.update { it.copy(showAddModal = true, form = JewelleryForm(txAccountId = rawAccounts.firstOrNull()?.id ?: "")) }
        }
    }
    fun closeAddModal() = _uiState.update { it.copy(showAddModal = false) }
    fun updateForm(update: (JewelleryForm) -> JewelleryForm) = _uiState.update { it.copy(form = update(it.form)) }

    fun openSellModal(item: Jewellery) = _uiState.update { it.copy(showSellModal = true, selectedItem = item, sellForm = SellJewelleryForm(saleAmount = item.currentZakatValue?.toString() ?: "", accountId = rawAccounts.firstOrNull()?.id ?: "")) }
    fun closeSellModal() = _uiState.update { it.copy(showSellModal = false, selectedItem = null) }
    fun updateSellForm(update: (SellJewelleryForm) -> SellJewelleryForm) = _uiState.update { it.copy(sellForm = update(it.sellForm)) }

    fun openDeleteConfirm(item: Jewellery) = _uiState.update { it.copy(showDeleteConfirm = true, selectedItem = item) }
    fun closeDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = false, selectedItem = null) }

    private suspend fun ensureRedemptionCategory(uid: String): String {
        val snap = db.collection("users").document(uid).collection("categories")
            .whereEqualTo("name", "Jewellery Redemption").get().await()
        if (!snap.isEmpty) return snap.documents.first().id
        val ref = db.collection("users").document(uid).collection("categories").add(
            mapOf("name" to "Jewellery Redemption", "type" to "Both", "color" to "#F59E0B", "isSystem" to true, "createdAt" to FieldValue.serverTimestamp())
        ).await()
        return ref.id
    }

    fun saveJewellery() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.form
        
        if (form.name.isBlank()) { emitEvent(JewelleryEvent.ShowToast("Item name is required", true)); return }
        
        val vori = form.weightVori.toIntOrNull() ?: 0
        val ana = form.weightAna.toIntOrNull() ?: 0
        val roti = form.weightRoti.toIntOrNull() ?: 0
        val pt = form.weightPoint.toIntOrNull() ?: 0
        val grams = calculateGrams(vori, ana, roti, pt)
        
        if (grams <= 0) { emitEvent(JewelleryEvent.ShowToast("Weight must be greater than 0", true)); return }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val data = mutableMapOf<String, Any?>(
                    "name" to form.name.trim(), "category" to form.category, "metal" to form.metal, "karat" to form.karat,
                    "notes" to form.notes.trim(), "acquisitionType" to form.acquisitionType,
                    "weightVori" to vori, "weightAna" to ana, "weightRoti" to roti, "weightPoint" to pt, "weightGrams" to grams,
                    "purchaseDate" to form.purchaseDate, "updatedAt" to FieldValue.serverTimestamp()
                )

                var finalCost = 0.0
                if (form.acquisitionType == "purchased") {
                    if (form.usePurchaseManual) {
                        finalCost = form.purchaseTotal.toDoubleOrNull() ?: 0.0
                        data["purchaseTotal"] = if (finalCost > 0) finalCost else null
                    } else {
                        val basePrice = (form.purchaseGoldPrice.toDoubleOrNull() ?: 0.0) * grams
                        val vat = basePrice * ((form.purchaseVat.toDoubleOrNull() ?: 0.0) / 100)
                        val making = basePrice * ((form.purchaseMaking.toDoubleOrNull() ?: 0.0) / 100)
                        val oth = form.purchaseOther.toDoubleOrNull() ?: 0.0
                        finalCost = basePrice + vat + making + oth
                        data["purchaseTotal"] = if (finalCost > 0) finalCost else null
                    }
                }

                val ref = db.collection("users").document(uid).collection("jewellery")
                if (form.id != null) {
                    ref.document(form.id).set(data, SetOptions.merge()).await()
                    emitEvent(JewelleryEvent.ShowToast("Jewellery updated successfully"))
                } else {
                    data["status"] = "active"
                    data["createdAt"] = FieldValue.serverTimestamp()
                    val newDoc = ref.add(data).await()

                    if (form.recordTransaction && form.txAccountId.isNotBlank() && finalCost > 0) {
                        val acc = rawAccounts.find { it.id == form.txAccountId }
                        if (acc != null && acc.balance >= finalCost) {
                            db.collection("users").document(uid).collection("accounts").document(acc.id)
                                .update("balance", acc.balance - finalCost).await()
                            val catId = ensureRedemptionCategory(uid)
                            val txRef = db.collection("users").document(uid).collection("transactions").add(
                                mapOf(
                                    "type" to "Expense", "amount" to finalCost, "accountId" to acc.id,
                                    "categoryId" to catId, "description" to "Jewellery purchased: ${form.name}",
                                    "date" to form.purchaseDate, "source" to "jewellery_purchase", "jewelleryId" to newDoc.id,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                            newDoc.update("purchaseTransactionId", txRef.id).await()
                            emitEvent(JewelleryEvent.ShowToast("Jewellery added & expense recorded!"))
                        } else {
                            emitEvent(JewelleryEvent.ShowToast("Added, but insufficient funds to record transaction", true))
                        }
                    } else {
                        emitEvent(JewelleryEvent.ShowToast("Jewellery added successfully"))
                    }
                }
                closeAddModal()
            } catch (e: Exception) {
                emitEvent(JewelleryEvent.ShowToast("Save failed: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun submitSale() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.sellForm
        val item = _uiState.value.selectedItem ?: return

        val amount = form.saleAmount.toDoubleOrNull()
        if (amount == null || amount <= 0) { emitEvent(JewelleryEvent.ShowToast("Enter a valid sale amount", true)); return }
        val acc = rawAccounts.find { it.id == form.accountId }
        if (acc == null) { emitEvent(JewelleryEvent.ShowToast("Select an account", true)); return }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val catId = ensureRedemptionCategory(uid)
                val txRef = db.collection("users").document(uid).collection("transactions").add(
                    mapOf(
                        "type" to "Income", "amount" to amount, "accountId" to acc.id,
                        "categoryId" to catId, "description" to "Jewellery sold: ${item.name}",
                        "date" to form.saleDate, "source" to "jewellery_sale", "jewelleryId" to item.id,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                db.collection("users").document(uid).collection("accounts").document(acc.id)
                    .update("balance", acc.balance + amount).await()

                db.collection("users").document(uid).collection("jewellery").document(item.id).update(
                    mapOf(
                        "status" to "sold", "soldAt" to form.saleDate, "soldPrice" to amount,
                        "soldNotes" to form.notes.trim(), "soldToAccountId" to acc.id,
                        "saleTransactionId" to txRef.id, "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                emitEvent(JewelleryEvent.ShowToast("${item.name} sold! Money added to account."))
                closeSellModal()
            } catch (e: Exception) {
                emitEvent(JewelleryEvent.ShowToast("Failed to process sale: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteJewellery() {
        val uid = auth.currentUser?.uid ?: return
        val item = _uiState.value.selectedItem ?: return

        viewModelScope.launch {
            try {
                val history = db.collection("users").document(uid).collection("jewellery").document(item.id).collection("priceHistory").get().await()
                history.documents.forEach { it.reference.delete() }
                db.collection("users").document(uid).collection("jewellery").document(item.id).delete().await()
                emitEvent(JewelleryEvent.ShowToast("Jewellery deleted"))
                closeDeleteConfirm()
            } catch (e: Exception) {
                emitEvent(JewelleryEvent.ShowToast("Error deleting: ${e.message}", true))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        jewListener?.remove()
        accListener?.remove()
    }

    private fun emitEvent(event: JewelleryEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}