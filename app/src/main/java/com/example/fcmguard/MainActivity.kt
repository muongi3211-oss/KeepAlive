package com.example.fcmguard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kiểm tra và xin quyền thay đổi cài đặt hệ thống
        requestWriteSettingsPermission()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val tvStatus = TextView(this).apply {
            text = "FCM Guard - Đang hoạt động"
            textSize = 18f
            setPadding(0, 0, 0, 40)
        }

        val btnStart = Button(this).apply {
            text = "Kích hoạt bảo vệ ngay"
            setOnClickListener {
                try {
                    val serviceIntent = Intent(this@MainActivity, FcmMonitorService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    Toast.makeText(this@MainActivity, "Đã khởi động dịch vụ!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        val btnUpdate = Button(this).apply {
            text = "Kiểm tra bản cập nhật"
            setOnClickListener {
                val updateUrl = "https://github.com/muongi3211-oss/KeepAlive/releases/latest"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                startActivity(intent)
            }
        }

        layout.addView(tvStatus)
        layout.addView(btnStart)
        layout.addView(btnUpdate)

        setContentView(layout)
    }

    // Đặt hàm kiểm tra quyền bên ngoài onCreate (nhưng vẫn nằm trong class MainActivity)
    private fun requestWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "✅ Đã có quyền Thay đổi cài đặt hệ thống.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
