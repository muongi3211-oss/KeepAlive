package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

object GmsProtector {
    private const val TAG = "GmsProtector"

    const val GMS_PACKAGE = "com.google.android.gms"
    const val ACTION_REFRESH_MIPUSH = "com.xiaomi.mipush.REFRESH_WHITE_LIST_ACTION"

    enum class Result { SENT, GMS_MISSING, FAILED }
    enum class HyperOsProtectionResult { SENT, GMS_MISSING, FAILED }

    fun protect(context: Context): Result = protect(context, null)

    fun protect(context: Context, recoveryFinished: Runnable?): Result {
        val app = context.applicationContext
        Log.i(TAG, "protect START package=" + app.packageName)

        try {
            val info: ApplicationInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                app.packageManager.getApplicationInfo(
                    GMS_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                app.packageManager.getApplicationInfo(GMS_PACKAGE, 0)
            }
            if (info.uid <= 0) {
                Log.w(TAG, "protect STOP GMS_MISSING uid=" + info.uid)
                runCompletion(recoveryFinished)
                return Result.GMS_MISSING
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "protect STOP GMS_MISSING")
            runCompletion(recoveryFinished)
            return Result.GMS_MISSING
        } catch (t: Throwable) {
            Log.w(TAG, "GMS package probe failed; continuing error=" + t.javaClass.simpleName + ":" + t.message)
        }

        val milletResult = MilletNoRestrict.ensureGms(app)
        val milletOk = milletResult == MilletNoRestrict.Result.ADDED || milletResult == MilletNoRestrict.Result.ALREADY_PRESENT

        Log.i(TAG, "MILLET result=$milletResult canWrite=" + MilletNoRestrict.canWrite(app) + " containsGms=" + MilletNoRestrict.containsGms(app))
        if (milletResult == MilletNoRestrict.Result.ADDED) {
            GameWatchJournal.event(app, "millet_repaired", "GmsProtector.protect: $milletResult")
        } else if (!milletOk) {
            GameWatchJournal.event(app, "millet_error", "GmsProtector.protect: $milletResult")
        }

        if (!milletOk) {
            Log.e(TAG, "protect FAILED because MILLET is not active result=$milletResult")
            runCompletion(recoveryFinished)
            return Result.FAILED
        }

        try {
            if (GameWatchCore.isEnabled(app)) {
                GameWatchCore.ensureTargets(app, "GMS_PROTECT")
                GameWatchScheduler.ensure(app, "GMS_PROTECT")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Game Allowlist auto-maintenance unavailable", t)
        }

        return try {
            GmsBindingProbe.ensureBound(app)
            val mipushSent = continueProtection(app, recoveryFinished)
            if (!mipushSent) {
                Log.e(TAG, "protect FAILED because MiPush protect broadcast was not sent")
                return Result.FAILED
            }
            Log.i(TAG, "protect DONE result=SENT")
            Result.SENT
        } catch (t: Throwable) {
            Log.e(TAG, "protect FAILED after MILLET error=" + t.javaClass.simpleName + ":" + t.message, t)
            runCompletion(recoveryFinished)
            Result.FAILED
        }
    }

    fun sendHyperOsProtectionRequest(context: Context): HyperOsProtectionResult {
        val app = context.applicationContext
        try {
            val info: ApplicationInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                app.packageManager.getApplicationInfo(
                    GMS_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                app.packageManager.getApplicationInfo(GMS_PACKAGE, 0)
            }
            if (info.uid <= 0) {
                Log.w(TAG, "HyperOS protection request skipped: GMS missing uid=" + info.uid)
                return HyperOsProtectionResult.GMS_MISSING
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "HyperOS protection request skipped: GMS missing")
            return HyperOsProtectionResult.GMS_MISSING
        } catch (t: Throwable) {
            Log.w(TAG, "GMS package probe failed before HyperOS protection request; continuing error=" + t.javaClass.simpleName + ":" + t.message)
        }

        return try {
            val intent = Intent(ACTION_REFRESH_MIPUSH).apply {
                putExtra("pkgName", GMS_PACKAGE)
                putExtra("option", 1)
            }
            app.sendBroadcast(intent)
            CompatibilityVerification.markHyperOsProtectionRequestSent(app)
            Log.i(TAG, "MiPush protect broadcast SENT pkg=$GMS_PACKAGE")
            GameWatchJournal.event(app, "mipush_requested", "Broadcast SENT pkg=$GMS_PACKAGE; this does not confirm Xiaomi accepted the request")
            HyperOsProtectionResult.SENT
        } catch (t: Throwable) {
            Log.e(TAG, "MiPush protect broadcast FAILED error=" + t.javaClass.simpleName + ":" + t.message, t)
            GameWatchJournal.event(app, "mipush_error", "Broadcast FAILED: $t")
            HyperOsProtectionResult.FAILED
        }
    }

    private fun continueProtection(app: Context, recoveryFinished: Runnable?): Boolean {
        val request = sendHyperOsProtectionRequest(app)
        if (request != HyperOsProtectionResult.SENT) {
            runCompletion(recoveryFinished)
            return false
        }
        try {
            FcmReconnect.recoverIfDisconnected(app, recoveryFinished)
        } catch (t: Throwable) {
            Log.w(TAG, "FCM recovery failed after MiPush send error=" + t.javaClass.simpleName + ":" + t.message)
            runCompletion(recoveryFinished)
        }
        return true
    }

    private fun runCompletion(completion: Runnable?) {
        if (completion == null) return
        try {
            completion.run()
        } catch (ignored: Throwable) {}
    }
}
