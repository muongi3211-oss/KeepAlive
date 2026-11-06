package com.hanh.gmsprotect

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.animation.DecelerateInterpolator

object SettingsMotion {
    private fun dp(c: Context, x: Int): Int = UiKit.dp(c, x)

    fun ripple(c: Context): RippleDrawable {
        return RippleDrawable(
            ColorStateList.valueOf(0x1B666577.toInt()),
            ColorDrawable(Color.TRANSPARENT),
            null
        )
    }

    fun largeTitle(c: Context, page: LinearLayout, title: String) {
        val label = TextView(c).apply {
            text = title
            setTextColor(UiKit.TEXT)
            textSize = 30f
            setTypeface(null, Typeface.NORMAL)
            gravity = Gravity.BOTTOM or Gravity.START
            setPadding(dp(c, 20), dp(c, 103), dp(c, 20), dp(c, 22))
        }
        page.addView(label, LinearLayout.LayoutParams(-1, -2))
    }

    class CollapsingHeader(
        private val context: Context,
        private val title: TextView,
        private val bar: LinearLayout,
        private val scroll: ScrollView,
        private val hasBack: Boolean
    ) {
        private var compactOnly = false

        fun setTitle(value: String) {
            title.text = value
        }

        fun setCompactOnly(compact: Boolean) {
            compactOnly = compact
            update()
        }

        internal fun update() {
            if (compactOnly) {
                bar.visibility = View.VISIBLE
                bar.alpha = 1f
                bar.setBackgroundColor(UiKit.BG)
                title.alpha = 1f
                return
            }
            val fraction = Math.max(0f, Math.min(1f, (scroll.scrollY - dp(context, 65)) / dp(context, 58).toFloat()))
            if (hasBack) {
                bar.visibility = View.VISIBLE
                bar.alpha = 1f
                title.alpha = fraction
                bar.setBackgroundColor(
                    Color.argb(
                        Math.round(255 * fraction),
                        Color.red(UiKit.BG),
                        Color.green(UiKit.BG),
                        Color.blue(UiKit.BG)
                    )
                )
            } else {
                bar.alpha = fraction
                bar.visibility = if (fraction > 0.01f) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    fun collapsingBar(
        activity: Activity,
        frame: FrameLayout,
        scroll: ScrollView,
        title: String,
        back: View.OnClickListener?
    ): CollapsingHeader {
        val bar = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(UiKit.BG)
        }
        if (back != null) {
            val arrow = TextView(activity).apply {
                text = "‹"
                textSize = 32f
                setTextColor(UiKit.TEXT)
                gravity = Gravity.CENTER
                background = ripple(activity)
                setOnClickListener(back)
                contentDescription = "Back"
            }
            bar.addView(arrow, LinearLayout.LayoutParams(dp(activity, 56), dp(activity, 56)))
        }
        val small = TextView(activity).apply {
            text = title
            setTextColor(UiKit.TEXT)
            textSize = 20f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(if (back == null) dp(activity, 20) else dp(activity, 4), 0, dp(activity, 16), 0)
        }
        bar.addView(small, LinearLayout.LayoutParams(0, -1, 1f))
        val bp = FrameLayout.LayoutParams(-1, dp(activity, 56), Gravity.TOP)
        frame.addView(bar, bp)

        val header = CollapsingHeader(activity, small, bar, scroll, back != null)
        header.update()
        scroll.viewTreeObserver.addOnScrollChangedListener { header.update() }
        scroll.isVerticalScrollBarEnabled = false
        return header
    }

    fun enter(target: View?) {
        if (target == null || !target.isAttachedToWindow) return
        target.alpha = 0.75f
        target.translationY = dp(target.context, 8).toFloat()
        target.animate().alpha(1f).translationY(0f).setDuration(180)
            .setInterpolator(DecelerateInterpolator()).start()
    }
}
