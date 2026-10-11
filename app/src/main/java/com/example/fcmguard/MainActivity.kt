package com.example.fcmguard

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var logTextView: TextView
    private var downloadId: Long = -1L
    private lateinit var downloadReceiver: BroadcastReceiver

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

        // Nút bấm cập nhật: Tải ngầm và tự động bật giao diện cài đặt ngay khi xong
        val updateButton = Button(this).apply {
            text = "Kiểm tra & Cập nhật ngay"
            setOnClickListener {
                val apkUrl = "https://github.com/muongi311-oss/KeepAlive/releases/download/latest/app-debug.apk"
                
                Toast.makeText(this@MainActivity, "Đang tải bản cập nhật ngầm...", Toast.LENGTH_SHORT).show()
                
                val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                    setTitle("FCM Guard Update")
                    setDescription("Đang tải phiên bản mới...")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "fcm_guard_update.apk")
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }

                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadId = dm.enqueue(request)
            }
        }
        layout.addView(updateButton)

        setContentView(layout)

        LogStore.add(applicationContext, "System: Mở ứng dụng FCM Guard thành công.")
        startGuardService()

        // Lắng nghe sự kiện tải xong để tự động mở màn hình cài đặt
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val uri = dm.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        try {
                            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(installIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Lỗi mở trình cài đặt: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Không tìm thấy file APK đã tải.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {}
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
