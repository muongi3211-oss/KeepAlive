package com.example.fcmguard

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

class FcmKeepAliveJobService : JobService() {

    companion object {
        private const val JOB_ID = 2026

        fun scheduleJob(context: Context) {
            val componentName = ComponentName(context, FcmKeepAliveJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, componentName)
                .setPersisted(true)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false) // Sửa lại đúng tên phương thức của JobInfo.Builder

            // Đặt thời gian lặp lại định kỳ 15 phút (an toàn và tương thích mọi phiên bản Android)
            val intervalMillis = 15 * 60 * 1000L
            builder.setPeriodic(intervalMillis)

            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            scheduler.schedule(builder.build())
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val serviceIntent = Intent(applicationContext, FcmMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(serviceIntent)
        } else {
            applicationContext.startService(serviceIntent)
        }
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }
}
