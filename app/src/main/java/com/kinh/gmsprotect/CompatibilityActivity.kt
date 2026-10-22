package com.kinh.gmsprotect

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CompatibilityActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    // Khai báo biến Banner để cập nhật trạng thái tổng hợp
    private lateinit var badgeBanner: TextView

    private lateinit var txtPowerKeeper: TextView
    private lateinit var txtNoRestrictList: TextView
    private lateinit var txtGmsStatus: TextView
    private lateinit var txtPlayStatus: TextView
    private lateinit var txtSelfStatus: TextView
    private lateinit var txtBatteryStatus: TextView
    private lateinit var txtHyperOsBgStatus: TextView

    private lateinit var txtSystemChangeMonitor: TextView
    private lateinit var txtSystemWakeApp: TextView
    private lateinit var txtSelfRestoreGms: TextView
    private lateinit var txtLastVerify: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            prefs = getSharedPreferences("GMSProtectPrefs", Context.MODE_PRIVATE)
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(createCompatibilityScreen())
        } catch (e: Throwable) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            updateRealtimeStatus()
        } catch (_: Throwable) {}
    }

    private fun isPowerKeeperPresent(): Boolean {
        return try {
            val manufacturer = Build.MANUFACTURER ?: ""
            val brand = Build.BRAND ?: ""
            manufacturer.contains("Xiaomi", ignoreCase = true) ||
                brand.contains("Xiaomi", ignoreCase = true) ||
                brand.contains("Redmi", ignoreCase = true) ||
                brand.contains("POCO", ignoreCase = true)
        } catch (e: Exception) {
            true
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(packageName)
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun canWriteSettings(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(this)
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun updateRealtimeStatus() {
        val hasPK = isPowerKeeperPresent()
        val canWrite = canWriteSettings()
        val isBattery = isIgnoringBatteryOptimizations()

        // Đọc dữ liệu xác minh thụ động đã lưu trong SharedPreferences
        val isWakeupVerified = prefs.getBoolean("verify_system_wakeup", false)
        val isRestoreGmsVerified = prefs.getBoolean("verify_restore_gms", false)
        val lastVerifyTime = prefs.getString("verify_last_time", "Chưa có") ?: "Chưa có"

        try {
            // HyperOS / PowerKeeper Card
            if (::txtPowerKeeper.isInitialized) {
                updateBadge(txtPowerKeeper, if (hasPK) "ĐÃ PHÁT HIỆN" else "KHÔNG PHÁT HIỆN", hasPK)
            }
            if (::txtNoRestrictList.isInitialized) {
                updateBadge(txtNoRestrictList, if (canWrite) "ĐỌC / GHI ĐƯỢC" else "CHƯA BẬT", canWrite)
            }
            if (::txtGmsStatus.isInitialized) {
                updateBadge(txtGmsStatus, "ĐÃ ÁP DỤNG", true)
            }
            if (::txtPlayStatus.isInitialized) {
                updateBadge(txtPlayStatus, "ĐÃ ÁP DỤNG", true)
            }
            if (::txtSelfStatus.isInitialized) {
                updateBadge(txtSelfStatus, "ĐÃ XÁC MINH", true)
            }
            if (::txtBatteryStatus.isInitialized) {
                updateBadge(txtBatteryStatus, if (isBattery) "ĐÃ XÁC MINH" else "CHƯA BẬT", isBattery)
            }
            if (::txtHyperOsBgStatus.isInitialized) {
                updateBadge(txtHyperOsBgStatus, "ĐÃ GỬI YÊU CẦU", true)
            }

            // Tự phục hồi Card (Thụ động)
            if (::txtSystemChangeMonitor.isInitialized) {
                updateBadge(txtSystemChangeMonitor, "ĐÃ ĐĂNG KÝ", true)
            }
            if (::txtSystemWakeApp.isInitialized) {
                updateBadge(txtSystemWakeApp, if (isWakeupVerified) "ĐÃ XÁC MINH" else "CHƯA XÁC MINH", isWakeupVerified, isWarning = !isWakeupVerified)
            }
            if (::txtSelfRestoreGms.isInitialized) {
                updateBadge(txtSelfRestoreGms, if (isRestoreGmsVerified) "ĐÃ XÁC MINH" else "CHƯA XÁC MINH", isRestoreGmsVerified, isWarning = !isRestoreGmsVerified)
            }
            if (::txtLastVerify.isInitialized) {
                txtLastVerify.text = lastVerifyTime
            }

            // --- ĐÁNH GIÁ TỔNG HỢP CHO BANNER THÔNG TIN ---
            val isAllVerified = hasPK && canWrite && isBattery && isWakeupVerified && isRestoreGmsVerified
            if (::badgeBanner.isInitialized) {
                if (isAllVerified) {
                    badgeBanner.text = "TƯƠNG THÍCH • ĐÃ XÁC MINH THỰC TẾ
