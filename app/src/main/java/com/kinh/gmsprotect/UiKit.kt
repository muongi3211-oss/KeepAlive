package com.kinh.gmsprotect

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.TextView

object UiKit {
    const val PRIMARY = 0xFF50647F.toInt()
    const val PRIMARY_DARK = 0xFF35465E.toInt()
    const val PRIMARY_SOFT = 0xFFEEEFF7.toInt()
    const val BG = 0xFFF7F5FA.toInt()
    const val SURFACE = 0xFFF7F5FA.toInt()
    const val TEXT = 0xFF262832.toInt()
    const val MUTED = 0xFF686A76.toInt()
    const val BORDER = 0xFFE1DFE7.toInt()
    const val SUCCESS = 0xFF16875E.toInt()
    const val ERROR = 0xFFCA4A59.toInt()

    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    fun applyTopSystemInset(view: View) {
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        view.setOnApplyWindowInsetsListener { v, insets ->
            val insetTop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                insets.getInsets(WindowInsets.Type.statusBars()).top
            else
                insets.systemWindowInsetTop
            v.setPadding(left, top + insetTop, right, bottom)
            insets
        }
        view.requestApplyInsets()
    }

    fun applyBottomSystemInset(view: View) {
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        view.setOnApplyWindowInsetsListener { v, insets ->
            val insetBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            else
                insets.systemWindowInsetBottom
            v.setPadding(left, top, right, bottom + insetBottom)
            insets
        }
        view.requestApplyInsets()
    }

    fun rounded(color: Int, radiusDp: Int, context: Context): GradientDrawable {
        return rounded(color, radiusDp, context, null, 1)
    }

    fun rounded(
        color: Int,
        radiusDp: Int,
        context: Context,
        strokeColor: Int?,
        strokeDp: Int
    ): GradientDrawable {
        val out = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColor != null) {
                setStroke(dp(context, strokeDp), strokeColor)
            }
        }
        return out
    }

    fun muted(context: Context, text: String, size: Float): TextView {
        return TextView(context).apply {
            setText(text)
            textSize = Math.max(size, 12.5f)
            setTextColor(MUTED)
        }
    }

    fun primaryButton(context: Context, text: String, action: View.OnClickListener?): Button {
        return Button(context).apply {
            setText(text)
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rounded(PRIMARY, 14, context)
            minHeight = dp(context, 48)
            minimumHeight = dp(context, 48)
            stateListAnimator = null
            elevation = 0f
            setOnClickListener(action)
        }
    }

    fun secondaryButton(context: Context, text: String, action: View.OnClickListener?): Button {
        return Button(context).apply {
            setText(text)
            isAllCaps = false
            textSize = 14f
            setTextColor(PRIMARY)
            background = rounded(PRIMARY_SOFT, 12, context, 0xFFD8D5F2.toInt(), 1)
            minHeight = dp(context, 48)
            minimumHeight = dp(context, 48)
            stateListAnimator = null
            elevation = 0f
            setOnClickListener(action)
        }
    }
}
