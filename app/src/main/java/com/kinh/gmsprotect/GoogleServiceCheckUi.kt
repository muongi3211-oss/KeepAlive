package com.kinh.gmsprotect

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

object GoogleServiceCheckUi {
    private const val MIN_VISIBLE_SPINNER_MS = 900L

    enum class Outcome { VERIFIED_REQUEST_SENT, REQUEST_FAILED, ATTENTION, GMS_MISSING }

    fun checkOnce(
        activity: Activity,
        row: LinearLayout,
        requestResult: GmsProtector.Result
    ) {
        val textColumn = row.getChildAt(1) as LinearLayout
        val subtitle = textColumn.getChildAt(1) as TextView
        subtitle.setText(R.string.settings14_checking)
        subtitle.setTextColor(UiKit.MUTED)

        val previous = if (row.tag is View) row.tag as View else null
        if (previous != null && previous.parent === row) row.removeView(previous)
        val size = UiKit.dp(activity, 24)
        val spinner = ProgressBar(activity, null, android.R.attr.progressBarStyleSmall).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(UiKit.PRIMARY)
            contentDescription = activity.getString(R.string.settings14_checking)
        }
        val spinnerLp = LinearLayout.LayoutParams(size, size).apply {
            leftMargin = UiKit.dp(activity, 12)
            rightMargin = UiKit.dp(activity, 8)
        }
        row.addView(spinner, spinnerLp)
        row.tag = spinner

        val app = activity.applicationContext
        val started = SystemClock.uptimeMillis()
        val main = Handler(Looper.getMainLooper())
        
        // Quét trạng thái thực tế của thiết bị trên luồng nền để tránh đơ giao diện
        Thread({
            val outcome = readRealDeviceOutcome(app, requestResult)
            val wait = Math.max(0L, MIN_VISIBLE_SPINNER_MS - (SystemClock.uptimeMillis() - started))
            main.post {
                if (activity.isFinishing ||
                    (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed) ||
                    !row.isAttachedToWindow || row.tag !== spinner
                ) return@post
                render(activity, row, spinner, subtitle, outcome)
            }
        }, "GMS-Realtime-Check").start()
    }

    private fun readRealDeviceOutcome(app: Context, requestResult: GmsProtector.Result): Outcome {
        if (requestResult == GmsProtector.Result.GMS_MISSING) return Outcome.GMS_MISSING
        return try {
            // Kiểm tra thực tế xem GMS có thực sự tồn tại trong hệ thống máy không
            val gmsInstalled = try {
                app.packageManager.getPackageInfo(GameWatchCore.GMS, 0)
                true
            } catch (_: Throwable) {
                false
            }
            if (!gmsInstalled) return Outcome.GMS_MISSING

            // Đọc thực tế từ chuỗi cài đặt hệ thống Settings.System (millet_no_restrict_app)
            val milletGms = MilletNoRestrict.containsGms(app)
            val milletPlay = MilletNoRestrict.containsPlayStore(app)
            val milletSelf = MilletNoRestrict.containsSelf(app)
            
            // Kiểm tra quyền ghi hệ thống thực tế
            val canWrite = MilletNoRestrict.canWrite(app)

            // Nếu chưa được ghi vào danh sách trắng thực tế hoặc chưa cấp quyền -> Báo Attention (Chưa tối ưu)
            if (!canWrite || !milletGms || !milletPlay || !milletSelf) {
                return Outcome.ATTENTION
            }

            // Nếu đã khớp hoàn toàn trạng thái thực tế trên hệ thống
            if (requestResult == GmsProtector.Result.SENT) {
                Outcome.VERIFIED_REQUEST_SENT
            } else {
                Outcome.REQUEST_FAILED
            }
        } catch (_: Throwable) {
            Outcome.ATTENTION
        }
    }

    private fun render(
        activity: Activity,
        row: LinearLayout,
        spinner: View,
        subtitle: TextView,
        result: Outcome
    ) {
        row.removeView(spinner)
        val ok = result == Outcome.VERIFIED_REQUEST_SENT
        val textRes = when (result) {
            Outcome.VERIFIED_REQUEST_SENT -> R.string.settings14_verified
            Outcome.GMS_MISSING -> R.string.gms_missing
            Outcome.REQUEST_FAILED -> R.string.settings14_request_failed
            else -> R.string.settings14_attention
        }
        subtitle.setText(textRes)
        subtitle.setTextColor(if (ok) UiKit.MUTED else UiKit.ERROR)
        
        val image = ImageView(activity).apply {
            setImageResource(if (ok) R.drawable.sg_ic_verified_check else R.drawable.sg_ic_check_attention)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = activity.getString(if (ok) R.string.settings14_check_verified_a11y else R.string.settings14_check_attention_a11y)
        }
        val lp = LinearLayout.LayoutParams(UiKit.dp(activity, 24), UiKit.dp(activity, 24)).apply {
            leftMargin = UiKit.dp(activity, 12)
            rightMargin = UiKit.dp(activity, 8)
        }
        row.addView(image, lp)
        row.tag = image
        image.alpha = 0f
        image.scaleX = 0.65f
        image.scaleY = 0.65f
        image.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260L)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
    }
}
