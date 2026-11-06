package com.hanh.gmsprotect

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

object GmsBindingProbe {
    private const val TAG = "GmsBindProbe"
    private const val GMS = "com.google.android.gms"
    private const val PERSISTENT_API_SERVICE = "com.google.android.gms.chimera.PersistentApiService"
    private const val CHIMERA_BIND_ACTION = "com.google.android.chimera.BoundService.START"

    private val TARGET_ACTIONS = arrayOf(
        "com.google.android.gms.common.telemetry.service.START",
        "com.google.android.gms.appusage.service.START",
        "com.google.android.gms.pseudonymous.service.START"
    )

    private val MAIN = Handler(Looper.getMainLooper())
    private val LOCK = Any()

    private var appContext: Context? = null
    private var activeConnection: ServiceConnection? = null
    private var bindRequested = false
    private var connected = false
    private var activeCandidate = -1

    fun ensureBound(context: Context) {
        val app = context.applicationContext
        synchronized(LOCK) {
            appContext = app
            if (connected || bindRequested) {
                Log.i(TAG, "ensureBound skip connected=$connected requested=$bindRequested candidate=" + candidateName(activeCandidate))
                return
            }
            bindRequested = true
        }
        MAIN.post { tryCandidate(0) }
    }

    fun isConnected(): Boolean {
        synchronized(LOCK) {
            return connected
        }
    }

    fun state(): String {
        synchronized(LOCK) {
            return "connected=$connected requested=$bindRequested candidate=" + candidateName(activeCandidate)
        }
    }

    private fun tryCandidate(index: Int) {
        val app: Context
        synchronized(LOCK) {
            app = appContext ?: return
            if (connected) return
            if (index >= TARGET_ACTIONS.size) {
                bindRequested = false
                activeCandidate = -1
                Log.w(TAG, "all candidates failed")
                return
            }
            activeCandidate = index
        }

        val targetAction = TARGET_ACTIONS[index]
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder?) {
                synchronized(LOCK) {
                    activeConnection = this
                    connected = true
                    bindRequested = true
                    activeCandidate = index
                }
                Log.i(TAG, "CONNECTED candidate=$targetAction component=$name binder=" + (service != null))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                synchronized(LOCK) {
                    if (activeConnection == this) {
                        connected = false
                    }
                }
                Log.w(TAG, "DISCONNECTED candidate=$targetAction component=$name")
            }

            override fun onBindingDied(name: ComponentName) {
                synchronized(LOCK) {
                    if (activeConnection == this) {
                        connected = false
                        activeConnection = null
                        bindRequested = false
                    }
                }
                Log.w(TAG, "BINDING_DIED candidate=$targetAction component=$name")
                MAIN.postDelayed({ ensureBound(app) }, 1500L)
            }

            override fun onNullBinding(name: ComponentName) {
                Log.w(TAG, "NULL_BINDING candidate=$targetAction component=$name")
                safeUnbind(app, this)
                synchronized(LOCK) {
                    if (activeConnection == this) activeConnection = null
                    connected = false
                }
                MAIN.postDelayed({ tryCandidate(index + 1) }, 250L)
            }
        }

        val intent = Intent(CHIMERA_BIND_ACTION).apply {
            component = ComponentName(GMS, PERSISTENT_API_SERVICE)
            data = Uri.parse("chimera-action:$targetAction")
        }

        var accepted = false
        try {
            accepted = app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            Log.i(TAG, "bindService candidate=$targetAction accepted=$accepted")
        } catch (t: Throwable) {
            Log.w(TAG, "bindService exception candidate=$targetAction error=" + t.javaClass.simpleName + ":" + t.message)
        }

        if (!accepted) {
            MAIN.postDelayed({ tryCandidate(index + 1) }, 250L)
            return
        }

        synchronized(LOCK) {
            activeConnection = connection
        }

        MAIN.postDelayed({
            val stillWaiting: Boolean
            synchronized(LOCK) {
                stillWaiting = !connected && activeConnection == connection
            }
            if (!stillWaiting) return@postDelayed

            Log.w(TAG, "CONNECT_TIMEOUT candidate=$targetAction")
            safeUnbind(app, connection)
            synchronized(LOCK) {
                if (activeConnection == connection) activeConnection = null
            }
            tryCandidate(index + 1)
        }, 3000L)
    }

    private fun safeUnbind(context: Context, connection: ServiceConnection) {
        try {
            context.unbindService(connection)
        } catch (ignored: Throwable) {}
    }

    private fun candidateName(index: Int): String {
        if (index < 0 || index >= TARGET_ACTIONS.size) return "none"
        return TARGET_ACTIONS[index]
    }
}
