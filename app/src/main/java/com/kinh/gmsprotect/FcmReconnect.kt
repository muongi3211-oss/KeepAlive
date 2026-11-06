package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

object FcmReconnect {
    private const val GMS = "com.google.android.gms"
    private const val ACTION_HEARTBEAT_NOW = "com.google.android.gms.gcm.ACTION_HEARTBEAT_NOW"
    private const val ACTION_RECONNECT = "com.google.android.intent.action.GCM_RECONNECT"
    private const val HEARTBEAT_RECHECK_MS = 2_000L
    private val IN_FLIGHT = AtomicBoolean(false)

    fun recoverIfDisconnected(context: Context, completion: Runnable?) {
        val app = context.applicationContext
        if (!IN_FLIGHT.compareAndSet(false, true)) {
            runCompletion(completion)
            return
        }

        val main = Handler(Looper.getMainLooper())
        val client = GcmStatusClient(app)

        val finish = Runnable {
            IN_FLIGHT.set(false)
            runCompletion(completion)
        }

        client.request { status, error ->
            if (error != null || "3" != status) {
                finish.run()
                return@request
            }

            try {
                app.sendBroadcast(Intent(ACTION_HEARTBEAT_NOW).setPackage(GMS))
            } catch (ignored: Throwable) {
                trySendReconnect(app)
                finish.run()
                return@request
            }

            main.postDelayed({
                client.request { afterHeartbeat, afterError ->
                    if (afterError == null && "3" == afterHeartbeat) {
                        trySendReconnect(app)
                    }
                    finish.run()
                }
            }, HEARTBEAT_RECHECK_MS)
        }
    }

    private fun trySendReconnect(app: Context) {
        try {
            app.sendBroadcast(Intent(ACTION_RECONNECT).setPackage(GMS))
        } catch (ignored: Throwable) {}
    }

    private fun runCompletion(completion: Runnable?) {
        if (completion == null) return
        try {
            completion.run()
        } catch (ignored: Throwable) {}
    }
}
