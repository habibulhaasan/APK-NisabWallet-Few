package com.hasan.nisabwallet.ui.screens.tax.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.hasan.nisabwallet.core.util.TaxCategoryUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

data class UserCategory(
    val id: String,
    val name: String,
    val type: String
)

data class CategoryMappingState(
    val userCategoryId: String,
    val userCategoryName: String,
    val userCategoryType: String,
    val selectedTaxCategoryId: String,
    val confidence: String // "high", "medium", "low"
)

data class TaxSetupUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val mappings: List<CategoryMappingState> = emptyList()
)

sealed class TaxSetupEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : TaxSetupEvent()
    object SetupComplete : TaxSetupEvent()
}

@HiltViewModel
class TaxSetupViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaxSetupUiState())
    val uiState: StateFlow<TaxSetupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TaxSetupEvent>()
    val events = _events.asSharedFlow()

    init {
        loadCategoriesAndMappings()
    }

    private fun loadCategoriesAndMappings() {
        val uid = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            try {
                // 1. Fetch User Categories[cite: 14]
                val catSnap = db.collection("users").document(uid).collection("categories").get().await()
                val userCategories = catSnap.documents.map {
                    UserCategory(it.id, it.getString("name") ?: "", it.getString("type") ?: "Expense")
                }

                // 2. Fetch Existing Mappings[cite: 14]
                val mapSnap = db.collection("users").document(uid).collection("taxCategoryMappings").get().await()
                val existingMappings = mapSnap.documents.associateBy(
                    { it.getString("userCategoryId") ?: "" },
                    { it.getString("taxCategoryId") ?: "" }
                )

                // 3. Generate State (Merge existing + Auto-suggest)
                val stateMappings = userCategories.map { userCat ->
                    val existingTaxId = existingMappings[userCat.id]
                    
                    if (existingTaxId != null) {
                        CategoryMappingState(userCat.id, userCat.name, userCat.type, existingTaxId, "high")
                    } else {
                        val suggestion = autoSuggestTaxCategory(userCat.name, userCat.type)
                        CategoryMappingState(userCat.id, userCat.name, userCat.type, suggestion.first, suggestion.second)
                    }
                }

                _uiState.update { it.copy(isLoading = false, mappings = stateMappings) }
            } catch (e: Exception) {
                emitEvent(TaxSetupEvent.ShowToast("Failed to load categories: ${e.message}", true))
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun autoSuggestTaxCategory(name: String, type: String): Pair<String, String> {
        val lowerName = name.lowercase()
        val taxCategories = if (type == "Income") TaxCategoryUtils.INCOME_TAX_CATEGORIES else TaxCategoryUtils.ALL_EXPENSE_TAX_CATEGORIES

        for (taxCat in taxCategories) {
            if (lowerName == taxCat.name.lowercase()) return Pair(taxCat.id, "high")
            for (keyword in taxCat.suggestedKeywords) {
                if (lowerName.contains(keyword.lowercase())) return Pair(taxCat.id, "medium")
            }
        }
        
        return if (type == "Income") Pair("other_income", "low") else Pair("miscellaneous", "low")
    }

    fun updateMapping(userCategoryId: String, newTaxCategoryId: String) {
        _uiState.update { state ->
            val updatedMappings = state.mappings.map {
                if (it.userCategoryId == userCategoryId) it.copy(selectedTaxCategoryId = newTaxCategoryId, confidence = "high")
                else it
            }
            state.copy(mappings = updatedMappings)
        }
    }

    fun saveMappings() {
        val uid = auth.currentUser?.uid ?: return
        val mappingsToSave = _uiState.value.mappings

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Fetch existing docs to delete old mappings before saving new ones (batch replacement)
                db.collection("users").document(uid).collection("taxCategoryMappings").get().addOnSuccessListener { snap ->
                    val batch = db.batch()
                    
                    // Clear old
                    snap.documents.forEach { batch.delete(it.reference) }

                    // Write new mappings instantly (offline friendly)
                    mappingsToSave.forEach { mapping ->
                        val docRef = db.collection("users").document(uid).collection("taxCategoryMappings").document()
                        batch.set(docRef, mapOf(
                            "mappingId" to UUID.randomUUID().toString(),
                            "userCategoryId" to mapping.userCategoryId,
                            "userCategoryName" to mapping.userCategoryName,
                            "userCategoryType" to mapping.userCategoryType,
                            "taxCategoryId" to mapping.selectedTaxCategoryId,
                            "confidence" to mapping.confidence,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    }

                    batch.commit() // Instant offline commit
                    
                    emitEvent(TaxSetupEvent.ShowToast("Tax mappings saved successfully!"))
                    emitEvent(TaxSetupEvent.SetupComplete)
                    
                }.addOnFailureListener {
                    emitEvent(TaxSetupEvent.ShowToast("Failed to clear old mappings", true))
                    _uiState.update { it.copy(isSaving = false) }
                }
            } catch (e: Exception) {
                emitEvent(TaxSetupEvent.ShowToast("Failed to save mappings: ${e.message}", true))
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun emitEvent(event: TaxSetupEvent) = viewModelScope.launch { _events.emit(event) }
}