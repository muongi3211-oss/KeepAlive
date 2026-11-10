package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent

object AdvancedAppProtector {
    fun maintain(context: Context, tag: String) {
        try {
            val intent = Intent(MiPushProtectManager.ACTION_REFRESH_MIPUSH).apply {
                setPackage("com.xiaomi.xmsf")
                putExtra("caller_tag", tag)
                putExtra("protect_gms", true)
            }
            context.sendBroadcast(intent)
        } catch (_: Throwable) {}
    }
}
