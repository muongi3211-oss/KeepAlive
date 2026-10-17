package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build

object MilletWatchScheduler {
    private const val JOB_ID = 2026102
    const val KEY = "millet_no_restrict_app"

    fun schedule(context: Context) {
        try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val component = ComponentName(context, MilletWatchJobService::class.java)

            val builder = JobInfo.Builder(JOB_ID, component).apply {
                setPeriodic(15 * 60 * 1000L) // Chạy định kỳ 15 phút
                setPersisted(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setRequiresBatteryNotLow(false)
                    setRequiresCharging(false)
                }
            }

            scheduler.schedule(builder.build())
        } catch (_: Throwable) {}
    }
}
