package com.kinh.gmsprotect

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger

/** Lightweight copy of FCM Auto's GMS local/status probe. */
class GcmStatusClient(context: Context) {
    interface Callback {
        fun onResult(status: String?, error: String?)
    }

    companion object {
        private const val GMS = "com.google.android.gms"
        private const val ACTION_SEND = "com.google.android.gcm.intent.SEND"
        private const val GTALK_PERMISSION = "com.google.android.gtalkservice.permission.GTALK_SERVICE"
        private const val LOCAL_STATUS = "https://gcm.googleapis.com/local/status"
        private const val TIMEOUT_MS = 3_000L
        private val SEQ = AtomicInteger(1)
    }

    private val context: Context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val appIdentity: PendingIntent

    private var callback: Callback? = null
    private var messageId: String? = null
    private var finished = false

    init {
        val invalid = Intent().apply {
            setPackage("com.google.example.invalidpackage")
        }
        appIdentity = PendingIntent.getBroadcast(
            this.context,
            0,
            invalid,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun request(cb: Callback) {
        if (callback != null) {
            cb.onResult(null, "BUSY")
            return
        }

        callback = cb
        finished = false
        messageId = "gmsprotect-status-" +
                SEQ.getAndIncrement() +
                "-" +
                SystemClock.elapsedRealtime()

        val replyHandler = Handler(Looper.getMainLooper(), ::handleReply)
        val replyMessenger = Messenger(replyHandler)

        val intent = Intent(ACTION_SEND).setPackage(GMS).apply {
            putExtra("app", appIdentity)
            putExtra("google.to", LOCAL_STATUS)
            putExtra("google.message_id", messageId)
            putExtra("google.ttl", "0")
            putExtra("google.messenger", replyMessenger)
        }

        try {
            context.sendOrderedBroadcast(
                intent,
                GTALK_PERMISSION,
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context, i: Intent) {
                        // The Messenger response below carries local/status.
                    }
                },
                main,
                Activity.RESULT_OK,
                null,
                null
            )
            main.postDelayed({ finish(null, "NO_RESPONSE") }, TIMEOUT_MS)
        } catch (t: Throwable) {
            finish(null, t.javaClass.simpleName)
        }
    }

    private fun handleReply(msg: Message): Boolean {
        val response = msg.obj as? Intent ?: return true
        val replyTo = response.getStringExtra("In-Reply-To")
        if (replyTo != null && messageId != null && messageId != replyTo) {
            return true
        }

        finish(response.getStringExtra("status"), null)
        return true
    }

    private fun finish(status: String?, error: String?) {
        if (finished) return
        finished = true
        main.removeCallbacksAndMessages(null)

        val cb = callback
        callback = null
        cb?.onResult(status, error)
    }
}
