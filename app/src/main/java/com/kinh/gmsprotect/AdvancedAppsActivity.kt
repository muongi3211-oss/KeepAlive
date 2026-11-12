package com.kinh.gmsprotect

import android.widget.FrameLayout
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class AdvancedAppsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderUi()
    }

    private fun renderUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.BG)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val title = TextView(this).apply {
            text = getString(R.string.ui_section_apps)
            textSize = 22f
            setTextColor(UiKit.TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val addBtn = UiKit.primaryButton(this, getString(R.string.system_access_apply_now)) {
            showAddAppDialog()
        }
        root.addView(addBtn, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val entries = AdvancedAppStore.list(this)
        if (entries.isEmpty()) {
            val emptyTxt = TextView(this).apply {
                text = "Danh sách trống"
                textSize = 14f
                setTextColor(UiKit.MUTED)
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(32), 0, 0)
            }
            container.addView(emptyTxt, LinearLayout.LayoutParams(-1, -2))
        } else {
            for (entry in entries) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = UiKit.rounded(UiKit.SURFACE, 12, this@AdvancedAppsActivity)
                }
                val pkgTxt = TextView(this).apply {
                    text = entry.pkg
                    textSize = 14f
                    setTextColor(UiKit.TEXT)
                }
                row.addView(pkgTxt, LinearLayout.LayoutParams(0, -2, 1f))

                val delBtn = UiKit.secondaryButton(this, "Xóa") {
                    AdvancedAppStore.remove(this@AdvancedAppsActivity, entry.pkg)
                    renderUi()
                }
                row.addView(delBtn, LinearLayout.LayoutParams(-2, -2))

                container.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }

        scroll.addView(container, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun showAddAppDialog() {
        val input = EditText(this).apply {
            hint = "Nhập tên gói (ví dụ: com.example.app)"
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setTextColor(UiKit.TEXT)
            setHintTextColor(UiKit.MUTED)
        }

        AlertDialog.Builder(this)
            .setTitle("Thêm ứng dụng thủ công")
            .setView(input)
            .setPositiveButton("Thêm") { _, _ ->
                val pkg = input.text.toString().trim()
                if (AdvancedAppStore.valid(this, pkg)) {
                    val success = AdvancedAppStore.save(this, AdvancedAppStore.Entry(pkg, true))
                    if (success) {
                        Toast.makeText(this, "Đã thêm thành công", Toast.LENGTH_SHORT).show()
                        renderUi()
                    } else {
                        Toast.makeText(this, "Không thể thêm ứng dụng này", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Tên gói không hợp lệ", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun dp(value: Int): Int = UiKit.dp(this, value)
}
