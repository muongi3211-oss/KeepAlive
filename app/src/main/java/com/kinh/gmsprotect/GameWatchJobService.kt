package com.hanh.gmsprotect

import android.app.job.JobParameters
import android.app.job.JobService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.util.Arrays

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
                for (uri in uris) if (expected == uri) triggered = true
            }
        }
        val realUri = triggered
        val coreEnabled = GameWatchCore.isEnabled(this)
        if (coreEnabled) {
            GameWatchCore.onJobStart(this, realUri)
            GameWatchEvidence.noteUriJob(this, realUri)
        }
        GameWatchJournal.add(this, "JOB", "START pid=" + android.os.Process.myPid() + " uriTrigger=" + realUri + " uris=" + urisText)

        main.post {
            if (!GameWatchScheduler.shouldWatch(this@GameWatchJobService)) {
                jobFinished(params, false)
                return@post
            }
            if (coreEnabled) {
                GameWatchCore.observe(this@GameWatchJobService, "JOB")
                var missing = false
                var beforeReadable = false
                try {
                    val before = GameWatchCore.raw(this@GameWatchJobService)
                    missing = !GameWatchCore.contains(before, GameWatchCore.GMS) || !GameWatchCore.contains(before, packageName)
                    beforeReadable = true
                } catch (err: Throwable) {
                    GameWatchJournal.add(this@GameWatchJobService, "COMPAT", "read before job failed: $err")
                }
                val writesBefore = GameWatchCore.repairCount(this@GameWatchJobService)
                val repairOk = GameWatchCore.ensureTargets(this@GameWatchJobService, if (realUri) "URI_TRIGGER" else "JOB_OTHER")
                val written = repairOk && GameWatchCore.repairCount(this@GameWatchJobService) > writesBefore
                var bothAfter = false
                try {
                    val after = GameWatchCore.raw(this@GameWatchJobService)
                    bothAfter = GameWatchCore.contains(after, GameWatchCore.GMS) && GameWatchCore.contains(after, packageName)
                } catch (err: Throwable) {
                    GameWatchJournal.add(this@GameWatchJobService, "COMPAT", "read after job failed: $err")
                }
                GameWatchEvidence.noteRepair(this@GameWatchJobService, realUri, beforeReadable && missing, written, bothAfter)
            }
            try {
                AdvancedAppProtector.maintain(this@GameWatchJobService, if (realUri) "GAME_URI_JOB" else "GAME_JOB")
            } catch (error: Throwable) {
                GameWatchJournal.add(this@GameWatchJobService, "ADV", "Job exception: $error")
            }
            val rearmed = GameWatchScheduler.rearm(this@GameWatchJobService)
            GameWatchJournal.add(this@GameWatchJobService, "JOB", "END rearm=$rearmed")
            if (!rearmed) jobFinished(params, true)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        GameWatchJournal.add(this, "JOB", "STOP id=" + (params?.jobId ?: -1))
        return false
    }
}
