package com.kinh.gmsprotect

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AurogonRefreshReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REFRESH_ALARM = "com.kinh.gmsprotect.AUROGON_REFRESH_ALARM"
        const val EXTRA_STAGE = "extra_stage"
        const val STAGE_PERIODIC = 1

        fun scheduleNextAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, AurogonRefreshReceiver::class.java).apply {
                    action = ACTION_REFRESH_ALARM
                    putExtra(EXTRA_STAGE, STAGE_PERIODIC)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAt = System.currentTimeMillis() + (3 * 60 * 1000L) // 3 phút một lần
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (_: Throwable) {}
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_REFRESH_ALARM) {
            handleRefreshAlarm(context)
        }
    }

    private fun handleRefreshAlarm(context: Context) {
        try {
            MilletNoRestrict.ensureGms(context)
            AurogonAllowlistService.startIfEnabled(context)
        } catch (_: Throwable) {}
        
        // Lên lịch cho lần phát tiếp theo
        scheduleNextAlarm(context)
    }
}
