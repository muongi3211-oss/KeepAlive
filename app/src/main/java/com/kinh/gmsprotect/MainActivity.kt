package com.hanh.gmsprotect

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Thực thi bảo vệ ngay khi mở app
        val isProtected = GmsProtector.protect(this)
        MilletWatchJobService.schedule(this)

        // Xây dựng giao diện UI programmatically gọn nhẹ
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
            setBackgroundColor(0xFFF6F6FB.toInt())
        }

        val title = TextView(this).apply {
            text = "GMS Protect HyperOS"
            textSize = 24f
            setTextColor(0xFF3F3D9B.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val status = TextView(this).apply {
            text = if (isProtected) "Trạng thái: Đã bảo vệ thành công!" else "Trạng thái: Cần cấp quyền hệ thống"
            textSize = 14f
            setPadding(0, 20, 0, 40)
            setTextColor(if (isProtected) 0xFF28A96B.toInt() else 0xFFE45A62.toInt())
        }
        root.addView(status)

        val btnPermission = Button(this).apply {
            text = "1. Cấp quyền Write Settings"
            isAllCaps = false
            setOnClickListener {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:$packageName"))
                    startActivity(intent)
                } catch (e: Throwable) {
                    Toast.makeText(this@MainActivity, "Không mở được cài đặt", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(btnPermission)

        val btnRefresh = Button(this).apply {
            text = "2. Kiểm tra & Kích hoạt lại"
            isAllCaps = false
            setPadding(0, 20, 0, 0)
            setOnClickListener {
                val success = GmsProtector.protect(this@MainActivity)
                status.text = if (success) "Trạng thái: Đã bảo vệ thành công!" else "Trạng thái: Thất bại, hãy kiểm tra quyền!"
                status.setTextColor(if (success) 0xFF28A96B.toInt() else 0xFFE45A62.toInt())
                Toast.makeText(this@MainActivity, if (success) "Đã tối ưu hóa!" else "Vui lòng cấp quyền trước!", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnRefresh)

        setContentView(root)
    }
}
