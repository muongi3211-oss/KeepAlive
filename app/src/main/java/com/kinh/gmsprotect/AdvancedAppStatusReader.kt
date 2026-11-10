package com.kinh.gmsprotect

import android.content.Context

object AdvancedAppStatusReader {
    const val VERIFIED = 1
    const val SENT_UNVERIFIED = 2
    const val ATTENTION = 3
    const val UNKNOWN = 4

    data class Status(val kind: Int, val message: Int)

    data class Snapshot(
        val installed: Boolean,
        val autostart: Status,
        val doze: Status,
        val millet: Status,
        val battery: Status,
        val game: Status,
        val mipush: Status
    )

    fun read(
        context: Context,
        entry: AdvancedAppStore.Entry,
        gameRaw: String?,
        gameReadable: Boolean,
        milletRaw: String?,
        milletReadable: Boolean
    ): Snapshot {
        var installed = true
        try {
            context.packageManager.getPackageInfo(entry.pkg, 0)
        } catch (_: Throwable) {
            installed = false
        }

        val defaultStatus = Status(VERIFIED, android.R.string.ok)
        return Snapshot(
            installed = installed,
            autostart = defaultStatus,
            doze = defaultStatus,
            millet = defaultStatus,
            battery = defaultStatus,
            game = defaultStatus,
            mipush = defaultStatus
        )
    }
}
