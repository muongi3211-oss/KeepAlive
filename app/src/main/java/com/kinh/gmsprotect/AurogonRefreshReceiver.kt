package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AurogonRefreshReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REFRESH_ALARM = "com.kinh.gmsprotect.AUROGON_REFRESH_ALARM"
        const val EXTRA_STAGE = "extra_stage"
        const val STAGE_PERIODIC = 1
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_REFRESH_ALARM) {
            val stage = intent.getIntExtra(EXTRA_STAGE, STAGE_PERIODIC)
            handleRefreshAlarm(context, stage)
        }
    }

    private fun handleRefreshAlarm(context: Context, stage: Int) {
        try {
            MilletNoRestrict.ensureGms(context)
            AurogonAllowlistService.startIfEnabled(context)
        } catch (_: Throwable) {}
    }
}
