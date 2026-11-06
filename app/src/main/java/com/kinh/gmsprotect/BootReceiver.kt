package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED == action ||
            Intent.ACTION_BOOT_COMPLETED == action ||
            Intent.ACTION_USER_UNLOCKED == action ||
            Intent.ACTION_MY_PACKAGE_REPLACED == action
        ) {
            val pending = goAsync()
            try {
                if (Intent.ACTION_LOCKED_BOOT_COMPLETED != action && GameWatchCore.isEnabled(context)) {
                    val targetsReady = GameWatchCore.ensureTargets(context, "BOOT_$action")
                    val jobReady = GameWatchScheduler.ensure(context, "BOOT_$action")
                    if (Intent.ACTION_BOOT_COMPLETED == action || Intent.ACTION_USER_UNLOCKED == action) {
                        GameWatchEvidence.noteBoot(context, targetsReady && jobReady)
                    }
                }
                if (Intent.ACTION_LOCKED_BOOT_COMPLETED != action) {
                    try {
                        AdvancedAppProtector.maintain(context, "BOOT_$action")
                        if (GameWatchScheduler.shouldWatch(context)) {
                            GameWatchScheduler.ensure(context, "BOOT_ADV_$action")
                        }
                    } catch (ignored: Throwable) {}
                }
                if (Intent.ACTION_LOCKED_BOOT_COMPLETED != action) {
                    LegacyBackgroundCleanup.runOnce(context)
                }
                GmsProtector.protect(context) {
                    MilletWatchScheduler.schedule(context)
                    pending.finish()
                }
            } catch (t: Throwable) {
                pending.finish()
            }
        }
    }
}
