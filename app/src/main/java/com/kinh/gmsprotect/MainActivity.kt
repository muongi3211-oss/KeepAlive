package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Dựng giao diện Onboarding / Màn hình cấp quyền trực tiếp bằng code
        setupOnboardingUI()

        // 2. Chạy cốt lõi bảo vệ Millet & JobScheduler ngay khi mở app
        applyMilletProtection(this)
        scheduleMilletJob(this)
        startProtectionService(this)
    }

    private fun setupOnboardingUI() {
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFFF8F9FA.toInt())
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 60, 50, 60)
        }

        // Tiêu đề ứng dụng
        val title = TextView(this).apply {
            text = "GMS-Protect"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF212529.toInt())
            setPadding(0, 0, 0, 16)
        }
        container.addView(title)

        // Mô tả trạng thái
        val desc = TextView(this).apply {
            text = "Ứng dụng đang giữ cho các dịch vụ Google (GMS/FCM) luôn hoạt động ổn định trên HyperOS mà không bị hệ thống bóp tiến trình ngầm."
            textSize = 14f
            setTextColor(0xFF6C757D.toInt())
            setPadding(0, 0, 0, 30)
        }
        container.addView(desc)

        // --- CÁC NÚT CẤP QUYỀN HỆ THỐNG ---
        
        // Nút 1: Yêu cầu bỏ qua tối ưu hóa pin
        val btnBattery = createStyledButton("1. Yêu cầu bỏ qua tối ưu hóa pin") {
            requestBatteryOptimization()
        }
        container.addView(btnBattery)

        // Nút 2: Mở cài đặt quyền hệ thống (Write Settings)
        val btnWriteSettings = createStyledButton("2. Cấp quyền thay đổi cài đặt") {
            openWriteSettingsAccess()
        }
        container.addView(btnWriteSettings)

        // Nút 3: Mở cài đặt Tự khởi động (Autostart của Xiaomi/HyperOS)
        val btnAutostart = createStyledButton("3. Bật Tự khởi động (Autostart)") {
            openAutostartSettings()
        }
        container.addView(btnAutostart)

        // Trạng thái thành công ở dưới cùng
        val statusText = TextView(this).apply {
            text = "✓ Cốt lõi Millet & JobScheduler đang chạy ngầm."
            textSize = 13f
            setTextColor(0xFF28A745.toInt())
            setPadding(0, 40, 0, 0)
        }
        container.addView(statusText)

        scroll.addView(container)
        setContentView(scroll)
    }

    private fun createStyledButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF007BFF.toInt())
            setPadding(30, 25, 30, 25)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 12, 0, 12)
            }
            setOnClickListener { onClick() }
        }
    }

    // --- CÁC HÀM XỬ LÝ QUYỀN HỆ THỐNG ---

    private fun requestBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Thiết bị không hỗ trợ tính năng này", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở cài đặt pin: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWriteSettingsAccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở cài đặt hệ thống", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAutostartSettings() {
        try {
            // Intent đặc trưng để mở trang quản lý tự khởi động của Xiaomi / HyperOS / MIUI
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback nếu máy không đúng package MIUI Security
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Vui lòng bật thủ công trong Cài đặt ứng dụng", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val TAG = "GMSProtect"
        private const val JOB_ID = 10086
        private const val MILLET_NO_RESTRICT_APP = "MILLET_NO_RESTRICT_APP"

        /**
         * Gọi API ẩn Millet của HyperOS/MIUI thông qua Reflection
         */
        fun applyMilletProtection(context: Context) {
            try {
                val serviceManager = Class.forName("android.os.ServiceManager")
                val getService = serviceManager.getMethod("getService", String::class.java)
                val binder = getService.invoke(null, "millet")

                if (binder != null) {
                    Log.d(TAG, "Đã kết nối dịch vụ Millet. Đang áp dụng cờ: $MILLET_NO_RESTRICT_APP")
                    
                    val systemProperties = Class.forName("android.os.SystemProperties")
                    val setProp = systemProperties.getMethod("set", String::class.java, String::class.java)
                    setProp.invoke(null, "persist.sys.millet.gms", "1")
                    
                    Log.i(TAG, "Áp dụng cờ Millet thành công!")
                } else {
                    Log.w(TAG, "Dịch vụ Millet không tồn tại trên thiết bị này.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi gọi API Millet: ${e.message}", e)
            }
        }

        /**
         * Đăng ký JobScheduler chạy định kỳ mỗi 15 phút
         */
        fun scheduleMilletJob(context: Context) {
            val componentName = ComponentName(context, MilletWatchJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, componentName)
                .setPeriodic(15 * 60 * 1000L) // 15 phút lập lịch 1 lần
                .setPersisted(true)           // Tự khôi phục sau khi khởi động lại máy
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)

            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val resultCode = jobScheduler.schedule(builder.build())

            if (resultCode == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "Đã lập lịch MilletWatchJobService thành công!")
            } else {
                Log.e(TAG, "Lập lịch MilletWatchJobService thất bại!")
            }
        }

        /**
         * Khởi chạy Foreground Service
         */
        fun startProtectionService(context: Context) {
            try {
                val serviceIntent = Intent(context, MyForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi khởi chạy ForegroundService: ${e.message}")
            }
        }
    }
}
