package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle

class MilletWatchJobService : JobService() {
    companion object {
        private const val JOB_ID = 202610

        fun schedule(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val component = ComponentName(context, MilletWatchJobService::class.java)
                
                val builder = JobInfo.Builder(JOB_ID, component).apply {
                    setPeriodic(15 * 60 * 1000L) // Chạy định kỳ mỗi 15 phút
                    setPersisted(true)           // Tự động khôi phục sau khi khởi động lại máy
                    setRequiresDeviceIdle(false)
                    setRequiresCharging(false)
                }

                scheduler.schedule(builder.build())
            } catch (_: Throwable) {}
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            MilletNoRestrict.ensureGms(applicationContext)
            AurogonAllowlistService.startIfEnabled(applicationContext)
        } catch (_: Throwable) {}
        
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Lên lịch chạy lại nếu bị dừng đột ngột
        schedule(applicationContext)
        return true
    }
}
