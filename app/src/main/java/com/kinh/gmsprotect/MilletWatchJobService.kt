package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log

class MilletWatchJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            val hasGms = MilletNoRestrict.containsPackage(this, MilletNoRestrict.GMS_PACKAGE)
            val hasSelf = MilletNoRestrict.containsPackage(this, packageName)

            if (!hasGms || !hasSelf) {
                MilletNoRestrict.ensureGms(this)
                Log.i("MilletWatch", "Auto-repaired missing package in background")
            }
        } catch (e: Throwable) {
            Log.e("MilletWatch", "Job execution error", e)
        }
        
        // Lập lịch lại watcher
        schedule(applicationContext)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 0x47534D57

        fun schedule(context: Context) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return
            try {
                val app = context.applicationContext
                val scheduler = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val uri = Settings.System.getUriFor(MilletNoRestrict.KEY)

                val job = JobInfo.Builder(JOB_ID, ComponentName(app, MilletWatchJobService::class.java))
                    .addTriggerContentUri(JobInfo.TriggerContentUri(uri, 0))
                    .setTriggerContentUpdateDelay(1000L)
                    .setTriggerContentMaxDelay(5000L)
                    .build()

                scheduler.schedule(job)
            } catch (e: Throwable) {
                Log.w("MilletWatch", "Schedule failed: ${e.message}")
            }
        }
    }
}
