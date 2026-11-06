package com.hanh.gmsprotect

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private var protectResult = GmsProtector.Result.FAILED
    private var onboardingStep = 0
    private var waitingAutostartSettingsReturn = false
    private var waitingWriteSettingsReturn = false
    private var waitingBatterySettingsReturn = false
    private var donatePromptCheckedThisSession = false
    private var waitingAdvancedScreenReturn = false
    private var googleServiceCheckRow: LinearLayout? = null
    private var recheckGoogleRowAfterPause = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) UiKit.BG else UiKit.PRIMARY_DARK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        window.navigationBarColor = UiKit.SURFACE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        protectResult = GmsProtector.protect(this)
        LegacyBackgroundCleanup.runOnce(this)

        MilletWatchScheduler.schedule(this)
        if (GameWatchCore.isEnabled(this)) {
            GameWatchCore.ensureTargets(this, "APP_OPEN")
        }
        AdvancedAppProtector.maintain(this, "APP_OPEN")
        if (GameWatchScheduler.shouldWatch(this)) GameWatchScheduler.ensure(this, "APP_OPEN")

        if (isSetupDone()) {
            recordReturningLaunchForDonate()
            showMainScreen()
        } else {
            continueSetupFlow()
        }
    }

    override fun onPause() {
        GameWatchEvidence.noteUi(this, false)
        recheckGoogleRowAfterPause = googleServiceCheckRow != null
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        GameWatchEvidence.noteUi(this, true)
        if (GameWatchCore.isEnabled(this)) {
            GameWatchCore.ensureTargets(this, "APP_RESUME")
        }
        if (GameWatchScheduler.shouldWatch(this)) GameWatchScheduler.ensure(this, "APP_RESUME")

        if (waitingAdvancedScreenReturn) {
            waitingAdvancedScreenReturn = false
            if (isSetupDone()) showMainScreen()
            return
        }

        if (waitingAutostartSettingsReturn) {
            waitingAutostartSettingsReturn = false
            checkAutostartAfterReturn(0)
            return
        }

        if (waitingWriteSettingsReturn) {
            waitingWriteSettingsReturn = false
            checkWriteSettingsAfterReturn(0)
            return
        }

        if (waitingBatterySettingsReturn) {
            waitingBatterySettingsReturn = false
            if (isSetupDone()) showMainScreen()
            else showOnboarding(2)
            return
        }

        if (recheckGoogleRowAfterPause && isSetupDone() && googleServiceCheckRow != null && googleServiceCheckRow!!.isAttachedToWindow) {
            GoogleServiceCheckUi.checkOnce(this, googleServiceCheckRow!!, protectResult)
        }
        recheckGoogleRowAfterPause = false
    }

    private fun continueSetupFlow() {
        val autostart = XiaomiSetupState.getAutostartState(this)
        val autostartManuallyConfirmed = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("autostart_manual_confirmed", false)

        if (autostart == XiaomiSetupState.AutostartState.DENIED ||
            (autostart == XiaomiSetupState.AutostartState.UNKNOWN && !autostartManuallyConfirmed && !isSetupDone())
        ) {
            showOnboarding(0)
            return
        }

        if (!MilletNoRestrict.canWrite(this)) {
            showOnboarding(1)
            return
        }

        if (!isBatteryUserConfirmed()) {
            showOnboarding(2)
            return
        }

        finishSetup()
    }

    private fun checkAutostartAfterReturn(attempt: Int) {
        val state = XiaomiSetupState.getAutostartState(this)
        if (state == XiaomiSetupState.AutostartState.ALLOWED) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("autostart_manual_confirmed", true).apply()
            continueSetupFlow()
            return
        }
        if (state == XiaomiSetupState.AutostartState.UNKNOWN) {
            showOnboarding(0)
            return
        }
        if (attempt < 2) {
            val delayMs = if (attempt == 0) 450L else 900L
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing) checkAutostartAfterReturn(attempt + 1)
            }, delayMs)
            return
        }
        showOnboarding(0)
        toast(getString(R.string.autostart_not_enabled))
    }

    private fun checkWriteSettingsAfterReturn(attempt: Int) {
        if (MilletNoRestrict.canWrite(this)) {
            MilletNoRestrict.ensureGms(this)
            continueSetupFlow()
            return
        }
        if (attempt < 2) {
            val delayMs = if (attempt == 0) 500L else 1000L
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing) checkWriteSettingsAfterReturn(attempt + 1)
            }, delayMs)
            return
        }
        showOnboarding(1)
        toast(getString(R.string.system_access_not_granted))
    }

    private fun finishSetup() {
        MilletNoRestrict.ensureGms(this)
        protectResult = GmsProtector.protect(this)
        setSetupDone(true)
        showMainScreen()
    }

    private fun isSetupDone(): Boolean = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SETUP_DONE, false)

    private fun isBatteryUserConfirmed(): Boolean = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_BATTERY_USER_CONFIRMED, false)

    private fun setSetupDone(done: Boolean) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val editor = prefs.edit().putBoolean(KEY_SETUP_DONE, done)
        if (done && prefs.getLong(KEY_DONATE_START_MS, 0L) == 0L) {
            editor.putLong(KEY_DONATE_START_MS, System.currentTimeMillis())
            editor.putInt(KEY_DONATE_LAUNCH_COUNT, Math.max(1, prefs.getInt(KEY_DONATE_LAUNCH_COUNT, 0)))
        }
        editor.apply()
    }

    private fun recordReturningLaunchForDonate() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val launches = prefs.getInt(KEY_DONATE_LAUNCH_COUNT, 0)
        prefs.edit().putInt(KEY_DONATE_LAUNCH_COUNT, launches + 1).apply()
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(16))
            setBackgroundColor(UiKit.BG)
        }
        UiKit.applyTopSystemInset(header)

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(UiKit.TEXT)
            textSize = 23f
            setTypeface(typeface, Typeface.BOLD)
        }
        header.addView(title)

        val sub = TextView(this).apply {
            text = getString(R.string.ui_short_header)
            setTextColor(UiKit.MUTED)
            textSize = 13f
            setPadding(0, dp(3), 0, 0)
        }
        header.addView(sub)
        return header
    }

    private fun shell(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.BG)
            addView(buildHeader(), matchWidth())
        }
    }

    private fun showOnboarding(step: Int) {
        onboardingStep = Math.max(0, Math.min(2, step))
        val autostart = XiaomiSetupState.getAutostartState(this)
        val autostartManual = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("autostart_manual_confirmed", false)
        val complete = booleanArrayOf(
            autostart == XiaomiSetupState.AutostartState.ALLOWED || (autostart == XiaomiSetupState.AutostartState.UNKNOWN && autostartManual),
            MilletNoRestrict.canWrite(this),
            isBatteryUserConfirmed()
        )
        val completed = (if (complete[0]) 1 else 0) + (if (complete[1]) 1 else 0) + (if (complete[2]) 1 else 0)

        val root = shell()
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isClipToPadding = false
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(14))
        }
        scroll.addView(page, ScrollView.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val shield = onboardingIcon(R.drawable.sg_ic_shield, 46, 24, true)
        heading.addView(shield, LinearLayout.LayoutParams(dp(46), dp(46)))

        val headingCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        headingCopy.addView(onboardingText(R.string.onboarding_overview_title, 18f, UiKit.TEXT, true), matchWidth())
        val summary = onboardingText(R.string.onboarding_overview_subtitle, 12.5f, UiKit.MUTED, false).apply {
            setPadding(0, dp(3), 0, 0)
        }
        headingCopy.addView(summary, matchWidth())
        heading.addView(headingCopy, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(heading, matchWidthWithBottom(dp(22)))

        val progress = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0..2) {
            val segment = View(this)
            val color = if (complete[i]) UiKit.SUCCESS else if (i == onboardingStep) UiKit.PRIMARY else UiKit.BORDER
            segment.background = UiKit.rounded(color, 4, this)
            val lp = LinearLayout.LayoutParams(0, dp(5), 1f)
            if (i > 0) lp.leftMargin = dp(7)
            progress.addView(segment, lp)
        }
        page.addView(progress, matchWidthWithBottom(dp(13)))

        val counter = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        counter.addView(onboardingText(R.string.onboarding_section_label, 13f, UiKit.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        val count = TextView(this).apply {
            text = getString(R.string.onboarding_completed_format, completed, 3)
            setTextColor(UiKit.MUTED)
            textSize = 12.5f
        }
        counter.addView(count)
        page.addView(counter, matchWidthWithBottom(dp(7)))

        val permissionRows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.rounded(UiKit.BG, 16, this)
        }
        addOnboardingRow(permissionRows, 0, R.drawable.sg_ic_power, R.string.onboarding_autostart_name, R.string.onboarding_autostart_desc, complete[0], autostart == XiaomiSetupState.AutostartState.ALLOWED)
        onboardingDivider(permissionRows)
        addOnboardingRow(permissionRows, 1, R.drawable.sg_ic_settings, R.string.onboarding_system_name, R.string.onboarding_system_desc, complete[1], true)
        onboardingDivider(permissionRows)
        addOnboardingRow(permissionRows, 2, R.drawable.sg_ic_battery, R.string.onboarding_battery_name, R.string.onboarding_battery_desc, complete[2], false)
        page.addView(permissionRows, matchWidthWithBottom(dp(15)))

        val stepLabel = onboardingText(R.string.onboarding_instruction_label, 12.5f, UiKit.PRIMARY, true)
        page.addView(stepLabel, matchWidthWithBottom(dp(6)))
        val explanation = onboardingText(
            if (onboardingStep == 0) R.string.autostart_body_auto else if (onboardingStep == 1) R.string.system_access_message_auto else R.string.battery_hyperos_check_limited,
            12.5f,
            UiKit.MUTED,
            false
        ).apply { setLineSpacing(dp(2).toFloat(), 1f) }
        page.addView(explanation, matchWidthWithBottom(dp(12)))

        if (onboardingStep == 2) {
            val exempt = BatterySetupState.isIgnoringBatteryOptimizations(this)
            val androidBattery = onboardingText(if (exempt) R.string.battery_doze_granted else R.string.battery_doze_missing, 12f, if (exempt) UiKit.SUCCESS else UiKit.MUTED, false)
            page.addView(androidBattery, matchWidthWithBottom(dp(10)))
        }

        val hint = LinearLayout(this).apply {
            gravity = Gravity.TOP
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(11), dp(10), dp(11), dp(10))
            background = UiKit.rounded(UiKit.PRIMARY_SOFT, 12, this)
        }
        val hintIcon = ImageView(this).apply {
            setImageResource(R.drawable.sg_ic_info)
            setColorFilter(UiKit.PRIMARY)
        }
        hint.addView(hintIcon, LinearLayout.LayoutParams(dp(18), dp(18)))
        val hintText = onboardingText(R.string.onboarding_info_note, 11.5f, UiKit.MUTED, false)
        val hintLp = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) }
        hint.addView(hintText, hintLp)
        page.addView(hint, matchWidth())

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(14))
            setBackgroundColor(UiKit.BG)
        }
        UiKit.applyBottomSystemInset(actions)

        if (onboardingStep == 0) {
            if (complete[0]) {
                actions.addView(UiKit.primaryButton(this, getString(R.string.onboarding_next_pending)) { continueSetupFlow() }, matchWidth())
            } else {
                actions.addView(UiKit.primaryButton(this, getString(R.string.autostart_button)) {
                    waitingAutostartSettingsReturn = true
                    openAutostartSettings()
                }, matchWidth())
                if (autostart == XiaomiSetupState.AutostartState.UNKNOWN) {
                    actions.addView(UiKit.secondaryButton(this, getString(R.string.autostart_manual_continue)) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("autostart_manual_confirmed", true).apply()
                        continueSetupFlow()
                    }, matchWidthWithTop(dp(9)))
                }
            }
        } else if (onboardingStep == 1) {
            if (complete[1]) {
                actions.addView(UiKit.primaryButton(this, getString(R.string.onboarding_next_pending)) { continueSetupFlow() }, matchWidth())
            } else {
                actions.addView(UiKit.primaryButton(this, getString(R.string.system_access_button)) { openWriteSettingsAccess() }, matchWidth())
            }
        } else {
            val ignoringDoze = BatterySetupState.isIgnoringBatteryOptimizations(this)
            val canRequestExemption = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !ignoringDoze
            if (canRequestExemption) {
                actions.addView(UiKit.primaryButton(this, getString(R.string.battery_button)) { requestAndroidBatteryExemption() }, matchWidth())
            }
            if (!complete[2]) {
                val confirm = if (canRequestExemption)
                    UiKit.secondaryButton(this, getString(R.string.battery_continue)) { confirmOnboardingBattery() }
                else
                    UiKit.primaryButton(this, getString(R.string.battery_continue)) { confirmOnboardingBattery() }
                actions.addView(confirm, if (canRequestExemption) matchWidthWithTop(dp(9)) else matchWidth())
            } else {
                actions.addView(UiKit.primaryButton(this, getString(R.string.onboarding_next_pending)) { continueSetupFlow() }, matchWidthWithTop(if (canRequestExemption) dp(9) else 0))
            }
        }
        root.addView(actions, matchWidth())
        setContentView(root)
    }

    private fun confirmOnboardingBattery() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_BATTERY_USER_CONFIRMED, true).apply()
        continueSetupFlow()
    }

    private fun onboardingText(res: Int, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            setText(res)
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun onboardingIcon(iconRes: Int, boxDp: Int, glyphDp: Int, hero: Boolean): ImageView {
        return ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(if (hero) UiKit.PRIMARY else UiKit.MUTED)
            setPadding(dp((boxDp - glyphDp) / 2), dp((boxDp - glyphDp) / 2), dp((boxDp - glyphDp) / 2), dp((boxDp - glyphDp) / 2))
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = UiKit.rounded(UiKit.PRIMARY_SOFT, 13, this@MainActivity)
        }
    }

    private fun onboardingDivider(parent: LinearLayout) {
        val separator = View(this).apply { setBackgroundColor(UiKit.BORDER) }
        val lp = LinearLayout.LayoutParams(-1, dp(1)).apply { leftMargin = dp(60) }
        parent.addView(separator, lp)
    }

    private fun addOnboardingRow(
        parent: LinearLayout,
        index: Int,
        iconRes: Int,
        titleRes: Int,
        descriptionRes: Int,
        done: Boolean,
        independentlyVerified: Boolean
    ) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(5), dp(10), dp(5), dp(10))
            minimumHeight = dp(76)
            background = if (onboardingStep == index) UiKit.rounded(0xFFEBEDF4.toInt(), 13, this@MainActivity) else SettingsMotion.ripple(this@MainActivity)
        }
        val icon = onboardingIcon(iconRes, 42, 22, false)
        row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))

        val label = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        label.addView(onboardingText(titleRes, 14.5f, UiKit.TEXT, true), matchWidth())
        val description = onboardingText(descriptionRes, 12f, UiKit.MUTED, false).apply {
            maxLines = 2
            setPadding(0, dp(3), 0, 0)
        }
        label.addView(description, matchWidth())

        val labelRes = if (done) (if (independentlyVerified) R.string.onboarding_verified else R.string.onboarding_confirmed) else R.string.onboarding_pending
        val statusLabel = onboardingText(labelRes, 11.5f, if (done) (if (independentlyVerified) UiKit.SUCCESS else UiKit.MUTED) else UiKit.MUTED, false).apply {
            setPadding(0, dp(3), 0, 0)
        }
        label.addView(statusLabel, matchWidth())
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))

        val status = ImageView(this).apply {
            setImageResource(if (done) R.drawable.sg_ic_verified_check else R.drawable.sg_ic_status_pending)
            contentDescription = getString(if (done) (if (independentlyVerified) R.string.onboarding_verified else R.string.onboarding_confirmed) else R.string.onboarding_pending)
        }
        val statusLp = LinearLayout.LayoutParams(dp(21), dp(21)).apply {
            leftMargin = dp(9)
            rightMargin = dp(6)
        }
        row.addView(status, statusLp)
        row.contentDescription = getString(titleRes) + ", " + status.contentDescription
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener { showOnboarding(index) }
        parent.addView(row, matchWidth())
    }

    private fun showMainScreen() {
        val frame = FrameLayout(this).apply { setBackgroundColor(UiKit.BG) }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isClipToPadding = false
        }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        UiKit.applyBottomSystemInset(page)
        scroll.addView(page, ScrollView.LayoutParams(-1, -2))
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        SettingsMotion.collapsingBar(this, frame, scroll, getString(R.string.app_name), null)
        setContentView(frame)

        SettingsMotion.largeTitle(this, page, getString(R.string.app_name))

        val verify = SettingsListUi.group(this)
        SettingsListUi.row(this, verify, R.drawable.sg_ic_check, getString(R.string.settings13_self_check), getString(R.string.settings13_self_check_desc), null, SettingsListUi.NEUTRAL) {
            startActivity(Intent(this, CompatibilityActivity::class.java))
        }
        page.addView(verify, matchWidth())
        addMicrogSeparator(page)

        page.addView(SettingsListUi.heading(this, getString(R.string.settings13_google_section)), matchWidth())
        val protection = SettingsListUi.group(this)
        val googleServiceRow = SettingsListUi.row(this, protection, R.drawable.sg_ic_shield, getString(R.string.settings_gms_service_title), getString(R.string.settings14_checking), null, SettingsListUi.NEUTRAL) {
            showProtectionDetailsDialog()
        }
        googleServiceRow.contentDescription = getString(R.string.settings_gms_service_title) + ". " + getString(R.string.settings_protection_details_desc)
        googleServiceCheckRow = googleServiceRow
        recheckGoogleRowAfterPause = false
        GoogleServiceCheckUi.checkOnce(this, googleServiceRow, protectResult)

        SettingsListUi.row(this, protection, R.drawable.sg_ic_apps, getString(R.string.ui_section_apps), getString(R.string.adv_main_desc, 1 + AdvancedAppStore.activeCount(this)), null, SettingsListUi.NEUTRAL) {
            openManagedApps()
        }
        page.addView(protection, matchWidth())

        val needed = buildSetupCardIfNeeded()
        if (needed != null) {
            addMicrogSeparator(page)
            page.addView(SettingsListUi.heading(this, getString(R.string.ui_section_attention)), matchWidth())
            val wrapper = LinearLayout(this).apply { setPadding(dp(20), 0, dp(20), dp(8)) }
            wrapper.addView(needed, matchWidth())
            page.addView(wrapper, matchWidth())
        }

        addMicrogSeparator(page)
        page.addView(SettingsListUi.heading(this, getString(R.string.settings_tools)), matchWidth())
        val tools = SettingsListUi.group(this)
        val doze = BatterySetupState.isIgnoringBatteryOptimizations(this)
        SettingsListUi.row(
            this, tools, R.drawable.sg_ic_battery, getString(R.string.settings_battery_short),
            getString(if (doze) R.string.ui_status_verified else R.string.ui_status_missing), null, if (doze) SettingsListUi.GOOD else SettingsListUi.BAD
        ) {
            if (!BatterySetupState.isIgnoringBatteryOptimizations(this)) requestAndroidBatteryExemption()
            else showAndroidBatteryInfo()
        }
        SettingsListUi.row(this, tools, R.drawable.sg_ic_history, getString(R.string.gamewatch_logs), getString(R.string.settings_history_desc), null, SettingsListUi.NEUTRAL) {
            startActivity(Intent(this, ActivityLogActivity::class.java))
        }
        SettingsListUi.row(this, tools, R.drawable.sg_ic_heart, getString(R.string.donate_title), null, null, SettingsListUi.NEUTRAL) {
            showDonateDialog()
        }
        SettingsListUi.row(this, tools, R.drawable.sg_ic_info, getString(R.string.app_info), null, null, SettingsListUi.NEUTRAL) {
            showAboutDialog()
        }
        page.addView(tools, matchWidth())
        page.post { maybeShowDonatePrompt() }
    }

    private fun addMicrogSeparator(page: LinearLayout) {
        val lp = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(9) }
        page.addView(SettingsListUi.separator(this), lp)
    }

    private fun showProtectionDetailsDialog() {
        val details = SettingsListUi.group(this).apply { setPadding(dp(2), dp(8), dp(2), dp(6)) }
        val gmsInMillet = MilletNoRestrict.containsGms(this)
        val playInMillet = MilletNoRestrict.containsPlayStore(this)
        addProtectionStatusRow(details, R.string.dashboard_gms, gmsInMillet, false)
        addProtectionStatusRow(details, R.string.dashboard_greezer, protectResult == GmsProtector.Result.SENT, true)
        addProtectionStatusRow(details, R.string.dashboard_play_store, playInMillet, false)
        val explanation = UiKit.muted(this, getString(R.string.settings105_mipush_note), 12.5f).apply {
            setPadding(dp(20), dp(10), dp(20), dp(8))
        }
        details.addView(explanation, matchWidth())
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_protection)
            .setView(details)
            .setNeutralButton(R.string.retry) { _, _ ->
                protectResult = GmsProtector.protect(this)
                showMainScreen()
            }
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun showAndroidBatteryInfo() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_battery_short)
            .setMessage(R.string.battery_doze_granted)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun showAboutDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.app_info)
            .setView(buildAppInfoCard())
            .setPositiveButton(R.string.close, null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(UiKit.rounded(UiKit.BG, 20, this))
            val close = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            close?.apply {
                isAllCaps = false
                setTextColor(UiKit.PRIMARY)
            }
        }
        dialog.show()
    }

    private fun addProtectionStatusRow(group: LinearLayout, labelRes: Int, success: Boolean, requestOnly: Boolean) {
        val statusRes = if (requestOnly) (if (success) R.string.ui_status_sent else R.string.settings105_mipush_not_sent) else if (success) R.string.ui_status_verified else R.string.ui_status_missing
        val iconRes = if (success) R.drawable.sg_ic_verified_check else R.drawable.sg_ic_status_error
        val row = SettingsListUi.row(this, group, iconRes, getString(labelRes), getString(statusRes), null, if (success) SettingsListUi.GOOD else SettingsListUi.BAD, null)
        (row.getChildAt(0) as ImageView).clearColorFilter()
    }

    private fun buildSetupCardIfNeeded(): View? {
        val autostart = XiaomiSetupState.getAutostartState(this)
        val writeAllowed = MilletNoRestrict.canWrite(this)
        val gmsOk = MilletNoRestrict.containsGms(this)
        val playStoreOk = MilletNoRestrict.containsPlayStore(this)
        val selfOk = MilletNoRestrict.containsSelf(this)
        val batteryPending = !isBatteryUserConfirmed() || BatterySetupState.isBackgroundRestricted(this)

        val autostartMissing = autostart == XiaomiSetupState.AutostartState.DENIED
        val writeMissing = !writeAllowed
        val milletMissing = writeAllowed && (!gmsOk || !playStoreOk || !selfOk)

        if (!autostartMissing && !writeMissing && !milletMissing && !batteryPending) {
            return null
        }

        val setup = SettingsListUi.group(this).apply { setPadding(dp(16), dp(12), dp(16), dp(15)) }
        val setupTitle = TextView(this).apply {
            text = getString(R.string.setup)
            textSize = 15f
            setTextColor(UiKit.TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }
        setup.addView(setupTitle, matchWidthWithBottom(dp(8)))

        if (autostartMissing) {
            val line = UiKit.muted(this, getString(R.string.autostart_setup_missing), 11.5f)
            setup.addView(line, matchWidthWithBottom(dp(8)))
            setup.addView(UiKit.primaryButton(this, getString(R.string.autostart_button)) {
                waitingAutostartSettingsReturn = true
                openAutostartSettings()
            }, matchWidthWithBottom(dp(9)))
        }

        if (writeMissing) {
            val line = UiKit.muted(this, getString(R.string.system_access_setup_line), 11.5f)
            setup.addView(line, matchWidthWithBottom(dp(8)))
            setup.addView(UiKit.primaryButton(this, getString(R.string.system_access_button)) { openWriteSettingsAccess() }, matchWidthWithBottom(dp(9)))
        } else if (milletMissing) {
            setup.addView(UiKit.primaryButton(this, getString(R.string.system_access_apply_now)) {
                val result = MilletNoRestrict.ensureGms(this)
                protectResult = GmsProtector.protect(this)
                toast(getString(if (result == MilletNoRestrict.Result.ADDED || result == MilletNoRestrict.Result.ALREADY_PRESENT) R.string.system_access_applied else R.string.system_access_apply_failed))
                showMainScreen()
            }, matchWidthWithBottom(dp(9)))
        }

        if (batteryPending) {
            val line = UiKit.muted(this, getString(R.string.battery_setup_missing), 11.5f)
            setup.addView(line, matchWidthWithBottom(dp(8)))
            setup.addView(UiKit.secondaryButton(this, getString(R.string.battery_review)) { showOnboarding(2) }, matchWidthWithBottom(dp(9)))
        }
        return setup
    }

    private fun maybeShowDonatePrompt() {
        if (donatePromptCheckedThisSession || !isSetupDone() || isFinishing) return
        donatePromptCheckedThisSession = true

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val start = prefs.getLong(KEY_DONATE_START_MS, 0L)
        val launches = prefs.getInt(KEY_DONATE_LAUNCH_COUNT, 0)
        val lastPrompt = prefs.getLong(KEY_DONATE_LAST_PROMPT_MS, 0L)
        val promptCount = prefs.getInt(KEY_DONATE_PROMPT_COUNT, 0)

        if (start <= 0L) {
            prefs.edit().putLong(KEY_DONATE_START_MS, now).apply()
            return
        }
        if (now - start < DONATE_FIRST_DELAY_MS) return
        if (launches < DONATE_MIN_LAUNCHES) return
        if (promptCount >= DONATE_MAX_AUTO_PROMPTS) return
        if (lastPrompt > 0L && now - lastPrompt < DONATE_REPEAT_DELAY_MS) return

        val days = Math.max(7L, (now - start) / DAY_MS)
        prefs.edit().putLong(KEY_DONATE_LAST_PROMPT_MS, now).putInt(KEY_DONATE_PROMPT_COUNT, promptCount + 1).apply()

        AlertDialog.Builder(this)
            .setTitle(R.string.donate_reminder_title)
            .setMessage(getString(R.string.donate_reminder_message, days))
            .setPositiveButton(R.string.donate_button) { _, _ -> showDonateDialog() }
            .setNegativeButton(R.string.donate_later, null)
            .show()
    }

    private fun openManagedApps() {
        waitingAdvancedScreenReturn = true
        startActivity(Intent(this, AdvancedAppsActivity::class.java))
    }

    private fun buildAppInfoCard(): View {
        val content = SettingsListUi.group(this).apply { setPadding(0, dp(4), 0, dp(8)) }
        SettingsListUi.row(this, content, R.drawable.sg_ic_info, getString(R.string.about_developer_label), getString(R.string.developer), null, SettingsListUi.NEUTRAL, null)
        SettingsListUi.row(this, content, R.drawable.ic_facebook, getString(R.string.facebook), null, null, SettingsListUi.NEUTRAL) { openFacebookProfile() }

        val note = UiKit.muted(this, getString(R.string.about_note), 13f).apply {
            setLineSpacing(dp(2).toFloat(), 1.02f)
            setPadding(dp(20), dp(12), dp(20), dp(6))
        }
        content.addView(note, matchWidth())
        return content
    }

    private fun showDonateDialog() {
        val usdtEvm = "0x4cC35bE54c358146E7b71E58f965532193848FDd"
        val usdtTrc20 = "TD8fkqHy5cGQU3tFf7Qi7AeeqrAWi6e9Rm"

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(10), dp(22), dp(12))
        }

        val thanks = TextView(this).apply {
            text = getString(R.string.donate_thanks)
            textSize = 14f
            setTextColor(UiKit.TEXT)
            gravity = Gravity.CENTER
        }
        content.addView(thanks, matchWidthWithBottom(dp(14)))

        val qr = ImageView(this).apply {
            setImageResource(R.drawable.donate_qr)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            background = UiKit.rounded(Color.WHITE, 16, this@MainActivity)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        content.addView(qr, LinearLayout.LayoutParams(dp(240), dp(240)))

        val hint = UiKit.muted(this, getString(R.string.donate_qr_hint), 12f).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        content.addView(hint, matchWidth())

        val cryptoTitle = TextView(this).apply {
            text = getString(R.string.donate_usdt)
            textSize = 13f
            setTextColor(UiKit.TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }
        content.addView(cryptoTitle, matchWidth())
        content.addView(buildCryptoAddressRow("USDT - EVM", usdtEvm), matchWidthWithTop(dp(8)))
        content.addView(buildCryptoAddressRow("USDT - TRC20", usdtTrc20), matchWidthWithTop(dp(8)))

        val warning = UiKit.muted(this, getString(R.string.donate_network_warning), 10.5f).apply {
            setPadding(0, dp(9), 0, 0)
        }
        content.addView(warning, matchWidth())

        val scroll = ScrollView(this).apply { addView(content, ScrollView.LayoutParams(-1, -2)) }
        AlertDialog.Builder(this)
            .setTitle(R.string.donate_dialog_title)
            .setView(scroll)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun buildCryptoAddressRow(network: String, address: String): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(9), dp(9), dp(9))
            background = UiKit.rounded(0xFFF8F7FF.toInt(), 12, this@MainActivity, UiKit.BORDER, 1)
        }

        val label = TextView(this).apply {
            text = network
            textSize = 12.5f
            setTextColor(UiKit.TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }
        box.addView(label, matchWidth())

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, 0)
        }

        val value = TextView(this).apply {
            text = address
            textSize = 12.5f
            setTextColor(UiKit.MUTED)
            setTextIsSelectable(true)
            setTypeface(Typeface.MONOSPACE)
        }
        row.addView(value, LinearLayout.LayoutParams(0, -2, 1f))

        val copy = UiKit.secondaryButton(this, getString(R.string.copy)) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText(network, address))
            toast(getString(R.string.copied_address, network))
        }
        val copyLp = LinearLayout.LayoutParams(dp(82), dp(42)).apply { leftMargin = dp(8) }
        row.addView(copy, copyLp)
        box.addView(row, matchWidth())
        return box
    }

    private fun openWriteSettingsAccess() {
        waitingWriteSettingsReturn = true
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        } catch (ignored: Throwable) {}

        try {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
        } catch (ignored: Throwable) {
            waitingWriteSettingsReturn = false
            openAppDetails(getString(R.string.system_access_settings_fallback))
        }
    }

    private fun requestAndroidBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (BatterySetupState.isIgnoringBatteryOptimizations(this)) {
            showOnboarding(2)
            return
        }
        waitingBatterySettingsReturn = true
        try {
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            startActivity(request)
        } catch (ignored: Throwable) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (ignoredAgain: Throwable) {
                waitingBatterySettingsReturn = false
                toast(getString(R.string.battery_request_unavailable))
            }
        }
    }

    private fun openAutostartSettings() {
        val action = Intent("miui.intent.action.OP_AUTO_START").apply { setPackage("com.miui.securitycenter") }
        val direct = componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")

        if (!tryIntents(arrayOf(action, direct))) {
            waitingAutostartSettingsReturn = false
            openAppDetails(getString(R.string.settings_hint_autostart))
        }
    }

    private fun componentIntent(packageName: String, className: String): Intent {
        return Intent().setComponent(ComponentName(packageName, className))
    }

    private fun tryIntents(intents: Array<Intent>): Boolean {
        for (intent in intents) {
            try {
                startActivity(intent)
                return true
            } catch (ignored: Throwable) {}
        }
        return false
    }

    private fun openAppDetails(hint: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            toast(hint)
        } catch (ignored: Throwable) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (ignoredAgain: Throwable) {}
        }
    }

    private fun openFacebookProfile() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/mrhankytb")))
        } catch (t: Throwable) {
            toast(getString(R.string.facebook_failed))
        }
    }

    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show() }
    private fun dp(value: Int): Int = UiKit.dp(this, value)
    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2)
    private fun matchWidthWithBottom(bottom: Int): LinearLayout.LayoutParams = matchWidth().apply { bottomMargin = bottom }
    private fun matchWidthWithTop(top: Int): LinearLayout.LayoutParams = matchWidth().apply { topMargin = top }

    companion object {
        private const val PREFS = "setup_v1"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_BATTERY_USER_CONFIRMED = "battery_user_confirmed_v1"
        private const val KEY_DONATE_START_MS = "donate_start_ms"
        private const val KEY_DONATE_LAUNCH_COUNT = "donate_launch_count"
        private const val KEY_DONATE_LAST_PROMPT_MS = "donate_last_prompt_ms"
        private const val KEY_DONATE_PROMPT_COUNT = "donate_prompt_count"

        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val DONATE_FIRST_DELAY_MS = 7L * DAY_MS
        private const val DONATE_REPEAT_DELAY_MS = 30L * DAY_MS
        private const val DONATE_MIN_LAUNCHES = 3
        private const val DONATE_MAX_AUTO_PROMPTS = 3
    }
}
