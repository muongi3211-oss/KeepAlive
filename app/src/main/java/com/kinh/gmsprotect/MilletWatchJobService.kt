package com.kinh.gmsprotect

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context

class MilletWatchJobService : JobService() {
    companion object {
        private const val TAG = "MilletWatchJob"

        fun schedule(context: Context, reason: String) {
            MilletWatchScheduler.ensure(context, reason)
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            AdvancedAppProtector.maintain(applicationContext, TAG)
            jobFinished(params, false)
        } catch (_: Throwable) {}
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
