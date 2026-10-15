package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val action = intent?.action

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            try {
                AurogonAllowlistService.startIfEnabled(ctx)
                GmsProtector.protect(ctx)
                MilletWatchJobService.schedule(ctx)
            } catch (_: Throwable) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
