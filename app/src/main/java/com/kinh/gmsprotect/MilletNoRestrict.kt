package com.kinh.gmsprotect

import android.content.Context
import android.provider.Settings
import android.util.Log

object MilletNoRestrict {
    private const val TAG = "MilletNoRestrict"
    const val KEY = "MILLET_NO_RESTRICT_APP"
    const val GMS_PACKAGE = "com.google.android.gms"
    const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun canWrite(context: Context): Boolean = try {
        Settings.System.canWrite(context.applicationContext)
    } catch (e: Throwable) {
        false
    }

    fun containsPackage(context: Context, pkg: String): Boolean = try {
        val raw = Settings.System.getString(context.applicationContext.contentResolver, KEY) ?: ""
        parsePackages(raw).contains(pkg)
    } catch (e: Throwable) {
        false
    }

    fun containsGms(context: Context): Boolean = containsPackage(context, GMS_PACKAGE)
    fun containsPlayStore(context: Context): Boolean = containsPackage(context, PLAY_STORE_PACKAGE)

    fun ensureGms(context: Context): Boolean {
        val app = context.applicationContext
        if (!canWrite(app)) return false

        try {
            val resolver = app.contentResolver
            val raw = Settings.System.getString(resolver, KEY) ?: ""
            val currentList = parsePackages(raw).toMutableList()

            // Thêm các gói bắt buộc nếu chưa có
            if (!currentList.contains(GMS_PACKAGE)) currentList.add(GMS_PACKAGE)
            if (!currentList.contains(PLAY_STORE_PACKAGE)) currentList.add(PLAY_STORE_PACKAGE)
            if (!currentList.contains(app.packageName)) currentList.add(app.packageName)

            // Nối chuỗi bằng dấu phẩy chuẩn
            val newListStr = currentList.joinToString(",")

            // Ép ghi trực tiếp vào Settings.System
            val isWritten = Settings.System.putString(resolver, KEY, newListStr)

            // Đọc lại để xác nhận kết quả ghi
            val verifiedRaw = Settings.System.getString(resolver, KEY) ?: ""
            val verifiedList = parsePackages(verifiedRaw)

            return isWritten && verifiedList.contains(GMS_PACKAGE)
        } catch (e: Throwable) {
            Log.e(TAG, "ensureGms failed", e)
            return false
        }
    }

    private fun parsePackages(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
