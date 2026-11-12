package com.kinh.gmsprotect

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * One-time upgrade cleanup of the discontinued optional background monitor.
 * This class never starts a service, registers a receiver or schedules an alarm.
 */
object LegacyBackgroundCleanup {
    private const val TAG = "LegacyBgCleanup"
    private const val PREFS = "removed_background_monitor_v1"
    private const val ALARM_REQUEST_CODE = 4108
    private const val NOTIFICATION_ID = 4107

    fun runOnce(context: Context) {
        val app = context.applicationContext
        val storage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            app.createDeviceProtectedStorageContext() else app
        val migration = storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (migration.getBoolean("complete", false)) return
        try {
            // Disable the old user preference before cleaning historical artifacts.
            storage.getSharedPreferences("aurogon_background_v1", Context.MODE_PRIVATE)
                .edit().remove("enabled").commit()

            val obsoleteService = Intent().apply {
                setComponent(
                    ComponentName(
                        app.packageName,
                        app.packageName + ".AurogonAllowlistService"
                    )
                )
            }
            try {
                app.stopService(obsoleteService)
            } catch (ignored: Throwable) {
            }

            val obsoleteAlarm = Intent("com.kinh.gmsprotect.AUROGON_REFRESH_ALARM").apply {
                setComponent(
                    ComponentName(
                        app.packageName,
                        app.packageName + ".AurogonRefreshReceiver"
                    )
                )
            }
            var flags = PendingIntent.FLAG_NO_CREATE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags =
                flags or PendingIntent.FLAG_IMMUTABLE
            val pending = PendingIntent.getBroadcast(app, ALARM_REQUEST_CODE, obsoleteAlarm, flags)
            if (pending != null) {
                val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.cancel(pending)
                pending.cancel()
            }

            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null) {
                nm.cancel(NOTIFICATION_ID)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    nm.deleteNotificationChannel("aurogon_allowlist_background")
                }
            }
            migration.edit().putBoolean("complete", true).apply()
            Log.i(TAG, "Legacy background artifacts cleaned")
        } catch (error: Throwable) {
            // Keep the migration pending so it can retry next launch or boot.
            Log.w(TAG, "Legacy cleanup deferred", error)
        }
    }
}
