package com.kinh.gmsprotect

import android.app.job.JobParameters
import android.app.job.JobService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.util.Arrays

/** Short lived; started by system_server on the Settings content-URI event. */
class GameWatchJobService : JobService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onStartJob(params: JobParameters?): Boolean {
        if (!GameWatchScheduler.shouldWatch(this)) return false
        var triggered = false
        var urisText = "[]"
        if (Build.VERSION.SDK_INT >= 24 && params != null) {
            val uris = params.triggeredContentUris
            urisText = if (uris == null) "[]" else Arrays.toString(uris)
            if (uris != null) {
                val expected = Settings.System.getUriFor(GameWatchCore.KEY)
                for (uri in uris) {
                    if (expected == uri) {
                        triggered = true
                    }
                }
            }
        }
        val realUri = triggered
        val coreEnabled = GameWatchCore.isEnabled(this)
        if (coreEnabled) {
            GameWatchCore.onJobStart(this, realUri)
            GameWatchEvidence.noteUriJob(this, realUri)
        }
        GameWatchJournal.add(
            this, "JOB", "START pid=" + android.os.Process.myPid()
                    + " uriTrigger=" + realUri + " uris=" + urisText
        )

        // Schedule work AFTER returning true. The rearm replaces the one-shot job
        // and Android may then call onStopJob for that replaced instance.
        main.post {
            if (!GameWatchScheduler.shouldWatch(this)) {
                jobFinished(params, false)
                return@post
            }
            if (coreEnabled) {
                GameWatchCore.observe(this, "JOB")
                // Capture the state BEFORE repairing: a mere read-back or a forced
                // job must not earn the passive repair PASS.
                var missing = false
                var beforeReadable = false
                try {
                    val before = GameWatchCore.raw(this)
                    missing = !GameWatchCore.contains(before, GameWatchCore.GMS)
                            || !GameWatchCore.contains(before, packageName)
                    beforeReadable = true
                } catch (err: Throwable) {
                    GameWatchJournal.add(this, "COMPAT", "read before job failed: $err")
                }
                // Even a system-forced callback may repair; only real content URI
                // events count toward compatibility evidence.
                val writesBefore = GameWatchCore.repairCount(this)
                val repairOk = GameWatchCore.ensureTargets(
                    this,
                    if (realUri) "URI_TRIGGER" else "JOB_OTHER"
                )
                // A successful read alone must never be treated as a performed repair.
                val written = repairOk && GameWatchCore.repairCount(this) > writesBefore
                var bothAfter = false
                try {
                    val after = GameWatchCore.raw(this)
                    bothAfter = GameWatchCore.contains(after, GameWatchCore.GMS)
                            && GameWatchCore.contains(after, packageName)
                } catch (err: Throwable) {
                    GameWatchJournal.add(this, "COMPAT", "read after job failed: $err")
                }
                GameWatchEvidence.noteRepair(
                    this, realUri, beforeReadable && missing,
                    written, bothAfter
                )
            } // Core GMS verification must not be awarded for advanced-only jobs.
            try {
                AdvancedAppProtector.maintain(this, if (realUri) "GAME_URI_JOB" else "GAME_JOB")
            } catch (error: Throwable) {
                GameWatchJournal.add(this, "ADV", "Job exception: $error")
            }
            val rearmed = GameWatchScheduler.rearm(this)
            GameWatchJournal.add(this, "JOB", "END rearm=$rearmed")
            if (!rearmed) {
                jobFinished(params, true)
            }
            // When successful, re-scheduling same ID replaces this job.
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        GameWatchJournal.add(this, "JOB", "STOP id=" + (params?.jobId ?: -1))
        return false
    }
}
