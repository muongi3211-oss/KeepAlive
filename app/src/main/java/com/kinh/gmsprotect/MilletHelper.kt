package com.kinh.gmsprotect

import android.content.Context

object MilletHelper {
    fun schedule(context: Context, reason: String) {
        try {
            MilletWatchScheduler.ensure(context, reason)
        } catch (_: Throwable) {}
    }
}
