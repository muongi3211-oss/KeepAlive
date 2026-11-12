package com.kinh.gmsprotect

import android.app.AppOpsManager
import android.content.Context
import java.lang.reflect.Method

/**
 * Best-effort reader for Xiaomi/HyperOS setup state.
 *
 * OP_AUTO_START (10008) is Xiaomi's AppOps entry for Autostart on this ROM
 * family. We only read it; the user still changes the value in SecurityCenter.
 * If a future HyperOS build blocks this hidden read, callers receive UNKNOWN
 * and the UI falls back to a manual confirmation instead of pretending success.
 */
object XiaomiSetupState {
    private const val OP_AUTO_START = 10008

    enum class AutostartState {
        ALLOWED,
        DENIED,
        UNKNOWN
    }

    fun getAutostartState(context: Context): AutostartState {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return AutostartState.UNKNOWN

        return try {
            val method: Method = AppOpsManager::class.java.getDeclaredMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
            ).apply {
                isAccessible = true
            }
            val value = method.invoke(
                manager,
                OP_AUTO_START,
                app.applicationInfo.uid,
                app.packageName
            )
            if (value !is Int) return AutostartState.UNKNOWN

            val mode = value
            if (mode == AppOpsManager.MODE_ALLOWED) AutostartState.ALLOWED else AutostartState.DENIED
        } catch (ignored: Throwable) {
            AutostartState.UNKNOWN
        }
    }
}
