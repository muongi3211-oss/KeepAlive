package com.kinh.gmsprotect

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CompatibilityActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ẩn ActionBar mặc định nếu có
        supportActionBar?.hide()

        // Tạo giao diện kiểm tra tương thích đơn giản, an toàn
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Kiểm tra tương thích HyperOS"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }

        val statusText = TextView(this).apply {
            val gms = if (MilletNoRestrict.containsGms(this@CompatibilityActivity)) "✓ Đã áp dụng" else "✗ Chưa áp dụng"
            val play = if (MilletNoRestrict.containsPlayStore(this@CompatibilityActivity)) "✓ Đã áp dụng" else "✗ Chưa áp dụng"
            val self = if (MilletNoRestrict.containsSelf(this@CompatibilityActivity)) "✓ Đã áp dụng" else "✗ Chưa áp dụng"
            
            text = "Trạng thái danh sách trắng Millet:\n\n• Google Services: $gms\n• CH Play: $play\n• GMS Protect: $self"
            textSize = 14f
            setLineSpacing(0f, 1.3f)
        }

        val btnBack = Button(this).apply {
            text = "Quay lại"
            setOnClickListener { finish() }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(btnBack)

        setContentView(layout)
    }
}
