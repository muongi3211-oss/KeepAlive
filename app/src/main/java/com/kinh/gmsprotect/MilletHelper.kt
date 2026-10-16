package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object MilletHelper {
    private const val TAG = "GMSProtect"
    private const val JOB_ID = 10086
    private const val MILLET_NO_RESTRICT_APP = "MILLET_NO_RESTRICT_APP"

    // Biến lưu trạng thái theo dõi lệnh can thiệp hệ thống sâu để MainActivity hiển thị
    var systemPropStatus: String = "Đang kiểm tra..."
    var isPropAccepted: Boolean = false

    /**
     * Gọi API ẩn Millet của HyperOS/MIUI thông qua Reflection
     */
    fun applyMilletProtection(context: Context) {
        try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "millet")

            val systemProperties = Class.forName("android.os.SystemProperties")
            val setProp = systemProperties.getMethod("set", String::class.java, String::class.java)
            setProp.invoke(null, "persist.sys.millet.gms", "1")

            // Đọc ngược lại giá trị SystemProperties để xác thực hệ thống có thực sự chấp nhận không
            val getProp = systemProperties.getMethod("get", String::class.java, String::class.java)
            val currentValue = getProp.invoke(null, "persist.sys.millet.gms", "0") as? String

            if (currentValue == "1") {
                isPropAccepted = true
                systemPropStatus = "THÀNH CÔNG (ĐÃ XÁC THỰC)"
                Log.i(TAG, "Cờ Millet đã được hệ thống ghi nhận và chấp nhận thành công!")
            } else {
                isPropAccepted = false
                systemPropStatus = "BỊ HỆ THỐNG CHẶN NGẦM"
                Log.w(TAG, "Lệnh gọi không báo lỗi nhưng giá trị hệ thống không đổi (Bị ROM khóa)")
            }

            if (binder != null) {
                Log.d(TAG, "Đã kết nối dịch vụ Millet. Đang áp dụng cờ: $MILLET_NO_RESTRICT_APP")
            } else {
                Log.w(TAG, "Dịch vụ Millet không tồn tại trên thiết bị này.")
            }
        } catch (e: SecurityException) {
            isPropAccepted = false
            systemPropStatus = "BỊ CHẶN (SECURITY EXCEPTION)"
            Log.e(TAG, "Android/HyperOS từ chối quyền ghi SystemProperties: ${e.message}")
        } catch (e: Exception) {
            isPropAccepted = false
            systemPropStatus = "LỖI: ${e.javaClass.simpleName}"
            Log.e(TAG, "Lỗi khi gọi API Millet: ${e.message}", e)
        }
    }

    /**
     * Đăng ký JobScheduler chạy định kỳ mỗi 15 phút
     */
    fun scheduleMilletJob(context: Context) {
        try {
            val componentName = ComponentName(context, MilletWatchJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, componentName)
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)

            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val resultCode = jobScheduler.schedule(builder.build())

            if (resultCode == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "Đã lập lịch MilletWatchJobService thành công!")
            } else {
                Log.e(TAG, "Lập lịch MilletWatchJobService thất bại!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lập lịch Job: ${e.message}")
        }
    }

    /**
     * Khởi chạy Foreground Service
     */
    fun startProtectionService(context: Context) {
        try {
            val serviceIntent = Intent(context, MyForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi khởi chạy ForegroundService: ${e.message}")
        }
    }
}
