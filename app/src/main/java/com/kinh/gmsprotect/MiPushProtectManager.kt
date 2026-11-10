package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import java.util.LinkedHashSet

object MiPushProtectManager {
    private const val TAG = "MiPushProtectManager"
    
    // Khóa hệ thống phổ biến cho danh sách dịch vụ đẩy/MiPush trên một số biến thể MIUI/HyperOS
    private const val MIPUSH_KEY = "mipush_no_restrict_list"
    private const val ALTERNATIVE_MIPUSH_KEY = "xiaomi_push_protected_apps"

    fun ensureMiPushProtection(context: Context): Boolean {
        val app = context.applicationContext
        return try {
            val resolver = app.contentResolver
            
            // 1. Thao tác với Settings System/Secure nếu ROM hỗ trợ ghi trực tiếp
            val currentPrimary = Settings.Secure.getString(resolver, MIPUSH_KEY) ?: ""
            val packages = parsePackages(currentPrimary)
            
            val addedGms = packages.add(GmsProtector.GMS_PACKAGE)
            val addedSelf = packages.add(app.packageName)
            val addedStore = packages.add("com.android.vending")

            if (addedGms || addedSelf || addedStore) {
                val merged = TextUtils.join(",", packages)
                try {
                    Settings.Secure.putString(resolver, MIPUSH_KEY, merged)
                } catch (_: Throwable) {}
                try {
                    Settings.System.putString(resolver, MIPUSH_KEY, merged)
                } catch (_: Throwable) {}
            }

            // 2. Gửi lệnh kích hoạt qua Intent Broadcast đặc trưng của MiPush Framework
            val broadcastIntent = Intent("com.xiaomi.mipush.RECEIVE_MESSAGE").apply {
                setPackage("com.xiaomi.xmsf")
                putExtra("miipush_payload_package", app.packageName)
                putExtra("protect_gms", true)
            }
            app.sendBroadcast(broadcastIntent)

            Log.i(TAG, "MiPush Protect list updated successfully for GMS and Self.")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "ensureMiPushProtection FAILED: ${t.message}")
            false
        }
    }

    private fun parsePackages(raw: String?): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        if (raw.isNullOrBlank()) return out
        for (token in raw.split(",")) {
            val value = token.trim()
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }
}
