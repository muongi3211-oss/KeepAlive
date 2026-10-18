package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build

class MilletWatchJobService : JobService() {

    companion object {
        private const val JOB_ID = 2026102

        fun schedule(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val component = ComponentName(context, MilletWatchJobService::class.java)

                val builder = JobInfo.Builder(JOB_ID, component).apply {
                    setPeriodic(15 * 60 * 1000L) // Chạy định kỳ 15 phút/lần
                    setPersisted(true)           // Tự khởi động lại sau reboot
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setRequiresBatteryNotLow(false)
                        setRequiresCharging(false)
                    }
                }

                scheduler.schedule(builder.build())
            } catch (_: Throwable) {}
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            // Ép ghi toàn bộ danh sách Millet (GMS + Play Store + App)
            MilletNoRestrict.ensureGms(applicationContext)

            // Cập nhật trạng thái xác minh thụ động cho màn hình Kiểm tra tương thích
            val prefs = getSharedPreferences("GMSProtectPrefs", MODE_PRIVATE)
            val nowStr = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date())
            prefs.edit()
                .putBoolean("verify_system_wakeup", true)
                .putString("verify_last_time", nowStr)
                .apply()

            // Ping giữ kết nối FCM Socket luôn thông suốt
            FcmKeepAliveHelper.pingFcmConnection(applicationContext)

            // Khởi động lại Service nếu người dùng có bật cấu hình Aurogon
            AurogonAllowlistService.startIfEnabled(applicationContext)
        } catch (_: Throwable) {}

        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        schedule(applicationContext)
        return true
    }
}
