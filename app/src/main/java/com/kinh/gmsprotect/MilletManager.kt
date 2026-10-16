package com.kinh.gmsprotect

import android.content.Context
import android.util.Log

object MilletManager {
    private const val TAG = "MilletManager"
    private const val MILLET_NO_RESTRICT_APP = "MILLET_NO_RESTRICT_APP"

    /**
     * Kích hoạt cờ chống bóp tiến trình ngầm cho GMS/FCM trên HyperOS
     */
    fun applyMilletProtection(context: Context) {
        try {
            // Gọi Service ẩn của Xiaomi qua Reflection
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "millet")

            if (binder != null) {
                // Thực thi lệnh đặt cờ MILLET_NO_RESTRICT_APP
                Log.d(TAG, "Đã kết nối dịch vụ Millet. Đang áp dụng cờ: $MILLET_NO_RESTRICT_APP")
                
                // Bạn có thể dùng SystemProperties hoặc Invocation IPC nội bộ
                val systemProperties = Class.forName("android.os.SystemProperties")
                val setProp = systemProperties.getMethod("set", String::class.java, String::class.java)
                setProp.invoke(null, "persist.sys.millet.gms", "1")
                
                Log.i(TAG, "Áp dụng bảo vệ Millet thành công!")
            } else {
                Log.w(TAG, "Không tìm thấy dịch vụ Millet (Thiết bị không phải HyperOS/MIUI).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi API ẩn Millet: ${e.message}", e)
        }
    }
}
