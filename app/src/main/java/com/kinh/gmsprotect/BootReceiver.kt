package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED == action
            || Intent.ACTION_BOOT_COMPLETED == action
            || Intent.ACTION_USER_UNLOCKED == action
            || Intent.ACTION_MY_PACKAGE_REPLACED == action
        ) {
            val pending = goAsync()
            try {
                // GAME_WATCH is deliberately independent of FCM recovery callbacks.
                // Even if MiPush/FCM is unavailable, this watcher still rearms.
                if (Intent.ACTION_LOCKED_BOOT_COMPLETED != action
                    && GameWatchCore.isEnabled(context)
                ) {
                    val targetsReady = GameWatchCore.ensureTargets(
                        context,
                        "BOOT_$action"
                    )
                    val jobReady = GameWatchScheduler.ensure(
                        context,
                        "BOOT_$action"
                    )
                    if (Intent.ACTION_BOOT_COMPLETED == action
                        || Intent.ACTION_USER_UNLOCKED == action
                    ) {
                        GameWatchEvidence.noteBoot(context, targetsReady && jobReady)
                    }
                }
                if (Intent.ACTION_LOCKED_BOOT_COMPLETED != action) {
                    try {
                        AdvancedAppProtector.maintain(context, "BOOT_$action")
                        if (GameWatchScheduler.shouldWatch(context)) {
                            GameWatchScheduler.ensure(context, "BOOT_ADV_$action")
                        }
                    } catch (ignored: Throwable) {
                    }
                }
                // Clear obsolete background alarms on upgrade / first regular boot.
                // No new alarm, service or polling task is created.
                if (Intent.ACTION_LOCKED_BOOT_COMPLETED != action) {
                    LegacyBackgroundCleanup.runOnce(context)
                }
                // Arm the event-driven MILLET watcher after the normal boot protection
                // pass. No polling or long-running process is required.
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
