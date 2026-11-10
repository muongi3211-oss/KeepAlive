package com.kinh.gmsprotect

import android.app.job.JobParameters
import android.app.job.JobService

class GameWatchJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            GameWatchCore.rearm(applicationContext)
            jobFinished(params, false)
        } catch (_: Throwable) {}
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
