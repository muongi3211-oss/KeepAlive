package com.kinh.gmsprotect

import android.content.Context

object GmsProtector {
    enum class Result {
        SENT, FAILED, GMS_MISSING
    }

    fun protect(context: Context): Result {
        return try {
            // Kiểm tra GMS có tồn tại trên thiết bị không
            context.packageManager.getPackageInfo(GameWatchCore.GMS, 0)
            
            // 1. Thực hiện ghi GMS và Play Store vào danh sách MILLET của HyperOS
            MilletNoRestrict.ensureGms(context)
            
            // 2. Kích hoạt tín hiệu thông báo nền MiPush / Greezer
            GameWatchCore.refreshMiPush(context)
            
            Result.SENT
        } catch (_: Throwable) {
            Result.GMS_MISSING
        }
    }
}
