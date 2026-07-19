package com.hasan.nisabwallet.ui.screens.loans.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.hasan.nisabwallet.ui.screens.loans.Loan
import com.hasan.nisabwallet.ui.screens.loans.LoanAccount
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
import java.util.Locale
import javax.inject.Inject

// ─── Data Models ───
data class LoanPayment(
    val id: String = "",
    val loanId: String = "",
    val amount: Double = 0.0,
    val paymentDate: String = "",
    val principalPaid: Double = 0.0,
    val interestPaid: Double = 0.0,
    val accountId: String = "",
    val notes: String = "",
    val createdAtMillis: Long = 0L
)

data class AmortizationRow(
    val month: Int,
    val payment: Double,
    val principal: Double,
    val interest: Double,
    val balance: Double
)

data class EarlyPayoffResult(
    val monthsRemaining: Int,
    val totalInterest: Double,
    val savedInterest: Double
)

data class LoanDetailUiState(
    val isLoading: Boolean = true,
    val loan: Loan? = null,
    val payments: List<LoanPayment> = emptyList(),
    val accounts: List<LoanAccount> = emptyList(),
    val amortizationSchedule: List<AmortizationRow> = emptyList(),
    val earlyPayoff500: EarlyPayoffResult? = null,
    val earlyPayoff1000: EarlyPayoffResult? = null,
    val showDeleteConfirm: Boolean = false
)

sealed class LoanDetailEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : LoanDetailEvent()
    object NavigateBack : LoanDetailEvent()
    object TriggerCsvExport : LoanDetailEvent()
}

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val loanId: String = checkNotNull(savedStateHandle["loanId"])

    private val _uiState = MutableStateFlow(LoanDetailUiState())
    val uiState: StateFlow<LoanDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoanDetailEvent>()
    val events = _events.asSharedFlow()

    private var loanListener: ListenerRegistration? = null
    private var paymentsListener: ListenerRegistration? = null
    private var accountsListener: ListenerRegistration? = null

    private var rawLoan: Loan? = null
    private var rawPayments = emptyList<LoanPayment>()
    private var rawAccounts = emptyList<LoanAccount>()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        accountsListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawAccounts = snap.documents.map { d ->
                        LoanAccount(d.id, d.getString("name") ?: "", d.getDouble("balance") ?: 0.0)
                    }
                    combineAndEmit()
                }
            }

        loanListener = db.collection("users").document(uid).collection("loans").document(loanId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    emitEvent(LoanDetailEvent.ShowToast("Sync error: ${e.message}", true))
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    rawLoan = Loan(
                        id = snap.id,
                        lenderName = snap.getString("lenderName") ?: "",
                        loanType = snap.getString("loanType") ?: "qard-hasan",
                        principalAmount = snap.getDouble("principalAmount") ?: 0.0,
                        interestRate = snap.getDouble("interestRate") ?: 0.0,
                        monthlyPayment = snap.getDouble("monthlyPayment"),
                        totalMonths = snap.getLong("totalMonths")?.toInt() ?: 0,
                        startDate = snap.getString("startDate") ?: "",
                        endDate = snap.getString("endDate"),
                        totalPaid = snap.getDouble("totalPaid") ?: 0.0,
                        remainingBalance = snap.getDouble("remainingBalance") ?: 0.0,
                        status = snap.getString("status") ?: "active",
                        nextPaymentDue = snap.getString("nextPaymentDue"),
                        lastPaymentDate = snap.getString("lastPaymentDate"),
                        notes = snap.getString("notes") ?: "",
                        accountId = snap.getString("accountId") ?: "",
                        totalInterest = snap.getDouble("totalInterest") ?: 0.0,
                        totalRepayment = snap.getDouble("totalRepayment") ?: 0.0,
                        enableReminders = snap.getBoolean("enableReminders") ?: true,
                        createdAtMillis = snap.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                    combineAndEmit()
                } else {
                    emitEvent(LoanDetailEvent.ShowToast("Loan not found", true))
                    emitEvent(LoanDetailEvent.NavigateBack)
                }
            }

        paymentsListener = db.collection("users").document(uid).collection("loanPayments")
            .whereEqualTo("loanId", loanId)
            .orderBy("paymentDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawPayments = snap.documents.map { d ->
                        LoanPayment(
                            id = d.id,
                            loanId = d.getString("loanId") ?: "",
                            amount = d.getDouble("amount") ?: 0.0,
                            paymentDate = d.getString("paymentDate") ?: "",
                            principalPaid = d.getDouble("principalPaid") ?: 0.0,
                            interestPaid = d.getDouble("interestPaid") ?: 0.0,
                            accountId = d.getString("accountId") ?: "",
                            notes = d.getString("notes") ?: "",
                            createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        )
                    }
                    combineAndEmit()
                }
            }
    }

    private fun combineAndEmit() {
        val loan = rawLoan ?: return

        val schedule = generateAmortizationSchedule(loan)
        val payoff500 = calculateEarlyPayoff(loan, 500.0)
        val payoff1000 = calculateEarlyPayoff(loan, 1000.0)

        _uiState.update {
            it.copy(
                isLoading = false,
                loan = loan,
                payments = rawPayments,
                accounts = rawAccounts,
                amortizationSchedule = schedule,
                earlyPayoff500 = payoff500,
                earlyPayoff1000 = payoff1000
            )
        }
    }

    // ─── Financial Engine ───

    private fun generateAmortizationSchedule(loan: Loan): List<AmortizationRow> {
        if (loan.loanType == "qard-hasan" || loan.monthlyPayment == null) return emptyList()

        val schedule = mutableListOf<AmortizationRow>()
        var balance = loan.principalAmount
        val monthlyRate = loan.interestRate / 12.0 / 100.0
        val monthlyPmt = loan.monthlyPayment
        val totalMonths = loan.totalMonths

        for (month in 1..totalMonths) {
            val interestPayment = balance * monthlyRate
            val principalPayment = monthlyPmt - interestPayment
            balance -= principalPayment

            schedule.add(
                AmortizationRow(
                    month = month,
                    payment = monthlyPmt,
                    principal = principalPayment,
                    interest = interestPayment,
                    balance = if (balance > 0) balance else 0.0
                )
            )
            if (balance <= 0) break
        }
        return schedule
    }

    private fun calculateEarlyPayoff(loan: Loan, extraPayment: Double): EarlyPayoffResult? {
        if (loan.loanType == "qard-hasan" || loan.monthlyPayment == null) return null

        var balance = loan.remainingBalance
        val monthlyRate = loan.interestRate / 12.0 / 100.0
        val totalPayment = loan.monthlyPayment + extraPayment
        var months = 0
        var totalInterest = 0.0

        while (balance > 0 && months < 1000) {
            val interestPmt = balance * monthlyRate
            val principalPmt = totalPayment - interestPmt
            totalInterest += interestPmt
            balance -= principalPmt
            months++
            if (balance <= 0) break
        }

        var origBalance = loan.remainingBalance
        var origMonths = 0
        var origInterest = 0.0

        while (origBalance > 0 && origMonths < 1000) {
            val interestPmt = origBalance * monthlyRate
            val principalPmt = loan.monthlyPayment - interestPmt
            origInterest += interestPmt
            origBalance -= principalPmt
            origMonths++
            if (origBalance <= 0) break
        }

        return EarlyPayoffResult(
            monthsRemaining = months,
            totalInterest = totalInterest,
            savedInterest = origInterest - totalInterest
        )
    }

    // ─── Intents ───

    fun openDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = true) }
    fun closeDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = false) }

    fun deleteLoan() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                if (rawPayments.isNotEmpty()) {
                    emitEvent(LoanDetailEvent.ShowToast("Cannot delete loan with payment history", true))
                    closeDeleteConfirm()
                    return@launch
                }
                db.collection("users").document(uid).collection("loans").document(loanId).delete().await()
                emitEvent(LoanDetailEvent.ShowToast("Loan deleted successfully"))
                emitEvent(LoanDetailEvent.NavigateBack)
            } catch (e: Exception) {
                emitEvent(LoanDetailEvent.ShowToast("Failed to delete loan: ${e.message}", true))
            }
        }
    }

    fun requestCsvExport() {
        if (rawPayments.isEmpty()) {
            emitEvent(LoanDetailEvent.ShowToast("No payment history to export", true))
            return
        }
        viewModelScope.launch { _events.emit(LoanDetailEvent.TriggerCsvExport) }
    }

    fun executeCsvExport(uri: Uri) {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfOut = SimpleDateFormat("dd/MM/yyyy", Locale.US)

        viewModelScope.launch {
            try {
                val csvContent = buildString {
                    append("Date,Amount,Principal,Interest,Account,Notes\n")
                    rawPayments.forEach { p ->
                        val displayDate = try {
                            sdfOut.format(sdfIn.parse(p.paymentDate)!!)
                        } catch (e: Exception) {
                            p.paymentDate
                        }
                        val accName = rawAccounts.find { it.id == p.accountId }?.name ?: "Unknown"
                        append("\"$displayDate\",\"${p.amount}\",\"${p.principalPaid}\",\"${p.interestPaid}\",\"$accName\",\"${p.notes.replace("\"", "\"\"")}\"\n")
                    }
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        BufferedWriter(OutputStreamWriter(out)).use { writer ->
                            writer.write(csvContent)
                        }
                    }
                }
                emitEvent(LoanDetailEvent.ShowToast("Payment history exported successfully"))
            } catch (e: Exception) {
                emitEvent(LoanDetailEvent.ShowToast("Export failed: ${e.message}", true))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loanListener?.remove()
        paymentsListener?.remove()
        accountsListener?.remove()
    }

    private fun emitEvent(event: LoanDetailEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}