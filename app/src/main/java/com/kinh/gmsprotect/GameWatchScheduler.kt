package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build

object GameWatchScheduler {

    fun ensure(context: Context, reason: String) {
        try {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val componentName = ComponentName(context, GameWatchJobService::class.java)
            
            val builder = JobInfo.Builder(1001, componentName).apply {
                // Tận dụng sự kiện khởi động lại và kết nối mạng để check/vá tự động
                setPersisted(true)
                setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                // Giãn cách thời gian thực thi cực thưa để tiết kiệm pin tuyệt đối (ví dụ: chạy ngầm nhẹ mỗi vài tiếng hoặc theo sự kiện)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setPeriodic(4 * 60 * 60 * 1000L, JobInfo.flexibilityMillisNotSpecified)
                } else {
                    setPeriodic(4 * 60 * 60 * 1000L)
                }
            }

            scheduler.schedule(builder.build())
            AdvancedAppProtector.log(context, "JobScheduler scheduled: $reason")
        } catch (e: Throwable) {
            android.util.Log.e("GameWatchScheduler", "Error scheduling job", e)
        }
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
