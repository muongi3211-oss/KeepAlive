package com.kinh.gmsprotect

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.Collator
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale

class AdvancedAppsActivity : Activity() {
    private var mainContent: LinearLayout? = null
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

    private fun dp(px: Int): Int = UiKit.dp(this, px)
    private fun full(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
    private fun spaceBottom(dp: Int): LinearLayout.LayoutParams {
        val lp = full()
        lp.bottomMargin = dp(dp)
        return lp
    }
    private fun toast(res: Int) { 
        try { Toast.makeText(this, res, Toast.LENGTH_SHORT).show() } catch (_: Throwable) {} 
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        try {
            window.statusBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) UiKit.BG else UiKit.PRIMARY_DARK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            window.navigationBarColor = UiKit.SURFACE
            if (Build.VERSION.SDK_INT >= 26) {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            lastWritePermission = GameWatchCore.canWrite(this)
            if (state != null) {
                detailPkg = state.getString("advanced_detail_pkg")
                pendingInstalledAppsSettings = state.getBoolean("advanced_pending_installed_apps", false)
            }
            buildShell()
            render()
        } catch (e: Throwable) {
            android.util.Log.e("AdvancedApps", "onCreate error", e)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        try {
            outState.putString("advanced_detail_pkg", detailPkg)
            outState.putBoolean("advanced_pending_installed_apps", pendingInstalledAppsSettings)
        } catch (_: Throwable) {}
    }

    override fun onBackPressed() {
        try {
            if (detailPkg != null) {
                showList()
                return
            }
            super.onBackPressed()
        } catch (_: Throwable) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val writeAllowed = GameWatchCore.canWrite(this)
            if (writeAllowed && !lastWritePermission) {
                AdvancedAppProtector.maintain(this, "WRITE_SETTINGS_GRANTED")
                if (GameWatchScheduler.shouldWatch(this)) GameWatchScheduler.ensure(this, "ADV_ACCESS_GRANTED")
            }
            lastWritePermission = writeAllowed
            val continueAdd = pendingInstalledAppsSettings && canReadInstalledApps()
            pendingInstalledAppsSettings = false
            if (screenBuilt) {
                val scrollY = mainScroll?.scrollY ?: 0
                render()
                mainScroll?.post { mainScroll?.scrollTo(0, scrollY) }
                if (continueAdd) {
                    mainScroll?.post {
                        if (!isFinishing && detailPkg == null) {
                            selectInstalledApp()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("AdvancedApps", "onResume error", e)
        }
    }

    private fun buildShell() {
        val frame = FrameLayout(this).apply { setBackgroundColor(UiKit.BG) }
        mainScroll = ScrollView(this).apply { isFillViewport = true }
        mainContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        UiKit.applyBottomSystemInset(mainContent!!)
        mainScroll?.addView(mainContent, FrameLayout.LayoutParams(-1, -2))
        frame.addView(mainScroll, FrameLayout.LayoutParams(-1, -1))
        collapsingHeader = SettingsMotion.collapsingBar(this, frame, mainScroll!!, getString(R.string.adv_title)) {
            try {
                if (detailPkg != null) showList() else finish()
            } catch (_: Throwable) {
                finish()
            }
        }
        setContentView(frame)
        screenBuilt = true
    }

    private fun render() {
        val contentLayout = this.mainContent ?: return
        try {
            contentLayout.removeAllViews()
            gameRawSnapshot = null
            gameSnapshotReadable = false
            try {
                gameRawSnapshot = GameWatchCore.raw(this)
                gameSnapshotReadable = true
            } catch (ignored: Throwable) {}

            milletRawSnapshot = null
            milletSnapshotReadable = false
            try {
                milletRawSnapshot = Settings.System.getString(contentResolver, MilletNoRestrict.KEY)
                milletSnapshotReadable = true
            } catch (ignored: Throwable) {}

            val selected = try { AdvancedAppStore.list(this) } catch (_: Throwable) { emptyList() }
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
                    contentLayout.addView(View(this), LinearLayout.LayoutParams(-1, dp(66)))
                    contentLayout.addView(buildAppDetail(current), spaceBottom(12))
                    return
                }
                detailPkg = null
            }
            collapsingHeader?.setTitle(getString(R.string.adv_title))
            collapsingHeader?.setCompactOnly(false)
            SettingsMotion.largeTitle(this, contentLayout, getString(R.string.adv_title))

            contentLayout.addView(SettingsListUi.heading(this, getString(R.string.settings16_special_heading)), full())
            val specialGroup = SettingsListUi.group(this)
            specialGroup.addView(buildSpecialGmsRow(), full())
            contentLayout.addView(specialGroup, spaceBottom(5))

            contentLayout.addView(SettingsListUi.heading(this, getString(R.string.settings16_user_apps_heading)), full())
            val actionGroup = SettingsListUi.group(this)
            SettingsListUi.row(this, actionGroup, R.drawable.sg_ic_apps, getString(R.string.adv_add), getString(R.string.settings_add_desc), null, SettingsListUi.NEUTRAL) { onAddAppClicked() }
            contentLayout.addView(actionGroup, spaceBottom(12))

            if (!GameWatchCore.canWrite(this)) {
                val warning = SettingsListUi.group(this)
                SettingsListUi.row(this, warning, R.drawable.sg_ic_settings, getString(R.string.adv_open_access), getString(R.string.adv_write_required), getString(R.string.ui_watch_attention), SettingsListUi.BAD) { openWriteSettings() }
                contentLayout.addView(warning, spaceBottom(12))
            }

            if (selected.isEmpty()) {
                val empty = UiKit.muted(this, getString(R.string.adv_empty), 14f).apply {
                    setPadding(dp(12), dp(25), dp(12), dp(25))
                }
                contentLayout.addView(empty, full())
            } else {
                contentLayout.addView(SettingsListUi.separator(this), LinearLayout.LayoutParams(-1, dp(1)))
                val appGroup = SettingsListUi.group(this)
                for (e in selected) {
                    try {
                        appGroup.addView(buildAppSummary(e), full())
                    } catch (_: Throwable) {}
                }
                contentLayout.addView(appGroup, spaceBottom(12))
            }

            val infoToggle = UiKit.muted(this, getString(if (infoExpanded) R.string.ui_hide_help else R.string.ui_show_help), 13f).apply {
                setTextColor(UiKit.PRIMARY)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(12), dp(18), dp(12))
                minimumHeight = dp(48)
                background = SettingsMotion.ripple(this@AdvancedAppsActivity)
                isClickable = true
                isFocusable = true
            }
            contentLayout.addView(infoToggle, spaceBottom(4))

            val helpContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (infoExpanded) View.VISIBLE else View.GONE
            }
            helpContainer.addView(SettingsListUi.heading(this, getString(R.string.adv_help_status_heading)), full())
            addHelpRow(helpContainer, R.drawable.sg_ic_verified_check, R.string.adv_help_pass_title, R.string.adv_help_pass_desc)
            addHelpRow(helpContainer, R.drawable.sg_ic_status_error, R.string.adv_help_attention_title, R.string.adv_help_attention_desc)
            addHelpRow(helpContainer, R.drawable.sg_ic_status_pending, R.string.adv_help_unknown_title, R.string.adv_help_unknown_desc)
            addHelpRow(helpContainer, R.drawable.sg_ic_status_off, R.string.adv_help_off_title, R.string.adv_help_off_desc)
            helpContainer.addView(SettingsListUi.heading(this, getString(R.string.adv_help_rule_heading)), full())
            addHelpRow(helpContainer, R.drawable.sg_ic_status_info, R.string.adv_help_five_title, R.string.adv_help_five_desc)
            addHelpRow(helpContainer, R.drawable.sg_ic_status_info, R.string.adv_help_mipush_title, R.string.adv_help_mipush_desc)
            addHelpRow(helpContainer, R.drawable.sg_ic_status_info, R.string.adv_help_battery_title, R.string.adv_help_battery_desc)
            addHelpRow(helpContainer, R.drawable.sg_ic_status_info, R.string.adv_help_watcher_title, R.string.adv_help_watcher_desc)

            val jobInfo = UiKit.muted(
                this,
                try {
                    getString(
                        R.string.adv_jobs_status,
                        getString(if (GameWatchScheduler.isPending(this)) R.string.adv_job_yes else R.string.adv_job_no),
                        getString(if (MilletWatchScheduler.isScheduled(this)) R.string.adv_job_yes else R.string.adv_job_no)
                    )
                } catch (_: Throwable) { "" },
                12f
            ).apply { setPadding(dp(18), dp(12), dp(18), dp(18)) }
            helpContainer.addView(jobInfo, full())
            contentLayout.addView(helpContainer, full())

            infoToggle.setOnClickListener {
                infoExpanded = !infoExpanded
                helpContainer.visibility = if (infoExpanded) View.VISIBLE else View.GONE
                infoToggle.text = getString(if (infoExpanded) R.string.ui_hide_help else R.string.ui_show_help)
            }
        } catch (e: Throwable) {
            android.util.Log.e("AdvancedApps", "render error", e)
        }
    }

    private fun addHelpRow(container: LinearLayout, iconRes: Int, titleRes: Int, descriptionRes: Int) {
        try {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(dp(18), dp(10), dp(18), dp(10))
            }
            val icon = ImageView(this).apply { setImageResource(iconRes) }
            val iconLp = LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(20) }
            row.addView(icon, iconLp)

            val textLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val title = TextView(this).apply {
                text = getString(titleRes)
                textSize = 14.5f
                setTextColor(UiKit.TEXT)
            }
            textLayout.addView(title, full())
            val description = UiKit.muted(this, getString(descriptionRes), 12.5f).apply {
                setPadding(0, dp(3), 0, 0)
            }
            textLayout.addView(description, full())
            row.addView(textLayout, LinearLayout.LayoutParams(0, -2, 1f))
            container.addView(row, full())
        } catch (_: Throwable) {}
    }

    private fun showDetail(pkg: String) {
        try {
            previousScrollY = mainScroll?.scrollY ?: 0
            detailPkg = pkg
            render()
            mainScroll?.scrollTo(0, 0)
            SettingsMotion.enter(mainContent!!)
        } catch (_: Throwable) {}
    }

    private fun showList() {
        try {
            detailPkg = null
            render()
            mainScroll?.post { mainScroll?.scrollTo(0, previousScrollY) }
            SettingsMotion.enter(mainContent!!)
        } catch (_: Throwable) {}
    }

    private fun milletInSnapshot(packageName: String): Boolean {
        try {
            if (!milletSnapshotReadable || milletRawSnapshot == null) return false
            for (part in milletRawSnapshot!!.replace(';', ',').replace('\n', ',').replace('\t', ',').split(",".toRegex())) {
                if (packageName == part.trim()) return true
            }
        } catch (_: Throwable) {}
        return false
    }

    private fun buildSpecialGmsRow(): View {
        var status = SettingsListUi.NEUTRAL
        var statusRes = R.string.settings16_special_unknown
        try {
            packageManager.getApplicationInfo(GameWatchCore.GMS, 0)
            if (milletSnapshotReadable && (!GameWatchCore.isEnabled(this) || gameSnapshotReadable)) {
                val milletOk = milletInSnapshot(GameWatchCore.GMS) &&
                        milletInSnapshot(MilletNoRestrict.PLAY_STORE_PACKAGE) &&
                        milletInSnapshot(packageName)
                val watcherOk = !MilletWatchScheduler.isSupported() || MilletWatchScheduler.isScheduled(this)
                var gameOk = true
                if (GameWatchCore.isEnabled(this)) {
                    gameOk = GameWatchCore.contains(gameRawSnapshot, GameWatchCore.GMS) &&
                            GameWatchCore.contains(gameRawSnapshot, packageName) &&
                            GameWatchScheduler.isPending(this) &&
                            GameWatchCore.canWrite(this)
                }
                status = if (milletOk && watcherOk && gameOk) SettingsListUi.GOOD else SettingsListUi.BAD
                statusRes = if (status == SettingsListUi.GOOD) R.string.settings16_special_verified else R.string.settings16_special_attention
            }
        } catch (_: Throwable) {
            status = SettingsListUi.BAD
            statusRes = R.string.settings16_special_missing
        }

        val markerIcon = when (status) {
            SettingsListUi.GOOD -> R.drawable.sg_ic_verified_check
            SettingsListUi.BAD -> R.drawable.sg_ic_status_error
            else -> R.drawable.sg_ic_status_pending
        }
        val verifiedStatus = try { getString(statusRes) } catch (_: Throwable) { "" }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(78)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            contentDescription = try { getString(R.string.settings_gms_service_title) } catch (_: Throwable) { "" } + ". " + verifiedStatus
        }

        val icon = appIcon(GameWatchCore.GMS)
        val iconLp = LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) }
        row.addView(icon, iconLp)

        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = try { getString(R.string.settings_gms_service_title) } catch (_: Throwable) { "Google Play Services" }
            setTextColor(UiKit.TEXT)
            textSize = 16f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(title, full())
        val subtitle = UiKit.muted(this, try { getString(R.string.settings16_special_locked) } catch (_: Throwable) { "" }, 12.5f).apply {
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
        val lp = LinearLayout.LayoutParams(dp(23), dp(23)).apply { leftMargin = dp(10) }
        row.addView(marker, lp)
        return row
    }

    private fun buildAppSummary(entry: AdvancedAppStore.Entry): View {
        var passed = 0
        var attention = 0
        var unverified = 0
        var isInstalled = true
        try {
            val state = inspect(entry)
            isInstalled = state.installed
            val statuses = intArrayOf(
                state.autostart.kind, state.doze.kind, state.millet.kind,
                state.game.kind, state.mipush.kind
            )
            for (kind in statuses) {
                if (kind == AdvancedAppStatusReader.VERIFIED || kind == AdvancedAppStatusReader.SENT_UNVERIFIED) passed++
                else if (kind == AdvancedAppStatusReader.ATTENTION) attention++
                else unverified++
            }
        } catch (_: Throwable) {
            unverified = 5
        }

        val active = entry.enabled
        val statusColor: Int
        val summary: String
        if (!isInstalled) {
            summary = try { getString(R.string.settings_app_missing) } catch (_: Throwable) { "Missing" }
            statusColor = UiKit.ERROR
        } else if (!active) {
            summary = try { getString(R.string.settings_app_disabled) } catch (_: Throwable) { "Disabled" }
            statusColor = UiKit.MUTED
        } else if (attention > 0) {
            summary = try { getString(R.string.settings_app_attention, passed, attention) } catch (_: Throwable) { "Attention" }
            statusColor = UiKit.ERROR
        } else if (unverified > 0) {
            summary = try { getString(R.string.settings_app_unknown, passed, unverified) } catch (_: Throwable) { "Unknown" }
            statusColor = UiKit.MUTED
        } else {
            summary = try { getString(R.string.settings_app_all_ready) } catch (_: Throwable) { "Ready" }
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
        }

        val icon = appIcon(entry.pkg)
        val iconLp = LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) }
        row.addView(icon, iconLp)

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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
            text = if (active && isInstalled && attention == 0 && unverified == 0) "✓" else if (attention > 0 || !isInstalled) "!" else "•"
            textSize = 20f
            setTextColor(statusColor)
            gravity = Gravity.CENTER
        }
        val markLp = LinearLayout.LayoutParams(dp(25), dp(32)).apply { leftMargin = dp(6) }
        row.addView(indicator, markLp)
        return row
    }

    private fun appIcon(pkg: String): ImageView {
        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            try {
                setImageDrawable(packageManager.getApplicationIcon(pkg))
            } catch (_: Throwable) {
                try { setImageResource(android.R.drawable.sym_def_app_icon) } catch (_: Throwable) {}
            }
        }
    }

    private fun bold(value: String, size: Float): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(UiKit.TEXT)
            setTypeface(null, Typeface.BOLD)
        }
    }

    private fun buildAppDetail(entry: AdvancedAppStore.Entry): View {
        val page = SettingsListUi.group(this)
        val pm = packageManager

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(12), dp(18), dp(10))
        }
        val icon = ImageView(this).apply {
            try {
                setImageDrawable(pm.getApplicationIcon(entry.pkg))
            } catch (_: Throwable) {
                try { setImageResource(android.R.drawable.sym_def_app_icon) } catch (_: Throwable) {}
            }
        }
        val iconLp = LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(13) }
        header.addView(icon, iconLp)

        val nameBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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
            setOnCheckedChangeListener { _, checked ->
                try {
                    saveChanged(AdvancedAppStore.Entry(entry.pkg, checked))
                } catch (_: Throwable) {}
            }
        }
        val switchLp = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) }
        header.addView(main, switchLp)
        page.addView(header, full())

        try {
            val state = inspect(entry)
            if (!state.installed) {
                val unavailable = SettingsListUi.group(this)
                SettingsListUi.row(this, unavailable, R.drawable.sg_ic_status_error, getString(R.string.adv_app_missing), null, null, SettingsListUi.BAD, null)
                page.addView(unavailable, full())
            }

            page.addView(SettingsListUi.heading(this, getString(R.string.ui_adv_permission_section)), full())
            val permissions = SettingsListUi.group(this)
            addStatusRow(permissions, getString(R.string.adv_autostart), state.autostart, R.string.adv_configure) { openAutostartSettings(entry.pkg) }
            addStatusRow(permissions, getString(R.string.adv_android_doze), state.doze, 0, null)
            addStatusRow(permissions, getString(R.string.adv_millet), state.millet, 0, null)
            addStatusRow(permissions, getString(R.string.adv_xiaomi_battery), state.battery, R.string.adv_configure) { openXiaomiBatterySettings(entry.pkg) }
            page.addView(permissions, full())
            addDetailNote(page, R.string.adv_pin_read_note)

            page.addView(SettingsListUi.heading(this, getString(R.string.adv_auto_layers)), full())
            val automatic = SettingsListUi.group(this)
            if (entry.enabled && !GameWatchCore.canWrite(this)) {
                addDetailNote(page, R.string.adv_write_required)
            }
            addStatusRow(
                automatic,
                getString(R.string.adv_game),
                state.game,
                if (!GameWatchCore.canWrite(this) && entry.enabled) R.string.adv_open_access else 0,
                if (!GameWatchCore.canWrite(this) && entry.enabled) View.OnClickListener { openWriteSettings() } else null
            )
            addStatusRow(automatic, getString(R.string.adv_mipush), state.mipush, 0, null)
            page.addView(automatic, full())
            addDetailNote(page, R.string.adv_mipush_legend)
        } catch (_: Throwable) {}

        val actions = SettingsListUi.group(this)
        SettingsListUi.row(this, actions, android.R.drawable.ic_menu_delete, try { getString(R.string.adv_remove) } catch (_: Throwable) { "Remove" }, null, null, SettingsListUi.NEUTRAL) {
            try {
                AlertDialog.Builder(this)
                    .setTitle(R.string.adv_remove)
                    .setMessage(R.string.adv_remove_note)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.adv_remove_confirm) { _, _ ->
                        try {
                            if (!AdvancedAppStore.remove(this, entry.pkg)) {
                                toast(R.string.adv_save_failed)
                                return@setPositiveButton
                            }
                            detailPkg = null
                            afterChange()
                        } catch (_: Throwable) {}
                    }.show()
            } catch (_: Throwable) {}
        }
        page.addView(actions, spaceBottom(14))
        return page
    }

    private fun addDetailNote(page: LinearLayout, stringRes: Int) {
        try {
            val note = UiKit.muted(this, getString(stringRes), 12f).apply {
                setPadding(dp(70), dp(7), dp(20), dp(10))
                setLineSpacing(dp(2).toFloat(), 1.05f)
            }
            page.addView(note, full())
        } catch (_: Throwable) {}
    }

    private fun inspect(entry: AdvancedAppStore.Entry): AdvancedAppStatusReader.Snapshot {
    return try {
        AdvancedAppStatusReader.read(this, entry, gameRawSnapshot, gameSnapshotReadable, milletRawSnapshot, milletSnapshotReadable)
    } catch (e: Throwable) {
        val fallbackStatus = AdvancedAppStatusReader.Status(AdvancedAppStatusReader.ATTENTION, 0)
        AdvancedAppStatusReader.Snapshot(
            false,
            fallbackStatus,
            fallbackStatus,
            fallbackStatus,
            fallbackStatus,
            fallbackStatus,
            fallbackStatus
        )
    }
}

    private fun addStatusRow(
        group: LinearLayout,
        title: String,
        status: AdvancedAppStatusReader.Status,
        actionLabel: Int,
        click: View.OnClickListener?
    ) {
        try {
            val kind = status.kind
            val iconRes = if (kind == AdvancedAppStatusReader.VERIFIED || kind == AdvancedAppStatusReader.SENT_UNVERIFIED)
                R.drawable.sg_ic_verified_check
            else if (kind == AdvancedAppStatusReader.ATTENTION)
                R.drawable.sg_ic_status_error
            else
                R.drawable.sg_ic_status_pending

            val tint = if (kind == AdvancedAppStatusReader.VERIFIED || kind == AdvancedAppStatusReader.SENT_UNVERIFIED)
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
            val iconLp = LinearLayout.LayoutParams(dp(23), dp(23)).apply { rightMargin = dp(31) }
            row.addView(statusIcon, iconLp)

            val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val label = TextView(this).apply {
                text = title
                textSize = 16f
                setTextColor(UiKit.TEXT)
            }
            textColumn.addView(label, full())
            val value = TextView(this).apply {
                try { setText(status.message) } catch (_: Throwable) {}
                textSize = 13f
                setTextColor(tint)
                maxLines = 3
                setPadding(0, dp(3), 0, 0)
            }
            textColumn.addView(value, full())
            row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))

            if (click != null && actionLabel != 0) {
                val action = TextView(this).apply {
                    text = try { getString(actionLabel) } catch (_: Throwable) { "" } + "  ›"
                    setTextColor(UiKit.PRIMARY)
                    textSize = 13f
                    gravity = Gravity.CENTER_VERTICAL or Gravity.END
                }
                val lp = LinearLayout.LayoutParams(-2, dp(40)).apply { leftMargin = dp(8) }
                row.addView(action, lp)
                row.background = SettingsMotion.ripple(this)
                row.setOnClickListener(click)
                row.isClickable = true
                row.isFocusable = true
            }
            group.addView(row, full())
        } catch (_: Throwable) {}
    }

    private fun supportsInstalledAppsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        return try {
            val permission = packageManager.getPermissionInfo(INSTALLED_APPS_PERMISSION, 0)
            permission != null
        } catch (_: Throwable) {
            false
        }
    }

    private fun canReadInstalledApps(): Boolean {
        if (!supportsInstalledAppsPermission()) return true
        return try {
            checkSelfPermission(INSTALLED_APPS_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun onAddAppClicked() {
        try {
            if (canReadInstalledApps()) {
                selectInstalledApp()
                return
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.adv_installed_apps_title)
                .setMessage(R.string.adv_installed_apps_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.adv_installed_apps_allow) { _, _ -> requestInstalledAppsAccess() }
                .show()
        } catch (_: Throwable) {
            selectInstalledApp()
        }
    }

    private fun requestInstalledAppsAccess() {
        try {
            if (canReadInstalledApps()) {
                selectInstalledApp()
                return
            }
            if (Build.VERSION.SDK_INT >= 23 && supportsInstalledAppsPermission()) {
                try {
                    requestPermissions(arrayOf(INSTALLED_APPS_PERMISSION), REQUEST_INSTALLED_APPS)
                    return
                } catch (_: Throwable) {}
            }
            showManualInstalledAppsPermission()
        } catch (_: Throwable) {}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        try {
            if (requestCode != REQUEST_INSTALLED_APPS) return
            if (canReadInstalledApps()) {
                if (!isFinishing && detailPkg == null) {
                    mainScroll?.post {
                        if (!isFinishing && canReadInstalledApps()) selectInstalledApp()
                    }
                }
            } else {
                showManualInstalledAppsPermission()
            }
        } catch (_: Throwable) {}
    }

    private fun showManualInstalledAppsPermission() {
        try {
            AlertDialog.Builder(this)
                .setTitle(R.string.adv_installed_apps_manual_title)
                .setMessage(R.string.adv_installed_apps_manual_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.adv_installed_apps_settings) { _, _ ->
                    pendingInstalledAppsSettings = true
                    openAppDetails(packageName)
                }
                .show()
        } catch (_: Throwable) {}
    }

    private fun showInstalledAppsUnavailable() {
        try {
            AlertDialog.Builder(this)
                .setTitle(R.string.adv_installed_apps_title)
                .setMessage(R.string.adv_installed_apps_scan_error)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.adv_installed_apps_settings) { _, _ -> openAppDetails(packageName) }
                .show()
        } catch (_: Throwable) {}
    }

    private fun openWriteSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Throwable) {
            openAppDetails(packageName)
        }
    }

    private fun saveChanged(entry: AdvancedAppStore.Entry) {
        try {
            if (!AdvancedAppStore.save(this, entry)) {
                toast(R.string.adv_save_failed)
                render()
                return
            }
            afterChange()
        } catch (_: Throwable) {}
    }

    private fun afterChange() {
        try {
            AdvancedAppProtector.maintain(this, "USER_CHANGE")
            if (GameWatchScheduler.shouldWatch(this)) GameWatchScheduler.ensure(this, "ADV_CHANGE")
            else GameWatchScheduler.cancel(this)
            val scrollY = mainScroll?.scrollY ?: 0
            render()
            mainScroll?.post { mainScroll?.scrollTo(0, scrollY) }
        } catch (_: Throwable) {}
    }

    private fun appLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Throwable) {
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

    private fun scanInstalledApps(existing: Set<String>): InstalledAppScan {
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
        } catch (_: Throwable) {
            result.primaryFailed = true
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
                            } catch (_: Throwable) {
                                continue
                            }
                        }
                        found[item.packageName] = info
                    }
                }
            } catch (_: Throwable) {}
        }

        if (Math.max(result.applicationsReturned, result.packagesReturned) <= 5 && !supportsInstalledAppsPermission()) {
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
            } catch (_: Throwable) {}
        }

        for (info in found.values) {
            try {
                val pkg = info.packageName
                if (!AdvancedAppStore.valid(this, pkg) || existing.contains(pkg)) continue
                val label = try {
                    pm.getApplicationLabel(info).toString()
                } catch (_: Throwable) {
                    pkg
                }
                val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                result.choices.add(AppChoice(pkg, label, system))
            } catch (_: Throwable) {}
        }
        return result
    }

    private fun selectInstalledApp() {
        try {
            val pm = packageManager
            val current = try { AdvancedAppStore.list(this) } catch (_: Throwable) { emptyList() }
            if (current.size >= AdvancedAppStore.MAX_APPS) {
                toast(R.string.adv_save_failed)
                return
            }
            val existing = java.util.HashSet<String>()
            for (e in current) existing.add(e.pkg)
            val scan = scanInstalledApps(existing)
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
            val pickerNote = UiKit.muted(
                this,
                if (scan.limitedLauncherFallback) getString(R.string.adv_installed_apps_limited_list) else getString(R.string.adv_picker_note, all.size),
                12.5f
            )
            box.addView(pickerNote, spaceBottom(5))

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
                        val imageParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(12) }
                        row.addView(icon, imageParams)

                        val names = LinearLayout(this@AdvancedAppsActivity).apply { orientation = LinearLayout.VERTICAL }
                        val name = bold("", 15.5f).apply {
                            isSingleLine = true
                            ellipsize = TextUtils.TruncateAt.END
                        }
                        names.addView(name, full())
                        val pkgText = UiKit.muted(this@AdvancedAppsActivity, "", 12.5f).apply {
                            isSingleLine = true
                            ellipsize = TextUtils.TruncateAt.MIDDLE
                        }
                        names.addView(pkgText, full())
                        row.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
                        
                        h = PickerHolder(icon, name, pkgText)
                        row.tag = h
                        view = row
                    } else {
                        h = recycled.tag as PickerHolder
                        view = recycled
                    }
                    val item = filtered[position]
                    h.name.text = item.label
                    h.pkg.text = item.pkg + if (item.system) "  ·  " + try { getString(R.string.adv_system_app) } catch (_: Throwable) { "System" } else ""
                    try {
                        h.icon.setImageDrawable(pm.getApplicationIcon(item.pkg))
                    } catch (_: Throwable) {
                        try { h.icon.setImageResource(android.R.drawable.sym_def_app_icon) } catch (_: Throwable) {}
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
                try {
                    if (position < 0 || position >= filtered.size) return@setOnItemClickListener
                    val pkg = filtered[position].pkg
                    if (!AdvancedAppStore.save(this, AdvancedAppStore.Entry(pkg, true))) {
                        toast(R.string.adv_save_failed)
                        return@setOnItemClickListener
                    }
                    dialog.dismiss()
                    afterChange()
                    showDetail(pkg)
                } catch (_: Throwable) {}
            }

            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    try {
                        val filter = s?.toString()?.trim()?.lowercase(Locale.ROOT) ?: ""
                        filtered.clear()
                        for (app in all) {
                            if (app.label.lowercase(Locale.ROOT).contains(filter) || app.pkg.lowercase(Locale.ROOT).contains(filter)) {
                                filtered.add(app)
                            }
                        }
                        adapter.notifyDataSetChanged()
                        pickerNote.text = if (filtered.isEmpty()) getString(R.string.adv_no_results)
                        else if (scan.limitedLauncherFallback) getString(R.string.adv_installed_apps_limited_list)
                        else getString(R.string.adv_picker_note, filtered.size)
                        list.setSelection(0)
                    } catch (_: Throwable) {}
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            dialog.show()
        } catch (_: Throwable) {}
    }

    private class AppChoice(val pkg: String, val label: String, val system: Boolean)
    private class PickerHolder(val icon: ImageView, val name: TextView, val pkg: TextView)

    private fun openXiaomiBatterySettings(pkg: String?) {
        if (pkg.isNullOrEmpty()) {
            toast(R.string.adv_no_settings)
            return
        }
        try {
            val intent = Intent().apply {
                component = ComponentName("com.miui.securitycenter", "com.miui.powercenter.legacypowerrank.PowerDetailActivity")
                putExtra("package_name", pkg)
                putExtra("package_label", appLabel(pkg))
            }
            startActivity(intent)
        } catch (_: Throwable) {
            openAppDetails(pkg)
        }
    }

    private fun openAutostartSettings(pkg: String) {
        try {
            val action = Intent("miui.intent.action.OP_AUTO_START").apply {
                setPackage("com.miui.securitycenter")
                putExtra("package_name", pkg)
            }
            startActivity(action)
        } catch (_: Throwable) {
            try {
                val direct = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
                startActivity(direct)
            } catch (_: Throwable) {
                openAppDetails(pkg)
            }
        }
    }

    private fun openAppDetails(pkg: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        } catch (_: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Throwable) {
                toast(R.string.adv_no_settings)
            }
        }
    }

    companion object {
        private const val INSTALLED_APPS_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"
        private const val REQUEST_INSTALLED_APPS = 1152
    }
}
