package com.hasan.nisabwallet.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Composable
fun rememberNetworkMonitor(context: Context): StateFlow<Boolean> {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isOnline = remember { MutableStateFlow(false) }

    DisposableEffect(connectivityManager) {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline.value = true
                // Save the exact timestamp to preferences when internet is restored
                context.getSharedPreferences("nisab_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("last_sync_time", System.currentTimeMillis())
                    .apply()
            }

            override fun onLost(network: Network) {
                isOnline.value = false
            }
        }

        connectivityManager.registerNetworkCallback(request, callback)

        // Initial check on load
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isOnline.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    return isOnline.asStateFlow()
}