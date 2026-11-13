package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object GmsProtector {
    private const val TAG = "GmsProtector"

    const val GMS_PACKAGE = "com.google.android.gms"
    // Đã loại bỏ ACTION_REFRESH_MIPUSH cũ vì Xiaomi không còn sử dụng/đáp ứng nó.

    enum class Result {
        SENT, GMS_MISSING, FAILED
    }

    enum class HyperOsProtectionResult {
        SENT, GMS_MISSING, FAILED
    }

    fun protect(context: Context): Result {
        return protect(context, null)
    }

    fun protect(context: Context, recoveryFinished: Runnable?): Result {
        val app = context.applicationContext
        Log.i(TAG, "protect START package=" + app.packageName)

        try {
            val info: ApplicationInfo = if (Build.VERSION.SDK_INT >= 33) {
                app.packageManager.getApplicationInfo(
                    GMS_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                app.packageManager.getApplicationInfo(GMS_PACKAGE, 0)
            }
            if (info.uid <= 0) {
                Log.w(TAG, "protect STOP GMS_MISSING uid=" + info.uid)
                runCompletion(recoveryFinished)
                return Result.GMS_MISSING
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "protect STOP GMS_MISSING")
            runCompletion(recoveryFinished)
            return Result.GMS_MISSING
        } catch (t: Throwable) {
            // Package visibility is declared in the manifest; continue best-effort.
            Log.w(
                TAG, "GMS package probe failed; continuing error="
                        + t.javaClass.simpleName + ":" + t.message
            )
        }

        // IMPORTANT: MILLET is the primary gate against HyperOS' special GMS
        // quick-freeze path. Do this first and do not report the protection as
        // successful when WRITE_SETTINGS was granted but the setting could not
        // actually be written/verified.
        val milletResult = MilletNoRestrict.ensureGms(app)
        val milletOk = milletResult == MilletNoRestrict.Result.ADDED
                || milletResult == MilletNoRestrict.Result.ALREADY_PRESENT

        Log.i(
            TAG, "MILLET result=$milletResult"
                    + " canWrite=" + MilletNoRestrict.canWrite(app)
                    + " containsGms=" + MilletNoRestrict.containsGms(app)
        )
        if (milletResult == MilletNoRestrict.Result.ADDED) {
            GameWatchJournal.event(app, "millet_repaired", "GmsProtector.protect: $milletResult")
        } else if (!milletOk) {
            GameWatchJournal.event(app, "millet_error", "GmsProtector.protect: $milletResult")
        }

                if (!milletOk) {
            // VÁ LỖI: Dù MILLET bị HyperOS lọc, VẪN PHẢI ĐI TIẾP để bảo vệ Game Allowlist và MiPush!
            Log.w(TAG, "MILLET verify failed (HyperOS filters Google apps). Continuing to next layers...")
            // (Đã xóa lệnh return Result.FAILED)
        }

        // Always request the Game Allowlist on supported ROMs as another layer.
        // Its failure must not alter the established MILLET + MiPush success path.
        try {
            if (GameWatchCore.isEnabled(app)) {
                GameWatchCore.ensureTargets(app, "GMS_PROTECT")
                GameWatchScheduler.ensure(app, "GMS_PROTECT")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Game Allowlist auto-maintenance unavailable", t)
        }

        return try {
            // Experimental binding layer kept unchanged.
            GmsBindingProbe.ensureBound(app)

            val mipushSent = continueProtection(app, recoveryFinished)
            if (!mipushSent) {
                Log.e(TAG, "protect FAILED because MiPush protect broadcast was not sent")
                return Result.FAILED
            }

            Log.i(TAG, "protect DONE result=SENT")
            Result.SENT
        } catch (t: Throwable) {
            Log.e(
                TAG, "protect FAILED after MILLET error="
                        + t.javaClass.simpleName + ":" + t.message, t
            )
            runCompletion(recoveryFinished)
            Result.FAILED
        }
    }

    /** 
     * NÚT THẮT SỐ 2: Gửi GMS vào List MiPush Protect ẩn.
     * Dựa theo phát hiện ngách siêu nhỏ của Xiaomi Framework để chống đóng băng 
     * khi tắt màn hình, bắn thẳng broadcast vào com.miui.securitycenter.
     */
    fun sendHyperOsProtectionRequest(context: Context): HyperOsProtectionResult {
        val app = context.applicationContext

        try {
            val info: ApplicationInfo = if (Build.VERSION.SDK_INT >= 33) {
                app.packageManager.getApplicationInfo(
                    GMS_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(0L)
                )
            } else {
                app.packageManager.getApplicationInfo(GMS_PACKAGE, 0)
            }
            if (info.uid <= 0) {
                Log.w(TAG, "HyperOS protection request skipped: GMS missing uid=" + info.uid)
                return HyperOsProtectionResult.GMS_MISSING
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "HyperOS protection request skipped: GMS missing")
            return HyperOsProtectionResult.GMS_MISSING
        } catch (t: Throwable) {
            Log.w(
                TAG, "GMS package probe failed before HyperOS protection request; continuing error="
                        + t.javaClass.simpleName + ":" + t.message
            )
        }

        return try {
            // Nhắm thẳng vào ngách Xiaomi Framework (Trung tâm bảo mật)
            val intent = Intent("com.xiaomi.mipush.RECEIVE_MESSAGE").apply {
                setPackage("com.miui.securitycenter")
                putExtra("message_type", "push_whitelist")
                putExtra("package_name", GMS_PACKAGE)
            }
            
            app.sendBroadcast(intent)
            
            CompatibilityVerification.markHyperOsProtectionRequestSent(app)
            Log.i(TAG, "MiPush hidden protect broadcast SENT pkg=$GMS_PACKAGE")
            GameWatchJournal.event(
                app, "mipush_requested", "Broadcast SENT to List MiPush Protect pkg=$GMS_PACKAGE"
            )
            HyperOsProtectionResult.SENT
        } catch (t: Throwable) {
            Log.e(
                TAG, "MiPush hidden protect broadcast FAILED error="
                        + t.javaClass.simpleName + ":" + t.message, t
            )
            GameWatchJournal.event(app, "mipush_error", "Broadcast FAILED: $t")
            HyperOsProtectionResult.FAILED
        }
    }

    private fun continueProtection(app: Context, recoveryFinished: Runnable?): Boolean {
        val request = sendHyperOsProtectionRequest(app)
        if (request != HyperOsProtectionResult.SENT) {
            runCompletion(recoveryFinished)
            return false
        }

        // FCM recovery is best-effort. A failure here must not turn a
        // successfully sent MiPush/Greezer protection request into FAILED.
        try {
            FcmReconnect.recoverIfDisconnected(app, recoveryFinished)
        } catch (t: Throwable) {
            Log.w(
                TAG, "FCM recovery failed after MiPush send error="
                        + t.javaClass.simpleName + ":" + t.message
            )
            runCompletion(recoveryFinished)
        }
        return true
    }

    private fun runCompletion(completion: Runnable?) {
        if (completion == null) return
        try {
            completion.run()
        } catch (ignored: Throwable) {
        }
    }
}
