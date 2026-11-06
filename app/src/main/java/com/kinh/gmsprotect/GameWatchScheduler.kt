package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings

object GameWatchScheduler {
    const val JOB_ID = 0x47414D45

    private fun get(c: Context): JobScheduler? {
        return c.applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
    }

    fun shouldWatch(c: Context): Boolean = GameWatchCore.isEnabled(c)

    fun isPending(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        return try {
            val s = get(c)
            s != null && s.getPendingJob(JOB_ID) != null
        } catch (ex: Throwable) {
            GameWatchJournal.add(c, "WATCH", "isPending error: $ex")
            false
        }
    }

    fun keepOrCancel(c: Context) {
        if (shouldWatch(c)) ensure(c, "KEEP_ADVANCED")
        else cancel(c)
    }

    fun cancel(c: Context) {
        try {
            val s = get(c)
            s?.cancel(JOB_ID)
        } catch (ex: Throwable) {
            GameWatchJournal.add(c, "WATCH", "cancel error: $ex")
        }
    }

    fun ensure(c: Context, reason: String): Boolean = schedule(c, reason, false)

    fun rearm(c: Context): Boolean = schedule(c, "JOB_REARM", true)

    @Synchronized
    private fun schedule(c: Context, reason: String, replace: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 24 || !shouldWatch(c)) return false
        val js = get(c) ?: return false
        if (!replace && isPending(c)) return true
        return try {
            val uri = Settings.System.getUriFor(GameWatchCore.KEY)
            val job = JobInfo.Builder(JOB_ID, ComponentName(c, GameWatchJobService::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(uri, 0))
                .setTriggerContentUpdateDelay(1000L)
                .setTriggerContentMaxDelay(5000L)
                .build()
            val ok = js.schedule(job) == JobScheduler.RESULT_SUCCESS
            GameWatchJournal.add(c, "WATCH", "schedule=$ok reason=$reason id=$JOB_ID")
            ok
        } catch (ex: Throwable) {
            GameWatchJournal.add(c, "WATCH", "schedule error=$ex")
            false
        }
    }
}
