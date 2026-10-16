package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_SCREEN_ON
        ) {
            try {
                val appContext = context.applicationContext
                MilletNoRestrict.ensureGms(appContext)
                AurogonAllowlistService.startIfEnabled(appContext)
                MilletWatchJobService.schedule(appContext)
            } catch (_: Throwable) {}
        }
    }
}
