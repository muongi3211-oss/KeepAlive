package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import java.util.ArrayList

object AdvancedAppProtector {
    private const val TAG = "AdvancedApps"

    fun maintain(context: Context, reason: String) {
        val app = context.applicationContext
        val rows = AdvancedAppStore.list(app)
        val gamePackages = ArrayList<String>()
        var requests = 0
        for (e in rows) {
            if (!e.enabled) continue
            if (!isInstalled(app, e.pkg)) {
                Log.i(TAG, "SKIP missing app=${e.pkg}")
                continue
            }
            if (e.pkg.indexOf('_') < 0) gamePackages.add(e.pkg)
            try {
                val request = Intent(GmsProtector.ACTION_REFRESH_MIPUSH).apply {
                    putExtra("pkgName", e.pkg)
                    putExtra("option", 1)
                }
                app.sendBroadcast(request)
                AdvancedAppStore.noteMiPush(app, e.pkg, true, null)
                requests++
            } catch (error: Throwable) {
                AdvancedAppStore.noteMiPush(app, e.pkg, false, error.toString())
                Log.w(TAG, "MiPush request failed pkg=${e.pkg}", error)
            }
        }
        if (GameWatchCore.isEnabled(app)) {
            ensureGame(app, gamePackages, reason)
        }
        Log.i(TAG, "MAINTAIN $reason gameTargets=${gamePackages.size} mipushRequestsSent=$requests")
    }

    fun isInstalled(c: Context, pkg: String): Boolean {
        return try {
            c.packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (error: Throwable) {
            Log.w(TAG, "Package check unavailable: $pkg", error)
            false
        }
    }

    fun ensureGame(c: Context, targets: List<String>, reason: String): Boolean {
        return try {
            GameWatchCore.ensureTargets(c, reason)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun gameStatus(c: Context, pkg: String): String {
        if (pkg.indexOf('_') >= 0) return "unsupported"
        if (!GameWatchCore.canWrite(c)) return "permission"
        if (!isInstalled(c, pkg)) return "missing"
        return try {
            if (GameWatchCore.contains(GameWatchCore.raw(c), pkg)) "present" else "absent"
        } catch (_: Throwable) {
            "unknown"
        }
    }
}
