package com.kinh.gmsprotect

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

class AurogonAllowlistService : Service() {
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ctx = context ?: return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "SCREEN_OFF -> schedule 40s / 2m / 10m")
                    cancelRefreshAlarm(ctx)
                    scheduleRefreshAlarm(ctx, STAGE_FIRST, FIRST_DELAY_MS)
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.i(TAG, "SCREEN_ON -> cancel background alarm + restore once")
                    cancelRefreshAlarm(ctx)
                    AurogonAllowlistNotifyProbe.notifyReload(ctx, "screen_on")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!isEnabled(this)) {
            stopSelf()
            return
        }

        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerScreenReceiver()

        val pm = getSystemService(POWER_SERVICE) as? PowerManager
        val interactive = pm?.isInteractive ?: true
        if (!interactive) {
            cancelRefreshAlarm(this)
            scheduleRefreshAlarm(this, STAGE_FIRST, FIRST_DELAY_MS)
        }
        Log.i(TAG, "service started interactive=$interactive")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Throwable) {}
            receiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.aurogon_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.aurogon_notification_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val content = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder.setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.aurogon_notification_title))
            .setContentText(getString(R.string.aurogon_notification_text))
            .setContentIntent(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setColor(0xFF3F3D9B.toInt())
                }
            }
            .build()
    }

    companion object {
        private const val TAG = "AurogonAllowBg"
        private const val PREFS = "aurogon_background_v1"
        private const val KEY_ENABLED = "enabled"

        private const val CHANNEL_ID = "aurogon_allowlist_background"
        private const val NOTIFICATION_ID = 4107
        private const val ALARM_REQUEST_CODE = 4108

        const val ACTION_REFRESH_ALARM = "com.kinh.protect.AUROGON_REFRESH_ALARM"
        const val EXTRA_STAGE = "stage"
        const val STAGE_FIRST = 1
        const val STAGE_SECOND = 2
        const val STAGE_PERIODIC = 3

        private const val FIRST_DELAY_MS = 40_000L
        private const val SECOND_DELAY_MS = 80_000L
        private const val PERIODIC_DELAY_MS = 10L * 60L * 1000L

        fun isEnabled(context: Context): Boolean = try {
            prefs(context).getBoolean(KEY_ENABLED, false)
        } catch (_: Throwable) {
            false
        }

        fun setEnabled(context: Context, enabled: Boolean): Boolean = try {
            prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit()
            val app = context.applicationContext
            if (enabled) {
                AurogonAllowlistNotifyProbe.notifyReload(app, "switch_on")
                start(app)
            } else {
                cancelRefreshAlarm(app)
                app.stopService(Intent(app, AurogonAllowlistService::class.java))
            }
            true
        } catch (t: Throwable) {
            try { prefs(context).edit().putBoolean(KEY_ENABLED, false).apply() } catch (_: Throwable) {}
            Log.w(TAG, "setEnabled failed: $t")
            false
        }

        fun startIfEnabled(context: Context) {
            if (!isEnabled(context)) return
            try {
                start(context)
            } catch (t: Throwable) {
                Log.w(TAG, "startIfEnabled failed: $t")
            }
        }

        private fun start(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, AurogonAllowlistService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }

        private fun prefs(context: Context): SharedPreferences {
            var app = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    app = app.createDeviceProtectedStorageContext()
                } catch (_: Throwable) {}
            }
            return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }

        fun handleRefreshAlarm(context: Context, stage: Int) {
            if (!isEnabled(context)) {
                cancelRefreshAlarm(context)
                return
            }

            val pm = context.getSystemService(POWER_SERVICE) as? PowerManager
            if (pm != null && pm.isInteractive) {
                cancelRefreshAlarm(context)
                Log.i(TAG, "alarm ignored because screen is on")
                return
            }

            val (reason, nextDelay, nextStage) = when (stage) {
                STAGE_FIRST -> Triple("screen_off_40s", SECOND_DELAY_MS, STAGE_SECOND)
                STAGE_SECOND -> Triple("screen_off_2m", PERIODIC_DELAY_MS, STAGE_PERIODIC)
                else -> Triple("screen_off_10m", PERIODIC_DELAY_MS, STAGE_PERIODIC)
            }

            AurogonAllowlistNotifyProbe.notifyReload(context, reason)
            scheduleRefreshAlarm(context, nextStage, nextDelay)
        }

        fun scheduleRefreshAlarm(context: Context, stage: Int, delayMs: Long) {
            val am = context.getSystemService(ALARM_SERVICE) as? AlarmManager ?: return
            val pi = refreshPendingIntent(context, stage)
            val whenMillis = SystemClock.elapsedRealtime() + Math.max(1_000L, delayMs)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, whenMillis, pi)
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, whenMillis, pi)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "schedule alarm failed: $t")
            }
        }

        fun cancelRefreshAlarm(context: Context) {
            val am = context.getSystemService(ALARM_SERVICE) as? AlarmManager ?: return
            try {
                am.cancel(refreshPendingIntent(context, STAGE_FIRST))
            } catch (_: Throwable) {}
        }

        private fun refreshPendingIntent(context: Context, stage: Int): PendingIntent {
            val app = context.applicationContext
            val intent = Intent(app, AurogonRefreshReceiver::class.java).apply {
                action = ACTION_REFRESH_ALARM
                putExtra(EXTRA_STAGE, stage)
            }
            return PendingIntent.getBroadcast(
                app,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
