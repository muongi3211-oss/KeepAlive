package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AurogonRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            GameWatchCore.startIfEnabled(context)
            GameWatchCore.ensureTargets(context, "AUROGON_REFRESH")
        } catch (_: Throwable) {}
    }
}
