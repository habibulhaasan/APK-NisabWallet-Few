package com.hasan.nisabwallet.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─── Data Models ───
data class SubscriptionItem(
    val id: String = "",
    val planName: String = "",
    val status: String = "",
    val amount: Double = 0.0,
    val durationDays: Int = 0,
    val paymentMethod: String = "",
    val transactionId: String = "",
    val isExtension: Boolean = false,
    val rejectionReason: String? = null,
    val startDate: String = "",
    val endDate: String = ""
)

data class AppPreferences(
    val theme: String = "light",
    val currency: String = "BDT",
    val dateFormat: String = "DD/MM/YYYY",
    val language: String = "en"
)

data class SettingsUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val syncStatus: String = "Connecting...", // Added missing syncStatus
    val userEmail: String = "",
    val displayName: String = "",
    val uid: String = "",
    val creationTime: String = "",
    val currentSubscription: SubscriptionItem? = null,
    val subscriptionHistory: List<SubscriptionItem> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val exportMode: String? = null,
    val selectedCollections: Set<String> = emptySet(),
    val exportFormat: String = "json",
    val dateFrom: String = "",
    val dateTo: String = ""
)

sealed class SettingsEvent {
    data class ShowToast(val message: String, val isError: Boolean = false) : SettingsEvent()
    object TriggerJsonFileCreate : SettingsEvent()
    object TriggerCsvFolderSelection : SettingsEvent()
    object LogoutComplete : SettingsEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    private var prefsListener: ListenerRegistration? = null
    private var subsListener: ListenerRegistration? = null

    val allCollections = listOf(
        "accounts" to "Accounts", "transactions" to "Transactions", "transfers" to "Transfers",
        "categories" to "Categories", "zakatCycles" to "Zakat Cycles", "zakatPayments" to "Zakat Payments",
        "lendings" to "Lendings", "loans" to "Loans", "investments" to "Investments",
        "goals" to "Goals", "jewellery" to "Jewellery", "ribaTransactions" to "Riba Transactions",
        "financialGoals" to "Financial Goals", "settings" to "Settings"
    )

    init {
        val user = auth.currentUser
        if (user != null) {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            val timestamp = user.metadata?.creationTimestamp
            val creationString = if (timestamp != null) sdf.format(Date(timestamp)) else "N/A"

            _uiState.update {
                it.copy(
                    userEmail = user.email ?: "",
                    displayName = user.displayName ?: "",
                    uid = user.uid,
                    creationTime = creationString,
                    selectedCollections = allCollections.map { c -> c.first }.toSet()
                )
            }
            startRealTimeSync(user.uid)
        }
    }

    private fun startRealTimeSync(uid: String) {
        prefsListener = db.collection("users").document(uid).collection("settings")
            .limit(1)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val status = when {
                        snap.metadata.hasPendingWrites() -> "Syncing..."
                        snap.metadata.isFromCache() -> "Offline (Cached)"
                        else -> "Synced"
                    }

                    val p = if (!snap.isEmpty) {
                        val d = snap.documents.first()
                        AppPreferences(
                            theme = d.getString("theme") ?: "light",
                            currency = d.getString("currency") ?: "BDT",
                            dateFormat = d.getString("dateFormat") ?: "DD/MM/YYYY",
                            language = d.getString("language") ?: "en"
                        )
                    } else AppPreferences()

                    _uiState.update { it.copy(preferences = p, syncStatus = status) }
                }
            }

        subsListener = db.collection("users").document(uid).collection("subscriptions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val history = snap.documents.map { d ->
                        SubscriptionItem(
                            id = d.id,
                            planName = d.getString("planName") ?: "Trial Plan",
                            status = d.getString("status") ?: "",
                            amount = d.getDouble("amount") ?: 0.0,
                            durationDays = d.getLong("durationDays")?.toInt() ?: 0,
                            paymentMethod = d.getString("paymentMethod") ?: "",
                            transactionId = d.getString("transactionId") ?: "",
                            isExtension = d.getBoolean("isExtension") ?: false,
                            rejectionReason = d.getString("rejectionReason"),
                            startDate = d.getString("startDate") ?: "",
                            endDate = d.getString("endDate") ?: ""
                        )
                    }
                    val current = history.find { it.status == "active" || it.status == "trial" }
                        ?: history.firstOrNull()

                    _uiState.update { it.copy(subscriptionHistory = history, currentSubscription = current) }
                }
            }
    }

    fun updateDisplayName(name: String) {
        _uiState.update { it.copy(displayName = name) }
    }

    fun handleUpdateProfile() {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(_uiState.value.displayName)
                    .build()
                user.updateProfile(profileUpdates).await()
                _events.emit(SettingsEvent.ShowToast("Profile updated successfully!"))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowToast("Update failed: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun updateTheme(v: String) = _uiState.update { it.copy(preferences = it.preferences.copy(theme = v)) }
    fun updateCurrency(v: String) = _uiState.update { it.copy(preferences = it.preferences.copy(currency = v)) }
    fun updateDateFormat(v: String) = _uiState.update { it.copy(preferences = it.preferences.copy(dateFormat = v)) }
    fun updateLanguage(v: String) = _uiState.update { it.copy(preferences = it.preferences.copy(language = v)) }

    fun savePreferences() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val p = _uiState.value.preferences
                val payload = mapOf(
                    "theme" to p.theme, "currency" to p.currency,
                    "dateFormat" to p.dateFormat, "language" to p.language,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val ref = db.collection("users").document(uid).collection("settings")
                val existing = ref.get().await()
                if (existing.isEmpty) ref.add(payload).await()
                else existing.documents.first().reference.set(payload, SetOptions.merge()).await()

                _events.emit(SettingsEvent.ShowToast("Preferences saved! Change will reflect dynamically."))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowToast("Failed: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun setExportMode(mode: String?) = _uiState.update { it.copy(exportMode = mode) }
    fun setExportFormat(fmt: String) = _uiState.update { it.copy(exportFormat = fmt) }
    fun setDateFrom(d: String) = _uiState.update { it.copy(dateFrom = d) }
    fun setDateTo(d: String) = _uiState.update { it.copy(dateTo = d) }

    fun toggleCollection(key: String) = _uiState.update {
        val set = it.selectedCollections.toMutableSet()
        if (set.contains(key)) set.remove(key) else set.add(key)
        it.copy(selectedCollections = set)
    }

    fun selectAllCollections() = _uiState.update { it.copy(selectedCollections = allCollections.map { c -> c.first }.toSet()) }
    fun selectNoCollections() = _uiState.update { it.copy(selectedCollections = emptySet()) }

    fun requestExportAccess() {
        viewModelScope.launch {
            if (_uiState.value.exportFormat == "json") {
                _events.emit(SettingsEvent.TriggerJsonFileCreate)
            } else {
                _events.emit(SettingsEvent.TriggerCsvFolderSelection)
            }
        }
    }

    fun executeJsonExport(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val collectionsObj = JSONObject()
                val targetCols = if (state.exportMode == "all") allCollections.map { it.first } else state.selectedCollections.toList()

                for (colKey in targetCols) {
                    val array = fetchCollectionData(uid, colKey, state.dateFrom, state.dateTo)
                    collectionsObj.put(colKey, array)
                }

                val masterObj = JSONObject().apply {
                    put("exportDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
                    put("exportedBy", JSONObject().put("email", state.userEmail).put("displayName", state.displayName))
                    put("collections", collectionsObj)
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        BufferedWriter(OutputStreamWriter(out)).use { writer ->
                            writer.write(masterObj.toString(2))
                        }
                    }
                }
                _events.emit(SettingsEvent.ShowToast("Data backup exported successfully!"))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowToast("Export crash: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun executeCsvExport(treeUri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val targetCols = if (state.exportMode == "all") allCollections.map { it.first } else state.selectedCollections.toList()
                val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri) ?: throw Exception("Directory read write crash")

                withContext(Dispatchers.IO) {
                    for (colKey in targetCols) {
                        val array = fetchCollectionData(uid, colKey, state.dateFrom, state.dateTo)
                        if (array.length() == 0) continue

                        val csvContent = buildCsvData(array)
                        val fileName = "${colKey}-${System.currentTimeMillis()}.csv"
                        val file = pickedDir.createFile("text/csv", fileName)

                        file?.uri?.let { fileUri ->
                            context.contentResolver.openOutputStream(fileUri)?.use { stream ->
                                BufferedWriter(OutputStreamWriter(stream)).use { writer ->
                                    writer.write(csvContent)
                                }
                            }
                        }
                    }
                }
                _events.emit(SettingsEvent.ShowToast("CSV database exports added inside selected directory."))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowToast("CSV creation error: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun fetchCollectionData(uid: String, colKey: String, fromDate: String, toDate: String): JSONArray {
        val snap = db.collection("users").document(uid).collection(colKey).get().await()
        val array = JSONArray()

        val hasDateFilter = (colKey in listOf("transactions", "transfers", "ribaTransactions")) && (fromDate.isNotBlank() || toDate.isNotBlank())

        for (doc in snap.documents) {
            val obj = JSONObject()
            obj.put("id", doc.id)
            doc.data?.forEach { (k, v) ->
                if (v is com.google.firebase.Timestamp) obj.put(k, v.toDate().time)
                else obj.put(k, v)
            }

            if (colKey == "jewellery") {
                val subSnap = db.collection("users").document(uid).collection("jewellery").document(doc.id).collection("priceHistory").get().await()
                val subArray = JSONArray()
                for (sDoc in subSnap.documents) {
                    val sObj = JSONObject()
                    sObj.put("id", sDoc.id)
                    sDoc.data?.forEach { (sk, sv) -> sObj.put(sk, sv) }
                    subArray.put(sObj)
                }
                obj.put("priceHistory", subArray)
            }

            if (hasDateFilter) {
                val dateStr = doc.getString("date") ?: ""
                if (fromDate.isNotBlank() && dateStr < fromDate) continue
                if (toDate.isNotBlank() && dateStr > toDate) continue
            }
            array.put(obj)
        }
        return array
    }

    private fun buildCsvData(array: JSONArray): String {
        if (array.length() == 0) return ""
        val keys = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            obj.keys().forEach { if (!keys.contains(it)) keys.add(it) }
        }

        val sb = StringBuilder()
        sb.append(keys.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }).append("\n")

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val row = keys.map { k ->
                val v = obj.opt(k)?.toString() ?: ""
                "\"${v.replace("\"", "\"\"")}\""
            }
            sb.append(row.joinToString(",")).append("\n")
        }
        return sb.toString()
    }

    fun executeImport(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val rawText = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().use { it.readText() }
                    }
                } ?: throw Exception("Unreadable stream resource")

                val master = JSONObject(rawText)
                val cols = master.optJSONObject("collections") ?: master // Fallback wrapper

                var importedCount = 0
                cols.keys().forEach { key ->
                    val arr = cols.optJSONArray(key) ?: return@forEach
                    for (i in 0 until arr.length()) {
                        val rawRow = arr.getJSONObject(i)
                        val rowMap = mutableMapOf<String, Any?>()

                        rawRow.keys().forEach { k ->
                            if (k != "id" && k != "priceHistory") rowMap[k] = rawRow.get(k)
                        }

                        rowMap["createdAt"] = FieldValue.serverTimestamp()
                        val singleKeyRef = db.collection("users").document(uid).collection(key).add(rowMap).await()

                        if (key == "jewellery" && rawRow.has("priceHistory")) {
                            val subHistory = rawRow.optJSONArray("priceHistory") ?: JSONArray()
                            for (j in 0 until subHistory.length()) {
                                val subObj = subHistory.getJSONObject(j)
                                val subMap = mutableMapOf<String, Any?>()
                                subObj.keys().forEach { sk -> if (sk != "id") subMap[sk] = subObj.get(sk) }
                                subMap["createdAt"] = FieldValue.serverTimestamp()
                                db.collection("users").document(uid).collection("jewellery").document(singleKeyRef.id).collection("priceHistory").add(subMap).await()
                            }
                        }
                        importedCount++
                    }
                }
                _events.emit(SettingsEvent.ShowToast("Successfully restored $importedCount data nodes into live instances."))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowToast("Import extraction crash: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun executePurgeWallet(confirmation: String) {
        val uid = auth.currentUser?.uid ?: return
        if (confirmation != "DELETE") return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val paths = listOf("accounts", "transactions", "transfers", "categories", "zakatCycles", "zakatPayments", "lendings", "loans", "investments", "goals", "jewellery", "ribaTransactions", "financialGoals", "settings")
                for (p in paths) {
                    val snap = db.collection("users").document(uid).collection(p).get().await()
                    for (doc in snap.documents) {
                        if (p == "jewellery") {
                            val sub = db.collection("users").document(uid).collection("jewellery").document(doc.id).collection("priceHistory").get().await()
                            sub.documents.forEach { it.reference.delete().await() }
                        }
                        doc.reference.delete().await()
                    }
                }
                _events.emit(SettingsEvent.ShowToast("Purged all cloud instances. Active session maintained."))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowToast("Purge failed: ${e.message}", true))
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        auth.signOut()
        onComplete()
    }
}