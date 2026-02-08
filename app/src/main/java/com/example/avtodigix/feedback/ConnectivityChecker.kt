package com.example.avtodigix.feedback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class ConnectivityChecker(private val context: Context) {
    fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
