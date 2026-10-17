package com.kinh.gmsprotect

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CompatibilityActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE)

            val rootScroll = ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#F4F5F9"))
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 60, 40, 40)
            }

            val title = TextView(this).apply {
                text = "Kiểm tra tương thích HyperOS"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 20)
                setTextColor(Color.parseColor("#212529"))
            }

            val gmsStatus = try { if (MilletNoRestrict.containsGms(this)) "✓ Đã áp dụng" else "✗ Chưa áp dụng" } catch (_: Throwable) { "✗ Không xác định" }
            val playStatus = try { if (MilletNoRestrict.containsPlayStore(this)) "✓ Đã áp dụng" else "✗ Chưa áp dụng" } catch (_: Throwable) { "✗ Không xác định" }
            val selfStatus = try { if (MilletNoRestrict.containsSelf(this)) "✓ Đã áp dụng" else "✗ Chưa áp dụng" } catch (_: Throwable) { "✗ Không xác định" }

            val statusText = TextView(this).apply {
                text = "Trạng thái danh sách trắng Millet:\n\n• Google Play Services: $gmsStatus\n• CH Play: $playStatus\n• GMS Protect: $selfStatus"
                textSize = 15f
                setLineSpacing(0f, 1.4f)
                setTextColor(Color.parseColor("#495057"))
            }

            val btnBack = Button(this).apply {
                text = "Quay lại"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#383B8E"))
                    cornerRadius = 20f
                }
                setPadding(0, 25, 0, 25)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = 50
                }
                setOnClickListener { finish() }
            }

            layout.addView(title)
            layout.addView(statusText)
            layout.addView(btnBack)
            rootScroll.addView(layout)

            setContentView(rootScroll)
        } catch (e: Throwable) {
            // Đề phòng mọi ngoại lệ phát sinh, tự động quay về màn hình trước an toàn
            finish()
        }
    }
}
