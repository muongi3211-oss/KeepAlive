package com.kinh.gmsprotect

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object UiKit {
    const val PRIMARY = 0xFF3F3D9B.toInt()
    const val PRIMARY_DARK = 0xFF302E78.toInt()
    const val PRIMARY_SOFT = 0xFFECEBFF.toInt()
    const val BG = 0xFFF6F6FB.toInt()
    const val SURFACE = 0xFFFFFFFF.toInt()
    const val TEXT = 0xFF202033.toInt()
    const val MUTED = 0xFF77778B.toInt()
    const val BORDER = 0xFFE7E7F0.toInt()
    const val SUCCESS = 0xFF28A96B.toInt()
    const val SUCCESS_SOFT = 0xFFE7F7EF.toInt()
    const val ERROR = 0xFFE45A62.toInt()
    const val ERROR_SOFT = 0xFFFFECEE.toInt()

    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    fun rounded(color: Int, radiusDp: Int, context: Context, strokeColor: Int? = null, strokeDp: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColor != null) {
                setStroke(dp(context, strokeDp), strokeColor)
            }
        }
    }

    fun muted(context: Context, text: String, size: Float): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(MUTED)
        }
    }

    fun primaryButton(context: Context, text: String, action: android.view.View.OnClickListener): Button {
        return Button(context).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            background = rounded(PRIMARY, 14, context)
            minHeight = dp(context, 50)
            setOnClickListener(action)
        }
    }

    fun secondaryButton(context: Context, text: String, action: android.view.View.OnClickListener): Button {
        return Button(context).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(PRIMARY)
            background = rounded(PRIMARY_SOFT, 12, context, 0xFFD8D5F2.toInt(), 1)
            minHeight = dp(context, 46)
            setOnClickListener(action)
        }
    }

    fun card(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
            background = rounded(SURFACE, 20, context, BORDER, 1)
        }
    }
}
