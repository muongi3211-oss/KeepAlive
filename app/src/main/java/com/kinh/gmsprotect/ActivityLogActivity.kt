package com.kinh.gmsprotect

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityLogActivity : Activity() {
    private var entries: LinearLayout? = null
    private var filters: LinearLayout? = null
    private var scroll: ScrollView? = null
    private var selectedFilter = 0

    private fun dp(value: Int): Int = UiKit.dp(this, value)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            UiKit.BG
        } else {
            UiKit.PRIMARY_DARK
        }
        window.navigationBarColor = UiKit.SURFACE

        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        GameWatchEvidence.noteUi(this, true)
        if (entries != null) refresh(true)
    }

    override fun onPause() {
        GameWatchEvidence.noteUi(this, false)
        super.onPause()
    }

    private fun buildScreen() {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(UiKit.BG)
        }

        scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        UiKit.applyBottomSystemInset(page)

        scroll?.addView(page, FrameLayout.LayoutParams(-1, -2))
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        SettingsMotion.collapsingBar(
            this, frame, scroll!!,
            getString(R.string.gamewatch_logs)
        ) { finish() }
        setContentView(frame)

        SettingsMotion.largeTitle(this, page, getString(R.string.gamewatch_logs))
        val intro = UiKit.muted(this, getString(R.string.activity_log_intro_v2), 13.5f).apply {
            setPadding(dp(20), 0, dp(20), dp(14))
        }
        page.addView(intro, LinearLayout.LayoutParams(-1, -2))

        filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), dp(1), dp(12), dp(8))
        }
        page.addView(filters, LinearLayout.LayoutParams(-1, -2))
        updateFilters()

        entries = SettingsListUi.group(this)
        page.addView(entries, LinearLayout.LayoutParams(-1, -2))

        page.addView(
            SettingsListUi.heading(this, getString(R.string.activity_log_actions)),
            LinearLayout.LayoutParams(-1, -2)
        )

        val actions = SettingsListUi.group(this)
        SettingsListUi.row(
            this, actions, R.drawable.sg_ic_history,
            getString(R.string.activity_log_copy_technical), null, null,
            SettingsListUi.NEUTRAL
        ) { copyTechnical() }

        SettingsListUi.row(
            this, actions, R.drawable.sg_ic_check,
            getString(R.string.activity_log_refresh), null, null,
            SettingsListUi.NEUTRAL
        ) { refresh(false) }

        page.addView(actions, LinearLayout.LayoutParams(-1, -2))

        refresh(false)
        page.post { SettingsMotion.enter(entries) }
    }

    private fun pill(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            setColor(if (selected) 0xFFE4EAEE.toInt() else UiKit.BG)
            cornerRadius = dp(18).toFloat()
            if (!selected) {
                setStroke(dp(1), UiKit.BORDER)
            }
        }
    }

    private fun updateFilters() {
        filters?.removeAllViews()
        val labels = intArrayOf(
            R.string.activity_log_all,
            R.string.activity_log_important,
            R.string.activity_log_errors
        )

        for (i in labels.indices) {
            val filter = i
            val chip = TextView(this).apply {
                setText(labels[i])
                textSize = 13f
                setTypeface(null, if (i == selectedFilter) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (i == selectedFilter) UiKit.TEXT else UiKit.MUTED)
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(7), dp(14), dp(7))
                background = pill(i == selectedFilter)
                setOnClickListener {
                    if (selectedFilter == filter) return@setOnClickListener
                    selectedFilter = filter
                    updateFilters()
                    refresh(false)
                }
            }

            val p = LinearLayout.LayoutParams(-2, dp(35)).apply {
                rightMargin = dp(8)
            }
            filters?.addView(chip, p)
        }
    }

    private fun visible(e: GameWatchJournal.Event): Boolean {
        if (selectedFilter == 0) return true
        if (selectedFilter == 2) return "error" == e.kind || "warning" == e.kind
        return "info" != e.kind
    }

    private fun refresh(preserveScroll: Boolean) {
        val currentEntries = entries ?: return
        val offset = if (preserveScroll && scroll != null) scroll!!.scrollY else 0
        currentEntries.removeAllViews()

        val data = GameWatchJournal.entries(this)
        val dayFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        var lastDay: String? = null
        var shown = 0

        for (e in data) {
            if (!visible(e)) continue
            val day = dayFmt.format(Date(e.lastMs))
            if (day != lastDay) {
                lastDay = day
                currentEntries.addView(
                    SettingsListUi.heading(this, day),
                    LinearLayout.LayoutParams(-1, -2)
                )
            }
            appendEvent(e, timeFmt.format(Date(e.lastMs)))
            shown++
        }

        if (shown == 0) {
            val empty = UiKit.muted(this, getString(R.string.activity_log_empty), 14f).apply {
                setPadding(dp(70), dp(18), dp(20), dp(22))
            }
            currentEntries.addView(empty, LinearLayout.LayoutParams(-1, -2))
        }

        scroll?.post { scroll?.scrollTo(0, offset) }
    }

    private fun appendEvent(e: GameWatchJournal.Event, time: String) {
        val color = when (e.kind) {
            "success" -> UiKit.SUCCESS
            "error", "warning" -> UiKit.ERROR
            "info" -> UiKit.PRIMARY
            else -> UiKit.MUTED
        }

        val symbol = when (e.kind) {
            "success" -> "✓"
            "error", "warning" -> "!"
            "change" -> "↻"
            else -> "i"
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(12), dp(12), dp(18), dp(12))
            background = SettingsMotion.ripple(this@ActivityLogActivity)
            isClickable = true
            isFocusable = true
        }

        val icon = TextView(this).apply {
            text = symbol
            setTextColor(color)
            textSize = 21f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }
        val ip = LinearLayout.LayoutParams(dp(36), dp(32)).apply {
            leftMargin = dp(5)
            rightMargin = dp(17)
        }
        row.addView(icon, ip)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val headline = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = titleFor(e.code)
            textSize = 15.5f
            setTextColor(UiKit.TEXT)
            setTypeface(null, Typeface.BOLD)
        }
        headline.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

        val stamp = TextView(this).apply {
            text = time
            textSize = 12f
            setTextColor(UiKit.MUTED)
        }
        val tp = LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = dp(8)
        }
        headline.addView(stamp, tp)
        content.addView(headline)

        val sub = TextView(this).apply {
            text = subtitleFor(e)
            textSize = 13f
            setTextColor(UiKit.MUTED)
            setPadding(0, dp(4), 0, 0)
        }
        content.addView(sub)

        if (e.count > 1) {
            val repeated = TextView(this).apply {
                text = getString(R.string.activity_log_repeated, e.count)
                textSize = 12f
                setTextColor(UiKit.MUTED)
                setPadding(0, dp(3), 0, 0)
            }
            content.addView(repeated)
        }

        val technical = TextView(this).apply {
            text = getString(
                R.string.activity_log_technical_item,
                e.code, e.kind, e.detail, e.count
            )
            textSize = 12f
            setTextColor(UiKit.MUTED)
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, 0)
            visibility = View.GONE
        }
        content.addView(technical)

        row.setOnClickListener {
            technical.visibility = if (technical.visibility == View.GONE) View.VISIBLE else View.GONE
        }
        row.addView(content, LinearLayout.LayoutParams(0, -2, 1f))

        entries?.addView(row, LinearLayout.LayoutParams(-1, -2))
        val divider = SettingsListUi.separator(this)
        val d = LinearLayout.LayoutParams(-1, dp(1)).apply {
            leftMargin = dp(70)
        }
        entries?.addView(divider, d)
    }

    private fun subtitleFor(e: GameWatchJournal.Event): String {
        if (e.code.startsWith("app_added") || e.code.startsWith("app_enabled")
            || e.code.startsWith("app_disabled") || e.code.startsWith("app_removed")
        ) {
            return e.detail
        }
        return when (e.code) {
            "millet_repaired" -> getString(R.string.activity_log_desc_millet_repaired)
            "millet_error" -> getString(R.string.activity_log_desc_millet_error)
            "mipush_requested" -> getString(R.string.activity_log_desc_mipush_requested)
            "mipush_error" -> getString(R.string.activity_log_desc_mipush_error)
            "game_repaired" -> getString(R.string.activity_log_desc_game_repaired)
            "game_changed" -> getString(R.string.activity_log_desc_game_changed)
            "game_deferred" -> getString(R.string.activity_log_desc_game_deferred)
            "game_error" -> getString(R.string.activity_log_desc_game_error)
            "watch_registered" -> getString(R.string.activity_log_desc_watch_registered)
            "watch_verified" -> getString(R.string.activity_log_desc_watch_verified)
            "watch_error" -> getString(R.string.activity_log_desc_watch_error)
            "app_error" -> getString(R.string.activity_log_desc_app_error)
            else -> getString(R.string.activity_log_desc_other)
        }
    }

    private fun titleFor(code: String): String {
        return when (code) {
            "millet_repaired" -> getString(R.string.activity_log_title_millet_repaired)
            "millet_error" -> getString(R.string.activity_log_title_millet_error)
            "mipush_requested" -> getString(R.string.activity_log_title_mipush_requested)
            "mipush_error" -> getString(R.string.activity_log_title_mipush_error)
            "game_repaired" -> getString(R.string.activity_log_title_game_repaired)
            "game_changed" -> getString(R.string.activity_log_title_game_changed)
            "game_deferred" -> getString(R.string.activity_log_title_game_deferred)
            "game_error" -> getString(R.string.activity_log_title_game_error)
            "watch_registered" -> getString(R.string.activity_log_title_watch_registered)
            "watch_verified" -> getString(R.string.activity_log_title_watch_verified)
            "watch_error" -> getString(R.string.activity_log_title_watch_error)
            "app_added" -> getString(R.string.activity_log_title_app_added)
            "app_enabled" -> getString(R.string.activity_log_title_app_enabled)
            "app_disabled" -> getString(R.string.activity_log_title_app_disabled)
            "app_removed" -> getString(R.string.activity_log_title_app_removed)
            "app_error" -> getString(R.string.activity_log_title_app_error)
            else -> getString(R.string.activity_log_title_other)
        }
    }

    private fun copyTechnical() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                getString(R.string.gamewatch_logs),
                GameWatchJournal.technicalHistory(this)
            )
        )
        Toast.makeText(this, R.string.activity_log_copied, Toast.LENGTH_SHORT).show()
    }
}
