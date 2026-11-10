package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                GameWatchCore.ensureTargets(context, "BOOT_COMPLETED")
                GameWatchCore.protect(context, "BOOT_COMPLETED")
                GameWatchCore.schedule(context, "BOOT_COMPLETED")
                MilletWatchScheduler.ensure(context, "BOOT_COMPLETED")
            }
        } catch (_: Throwable) {}
    }
}
