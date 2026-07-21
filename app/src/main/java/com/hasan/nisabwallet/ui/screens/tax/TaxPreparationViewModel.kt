package com.hasan.nisabwallet.ui.screens.tax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hasan.nisabwallet.core.util.TaxCategoryUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaxYearRecord(
    val id: String = "",
    val incomeYear: String = "",
    val fiscalYearStart: String = "",
    val fiscalYearEnd: String = "",
    val taxYear: String = "",
    val filingDeadline: String = "",
    val status: String = "draft",
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalAssets: Double = 0.0,
    val netWorth: Double = 0.0
)

data class TaxPreparationUiState(
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val taxYears: List<TaxYearRecord> = emptyList(),
    val hasMappings: Boolean = false,
    val currentIncomeYear: String = TaxCategoryUtils.getCurrentIncomeYear()
)

sealed class TaxPreparationEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : TaxPreparationEvent()
    data class NavigateToSetup(val isNew: Boolean = false) : TaxPreparationEvent()
    data class NavigateToTaxYear(val id: String) : TaxPreparationEvent()
}

@HiltViewModel
class TaxPreparationViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaxPreparationUiState())
    val uiState: StateFlow<TaxPreparationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TaxPreparationEvent>()
    val events = _events.asSharedFlow()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        // Fetch Tax Mappings[cite: 12]
        db.collection("users").document(uid).collection("taxCategoryMappings")
            .limit(1)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    _uiState.update { it.copy(hasMappings = !snap.isEmpty) }
                }
            }

        // Fetch Tax Years[cite: 12]
        db.collection("users").document(uid).collection("taxYears")
            .orderBy("fiscalYearStart", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val years = snap.documents.map { d ->
                        TaxYearRecord(
                            id = d.id,
                            incomeYear = d.getString("incomeYear") ?: "",
                            fiscalYearStart = d.getString("fiscalYearStart") ?: "",
                            fiscalYearEnd = d.getString("fiscalYearEnd") ?: "",
                            taxYear = d.getString("taxYear") ?: "",
                            filingDeadline = d.getString("filingDeadline") ?: "",
                            status = d.getString("status") ?: "draft",
                            totalIncome = d.getDouble("totalIncome") ?: 0.0,
                            totalExpenses = d.getDouble("totalExpenses") ?: 0.0,
                            totalAssets = d.getDouble("totalAssets") ?: 0.0,
                            netWorth = d.getDouble("netWorth") ?: 0.0
                        )
                    }
                    _uiState.update { it.copy(taxYears = years, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
    }

    fun createCurrentYear() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true) }
            try {
                val year = state.currentIncomeYear
                val existing = state.taxYears.find { it.incomeYear == year }

                if (existing != null) {
                    emitEvent(TaxPreparationEvent.ShowToast("Tax year already exists"))
                    emitEvent(TaxPreparationEvent.NavigateToTaxYear(existing.id))
                } else {
                    // Create offline-first without .await()[cite: 12]
                    val (start, end) = TaxCategoryUtils.getFiscalYearDates(year)
                    val taxYear = TaxCategoryUtils.getTaxYear(year)
                    val deadline = TaxCategoryUtils.getFilingDeadline(taxYear)

                    val docRef = db.collection("users").document(uid).collection("taxYears").document()
                    val data = mapOf(
                        "incomeYear" to year,
                        "fiscalYearStart" to start,
                        "fiscalYearEnd" to end,
                        "taxYear" to taxYear,
                        "filingDeadline" to deadline,
                        "status" to "draft",
                        "totalIncome" to 0.0,
                        "totalExpenses" to 0.0,
                        "totalAssets" to 0.0,
                        "totalLiabilities" to 0.0,
                        "netWorth" to 0.0,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    docRef.set(data) // Instant offline write
                    emitEvent(TaxPreparationEvent.ShowToast("Tax year created!"))

                    if (!state.hasMappings) {
                        emitEvent(TaxPreparationEvent.NavigateToSetup(isNew = true))
                    } else {
                        emitEvent(TaxPreparationEvent.NavigateToTaxYear(docRef.id))
                    }
                }
            } catch (e: Exception) {
                emitEvent(TaxPreparationEvent.ShowToast("Failed to create tax year: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isCreating = false) }
            }
        }
    }

    private fun emitEvent(event: TaxPreparationEvent) = viewModelScope.launch { _events.emit(event) }
}