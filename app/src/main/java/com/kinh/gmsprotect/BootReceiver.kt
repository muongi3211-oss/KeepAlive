package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED == action
            || Intent.ACTION_BOOT_COMPLETED == action
            || Intent.ACTION_USER_UNLOCKED == action
            || Intent.ACTION_MY_PACKAGE_REPLACED == action
        ) {
            // Dùng goAsync() để xin hệ thống thêm thời gian xử lý mà không block giao diện
            val pending = goAsync()
            
            // Chuyển toàn bộ tác vụ xuống luồng nền (Background Thread)
            Thread {
                try {
                    // 1. Phục hồi cấu hình danh sách ứng dụng do người dùng cài đặt
                    if (Intent.ACTION_LOCKED_BOOT_COMPLETED != action) {
                        if (GameWatchCore.isEnabled(context)) {
                            val targetsReady = GameWatchCore.ensureTargets(context, "BOOT_$action")
                            val jobReady = GameWatchScheduler.ensure(context, "BOOT_$action")
                            if (Intent.ACTION_BOOT_COMPLETED == action || Intent.ACTION_USER_UNLOCKED == action) {
                                GameWatchEvidence.noteBoot(context, targetsReady && jobReady)
                            }
                        }
                        try {
                            AdvancedAppProtector.maintain(context, "BOOT_$action")
                            if (GameWatchScheduler.shouldWatch(context)) {
                                GameWatchScheduler.ensure(context, "BOOT_ADV_$action")
                            }
                        } catch (ignored: Throwable) {
                        }
                        
                        // Dọn dẹp rác (hủy các alarm cũ nếu có trên bản cập nhật)
                        LegacyBackgroundCleanup.runOnce(context)
                    }

                    // 2. VÁ LỖI CỐT LÕI (NÚT THẮT 1 & 2) CHO GMS
                    MilletNoRestrict.ensureGms(context)
                    GmsProtector.sendHyperOsProtectionRequest(context)

                    // 3. ĐẶT BẪY SỰ KIỆN VÀ CHO APP NGỦ
                    // Khởi tạo JobScheduler lắng nghe sự thay đổi của Settings.System.
                    // Tuyệt đối không có startForegroundService ở đây.
                    MilletWatchScheduler.schedule(context)

                } catch (t: Throwable) {
                    // Bỏ qua lỗi, đảm bảo điện thoại khởi động an toàn
                } finally {
                    // Trả lại tài nguyên, hệ thống sẽ đưa app vào trạng thái ngủ đông
                    pending.finish()
                }
            }.start()
        }
    }
}
