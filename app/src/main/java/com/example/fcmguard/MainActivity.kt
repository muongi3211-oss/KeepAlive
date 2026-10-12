package com.example.fcmguard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var tvLogViewer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kiểm tra quyền thay đổi cài đặt hệ thống khi mở app
        requestWriteSettingsPermission()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val tvStatus = TextView(this).apply {
            text = "🛡️ FCM Guard - Đang bảo vệ kết nối"
            textSize = 16f
            setPadding(0, 0, 0, 20)
        }

        // 1. Nút kích hoạt Foreground Service
        val btnStart = Button(this).apply {
            text = "1. Kích hoạt giám sát ngầm"
            setOnClickListener {
                try {
                    val serviceIntent = Intent(this@MainActivity, FcmMonitorService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    Toast.makeText(this@MainActivity, "Đã khởi động dịch vụ giám sát!", Toast.LENGTH_SHORT).show()
                    refreshLogs()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 2. Nút mở trang Autostart của HyperOS
        val btnAutoStart = Button(this).apply {
            text = "2. Mở cài đặt Tự khởi chạy (Autostart)"
            setOnClickListener {
                openMiuiAutostartSettings()
            }
        }

        // 3. Nút yêu cầu bỏ hạn chế pin
        val btnBattery = Button(this).apply {
            text = "3. Bỏ hạn chế pin hệ thống"
            setOnClickListener {
                requestBatteryOptimization()
            }
        }

        // Khu vực hiển thị Log trực tiếp trên giao diện
        val tvLogTitle = TextView(this).apply {
            text = "📋 Nhật ký hệ thống (Logs):"
            textSize = 14f
            setPadding(0, 30, 0, 10)
        }

        tvLogViewer = TextView(this).apply {
            text = "Đang tải nhật ký..."
            textSize = 12f
            setTextColor(0xFF00FF00.toInt()) // Chữ xanh lá kiểu terminal
            setBackgroundColor(0xFF000000.toInt()) // Nền đen
            setPadding(20, 20, 20, 20)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
            )
            addView(tvLogViewer)
        }

        rootLayout.addView(tvStatus)
        rootLayout.addView(btnStart)
        rootLayout.addView(btnAutoStart)
        rootLayout.addView(btnBattery)
        rootLayout.addView(tvLogTitle)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)
        refreshLogs()
    }

    private fun refreshLogs() {
        // Tải log từ LogStore nếu có, hoặc hiển thị trạng thái sẵn sàng
        tvLogViewer.text = "System initialized.\nReady to monitor FCM connections."
    }

    private fun requestWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun openMiuiAutostartSettings() {
        try {
            val intent = Intent().apply {
                setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            }
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
