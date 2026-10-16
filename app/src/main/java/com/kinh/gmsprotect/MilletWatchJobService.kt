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
                    setPeriodic(15 * 60 * 1000L) // Chu kỳ định kỳ 15 phút chuẩn
                    setPersisted(true)           // Tự động khôi phục sau khi reboot máy
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
            // Ép ghi lại GMS và Play Store vào bảng Millet System ngay lập tức
            MilletNoRestrict.ensureGms(applicationContext)
            
            // Kích hoạt dịch vụ nền Aurogon Allowlist nếu đang bật
            AurogonAllowlistService.startIfEnabled(applicationContext)
        } catch (_: Throwable) {}
        
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Lên lịch chạy lại ngay lập tức nếu bị hệ thống ngắt giữa chừng
        schedule(applicationContext)
        return true
    }
}
