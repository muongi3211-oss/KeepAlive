package com.kinh.gmsprotect

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Log

object AurogonAllowlistNotifyProbe {
    private const val TAG = "AurogonAllowProbe"
    private const val KEY = "immobulus_mode_switch_restrict"

    fun notifyReload(context: Context, reason: String = "manual"): Boolean {
        val app = context.applicationContext
        val uri = Settings.Secure.getUriFor(KEY)

        return try {
            app.contentResolver.notifyChange(uri, null)
            Log.i(TAG, "notifyChange SENT reason=$reason uri=$uri")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "notifyChange FAILED reason=$reason error=${t.javaClass.simpleName}:${t.message}")
            false
        }
    }
}
