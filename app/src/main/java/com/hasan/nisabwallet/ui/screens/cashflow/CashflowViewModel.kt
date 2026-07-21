package com.hasan.nisabwallet.ui.screens.cashflow

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

val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
val FULL_MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

data class CashflowTransaction(
    val id: String = "",
    val type: String = "",
    val amount: Double = 0.0,
    val categoryId: String = "",
    val date: String = "",
    val isTransfer: Boolean = false
)

data class CashflowCategory(
    val id: String,
    val name: String,
    val color: String
)

data class MonthChartData(val label: String, val income: Double, val expense: Double)
data class CategoryExpenseData(val name: String, val color: String, val amount: Double)
data class MonthTableRow(val label: String, val sortKey: String, val income: Double, val expense: Double, val net: Double)

data class CashflowUiState(
    val isLoading: Boolean = true,
    val period: String = "monthly", // weekly, monthly, quarterly, yearly, custom
    val curYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val curMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val customStart: String = "",
    val customEnd: String = "",
    
    val transactions: List<CashflowTransaction> = emptyList(),
    val categories: List<CashflowCategory> = emptyList(),
    
    // Derived Data
    val dateLabel: String = "",
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netFlow: Double = 0.0,
    val savingsRate: Double = 0.0,
    
    val last6Months: List<MonthChartData> = emptyList(),
    val expensesByCategory: List<CategoryExpenseData> = emptyList(),
    val monthRows: List<MonthTableRow> = emptyList(),
    
    val showNav: Boolean = true
)

sealed class CashflowEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : CashflowEvent()
}

@HiltViewModel
class CashflowViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashflowUiState())
    val uiState: StateFlow<CashflowUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CashflowEvent>()
    val events = _events.asSharedFlow()

    private var txListener: ListenerRegistration? = null
    private var catListener: ListenerRegistration? = null

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        catListener = db.collection("users").document(uid).collection("categories")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val cats = snap.documents.map { d ->
                        CashflowCategory(d.id, d.getString("name") ?: "Unknown", d.getString("color") ?: "#6B7280")
                    }
                    _uiState.update { it.copy(categories = cats) }
                    recalculateData()
                }
            }

        txListener = db.collection("users").document(uid).collection("transactions")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val txs = snap.documents.map { d ->
                        CashflowTransaction(
                            id = d.id,
                            type = d.getString("type") ?: "",
                            amount = d.getDouble("amount") ?: 0.0,
                            categoryId = d.getString("categoryId") ?: "",
                            date = d.getString("date") ?: "",
                            isTransfer = d.getBoolean("isTransfer") ?: false
                        )
                    }
                    _uiState.update { it.copy(transactions = txs, isLoading = false) }
                    recalculateData()
                }
            }
    }

    fun setPeriod(p: String) {
        _uiState.update { it.copy(period = p) }
        recalculateData()
    }

    fun setCustomDates(start: String, end: String) {
        _uiState.update { it.copy(customStart = start, customEnd = end) }
        recalculateData()
    }

    fun goPrev() {
        _uiState.update { state ->
            var y = state.curYear
            var m = state.curMonth
            when (state.period) {
                "monthly" -> if (m == 0) { y -= 1; m = 11 } else { m -= 1 }
                "quarterly" -> {
                    val q = m / 3
                    if (q == 0) { y -= 1; m = 9 } else { m = (q - 1) * 3 }
                }
                "yearly" -> y -= 1
            }
            state.copy(curYear = y, curMonth = m)
        }
        recalculateData()
    }

    fun goNext() {
        _uiState.update { state ->
            var y = state.curYear
            var m = state.curMonth
            when (state.period) {
                "monthly" -> if (m == 11) { y += 1; m = 0 } else { m += 1 }
                "quarterly" -> {
                    val q = m / 3
                    if (q == 3) { y += 1; m = 0 } else { m = (q + 1) * 3 }
                }
                "yearly" -> y += 1
            }
            state.copy(curYear = y, curMonth = m)
        }
        recalculateData()
    }

    private fun recalculateData() {
        val state = _uiState.value
        val now = Calendar.getInstance()
        
        var startStr = ""
        var endStr = ""
        var label = ""

        when (state.period) {
            "weekly" -> {
                val cal = Calendar.getInstance()
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val diff = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
                cal.add(Calendar.DAY_OF_MONTH, diff)
                val mon = cal.clone() as Calendar
                cal.add(Calendar.DAY_OF_MONTH, 6)
                val sun = cal
                
                startStr = "%04d-%02d-%02d".format(mon.get(Calendar.YEAR), mon.get(Calendar.MONTH) + 1, mon.get(Calendar.DAY_OF_MONTH))
                endStr = "%04d-%02d-%02d".format(sun.get(Calendar.YEAR), sun.get(Calendar.MONTH) + 1, sun.get(Calendar.DAY_OF_MONTH))
                label = "${mon.get(Calendar.DAY_OF_MONTH)} ${MONTHS[mon.get(Calendar.MONTH)]} – ${sun.get(Calendar.DAY_OF_MONTH)} ${MONTHS[sun.get(Calendar.MONTH)]} ${sun.get(Calendar.YEAR)}"
            }
            "monthly" -> {
                val cal = Calendar.getInstance().apply { set(state.curYear, state.curMonth, 1) }
                val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                startStr = "%04d-%02d-01".format(state.curYear, state.curMonth + 1)
                endStr = "%04d-%02d-%02d".format(state.curYear, state.curMonth + 1, lastDay)
                label = "${FULL_MONTHS[state.curMonth]} ${state.curYear}"
            }
            "quarterly" -> {
                val q = state.curMonth / 3
                val qStart = q * 3
                val qEnd = qStart + 2
                val cal = Calendar.getInstance().apply { set(state.curYear, qEnd, 1) }
                val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                startStr = "%04d-%02d-01".format(state.curYear, qStart + 1)
                endStr = "%04d-%02d-%02d".format(state.curYear, qEnd + 1, lastDay)
                label = "Q${q + 1} ${state.curYear}"
            }
            "yearly" -> {
                startStr = "%04d-01-01".format(state.curYear)
                endStr = "%04d-12-31".format(state.curYear)
                label = "${state.curYear}"
            }
            "custom" -> {
                startStr = state.customStart
                endStr = state.customEnd
                label = if (startStr.isNotBlank() && endStr.isNotBlank()) "$startStr → $endStr" else "Select Dates"
            }
        }

        // 1. Filter Period
        val baseTxs = state.transactions.filter { !it.isTransfer }
        val filtered = if (startStr.isNotBlank() && endStr.isNotBlank()) {
            baseTxs.filter { it.date in startStr..endStr }
        } else {
            baseTxs
        }

        // 2. Summary
        val inc = filtered.filter { it.type == "Income" }.sumOf { it.amount }
        val exp = filtered.filter { it.type == "Expense" }.sumOf { it.amount }
        val net = inc - exp
        val rate = if (inc > 0) (net / inc) * 100 else 0.0

        // 3. Last 6 Months Chart
        val last6 = (0..5).map { i ->
            val d = Calendar.getInstance().apply { add(Calendar.MONTH, -(5 - i)) }
            val y = d.get(Calendar.YEAR)
            val m = d.get(Calendar.MONTH)
            val mStart = "%04d-%02d-01".format(y, m + 1)
            val mEnd = "%04d-%02d-%02d".format(y, m + 1, d.getActualMaximum(Calendar.DAY_OF_MONTH))
            
            val mTx = baseTxs.filter { it.date in mStart..mEnd }
            MonthChartData(
                label = MONTHS[m],
                income = mTx.filter { it.type == "Income" }.sumOf { it.amount },
                expense = mTx.filter { it.type == "Expense" }.sumOf { it.amount }
            )
        }

        // 4. Expenses by Category
        val expByCat = filtered.filter { it.type == "Expense" }.groupBy { it.categoryId }
            .map { (catId, txs) ->
                val cat = state.categories.find { it.id == catId }
                CategoryExpenseData(
                    name = cat?.name ?: "Uncategorized",
                    color = cat?.color ?: "#6B7280",
                    amount = txs.sumOf { it.amount }
                )
            }.sortedByDescending { it.amount }.take(8)

        // 5. Month Table Rows
        val grouped = filtered.groupBy { it.date.take(7) } // yyyy-MM
        val mRows = grouped.map { (ym, txs) ->
            val parts = ym.split("-")
            val y = parts[0]
            val m = parts[1].toInt() - 1
            val rInc = txs.filter { it.type == "Income" }.sumOf { it.amount }
            val rExp = txs.filter { it.type == "Expense" }.sumOf { it.amount }
            MonthTableRow(
                label = "${FULL_MONTHS[m]} $y",
                sortKey = ym,
                income = rInc,
                expense = rExp,
                net = rInc - rExp
            )
        }.sortedByDescending { it.sortKey }

        _uiState.update { 
            it.copy(
                dateLabel = label,
                totalIncome = inc,
                totalExpense = exp,
                netFlow = net,
                savingsRate = rate,
                last6Months = last6,
                expensesByCategory = expByCat,
                monthRows = mRows,
                showNav = it.period != "weekly" && it.period != "custom"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        txListener?.remove()
        catListener?.remove()
    }
}