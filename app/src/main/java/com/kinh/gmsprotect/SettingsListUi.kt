package com.hanh.gmsprotect

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object SettingsListUi {
    const val GOOD = 1
    const val BAD = -1
    const val NEUTRAL = 0
    const val INFO = 2

    private fun dp(c: Context, x: Int): Int = UiKit.dp(c, x)

    fun heading(c: Context, value: String): TextView {
        return TextView(c).apply {
            text = value
            setTextColor(UiKit.MUTED)
            textSize = 14.5f
            setPadding(dp(c, 70), dp(c, 24), dp(c, 12), dp(c, 7))
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    fun group(c: Context): LinearLayout {
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiKit.BG)
        }
    }

    fun divider(c: Context, box: LinearLayout) {
        if (box.childCount == 0) return
        val line = View(c).apply { setBackgroundColor(UiKit.BORDER) }
        val lp = LinearLayout.LayoutParams(-1, dp(c, 1)).apply { topMargin = dp(c, 1) }
        box.addView(line, lp)
    }

    fun row(
        c: Context,
        group: LinearLayout,
        title: String,
        subtitle: String?,
        status: String?,
        kind: Int,
        action: View.OnClickListener?
    ): LinearLayout {
        return row(c, group, 0, title, subtitle, status, kind, action)
    }

    fun row(
        c: Context,
        group: LinearLayout,
        iconRes: Int,
        title: String,
        subtitle: String?,
        status: String?,
        kind: Int,
        action: View.OnClickListener?
    ): LinearLayout {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 12), dp(c, 10), dp(c, 14), dp(c, 10))
            minimumHeight = dp(c, if (subtitle == null && status == null) 58 else 72)
        }
        if (iconRes != 0) {
            val icon = ImageView(c).apply {
                setImageResource(iconRes)
                setColorFilter(UiKit.MUTED)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val ilp = LinearLayout.LayoutParams(dp(c, 25), dp(c, 25)).apply {
                leftMargin = dp(c, 4)
                rightMargin = dp(c, 27)
            }
            row.addView(icon, ilp)
        } else {
            val spacer = View(c)
            row.addView(spacer, LinearLayout.LayoutParams(dp(c, 56), 1))
        }

        val texts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val name = TextView(c).apply {
            text = title
            setTextColor(UiKit.TEXT)
            textSize = 16f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        texts.addView(name, LinearLayout.LayoutParams(-1, -2))

        if (!subtitle.isNullOrEmpty()) {
            val note = TextView(c).apply {
                text = subtitle
                setTextColor(if (kind == BAD) UiKit.ERROR else UiKit.MUTED)
                textSize = 13.5f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(c, 3), 0, 0)
            }
            texts.addView(note, LinearLayout.LayoutParams(-1, -2))
        }

        if (!status.isNullOrEmpty() && subtitle == null) {
            val value = TextView(c).apply {
                text = status
                textSize = 13.5f
                setTextColor(tint(kind))
                setPadding(0, dp(c, 3), 0, 0)
                maxLines = 3
            }
            texts.addView(value, LinearLayout.LayoutParams(-1, -2))
        }

        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        if (!status.isNullOrEmpty() && subtitle != null) {
            val value = TextView(c).apply {
                text = status
                textSize = 12.5f
                setTextColor(tint(kind))
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 2
            }
            val vlp = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(c, 8) }
            row.addView(value, vlp)
        }

        if (action != null) {
            row.background = SettingsMotion.ripple(c)
            row.setOnClickListener(action)
            row.isClickable = true
            row.isFocusable = true
        }
        group.addView(row, LinearLayout.LayoutParams(-1, -2))
        return row
    }

    fun tint(kind: Int): Int {
        return when (kind) {
            GOOD -> UiKit.SUCCESS
            BAD -> UiKit.ERROR
            INFO -> UiKit.PRIMARY
            else -> UiKit.MUTED
        }
    }

    fun separator(c: Context): View {
        return View(c).apply { setBackgroundColor(UiKit.BORDER) }
    }
}
