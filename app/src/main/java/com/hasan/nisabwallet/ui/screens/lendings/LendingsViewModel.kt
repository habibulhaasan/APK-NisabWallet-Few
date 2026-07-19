package com.hasan.nisabwallet.ui.screens.lendings

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

// ─── Data Models ───
data class LendingAccount(val id: String, val name: String, val balance: Double)

data class BorrowerContact(val phone: String = "", val email: String = "", val address: String = "")
data class Witness(val name: String = "", val contact: String = "")

data class Lending(
    val id: String = "",
    val borrowerName: String = "",
    val borrowerContact: BorrowerContact = BorrowerContact(),
    val lendingType: String = "qard-hasan",
    val principalAmount: Double = 0.0,
    val lendingDate: String = "",
    val dueDate: String = "",
    val repaymentType: String = "full-payment",
    val installmentAmount: Double? = null,
    val installmentFrequency: String = "monthly",
    val totalInstallments: Int? = null,
    val accountId: String = "",
    val notes: String = "",
    val category: String = "personal",
    val status: String = "active",
    val totalRepaid: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val paymentsReceived: Int = 0,
    val nextPaymentDue: String? = null,
    val witnesses: List<Witness> = emptyList(),
    val enableReminders: Boolean = true,
    val reminderDaysBefore: Int = 3,
    val createdAtMillis: Long = 0L
)

data class LendingStatusInfo(
    val isOverdue: Boolean,
    val remainingBalance: Double,
    val percentagePaid: Double,
    val daysOverdue: Int
)

data class LendingForm(
    val id: String? = null,
    val borrowerName: String = "",
    val borrowerPhone: String = "",
    val borrowerEmail: String = "",
    val borrowerAddress: String = "",
    val lendingType: String = "qard-hasan",
    val principalAmount: String = "",
    val lendingDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val dueDate: String = "",
    val repaymentType: String = "full-payment",
    val installmentAmount: String = "",
    val installmentFrequency: String = "monthly",
    val totalInstallments: String = "",
    val accountId: String = "",
    val notes: String = "",
    val category: String = "personal",
    val witness1Name: String = "",
    val witness1Contact: String = "",
    val witness2Name: String = "",
    val witness2Contact: String = "",
    val enableReminders: Boolean = true,
    val reminderDaysBefore: String = "3"
)

data class LendingPaymentForm(
    val amount: String = "",
    val paymentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val paymentMethod: String = "bank-transfer",
    val notes: String = ""
)

data class LendingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    
    val lendings: List<Lending> = emptyList(),
    val filteredLendings: List<Lending> = emptyList(),
    val accounts: List<LendingAccount> = emptyList(),
    
    val totalLent: Double = 0.0,
    val totalRepaid: Double = 0.0,
    val totalOutstanding: Double = 0.0,
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    val overdueCount: Int = 0,
    
    val filterStatus: String = "all",

    val showLendingModal: Boolean = false,
    val showPaymentModal: Boolean = false,
    val showDeleteModal: Boolean = false,
    val showReminderModal: Boolean = false,
    
    val lendingForm: LendingForm = LendingForm(),
    val paymentForm: LendingPaymentForm = LendingPaymentForm(),
    val reminderMessage: String = "",
    val selectedLending: Lending? = null
)

sealed class LendingsEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : LendingsEvent()
}

@HiltViewModel
class LendingsViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(LendingsUiState())
    val uiState: StateFlow<LendingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LendingsEvent>()
    val events = _events.asSharedFlow()

    private var lendingsListener: ListenerRegistration? = null
    private var accListener: ListenerRegistration? = null

    private var rawLendings = emptyList<Lending>()
    private var rawAccounts = emptyList<LendingAccount>()

    init {
        startRealTimeSync()
    }

    private fun startRealTimeSync() {
        val uid = auth.currentUser?.uid ?: return

        accListener = db.collection("users").document(uid).collection("accounts")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    rawAccounts = snap.documents.map { d ->
                        LendingAccount(d.id, d.getString("name") ?: "", d.getDouble("balance") ?: 0.0)
                    }
                    if (_uiState.value.lendingForm.accountId.isBlank() && rawAccounts.isNotEmpty()) {
                        _uiState.update { it.copy(lendingForm = it.lendingForm.copy(accountId = rawAccounts.first().id)) }
                    }
                }
            }

        lendingsListener = db.collection("users").document(uid).collection("lendings")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    emitEvent(LendingsEvent.ShowToast("Sync error: ${e.message}", true))
                    return@addSnapshotListener
                }
                if (snap != null) {
                    rawLendings = snap.documents.map { d ->
                        val contactMap = d.get("borrowerContact") as? Map<*, *>
                        val witnessesList = d.get("witnesses") as? List<Map<*, *>> ?: emptyList()
                        Lending(
                            id = d.id,
                            borrowerName = d.getString("borrowerName") ?: "",
                            borrowerContact = BorrowerContact(
                                phone = contactMap?.get("phone") as? String ?: "",
                                email = contactMap?.get("email") as? String ?: "",
                                address = contactMap?.get("address") as? String ?: ""
                            ),
                            lendingType = d.getString("lendingType") ?: "qard-hasan",
                            principalAmount = d.getDouble("principalAmount") ?: 0.0,
                            lendingDate = d.getString("lendingDate") ?: "",
                            dueDate = d.getString("dueDate") ?: "",
                            repaymentType = d.getString("repaymentType") ?: "full-payment",
                            installmentAmount = d.getDouble("installmentAmount"),
                            installmentFrequency = d.getString("installmentFrequency") ?: "monthly",
                            totalInstallments = d.getLong("totalInstallments")?.toInt(),
                            accountId = d.getString("accountId") ?: "",
                            notes = d.getString("notes") ?: "",
                            category = d.getString("category") ?: "personal",
                            status = d.getString("status") ?: "active",
                            totalRepaid = d.getDouble("totalRepaid") ?: 0.0,
                            remainingBalance = d.getDouble("remainingBalance") ?: 0.0,
                            paymentsReceived = d.getLong("paymentsReceived")?.toInt() ?: 0,
                            nextPaymentDue = d.getString("nextPaymentDue"),
                            witnesses = witnessesList.map { w -> Witness(w["name"] as? String ?: "", w["contact"] as? String ?: "") },
                            enableReminders = d.getBoolean("enableReminders") ?: true,
                            reminderDaysBefore = d.getLong("reminderDaysBefore")?.toInt() ?: 3,
                            createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                        )
                    }
                    combineAndEmit()
                }
            }
    }

    private fun combineAndEmit() {
        val state = _uiState.value
        
        var tLent = 0.0
        var tRepaid = 0.0
        var actCount = 0
        var compCount = 0
        var overCount = 0

        rawLendings.forEach { lending ->
            tLent += lending.principalAmount
            tRepaid += lending.totalRepaid
            if (lending.status == "active") actCount++
            if (lending.status == "completed") compCount++
            if (calculateLendingStatus(lending).isOverdue) overCount++
        }

        val filtered = rawLendings.filter { lending ->
            when (state.filterStatus) {
                "all" -> true
                "overdue" -> calculateLendingStatus(lending).isOverdue
                else -> lending.status == state.filterStatus
            }
        }

        _uiState.update { 
            it.copy(
                isLoading = false,
                lendings = rawLendings,
                filteredLendings = filtered,
                accounts = rawAccounts,
                totalLent = tLent,
                totalRepaid = tRepaid,
                totalOutstanding = tLent - tRepaid,
                activeCount = actCount,
                completedCount = compCount,
                overdueCount = overCount
            )
        }
    }

    fun calculateLendingStatus(lending: Lending): LendingStatusInfo {
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        val targetDateStr = lending.nextPaymentDue ?: lending.dueDate
        val targetDate = runCatching { sdf.parse(targetDateStr)?.time }.getOrNull() ?: today
        
        val isOverdue = targetDate < today && lending.status == "active"
        val daysOverdue = if (isOverdue) ceil((today - targetDate) / (1000.0 * 60 * 60 * 24)).toInt() else 0
        val percentage = if (lending.principalAmount > 0) (lending.totalRepaid / lending.principalAmount) * 100 else 0.0

        return LendingStatusInfo(isOverdue, lending.remainingBalance, percentage, daysOverdue)
    }

    override fun onCleared() {
        super.onCleared()
        lendingsListener?.remove()
        accListener?.remove()
    }

    fun setFilterStatus(s: String) { _uiState.update { it.copy(filterStatus = s) }; combineAndEmit() }

    fun openLendingModal(lending: Lending? = null) {
        if (lending != null) {
            _uiState.update {
                it.copy(
                    showLendingModal = true,
                    lendingForm = LendingForm(
                        id = lending.id, borrowerName = lending.borrowerName,
                        borrowerPhone = lending.borrowerContact.phone, borrowerEmail = lending.borrowerContact.email, borrowerAddress = lending.borrowerContact.address,
                        lendingType = lending.lendingType, principalAmount = lending.principalAmount.toString(), lendingDate = lending.lendingDate, dueDate = lending.dueDate,
                        repaymentType = lending.repaymentType, installmentAmount = lending.installmentAmount?.toString() ?: "",
                        installmentFrequency = lending.installmentFrequency, totalInstallments = lending.totalInstallments?.toString() ?: "",
                        accountId = lending.accountId, notes = lending.notes, category = lending.category,
                        witness1Name = lending.witnesses.getOrNull(0)?.name ?: "", witness1Contact = lending.witnesses.getOrNull(0)?.contact ?: "",
                        witness2Name = lending.witnesses.getOrNull(1)?.name ?: "", witness2Contact = lending.witnesses.getOrNull(1)?.contact ?: "",
                        enableReminders = lending.enableReminders, reminderDaysBefore = lending.reminderDaysBefore.toString()
                    )
                )
            }
        } else {
            _uiState.update { it.copy(showLendingModal = true, lendingForm = LendingForm(accountId = rawAccounts.firstOrNull()?.id ?: "")) }
        }
    }
    fun closeLendingModal() = _uiState.update { it.copy(showLendingModal = false) }
    fun updateLendingForm(update: (LendingForm) -> LendingForm) = _uiState.update { it.copy(lendingForm = update(it.lendingForm)) }

    fun openPaymentModal(lending: Lending) = _uiState.update {
        it.copy(
            showPaymentModal = true, selectedLending = lending,
            paymentForm = LendingPaymentForm(amount = lending.installmentAmount?.toString() ?: "")
        )
    }
    fun closePaymentModal() = _uiState.update { it.copy(showPaymentModal = false, selectedLending = null) }
    fun updatePaymentForm(update: (LendingPaymentForm) -> LendingPaymentForm) = _uiState.update { it.copy(paymentForm = update(it.paymentForm)) }

    fun openReminderModal(lending: Lending) {
        val amt = lending.installmentAmount ?: lending.remainingBalance
        val date = runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(lending.nextPaymentDue ?: lending.dueDate)!!) }.getOrDefault(lending.dueDate)
        val msg = "Assalamu Alaikum ${lending.borrowerName}, this is a friendly reminder that your payment of ৳$amt is due on $date. JazakAllah Khair!"
        _uiState.update { it.copy(showReminderModal = true, selectedLending = lending, reminderMessage = msg) }
    }
    fun closeReminderModal() = _uiState.update { it.copy(showReminderModal = false, selectedLending = null) }
    fun updateReminderMessage(msg: String) = _uiState.update { it.copy(reminderMessage = msg) }

    fun openDeleteModal(lending: Lending) = _uiState.update { it.copy(showDeleteModal = true, selectedLending = lending) }
    fun closeDeleteModal() = _uiState.update { it.copy(showDeleteModal = false, selectedLending = null) }

    private fun calculateNextPaymentDate(startDate: String, frequency: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = runCatching { sdf.parse(startDate) }.getOrNull() ?: Date()
        val cal = Calendar.getInstance().apply { time = date }
        if (frequency == "monthly") cal.add(Calendar.MONTH, 1)
        else if (frequency == "weekly") cal.add(Calendar.DAY_OF_YEAR, 7)
        return sdf.format(cal.time)
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

    fun saveLending() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.lendingForm

        if (form.borrowerName.isBlank() || form.principalAmount.isBlank() || form.dueDate.isBlank()) {
            emitEvent(LendingsEvent.ShowToast("Please fill in required fields", true))
            return
        }

        val principal = form.principalAmount.toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val data = mutableMapOf<String, Any?>(
                    "borrowerName" to form.borrowerName.trim(),
                    "borrowerContact" to mapOf("phone" to form.borrowerPhone, "email" to form.borrowerEmail, "address" to form.borrowerAddress),
                    "lendingType" to form.lendingType,
                    "principalAmount" to principal,
                    "interestRate" to 0.0,
                    "lendingDate" to form.lendingDate,
                    "dueDate" to form.dueDate,
                    "repaymentType" to form.repaymentType,
                    "accountId" to form.accountId,
                    "notes" to form.notes.trim(),
                    "category" to form.category,
                    "enableReminders" to form.enableReminders,
                    "reminderDaysBefore" to (form.reminderDaysBefore.toIntOrNull() ?: 3),
                    "reminderMethods" to listOf("whatsapp", "sms"),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                if (form.repaymentType == "installments") {
                    data["installmentAmount"] = form.installmentAmount.toDoubleOrNull()
                    data["installmentFrequency"] = form.installmentFrequency
                    data["totalInstallments"] = form.totalInstallments.toIntOrNull()
                    data["nextPaymentDue"] = calculateNextPaymentDate(form.lendingDate, form.installmentFrequency)
                } else {
                    data["nextPaymentDue"] = form.dueDate
                }

                val witnesses = mutableListOf<Map<String, String>>()
                if (form.witness1Name.isNotBlank()) witnesses.add(mapOf("name" to form.witness1Name, "contact" to form.witness1Contact))
                if (form.witness2Name.isNotBlank()) witnesses.add(mapOf("name" to form.witness2Name, "contact" to form.witness2Contact))
                data["witnesses"] = witnesses

                val ref = db.collection("users").document(uid).collection("lendings")
                if (form.id != null) {
                    ref.document(form.id).set(data, SetOptions.merge()).await()
                    emitEvent(LendingsEvent.ShowToast("Lending record updated"))
                } else {
                    data["status"] = "active"
                    data["totalRepaid"] = 0.0
                    data["remainingBalance"] = principal
                    data["paymentsReceived"] = 0
                    data["paymentsMissed"] = 0
                    data["createdAt"] = FieldValue.serverTimestamp()
                    ref.add(data).await()

                    // DOUBLE ENTRY: Deduct from Account & Log Expense Transaction
                    val acc = rawAccounts.find { it.id == form.accountId }
                    if (acc != null && principal > 0) {
                        db.collection("users").document(uid).collection("accounts").document(acc.id)
                            .update("balance", acc.balance - principal).await()
                        
                        val catId = ensureCategory(uid, "Lending Given", "Expense", "#8B5CF6")
                        db.collection("users").document(uid).collection("transactions").add(
                            mapOf(
                                "type" to "Expense", "amount" to principal, "accountId" to acc.id,
                                "categoryId" to catId, "description" to "Lent to ${form.borrowerName.trim()}",
                                "date" to form.lendingDate, "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }

                    emitEvent(LendingsEvent.ShowToast("Lending record created"))
                }
                closeLendingModal()
            } catch (e: Exception) {
                emitEvent(LendingsEvent.ShowToast("Save failed: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun submitPayment() {
        val uid = auth.currentUser?.uid ?: return
        val form = _uiState.value.paymentForm
        val lending = _uiState.value.selectedLending ?: return

        val amount = form.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            emitEvent(LendingsEvent.ShowToast("Please enter a valid amount", true))
            return
        }
        if (amount > lending.remainingBalance) {
            emitEvent(LendingsEvent.ShowToast("Amount exceeds remaining balance", true))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                db.collection("users").document(uid).collection("lendingPayments").add(
                    mapOf(
                        "lendingId" to lending.id, "amount" to amount, "paymentDate" to form.paymentDate,
                        "paymentMethod" to form.paymentMethod, "notes" to form.notes.trim(),
                        "principalPortion" to amount, "interestPortion" to 0.0,
                        "installmentNumber" to lending.paymentsReceived + 1,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                // DOUBLE ENTRY: Add to Account & Log Income Transaction
                val acc = rawAccounts.find { it.id == lending.accountId }
                if (acc != null) {
                    db.collection("users").document(uid).collection("accounts").document(acc.id)
                        .update("balance", acc.balance + amount).await()
                        
                    val catId = ensureCategory(uid, "Lending Repayment", "Income", "#10B981")
                    db.collection("users").document(uid).collection("transactions").add(
                        mapOf(
                            "type" to "Income", "amount" to amount, "accountId" to acc.id,
                            "categoryId" to catId, "description" to "Repayment from ${lending.borrowerName}",
                            "date" to form.paymentDate, "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                val newTotalRepaid = lending.totalRepaid + amount
                val newRemaining = lending.principalAmount - newTotalRepaid
                val newStatus = if (newRemaining <= 0) "completed" else "active"

                val updateData = mutableMapOf<String, Any>(
                    "totalRepaid" to newTotalRepaid, "remainingBalance" to newRemaining,
                    "status" to newStatus, "paymentsReceived" to lending.paymentsReceived + 1,
                    "lastPaymentDate" to form.paymentDate, "updatedAt" to FieldValue.serverTimestamp()
                )

                if (lending.repaymentType == "installments" && newStatus == "active") {
                    updateData["nextPaymentDue"] = calculateNextPaymentDate(form.paymentDate, lending.installmentFrequency)
                }

                db.collection("users").document(uid).collection("lendings").document(lending.id).update(updateData).await()

                if (newStatus == "completed") emitEvent(LendingsEvent.ShowToast("🎉 Lending fully repaid!"))
                else emitEvent(LendingsEvent.ShowToast("Payment recorded successfully"))

                closePaymentModal()
            } catch (e: Exception) {
                emitEvent(LendingsEvent.ShowToast("Error processing payment: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun sendReminder() {
        val uid = auth.currentUser?.uid ?: return
        val lending = _uiState.value.selectedLending ?: return
        val msg = _uiState.value.reminderMessage

        if (msg.isBlank()) {
            emitEvent(LendingsEvent.ShowToast("Please enter a message", true))
            return
        }

        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("lendingReminders").add(
                    mapOf(
                        "lendingId" to lending.id, "borrowerName" to lending.borrowerName,
                        "borrowerContact" to lending.borrowerContact.phone, "message" to msg,
                        "sentAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                db.collection("users").document(uid).collection("lendings").document(lending.id)
                    .update("lastReminderSent", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())).await()
                
                emitEvent(LendingsEvent.ShowToast("Reminder logged successfully"))
                closeReminderModal()
            } catch (e: Exception) {
                emitEvent(LendingsEvent.ShowToast("Error logging reminder", true))
            }
        }
    }

    fun deleteLending() {
        val uid = auth.currentUser?.uid ?: return
        val lending = _uiState.value.selectedLending ?: return

        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("lendings").document(lending.id).delete().await()
                emitEvent(LendingsEvent.ShowToast("Lending record deleted"))
                closeDeleteModal()
            } catch (e: Exception) {
                emitEvent(LendingsEvent.ShowToast("Error deleting record", true))
            }
        }
    }

    private fun emitEvent(event: LendingsEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}