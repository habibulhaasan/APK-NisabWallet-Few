package com.hasan.nisabwallet.ui.screens.investments.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.hasan.nisabwallet.ui.screens.investments.Investment
import com.hasan.nisabwallet.ui.screens.investments.InvestmentConstants
import com.hasan.nisabwallet.ui.screens.investments.InvestmentForm
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ─── Data Models ───
data class InvestmentAccount(val id: String, val name: String, val balance: Double)

data class DividendItem(
    val date: String = "",
    val amount: Double = 0.0,
    val type: String = "dividend",
    val notes: String = "",
    val recordedAt: String = ""
)

data class DividendForm(
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val amount: String = "",
    val type: String = "dividend",
    val accountId: String = "",
    val notes: String = ""
)

// Extended Investment model to include dividends array
data class DetailedInvestment(
    val id: String = "",
    val type: String = InvestmentConstants.STOCK,
    val name: String = "",
    val symbol: String = "",
    val exchange: String = "",
    val institution: String = "",
    val purchaseDate: String = "",
    val purchasePrice: Double = 0.0,
    val quantity: Double = 1.0,
    val currentValue: Double = 0.0,
    val status: String = "active",
    val interestRate: Double? = null,
    val maturityDate: String = "",
    val maturityAmount: Double? = null,
    val monthlyAmount: Double? = null,
    val period: Int? = null,
    val certificateNumber: String = "",
    val issueDate: String = "",
    val address: String = "",
    val propertyType: String = "",
    val category: String = "growth",
    val riskLevel: String = "medium",
    val notes: String = "",
    val totalDividends: Double = 0.0,
    val dividends: List<DividendItem> = emptyList(),
    val lastUpdated: String = "",
    val createdAtMillis: Long = 0L
) {
    val totalInvested get() = purchasePrice * quantity
    val totalCurrentValue get() = currentValue * quantity
    val absoluteReturn get() = totalCurrentValue - totalInvested
    val percentageReturn get() = if (totalInvested > 0) (absoluteReturn / totalInvested) * 100 else 0.0
}

data class InvestmentDetailUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val investment: DetailedInvestment? = null,
    val syncStatus: String = "Connecting...",
    val accounts: List<InvestmentAccount> = emptyList(),
    
    val showEditModal: Boolean = false,
    val editForm: InvestmentForm = InvestmentForm(),
    
    val showDividendModal: Boolean = false,
    val dividendForm: DividendForm = DividendForm(),
    
    val showDeleteConfirm: Boolean = false
)

sealed class InvestmentDetailEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : InvestmentDetailEvent()
    object NavigateBack : InvestmentDetailEvent()
}

@HiltViewModel
class InvestmentDetailViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val investmentId: String = checkNotNull(savedStateHandle["investmentId"])

    private val _uiState = MutableStateFlow(InvestmentDetailUiState())
    val uiState: StateFlow<InvestmentDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<InvestmentDetailEvent>()
    val events = _events.asSharedFlow()

    private var invListener: ListenerRegistration? = null
    private var accListener: ListenerRegistration? = null
    
    private var rawAccounts = emptyList<InvestmentAccount>()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        accListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawAccounts = snap.documents.map { d ->
                        InvestmentAccount(d.id, d.getString("name") ?: "", d.getDouble("balance") ?: 0.0)
                    }
                    if (_uiState.value.dividendForm.accountId.isBlank() && rawAccounts.isNotEmpty()) {
                        _uiState.update { it.copy(dividendForm = it.dividendForm.copy(accountId = rawAccounts.first().id)) }
                    }
                    _uiState.update { it.copy(accounts = rawAccounts) }
                }
            }

        invListener = db.collection("users").document(uid).collection("investments").document(investmentId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    emitEvent(InvestmentDetailEvent.ShowToast("Sync error: ${e.message}", true))
                    return@addSnapshotListener
                }
                
                if (snap != null && snap.exists()) {
                    val status = when {
                        snap.metadata.hasPendingWrites() -> "Syncing..."
                        snap.metadata.isFromCache() -> "Offline (Cached)"
                        else -> "Synced"
                    }

                    val rawDividends = snap.get("dividends") as? List<Map<String, Any>> ?: emptyList()
                    val parsedDividends = rawDividends.map { r ->
                        DividendItem(
                            date = r["date"] as? String ?: "",
                            amount = (r["amount"] as? Number)?.toDouble() ?: 0.0,
                            type = r["type"] as? String ?: "dividend",
                            notes = r["notes"] as? String ?: "",
                            recordedAt = r["recordedAt"] as? String ?: ""
                        )
                    }.sortedByDescending { it.date }

                    val inv = DetailedInvestment(
                        id = snap.id,
                        type = snap.getString("type") ?: InvestmentConstants.STOCK,
                        name = snap.getString("name") ?: "",
                        symbol = snap.getString("symbol") ?: "",
                        exchange = snap.getString("exchange") ?: "",
                        institution = snap.getString("institution") ?: "",
                        purchaseDate = snap.getString("purchaseDate") ?: "",
                        purchasePrice = snap.getDouble("purchasePrice") ?: 0.0,
                        quantity = snap.getDouble("quantity") ?: 1.0,
                        currentValue = snap.getDouble("currentValue") ?: 0.0,
                        status = snap.getString("status") ?: "active",
                        interestRate = snap.getDouble("interestRate"),
                        maturityDate = snap.getString("maturityDate") ?: "",
                        maturityAmount = snap.getDouble("maturityAmount"),
                        monthlyAmount = snap.getDouble("monthlyAmount"),
                        period = snap.getLong("period")?.toInt(),
                        certificateNumber = snap.getString("certificateNumber") ?: "",
                        issueDate = snap.getString("issueDate") ?: "",
                        address = snap.getString("address") ?: "",
                        propertyType = snap.getString("propertyType") ?: "",
                        category = snap.getString("category") ?: "growth",
                        riskLevel = snap.getString("riskLevel") ?: "medium",
                        notes = snap.getString("notes") ?: "",
                        totalDividends = snap.getDouble("totalDividends") ?: 0.0,
                        dividends = parsedDividends,
                        lastUpdated = snap.getString("lastUpdated") ?: "",
                        createdAtMillis = snap.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )

                    _uiState.update { it.copy(isLoading = false, investment = inv, syncStatus = status) }
                } else {
                    emitEvent(InvestmentDetailEvent.ShowToast("Investment not found", true))
                    emitEvent(InvestmentDetailEvent.NavigateBack)
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        invListener?.remove()
        accListener?.remove()
    }

    // ─── Actions ───

    fun openDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = true) }
    fun closeDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = false) }

    fun deleteInvestment() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("investments").document(investmentId).delete().await()
                emitEvent(InvestmentDetailEvent.ShowToast("Investment deleted"))
                emitEvent(InvestmentDetailEvent.NavigateBack)
            } catch (e: Exception) {
                emitEvent(InvestmentDetailEvent.ShowToast("Failed to delete: ${e.message}", true))
            }
        }
    }

    // ─── Dividend Form Logic ───
    fun openDividendModal() = _uiState.update { it.copy(showDividendModal = true, dividendForm = DividendForm(accountId = rawAccounts.firstOrNull()?.id ?: "")) }
    fun closeDividendModal() = _uiState.update { it.copy(showDividendModal = false) }
    fun updateDividendForm(update: (DividendForm) -> DividendForm) = _uiState.update { it.copy(dividendForm = update(it.dividendForm)) }

    private suspend fun ensureCategory(uid: String, name: String, type: String, color: String): String {
        val snap = db.collection("users").document(uid).collection("categories")
            .whereEqualTo("name", name).whereEqualTo("type", type).get().await()
        if (!snap.isEmpty) return snap.documents.first().id
        val ref = db.collection("users").document(uid).collection("categories").add(
            mapOf("name" to name, "type" to type, "color" to color, "isSystem" to true, "createdAt" to FieldValue.serverTimestamp())
        ).await()
        return ref.id
    }

    fun addDividend() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.dividendForm
        val inv = _uiState.value.investment ?: return

        val amt = form.amount.toDoubleOrNull()
        if (amt == null || amt <= 0) {
            emitEvent(InvestmentDetailEvent.ShowToast("Please enter a valid amount", true))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val newDividend = mapOf(
                    "date" to form.date,
                    "amount" to amt,
                    "type" to form.type,
                    "notes" to form.notes.trim(),
                    "recordedAt" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                )
                
                val currentTotal = inv.totalDividends + amt
                
                db.collection("users").document(uid).collection("investments").document(investmentId)
                    .update(
                        "dividends", FieldValue.arrayUnion(newDividend),
                        "totalDividends", currentTotal,
                        "updatedAt", FieldValue.serverTimestamp()
                    ).await()
                
                // DOUBLE ENTRY: Add to Account & Log Income Transaction
                val acc = rawAccounts.find { it.id == form.accountId }
                if (acc != null) {
                    db.collection("users").document(uid).collection("accounts").document(acc.id)
                        .update("balance", acc.balance + amt).await()
                        
                    val catId = ensureCategory(uid, "Investment Return", "Income", "#06B6D4")
                    db.collection("users").document(uid).collection("transactions").add(
                        mapOf(
                            "type" to "Income", "amount" to amt, "accountId" to acc.id,
                            "categoryId" to catId, "description" to "${form.type.replaceFirstChar { it.uppercase() }} from ${inv.name}",
                            "date" to form.date, "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                emitEvent(InvestmentDetailEvent.ShowToast("Dividend/Interest added successfully"))
                closeDividendModal()
            } catch (e: Exception) {
                emitEvent(InvestmentDetailEvent.ShowToast("Failed to add dividend: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // ─── Edit Modal Logic ───
    fun openEditModal() {
        val inv = _uiState.value.investment ?: return
        _uiState.update { 
            it.copy(
                showEditModal = true, 
                editForm = InvestmentForm(
                    id = inv.id,
                    type = inv.type,
                    name = inv.name,
                    accountId = "",
                    symbol = inv.symbol,
                    exchange = inv.exchange,
                    institution = inv.institution,
                    purchaseDate = inv.purchaseDate,
                    purchasePrice = inv.purchasePrice.toString(),
                    quantity = inv.quantity.toString(),
                    currentValue = inv.currentValue.toString(),
                    status = inv.status,
                    interestRate = inv.interestRate?.toString() ?: "",
                    maturityDate = inv.maturityDate,
                    maturityAmount = inv.maturityAmount?.toString() ?: "",
                    monthlyAmount = inv.monthlyAmount?.toString() ?: "",
                    period = inv.period?.toString() ?: "",
                    certificateNumber = inv.certificateNumber,
                    issueDate = inv.issueDate,
                    address = inv.address,
                    propertyType = inv.propertyType,
                    category = inv.category,
                    riskLevel = inv.riskLevel,
                    notes = inv.notes
                )
            )
        }
    }

    fun closeEditModal() = _uiState.update { it.copy(showEditModal = false) }
    fun updateEditForm(update: (InvestmentForm) -> InvestmentForm) = _uiState.update { it.copy(editForm = update(it.editForm)) }

    fun updateInvestment() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.editForm
        
        if (form.name.trim().isBlank()) {
            emitEvent(InvestmentDetailEvent.ShowToast("Please enter investment name", true))
            return
        }

        val price = form.purchasePrice.toDoubleOrNull() ?: return
        val qty = form.quantity.toDoubleOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val data = mutableMapOf<String, Any>(
                    "type" to form.type,
                    "name" to form.name.trim(),
                    "purchaseDate" to form.purchaseDate,
                    "purchasePrice" to price,
                    "quantity" to qty,
                    "currentValue" to (form.currentValue.toDoubleOrNull() ?: price),
                    "status" to form.status,
                    "category" to form.category,
                    "riskLevel" to form.riskLevel,
                    "notes" to form.notes.trim(),
                    "lastUpdated" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                when (form.type) {
                    InvestmentConstants.STOCK -> {
                        data["symbol"] = form.symbol.trim().uppercase()
                        data["exchange"] = form.exchange.trim()
                    }
                    InvestmentConstants.FDR, InvestmentConstants.DPS -> {
                        data["institution"] = form.institution.trim()
                        form.interestRate.toDoubleOrNull()?.let { data["interestRate"] = it }
                        data["maturityDate"] = form.maturityDate
                        form.maturityAmount.toDoubleOrNull()?.let { data["maturityAmount"] = it }
                        if (form.type == InvestmentConstants.DPS) {
                            form.monthlyAmount.toDoubleOrNull()?.let { data["monthlyAmount"] = it }
                            form.period.toIntOrNull()?.let { data["period"] = it }
                        }
                    }
                    InvestmentConstants.SAVINGS_CERTIFICATE -> {
                        data["certificateNumber"] = form.certificateNumber.trim()
                        data["issueDate"] = form.issueDate
                        data["maturityDate"] = form.maturityDate
                        form.interestRate.toDoubleOrNull()?.let { data["interestRate"] = it }
                        form.maturityAmount.toDoubleOrNull()?.let { data["maturityAmount"] = it }
                    }
                    InvestmentConstants.REAL_ESTATE -> {
                        data["address"] = form.address.trim()
                        data["propertyType"] = form.propertyType.trim()
                    }
                }

                db.collection("users").document(uid).collection("investments").document(investmentId)
                    .set(data, SetOptions.merge()).await()
                    
                emitEvent(InvestmentDetailEvent.ShowToast("Investment updated"))
                closeEditModal()
            } catch (e: Exception) {
                emitEvent(InvestmentDetailEvent.ShowToast("Update failed: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun emitEvent(event: InvestmentDetailEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}