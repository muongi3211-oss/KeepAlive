package com.hanh.gmsprotect

import android.app.AppOpsManager
import android.content.Context
import java.lang.reflect.Method

object XiaomiSetupState {
    private const val OP_AUTO_START = 10008

    enum class AutostartState {
        ALLOWED,
        DENIED,
        UNKNOWN
    }

    fun getAutostartState(context: Context): AutostartState {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return AutostartState.UNKNOWN

        return try {
            val method = AppOpsManager::class.java.getDeclaredMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            method.isAccessible = true
            val value = method.invoke(
                manager,
                OP_AUTO_START,
                app.applicationInfo.uid,
                app.packageName
            )
            if (value !is Int) return AutostartState.UNKNOWN

            if (value == AppOpsManager.MODE_ALLOWED) AutostartState.ALLOWED else AutostartState.DENIED
        } catch (ignored: Throwable) {
            AutostartState.UNKNOWN
        }
    }
}
