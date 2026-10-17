package com.kinh.gmsprotect

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CompatibilityActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
            setBackgroundColor(Color.parseColor("#F4F5F9"))
        }

        val title = TextView(this).apply {
            text = "Kiểm tra tương thích HyperOS"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 20)
            setTextColor(Color.parseColor("#212529"))
        }

        val gmsStatus = if (MilletNoRestrict.containsGms(this)) "✓ Đã áp dụng" else "✗ Chưa áp dụng"
        val playStatus = if (MilletNoRestrict.containsPlayStore(this)) "✓ Đã áp dụng" else "✗ Chưa áp dụng"
        val selfStatus = if (MilletNoRestrict.containsSelf(this)) "✓ Đã áp dụng" else "✗ Chưa áp dụng"

        val statusText = TextView(this).apply {
            text = "Trạng thái danh sách trắng Millet:\n\n• Google Play Services: $gmsStatus\n• CH Play: $playStatus\n• GMS Protect: $selfStatus"
            textSize = 14f
            setLineSpacing(0f, 1.3f)
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
                topMargin = 40
            }
            setOnClickListener { finish() }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(btnBack)

        setContentView(layout)
    }
}
