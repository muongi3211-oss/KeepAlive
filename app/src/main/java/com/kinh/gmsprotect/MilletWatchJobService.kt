package com.kinh.gmsprotect

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import android.util.Log

class MilletWatchJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            val before = MilletNoRestrict.readRaw(this)
            val hasGmsBefore = containsPackage(before, "com.google.android.gms")
            val hasPlayStoreBefore = containsPackage(before, "com.android.vending")
            val hasProtectorBefore = containsPackage(before, packageName)

            if (!hasGmsBefore || !hasPlayStoreBefore || !hasProtectorBefore) {
                MilletNoRestrict.ensureGms(this)
            }
        } catch (t: Throwable) {
            Log.e(MilletWatchScheduler.TAG, "JOB exception=${t.message}", t)
        }

        // Tái vũ trang (Rearm) JobScheduler sau khi chạy xong
        Handler(Looper.getMainLooper()).postDelayed({
            MilletWatchScheduler.schedule(applicationContext)
        }, 500L)

        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    private fun containsPackage(raw: String?, packageName: String): Boolean {
        if (raw.isNullOrBlank() || packageName.isBlank()) return false
        val normalized = raw.replace(';', ',').replace('\n', ',').replace('\t', ',')
        for (token in normalized.split(",")) {
            if (packageName == token.trim()) return true
        }
        return false
    }
}
