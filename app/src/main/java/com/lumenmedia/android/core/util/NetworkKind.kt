package com.lumenmedia.android.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class ConnectionKind { Lan, External }

object NetworkKindDetector {
    fun detect(context: Context): ConnectionKind {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return ConnectionKind.External
        val caps = cm.getNetworkCapabilities(network) ?: return ConnectionKind.External
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionKind.Lan
            else -> ConnectionKind.External
        }
    }
}
