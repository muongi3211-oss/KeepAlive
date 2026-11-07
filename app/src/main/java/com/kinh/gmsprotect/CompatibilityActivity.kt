package com.kinh.gmsprotect

import android.view.ViewGroup.LayoutParams
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

class CompatibilityActivity : Activity() {
    private enum class RowState { OK, PENDING, ERROR, INFO }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) UiKit.BG else UiKit.PRIMARY_DARK
        window.navigationBarColor = UiKit.SURFACE
        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = flags
        render()
    }

    override fun onResume() {
        super.onResume()
        GameWatchEvidence.noteUi(this, true)
        if (window.decorView != null) render()
    }

    override fun onPause() {
        GameWatchEvidence.noteUi(this, false)
        super.onPause()
    }

    private fun render() {
        val frame = FrameLayout(this).apply { setBackgroundColor(UiKit.BG) }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        UiKit.applyBottomSystemInset(page)
        scroll.addView(page, ScrollView.LayoutParams(-1, -2))
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        SettingsMotion.collapsingBar(this, frame, scroll, getString(R.string.compat_title)) { finish() }
        setContentView(frame)
        SettingsMotion.largeTitle(this, page, getString(R.string.compat_title))

        val powerKeeper = packageExists("com.miui.powerkeeper")
        val milletReadable = MilletNoRestrict.canRead(this)
        val canWrite = MilletNoRestrict.canWrite(this)
        val gmsOk = MilletNoRestrict.containsGms(this)
        val playStoreOk = MilletNoRestrict.containsPlayStore(this)
        val protectorOk = MilletNoRestrict.containsPackage(this, packageName)
        val hyperOsProtectionSent = CompatibilityVerification.hyperOsProtectionRequestSent(this)
        val watcherSupported = MilletWatchScheduler.isSupported()
        val watcherScheduled = MilletWatchScheduler.isScheduled(this)
        val watcherVerified = CompatibilityVerification.watcherVerified(this)
        val repairVerified = CompatibilityVerification.autoRepairVerified(this)
        val staleAfterUpdate = CompatibilityVerification.needsReverificationAfterSystemUpdate(this)

        val coreReady = powerKeeper && milletReadable && canWrite && gmsOk && playStoreOk &&
                protectorOk && hyperOsProtectionSent && watcherSupported && watcherScheduled
        val full = coreReady && watcherVerified && repairVerified && !staleAfterUpdate

        val overall: String
        val overallState: RowState
        if (!powerKeeper || !milletReadable) {
            overall = getString(R.string.compat_result_unsupported)
            overallState = RowState.ERROR
        } else if (full) {
            overall = getString(R.string.compat_result_full)
            overallState = RowState.OK
        } else if (coreReady) {
            overall = if (staleAfterUpdate) getString(R.string.compat_result_reverify) else getString(R.string.compat_result_pending)
            overallState = RowState.PENDING
        } else {
            overall = getString(R.string.compat_result_partial)
            overallState = RowState.PENDING
        }

        page.addView(SettingsListUi.heading(this, getString(R.string.compat_summary_section)), full())
        val summary = SettingsListUi.group(this).apply { setPadding(dp(16), dp(15), dp(16), dp(15)) }
        val headline = TextView(this).apply {
            text = overall
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (overallState == RowState.OK) UiKit.SUCCESS else if (overallState == RowState.ERROR) UiKit.ERROR else UiKit.TEXT)
        }
        val overviewLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        overviewLine.addView(makeStatusIcon(overallState), statusIconParams(12))
        overviewLine.addView(headline, LinearLayout.LayoutParams(0, -2, 1f))
        summary.addView(overviewLine, bottom(dp(7)))

        val device = UiKit.muted(this, "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}", 13f)
        summary.addView(device, bottom(dp(9)))

        val explanation = UiKit.muted(this, getString(R.string.compat_summary_desc), 13f).apply {
            setLineSpacing(dp(2).toFloat(), 1.03f)
        }
        summary.addView(explanation, full())
        page.addView(summary, full())

        page.addView(SettingsListUi.heading(this, getString(R.string.compat_hyperos_section)), full())
        val hyper = SettingsListUi.group(this)
        addRow(hyper, R.string.compat_powerkeeper, if (powerKeeper) R.string.compat_detected else R.string.compat_not_detected, if (powerKeeper) RowState.OK else RowState.ERROR)
        addRow(hyper, R.string.compat_millet_access, if (!milletReadable) R.string.compat_not_supported else if (canWrite) R.string.compat_read_write_ok else R.string.compat_permission_needed, if (!milletReadable) RowState.ERROR else if (canWrite) RowState.OK else RowState.ERROR)
        addRow(hyper, R.string.compat_gms, if (gmsOk) R.string.compat_applied else R.string.compat_not_applied, if (gmsOk) RowState.OK else RowState.ERROR)
        addRow(hyper, R.string.compat_play_store, if (playStoreOk) R.string.compat_applied else R.string.compat_not_applied, if (playStoreOk) RowState.OK else RowState.ERROR)
        addRow(hyper, R.string.compat_self_no_restrict, if (protectorOk) R.string.compat_verified else R.string.compat_self_needed, if (protectorOk) RowState.OK else RowState.ERROR)
        val androidBatteryExempt = BatterySetupState.isIgnoringBatteryOptimizations(this)
        addRow(hyper, R.string.compat_android_battery_exemption, if (androidBatteryExempt) R.string.compat_verified else R.string.compat_not_applied, if (androidBatteryExempt) RowState.OK else RowState.ERROR)
        addRow(hyper, R.string.compat_hyperos_protection, if (!powerKeeper) R.string.compat_not_supported else if (hyperOsProtectionSent) R.string.compat_request_sent else R.string.compat_not_checked, if (!powerKeeper) RowState.ERROR else if (hyperOsProtectionSent) RowState.INFO else RowState.PENDING)
        page.addView(hyper, full())
        addNote(page, R.string.compat_hyperos_protection_note)

        page.addView(SettingsListUi.heading(this, getString(R.string.compat_recovery_section)), full())
        val recovery = SettingsListUi.group(this)
        addRow(recovery, R.string.compat_watcher_registration, if (!watcherSupported) R.string.compat_not_supported else if (watcherScheduled) R.string.compat_registered else R.string.compat_not_registered, if (!watcherSupported) RowState.ERROR else if (watcherScheduled) RowState.OK else RowState.ERROR)
        addRow(recovery, R.string.compat_system_wake, if (staleAfterUpdate) R.string.compat_need_reverify else if (watcherVerified) R.string.compat_verified else R.string.compat_not_verified_yet, if (staleAfterUpdate || !watcherVerified) RowState.PENDING else RowState.OK)
        addRow(recovery, R.string.compat_auto_repair, if (staleAfterUpdate) R.string.compat_need_reverify else if (repairVerified) R.string.compat_verified else R.string.compat_not_verified_yet, if (staleAfterUpdate || !repairVerified) RowState.PENDING else RowState.OK)
        val last = CompatibilityVerification.lastVerifiedTime(this)
        val lastText = if (last > 0L) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last)) else getString(R.string.compat_never)
        addTextRow(recovery, getString(R.string.compat_last_verified), lastText)
        page.addView(recovery, full())

        page.addView(SettingsListUi.heading(this, getString(R.string.compat_game_section)), full())
        val game = SettingsListUi.group(this)
        val gwEnabled = GameWatchCore.isEnabled(this)
        val gwPlatform = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        val gwWrite = GameWatchCore.canWrite(this)
        var gwBoth = false
        var gwReadOk = false
        try {
            val current = GameWatchCore.raw(this)
            gwReadOk = true
            gwBoth = GameWatchCore.contains(current, GameWatchCore.GMS) && GameWatchCore.contains(current, packageName)
        } catch (ignored: Throwable) {}
        val gwPending = gwEnabled && GameWatchScheduler.isPending(this)
        addRow(game, R.string.compat_game_enabled, if (gwEnabled) R.string.compat_game_enabled_yes else R.string.compat_game_enabled_no, if (gwEnabled) RowState.OK else RowState.ERROR)
        addRow(game, R.string.compat_game_platform, if (gwPlatform) R.string.compat_verified else R.string.compat_not_supported, if (gwPlatform) RowState.OK else RowState.ERROR)
        addRow(game, R.string.compat_game_settings, if (gwWrite) R.string.compat_verified else R.string.compat_permission_needed, if (gwWrite) RowState.OK else RowState.ERROR)
        addRow(game, R.string.compat_game_targets, if (!gwReadOk) R.string.compat_not_verified_yet else if (gwBoth) R.string.compat_verified else R.string.compat_not_applied, if (!gwReadOk) RowState.PENDING else if (gwBoth) RowState.OK else RowState.ERROR)
        addRow(game, R.string.compat_game_job, if (gwPending) R.string.compat_registered else R.string.compat_not_registered, if (gwPending) RowState.OK else RowState.ERROR)
        addSubheading(game, getString(R.string.compat_game_passive_title))
        addRow(game, R.string.compat_game_real_uri, if (GameWatchEvidence.uriVerified(this)) R.string.compat_verified else R.string.compat_not_verified_yet, if (GameWatchEvidence.uriVerified(this)) RowState.OK else RowState.PENDING)
        addRow(game, R.string.compat_game_background, if (GameWatchEvidence.backgroundVerified(this)) R.string.compat_verified else R.string.compat_not_verified_yet, if (GameWatchEvidence.backgroundVerified(this)) RowState.OK else RowState.PENDING)
        addRow(game, R.string.compat_game_process, if (GameWatchEvidence.newPidVerified(this)) R.string.compat_verified else R.string.compat_not_verified_yet, if (GameWatchEvidence.newPidVerified(this)) RowState.OK else RowState.PENDING)
        addRow(game, R.string.compat_game_repair, if (GameWatchEvidence.repairVerified(this)) R.string.compat_verified else R.string.compat_not_verified_yet, if (GameWatchEvidence.repairVerified(this)) RowState.OK else RowState.PENDING)
        addRow(game, R.string.compat_game_boot, if (GameWatchEvidence.bootVerified(this)) R.string.compat_verified else R.string.compat_not_verified_yet, if (GameWatchEvidence.bootVerified(this)) RowState.OK else RowState.PENDING)
        val whenText = if (GameWatchEvidence.lastUri(this) == 0L) getString(R.string.compat_never) else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(GameWatchEvidence.lastUri(this)))
        addTextRow(game, getString(R.string.compat_game_last_uri), whenText)
        page.addView(game, full())
        addNote(page, R.string.compat_game_note)

        page.addView(SettingsListUi.heading(this, getString(R.string.compat_actions_section)), full())
        val actions = SettingsListUi.group(this)
        SettingsListUi.row(this, actions, R.drawable.sg_ic_check, getString(R.string.compat_run_check), null, null, SettingsListUi.NEUTRAL) { runCheck() }
        SettingsListUi.row(this, actions, R.drawable.sg_ic_arrow_back, getString(R.string.compat_back), null, null, SettingsListUi.NEUTRAL) { finish() }
        page.addView(actions, full())
        addNote(page, R.string.compat_passive_note)
    }

    private fun runCheck() {
        MilletNoRestrict.ensureGms(this)
        val protection = GmsProtector.sendHyperOsProtectionRequest(this)
        MilletWatchScheduler.schedule(this)

        val message = when (protection) {
            GmsProtector.HyperOsProtectionResult.SENT -> R.string.compat_check_done
            GmsProtector.HyperOsProtectionResult.GMS_MISSING -> R.string.compat_check_gms_missing
            else -> R.string.compat_check_hyperos_failed
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun addRow(group: LinearLayout, titleRes: Int, statusRes: Int, state: RowState) {
        val title = getString(titleRes)
        val result = getString(statusRes)
        val kind = when (state) {
            RowState.OK -> SettingsListUi.GOOD
            RowState.ERROR -> SettingsListUi.BAD
            RowState.INFO -> SettingsListUi.INFO
            else -> SettingsListUi.NEUTRAL
        }
        val row = SettingsListUi.row(this, group, title, null, result, kind, null)
        insertStatusIcon(row, state)
        row.contentDescription = "$title. $result"
    }

    private fun addTextRow(group: LinearLayout, title: String, value: String) {
        val row = SettingsListUi.row(this, group, title, null, value, SettingsListUi.NEUTRAL, null)
        insertStatusIcon(row, RowState.INFO)
        row.contentDescription = "$title. $value"
    }

    private fun insertStatusIcon(row: LinearLayout, state: RowState) {
        row.removeViewAt(0)
        row.addView(makeStatusIcon(state), 0, statusIconParams(27))
    }

    private fun statusIconParams(rightMargin: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(dp(24), dp(24))
        lp.leftMargin = dp(4)
        lp.rightMargin = dp(rightMargin)
        return lp
    }

    private fun makeStatusIcon(state: RowState): ImageView {
        val icon = ImageView(this).apply {
            setImageResource(
                when (state) {
                    RowState.OK -> R.drawable.sg_ic_verified_check
                    RowState.ERROR -> R.drawable.sg_ic_status_error
                    RowState.INFO -> R.drawable.sg_ic_status_info
                    else -> R.drawable.sg_ic_status_pending
                }
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val label = when (state) {
            RowState.OK -> R.string.settings16_status_ok
            RowState.ERROR -> R.string.settings16_status_error
            RowState.INFO -> R.string.settings16_status_info
            else -> R.string.settings16_status_pending
        }
        icon.contentDescription = getString(label)
        return icon
    }

    private fun addSubheading(group: LinearLayout, text: String) {
        SettingsListUi.divider(this, group)
        val label = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(UiKit.MUTED)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(15), dp(16), dp(3))
        }
        group.addView(label, full())
    }

    private fun addNote(page: LinearLayout, textRes: Int) {
        val note = UiKit.muted(this, getString(textRes), 13f).apply {
            setPadding(dp(12), dp(10), dp(12), dp(3))
            setLineSpacing(dp(2).toFloat(), 1.02f)
        }
        page.addView(note, full())
    }

    private fun packageExists(pkg: String): Boolean {
        return try {
            packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (t: Throwable) {
            false
        }
    }

    private fun dp(value: Int): Int = UiKit.dp(this, value)
    private fun full(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
    private fun bottom(margin: Int): LinearLayout.LayoutParams {
        val lp = full()
        lp.bottomMargin = margin
        return lp
    }
}
