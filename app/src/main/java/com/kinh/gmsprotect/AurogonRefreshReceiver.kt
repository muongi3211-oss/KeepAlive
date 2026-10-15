package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AurogonRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (AurogonAllowlistService.ACTION_REFRESH_ALARM != intent.action) return

        val stage = intent.getIntExtra(
            AurogonAllowlistService.EXTRA_STAGE,
            AurogonAllowlistService.STAGE_PERIODIC
        )
        AurogonAllowlistService.handleRefreshAlarm(context, stage)
    }
}
