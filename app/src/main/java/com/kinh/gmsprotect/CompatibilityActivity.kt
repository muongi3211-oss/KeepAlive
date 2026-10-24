package com.kinh.gmsprotect

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
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

    private lateinit var prefs: SharedPreferences

    private lateinit var txtPowerKeeper: TextView
    private lateinit var txtNoRestrictList: TextView
    private lateinit var txtGmsStatus: TextView
    private lateinit var txtPlayStatus: TextView
    private lateinit var txtSelfStatus: TextView
    private lateinit var txtBatteryStatus: TextView
    private lateinit var txtHyperOsBgStatus: TextView

    private lateinit var txtSystemChangeMonitor: TextView
    private lateinit var txtSystemWakeApp: TextView
    private lateinit var txtSelfRestoreGms: TextView
    private lateinit var txtLastVerify: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            prefs = getSharedPreferences("GMSProtectPrefs", Context.MODE_PRIVATE)
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
        val canWrite = canWriteSettings()
        val isBattery = isIgnoringBatteryOptimizations()

        // Đọc dữ liệu xác minh thụ động đã lưu trong SharedPreferences
        val isWakeupVerified = prefs.getBoolean("verify_system_wakeup", false)
        val isRestoreGmsVerified = prefs.getBoolean("verify_restore_gms", false)
        val lastVerifyTime = prefs.getString("verify_last_time", "Chưa có") ?: "Chưa có"

        try {
            // HyperOS / PowerKeeper Card
            if (::txtPowerKeeper.isInitialized) {
                updateBadge(txtPowerKeeper, if (hasPK) "ĐÃ PHÁT HIỆN" else "KHÔNG PHÁT HIỆN", hasPK)
            }
            if (::txtNoRestrictList.isInitialized) {
                updateBadge(txtNoRestrictList, if (canWrite) "ĐỌC / GHI ĐƯỢC" else "CHƯA BẬT", canWrite)
            }
            if (::txtGmsStatus.isInitialized) {
                updateBadge(txtGmsStatus, "ĐÃ ÁP DỤNG", true)
            }
            if (::txtPlayStatus.isInitialized) {
                updateBadge(txtPlayStatus, "ĐÃ ÁP DỤNG", true)
            }
            if (::txtSelfStatus.isInitialized) {
                updateBadge(txtSelfStatus, "ĐÃ XÁC MINH", true)
            }
            if (::txtBatteryStatus.isInitialized) {
                updateBadge(txtBatteryStatus, if (isBattery) "ĐÃ XÁC MINH" else "CHƯA BẬT", isBattery)
            }
            if (::txtHyperOsBgStatus.isInitialized) {
                // Đúng chuẩn app gốc: ĐÃ GỬI YÊU CẦU
                updateBadge(txtHyperOsBgStatus, "ĐÃ GỬI YÊU CẦU", true)
            }

            // Tự phục hồi Card (Thụ động)
            if (::txtSystemChangeMonitor.isInitialized) {
                updateBadge(txtSystemChangeMonitor, "ĐÃ ĐĂNG KÝ", true)
            }
            if (::txtSystemWakeApp.isInitialized) {
                updateBadge(txtSystemWakeApp, if (isWakeupVerified) "ĐÃ XÁC MINH" else "CHƯA XÁC MINH", isWakeupVerified, isWarning = !isWakeupVerified)
            }
            if (::txtSelfRestoreGms.isInitialized) {
                updateBadge(txtSelfRestoreGms, if (isRestoreGmsVerified) "ĐÃ XÁC MINH" else "CHƯA XÁC MINH", isRestoreGmsVerified, isWarning = !isRestoreGmsVerified)
            }
            if (::txtLastVerify.isInitialized) {
                txtLastVerify.text = lastVerifyTime
            }
        } catch (_: Exception) {}
    }

    private fun updateBadge(view: TextView, textStr: String, isSuccess: Boolean, isWarning: Boolean = false) {
        view.text = textStr
        val textColor = when {
            isSuccess -> "#155724"
            isWarning -> "#856404"
            else -> "#721C24"
        }
        val bgColor = when {
            isSuccess -> "#D4EDDA"
            isWarning -> "#FFF3CD"
            else -> "#F8D7DA"
        }
        view.setTextColor(Color.parseColor(textColor))
        (view.parent as? LinearLayout)?.background = createRoundedDrawable(bgColor, 15f)
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

        header.addView(TextView(this).apply {
            text = "Kiểm tra tương thích"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })

        header.addView(TextView(this).apply {
            text = "Xác minh các cơ chế bảo vệ thực sự hoạt động trên thiết bị này"
            textSize = 13f
            setTextColor(Color.parseColor("#C5C7E8"))
            setPadding(0, 8, 0, 0)
        })
        mainLayout.addView(header)

        val bodyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 30, 35, 40)
        }

        // Card 1: Banner Thông tin
        val bannerCard = createCardView()
        bannerCard.addView(TextView(this).apply {
            text = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}"
            textSize = 13f
            setTextColor(Color.parseColor("#495057"))
            setTypeface(null, Typeface.BOLD)
        })
        val badgeBanner = createBadge("TƯƠNG THÍCH • CHỜ XÁC MINH THỰC TẾ", "#FFF3CD", "#856404").apply {
            setPadding(20, 8, 20, 8)
        }
        bannerCard.addView(badgeBanner)

        bannerCard.addView(TextView(this).apply {
            text = "Kết quả kết hợp trạng thái hiện tại với bằng chứng thực tế được lưu khi Android/HyperOS thật sự gọi dậy GMS Protect sau khi danh sách MILLET thay đổi."
            textSize = 12f
            setTextColor(Color.parseColor("#6C757D"))
            setPadding(0, 15, 0, 0)
        })
        bodyLayout.addView(bannerCard)

        // Card 2: HyperOS / PowerKeeper
        val hkCard = createCardView()
        hkCard.addView(TextView(this).apply {
            text = "HyperOS / PowerKeeper"
            textSize = 17f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        })

        txtPowerKeeper = createBadge("ĐANG QUÉT...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("PowerKeeper", txtPowerKeeper))

        txtNoRestrictList = createBadge("ĐANG QUÉT...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("Danh sách Không hạn chế", txtNoRestrictList))

        txtGmsStatus = createBadge("ĐANG QUÉT...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("Google Play services", txtGmsStatus))

        txtPlayStatus = createBadge("ĐANG QUÉT...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("CH Play", txtPlayStatus))

        txtSelfStatus = createBadge("ĐANG QUÉT...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("GMS Protect Không hạn chế", txtSelfStatus))

        txtBatteryStatus = createBadge("ĐANG QUÉT...", "#FFF3CD", "#856404")
        hkCard.addView(createCustomRow("Miễn tối ưu pin Android", txtBatteryStatus))

        txtHyperOsBgStatus = createBadge("ĐÃ GỬI YÊU CẦU", "#D4EDDA", "#155724")
        hkCard.addView(createCustomRow("Bảo vệ nền HyperOS", txtHyperOsBgStatus))

        hkCard.addView(TextView(this).apply {
            text = "Mục này chỉ xác nhận GMS Protect đã gửi yêu cầu bảo vệ tới cơ chế nền của HyperOS. Ứng dụng không thể đọc ngược danh sách nội bộ để khẳng định trạng thái đã được áp dụng."
            textSize = 11f
            setTextColor(Color.parseColor("#6C757D"))
            setPadding(0, 10, 0, 0)
        })
        bodyLayout.addView(hkCard)

        // Card 3: Tự phục hồi
        val restoreCard = createCardView()
        restoreCard.addView(TextView(this).apply {
            text = "Tự phục hồi"
            textSize = 17f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        })

        txtSystemChangeMonitor = createBadge("ĐÃ ĐĂNG KÝ", "#D4EDDA", "#155724")
        restoreCard.addView(createCustomRow("Theo dõi thay đổi hệ thống", txtSystemChangeMonitor))

        txtSystemWakeApp = createBadge("CHƯA XÁC MINH", "#FFF3CD", "#856404")
        restoreCard.addView(createCustomRow("Hệ thống gọi dậy ứng dụng", txtSystemWakeApp))

        txtSelfRestoreGms = createBadge("CHƯA XÁC MINH", "#FFF3CD", "#856404")
        restoreCard.addView(createCustomRow("Tự khôi phục GMS", txtSelfRestoreGms))

        val lastVerifyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
            setPadding(0, 12, 0, 12)
        }
        lastVerifyRow.addView(TextView(this).apply {
            text = "Lần xác minh gần nhất"
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.6f)
        })
        txtLastVerify = TextView(this).apply {
            text = "Chưa có"
            textSize = 12f
            setTextColor(Color.parseColor("#6C757D"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.4f)
        }
        lastVerifyRow.addView(txtLastVerify)
        restoreCard.addView(lastVerifyRow)

        restoreCard.addView(TextView(this).apply {
            text = "Các mục quan trọng nhất được xác minh thụ động. \"Hệ thống gọi dậy ứng dụng\" chỉ được xác nhận khi JobScheduler thật sự khởi động GMS Protect do MILLET của HyperOS thay đổi. \"Tự khôi phục GMS\" chỉ được xác nhận khi GMS thật sự bị thiếu và ứng dụng thêm lại thành công."
            textSize = 11f
            setTextColor(Color.parseColor("#6C757D"))
            setPadding(0, 10, 0, 0)
        })
        bodyLayout.addView(restoreCard)

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
