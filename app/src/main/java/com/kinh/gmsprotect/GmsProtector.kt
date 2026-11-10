package com.kinh.gmsprotect

import android.content.Context

object GmsProtector {
    enum class Result {
        SENT, FAILED, GMS_MISSING
    }

    fun protect(context: Context): Result {
        return try {
            // Kiểm tra xem dịch vụ GMS có thực sự được cài đặt trên máy hay không
            context.packageManager.getPackageInfo(GameWatchCore.GMS, 0)
            
            // Kích hoạt tín hiệu MiPush / làm mới kết nối nền theo cơ chế chuẩn
            GameWatchCore.refreshMiPush(context)
            Result.SENT
        } catch (_: Throwable) {
            Result.GMS_MISSING
        }
    }
}
