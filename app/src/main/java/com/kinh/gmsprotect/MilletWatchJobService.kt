package com.kinh.gmsprotect

import android.app.job.JobParameters
import android.app.job.JobService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Arrays

/**
 * Event-driven MILLET repair service.
 *
 * JobScheduler/system_server watches MILLET_NO_RESTRICT_APP. When PowerKeeper
 * rebuilds that setting, this job is started without any polling loop. The job
 * writes only when GMS, Play Store, or GMS Protect itself is missing, then
 * immediately returns and rearms the one-shot content watcher.
 */
class MilletWatchJobService : JobService() {
    companion object {
        private const val TAG = MilletWatchScheduler.TAG
        private const val REARM_DELAY_MS = 500L
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        var repairResult: MilletNoRestrict.Result? = null

        try {
            val before = MilletNoRestrict.readRaw(this)
            val hasGmsBefore = containsPackage(before, MilletNoRestrict.GMS_PACKAGE)
            val hasPlayStoreBefore = containsPackage(before, MilletNoRestrict.PLAY_STORE_PACKAGE)
            val hasProtectorBefore = containsPackage(before, packageName)

            val milletTriggered = isMilletTrigger(params)

            Log.i(
                TAG, "JOB TRIGGERED"
                        + " jobId=" + (params?.jobId ?: -1)
                        + " containsGms=$hasGmsBefore"
                        + " containsPlayStore=$hasPlayStoreBefore"
                        + " containsProtector=$hasProtectorBefore"
                        + " milletTriggered=$milletTriggered"
                        + " triggeredUris=" + triggeredUris(params)
                        + " current=" + safeValue(before)
            )

            // This is the strongest passive compatibility proof: system_server
            // actually delivered the MILLET content change to our dead/background app.
            if (milletTriggered) {
                CompatibilityVerification.markWatcherTriggered(this)
            }

            if (!hasGmsBefore || !hasPlayStoreBefore || !hasProtectorBefore) {
                repairResult = MilletNoRestrict.ensureGms(this)

                val after = MilletNoRestrict.readRaw(this)
                val hasGmsAfter = containsPackage(after, MilletNoRestrict.GMS_PACKAGE)
                val hasPlayStoreAfter = containsPackage(after, MilletNoRestrict.PLAY_STORE_PACKAGE)
                val hasProtectorAfter = containsPackage(after, packageName)

                Log.i(
                    TAG, "AUTO_REPAIR result=$repairResult"
                            + " containsGms=$hasGmsAfter"
                            + " containsPlayStore=$hasPlayStoreAfter"
                            + " containsProtector=$hasProtectorAfter"
                            + " current=" + safeValue(after)
                )

                val restored = hasGmsAfter && hasPlayStoreAfter && hasProtectorAfter
                        && (repairResult == MilletNoRestrict.Result.ADDED
                        || repairResult == MilletNoRestrict.Result.ALREADY_PRESENT)
                // Do not claim we restored anything if another writer fixed the
                // list first (ALREADY_PRESENT), or when read-back failed.
                if (restored && repairResult == MilletNoRestrict.Result.ADDED) {
                    GameWatchJournal.event(
                        this, "millet_repaired",
                        "MilletWatchJobService: trigger=$milletTriggered"
                                + " result=$repairResult verified=true"
                    )
                } else if (!restored) {
                    GameWatchJournal.event(
                        this, "millet_error",
                        "MilletWatchJobService: trigger=$milletTriggered"
                                + " result=$repairResult verified=false"
                                + " GMS=$hasGmsAfter Play=$hasPlayStoreAfter"
                                + " Protect=$hasProtectorAfter"
                    )
                }

                // Do not mark a mere scheduled/forced job as verified repair.
                // Require a real MILLET trigger, GMS missing before, and a
                // verified write by THIS app. A concurrent external repair returning
                // ALREADY_PRESENT must not be counted as our successful repair.
                if (milletTriggered
                    && !hasGmsBefore
                    && hasGmsAfter
                    && hasPlayStoreAfter
                    && hasProtectorAfter
                    && repairResult == MilletNoRestrict.Result.ADDED
                ) {
                    CompatibilityVerification.markAutoRepairVerified(this)
                }
            } else {
                Log.i(TAG, "AUTO_REPAIR skipped=already_present")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "JOB exception=" + errorOf(t), t)
            GameWatchJournal.event(this, "millet_error", "MilletWatchJobService exception: " + errorOf(t))
        }

        // Every MILLET watcher run also maintains user-selected MiPush/Game targets.
        // Core GMS/MILLET compatibility evidence above is deliberately unchanged.
        try {
            AdvancedAppProtector.maintain(this, "MILLET_JOB")
        } catch (error: Throwable) {
            Log.w(TAG, "Advanced list maintenance failed", error)
        }

        // TriggerContentUri jobs are one-shot after execution. Rearm after this
        // run. If our repair generated another Settings notification, the next
        // run sees both required packages already present and performs no write.
        Handler(Looper.getMainLooper()).postDelayed(
            { MilletWatchScheduler.schedule(applicationContext) },
            REARM_DELAY_MS
        )

        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.i(TAG, "JOB STOPPED jobId=" + (params?.jobId ?: -1))
        return false
    }

    private fun isMilletTrigger(params: JobParameters?): Boolean {
        if (params == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val uris = params.triggeredContentUris ?: return false
            val expected = android.provider.Settings.System.getUriFor(MilletNoRestrict.KEY)
            for (uri in uris) {
                if (expected == uri) return true
            }
            false
        } catch (ignored: Throwable) {
            false
        }
    }

    private fun triggeredUris(params: JobParameters?): String {
        if (params == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return "[]"
        return try {
            val uris = params.triggeredContentUris
            if (uris == null) "[]" else Arrays.toString(uris)
        } catch (t: Throwable) {
            "<unavailable:" + errorOf(t) + ">"
        }
    }

    private fun containsPackage(raw: String?, packageName: String?): Boolean {
        if (raw.isNullOrEmpty() || packageName == null) return false
        val normalized = raw.replace(';', ',')
            .replace('\n', ',')
            .replace('\t', ',')
        for (token in normalized.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (packageName == token.trim()) return true
        }
        return false
    }

    private fun safeValue(value: String?): String {
        if (value.isNullOrEmpty()) return "<empty>"
        if (value.length <= 1200) return value
        return value.substring(0, 1200) + "...(len=" + value.length + ")"
    }

    private fun errorOf(t: Throwable?): String {
        if (t == null) return "unknown"
        return t.javaClass.simpleName + ":" + t.message
    }
}
