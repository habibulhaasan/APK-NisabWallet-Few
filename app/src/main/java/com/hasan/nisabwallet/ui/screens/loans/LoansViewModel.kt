package com.hasan.nisabwallet.ui.screens.loans

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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.log
import kotlin.math.pow

// ─── Data Models ───
data class LoanAccount(val id: String, val name: String, val balance: Double)

data class Loan(
    val id: String = "",
    val lenderName: String = "",
    val loanType: String = "qard-hasan",
    val principalAmount: Double = 0.0,
    val interestRate: Double = 0.0,
    val monthlyPayment: Double? = null,
    val totalMonths: Int = 0,
    val startDate: String = "",
    val endDate: String? = null,
    val totalPaid: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val status: String = "active",
    val nextPaymentDue: String? = null,
    val lastPaymentDate: String? = null,
    val notes: String = "",
    val accountId: String = "",
    val totalInterest: Double = 0.0,
    val totalRepayment: Double = 0.0,
    val enableReminders: Boolean = true,
    val createdAtMillis: Long = 0L
) {
    val progress: Int
        get() = if (principalAmount > 0) ((totalPaid / principalAmount) * 100).toInt() else 0
}

data class UpcomingPayment(val loan: Loan, val daysUntilDue: Int)

data class LoanForm(
    val id: String? = null,
    val lenderName: String = "",
    val loanType: String = "qard-hasan",
    val principalAmount: String = "",
    val interestRate: String = "0",
    val monthlyPayment: String = "",
    val totalMonths: String = "",
    val startDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val accountId: String = "",
    val notes: String = "",
    val enableReminders: Boolean = true
)

data class LoanPaymentForm(
    val loanId: String = "",
    val amount: String = "",
    val accountId: String = "",
    val paymentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val notes: String = ""
)

data class LoanCalculations(
    val monthlyPayment: Double = 0.0,
    val totalMonths: Int = 0,
    val totalInterest: Double = 0.0,
    val totalRepayment: Double = 0.0
)

data class LoansUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val syncStatus: String = "Connecting...",
    
    val loans: List<Loan> = emptyList(),
    val filteredLoans: List<Loan> = emptyList(),
    val accounts: List<LoanAccount> = emptyList(),
    
    val activeCount: Int = 0,
    val totalDebt: Double = 0.0,
    val totalMonthlyPayment: Double = 0.0,
    val upcomingPayments: List<UpcomingPayment> = emptyList(),
    
    val filterStatus: String = "active",
    val filterType: String = "all",

    val showLoanModal: Boolean = false,
    val showPaymentModal: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    
    val loanForm: LoanForm = LoanForm(),
    val paymentForm: LoanPaymentForm = LoanPaymentForm(),
    val selectedLoan: Loan? = null
)

sealed class LoansEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : LoansEvent()
}

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoansUiState())
    val uiState: StateFlow<LoansUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoansEvent>()
    val events = _events.asSharedFlow()

    private var loansListener: ListenerRegistration? = null
    private var accListener: ListenerRegistration? = null

    private var rawLoans = emptyList<Loan>()
    private var rawAccounts = emptyList<LoanAccount>()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        accListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawAccounts = snap.documents.map { d ->
                        LoanAccount(d.id, d.getString("name") ?: "", d.getDouble("balance") ?: 0.0)
                    }
                    if (_uiState.value.loanForm.accountId.isBlank() && rawAccounts.isNotEmpty()) {
                        _uiState.update { 
                            it.copy(
                                loanForm = it.loanForm.copy(accountId = rawAccounts.first().id),
                                paymentForm = it.paymentForm.copy(accountId = rawAccounts.first().id)
                            )
                        }
                    }
                    combineAndEmit(null)
                }
            }

        loansListener = db.collection("users").document(uid).collection("loans")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val status = when {
                        snap.metadata.hasPendingWrites() -> "Syncing..."
                        snap.metadata.isFromCache() -> "Offline (Cached)"
                        else -> "Synced"
                    }
                    rawLoans = snap.documents.map { d ->
                        Loan(
                            id = d.id,
                            lenderName = d.getString("lenderName") ?: "",
                            loanType = d.getString("loanType") ?: "qard-hasan",
                            principalAmount = d.getDouble("principalAmount") ?: 0.0,
                            interestRate = d.getDouble("interestRate") ?: 0.0,
                            monthlyPayment = d.getDouble("monthlyPayment"),
                            totalMonths = d.getLong("totalMonths")?.toInt() ?: 0,
                            startDate = d.getString("startDate") ?: "",
                            endDate = d.getString("endDate"),
                            totalPaid = d.getDouble("totalPaid") ?: 0.0,
                            remainingBalance = d.getDouble("remainingBalance") ?: 0.0,
                            status = d.getString("status") ?: "active",
                            nextPaymentDue = d.getString("nextPaymentDue"),
                            lastPaymentDate = d.getString("lastPaymentDate"),
                            notes = d.getString("notes") ?: "",
                            accountId = d.getString("accountId") ?: "",
                            totalInterest = d.getDouble("totalInterest") ?: 0.0,
                            totalRepayment = d.getDouble("totalRepayment") ?: 0.0,
                            enableReminders = d.getBoolean("enableReminders") ?: true,
                            createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        )
                    }
                    combineAndEmit(status)
                }
            }
    }

    private fun combineAndEmit(newSyncStatus: String?) {
        val state = _uiState.value
        
        var activeCount = 0
        var totalDebt = 0.0
        var totalMonthly = 0.0
        val upcoming = mutableListOf<UpcomingPayment>()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

        rawLoans.forEach { loan ->
            if (loan.status == "active") {
                activeCount++
                totalDebt += loan.remainingBalance
                totalMonthly += (loan.monthlyPayment ?: 0.0)

                loan.nextPaymentDue?.let { dueStr ->
                    val dueDate = runCatching { sdf.parse(dueStr)?.time }.getOrNull()
                    if (dueDate != null) {
                        val diffDays = ceil((dueDate - today) / (1000.0 * 60 * 60 * 24)).toInt()
                        if (diffDays in 0..7) {
                            upcoming.add(UpcomingPayment(loan, diffDays))
                        }
                    }
                }
            }
        }
        upcoming.sortBy { it.daysUntilDue }

        val filtered = rawLoans.filter { loan ->
            (state.filterStatus == "all" || loan.status == state.filterStatus) &&
            (state.filterType == "all" || loan.loanType == state.filterType)
        }

        _uiState.update { 
            it.copy(
                isLoading = false,
                loans = rawLoans,
                filteredLoans = filtered,
                accounts = rawAccounts,
                activeCount = activeCount,
                totalDebt = totalDebt,
                totalMonthlyPayment = totalMonthly,
                upcomingPayments = upcoming,
                syncStatus = newSyncStatus ?: it.syncStatus
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        loansListener?.remove()
        accListener?.remove()
    }

    // ─── Filter Intents ───
    fun setFilterStatus(s: String) { _uiState.update { it.copy(filterStatus = s) }; combineAndEmit(null) }
    fun setFilterType(t: String) { _uiState.update { it.copy(filterType = t) }; combineAndEmit(null) }

    // ─── Calculation Engine ───
    fun calculateLoanDetails(form: LoanForm): LoanCalculations {
        val principal = form.principalAmount.toDoubleOrNull() ?: 0.0
        val annualRate = form.interestRate.toDoubleOrNull() ?: 0.0
        val months = form.totalMonths.toIntOrNull() ?: 0
        val monthlyInput = form.monthlyPayment.toDoubleOrNull() ?: 0.0

        if (form.loanType == "qard-hasan") {
            if (months > 0) return LoanCalculations(principal / months, months, 0.0, principal)
            if (monthlyInput > 0) return LoanCalculations(monthlyInput, ceil(principal / monthlyInput).toInt(), 0.0, principal)
            return LoanCalculations(0.0, 0, 0.0, principal)
        }

        if (principal > 0 && annualRate > 0) {
            val monthlyRate = annualRate / 12.0 / 100.0
            if (months > 0) {
                val pmt = (principal * monthlyRate * (1 + monthlyRate).pow(months)) / ((1 + monthlyRate).pow(months) - 1)
                val totalRep = pmt * months
                return LoanCalculations(pmt, months, totalRep - principal, totalRep)
            } else if (monthlyInput > 0 && monthlyInput > principal * monthlyRate) {
                val calcMonths = ceil(log(monthlyInput / (monthlyInput - principal * monthlyRate), 10.0) / log(1 + monthlyRate, 10.0)).toInt()
                val totalRep = monthlyInput * calcMonths
                return LoanCalculations(monthlyInput, calcMonths, totalRep - principal, totalRep)
            }
        }
        return LoanCalculations(0.0, 0, 0.0, principal)
    }

    // ─── Modals ───
    fun openAddModal() = _uiState.update { it.copy(showLoanModal = true, loanForm = LoanForm(accountId = rawAccounts.firstOrNull()?.id ?: "")) }
    fun openEditModal(loan: Loan) = _uiState.update { 
        it.copy(
            showLoanModal = true, 
            loanForm = LoanForm(
                id = loan.id, lenderName = loan.lenderName, loanType = loan.loanType,
                principalAmount = loan.principalAmount.toString(), interestRate = loan.interestRate.toString(),
                monthlyPayment = loan.monthlyPayment?.toString() ?: "", totalMonths = if (loan.totalMonths > 0) loan.totalMonths.toString() else "",
                startDate = loan.startDate, accountId = loan.accountId, notes = loan.notes, enableReminders = loan.enableReminders
            )
        )
    }
    fun closeLoanModal() = _uiState.update { it.copy(showLoanModal = false, loanForm = LoanForm(accountId = rawAccounts.firstOrNull()?.id ?: "")) }
    fun updateLoanForm(update: (LoanForm) -> LoanForm) = _uiState.update { it.copy(loanForm = update(it.loanForm)) }

    fun openPaymentModal(loan: Loan) = _uiState.update { 
        it.copy(
            showPaymentModal = true, selectedLoan = loan,
            paymentForm = LoanPaymentForm(loanId = loan.id, amount = loan.monthlyPayment?.toString() ?: "", accountId = loan.accountId.ifBlank { rawAccounts.firstOrNull()?.id ?: "" })
        )
    }
    fun closePaymentModal() = _uiState.update { it.copy(showPaymentModal = false, selectedLoan = null) }
    fun updatePaymentForm(update: (LoanPaymentForm) -> LoanPaymentForm) = _uiState.update { it.copy(paymentForm = update(it.paymentForm)) }

    fun openDeleteConfirm(loan: Loan) = _uiState.update { it.copy(showDeleteConfirm = true, selectedLoan = loan) }
    fun closeDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = false, selectedLoan = null) }

    // ─── Actions ───

    fun toggleReminders(loan: Loan) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            db.collection("users").document(uid).collection("loans").document(loan.id)
                .update("enableReminders", !loan.enableReminders, "updatedAt", FieldValue.serverTimestamp()).await()
            emitEvent(LoansEvent.ShowToast(if (!loan.enableReminders) "Reminders enabled" else "Reminders disabled"))
        }
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

    fun saveLoan() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.loanForm

        if (form.lenderName.isBlank() || form.principalAmount.isBlank() || form.startDate.isBlank()) {
            emitEvent(LoansEvent.ShowToast("Please fill in all required fields", true))
            return
        }
        val principal = form.principalAmount.toDoubleOrNull() ?: 0.0
        if (principal <= 0) {
            emitEvent(LoansEvent.ShowToast("Principal amount must be greater than 0", true))
            return
        }
        if (form.loanType == "interest" && (form.interestRate.toDoubleOrNull() ?: 0.0) <= 0) {
            emitEvent(LoansEvent.ShowToast("Interest rate required for interest-based loans", true))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val calc = calculateLoanDetails(form)
                val cal = Calendar.getInstance().apply { time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(form.startDate)!! }
                val endDate = if (calc.totalMonths > 0) {
                    (cal.clone() as Calendar).apply { add(Calendar.MONTH, calc.totalMonths) }.time
                } else null
                
                val nextDue = if (calc.totalMonths > 0) {
                    (cal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.time
                } else null
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

                val data = mutableMapOf<String, Any?>(
                    "lenderName" to form.lenderName.trim(),
                    "loanType" to form.loanType,
                    "principalAmount" to principal,
                    "interestRate" to (form.interestRate.toDoubleOrNull() ?: 0.0),
                    "monthlyPayment" to if (calc.monthlyPayment > 0) calc.monthlyPayment else null,
                    "totalMonths" to calc.totalMonths,
                    "startDate" to form.startDate,
                    "endDate" to endDate?.let { sdf.format(it) },
                    "accountId" to form.accountId,
                    "notes" to form.notes.trim(),
                    "totalInterest" to calc.totalInterest,
                    "totalRepayment" to calc.totalRepayment,
                    "enableReminders" to form.enableReminders,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                val ref = db.collection("users").document(uid).collection("loans")
                if (form.id != null) {
                    ref.document(form.id).set(data, SetOptions.merge()).await()
                    emitEvent(LoansEvent.ShowToast("Loan updated successfully"))
                } else {
                    data["totalPaid"] = 0.0
                    data["remainingBalance"] = principal
                    data["status"] = "active"
                    data["nextPaymentDue"] = nextDue?.let { sdf.format(it) }
                    data["createdAt"] = FieldValue.serverTimestamp()
                    ref.add(data).await()

                    // DOUBLE ENTRY: Add Principal to Account & Log Income Transaction
                    val acc = rawAccounts.find { it.id == form.accountId }
                    if (acc != null && principal > 0) {
                        db.collection("users").document(uid).collection("accounts").document(acc.id)
                            .update("balance", acc.balance + principal).await()
                        
                        val catId = ensureCategory(uid, "Loan Received", "Income", "#8B5CF6")
                        db.collection("users").document(uid).collection("transactions").add(
                            mapOf(
                                "type" to "Income", "amount" to principal, "accountId" to acc.id,
                                "categoryId" to catId, "description" to "Loan from ${form.lenderName.trim()}",
                                "date" to form.startDate, "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }

                    emitEvent(LoansEvent.ShowToast("Loan added successfully"))
                }
                closeLoanModal()
            } catch (e: Exception) {
                emitEvent(LoansEvent.ShowToast("Failed to save loan: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun submitPayment() {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        val form = state.paymentForm
        val loan = state.selectedLoan ?: return

        val amount = form.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            emitEvent(LoansEvent.ShowToast("Please enter a valid payment amount", true))
            return
        }
        if (amount > loan.remainingBalance) {
            emitEvent(LoansEvent.ShowToast("Payment amount cannot exceed remaining balance", true))
            return
        }

        val acc = rawAccounts.find { it.id == form.accountId }
        if (acc == null || acc.balance < amount) {
            emitEvent(LoansEvent.ShowToast("Insufficient balance in selected account", true))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                var principalPaid = amount
                var interestPaid = 0.0

                if (loan.loanType == "interest" && loan.remainingBalance > 0) {
                    val monthlyRate = loan.interestRate / 12.0 / 100.0
                    interestPaid = loan.remainingBalance * monthlyRate
                    principalPaid = amount - interestPaid
                    if (principalPaid < 0) { principalPaid = 0.0; interestPaid = amount }
                }

                // Create Payment Record
                db.collection("users").document(uid).collection("loanPayments").add(
                    mapOf(
                        "loanId" to loan.id, "amount" to amount, "paymentDate" to form.paymentDate,
                        "principalPaid" to principalPaid, "interestPaid" to interestPaid,
                        "accountId" to form.accountId, "notes" to form.notes.trim(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                // DOUBLE ENTRY: Deduct from Account & Register Expense Transaction
                db.collection("users").document(uid).collection("accounts").document(acc.id)
                    .update("balance", acc.balance - amount).await()

                val catId = ensureCategory(uid, "Loan Payment", "Expense", "#F59E0B")
                db.collection("users").document(uid).collection("transactions").add(
                    mapOf(
                        "type" to "Expense", "amount" to amount, "accountId" to form.accountId,
                        "categoryId" to catId, "description" to "Loan payment: ${loan.lenderName}",
                        "date" to form.paymentDate, "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                // Update Loan Totals
                val newTotalPaid = loan.totalPaid + amount
                val newRemaining = loan.principalAmount - newTotalPaid
                val newStatus = if (newRemaining <= 0) "paid-off" else "active"

                var nextDue = loan.nextPaymentDue
                if (loan.totalMonths > 0 && newStatus == "active") {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val currentDue = runCatching { sdf.parse(loan.nextPaymentDue ?: loan.startDate) }.getOrNull() ?: Date()
                    val cal = Calendar.getInstance().apply { time = currentDue; add(Calendar.MONTH, 1) }
                    nextDue = sdf.format(cal.time)
                }

                db.collection("users").document(uid).collection("loans").document(loan.id).update(
                    mapOf(
                        "totalPaid" to newTotalPaid, "remainingBalance" to if (newRemaining > 0) newRemaining else 0.0,
                        "status" to newStatus, "lastPaymentDate" to form.paymentDate,
                        "nextPaymentDue" to (if (newStatus == "active") nextDue else null),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                if (newStatus == "paid-off") emitEvent(LoansEvent.ShowToast("🎉 Congratulations! Loan fully paid off!"))
                else emitEvent(LoansEvent.ShowToast("Payment recorded successfully"))

                closePaymentModal()
            } catch (e: Exception) {
                emitEvent(LoansEvent.ShowToast("Error processing payment: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteLoan() {
        val uid = auth.currentUser?.uid ?: return
        val loan = _uiState.value.selectedLoan ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val paymentsSnap = db.collection("users").document(uid).collection("loanPayments").whereEqualTo("loanId", loan.id).get().await()
                if (!paymentsSnap.isEmpty) {
                    emitEvent(LoansEvent.ShowToast("Cannot delete loan with payment history", true))
                    closeDeleteConfirm()
                    return@launch
                }
                db.collection("users").document(uid).collection("loans").document(loan.id).delete().await()
                emitEvent(LoansEvent.ShowToast("Loan deleted successfully"))
                closeDeleteConfirm()
            } catch (e: Exception) {
                emitEvent(LoansEvent.ShowToast("Failed to delete loan: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun emitEvent(event: LoansEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}