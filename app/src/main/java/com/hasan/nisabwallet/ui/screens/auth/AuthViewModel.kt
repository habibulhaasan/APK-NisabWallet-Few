package com.hasan.nisabwallet.ui.screens.auth

// New — there was no login/register screen anywhere in the uploaded project.
// Every other ViewModel (Dashboard/Transactions/MonthlyLedger/MonthlyGrocery)
// does `auth.currentUser?.uid ?: return`, i.e. they silently no-op if nobody
// is signed in. This ViewModel + LoginScreen/RegisterScreen are what actually
// get a FirebaseUser into `auth.currentUser` in the first place.

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    // Set true for exactly one frame after a successful sign in/register, so the
    // screen can navigate and then the caller resets it (see consumeSuccess()).
    val isSuccess: Boolean = false,
    val resetEmailSent: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** True if a user is already signed in — used by NavGraph to pick the start destination. */
    fun isSignedIn(): Boolean = auth.currentUser != null

    fun signIn(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Please enter both email and password.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(trimmedEmail, password).await()
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = friendlyMessage(e)) }
            }
        }
    }

    fun register(name: String, email: String, password: String, confirmPassword: String) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        when {
            trimmedName.isBlank() -> {
                _uiState.update { it.copy(error = "Please enter your name.") }
                return
            }
            trimmedEmail.isBlank() -> {
                _uiState.update { it.copy(error = "Please enter your email.") }
                return
            }
            password.length < 6 -> {
                _uiState.update { it.copy(error = "Password must be at least 6 characters.") }
                return
            }
            password != confirmPassword -> {
                _uiState.update { it.copy(error = "Passwords don't match.") }
                return
            }
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
                val uid = result.user?.uid

                // Set the displayName so DashboardScreen's "Welcome back, {name}!" works,
                // and so it round-trips correctly if the user signs in on another device.
                result.user?.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(trimmedName).build()
                )?.await()

                // Seed a minimal profile doc. Every read in the rest of the app (accounts,
                // nisab settings, categories, etc.) already tolerates a missing document/field
                // (they all fall back to `?: emptyList()` / `?: 0.0` / `?: false`), so this
                // isn't strictly required for the app to function — it just means
                // `users/{uid}/adminData/profile.isAdmin` exists (defaulted to false) instead
                // of being entirely absent, and gives the Firestore console something readable.
                if (uid != null) {
                    val userDoc = db.collection("users").document(uid)
                    userDoc.set(
                        mapOf(
                            "name" to trimmedName,
                            "email" to trimmedEmail,
                            "createdAt" to com.google.firebase.Timestamp.now(),
                        ),
                        SetOptions.merge(),
                    ).await()
                    userDoc.collection("adminData").document("profile").set(
                        mapOf("isAdmin" to false),
                        SetOptions.merge(),
                    ).await()
                }

                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = friendlyMessage(e)) }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            _uiState.update { it.copy(error = "Enter your email above first.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(trimmedEmail).await()
                _uiState.update { it.copy(isLoading = false, resetEmailSent = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = friendlyMessage(e)) }
            }
        }
    }

    fun signOut() = auth.signOut()

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun consumeSuccess() = _uiState.update { it.copy(isSuccess = false) }

    private fun friendlyMessage(e: Exception): String = when {
        e.message?.contains("password is invalid", ignoreCase = true) == true ->
            "Incorrect password."
        e.message?.contains("no user record", ignoreCase = true) == true ->
            "No account found with that email."
        e.message?.contains("email address is already in use", ignoreCase = true) == true ->
            "An account with that email already exists."
        e.message?.contains("badly formatted", ignoreCase = true) == true ->
            "That doesn't look like a valid email address."
        e.message?.contains("network", ignoreCase = true) == true ->
            "Network error — check your connection and try again."
        else -> e.message ?: "Something went wrong. Please try again."
    }
}
