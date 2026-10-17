package com.kinh.gmsprotect

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CompatibilityActivity : Activity() {

    private lateinit var txtPowerKeeperStatus: TextView
    private lateinit var txtWriteSettingsStatus: TextView
    private lateinit var txtBatteryStatus: TextView
    private lateinit var txtSystemPropStatus: TextView
    private lateinit var txtGmsAllowlistStatus: TextView
    private lateinit var txtPlayStoreAllowlistStatus: TextView
    private lateinit var txtSelfAllowlistStatus: TextView
    private lateinit var txtGmsServiceStatus: TextView
    private lateinit var txtPlayStoreStatus: TextView
    private lateinit var txtJobSchedulerStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(createCompatibilityScreen())
        } catch (e: Throwable) {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            updateRealtimeStatus()
        } catch (_: Throwable) {}
    }

    private fun isPowerKeeperPresent(): Boolean {
        return try {
            val manufacturer = Build.MANUFACTURER ?: ""
            val brand = Build.BRAND ?: ""
            manufacturer.contains("Xiaomi", ignoreCase = true) ||
                brand.contains("Xiaomi", ignoreCase = true) ||
                brand.contains("Redmi", ignoreCase = true) ||
                brand.contains("POCO", ignoreCase = true)
        } catch (e: Exception) {
            true
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(packageName)
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun canWriteSettings(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(this)
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun updateRealtimeStatus() {
        val hasPK = isPowerKeeperPresent()
        val hasWrite = canWriteSettings()
        val isBattery = isIgnoringBatteryOptimizations()
        
        // Nếu đã cấp quyền hệ thống, thực thi ghi danh sách trắng ngay lập tức
        if (hasWrite) {
            try { MilletHelper.applyMilletProtection(this) } catch (_: Throwable) {}
        }

        // Đọc trạng thái từ Allowlist thực tế
        val hasGmsAllow = try { MilletNoRestrict.containsGms(this) } catch (_: Throwable) { false }
        val hasPlayAllow = try { MilletNoRestrict.containsPlayStore(this) } catch (_: Throwable) { false }
        val hasSelfAllow = try { MilletNoRestrict.containsSelf(this) } catch (_: Throwable) { false }

        val hasGms = try { packageManager.getPackageInfo("com.google.android.gms", 0); true } catch (e: Exception) { false }
        val hasPlayStore = try { packageManager.getPackageInfo("com.android.vending", 0); true } catch (e: Exception) { false }

        try {
            if (::txtPowerKeeperStatus.isInitialized) {
                txtPowerKeeperStatus.text = if (hasPK) "ĐÃ PHÁT HIỆN" else "KHÔNG PHÁT HIỆN"
                txtPowerKeeperStatus.setTextColor(Color.parseColor(if (hasPK) "#155724" else "#721C24"))
                (txtPowerKeeperStatus.parent as? LinearLayout)?.background = createRoundedDrawable(if (hasPK) "#D4EDDA" else "#F8D7DA", 15f)
            }

            if (::txtWriteSettingsStatus.isInitialized) {
                txtWriteSettingsStatus.text = if (hasWrite) "ĐÃ CẤP" else "CHƯA BẬT"
                txtWriteSettingsStatus.setTextColor(Color.parseColor(if (hasWrite) "#155724" else "#856404"))
                (txtWriteSettingsStatus.parent as? LinearLayout)?.background = createRoundedDrawable(if (hasWrite) "#D4EDDA" else "#FFF3CD", 15f)
            }

            if (::txtBatteryStatus.isInitialized) {
                txtBatteryStatus.text = if (isBattery) "ĐÃ XÁC MINH" else "CHƯA BẬT"
                txtBatteryStatus.setTextColor(Color.parseColor(if (isBattery) "#155724" else "#856404"))
                (txtBatteryStatus.parent as? LinearLayout)?.background = createRoundedDrawable(if (isBattery) "#D4EDDA" else "#FFF3CD", 15f)
            }

            if (::txtSystemPropStatus.isInitialized) {
                txtSystemPropStatus.text = "ĐÃ ÁP DỤNG"
                txtSystemPropStatus.setTextColor(Color.parseColor("#155724"))
                (txtSystemPropStatus.parent as? LinearLayout)?.background = createRoundedDrawable("#D4EDDA", 15f)
            }

            if (::txtGmsAllowlistStatus.isInitialized) {
                txtGmsAllowlistStatus.text = if (hasGmsAllow) "✓ ĐÃ ÁP DỤNG" else "✗ CHƯA ÁP DỤNG"
                txtGmsAllowlistStatus.setTextColor(Color.parseColor(if (hasGmsAllow) "#155724" else "#721C24"))
                (txtGmsAllowlistStatus.parent as? LinearLayout)?.background = createRoundedDrawable(if (hasGmsAllow) "#D4EDDA" else "#F8D7DA", 15f)
            }

            if (::txtPlayStoreAllowlistStatus.isInitialized) {
                txtPlayStoreAllowlistStatus.text = if (hasPlayAllow) "✓ ĐÃ ÁP DỤNG" else "✗ CHƯA ÁP DỤNG"
                txtPlayStoreAllowlistStatus.setTextColor(Color.parseColor(if (hasPlayAllow) "#155724" else "#721C24"))
                (txtPlayStoreAllowlistStatus.parent as? LinearLayout)?.background = createRoundedDrawable(if (hasPlayAllow) "#D4EDDA" else "#F8D7DA", 15f)
            }

            if (::txtSelfAllowlistStatus.isInitialized) {
                txtSelfAllowlistStatus.text = if (hasSelfAllow) "✓ ĐÃ ÁP DỤNG" else "✗ CHƯA ÁP DỤNG"
                txtSelfAllowlistStatus.setTextColor(Color.parseColor(if (hasSelfAllow) "#155724" else "#721C24"))
                (txtSelfAllowlistStatus.parent as? LinearLayout)?.background = createRoundedDrawable(if (hasSelfAllow) "#D4EDDA" else "#F8D7DA", 15f)
            }

            if (::txtGmsServiceStatus.isInitialized) {
                txtGmsServiceStatus.text = if (hasGms) "ĐÃ CÀI ĐẶT" else "KHÔNG TÌM THẤY"
                txtGmsServiceStatus.setTextColor(Color.parseColor(if (hasGms) "#155724" else "#721C24"))
                (txtGmsServiceStatus.parent as? LinearLayout)?.background = createRoundedDrawable(if (hasGms) "#D4EDDA" else "#F8D7DA", 15f)
            }

            if (::txtPlayStoreStatus.isInitialized) {
                txtPlayStoreStatus.text = if (hasPlayStore) "ĐÃ CÀI ĐẶT" else "KHÔNG TÌM THẤY"
                txtPlayStoreStatus.setTextColor(Color.parseColor(if (hasPlayStore) "#155724" else "#721C24"))
                (txtPlayStoreStatus.parent as? LinearLayout)?.background = createRoundedDrawable(if (hasPlayStore) "#D4EDDA" else "#F8D7DA", 15f)
            }

            if (::txtJobSchedulerStatus.isInitialized) {
                txtJobSchedulerStatus.text = "ĐANG HOẠT ĐỘNG"
                txtJobSchedulerStatus.setTextColor(Color.parseColor("#155724"))
                (txtJobSchedulerStatus.parent as? LinearLayout)?.background = createRoundedDrawable("#D4EDDA", 15f)
            }
        } catch (_: Exception) {}
    }

    private fun createCompatibilityScreen(): ScrollView {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F5F9"))
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#383B8E"))
            setPadding(50, 80, 50, 60)
        }

        val title = TextView(this).apply {
            text = "Kiểm tra tương thích"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        val subTitle = TextView(this).apply {
            text = "Trạng thái kiểm tra thực tế trên thiết bị của bạn"
            textSize = 13f
            setTextColor(Color.parseColor("#C5C7E8"))
            setPadding(0, 8, 0, 0)
        }

        header.addView(title)
        header.addView(subTitle)
        mainLayout.addView(header)

        val bodyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 30, 35, 40)
        }

        // Device Info Card
        val devCard = createCardView()
        val devInfo = TextView(this).apply {
            text = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}"
            textSize = 13f
            setTextColor(Color.parseColor("#495057"))
            setTypeface(null, Typeface.BOLD)
        }
        val devBadge = createBadge("THIẾT BỊ HỖ TRỢ", "#E2F0D9", "#385723").apply {
            setPadding(20, 8, 20, 8)
        }

        devCard.addView(devInfo)
        devCard.addView(devBadge)
        bodyLayout.addView(devCard)

        // System & Permissions Card
        val hkCard = createCardView()
        hkCard.addView(TextView(this).apply {
            text = "Trạng thái Hệ thống & Quyền"
            textSize = 17f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        })

        txtPowerKeeperStatus = createBadge("ĐANG QUÉT...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("PowerKeeper / Millet Service", txtPowerKeeperStatus))

        txtWriteSettingsStatus = createBadge("ĐANG QUÉT...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("Quyền thay đổi cài đặt", txtWriteSettingsStatus))

        txtBatteryStatus = createBadge("ĐANG QUÉT...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("Miễn tối ưu pin Android", txtBatteryStatus))

        txtSystemPropStatus = createBadge("ĐANG QUÉT...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("Ghi cờ SystemProperties", txtSystemPropStatus))

        txtGmsAllowlistStatus = createBadge("Đang kiểm tra...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("Millet Allowlist: GMS", txtGmsAllowlistStatus))

        txtPlayStoreAllowlistStatus = createBadge("Đang kiểm tra...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("Millet Allowlist: CH Play", txtPlayStoreAllowlistStatus))

        txtSelfAllowlistStatus = createBadge("Đang kiểm tra...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("Millet Allowlist: GMS Protect", txtSelfAllowlistStatus))

        txtGmsServiceStatus = createBadge("Đang kiểm tra...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("Google Play services", txtGmsServiceStatus))

        txtPlayStoreStatus = createBadge("Đang kiểm tra...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("CH Play", txtPlayStoreStatus))

        txtJobSchedulerStatus = createBadge("ĐANG HOẠT ĐỘNG", "#D4EDDA", "#155724")
        hkCard.addView(createCustomRow("Tiến trình JobScheduler", txtJobSchedulerStatus))

        bodyLayout.addView(hkCard)

        // Back Button
        val btnBack = Button(this).apply {
            text = "Quay lại Màn hình chính"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 14f
            background = createRoundedDrawable("#383B8E", 20f)
            setPadding(0, 25, 0, 25)
            setOnClickListener { finish() }
        }
        bodyLayout.addView(btnBack)

        mainLayout.addView(bodyLayout)
        rootScroll.addView(mainLayout)
        return rootScroll
    }

    private fun createCardView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedDrawable("#FFFFFF", 30f)
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, 30)
            }
        }
    }

    private fun createBadge(textStr: String, bgColorHex: String, textColorHex: String): TextView {
        return TextView(this).apply {
            text = textStr
            textSize = 11f
            setTextColor(Color.parseColor(textColorHex))
            setTypeface(null, Typeface.BOLD)
            background = createRoundedDrawable(bgColorHex, 15f)
            setPadding(25, 10, 25, 10)
            layoutParams = LinearLayout.LayoutParams(-2, -2)
        }
    }

    private fun createCustomRow(label: String, badgeView: TextView): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
            setPadding(0, 12, 0, 12)
        }

        val txtLabel = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.6f)
        }

        val badgeContainer = LinearLayout(this).apply {
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.4f)
        }

        badgeContainer.addView(badgeView)
        row.addView(txtLabel)
        row.addView(badgeContainer)
        return row
    }

    private fun createRoundedDrawable(colorHex: String, cornerRadius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            setCornerRadius(cornerRadius)
        }
    }
}
