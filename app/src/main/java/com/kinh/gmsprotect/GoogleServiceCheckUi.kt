package com.kinh.gmsprotect

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView

object GoogleServiceCheckUi {
    fun checkOnce(context: Context, row: LinearLayout, protectResult: GmsProtector.Result) {
        try {
            val gmsInMillet = MilletNoRestrict.containsGms(context)
            val playInMillet = MilletNoRestrict.containsPlayStore(context)
            val isProtected = (protectResult == GmsProtector.Result.SENT) || gmsInMillet

            // Cập nhật hiển thị dòng trạng thái ngoài màn hình chính động theo thực tế
            val subtitleView = findSubtitleView(row)
            if (subtitleView != null) {
                if (isProtected) {
                    subtitleView.text = context.getString(R.string.ui_status_verified)
                    subtitleView.setTextColor(UiKit.SUCCESS)
                } else {
                    subtitleView.text = context.getString(R.string.ui_status_missing)
                    subtitleView.setTextColor(UiKit.MUTED)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun findSubtitleView(row: LinearLayout): TextView? {
        try {
            // Duyệt tìm TextView phụ (mô tả trạng thái bên dưới tiêu đề trong row)
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
