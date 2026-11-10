package com.kinh.gmsprotect

import android.content.Context
import android.util.Log

object GmsProtector {
    const val GMS_PACKAGE = "com.google.android.gms"
    
    enum class Result {
        SENT,
        FAILED
    }

    fun protect(context: Context): Result {
        return try {
            val app = context.applicationContext
            
            // 1. Kích hoạt ngách MiPush Protect List siêu nhỏ trên HyperOS/MIUI
            val mipushSuccess = MiPushProtectManager.ensureMiPushProtection(app)
            
            // 2. Đồng bộ danh sách Millet No Restrict
            val milletResult = MilletNoRestrict.ensureGms(app)
            val milletSuccess = (milletResult == MilletNoRestrict.Result.ADDED || milletResult == MilletNoRestrict.Result.ALREADY_PRESENT)

            // Nếu một trong hai cơ chế ngách thành công, coi như GmsProtector đã hoàn thành nhiệm vụ bảo vệ
            if (mipushSuccess || milletSuccess) {
                Log.i("GmsProtector", "GmsProtector.protect: SENT via MiPush/Millet bypass.")
                Result.SENT
            } else {
                Result.FAILED
            }
        } catch (t: Throwable) {
            Log.e("GmsProtector", "GmsProtector.protect exception: ${t.message}")
            Result.FAILED
        }
    }
}
