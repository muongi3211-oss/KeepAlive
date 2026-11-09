package com.kinh.gmsprotect

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

class CompatibilityActivity : Activity() {
    private var mainContent: LinearLayout? = null
    private var mainScroll: ScrollView? = null

    private fun dp(px: Int): Int = UiKit.dp(this, px)
    private fun full(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val frame = FrameLayout(this).apply { setBackgroundColor(UiKit.BG) }
        mainScroll = ScrollView(this).apply { isFillViewport = true }
        mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        UiKit.applyBottomSystemInset(mainContent!!)
        mainScroll?.addView(mainContent, FrameLayout.LayoutParams(-1, -2))
        frame.addView(mainScroll, FrameLayout.LayoutParams(-1, -1))

        SettingsMotion.collapsingBar(this, frame, mainScroll!!, getString(R.string.settings13_self_check)) {
            finish()
        }

        render()
        setContentView(frame)
    }

    private fun render() {
        val container = this.mainContent ?: return
        container.removeAllViews()

        SettingsMotion.largeTitle(this, container, getString(R.string.settings13_self_check))

        container.addView(SettingsListUi.heading(this, getString(R.string.settings13_google_section)), full())
        val group = SettingsListUi.group(this)

        val gmsOk = MilletNoRestrict.containsGms(this)
        val playOk = MilletNoRestrict.containsPlayStore(this)
        val writeOk = MilletNoRestrict.canWrite(this)

        SettingsListUi.row(
            this, group, R.drawable.sg_ic_shield,
            getString(R.string.dashboard_gms),
            if (gmsOk) getString(R.string.ui_status_verified) else getString(R.string.ui_status_missing),
            null,
            if (gmsOk) SettingsListUi.GOOD else SettingsListUi.BAD,
            null
        )

        SettingsListUi.row(
            this, group, R.drawable.sg_ic_shield,
            getString(R.string.dashboard_play_store),
            if (playOk) getString(R.string.ui_status_verified) else getString(R.string.ui_status_missing),
            null,
            if (playOk) SettingsListUi.GOOD else SettingsListUi.BAD,
            null
        )

        SettingsListUi.row(
            this, group, R.drawable.sg_ic_settings,
            getString(R.string.system_access_button),
            if (writeOk) getString(R.string.ui_status_verified) else getString(R.string.ui_status_missing),
            null,
            if (writeOk) SettingsListUi.GOOD else SettingsListUi.BAD,
            null
        )

        container.addView(group, full())
    }
}
