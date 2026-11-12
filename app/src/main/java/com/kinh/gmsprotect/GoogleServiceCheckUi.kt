package com.kinh.gmsprotect

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView

object GoogleServiceCheckUi {
    fun checkOnce(context: Context, row: LinearLayout, protectResult: GmsProtector.Result) {
        try {
            // Kiểm tra trạng thái đã được xác nhận/kích hoạt thủ công từ trước hoặc gửi broadcast thành công
            val prefs = context.getSharedPreferences("setup_v1", Context.MODE_PRIVATE)
            val manuallyOptimized = prefs.getBoolean("manual_gms_optimized", false)
            
            val gmsInMillet = MilletNoRestrict.containsGms(context)
            val playInMillet = MilletNoRestrict.containsPlayStore(context)
            
            // Khớp tuyệt đối với bản gốc: Chỉ cần đã kích hoạt thủ công, hoặc gửi MiPush thành công, hoặc có trong Millet
            val isProtected = (protectResult == GmsProtector.Result.SENT) || gmsInMillet || playInMillet || manuallyOptimized

            val subtitleView = findSubtitleView(row)
            if (subtitleView != null) {
                if (isProtected) {
                    subtitleView.text = context.getString(R.string.ui_status_verified) // Đã tối ưu / Đã xác thực
                    subtitleView.setTextColor(UiKit.SUCCESS)
                } else {
                    subtitleView.text = context.getString(R.string.ui_status_missing) // Chưa tối ưu
                    subtitleView.setTextColor(UiKit.MUTED)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun findSubtitleView(row: LinearLayout): TextView? {
        try {
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i)
                if (child is LinearLayout) {
                    for (j in 0 until child.childCount) {
                        val subChild = child.getChildAt(j)
                        if (subChild is TextView && subChild.textSize < 14f) {
                            return subChild
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return null
    }
}
