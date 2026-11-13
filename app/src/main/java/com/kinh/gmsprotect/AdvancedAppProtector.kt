package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Best-effort maintenance for EXPLICITLY selected third-party packages.
 * Never changes GMS/Play Store/core MILLET behavior; never removes vendor entries.
 * MiPush uses an unacknowledged broadcast: "sent" is NOT "Xiaomi accepted".
 */
object AdvancedAppProtector {
    private const val TAG = "AdvancedApps"

    @Synchronized
    fun maintain(context: Context, reason: String) {
        val app = context.applicationContext
        val rows = AdvancedAppStore.list(app)
                val gamePackages = ArrayList<String>()
        var requests = 0
        
        // VÁ LỖI: Bắt buộc phải đưa GMS và GMS Protect vào Game Allowlist đầu tiên
        // Nếu thiếu 2 dòng này, danh sách Game sẽ bị ghi đè và xóa mất GMS!
        gamePackages.add(GmsProtector.GMS_PACKAGE)
        gamePackages.add(app.packageName)

        for (e in rows) {
            if (!e.enabled) continue
            if (!isInstalled(app, e.pkg)) {
                Log.i(TAG, "SKIP missing app=" + e.pkg)
                continue
            }
            // Đảm bảo không thêm trùng lặp
            if (e.pkg.indexOf('_') < 0 && !gamePackages.contains(e.pkg)) {
                gamePackages.add(e.pkg)
            }
            // Same action/payload as GmsProtector, without FCM reconnect or
            // GMS compatibility evidence changes. Broadcast is not an ack.
            try {
                // Đã sửa: Sử dụng cơ chế ngách ẩn MiPush Protect của Xiaomi Framework
                val request = Intent("com.xiaomi.mipush.RECEIVE_MESSAGE").apply {
                    setPackage("com.miui.securitycenter")
                    putExtra("message_type", "push_whitelist")
                    putExtra("package_name", e.pkg)
                }
                app.sendBroadcast(request)
                AdvancedAppStore.noteMiPush(app, e.pkg, true, null)
                requests++
            } catch (error: Throwable) {
                AdvancedAppStore.noteMiPush(app, e.pkg, false, error.toString())
                Log.w(TAG, "MiPush request failed pkg=" + e.pkg, error)
            }
        }
        
        // Always ensure the mandatory core too, even when the user's list is empty.
        // Both paths now use one writer and one verification read.
        if (GameWatchCore.isEnabled(app)) {
            ensureGame(app, gamePackages, reason)
        }
        Log.i(
            TAG, "MAINTAIN " + reason + " gameTargets=" + gamePackages.size
                    + " mipushRequestsSent=" + requests
        )
    }

    fun isInstalled(c: Context, pkg: String): Boolean {
        return try {
            // Target SDK 22 retains legacy visibility; resolve only explicit user selection.
            c.packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (missing: PackageManager.NameNotFoundException) {
            false
        } catch (error: Throwable) {
            Log.w(TAG, "Package check unavailable: $pkg", error)
            false
        }
    }

    /** Shared, single-writer Game Allowlist maintenance (core + user apps). */
    internal fun ensureGame(c: Context, targets: List<String>, reason: String): Boolean {
        return GameWatchCore.ensurePackages(c, targets, reason)
    }

    fun gameStatus(c: Context, pkg: String): String {
        if (pkg.indexOf('_') >= 0) return "unsupported"
        if (!GameWatchCore.canWrite(c)) return "permission"
        if (!isInstalled(c, pkg)) return "missing"
        return try {
            if (GameWatchCore.contains(GameWatchCore.raw(c), pkg)) "present" else "absent"
        } catch (error: Throwable) {
            "unknown"
        }
    }
}
