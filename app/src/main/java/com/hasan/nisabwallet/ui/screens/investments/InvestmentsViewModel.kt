package com.hasan.nisabwallet.ui.screens.investments

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

// ─── Constants & Helpers ───
object InvestmentConstants {
    const val STOCK = "stock"
    const val MUTUAL_FUND = "mutual_fund"
    const val DPS = "dps"
    const val FDR = "fdr"
    const val SAVINGS_CERTIFICATE = "savings_certificate"
    const val BOND = "bond"
    const val PPF = "ppf"
    const val PENSION_FUND = "pension_fund"
    const val CRYPTO = "crypto"
    const val REAL_ESTATE = "real_estate"
    const val GOLD = "gold"
    const val OTHER = "other"

    val TYPES = listOf(STOCK, MUTUAL_FUND, DPS, FDR, SAVINGS_CERTIFICATE, BOND, PPF, PENSION_FUND, CRYPTO, REAL_ESTATE, GOLD, OTHER)

    fun getLabel(type: String): String = when(type) {
        STOCK -> "Stock"
        MUTUAL_FUND -> "Mutual Fund"
        DPS -> "DPS (Deposit Pension Scheme)"
        FDR -> "FDR (Fixed Deposit)"
        SAVINGS_CERTIFICATE -> "Savings Certificate"
        BOND -> "Bond"
        PPF -> "PPF (Public Provident Fund)"
        PENSION_FUND -> "Pension Fund"
        CRYPTO -> "Cryptocurrency"
        REAL_ESTATE -> "Real Estate"
        GOLD -> "Gold"
        else -> "Other"
    }

    fun getColor(type: String): String = when(type) {
        STOCK -> "#3B82F6"
        MUTUAL_FUND -> "#10B981"
        DPS -> "#F59E0B"
        FDR -> "#8B5CF6"
        SAVINGS_CERTIFICATE -> "#EC4899"
        BOND -> "#6366F1"
        PPF -> "#14B8A6"
        PENSION_FUND -> "#F97316"
        CRYPTO -> "#EAB308"
        REAL_ESTATE -> "#84CC16"
        GOLD -> "#FBBF24"
        else -> "#6B7280"
    }
}

// ─── Data Models ───
data class InvestmentAccount(val id: String, val name: String, val balance: Double)

data class Investment(
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
    val createdAtMillis: Long = 0L
) {
    val totalInvested get() = purchasePrice * quantity
    val totalCurrentValue get() = currentValue * quantity
    val absoluteReturn get() = totalCurrentValue - totalInvested
    val percentageReturn get() = if (totalInvested > 0) (absoluteReturn / totalInvested) * 100 else 0.0
}

data class PortfolioSummary(
    val totalInvested: Double = 0.0,
    val totalCurrentValue: Double = 0.0,
    val absoluteReturn: Double = 0.0,
    val percentageReturn: Double = 0.0,
    val totalDividends: Double = 0.0,
    val activeCount: Int = 0
)

data class AssetAllocation(
    val type: String,
    val value: Double,
    val percentage: Double
)

data class InvestmentForm(
    val id: String? = null,
    val type: String = InvestmentConstants.STOCK,
    val name: String = "",
    val accountId: String = "",
    val symbol: String = "",
    val exchange: String = "",
    val institution: String = "",
    val purchaseDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val purchasePrice: String = "",
    val quantity: String = "1",
    val currentValue: String = "",
    val status: String = "active",
    val interestRate: String = "",
    val maturityDate: String = "",
    val maturityAmount: String = "",
    val monthlyAmount: String = "",
    val period: String = "",
    val certificateNumber: String = "",
    val issueDate: String = "",
    val address: String = "",
    val propertyType: String = "",
    val category: String = "growth",
    val riskLevel: String = "medium",
    val notes: String = ""
)

data class InvestmentsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val syncStatus: String = "Connecting...",
    val investments: List<Investment> = emptyList(),
    val filteredInvestments: List<Investment> = emptyList(),
    val accounts: List<InvestmentAccount> = emptyList(),
    
    val summary: PortfolioSummary = PortfolioSummary(),
    val allocations: List<AssetAllocation> = emptyList(),

    val filterType: String = "all",
    val filterStatus: String = "active",
    val sortBy: String = "date",
    val searchQuery: String = "",
    val recordTransaction: Boolean = false,

    val showModal: Boolean = false,
    val form: InvestmentForm = InvestmentForm()
)

sealed class InvestmentsEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : InvestmentsEvent()
}

@HiltViewModel
class InvestmentsViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvestmentsUiState())
    val uiState: StateFlow<InvestmentsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<InvestmentsEvent>()
    val events = _events.asSharedFlow()

    private var invListener: ListenerRegistration? = null
    private var accListener: ListenerRegistration? = null
    
    private var rawInvestments = emptyList<Investment>()
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
                    if (_uiState.value.form.accountId.isBlank() && rawAccounts.isNotEmpty()) {
                        _uiState.update { it.copy(form = it.form.copy(accountId = rawAccounts.first().id)) }
                    }
                    combineAndEmit(null)
                }
            }

        invListener = db.collection("users").document(uid).collection("investments")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    emitEvent(InvestmentsEvent.ShowToast("Sync error: ${e.message}", true))
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val status = when {
                        snap.metadata.hasPendingWrites() -> "Syncing..."
                        snap.metadata.isFromCache() -> "Offline (Cached)"
                        else -> "Synced"
                    }
                    rawInvestments = snap.documents.map { d ->
                        Investment(
                            id = d.id,
                            type = d.getString("type") ?: InvestmentConstants.STOCK,
                            name = d.getString("name") ?: "",
                            symbol = d.getString("symbol") ?: "",
                            exchange = d.getString("exchange") ?: "",
                            institution = d.getString("institution") ?: "",
                            purchaseDate = d.getString("purchaseDate") ?: "",
                            purchasePrice = d.getDouble("purchasePrice") ?: 0.0,
                            quantity = d.getDouble("quantity") ?: 1.0,
                            currentValue = d.getDouble("currentValue") ?: 0.0,
                            status = d.getString("status") ?: "active",
                            interestRate = d.getDouble("interestRate"),
                            maturityDate = d.getString("maturityDate") ?: "",
                            maturityAmount = d.getDouble("maturityAmount"),
                            monthlyAmount = d.getDouble("monthlyAmount"),
                            period = d.getLong("period")?.toInt(),
                            certificateNumber = d.getString("certificateNumber") ?: "",
                            issueDate = d.getString("issueDate") ?: "",
                            address = d.getString("address") ?: "",
                            propertyType = d.getString("propertyType") ?: "",
                            category = d.getString("category") ?: "growth",
                            riskLevel = d.getString("riskLevel") ?: "medium",
                            notes = d.getString("notes") ?: "",
                            totalDividends = d.getDouble("totalDividends") ?: 0.0,
                            createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        )
                    }
                    combineAndEmit(status)
                }
            }
    }

    private fun combineAndEmit(newSyncStatus: String?) {
        val state = _uiState.value
        
        var totalInv = 0.0
        var totalCur = 0.0
        var totalDiv = 0.0
        var activeCount = 0
        val typeValues = mutableMapOf<String, Double>()

        rawInvestments.forEach { inv ->
            if (inv.status == "active") {
                totalInv += inv.totalInvested
                totalCur += inv.totalCurrentValue
                totalDiv += inv.totalDividends
                activeCount++
                typeValues[inv.type] = (typeValues[inv.type] ?: 0.0) + inv.totalCurrentValue
            }
        }

        val absRet = totalCur - totalInv
        val pctRet = if (totalInv > 0) (absRet / totalInv) * 100 else 0.0

        val summary = PortfolioSummary(totalInv, totalCur, absRet, pctRet, totalDiv, activeCount)
        
        val allocs = typeValues.map { (type, value) ->
            AssetAllocation(type, value, if (totalCur > 0) (value / totalCur) * 100 else 0.0)
        }.sortedByDescending { it.value }

        var filtered = rawInvestments
        if (state.filterType != "all") filtered = filtered.filter { it.type == state.filterType }
        if (state.filterStatus != "all") filtered = filtered.filter { it.status == state.filterStatus }
        if (state.searchQuery.isNotBlank()) {
            filtered = filtered.filter { 
                it.name.contains(state.searchQuery, true) || it.symbol.contains(state.searchQuery, true) 
            }
        }

        filtered = when (state.sortBy) {
            "return" -> filtered.sortedByDescending { it.percentageReturn }
            "value" -> filtered.sortedByDescending { it.totalCurrentValue }
            else -> filtered.sortedByDescending { it.purchaseDate }
        }

        _uiState.update { 
            it.copy(
                isLoading = false,
                investments = rawInvestments,
                filteredInvestments = filtered,
                accounts = rawAccounts,
                summary = summary,
                allocations = allocs,
                syncStatus = newSyncStatus ?: it.syncStatus
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        invListener?.remove()
        accListener?.remove()
    }

    fun setFilterType(t: String) { _uiState.update { it.copy(filterType = t) }; combineAndEmit(null) }
    fun setFilterStatus(s: String) { _uiState.update { it.copy(filterStatus = s) }; combineAndEmit(null) }
    fun setSortBy(s: String) { _uiState.update { it.copy(sortBy = s) }; combineAndEmit(null) }
    fun setSearchQuery(q: String) { _uiState.update { it.copy(searchQuery = q) }; combineAndEmit(null) }

    fun openAddModal() {
        _uiState.update { it.copy(showModal = true, form = InvestmentForm(accountId = rawAccounts.firstOrNull()?.id ?: "")) }
    }

    fun openEditModal(inv: Investment) {
        _uiState.update { 
            it.copy(
                showModal = true, 
                form = InvestmentForm(
                    id = inv.id,
                    type = inv.type,
                    name = inv.name,
                    accountId = "", // Block account change on edit
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

    fun closeModal() {
        _uiState.update { it.copy(showModal = false, form = InvestmentForm(accountId = rawAccounts.firstOrNull()?.id ?: "")) }
    }

    fun updateForm(update: (InvestmentForm) -> InvestmentForm) {
        _uiState.update { it.copy(form = update(it.form)) }
    }

    private suspend fun ensureCategory(uid: String, name: String, type: String, color: String): String {
        val snap = db.collection("users").document(uid).collection("categories")
            .whereEqualTo("name", name).whereEqualTo("type", type).get().await()
        if (!snap.isEmpty) return snap.documents.first().id
        val ref = db.collection("users").document(uid).collection("categories").add(
            mapOf("name" to name, "type" to type, "color" to color, "isSystem" to true, "createdAt" to FieldValue.serverTimestamp())
        ).await()
        return ref.id
    }

    fun saveInvestment() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.form
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val batch = db.batch()
                val invRef = if (form.id != null) {
                    db.collection("users").document(uid).collection("investments").document(form.id)
                } else {
                    db.collection("users").document(uid).collection("investments").document()
                }

                // Map your form fields to a map here (abbreviated for length)
                val invData = mutableMapOf<String, Any>(
                    "name" to form.name,
                    "type" to form.type,
                    "purchasePrice" to (form.purchasePrice.toDoubleOrNull() ?: 0.0),
                    "quantity" to (form.quantity.toDoubleOrNull() ?: 1.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                if (form.id != null) {
                    batch.update(invRef, invData)
                } else {
                    invData["createdAt"] = FieldValue.serverTimestamp()
                    batch.set(invRef, invData)
                    
                    // Deduct from account and create transaction if requested
                    if (form.accountId.isNotBlank()) {
                        val amount = (form.purchasePrice.toDoubleOrNull() ?: 0.0) * (form.quantity.toDoubleOrNull() ?: 1.0)
                        val accRef = db.collection("users").document(uid).collection("accounts").document(form.accountId)
                        val currentBal = _uiState.value.accounts.find { it.id == form.accountId }?.balance ?: 0.0
                        
                        batch.update(accRef, "balance", currentBal - amount)
                        
                        val txRef = db.collection("users").document(uid).collection("transactions").document()
                        batch.set(txRef, mapOf(
                            "type" to "Expense",
                            "amount" to amount,
                            "accountId" to form.accountId,
                            "description" to "Investment: ${form.name}",
                            "date" to form.purchaseDate,
                            "createdAt" to FieldValue.serverTimestamp()
                        ))
                    }
                }
                
                batch.commit() // Instant offline commit
                emitEvent(InvestmentsEvent.ShowToast("Investment saved"))
                closeModal()
            } catch (e: Exception) {
                emitEvent(InvestmentsEvent.ShowToast("Failed to save", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun emitEvent(event: InvestmentsEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}