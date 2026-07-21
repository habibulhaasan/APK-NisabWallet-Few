package com.hasan.nisabwallet.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class AnalyticsTransaction(
    val id: String = "",
    val type: String = "",
    val amount: Double = 0.0,
    val categoryId: String = "",
    val date: String = ""
)

data class AnalyticsCategory(
    val id: String,
    val name: String
)

data class CategoryData(
    val name: String,
    val value: Double,
    val colorHex: String
)

data class TrendData(
    val label: String,
    val income: Double,
    val expense: Double
)

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val timeRange: String = "month", // week, month, year, custom
    val customStartDate: String = "",
    val customEndDate: String = "",
    
    val transactions: List<AnalyticsTransaction> = emptyList(),
    val categories: List<AnalyticsCategory> = emptyList(),
    
    // Derived Data
    val filteredTransactions: List<AnalyticsTransaction> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netBalance: Double = 0.0,
    
    val expenseCategoryData: List<CategoryData> = emptyList(),
    val incomeCategoryData: List<CategoryData> = emptyList(),
    val trendData: List<TrendData> = emptyList()
)

sealed class AnalyticsEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : AnalyticsEvent()
}

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AnalyticsEvent>()
    val events = _events.asSharedFlow()

    private var txListener: ListenerRegistration? = null
    private var catListener: ListenerRegistration? = null

    private val chartColors = listOf(
        "#3B82F6", "#10B981", "#F59E0B", "#EF4444", 
        "#8B5CF6", "#EC4899", "#06B6D4", "#84CC16"
    )

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        catListener = db.collection("users").document(uid).collection("categories")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val cats = snap.documents.map { d ->
                        AnalyticsCategory(d.id, d.getString("name") ?: "Unknown")
                    }
                    _uiState.update { it.copy(categories = cats) }
                    recalculateData()
                }
            }

        // Note: For deep analytics we pull a larger set of transactions. 
        txListener = db.collection("users").document(uid).collection("transactions")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val txs = snap.documents.map { d ->
                        AnalyticsTransaction(
                            id = d.id,
                            type = d.getString("type") ?: "",
                            amount = d.getDouble("amount") ?: 0.0,
                            categoryId = d.getString("categoryId") ?: "",
                            date = d.getString("date") ?: ""
                        )
                    }
                    _uiState.update { it.copy(transactions = txs, isLoading = false) }
                    recalculateData()
                }
            }
    }

    fun setTimeRange(range: String) {
        _uiState.update { it.copy(timeRange = range) }
        recalculateData()
    }

    fun setCustomDates(start: String, end: String) {
        _uiState.update { it.copy(customStartDate = start, customEndDate = end) }
        recalculateData()
    }

    private fun recalculateData() {
        val state = _uiState.value
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        // 1. Filter Transactions
        val filtered = when (state.timeRange) {
            "week" -> {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
                val startStr = sdf.format(cal.time)
                state.transactions.filter { it.date >= startStr }
            }
            "month" -> {
                val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
                val startStr = sdf.format(cal.time)
                state.transactions.filter { it.date >= startStr }
            }
            "year" -> {
                val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, 1) }
                val startStr = sdf.format(cal.time)
                state.transactions.filter { it.date >= startStr }
            }
            "custom" -> {
                if (state.customStartDate.isNotBlank() && state.customEndDate.isNotBlank()) {
                    state.transactions.filter { it.date in state.customStartDate..state.customEndDate }
                } else {
                    state.transactions
                }
            }
            else -> state.transactions
        }

        // 2. Summary Stats
        val totalInc = filtered.filter { it.type == "Income" }.sumOf { it.amount }
        val totalExp = filtered.filter { it.type == "Expense" }.sumOf { it.amount }
        val net = totalInc - totalExp

        // 3. Category Breakdown
        fun buildCategoryData(type: String): List<CategoryData> {
            val grouped = filtered.filter { it.type == type }.groupBy { it.categoryId }
            return grouped.entries.mapIndexed { index, (catId, txs) ->
                val catName = state.categories.find { it.id == catId }?.name ?: "Unknown"
                val sum = txs.sumOf { it.amount }
                CategoryData(catName, sum, chartColors[index % chartColors.size])
            }.sortedByDescending { it.value }
        }

        val expData = buildCategoryData("Expense")
        val incData = buildCategoryData("Income")

        // 4. Trend Data
        val trend = if (state.timeRange == "week") {
            // Weekly Trend (Day of Week)
            val weekMap = mutableMapOf<String, TrendData>()
            val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            days.forEach { weekMap[it] = TrendData(it, 0.0, 0.0) }

            val daySdf = java.text.SimpleDateFormat("EEE", Locale.US)
            filtered.forEach { tx ->
                try {
                    val dateObj = sdf.parse(tx.date) ?: return@forEach
                    val dayStr = daySdf.format(dateObj)
                    val curr = weekMap[dayStr] ?: return@forEach
                    weekMap[dayStr] = if (tx.type == "Income") curr.copy(income = curr.income + tx.amount) else curr.copy(expense = curr.expense + tx.amount)
                } catch (_: Exception) {}
            }
            days.map { weekMap[it]!! }
        } else {
            // Monthly/Daily Trend (grouped by YYYY-MM or YYYY-MM-DD)
            val formatStr = if (state.timeRange == "year") "MMM yy" else "dd MMM"
            val outSdf = java.text.SimpleDateFormat(formatStr, Locale.US)
            val map = mutableMapOf<String, TrendData>()
            
            // Sort to ensure chronological order on chart
            val sorted = filtered.sortedBy { it.date }
            sorted.forEach { tx ->
                try {
                    val dateObj = sdf.parse(tx.date) ?: return@forEach
                    val key = outSdf.format(dateObj)
                    val curr = map[key] ?: TrendData(key, 0.0, 0.0)
                    map[key] = if (tx.type == "Income") curr.copy(income = curr.income + tx.amount) else curr.copy(expense = curr.expense + tx.amount)
                } catch (_: Exception) {}
            }
            map.values.toList()
        }

        _uiState.update { 
            it.copy(
                filteredTransactions = filtered,
                totalIncome = totalInc,
                totalExpense = totalExp,
                netBalance = net,
                expenseCategoryData = expData,
                incomeCategoryData = incData,
                trendData = trend
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        txListener?.remove()
        catListener?.remove()
    }
}