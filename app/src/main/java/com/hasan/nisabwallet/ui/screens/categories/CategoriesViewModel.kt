package com.hasan.nisabwallet.ui.screens.categories

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
import java.util.UUID
import javax.inject.Inject

// ─── Data Models ───
data class CategoryItem(
    val id: String = "",
    val name: String = "",
    val type: String = "Expense",
    val color: String = "#3B82F6",
    val isSystem: Boolean = false,
    val isDefault: Boolean = false,
    val isRiba: Boolean = false
)

data class CategoryForm(
    val id: String? = null,
    val name: String = "",
    val type: String = "Expense",
    val color: String = "#3B82F6",
    val isSystem: Boolean = false,
    val isRiba: Boolean = false
)

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isLoadingDefaults: Boolean = false,
    val syncStatus: String = "Connecting...",
    val categories: List<CategoryItem> = emptyList(),
    
    val showAddEditModal: Boolean = false,
    val form: CategoryForm = CategoryForm()
)

sealed class CategoriesEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : CategoriesEvent()
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CategoriesEvent>()
    val events = _events.asSharedFlow()

    private var catListener: ListenerRegistration? = null
    private var rawCategories = emptyList<CategoryItem>()

    // System and Default Categories mapping[cite: 6]
    private val systemIncomeCategories = listOf(
        CategoryItem(name = "Salary", type = "Income", color = "#10B981", isSystem = true, isDefault = true),
        CategoryItem(name = "Business Income", type = "Income", color = "#3B82F6", isSystem = true, isDefault = true),
        CategoryItem(name = "Interest / Riba", type = "Income", color = "#F59E0B", isSystem = true, isDefault = true, isRiba = true),
        CategoryItem(name = "Jewellery Redemption", type = "Income", color = "#F59E0B", isSystem = true, isDefault = true),
        CategoryItem(name = "Investment Return", type = "Income", color = "#06B6D4", isSystem = true, isDefault = true),
        CategoryItem(name = "Loan Received", type = "Income", color = "#8B5CF6", isSystem = true, isDefault = true),
        CategoryItem(name = "Lending Received", type = "Income", color = "#84CC16", isSystem = true, isDefault = true)
    )

    private val systemExpenseCategories = listOf(
        CategoryItem(name = "Food & Dining", type = "Expense", color = "#EC4899", isSystem = true, isDefault = true),
        CategoryItem(name = "Transport", type = "Expense", color = "#EF4444", isSystem = true, isDefault = true),
        CategoryItem(name = "Bills & Utilities", type = "Expense", color = "#6366F1", isSystem = true, isDefault = true),
        CategoryItem(name = "Fees & Charges", type = "Expense", color = "#F97316", isSystem = true, isDefault = true),
        CategoryItem(name = "Zakat Payment", type = "Expense", color = "#10B981", isSystem = true, isDefault = true),
        CategoryItem(name = "Sadaqah / Charity", type = "Expense", color = "#06B6D4", isSystem = true, isDefault = true),
        CategoryItem(name = "Loan Repayment", type = "Expense", color = "#F59E0B", isSystem = true, isDefault = true)
    )

    private val defaultIncomeCategories = listOf(
        CategoryItem(name = "Bonus", type = "Income", color = "#3B82F6", isDefault = true)
    )
    
    private val defaultExpenseCategories = listOf(
        CategoryItem(name = "Shopping", type = "Expense", color = "#8B5CF6", isDefault = true),
        CategoryItem(name = "Healthcare", type = "Expense", color = "#06B6D4", isDefault = true)
    )

    private val allDefaults = systemIncomeCategories + systemExpenseCategories + defaultIncomeCategories + defaultExpenseCategories

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        catListener = db.collection("users").document(uid).collection("categories")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    emitEvent(CategoriesEvent.ShowToast("Sync error: ${e.message}", true))
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val status = when {
                        snap.metadata.hasPendingWrites() -> "Syncing..."
                        snap.metadata.isFromCache() -> "Offline (Cached)"
                        else -> "Synced"
                    }
                    rawCategories = snap.documents.map { d ->
                        CategoryItem(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            type = d.getString("type") ?: "Expense",
                            color = d.getString("color") ?: "#3B82F6",
                            isSystem = d.getBoolean("isSystem") ?: false,
                            isDefault = d.getBoolean("isDefault") ?: false,
                            isRiba = d.getBoolean("isRiba") ?: false
                        )
                    }.sortedBy { it.name }
                    
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            categories = rawCategories,
                            syncStatus = status
                        )
                    }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        catListener?.remove()
    }

    // ─── Intent Actions ───

    fun loadDefaultCategories() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDefaults = true) }
            try {
                // Deduplicate combining type and name[cite: 6]
                val existingKeys = rawCategories.map { "${it.type.lowercase()}_${it.name.trim().lowercase()}" }.toSet()
                val toAdd = allDefaults.filter { cat -> 
                    val key = "${cat.type.lowercase()}_${cat.name.trim().lowercase()}"
                    !existingKeys.contains(key)
                }

                if (toAdd.isEmpty()) {
                    emitEvent(CategoriesEvent.ShowToast("All default categories already exist"))
                    return@launch
                }

                val batch = db.batch()
                val categoriesRef = db.collection("users").document(uid).collection("categories")

                toAdd.forEach { cat ->
                    val docRef = categoriesRef.document()
                    val data = hashMapOf(
                        "categoryId" to UUID.randomUUID().toString(),
                        "name" to cat.name,
                        "type" to cat.type,
                        "color" to cat.color,
                        "isSystem" to cat.isSystem,
                        "isDefault" to cat.isDefault,
                        "isRiba" to cat.isRiba,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(docRef, data)
                }

                batch.commit().await()
                emitEvent(CategoriesEvent.ShowToast("Added ${toAdd.size} categor${if (toAdd.size > 1) "ies" else "y"}"))
            } catch (e: Exception) {
                emitEvent(CategoriesEvent.ShowToast("Failed to load defaults: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isLoadingDefaults = false) }
            }
        }
    }

    fun openAddModal() {
        _uiState.update { it.copy(showAddEditModal = true, form = CategoryForm()) }
    }

    fun openEditModal(category: CategoryItem) {
        _uiState.update { 
            it.copy(
                showAddEditModal = true, 
                form = CategoryForm(
                    id = category.id,
                    name = category.name,
                    type = category.type,
                    color = category.color,
                    isSystem = category.isSystem,
                    isRiba = category.isRiba
                )
            )
        }
    }

    fun closeModal() {
        _uiState.update { it.copy(showAddEditModal = false, form = CategoryForm()) }
    }

    fun updateForm(update: (CategoryForm) -> CategoryForm) {
        _uiState.update { it.copy(form = update(it.form)) }
    }


    fun saveCategory() {
        val uid = auth.currentUser?.uid ?: return
        
        val form = _uiState.value.form 
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val data = mapOf(
                    "name" to form.name,
                    "type" to form.type,
                    "color" to form.color,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                if (form.id != null && form.id.isNotBlank()) {
                    db.collection("users").document(uid).collection("categories")
                        .document(form.id).update(data) // Instant offline write
                } else {
                    val newData = data + ("createdAt" to FieldValue.serverTimestamp())
                    db.collection("users").document(uid).collection("categories")
                        .document().set(newData) // Instant offline write
                }
                
                _events.emit(CategoriesEvent.ShowToast("Category saved successfully"))
                
                // Uses your existing function to close the bottom sheet
                closeModal() 
                
            } catch (e: Exception) {
                _events.emit(CategoriesEvent.ShowToast("Failed to save: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // Changed parameter from 'categoryId: String' to 'category: CategoryItem' to match the UI
    fun deleteCategory(category: CategoryItem) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("categories")
                    .document(category.id).delete() // Instant offline write
                    
                _events.emit(CategoriesEvent.ShowToast("Category deleted"))
            } catch (e: Exception) {
                _events.emit(CategoriesEvent.ShowToast("Failed to delete: ${e.message}", true))
            }
        }
    }
    

    private fun emitEvent(event: CategoriesEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}