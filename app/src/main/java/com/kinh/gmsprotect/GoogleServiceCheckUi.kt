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

/**
 * A one-shot, UI-only verification animation for the Google Play Services row.
 * This class never edits settings, sends MiPush, schedules work, or checks FCM
 * connectivity. The actual protect/ensure work remains in the existing core.
 */
object GoogleServiceCheckUi {
    private const val MIN_VISIBLE_SPINNER_MS = 900L

    /** Runs once when the main Settings screen is constructed. */
    fun checkOnce(
        activity: Activity, row: LinearLayout,
        requestResult: GmsProtector.Result
    ) {
        // row(icon, textColumn) is built in SettingsListUi with nonempty subtitle.
        val textColumn = row.getChildAt(1) as LinearLayout
        val subtitle = textColumn.getChildAt(1) as TextView
        subtitle.setText(R.string.settings14_checking)
        subtitle.setTextColor(UiKit.MUTED)

        // Replays must replace the previous indicator rather than append a new one.
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
        // Per-run token: a stale verification result must not replace a newer one.
        row.tag = spinner

        val app = activity.applicationContext
        val started = SystemClock.uptimeMillis()
        val main = Handler(Looper.getMainLooper())
        // Read Settings.System and scheduled-job state off the UI thread so
        // animations do not block when system_server is busy.
        Thread({
            val outcome = readOutcome(app, requestResult)
            val wait = Math.max(
                0L, MIN_VISIBLE_SPINNER_MS
                        - (SystemClock.uptimeMillis() - started)
            )
            main.postDelayed({
                // A navigation or Activity recreation can detach the old row.
                if (activity.isFinishing()
                    || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())
                    || !row.isAttachedToWindow() || row.tag !== spinner
                ) return@postDelayed
                render(activity, row, spinner, subtitle, outcome)
            }, wait)
        }, "GMS-UI-Check").start()
    }

    private fun readOutcome(app: Context, requestResult: GmsProtector.Result): Outcome {
        if (requestResult == GmsProtector.Result.GMS_MISSING) return Outcome.GMS_MISSING
        return try {
            // Every green indicator is grounded in a directly readable system value.
            val millet = MilletNoRestrict.containsGms(app)
                    && MilletNoRestrict.containsPlayStore(app)
                    && MilletNoRestrict.containsSelf(app)
            val watch = !MilletWatchScheduler.isSupported()
                    || MilletWatchScheduler.isScheduled(app)
            var game = true
            if (GameWatchCore.isEnabled(app)) {
                val raw = GameWatchCore.raw(app)
                game = GameWatchCore.contains(raw, GameWatchCore.GMS)
                        && GameWatchCore.contains(raw, app.packageName)
                        && GameWatchScheduler.isPending(app)
            }
            if (!millet || !watch || !game) return Outcome.ATTENTION
            // SENT only means the MiPush protection request was dispatched.
            if (requestResult == GmsProtector.Result.SENT) Outcome.VERIFIED_REQUEST_SENT else Outcome.REQUEST_FAILED
        } catch (ignored: Throwable) {
            // Unknown readings are not treated as success.
            Outcome.ATTENTION
        }
    }

    private fun render(
        activity: Activity, row: LinearLayout, spinner: View,
        subtitle: TextView, result: Outcome
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
            setImageResource(
                if (ok) R.drawable.sg_ic_verified_check
                else R.drawable.sg_ic_check_attention
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = activity.getString(
                if (ok)
                    R.string.settings14_check_verified_a11y
                else
                    R.string.settings14_check_attention_a11y
            )
        }
        val lp = LinearLayout.LayoutParams(
            UiKit.dp(activity, 24), UiKit.dp(activity, 24)
        ).apply {
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

    private enum class Outcome {
        VERIFIED_REQUEST_SENT, REQUEST_FAILED, ATTENTION, GMS_MISSING
    }
}
