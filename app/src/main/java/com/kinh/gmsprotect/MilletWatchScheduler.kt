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
    private const val JOB_ID = 0x47534D57

    fun schedule(context: Context): Boolean {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.i(TAG, "SCHEDULE unsupported sdk=" + Build.VERSION.SDK_INT)
            return false
        }

        return try {
            val scheduler = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            if (scheduler == null) {
                Log.w(TAG, "SCHEDULE failed reason=no_JobScheduler")
                return false
            }

            val uri = Settings.System.getUriFor(MilletNoRestrict.KEY)
            val job = JobInfo.Builder(JOB_ID, ComponentName(app, MilletWatchJobService::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(uri, 0))
                .setTriggerContentUpdateDelay(1_000L)
                .setTriggerContentMaxDelay(5_000L)
                .build()

            val result = scheduler.schedule(job)
            val ok = result == JobScheduler.RESULT_SUCCESS
            Log.i(TAG, "SCHEDULE result=" + (if (ok) "SUCCESS" else "FAILURE") + " jobId=$JOB_ID uri=$uri")
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "SCHEDULE exception=" + errorOf(t), t)
            false
        }
    }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    fun isScheduled(context: Context): Boolean {
        if (!isSupported()) return false
        return try {
            val scheduler = context.applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
            scheduler?.getPendingJob(JOB_ID) != null
        } catch (t: Throwable) {
            Log.w(TAG, "isScheduled failed error=" + errorOf(t))
            false
        }
    }

    internal fun jobId(): Int = JOB_ID

    private fun errorOf(t: Throwable?): String {
        if (t == null) return "unknown"
        return t.javaClass.simpleName + ":" + t.message
    }
}
