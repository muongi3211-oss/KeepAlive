package com.kinh.gmsprotect

import android.content.Context
import android.widget.LinearLayout

object GoogleServiceCheckUi {
    fun checkOnce(context: Context, row: LinearLayout, protectResult: GmsProtector.Result) {
        try {
            val isOk = protectResult == GmsProtector.Result.SENT && MilletNoRestrict.containsGms(context)
            if (!isOk) {
                // Thay thế GMS_MISSING bằng FAILED hoặc trạng thái phù hợp
                row.alpha = 0.8f
            } else {
                row.alpha = 1.0f
            }
        } catch (_: Throwable) {}
    }
}
