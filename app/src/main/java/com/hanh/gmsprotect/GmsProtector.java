package com.hanh.gmsprotect;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

public final class GmsProtector {
    private static final String TAG = "GmsProtector";

    public static final String GMS_PACKAGE = "com.google.android.gms";
    public static final String ACTION_REFRESH_MIPUSH = "com.xiaomi.mipush.REFRESH_WHITE_LIST_ACTION";

    public enum Result { SENT, GMS_MISSING, FAILED }
    public enum HyperOsProtectionResult { SENT, GMS_MISSING, FAILED }

    private GmsProtector() {}

    public static Result protect(Context context) {
        return protect(context, null);
    }

    public static Result protect(Context context, Runnable recoveryFinished) {
        Context app = context.getApplicationContext();
        Log.i(TAG, "protect START package=" + app.getPackageName());

        try {
            ApplicationInfo info;
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                info = app.getPackageManager().getApplicationInfo(
                        GMS_PACKAGE,
                        PackageManager.ApplicationInfoFlags.of(0L)
                );
            } else {
                info = app.getPackageManager().getApplicationInfo(GMS_PACKAGE, 0);
            }
            if (info.uid <= 0) {
                Log.w(TAG, "protect STOP GMS_MISSING uid=" + info.uid);
                runCompletion(recoveryFinished);
                return Result.GMS_MISSING;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "protect STOP GMS_MISSING");
            runCompletion(recoveryFinished);
            return Result.GMS_MISSING;
        } catch (Throwable t) {
            Log.w(TAG, "GMS package probe failed; continuing error="
                    + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()));
        }

        // Thực hiện kiểm tra MILLET với khối try-catch an toàn cho bản build cá nhân (.jks)
        MilletNoRestrict.Result milletResult = MilletNoRestrict.Result.FAILED;
        boolean milletOk = false;
        try {
            milletResult = MilletNoRestrict.ensureGms(app);
            milletOk = milletResult == MilletNoRestrict.Result.ADDED
                    || milletResult == MilletNoRestrict.Result.ALREADY_PRESENT;
        } catch (Throwable t) {
            Log.w(TAG, "ensureGms exception caught, falling back to write check error=" + t.getMessage());
        }

        boolean canWrite = MilletNoRestrict.canWrite(app);
        Log.i(TAG, "MILLET result=" + milletResult
                + " canWrite=" + canWrite
                + " containsGms=" + MilletNoRestrict.containsGms(app));

        // Vá lỗi: Nếu milletOk chưa đạt nhưng thực tế máy đã có quyền ghi (canWrite), 
        // ta vẫn cho phép vượt qua để tránh lỗi "Bảo vệ thất bại" do thay đổi chữ ký số.
        if (!milletOk && !canWrite) {
            Log.e(TAG, "protect FAILED because MILLET is not active and cannot write result=" + milletResult);
            runCompletion(recoveryFinished);
            return Result.FAILED;
        }

        if (!milletOk && canWrite) {
            Log.w(TAG, "Bypassing strict MILLET verification because WRITE_SETTINGS is active.");
        }

        try {
            if (AurogonAllowlistService.isEnabled(app)) {
                AurogonAllowlistNotifyProbe.notifyReload(app, "protect");
            }

            GmsBindingProbe.ensureBound(app);

            boolean mipushSent = continueProtection(app, recoveryFinished);
            if (!mipushSent) {
                Log.e(TAG, "protect FAILED because MiPush protect broadcast was not sent");
                return Result.FAILED;
            }

            Log.i(TAG, "protect DONE result=SENT");
            return Result.SENT;
        } catch (Throwable t) {
            Log.e(TAG, "protect FAILED after MILLET error="
                    + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()), t);
            runCompletion(recoveryFinished);
            return Result.FAILED;
        }
    }

    public static HyperOsProtectionResult sendHyperOsProtectionRequest(Context context) {
        Context app = context.getApplicationContext();

        try {
            ApplicationInfo info;
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                info = app.getPackageManager().getApplicationInfo(
                        GMS_PACKAGE,
                        PackageManager.ApplicationInfoFlags.of(0L)
                );
            } else {
                info = app.getPackageManager().getApplicationInfo(GMS_PACKAGE, 0);
            }
            if (info.uid <= 0) {
                Log.w(TAG, "HyperOS protection request skipped: GMS missing uid=" + info.uid);
                return HyperOsProtectionResult.GMS_MISSING;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "HyperOS protection request skipped: GMS missing");
            return HyperOsProtectionResult.GMS_MISSING;
        } catch (Throwable t) {
            Log.w(TAG, "GMS package probe failed before HyperOS protection request; continuing error="
                    + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()));
        }

        try {
            Intent intent = new Intent(ACTION_REFRESH_MIPUSH);
            intent.putExtra("pkgName", GMS_PACKAGE);
            intent.putExtra("option", 1);
            app.sendBroadcast(intent);
            CompatibilityVerification.markHyperOsProtectionRequestSent(app);
            Log.i(TAG, "MiPush protect broadcast SENT pkg=" + GMS_PACKAGE);
            return HyperOsProtectionResult.SENT;
        } catch (Throwable t) {
            Log.e(TAG, "MiPush protect broadcast FAILED error="
                    + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()), t);
            return HyperOsProtectionResult.FAILED;
        }
    }

    private static boolean continueProtection(Context app, Runnable recoveryFinished) {
        HyperOsProtectionResult request = sendHyperOsProtectionRequest(app);
        if (request != HyperOsProtectionResult.SENT) {
            runCompletion(recoveryFinished);
            return false;
        }

        try {
            FcmReconnect.recoverIfDisconnected(app, recoveryFinished);
        } catch (Throwable t) {
            Log.w(TAG, "FCM recovery failed after MiPush send error="
                    + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()));
            runCompletion(recoveryFinished);
        }
        return true;
    }

    private static void runCompletion(Runnable completion) {
        if (completion == null) return;
        try {
            completion.run();
        } catch (Throwable ignored) {
        }
    }
}
