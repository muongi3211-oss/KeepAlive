package com.kinh.gmsprotect

import android.content.Context

object GmsProtector {
    fun ensureTargets(context: Context, reason: String) {
        try {
            GameWatchCore.ensureTargets(context, reason)
        } catch (_: Throwable) {}
    }
}
