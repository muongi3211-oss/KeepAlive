package com.kinh.gmsprotect

import android.app.Activity
import android.os.Build
import android.os.Bundle
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
            setPadding(UiKit.dp(context, 16), UiKit.dp(context, 18), UiKit.dp(context, 16), UiKit.dp(context, 28))
        }

        val summary = UiKit.card(this)
        val deviceText = UiKit.muted(this, "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}", 11.5f)
        summary.addView(deviceText)
        page.addView(summary, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiKit.dp(this@CompatibilityActivity, 14) })

        scroll.addView(page, ScrollView.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
}
