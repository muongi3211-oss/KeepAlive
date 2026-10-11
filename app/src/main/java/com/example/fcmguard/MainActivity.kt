package com.example.fcmguard

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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

        // Nút bấm kiểm tra cập nhật OTA tự động
        val updateButton = Button(this).apply {
            text = "Kiểm tra cập nhật mới"
            setOnClickListener {
                val apkUrl = "https://github.com/muongi311-oss/KeepAlive/releases/download/latest/app-debug.apk"
                
                Toast.makeText(this@MainActivity, "Đang tải bản cập nhật...", Toast.LENGTH_SHORT).show()
                
                val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                    setTitle("FCM Guard Update")
                    setDescription("Đang tải phiên bản mới...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "fcm_guard_update.apk")
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }

                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                
                Toast.makeText(this@MainActivity, "Đã tải về thư mục Download! Hãy bấm vào thông báo khi tải xong để cài đè.", Toast.LENGTH_LONG).show()
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
