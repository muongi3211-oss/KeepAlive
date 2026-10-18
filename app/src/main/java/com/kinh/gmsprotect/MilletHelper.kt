package com.kinh.gmsprotect

import android.content.Context

object MilletHelper {
    var systemPropStatus: String = "ĐÃ ÁP DỤNG"
    var isPropAccepted: Boolean = true

    fun applyMilletProtection(context: Context) {
        try {
            MilletNoRestrict.applyProtection(context)
            isPropAccepted = true
            systemPropStatus = "ĐÃ ÁP DỤNG"
        } catch (_: Throwable) {
            isPropAccepted = true
            systemPropStatus = "ĐÃ BỎ QUA AN TOÀN"
        }
    }

    fun scheduleMilletJob(context: Context) {
        try {
            MilletWatchScheduler.schedule(context)
        } catch (_: Throwable) {}
    }
}
