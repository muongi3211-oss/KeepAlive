package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

object GameWatchScheduler {
    fun ensure(context: Context, reason: String) {
        try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val componentName = ComponentName(context, GameWatchJobService::class.java)
            val builder = JobInfo.Builder(1001, componentName).apply {
                setPersisted(true)
                setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                setPeriodic(4 * 60 * 60 * 1000L)
            }
            scheduler.schedule(builder.build())
        } catch (_: Throwable) {}
    }

    fun cancel(context: Context) {
        try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.cancel(1001)
        } catch (_: Throwable) {}
    }

    fun shouldWatch(context: Context): Boolean {
        return try {
            AdvancedAppStore.list(context).isNotEmpty() || GameWatchCore.isEnabled(context)
        } catch (_: Throwable) {
            false
        }
    }

    fun isPending(context: Context): Boolean {
        return try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.allPendingJobs?.any { it.id == 1001 } == true
        } catch (_: Throwable) {
            false
        }
    }
}
