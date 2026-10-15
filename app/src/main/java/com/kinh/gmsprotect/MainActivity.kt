package com.kinh.gmsprotect

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val PREFS = "setup_v1"
    private val KEY_SETUP_DONE = "setup_done"
    private val KEY_BATTERY_USER_CONFIRMED = "battery_user_confirmed_v1"

    private var protectResult = GmsProtector.Result.FAILED
    private var onboardingStep = 0
    private var waitingAutostartSettingsReturn = false
    private var waitingWriteSettingsReturn = false
    private var waitingBatterySettingsReturn = false

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        window.statusBarColor = UiKit.PRIMARY_DARK
        window.navigationBarColor = UiKit.SURFACE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        protectResult = GmsProtector.protect(this)
        AurogonAllowlistService.startIfEnabled(this)
        MilletWatchScheduler.schedule(this)

        if (isSetupDone()) {
            showMainScreen()
        } else {
            continueSetupFlow()
        }
    }

    override fun onResume() {
        super.onResume()
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
        }
    }

    private fun continueSetupFlow() {
        val autostart = XiaomiSetupState.getAutostartState(this)
        val autostartManuallyConfirmed = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean("autostart_manual_confirmed", false)

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
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("autostart_manual_confirmed", true).apply()
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

    private fun isSetupDone(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SETUP_DONE, false)

    private fun isBatteryUserConfirmed(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_BATTERY_USER_CONFIRMED, false)

    private fun setSetupDone(done: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SETUP_DONE, done).apply()
    }

    private fun buildHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(18))
            background = UiKit.verticalGradient()
            UiKit.applyTopSystemInset(this)

            addView(TextView(context).apply {
                text = getString(R.string.app_name)
                setTextColor(Color.WHITE)
                textSize = 25f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = getString(R.string.header_subtitle)
                setTextColor(0xFFDAD9FF.toInt())
                textSize = 12f
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun shell(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.BG)
            addView(buildHeader(), LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun showOnboarding(step: Int) {
        onboardingStep = Math.max(0, Math.min(2, step))
        val root = shell()

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
            UiKit.applyBottomSystemInset(this)
        }
        scroll.addView(page, ScrollView.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        page.addView(UiKit.muted(this, getString(R.string.step_format, onboardingStep + 1, 3), 12f).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val card = UiKit.card(this)
        card.addView(TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 46f
            setTextColor(UiKit.PRIMARY)
            text = if (onboardingStep == 0) "↻" else if (onboardingStep == 1) "⚙" else "☼"
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        card.addView(TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(UiKit.TEXT)
            setTypeface(typeface, Typeface.BOLD)
            text = getString(if (onboardingStep == 0) R.string.autostart_title else if (onboardingStep == 1) R.string.system_access_title else R.string.battery_title)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })

        card.addView(UiKit.muted(this, getString(if (onboardingStep == 0) R.string.autostart_body_auto else if (onboardingStep == 1) R.string.system_access_message_auto else R.string.battery_body), 13f).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })

        if (onboardingStep == 0) {
            card.addView(UiKit.primaryButton(this, getString(R.string.autostart_button)) {
                waitingAutostartSettingsReturn = true
                openAutostartSettings()
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })

            if (XiaomiSetupState.getAutostartState(this) == XiaomiSetupState.AutostartState.UNKNOWN) {
                card.addView(UiKit.secondaryButton(this, getString(R.string.autostart_manual_continue)) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean("autostart_manual_confirmed", true).apply()
                    continueSetupFlow()
                }, LinearLayout.LayoutParams(-1, -2))
            }
        } else if (onboardingStep == 1) {
            card.addView(UiKit.primaryButton(this, getString(R.string.system_access_button)) {
                openWriteSettingsAccess()
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            val ignoringDoze = BatterySetupState.isIgnoringBatteryOptimizations(this)
            card.addView(UiKit.muted(this, getString(if (ignoringDoze) R.string.battery_doze_granted else R.string.battery_doze_missing), 12f).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !ignoringDoze) {
                card.addView(UiKit.primaryButton(this, getString(R.string.battery_button)) {
                    requestAndroidBatteryExemption()
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            }
            card.addView(UiKit.secondaryButton(this, getString(R.string.battery_continue)) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_BATTERY_USER_CONFIRMED, true).apply()
                continueSetupFlow()
            }, LinearLayout.LayoutParams(-1, -2))
        }

        page.addView(card, LinearLayout.LayoutParams(-1, -2))
    }

    private void showMainScreen() {
        val root = shell()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            UiKit.applyBottomSystemInset(this)
        }
        scroll.addView(page, ScrollView.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val success = UiKit.card(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(25), dp(20), dp(22))
        }

        val ok = protectResult == GmsProtector.Result.SENT
        success.addView(TextView(this).apply {
            text = if (ok) "✓" else "!"
            textSize = 76f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (ok) UiKit.SUCCESS else UiKit.ERROR)
        }, LinearLayout.LayoutParams(-1, -2))

        success.addView(UiKit.pill(this, getString(if (ok) R.string.status_protected else R.string.status_failed), if (ok) UiKit.SUCCESS_SOFT else UiKit.ERROR_SOFT, if (ok) UiKit.SUCCESS else UiKit.ERROR), LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(12)
        })

        success.addView(TextView(this).apply {
            text = getString(if (ok) R.string.success_title else R.string.protect_failed)
            textSize = if (ok) 23f else 19f
            setTextColor(UiKit.TEXT)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })

        if (ok) {
            success.addView(UiKit.muted(this, getString(R.string.success_body), 13f).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        } else {
            success.addView(UiKit.primaryButton(this, getString(R.string.retry)) {
                protectResult = GmsProtector.protect(this)
                showMainScreen()
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        }
        page.addView(success, cardMargin())

        page.addView(buildAdvancedCard(), cardMargin())
        page.addView(buildCompatibilityCard(), cardMargin())

        val setupCard = buildSetupCardIfNeeded()
        if (setupCard != null) {
            page.addView(setupCard, cardMargin())
        }
        page.addView(buildAppInfoCard(), cardMargin())
    }

    private fun buildSetupCardIfNeeded(): View? {
        val autostart = XiaomiSetupState.getAutostartState(this)
        val writeAllowed = MilletNoRestrict.canWrite(this)
        val gmsOk = MilletNoRestrict.containsGms(this)
        val playStoreOk = MilletNoRestrict.containsPlayStore(this)
        val selfOk = MilletNoRestrict.containsPackage(this, packageName)
        val batteryPending = !isBatteryUserConfirmed() || BatterySetupState.isBackgroundRestricted(this)

        val autostartMissing = autostart == XiaomiSetupState.AutostartState.DENIED
        val writeMissing = !writeAllowed
        val milletMissing = writeAllowed && (!gmsOk || !playStoreOk || !selfOk)

        if (!autostartMissing && !writeMissing && !milletMissing && !batteryPending) return null

        return UiKit.card(this).apply {
            addView(TextView(context).apply {
                text = getString(R.string.setup)
                textSize = 15f
                setTextColor(UiKit.TEXT)
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

            if (autostartMissing) {
                addView(UiKit.muted(context, getString(R.string.autostart_setup_missing), 11.5f), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                addView(UiKit.primaryButton(context, getString(R.string.autostart_button)) {
                    waitingAutostartSettingsReturn = true
                    openAutostartSettings()
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            }

            if (writeMissing) {
                addView(UiKit.muted(context, getString(R.string.system_access_setup_line), 11.5f), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
                addView(UiKit.primaryButton(context, getString(R.string.system_access_button)) {
                    openWriteSettingsAccess()
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            } else if (milletMissing) {
                addView(UiKit.primaryButton(context, getString(R.string.system_access_apply_now)) {
                    MilletNoRestrict.ensureGms(context)
                    protectResult = GmsProtector.protect(context)
                    toast(getString(R.string.system_access_applied))
                    showMainScreen()
                }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            }
        }
    }

    private fun buildCompatibilityCard(): View {
        return UiKit.card(this).apply {
            addView(TextView(context).apply {
                text = getString(R.string.compat_title)
                textSize = 15f
                setTextColor(UiKit.TEXT)
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

            addView(UiKit.muted(context, getString(R.string.compat_card_desc), 11f), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })

            addView(UiKit.secondaryButton(context, getString(R.string.compat_open)) {
                startActivity(Intent(context, CompatibilityActivity::class.java))
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun buildAdvancedCard(): View {
        return UiKit.card(this).apply {
            addView(TextView(context).apply {
                text = getString(R.string.advanced)
                textSize = 15f
                setTextColor(UiKit.TEXT)
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = getString(R.string.aurogon_allowlist_title)
                    textSize = 13.5f
                    setTextColor(UiKit.TEXT)
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(UiKit.muted(context, getString(R.string.aurogon_allowlist_desc), 11f))
            }
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

            val toggle = Switch(context).apply {
                isChecked = AurogonAllowlistService.isEnabled(context)
                setOnCheckedChangeListener { _, isChecked ->
                    AurogonAllowlistService.setEnabled(context, isChecked)
                    toast(getString(if (isChecked) R.string.aurogon_allowlist_enabled else R.string.aurogon_allowlist_disabled))
                }
            }
            row.addView(toggle, LinearLayout.LayoutParams(-2, -2))
            addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun buildAppInfoCard(): View {
        return UiKit.card(this).apply {
            addView(TextView(context).apply {
                text = getString(R.string.app_info)
                textSize = 15f
                setTextColor(UiKit.TEXT)
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(UiKit.muted(context, getString(R.string.developer), 12f).apply {
                setPadding(0, dp(7), 0, 0)
            })
            addView(UiKit.muted(context, getString(R.string.about_note), 10.8f).apply {
                setPadding(0, dp(6), 0, 0)
            })
        }
    }

    private fun openWriteSettingsAccess() {
        waitingWriteSettingsReturn = true
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            startActivity(intent)
        } catch (_: Throwable) {
            waitingWriteSettingsReturn = false
            openAppDetails()
        }
    }

    private fun requestAndroidBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (BatterySetupState.isIgnoringBatteryOptimizations(this)) return
        waitingBatterySettingsReturn = true
        try {
            val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            startActivity(request)
        } catch (_: Throwable) {
            waitingBatterySettingsReturn = false
        }
    }

    private fun openAutostartSettings() {
        val action = Intent("miui.intent.action.OP_AUTO_START").setPackage("com.miui.securitycenter")
        try {
            startActivity(action)
        } catch (_: Throwable) {
            waitingAutostartSettingsReturn = false
            openAppDetails()
        }
    }

    private fun openAppDetails() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Throwable) {}
    }

    private fun toast(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = UiKit.dp(this, value)

    private fun cardMargin(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }
}
