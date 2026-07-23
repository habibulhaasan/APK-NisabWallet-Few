package com.hasan.nisabwallet.ui.screens.tax.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.hasan.nisabwallet.core.util.TaxCategoryUtils
import com.hasan.nisabwallet.ui.screens.tax.TaxYearRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TaxMapping(
    val userCategoryId: String = "",
    val userCategoryName: String = "",
    val taxCategoryId: String = ""
)

data class TransactionRecord(
    val id: String = "",
    val amount: Double = 0.0,
    val type: String = "",
    val categoryId: String = "",
    val date: String = "",
    val description: String = "",
    val isTransfer: Boolean = false,
    val source: String? = null,
    val goalId: String? = null,
    val lendingId: String? = null,
    val loanId: String? = null,
    val isInterest: Boolean = false
)

data class AccountRecord(
    val id: String = "",
    val name: String = "",
    val balance: Double = 0.0,
    val type: String = "Bank"
)

data class TaxAsset(
    val id: String = "",
    val assetType: String = "",
    val description: String = "",
    val currentValue: Double = 0.0
)

data class TaxLiability(
    val id: String = "",
    val liabilityType: String = "",
    val description: String = "",
    val principal: Double = 0.0,
    val lender: String = ""
)

data class TaxProfile(
    val taxpayerName: String = "",
    val tin: String = ""
)

data class TransactionAnalysisResult(
    val income: Map<String, Double> = emptyMap(),
    val expenses: Map<String, Double> = emptyMap(),
    val personal: Map<String, Double> = emptyMap(),
    val taxPaid: Map<String, Double> = emptyMap(),
    val investments: Map<String, Double> = emptyMap(),
    val loanRepayment: Map<String, Double> = emptyMap(),
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0
)

data class TaxDetailUiState(
    val isLoading: Boolean = true,
    val isAnalyzing: Boolean = false,
    val isSaving: Boolean = false,
    val taxYear: TaxYearRecord? = null,
    val mappings: List<TaxMapping> = emptyList(),
    val transactions: List<TransactionRecord> = emptyList(),
    val accounts: List<AccountRecord> = emptyList(),
    val assets: List<TaxAsset> = emptyList(),
    val liabilities: List<TaxLiability> = emptyList(),
    val profile: TaxProfile = TaxProfile(),
    val analysis: TransactionAnalysisResult = TransactionAnalysisResult(),
    
    // Modals
    val showAssetModal: Boolean = false,
    val showLiabilityModal: Boolean = false,
    val showProfileModal: Boolean = false,
    val showPdfModal: Boolean = false, // Added for PDF selection
    val editingAsset: TaxAsset? = null,
    val editingLiability: TaxLiability? = null,
    val itemToDelete: Pair<String, Any>? = null // Type to item
)

sealed class TaxDetailEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : TaxDetailEvent()
    object NavigateBack : TaxDetailEvent()
    // Added PDF Generation Event
    data class GeneratePdf(
        val format: String,
        val taxYear: TaxYearRecord,
        val profile: TaxProfile,
        val analysis: TransactionAnalysisResult,
        val assets: List<TaxAsset>,
        val liabilities: List<TaxLiability>
    ) : TaxDetailEvent()
}

@HiltViewModel
class TaxDetailViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val taxYearDocId: String = checkNotNull(savedStateHandle["id"])
    private val _uiState = MutableStateFlow(TaxDetailUiState())
    val uiState: StateFlow<TaxDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TaxDetailEvent>()
    val events = _events.asSharedFlow()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        // 1. Tax Year Doc
        db.collection("users").document(uid).collection("taxYears").document(taxYearDocId)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val record = TaxYearRecord(
                        id = snap.id,
                        incomeYear = snap.getString("incomeYear") ?: "",
                        fiscalYearStart = snap.getString("fiscalYearStart") ?: "",
                        fiscalYearEnd = snap.getString("fiscalYearEnd") ?: "",
                        taxYear = snap.getString("taxYear") ?: "",
                        filingDeadline = snap.getString("filingDeadline") ?: "",
                        status = snap.getString("status") ?: "draft",
                        totalIncome = snap.getDouble("totalIncome") ?: 0.0,
                        totalExpenses = snap.getDouble("totalExpenses") ?: 0.0,
                        totalAssets = snap.getDouble("totalAssets") ?: 0.0,
                        netWorth = snap.getDouble("netWorth") ?: 0.0
                    )
                    _uiState.update { it.copy(taxYear = record) }
                    recalculateAnalysis()
                } else {
                    emitEvent(TaxDetailEvent.ShowToast("Tax year record not found", true))
                    emitEvent(TaxDetailEvent.NavigateBack)
                }
            }

        // 2. Category Mappings
        db.collection("users").document(uid).collection("taxCategoryMappings")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val maps = snap.documents.map {
                        TaxMapping(
                            userCategoryId = it.getString("userCategoryId") ?: "",
                            userCategoryName = it.getString("userCategoryName") ?: "",
                            taxCategoryId = it.getString("taxCategoryId") ?: ""
                        )
                    }
                    _uiState.update { it.copy(mappings = maps) }
                    recalculateAnalysis()
                }
            }

        // 3. Transactions
        db.collection("users").document(uid).collection("transactions")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val txs = snap.documents.map { d ->
                        TransactionRecord(
                            id = d.id,
                            amount = d.getDouble("amount") ?: 0.0,
                            type = d.getString("type") ?: "",
                            categoryId = d.getString("categoryId") ?: "",
                            date = d.getString("date") ?: "",
                            description = d.getString("description") ?: "",
                            isTransfer = d.getBoolean("isTransfer") ?: false,
                            source = d.getString("source"),
                            goalId = d.getString("goalId"),
                            lendingId = d.getString("lendingId"),
                            loanId = d.getString("loanId"),
                            isInterest = d.getBoolean("isInterest") ?: false
                        )
                    }
                    _uiState.update { it.copy(transactions = txs) }
                    recalculateAnalysis()
                }
            }

        // 4. Accounts
        db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val accs = snap.documents.map {
                        AccountRecord(
                            id = it.id,
                            name = it.getString("name") ?: "",
                            balance = it.getDouble("balance") ?: 0.0,
                            type = it.getString("type") ?: "Bank"
                        )
                    }
                    _uiState.update { it.copy(accounts = accs) }
                }
            }

        // 5. Tax Assets
        db.collection("users").document(uid).collection("taxAssets")
            .whereEqualTo("taxYearId", taxYearDocId)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val assets = snap.documents.map {
                        TaxAsset(
                            id = it.id,
                            assetType = it.getString("assetType") ?: "",
                            description = it.getString("description") ?: "",
                            currentValue = it.getDouble("currentValue") ?: 0.0
                        )
                    }
                    _uiState.update { it.copy(assets = assets) }
                }
            }

        // 6. Tax Liabilities
        db.collection("users").document(uid).collection("taxLiabilities")
            .whereEqualTo("taxYearId", taxYearDocId)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val liabilities = snap.documents.map {
                        TaxLiability(
                            id = it.id,
                            liabilityType = it.getString("liabilityType") ?: "",
                            description = it.getString("description") ?: "",
                            principal = it.getDouble("principal") ?: 0.0,
                            lender = it.getString("lender") ?: ""
                        )
                    }
                    _uiState.update { it.copy(liabilities = liabilities, isLoading = false) }
                }
            }

        // 7. Profile
        db.collection("users").document(uid).collection("settings").document("taxProfile")
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val p = TaxProfile(
                        taxpayerName = snap.getString("taxpayerName") ?: "",
                        tin = snap.getString("tin") ?: ""
                    )
                    _uiState.update { it.copy(profile = p) }
                }
            }
    }

    // ─── Transaction Analysis Algorithm ───
    private fun recalculateAnalysis() {
        val taxYear = _uiState.value.taxYear ?: return
        val txs = _uiState.value.transactions
        val mappings = _uiState.value.mappings

        val taxableTransactions = txs.filter { t ->
            if (t.isTransfer) return@filter false
            if (t.source == "goal_deposit" || t.source == "goal_withdrawal" || t.goalId != null) return@filter false
            if (t.source == "lending_given" || t.source == "lending_payment_received" || t.lendingId != null) return@filter false
            if (t.source == "loan_taken" || t.loanId != null) {
                if (t.source == "loan_payment" && t.isInterest) return@filter true
                return@filter false
            }
            if (t.source == "loan_payment" && !t.isInterest) return@filter false
            true
        }

        val fiscalTransactions = taxableTransactions.filter { t ->
            t.date >= taxYear.fiscalYearStart && t.date <= taxYear.fiscalYearEnd
        }

        val incomeByTaxCat = mutableMapOf<String, Double>()
        val expensesByTaxCat = mutableMapOf<String, Double>()
        val personal = mutableMapOf<String, Double>()
        val taxPaid = mutableMapOf<String, Double>()
        val investments = mutableMapOf<String, Double>()
        val loanRepayment = mutableMapOf<String, Double>()
        var totalIncome = 0.0
        var totalExpenses = 0.0

        fiscalTransactions.forEach { tx ->
            val mapping = mappings.find { it.userCategoryId == tx.categoryId } ?: return@forEach
            val taxCatId = mapping.taxCategoryId
            val amt = tx.amount

            if (tx.type.equals("income", ignoreCase = true)) {
                incomeByTaxCat[taxCatId] = (incomeByTaxCat[taxCatId] ?: 0.0) + amt
                totalIncome += amt
            } else if (tx.type.equals("expense", ignoreCase = true)) {
                expensesByTaxCat[taxCatId] = (expensesByTaxCat[taxCatId] ?: 0.0) + amt
                totalExpenses += amt

                when {
                    TaxCategoryUtils.PERSONAL_EXPENSES.any { it.id == taxCatId } -> personal[taxCatId] = (personal[taxCatId] ?: 0.0) + amt
                    TaxCategoryUtils.TAX_PAID.any { it.id == taxCatId } -> taxPaid[taxCatId] = (taxPaid[taxCatId] ?: 0.0) + amt
                    TaxCategoryUtils.INVESTMENTS.any { it.id == taxCatId } -> investments[taxCatId] = (investments[taxCatId] ?: 0.0) + amt
                    TaxCategoryUtils.LOAN_REPAYMENT.any { it.id == taxCatId } -> loanRepayment[taxCatId] = (loanRepayment[taxCatId] ?: 0.0) + amt
                }
            }
        }

        _uiState.update {
            it.copy(
                analysis = TransactionAnalysisResult(
                    income = incomeByTaxCat,
                    expenses = expensesByTaxCat,
                    personal = personal,
                    taxPaid = taxPaid,
                    investments = investments,
                    loanRepayment = loanRepayment,
                    totalIncome = totalIncome,
                    totalExpenses = totalExpenses
                )
            )
        }
    }

    fun handleAnalyze() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }
            try {
                val totalManualAssets = state.assets.sumOf { it.currentValue }
                val totalAccounts = state.accounts.sumOf { it.balance }
                val totalAssets = totalManualAssets + totalAccounts
                val totalLiabilities = state.liabilities.sumOf { it.principal }
                val netWorth = totalAssets - totalLiabilities

                val updates = mapOf(
                    "totalIncome" to state.analysis.totalIncome,
                    "totalExpenses" to state.analysis.totalExpenses,
                    "totalAssets" to totalAssets,
                    "totalLiabilities" to totalLiabilities,
                    "netWorth" to netWorth,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                db.collection("users").document(uid).collection("taxYears").document(taxYearDocId)
                    .update(updates)

                emitEvent(TaxDetailEvent.ShowToast("Analysis complete! Summary metrics updated."))
            } catch (e: Exception) {
                emitEvent(TaxDetailEvent.ShowToast("Failed to save analysis: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isAnalyzing = false) }
            }
        }
    }

    // ─── PDF Generation Actions ───
    fun openPdfModal() = _uiState.update { it.copy(showPdfModal = true) }
    fun closePdfModal() = _uiState.update { it.copy(showPdfModal = false) }

    fun triggerPdfGeneration(format: String) {
        val state = _uiState.value
        val taxYear = state.taxYear ?: return
        
        emitEvent(
            TaxDetailEvent.GeneratePdf(
                format = format,
                taxYear = taxYear,
                profile = state.profile,
                analysis = state.analysis,
                assets = state.assets,
                liabilities = state.liabilities
            )
        )
        closePdfModal()
    }

    // ─── Modal Actions ───
    fun openAssetModal(asset: TaxAsset? = null) = _uiState.update { it.copy(showAssetModal = true, editingAsset = asset) }
    fun closeAssetModal() = _uiState.update { it.copy(showAssetModal = false, editingAsset = null) }

    fun saveAsset(assetType: String, description: String, currentValue: Double) {
        val uid = auth.currentUser?.uid ?: return
        val editing = _uiState.value.editingAsset

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val ref = if (editing != null) {
                    db.collection("users").document(uid).collection("taxAssets").document(editing.id)
                } else {
                    db.collection("users").document(uid).collection("taxAssets").document()
                }

                val data = mapOf(
                    "taxYearId" to taxYearDocId,
                    "assetType" to assetType,
                    "description" to description,
                    "currentValue" to currentValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                if (editing == null) {
                    ref.set(data + ("createdAt" to FieldValue.serverTimestamp()))
                } else {
                    ref.update(data)
                }

                emitEvent(TaxDetailEvent.ShowToast("Asset saved"))
                closeAssetModal()
                handleAnalyze()
            } catch (e: Exception) {
                emitEvent(TaxDetailEvent.ShowToast("Failed to save asset", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun openLiabilityModal(liability: TaxLiability? = null) = _uiState.update { it.copy(showLiabilityModal = true, editingLiability = liability) }
    fun closeLiabilityModal() = _uiState.update { it.copy(showLiabilityModal = false, editingLiability = null) }

    fun saveLiability(liabilityType: String, description: String, principal: Double, lender: String) {
        val uid = auth.currentUser?.uid ?: return
        val editing = _uiState.value.editingLiability

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val ref = if (editing != null) {
                    db.collection("users").document(uid).collection("taxLiabilities").document(editing.id)
                } else {
                    db.collection("users").document(uid).collection("taxLiabilities").document()
                }

                val data = mapOf(
                    "taxYearId" to taxYearDocId,
                    "liabilityType" to liabilityType,
                    "description" to description,
                    "principal" to principal,
                    "lender" to lender,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                if (editing == null) {
                    ref.set(data + ("createdAt" to FieldValue.serverTimestamp()))
                } else {
                    ref.update(data)
                }

                emitEvent(TaxDetailEvent.ShowToast("Liability saved"))
                closeLiabilityModal()
                handleAnalyze()
            } catch (e: Exception) {
                emitEvent(TaxDetailEvent.ShowToast("Failed to save liability", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun openProfileModal() = _uiState.update { it.copy(showProfileModal = true) }
    fun closeProfileModal() = _uiState.update { it.copy(showProfileModal = false) }

    fun saveProfile(taxpayerName: String, tin: String) {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val ref = db.collection("users").document(uid).collection("settings").document("taxProfile")
                val data = mapOf(
                    "taxpayerName" to taxpayerName,
                    "tin" to tin,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                ref.set(data)
                emitEvent(TaxDetailEvent.ShowToast("Tax profile updated"))
                closeProfileModal()
            } catch (e: Exception) {
                emitEvent(TaxDetailEvent.ShowToast("Failed to update profile", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun requestDelete(type: String, item: Any) = _uiState.update { it.copy(itemToDelete = Pair(type, item)) }
    fun cancelDelete() = _uiState.update { it.copy(itemToDelete = null) }

    fun confirmDelete() {
        val uid = auth.currentUser?.uid ?: return
        val target = _uiState.value.itemToDelete ?: return

        viewModelScope.launch {
            try {
                when (target.first) {
                    "asset" -> {
                        val item = target.second as TaxAsset
                        db.collection("users").document(uid).collection("taxAssets").document(item.id).delete()
                        emitEvent(TaxDetailEvent.ShowToast("Asset deleted"))
                    }
                    "liability" -> {
                        val item = target.second as TaxLiability
                        db.collection("users").document(uid).collection("taxLiabilities").document(item.id).delete()
                        emitEvent(TaxDetailEvent.ShowToast("Liability deleted"))
                    }
                    "taxYear" -> {
                        db.collection("users").document(uid).collection("taxYears").document(taxYearDocId).delete()
                        emitEvent(TaxDetailEvent.ShowToast("Tax year deleted"))
                        emitEvent(TaxDetailEvent.NavigateBack)
                    }
                }
                cancelDelete()
                handleAnalyze()
            } catch (e: Exception) {
                emitEvent(TaxDetailEvent.ShowToast("Failed to delete", true))
            }
        }
    }

    private fun emitEvent(event: TaxDetailEvent) = viewModelScope.launch { _events.emit(event) }
}