package com.hasan.nisabwallet.ui.screens.lendings.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.hasan.nisabwallet.ui.screens.lendings.BorrowerContact
import com.hasan.nisabwallet.ui.screens.lendings.Lending
import com.hasan.nisabwallet.ui.screens.lendings.LendingAccount
import com.hasan.nisabwallet.ui.screens.lendings.Witness
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ceil

// ─── Data Models ───
data class LendingPayment(
    val id: String = "",
    val lendingId: String = "",
    val amount: Double = 0.0,
    val paymentDate: String = "",
    val paymentMethod: String = "",
    val notes: String = "",
    val installmentNumber: Int? = null,
    val createdAtMillis: Long = 0L
)

data class LendingReminder(
    val id: String = "",
    val lendingId: String = "",
    val borrowerContact: String = "",
    val message: String = "",
    val sentAtMillis: Long = 0L
)

data class LendingDetailStatusInfo(
    val isOverdue: Boolean,
    val remainingBalance: Double,
    val percentagePaid: Double,
    val daysOverdue: Int,
    val totalPayments: Int,
    val expectedPayments: Int,
    val paymentsOnTime: Int,
    val paymentRate: Double
)

data class LendingDetailUiState(
    val isLoading: Boolean = true,
    val lending: Lending? = null,
    val payments: List<LendingPayment> = emptyList(),
    val reminders: List<LendingReminder> = emptyList(),
    val accounts: List<LendingAccount> = emptyList(),
    val statusInfo: LendingDetailStatusInfo? = null,
    val showDeleteConfirm: Boolean = false
)

sealed class LendingDetailEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : LendingDetailEvent()
    object NavigateBack : LendingDetailEvent()
    object TriggerCsvExport : LendingDetailEvent()
}

@HiltViewModel
class LendingDetailViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val lendingId: String = checkNotNull(savedStateHandle["lendingId"])

    private val _uiState = MutableStateFlow(LendingDetailUiState())
    val uiState: StateFlow<LendingDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LendingDetailEvent>()
    val events = _events.asSharedFlow()

    private var lendingListener: ListenerRegistration? = null
    private var paymentsListener: ListenerRegistration? = null
    private var remindersListener: ListenerRegistration? = null
    private var accountsListener: ListenerRegistration? = null

    private var rawLending: Lending? = null
    private var rawPayments = emptyList<LendingPayment>()
    private var rawReminders = emptyList<LendingReminder>()
    private var rawAccounts = emptyList<LendingAccount>()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        accountsListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawAccounts = snap.documents.map { d ->
                        LendingAccount(d.id, d.getString("name") ?: "", d.getDouble("balance") ?: 0.0)
                    }
                    combineAndEmit()
                }
            }

        lendingListener = db.collection("users").document(uid).collection("lendings").document(lendingId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    emitEvent(LendingDetailEvent.ShowToast("Sync error: ${e.message}", true))
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    val contactMap = snap.get("borrowerContact") as? Map<*, *>
                    val witnessesList = snap.get("witnesses") as? List<Map<*, *>> ?: emptyList()
                    rawLending = Lending(
                        id = snap.id,
                        borrowerName = snap.getString("borrowerName") ?: "",
                        borrowerContact = BorrowerContact(
                            phone = contactMap?.get("phone") as? String ?: "",
                            email = contactMap?.get("email") as? String ?: "",
                            address = contactMap?.get("address") as? String ?: ""
                        ),
                        lendingType = snap.getString("lendingType") ?: "qard-hasan",
                        principalAmount = snap.getDouble("principalAmount") ?: 0.0,
                        lendingDate = snap.getString("lendingDate") ?: "",
                        dueDate = snap.getString("dueDate") ?: "",
                        repaymentType = snap.getString("repaymentType") ?: "full-payment",
                        installmentAmount = snap.getDouble("installmentAmount"),
                        installmentFrequency = snap.getString("installmentFrequency") ?: "monthly",
                        totalInstallments = snap.getLong("totalInstallments")?.toInt(),
                        accountId = snap.getString("accountId") ?: "",
                        notes = snap.getString("notes") ?: "",
                        category = snap.getString("category") ?: "personal",
                        status = snap.getString("status") ?: "active",
                        totalRepaid = snap.getDouble("totalRepaid") ?: 0.0,
                        remainingBalance = snap.getDouble("remainingBalance") ?: 0.0,
                        paymentsReceived = snap.getLong("paymentsReceived")?.toInt() ?: 0,
                        nextPaymentDue = snap.getString("nextPaymentDue"),
                        witnesses = witnessesList.map { w -> Witness(w["name"] as? String ?: "", w["contact"] as? String ?: "") },
                        enableReminders = snap.getBoolean("enableReminders") ?: true,
                        reminderDaysBefore = snap.getLong("reminderDaysBefore")?.toInt() ?: 3,
                        createdAtMillis = snap.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                    combineAndEmit()
                } else {
                    emitEvent(LendingDetailEvent.ShowToast("Lending record not found", true))
                    emitEvent(LendingDetailEvent.NavigateBack)
                }
            }

        paymentsListener = db.collection("users").document(uid).collection("lendingPayments")
            .whereEqualTo("lendingId", lendingId)
            .orderBy("paymentDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawPayments = snap.documents.map { d ->
                        LendingPayment(
                            id = d.id,
                            lendingId = d.getString("lendingId") ?: "",
                            amount = d.getDouble("amount") ?: 0.0,
                            paymentDate = d.getString("paymentDate") ?: "",
                            paymentMethod = d.getString("paymentMethod") ?: "",
                            notes = d.getString("notes") ?: "",
                            installmentNumber = d.getLong("installmentNumber")?.toInt(),
                            createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        )
                    }
                    combineAndEmit()
                }
            }

        remindersListener = db.collection("users").document(uid).collection("lendingReminders")
            .whereEqualTo("lendingId", lendingId)
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawReminders = snap.documents.map { d ->
                        LendingReminder(
                            id = d.id,
                            lendingId = d.getString("lendingId") ?: "",
                            borrowerContact = d.getString("borrowerContact") ?: "",
                            message = d.getString("message") ?: "",
                            sentAtMillis = d.getTimestamp("sentAt")?.toDate()?.time ?: 0L
                        )
                    }
                    combineAndEmit()
                }
            }
    }

    private fun combineAndEmit() {
        val lending = rawLending ?: return
        
        // Compute Status[cite: 14]
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        val targetDateStr = lending.nextPaymentDue ?: lending.dueDate
        val targetDate = runCatching { sdf.parse(targetDateStr)?.time }.getOrNull() ?: today
        
        val isOverdue = targetDate < today && lending.status == "active"
        val daysOverdue = if (isOverdue) ceil((today - targetDate) / (1000.0 * 60 * 60 * 24)).toInt() else 0
        val percentagePaid = if (lending.principalAmount > 0) (lending.totalRepaid / lending.principalAmount) * 100 else 0.0

        val totalPayments = rawPayments.size
        val expectedPayments = lending.totalInstallments ?: 1
        
        var paymentsOnTime = 0
        rawPayments.forEach { p ->
            val payDate = runCatching { sdf.parse(p.paymentDate)?.time }.getOrNull() ?: 0L
            if (payDate <= targetDate) paymentsOnTime++
        }
        val paymentRate = if (totalPayments > 0) (paymentsOnTime.toDouble() / totalPayments) * 100 else 0.0

        val statusInfo = LendingDetailStatusInfo(isOverdue, lending.remainingBalance, percentagePaid, daysOverdue, totalPayments, expectedPayments, paymentsOnTime, paymentRate)

        _uiState.update { 
            it.copy(
                isLoading = false,
                lending = lending,
                payments = rawPayments,
                reminders = rawReminders,
                accounts = rawAccounts,
                statusInfo = statusInfo
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        lendingListener?.remove()
        paymentsListener?.remove()
        remindersListener?.remove()
        accountsListener?.remove()
    }

    // ─── Actions ───

    fun openDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = true) }
    fun closeDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = false) }

    fun deleteLending() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("lendings").document(lendingId).delete().await()
                emitEvent(LendingDetailEvent.ShowToast("Lending record deleted successfully"))
                emitEvent(LendingDetailEvent.NavigateBack)
            } catch (e: Exception) {
                emitEvent(LendingDetailEvent.ShowToast("Failed to delete record: ${e.message}", true))
            }
        }
    }

    fun requestCsvExport() {
        if (rawPayments.isEmpty()) {
            emitEvent(LendingDetailEvent.ShowToast("No payments to export", true))
            return
        }
        viewModelScope.launch { _events.emit(LendingDetailEvent.TriggerCsvExport) }
    }

    fun executeCsvExport(uri: Uri) {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfOut = SimpleDateFormat("dd/MM/yyyy", Locale.US)

        viewModelScope.launch {
            try {
                val csvContent = buildString {
                    append("Date,Amount,Method,Installment,Notes\n")
                    rawPayments.forEach { p ->
                        val displayDate = runCatching { sdfOut.format(sdfIn.parse(p.paymentDate)!!) }.getOrDefault(p.paymentDate)
                        val instStr = p.installmentNumber?.toString() ?: "-"
                        append("\"$displayDate\",\"${p.amount}\",\"${p.paymentMethod}\",\"$instStr\",\"${p.notes.replace("\"", "\"\"")}\"\n")
                    }
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        BufferedWriter(OutputStreamWriter(out)).use { writer ->
                            writer.write(csvContent)
                        }
                    }
                }
                emitEvent(LendingDetailEvent.ShowToast("Payments exported successfully"))
            } catch (e: Exception) {
                emitEvent(LendingDetailEvent.ShowToast("Export failed: ${e.message}", true))
            }
        }
    }

    fun getAccountName(id: String): String = rawAccounts.find { it.id == id }?.name ?: "Unknown"

    private fun emitEvent(event: LendingDetailEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}