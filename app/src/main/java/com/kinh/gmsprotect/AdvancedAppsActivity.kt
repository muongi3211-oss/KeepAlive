package com.kinh.gmsprotect

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.FrameLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.text.TextUtils

import java.util.ArrayList
import java.util.Collections
import java.util.List
import java.util.LinkedHashMap
import java.util.Locale
import java.text.Collator

/** Optional UI. Does not participate in the mandatory GMS setup flow. */
class AdvancedAppsActivity : Activity() {
    private var content: LinearLayout? = null
    private var mainScroll: ScrollView? = null
    private var collapsingHeader: SettingsMotion.CollapsingHeader? = null
    private var detailPkg: String? = null
    private var previousScrollY = 0
    private var screenBuilt = false
    private var lastWritePermission = false
    private var pendingInstalledAppsSettings = false
    private var infoExpanded = false
    private var gameRawSnapshot: String? = null
    private var gameSnapshotReadable = false
    private var milletRawSnapshot: String? = null
    private var milletSnapshotReadable = false

    companion object {
        private const val INSTALLED_APPS_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"
        private const val REQUEST_INSTALLED_APPS = 1152
    }

    private fun dp(px: Int): Int = UiKit.dp(this, px)
    private fun full(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
    private fun spaceBottom(dp: Int): LinearLayout.LayoutParams {
        val lp = full()
        lp.bottomMargin = dp(dp)
        return lp
    }
    private fun toast(res: Int) { Toast.makeText(this, res, Toast.LENGTH_SHORT).show() }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            UiKit.BG
        } else {
            UiKit.PRIMARY_DARK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        window.navigationBarColor = UiKit.SURFACE
        if (Build.VERSION.SDK_INT >= 26) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        lastWritePermission = GameWatchCore.canWrite(this)
        if (state != null) {
            detailPkg = state.getString("advanced_detail_pkg")
            pendingInstalledAppsSettings = state.getBoolean("advanced_pending_installed_apps", false)
        }
        buildShell()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("advanced_detail_pkg", detailPkg)
        outState.putBoolean("advanced_pending_installed_apps", pendingInstalledAppsSettings)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (detailPkg != null) {
            showList()
            return
        }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        val writeAllowed = GameWatchCore.canWrite(this)
        if (writeAllowed && !lastWritePermission) {
            AdvancedAppProtector.maintain(this, "WRITE_SETTINGS_GRANTED")
            if (GameWatchScheduler.shouldWatch(this)) {
                GameWatchScheduler.ensure(this, "ADV_ACCESS_GRANTED")
            }
        }
        lastWritePermission = writeAllowed
        // Resume Add app only when returning from a user-requested app settings visit.
        // Do not mix this with WRITE_SETTINGS: it is a different Xiaomi permission.
        val continueAdd = pendingInstalledAppsSettings && canReadInstalledApps()
        pendingInstalledAppsSettings = false
        if (screenBuilt) {
            // Returning from Autostart / Xiaomi Battery settings must immediately
            // update the status chips without resetting the user's scroll position.
            val scrollY = mainScroll?.scrollY ?: 0
            render()
            mainScroll?.post { mainScroll?.scrollTo(0, scrollY) }
            if (continueAdd) {
                mainScroll?.post {
                    if (!isFinishing() && detailPkg == null) {
                        selectInstalledApp()
                    }
                }
            }
        }
    }

    private fun buildShell() {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(UiKit.BG)
        }
        mainScroll = ScrollView(this).apply {
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        UiKit.applyBottomSystemInset(content!!)
        mainScroll?.addView(content, ScrollView.LayoutParams(-1, -2))
        frame.addView(mainScroll, FrameLayout.LayoutParams(-1, -1))
        collapsingHeader = SettingsMotion.collapsingBar(this, frame, mainScroll!!,
            getString(R.string.adv_title)
        ) {
            if (detailPkg != null) showList() else finish()
        }
        setContentView(frame)
        screenBuilt = true
    }

    private fun render() {
        val currentContent = content ?: return
        currentContent.removeAllViews()
        // Read Game Allowlist once per page refresh, never once per card.
        // A null string is an empty list if the provider read itself succeeded.
        gameRawSnapshot = null
        gameSnapshotReadable = false
        try {
            gameRawSnapshot = GameWatchCore.raw(this)
            gameSnapshotReadable = true
        } catch (ignored: Throwable) {
        }
        // Read MILLET once as well, and don't misreport permission/read failures
        // as an absent package. This check is read-only; no policy changes here.
        milletRawSnapshot = null
        milletSnapshotReadable = false
        try {
            milletRawSnapshot = Settings.System.getString(contentResolver, MilletNoRestrict.KEY)
            milletSnapshotReadable = true
        } catch (ignored: Throwable) {
        }
        val selected = AdvancedAppStore.list(this)
        if (detailPkg != null) {
            var current: AdvancedAppStore.Entry? = null
            for (e in selected) {
                if (e.pkg == detailPkg) {
                    current = e
                    break
                }
            }
            if (current != null) {
                collapsingHeader?.setTitle(getString(R.string.adv_detail_title))
                collapsingHeader?.setCompactOnly(true)
                // The fixed Settings toolbar is the only page title on detail screens.
                // Do not add another giant heading above the app identity row.
                currentContent.addView(View(this), LinearLayout.LayoutParams(-1, dp(66)))
                currentContent.addView(buildAppDetail(current), spaceBottom(12))
                return
            }
            detailPkg = null
        }
        collapsingHeader?.setTitle(getString(R.string.adv_title))
        collapsingHeader?.setCompactOnly(false)
        SettingsMotion.largeTitle(this, currentContent, getString(R.string.adv_title))

        // Built-in GMS protection is represented here for clarity, but is not
        // stored in AdvancedAppStore and cannot be edited/removed by the user.
        // Always visible even if there are no user-managed applications.
        currentContent.addView(
            SettingsListUi.heading(this, getString(R.string.settings16_special_heading)),
            full()
        )
        val specialGroup = SettingsListUi.group(this)
        specialGroup.addView(buildSpecialGmsRow(), full())
        currentContent.addView(specialGroup, spaceBottom(5))

        currentContent.addView(
            SettingsListUi.heading(this, getString(R.string.settings16_user_apps_heading)),
            full()
        )
        val actionGroup = SettingsListUi.group(this)
        SettingsListUi.row(
            this, actionGroup, R.drawable.sg_ic_apps, getString(R.string.adv_add),
            getString(R.string.settings_add_desc), null, SettingsListUi.NEUTRAL
        ) { onAddAppClicked() }
        currentContent.addView(actionGroup, spaceBottom(12))

        if (!GameWatchCore.canWrite(this)) {
            val warning = SettingsListUi.group(this)
            SettingsListUi.row(
                this, warning, R.drawable.sg_ic_settings, getString(R.string.adv_open_access),
                getString(R.string.adv_write_required),
                getString(R.string.ui_watch_attention), SettingsListUi.BAD
            ) { openWriteSettings() }
            currentContent.addView(warning, spaceBottom(12))
        }

        if (selected.isEmpty()) {
            val empty = UiKit.muted(this, getString(R.string.adv_empty), 14f).apply {
                setPadding(dp(12), dp(25), dp(12), dp(25))
            }
            currentContent.addView(empty, full())
        } else {
            currentContent.addView(
                SettingsListUi.separator(this),
                LinearLayout.LayoutParams(-1, dp(1))
            )
            val appGroup = SettingsListUi.group(this)
            for (e in selected) {
                appGroup.addView(buildAppSummary(e), full())
            }
            currentContent.addView(appGroup, spaceBottom(12))
        }
        // Collapsed by default: plain Settings rows, not legacy long paragraphs/cards.
        val infoToggle = UiKit.muted(
            this,
            getString(if (infoExpanded) R.string.ui_hide_help else R.string.ui_show_help), 13f
        ).apply {
            setTextColor(UiKit.PRIMARY)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            minimumHeight = dp(48)
            background = SettingsMotion.ripple(this@AdvancedAppsActivity)
            isClickable = true
            isFocusable = true
        }
        currentContent.addView(infoToggle, spaceBottom(4))

        val help = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        help.addView(SettingsListUi.heading(this, getString(R.string.adv_help_status_heading)), full())
        addHelpRow(
            help, R.drawable.sg_ic_verified_check,
            R.string.adv_help_pass_title, R.string.adv_help_pass_desc
        )
        addHelpRow(
            help, R.drawable.sg_ic_status_error,
            R.string.adv_help_attention_title, R.string.adv_help_attention_desc
        )
        addHelpRow(
            help, R.drawable.sg_ic_status_pending,
            R.string.adv_help_unknown_title, R.string.adv_help_unknown_desc
        )
        addHelpRow(
            help, R.drawable.sg_ic_status_off,
            R.string.adv_help_off_title, R.string.adv_help_off_desc
        )
        help.addView(SettingsListUi.heading(this, getString(R.string.adv_help_rule_heading)), full())
        addHelpRow(
            help, R.drawable.sg_ic_status_info,
            R.string.adv_help_five_title, R.string.adv_help_five_desc
        )
        addHelpRow(
            help, R.drawable.sg_ic_status_info,
            R.string.adv_help_mipush_title, R.string.adv_help_mipush_desc
        )
        addHelpRow(
            help, R.drawable.sg_ic_status_info,
            R.string.adv_help_battery_title, R.string.adv_help_battery_desc
        )
        addHelpRow(
            help, R.drawable.sg_ic_status_info,
            R.string.adv_help_watcher_title, R.string.adv_help_watcher_desc
        )
        val jobInfo = UiKit.muted(
            this, getString(
                R.string.adv_jobs_status,
                getString(if (GameWatchScheduler.isPending(this)) R.string.adv_job_yes else R.string.adv_job_no),
                getString(if (MilletWatchScheduler.isScheduled(this)) R.string.adv_job_yes else R.string.adv_job_no)
            ),
            12f
        ).apply {
            setPadding(dp(18), dp(12), dp(18), dp(18))
        }
        help.addView(jobInfo, full())
        help.visibility = if (infoExpanded) View.VISIBLE else View.GONE
        currentContent.addView(help, full())
        infoToggle.setOnClickListener {
            infoExpanded = !infoExpanded
            help.visibility = if (infoExpanded) View.VISIBLE else View.GONE
            infoToggle.setText(if (infoExpanded) R.string.ui_hide_help else R.string.ui_show_help)
        }
    }

    /** Non-interactive explanation row, visually consistent with microG Settings. */
    private fun addHelpRow(container: LinearLayout, iconRes: Int, titleRes: Int, descriptionRes: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
        }
        val iconLp = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
            rightMargin = dp(20)
        }
        row.addView(icon, iconLp)
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = TextView(this).apply {
            setText(titleRes)
            textSize = 14.5f
            setTextColor(UiKit.TEXT)
        }
        text.addView(title, full())
        val description = UiKit.muted(this, getString(descriptionRes), 12.5f).apply {
            setPadding(0, dp(3), 0, 0)
        }
        text.addView(description, full())
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        container.addView(row, full())
    }

    private fun showDetail(pkg: String) {
        previousScrollY = mainScroll?.scrollY ?: 0
        detailPkg = pkg
        render()
        mainScroll?.scrollTo(0, 0)
        SettingsMotion.enter(content!!)
    }

    private fun showList() {
        detailPkg = null
        render()
        mainScroll?.post { mainScroll?.scrollTo(0, previousScrollY) }
        SettingsMotion.enter(content!!)
    }

    private fun milletInSnapshot(packageName: String): Boolean {
        if (!milletSnapshotReadable || milletRawSnapshot == null) return false
        for (part in milletRawSnapshot!!.replace(';', ',')
            .replace('\n', ',').replace('\t', ',').split(",")) {
            if (packageName == part.trim()) return true
        }
        return false
    }

    /** Read-only built-in protection. Never writes to the user-managed app list. */
    private View buildSpecialGmsRow(): View {
        var status = SettingsListUi.NEUTRAL
        var statusRes = R.string.settings16_special_unknown
        try {
            packageManager.getApplicationInfo(GameWatchCore.GMS, 0)
            if (milletSnapshotReadable
                && (!GameWatchCore.isEnabled(this) || gameSnapshotReadable)
            ) {
                val milletOk = milletInSnapshot(GameWatchCore.GMS)
                        && milletInSnapshot(MilletNoRestrict.PLAY_STORE_PACKAGE)
                        && milletInSnapshot(packageName)
                val watcherOk = !MilletWatchScheduler.isSupported()
                        || MilletWatchScheduler.isScheduled(this)
                var gameOk = true
                if (GameWatchCore.isEnabled(this)) {
                    gameOk = GameWatchCore.contains(gameRawSnapshot, GameWatchCore.GMS)
                            && GameWatchCore.contains(gameRawSnapshot, packageName)
                            && GameWatchScheduler.isPending(this)
                            && GameWatchCore.canWrite(this)
                }
                status = if (milletOk && watcherOk && gameOk) SettingsListUi.GOOD else SettingsListUi.BAD
                statusRes = if (status == SettingsListUi.GOOD)
                    R.string.settings16_special_verified
                else
                    R.string.settings16_special_attention
            }
        } catch (missing: PackageManager.NameNotFoundException) {
            status = SettingsListUi.BAD
            statusRes = R.string.settings16_special_missing
        } catch (ignored: Throwable) {
            // Do not present success when SettingsProvider or PM is unreadable.
        }

        val markerIcon = if (status == SettingsListUi.GOOD)
            R.drawable.sg_ic_verified_check
        else if (status == SettingsListUi.BAD)
            R.drawable.sg_ic_status_error
        else
            R.drawable.sg_ic_status_pending
        val verifiedStatus = getString(statusRes)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(78)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            contentDescription = (getString(R.string.settings_gms_service_title)
                    + ". " + getString(R.string.settings16_special_locked)
                    + ". " + verifiedStatus)
        }

        val icon = appIcon(GameWatchCore.GMS)
        val iconLp = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            rightMargin = dp(12)
        }
        row.addView(icon, iconLp)

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = TextView(this).apply {
            setText(R.string.settings_gms_service_title)
            setTextColor(UiKit.TEXT)
            textSize = 16f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(title, full())
        val subtitle = UiKit.muted(this, getString(R.string.settings16_special_locked), 12.5f).apply {
            setPadding(0, dp(4), 0, 0)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(subtitle, full())
        val evidence = UiKit.muted(this, verifiedStatus, 12f).apply {
            setTextColor(SettingsListUi.tint(status))
            setPadding(0, dp(3), 0, 0)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(evidence, full())
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))

        val marker = ImageView(this).apply {
            setImageResource(markerIcon)
            contentDescription = verifiedStatus
        }
        val lp = LinearLayout.LayoutParams(dp(23), dp(23)).apply {
            leftMargin = dp(10)
        }
        row.addView(marker, lp)
        return row
    }

    /** Single compact row per app. Full permission diagnostics remain in detail. */
    private View buildAppSummary(AdvancedAppStore.Entry entry): View {
        val state = inspect(entry)
        val statuses = intArrayOf(
            state.autostart.kind, state.doze.kind, state.millet.kind,
            state.game.kind, state.mipush.kind
        )
        var passed = 0
        var attention = 0
        var unverified = 0
        for (kind in statuses) {
            if (kind == AdvancedAppStatusReader.VERIFIED
                || kind == AdvancedAppStatusReader.SENT_UNVERIFIED
            ) passed++
            else if (kind == AdvancedAppStatusReader.ATTENTION) attention++
            else unverified++
        }
        val active = entry.enabled
        val statusColor: Int
        val summary: String
        if (!state.installed) {
            summary = getString(R.string.settings_app_missing)
            statusColor = UiKit.ERROR
        } else if (!active) {
            summary = getString(R.string.settings_app_disabled)
            statusColor = UiKit.MUTED
        } else if (attention > 0) {
            summary = getString(R.string.settings_app_attention, passed, attention)
            statusColor = UiKit.ERROR
        } else if (unverified > 0) {
            summary = getString(R.string.settings_app_unknown, passed, unverified)
            statusColor = UiKit.MUTED
        } else {
            summary = getString(R.string.settings_app_all_ready)
            statusColor = UiKit.SUCCESS
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(73)
            setPadding(dp(16), dp(11), dp(18), dp(11))
            isClickable = true
            isFocusable = true
            background = SettingsMotion.ripple(this@AdvancedAppsActivity)
            setOnClickListener { showDetail(entry.pkg) }
            contentDescription = getString(R.string.adv_open_app_details, appLabel(entry.pkg)) + ", " + summary
        }

        val icon = appIcon(entry.pkg)
        val iconLp = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            rightMargin = dp(12)
        }
        row.addView(icon, iconLp)

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val name = TextView(this).apply {
            text = appLabel(entry.pkg)
            textSize = 16f
            setTextColor(UiKit.TEXT)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(name, full())
        val note = TextView(this).apply {
            text = summary
            textSize = 13f
            setTextColor(statusColor)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        }
        texts.addView(note, full())
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        val indicator = TextView(this).apply {
            text = if (active && state.installed && attention == 0 && unverified == 0) "✓"
            else if (attention > 0 || !state.installed) "!" else "•"
            textSize = 20f
            setTextColor(statusColor)
            gravity = Gravity.CENTER
        }
        val markLp = LinearLayout.LayoutParams(dp(25), dp(32)).apply {
            leftMargin = dp(6)
        }
        row.addView(indicator, markLp)
        return row
    }

    private fun appIcon(pkg: String): ImageView {
        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        try {
            icon.setImageDrawable(packageManager.getApplicationIcon(pkg))
        } catch (ignored: Throwable) {
            icon.setImageResource(android.R.drawable.sym_def_app_icon)
        }
        return icon
    }

    private fun bold(value: String, size: Float): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(UiKit.TEXT)
            setTypeface(null, Typeface.BOLD)
        }
    }

    /** Flat native Settings detail, intentionally without rounded cards or status pills. */
    private View buildAppDetail(AdvancedAppStore.Entry entry): View {
        val page = SettingsListUi.group(this)
        val pm = packageManager

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(12), dp(18), dp(10))
        }
        val icon = ImageView(this)
        try {
            icon.setImageDrawable(pm.getApplicationIcon(entry.pkg))
        } catch (ignored: Throwable) {
            icon.setImageResource(android.R.drawable.sym_def_app_icon)
        }
        val iconLp = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
            rightMargin = dp(13)
        }
        header.addView(icon, iconLp)
        val nameBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val name = bold(appLabel(entry.pkg), 17f).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        nameBox.addView(name, full())
        val packageLine = UiKit.muted(this, entry.pkg, 12.5f).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        nameBox.addView(packageLine, full())
        header.addView(nameBox, LinearLayout.LayoutParams(0, -2, 1f))
        
        val main = Switch(this).apply {
            isChecked = entry.enabled
            contentDescription = getString(R.string.adv_enable_app, appLabel(entry.pkg))
            setOnCheckedChangeListener { _, checked ->
                saveChanged(AdvancedAppStore.Entry(entry.pkg, checked))
            }
        }
        val switchLp = LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = dp(6)
        }
        header.addView(main, switchLp)
        page.addView(header, full())

        val state = inspect(entry)
        if (!state.installed) {
            val unavailable = SettingsListUi.group(this)
            SettingsListUi.row(
                this, unavailable, R.drawable.sg_ic_status_error,
                getString(R.string.adv_app_missing), null, null,
                SettingsListUi.BAD, null
            )
            page.addView(unavailable, full())
        }

        page.addView(SettingsListUi.heading(this, getString(R.string.ui_adv_permission_section)), full())
        val permissions = SettingsListUi.group(this)
        addStatusRow(permissions, getString(R.string.adv_autostart), state.autostart,
            R.string.adv_configure) { openAutostartSettings(entry.pkg) }
        addStatusRow(permissions, getString(R.string.adv_android_doze), state.doze, 0, null)
        addStatusRow(permissions, getString(R.string.adv_millet), state.millet, 0, null)
        addStatusRow(permissions, getString(R.string.adv_xiaomi_battery), state.battery,
            R.string.adv_configure) { openXiaomiBatterySettings(entry.pkg) }
        page.addView(permissions, full())
        addDetailNote(page, R.string.adv_pin_read_note)

        page.addView(SettingsListUi.heading(this, getString(R.string.adv_auto_layers)), full())
        val automatic = SettingsListUi.group(this)
        if (entry.enabled && !GameWatchCore.canWrite(this)) {
            addDetailNote(page, R.string.adv_write_required)
        }
        addStatusRow(
            automatic, getString(R.string.adv_game), state.game,
            if (!GameWatchCore.canWrite(this) && entry.enabled) R.string.adv_open_access else 0
        ) {
            if (!GameWatchCore.canWrite(this) && entry.enabled) openWriteSettings()
        }
        addStatusRow(automatic, getString(R.string.adv_mipush), state.mipush, 0, null)
        page.addView(automatic, full())
        addDetailNote(page, R.string.adv_mipush_legend)

        val actions = SettingsListUi.group(this)
        SettingsListUi.row(
            this, actions, android.R.drawable.ic_menu_delete,
            getString(R.string.adv_remove), null, null, SettingsListUi.NEUTRAL
        ) {
            AlertDialog.Builder(this)
                .setTitle(R.string.adv_remove)
                .setMessage(R.string.adv_remove_note)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.adv_remove_confirm) { _, _ ->
                    if (!AdvancedAppStore.remove(this, entry.pkg)) {
                        toast(R.string.adv_save_failed)
                        return@setPositiveButton
                    }
                    detailPkg = null
                    afterChange()
                }.show()
        }
        page.addView(actions, spaceBottom(14))
        return page
    }

    private fun addDetailNote(page: LinearLayout, stringRes: Int) {
        val note = UiKit.muted(this, getString(stringRes), 12f).apply {
            setPadding(dp(70), dp(7), dp(20), dp(10))
            setLineSpacing(dp(2).toFloat(), 1.05f)
        }
        page.addView(note, full())
    }

    private fun inspect(entry: AdvancedAppStore.Entry): AdvancedAppStatusReader.Snapshot {
        return AdvancedAppStatusReader.read(
            this, entry, gameRawSnapshot, gameSnapshotReadable,
            milletRawSnapshot, milletSnapshotReadable
        )
    }

    private fun addStatusRow(
        group: LinearLayout, title: String,
        status: AdvancedAppStatusReader.Status,
        actionLabel: Int, click: View.OnClickListener?
    ) {
        val kind = status.kind
        val iconRes = if (kind == AdvancedAppStatusReader.VERIFIED
            || kind == AdvancedAppStatusReader.SENT_UNVERIFIED
        )
            R.drawable.sg_ic_verified_check
        else if (kind == AdvancedAppStatusReader.ATTENTION)
            R.drawable.sg_ic_status_error
        else
            R.drawable.sg_ic_status_pending
            
        val tint = if (kind == AdvancedAppStatusReader.VERIFIED
            || kind == AdvancedAppStatusReader.SENT_UNVERIFIED
        )
            UiKit.SUCCESS
        else if (kind == AdvancedAppStatusReader.ATTENTION)
            UiKit.ERROR
        else
            UiKit.MUTED

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(18), dp(10))
            minimumHeight = dp(68)
        }

        val statusIcon = ImageView(this).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val iconLp = LinearLayout.LayoutParams(dp(23), dp(23)).apply {
            rightMargin = dp(31)
        }
        row.addView(statusIcon, iconLp)

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val label = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(UiKit.TEXT)
        }
        textColumn.addView(label, full())
        val value = TextView(this).apply {
            setText(status.message)
            textSize = 13f
            setTextColor(tint)
            maxLines = 3
            setPadding(0, dp(3), 0, 0)
        }
        textColumn.addView(value, full())
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))

        if (click != null && actionLabel != 0) {
            val action = TextView(this).apply {
                text = "${getString(actionLabel)}  ›"
                setTextColor(UiKit.PRIMARY)
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
            }
            val lp = LinearLayout.LayoutParams(-2, dp(40)).apply {
                leftMargin = dp(8)
            }
            row.addView(action, lp)
            row.background = SettingsMotion.ripple(this)
            row.setOnClickListener(click)
            row.isClickable = true
            row.isFocusable = true
        }
        row.contentDescription = title + ". " + getString(status.message) +
                if (click != null && actionLabel != 0) ". " + getString(actionLabel) else ""
        group.addView(row, full())
    }

    private fun supportsInstalledAppsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        return try {
            val permission = packageManager.getPermissionInfo(INSTALLED_APPS_PERMISSION, 0)
            permission != null
        } catch (unsupported: PackageManager.NameNotFoundException) {
            false
        } catch (restricted: Throwable) {
            true
        }
    }

    private fun canReadInstalledApps(): Boolean {
        if (!supportsInstalledAppsPermission()) return true
        return try {
            checkSelfPermission(INSTALLED_APPS_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } catch (ignored: Throwable) {
            false
        }
    }

    private fun onAddAppClicked() {
        if (canReadInstalledApps()) {
            selectInstalledApp()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.adv_installed_apps_title)
            .setMessage(R.string.adv_installed_apps_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.adv_installed_apps_allow) { _, _ ->
                requestInstalledAppsAccess()
            }.show()
    }

    private fun requestInstalledAppsAccess() {
        if (canReadInstalledApps()) {
            selectInstalledApp()
            return
        }
        if (Build.VERSION.SDK_INT >= 23 && supportsInstalledAppsPermission()) {
            try {
                requestPermissions(arrayOf(INSTALLED_APPS_PERMISSION), REQUEST_INSTALLED_APPS)
                return
            } catch (unsupported: Throwable) {
                android.util.Log.w("AdvancedApps", "Installed apps runtime permission unavailable", unsupported)
            }
        }
        showManualInstalledAppsPermission()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_INSTALLED_APPS) return
        if (canReadInstalledApps()) {
            if (!isFinishing() && detailPkg == null) {
                mainScroll?.post {
                    if (!isFinishing() && canReadInstalledApps()) selectInstalledApp()
                }
            }
        } else {
            showManualInstalledAppsPermission()
        }
    }

    private fun showManualInstalledAppsPermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.adv_installed_apps_manual_title)
            .setMessage(R.string.adv_installed_apps_manual_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.adv_installed_apps_settings) { _, _ ->
                pendingInstalledAppsSettings = true
                openAppDetails(packageName)
            }.show()
    }

    private fun showInstalledAppsUnavailable() {
        AlertDialog.Builder(this)
            .setTitle(R.string.adv_installed_apps_title)
            .setMessage(R.string.adv_installed_apps_scan_error)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.adv_installed_apps_settings) { _, _ ->
                openAppDetails(packageName)
            }.show()
    }

    private fun openWriteSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (ignored: Throwable) {
            openAppDetails(packageName)
        }
    }

    private fun saveChanged(entry: AdvancedAppStore.Entry) {
        if (!AdvancedAppStore.save(this, entry)) {
            toast(R.string.adv_save_failed)
            render()
            return
        }
        afterChange()
    }

    private fun afterChange() {
        AdvancedAppProtector.maintain(this, "USER_CHANGE")
        if (GameWatchScheduler.shouldWatch(this)) {
            GameWatchScheduler.ensure(this, "ADV_CHANGE")
        } else {
            GameWatchScheduler.cancel(this)
        }
        val scrollY = mainScroll?.scrollY ?: 0
        render()
        mainScroll?.post { mainScroll?.scrollTo(0, scrollY) }
    }

    private fun appLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (ignored: Throwable) {
            pkg
        }
    }

    private class InstalledAppScan {
        val choices = ArrayList<AppChoice>()
        var applicationsReturned = 0
        var packagesReturned = 0
        var primaryFailed = false
        var limitedLauncherFallback = false

        fun looksRestricted(): Boolean {
            return Math.max(applicationsReturned, packagesReturned) <= 5 && !limitedLauncherFallback
        }
    }

    private fun scanInstalledApps(existing: java.util.Set<String>): InstalledAppScan {
        val pm = packageManager
        val result = InstalledAppScan()
        val found = LinkedHashMap<String, ApplicationInfo>()
        try {
            val apps = pm.getInstalledApplications(0)
            result.applicationsReturned = apps?.size ?: 0
            if (apps != null) {
                for (info in apps) {
                    if (info?.packageName != null) found[info.packageName] = info
                }
            }
        } catch (error: Throwable) {
            result.primaryFailed = true
            android.util.Log.w("AdvancedApps", "getInstalledApplications failed", error)
        }

        if (result.applicationsReturned <= 5) {
            try {
                val packages = pm.getInstalledPackages(0)
                result.packagesReturned = packages?.size ?: 0
                if (packages != null) {
                    for (item in packages) {
                        if (item?.packageName == null) continue
                        var info = item.applicationInfo
                        if (info == null) {
                            try {
                                info = pm.getApplicationInfo(item.packageName, 0)
                            } catch (ignored: Throwable) {
                                continue
                            }
                        }
                        found[item.packageName] = info
                    }
                }
            } catch (error: Throwable) {
                android.util.Log.w("AdvancedApps", "getInstalledPackages failed", error)
            }
        }

        if (Math.max(result.applicationsReturned, result.packagesReturned) <= 5
            && !supportsInstalledAppsPermission()
        ) {
            try {
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val launchable = pm.queryIntentActivities(launcher, 0)
                if (launchable != null) {
                    for (activity in launchable) {
                        val info = activity.activityInfo?.applicationInfo
                        if (info?.packageName != null) found[info.packageName] = info
                    }
                }
                result.limitedLauncherFallback = found.size > 5
            } catch (error: Throwable) {
                android.util.Log.w("AdvancedApps", "launcher fallback failed", error)
            }
        }

        for (info in found.values) {
            val pkg = info.packageName
            if (!AdvancedAppStore.valid(this, pkg) || existing.contains(pkg)) continue
            val label = try {
                pm.getApplicationLabel(info).toString()
            } catch (error: Throwable) {
                pkg
            }
            val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            result.choices.add(AppChoice(pkg, label, system))
        }
        return result
    }

    private fun selectInstalledApp() {
        val pm = packageManager
        val current = AdvancedAppStore.list(this)
        if (current.size >= AdvancedAppStore.MAX_APPS) {
            toast(R.string.adv_save_failed)
            return
        }
        val existing = java.util.HashSet<String>()
        for (e in current) existing.add(e.pkg)
        val scan = scanInstalledApps(existing)
        if (scan.looksRestricted()) {
            if (supportsInstalledAppsPermission()) showManualInstalledAppsPermission()
            else showInstalledAppsUnavailable()
            return
        }
        if (scan.choices.isEmpty()) {
            toast(if (scan.primaryFailed) R.string.adv_installed_apps_scan_error else R.string.adv_no_available_apps)
            return
        }
        val all = scan.choices
        val collator = Collator.getInstance(Locale.getDefault())
        Collections.sort(all) { a, b ->
            if (a.system != b.system) if (a.system) 1 else -1
            else {
                val res = collator.compare(a.label, b.label)
                if (res == 0) a.pkg.compareTo(b.pkg) else res
            }
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(5), dp(12), dp(8))
        }
        val note = UiKit.muted(
            this, if (scan.limitedLauncherFallback)
                getString(R.string.adv_installed_apps_limited_list)
            else
                getString(R.string.adv_picker_note, all.size), 12.5f
        )
        box.addView(note, spaceBottom(5))
        val search = EditText(this).apply {
            isSingleLine = true
            textSize = 16f
            setHint(R.string.adv_search)
        }
        box.addView(search, spaceBottom(5))
        val list = ListView(this).apply {
            dividerHeight = 0
            isFastScrollEnabled = true
        }
        val filtered = ArrayList(all)
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = filtered.size
            override fun getItem(position: Int): Any = filtered[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, recycled: View?, parent: android.view.ViewGroup?): View {
                val h: PickerHolder
                val view: View
                if (recycled == null) {
                    val row = LinearLayout(this@AdvancedAppsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(9), dp(8), dp(8), dp(8))
                    }
                    val icon = ImageView(this@AdvancedAppsActivity).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                    val imageParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                        rightMargin = dp(12)
                    }
                    row.addView(icon, imageParams)
                    val names = LinearLayout(this@AdvancedAppsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    val name = bold("", 15.5f).apply {
                        isSingleLine = true
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    names.addView(name, full())
                    val pkg = UiKit.muted(this@AdvancedAppsActivity, "", 12.5f).apply {
                        isSingleLine = true
                        ellipsize = TextUtils.TruncateAt.MIDDLE
                    }
                    names.addView(pkg, full())
                    row.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
                    h = PickerHolder(icon, name, pkg)
                    row.tag = h
                    view = row
                } else {
                    h = recycled.tag as PickerHolder
                    view = recycled
                }
                val item = filtered[position]
                h.name.text = item.label
                h.pkg.text = item.pkg + if (item.system) "  ·  " + getString(R.string.adv_system_app) else ""
                try {
                    h.icon.setImageDrawable(pm.getApplicationIcon(item.pkg))
                } catch (ignored: Throwable) {
                    h.icon.setImageResource(android.R.drawable.sym_def_app_icon)
                }
                return view
            }
        }
        list.adapter = adapter
        box.addView(list, LinearLayout.LayoutParams(-1, dp(430)))
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.adv_choose_app)
            .setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            
        list.setOnItemClickListener { _, _, position, _ ->
            if (position < 0 || position >= filtered.size) return@setOnItemClickListener
            val pkg = filtered[position].pkg
            if (!AdvancedAppStore.save(this, AdvancedAppStore.Entry(pkg, true))) {
                toast(R.string.adv_save_failed)
                return@setOnItemClickListener
            }
            dialog.dismiss()
            afterChange()
            showDetail(pkg)
        }
        
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val filter = s.toString().trim().lowercase(Locale.ROOT)
                filtered.clear()
                for (app in all) {
                    if (app.label.lowercase(Locale.ROOT).contains(filter)
                        || app.pkg.
