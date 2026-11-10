package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

object MilletWatchScheduler {
    fun isSupported(): Boolean = true

    fun isScheduled(context: Context): Boolean {
        return try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.allPendingJobs?.any { it.id == 1002 } == true
        } catch (_: Throwable) {
            false
        }
    }

    fun ensure(context: Context, reason: String) {
        try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val componentName = ComponentName(context, MilletWatchJobService::class.java)
            val builder = JobInfo.Builder(1002, componentName).apply {
                setPersisted(true)
                setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                setPeriodic(4 * 60 * 60 * 1000L)
            }
            scheduler.schedule(builder.build())
        } catch (_: Throwable) {}
    }
}
