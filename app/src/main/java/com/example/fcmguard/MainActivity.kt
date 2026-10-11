package com.example.fcmguard

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
        }

        val title = TextView(this).apply {
            text = "FCM Guard - Giám sát & Chống Ngủ Đông"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        }
        layout.addView(title)

        val refreshButton = Button(this).apply {
            text = "Làm mới nhật ký"
            setOnClickListener {
                updateLogs()
            }
        }
        layout.addView(refreshButton)

        val clearButton = Button(this).apply {
            text = "Xóa toàn bộ log"
            setOnClickListener {
                LogStore.clear(this@MainActivity)
                updateLogs()
            }
        }
        layout.addView(clearButton)

        // Nút đường tắt: Mở cài đặt Dịch vụ Google Play trực tiếp
        val openGmsButton = Button(this).apply {
            text = "Mở cài đặt Dịch vụ Google Play"
            setOnClickListener {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:com.google.android.gms")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Không mở được cài đặt: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(openGmsButton)

        // Nút đường tắt: Yêu cầu bỏ hạn chế pin cho FCM Guard
        val batteryOptButton = Button(this).apply {
            text = "Tắt hạn chế pin cho FCM Guard"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Thiết bị không hỗ trợ", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(batteryOptButton)

        logTextView = TextView(this).apply {
            text = "Đang tải dữ liệu...\n"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 20, 0, 0)
        }

        val scrollView = ScrollView(this).apply {
            addView(logTextView)
        }
        layout.addView(scrollView)

        // Nút bấm cập nhật: Mở trực tiếp trình duyệt để tải file ổn định tuyệt đối
        val updateButton = Button(this).apply {
            text = "Kiểm tra & Tải bản cập nhật mới"
            setOnClickListener {
                val releaseUrl = "https://github.com/muongi311-oss/KeepAlive/releases/latest"
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
                    startActivity(intent)
                    Toast.makeText(this@MainActivity, "Đang mở trang tải về...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Không mở được trình duyệt: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        layout.addView(updateButton)

        setContentView(layout)

        LogStore.add(applicationContext, "System: Mở ứng dụng FCM Guard thành công.")
        startGuardService()
    }

    override fun onResume() {
        super.onResume()
        updateLogs()
    }

    private fun updateLogs() {
        val logs = LogStore.getLogs(this)
        val sb = StringBuilder()
        for (log in logs) {
            sb.append(log).append("\n\n")
        }
        logTextView.text = if (sb.isEmpty()) "Chưa có sự kiện nào." else sb.toString()
    }

    private fun startGuardService() {
        try {
            val intent = Intent(this, FcmMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            LogStore.add(applicationContext, "Lỗi khởi động Service: ${e.message}")
        }
    }
}
