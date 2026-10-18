package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object MilletWatchScheduler {
    const val TAG = "MILLET_WATCH"
    private const val JOB_ID = 0x47534D57 // "GSMW"

    fun schedule(context: Context): Boolean {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        try {
            val scheduler = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return false
            val uri = Settings.System.getUriFor(MilletNoRestrict.KEY)
            
            val job = JobInfo.Builder(JOB_ID, ComponentName(app, MilletWatchJobService::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(uri, 0))
                .setTriggerContentUpdateDelay(1_000L)
                .setTriggerContentMaxDelay(5_000L)
                .build()

            val result = scheduler.schedule(job)
            return result == JobScheduler.RESULT_SUCCESS
        } catch (t: Throwable) {
            Log.e(TAG, "SCHEDULE exception=${t.javaClass.simpleName}:${t.message}", t)
            return false
        }
    }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    fun isScheduled(context: Context): Boolean {
        if (!isSupported()) return false
        try {
            val scheduler = context.applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return false
            return scheduler.getPendingJob(JOB_ID) != null
        } catch (t: Throwable) {
            return false
        }
    }
}
