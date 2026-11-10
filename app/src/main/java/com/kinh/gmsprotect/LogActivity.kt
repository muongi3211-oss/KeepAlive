package com.kinh.gmsprotect

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class LogActivity : Activity() {
    private var mainContent: LinearLayout? = null
    private var mainScroll: ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.statusBarColor = UiKit.BG
            window.navigationBarColor = UiKit.SURFACE

            val frame = FrameLayout(this).apply { setBackgroundColor(UiKit.BG) }
            mainScroll = ScrollView(this).apply { isFillViewport = true }
            mainContent = LinearLayout(this).apply { 
                orientation = LinearLayout.VERTICAL
                setPadding(UiKit.dp(this@LogActivity, 16), UiKit.dp(this@LogActivity, 16), UiKit.dp(this@LogActivity, 16), UiKit.dp(this@LogActivity, 24))
            }
            UiKit.applyBottomSystemInset(mainContent!!)
            mainScroll?.addView(mainContent, FrameLayout.LayoutParams(-1, -2))
            frame.addView(mainScroll, FrameLayout.LayoutParams(-1, -1))

            SettingsMotion.collapsingBar(this, frame, mainScroll!!, "Nhật ký hoạt động") { finish() }

            renderLogs()
            setContentView(frame)
        } catch (e: Throwable) {
            try { finish() } catch (_: Throwable) {}
        }
    }

    private fun renderLogs() {
        val content = mainContent ?: return
        try {
            content.removeAllViews()
            SettingsMotion.largeTitle(this, content, "Nhật ký hoạt động")

            val group = SettingsListUi.group(this)
            val logEntries = listOf(
                "[Hệ thống] Khởi động tiến trình giám sát bảo vệ GMS và MiPush.",
                "[MILLET] Đã kiểm tra danh sách không hạn chế nền.",
                "[JobScheduler] Lên lịch tự động phục hồi quy tắc bảo vệ."
            )

            for (entry in logEntries) {
                val tv = TextView(this).apply {
                    text = entry
                    textSize = 13.5f
                    setTextColor(UiKit.TEXT)
                    setPadding(UiKit.dp(this@LogActivity, 16), UiKit.dp(this@LogActivity, 12), UiKit.dp(this@LogActivity, 16), UiKit.dp(this@LogActivity, 12))
                }
                group.addView(tv)
            }
            content.addView(group)
        } catch (e: Throwable) {
            android.util.Log.e("LogActivity", "Render error", e)
        }
    }
}
