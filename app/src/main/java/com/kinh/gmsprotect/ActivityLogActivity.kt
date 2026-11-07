package com.kinh.gmsprotect

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class ActivityLogActivity : Activity() {
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.BG)
        }
        UiKit.applyTopSystemInset(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = getString(R.string.gamewatch_logs)
            textSize = 20f
            setTextColor(UiKit.TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

        val copyBtn = TextView(this).apply {
            text = getString(R.string.activity_log_copy_text)
            textSize = 13f
            setTextColor(UiKit.PRIMARY)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            isClickable = true
            isFocusable = true
            background = UiKit.rounded(UiKit.PRIMARY_SOFT, 8, this@ActivityLogActivity)
            setOnClickListener { copyLogsToClipboard() }
        }
        header.addView(copyBtn)
        root.addView(header, matchWidth())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(24))
        }
        scroll.addView(container, ScrollView.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        loadLogs()
    }

    private fun loadLogs() {
        container.removeAllViews()
        // Sử dụng entries thay vì getAll cho GameWatchJournal
        val logs = GameWatchJournal.entries(this)
        if (logs.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.activity_log_empty)
                textSize = 13.5f
                setTextColor(UiKit.MUTED)
                setPadding(0, dp(24), 0, 0)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            container.addView(empty, matchWidth())
            return
        }

        for (log in logs) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = UiKit.rounded(UiKit.SURFACE, 12, this@ActivityLogActivity)
            }
            val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }

            val tagText = TextView(this).apply {
                text = "[${log.kind}] ${log.code}" // Sử dụng code và kind thay cho tag
                textSize = 11.5f
                setTextColor(UiKit.PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
            }
            item.addView(tagText, matchWidth())

            if (log.detail.isNotEmpty()) {
                val msgText = TextView(this).apply {
                    text = log.detail // Sử dụng detail thay cho message
                    textSize = 13f
                    setTextColor(UiKit.TEXT)
                    setPadding(0, dp(4), 0, 0)
                }
                item.addView(msgText, matchWidth())
            }

            container.addView(item, lp)
        }
    }

    private fun copyLogsToClipboard() {
        val logs = GameWatchJournal.entries(this)
        val sb = StringBuilder()
        for (log in logs) {
            sb.append("[${log.kind}] ${log.code}: ${log.detail}\n")
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("GMS Protect Logs", sb.toString()))
        Toast.makeText(this, getString(R.string.activity_log_copied), Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = UiKit.dp(this, value)
    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
}
