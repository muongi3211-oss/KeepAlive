package com.kinh.gmsprotect

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MilletWatchJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            val before = MilletNoRestrict.readRaw(this)
            val hasGmsBefore = containsPackage(before, "com.google.android.gms")
            val hasPlayStoreBefore = containsPackage(before, "com.android.vending")
            val hasProtectorBefore = containsPackage(before, packageName)

            val milletTriggered = isMilletTrigger(params)
            val prefs = getSharedPreferences("GMSProtectPrefs", Context.MODE_PRIVATE)
            val currentTime = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date())

            // Ghi nhận khi hệ thống đánh thức ứng dụng qua JobScheduler
            if (milletTriggered) {
                prefs.edit()
                    .putBoolean("verify_system_wakeup", true)
                    .putString("verify_last_time", currentTime)
                    .apply()
            }

            var repairResult: MilletNoRestrict.Result? = null
            if (!hasGmsBefore || !hasPlayStoreBefore || !hasProtectorBefore) {
                repairResult = MilletNoRestrict.ensureGms(this)

                val after = MilletNoRestrict.readRaw(this)
                val hasGmsAfter = containsPackage(after, "com.google.android.gms")

                // Ghi nhận khi tự khôi phục thành công GMS bị thiếu
                if (milletTriggered && !hasGmsBefore && hasGmsAfter &&
                    (repairResult == MilletNoRestrict.Result.ADDED || repairResult == MilletNoRestrict.Result.ALREADY_PRESENT)) {
                    prefs.edit()
                        .putBoolean("verify_restore_gms", true)
                        .putString("verify_last_time", currentTime)
                        .apply()
                }
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

    private fun isMilletTrigger(params: JobParameters?): Boolean {
        if (params == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val uris = params.triggeredContentUris ?: return false
            val expected = Settings.System.getUriFor(MilletNoRestrict.KEY)
            uris.any { expected == it }
        } catch (_: Throwable) {
            false
        }
    }

    private fun containsPackage(raw: String?, packageName: String): Boolean {
        if (raw.isNullOrBlank() || packageName.isBlank()) return false
        val normalized = raw.replace(';', ',').replace('\n', ',').replace('\t', ',')
        for (token in normalized.split(",")) {
            if (packageName == token.trim()) return true
        }
        return false
    }
}
