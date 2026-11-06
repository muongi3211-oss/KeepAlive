package com.hanh.gmsprotect

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

object AdvancedAppStatusReader {
    const val UNKNOWN = 0
    const val VERIFIED = 1
    const val ATTENTION = 2
    const val OFF = 3
    const val SENT_UNVERIFIED = 4

    class Status(val kind: Int, val message: Int)

    class Snapshot(
        val installed: Boolean,
        val autostart: Status,
        val doze: Status,
        val battery: Status,
        val millet: Status,
        val game: Status,
        val mipush: Status
    )

    fun read(
        context: Context,
        entry: AdvancedAppStore.Entry,
        gameRaw: String?,
        gameReadOk: Boolean,
        milletRaw: String?,
        milletReadOk: Boolean
    ): Snapshot {
        val installed = AdvancedAppProtector.isInstalled(context, entry.pkg)
        val autostart = if (installed) autostart(context, entry.pkg) else unknown()
        val doze = if (installed) doze(context, entry.pkg) else unknown()
        val battery = unknownBattery()
        val millet = if (installed) millet(entry.pkg, milletRaw, milletReadOk) else unknown()
        val game = game(context, entry, installed, gameRaw, gameReadOk)
        val mipush = mipush(context, entry, installed)
        return Snapshot(installed, autostart, doze, battery, millet, game, mipush)
    }

    private fun unknown(): Status = Status(UNKNOWN, R.string.adv_unknown)
    private fun unknownBattery(): Status = Status(UNKNOWN, R.string.adv_xiaomi_battery_unverified)

    private fun autostart(c: Context, pkg: String): Status {
        try {
            val info = c.packageManager.getApplicationInfo(pkg, 0)
            val ops = c.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return unknown()
            val method = AppOpsManager::class.java.getDeclaredMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            method.isAccessible = true
            val result = method.invoke(ops, 10008, info.uid, pkg) as? Int ?: return unknown()
            if (result == AppOpsManager.MODE_ALLOWED) return Status(VERIFIED, R.string.adv_auto_allowed)
            if (result == AppOpsManager.MODE_IGNORED || result == AppOpsManager.MODE_ERRORED) {
                return Status(ATTENTION, R.string.adv_denied)
            }
        } catch (unavailable: Throwable) {
            // OEM restriction
        }
        return unknown()
    }

    private fun millet(pkg: String, raw: String?, readable: Boolean): Status {
        if (!readable || raw == null) return Status(UNKNOWN, R.string.adv_millet_unknown)
        for (token in raw.split("[,;\n\t]".toRegex()).dropLastWhile { it.isEmpty() }) {
            if (pkg == token.trim()) return Status(VERIFIED, R.string.adv_millet_present)
        }
        return Status(ATTENTION, R.string.adv_millet_absent)
    }

    private fun doze(c: Context, pkg: String): Status {
        if (Build.VERSION.SDK_INT < 23) return unknown()
        return try {
            val pm = c.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return unknown()
            if (pm.isIgnoringBatteryOptimizations(pkg)) {
                Status(VERIFIED, R.string.adv_doze_exempt)
            } else {
                Status(ATTENTION, R.string.adv_not_exempt)
            }
        } catch (unavailable: Throwable) {
            unknown()
        }
    }

    private fun game(
        c: Context,
        entry: AdvancedAppStore.Entry,
        installed: Boolean,
        raw: String?,
        readable: Boolean
    ): Status {
        if (!entry.enabled) return Status(OFF, R.string.adv_off)
        if (!installed) return Status(ATTENTION, R.string.adv_app_missing)
        if (entry.pkg.indexOf('_') >= 0) return Status(ATTENTION, R.string.adv_game_unsupported)
        if (!readable) return unknown()
        if (GameWatchCore.contains(raw, entry.pkg)) return Status(VERIFIED, R.string.adv_game_present)
        if (!GameWatchCore.canWrite(c)) return Status(ATTENTION, R.string.adv_game_permission)
        return Status(ATTENTION, R.string.adv_game_absent)
    }

    private fun mipush(c: Context, entry: AdvancedAppStore.Entry, installed: Boolean): Status {
        if (!entry.enabled) return Status(OFF, R.string.adv_off)
        if (!installed) return Status(ATTENTION, R.string.adv_app_missing)
        if (AdvancedAppStore.lastMiPushAttempt(c, entry.pkg) == 0L) return Status(UNKNOWN, R.string.adv_mipush_waiting)
        return if (AdvancedAppStore.lastMiPushSent(c, entry.pkg)) {
            Status(SENT_UNVERIFIED, R.string.adv_mipush_sent)
        } else {
            Status(ATTENTION, R.string.adv_mipush_failed)
        }
    }
}
