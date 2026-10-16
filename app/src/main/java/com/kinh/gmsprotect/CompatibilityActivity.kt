package com.kinh.gmsprotect

import android.app.Activity
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CompatibilityActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiKit.PRIMARY_DARK
        window.navigationBarColor = UiKit.SURFACE
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.BG)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
            UiKit.applyBottomSystemInset(this)
        }

        // Card thông tin thiết bị
        val summary = UiKit.card(this)
        summary.addView(TextView(this).apply {
            text = getString(R.string.compat_title)
            textSize = 15f
            setTextColor(UiKit.TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        val deviceText = UiKit.muted(this, "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}", 11.5f)
        summary.addView(deviceText, LinearLayout.LayoutParams(-1, -2))
        page.addView(summary, cardMargin())

        // Card trạng thái bảo vệ GMS & Play Store
        val statusCard = UiKit.card(this)
        statusCard.addView(TextView(this).apply {
            text = "Trạng thái danh sách bảo vệ"
            textSize = 15f
            setTextColor(UiKit.TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val canWrite = MilletNoRestrict.canWrite(this)
        val gmsOk = MilletNoRestrict.containsGms(this)
        val playStoreOk = MilletNoRestrict.containsPlayStore(this)
        val selfOk = MilletNoRestrict.containsPackage(this, packageName)

        statusCard.addView(buildStatusRow("Ghi quyền hệ thống", canWrite))
        statusCard.addView(buildStatusRow("Google Play Services (GMS)", gmsOk))
        statusCard.addView(buildStatusRow("Google Play Store", playStoreOk))
        statusCard.addView(buildStatusRow("Gói ứng dụng hiện tại", selfOk))

        page.addView(statusCard, cardMargin())

        scroll.addView(page, ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun buildStatusRow(label: String, ok: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))

            addView(TextView(context).apply {
                text = label
                textSize = 13.5f
                setTextColor(UiKit.TEXT)
            }, LinearLayout.LayoutParams(0, -2, 1f))

            addView(TextView(context).apply {
                text = if (ok) "✓ Đã áp dụng" else "✗ Chưa có"
                textSize = 13.5f
                setTextColor(if (ok) UiKit.SUCCESS else UiKit.ERROR)
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(-2, -2))
        }
    }

    private fun dp(value: Int): Int = UiKit.dp(this, value)

    private fun cardMargin(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }
}
